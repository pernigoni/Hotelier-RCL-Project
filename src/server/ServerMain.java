package server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import entities.Hotel;
import entities.Review;
import entities.User;
import rmi.NotifyServerImpl;
import rmi.NotifyServerInterface;
import rmi.UserHashMap;
import rmi.UserHashMapInterface;

public class ServerMain
{
	public static final String configFile = "server.properties";
	public static final String citiesJsonPath = "json/Cities.json";
	public static final String hotelsJsonPath = "json/Hotels.json";
	public static final String usersJsonPath = "json/Users.json";
	public static final String reviewsJsonPath = "json/Reviews.json";
	public static final int DEFAULT_PERIOD = 5;

	public static int RMIport; // porta per il registry RMI
	public static String RMIserviceNameRegUser; // nome del servizio RMI offerto dal server (registrazione utente)
	public static String RMIserviceNameNotify; // nome del servizio RMI offerto dal server (notifica aggiornamento classifica)

	public static int maxDelay; // tempo di attesa prima della chiusura del pool di thread, in millisecondi
	public static int TCPport; // porta di ascolto del server
	public static ServerSocket serverSocket;
	public static final ExecutorService pool = Executors.newCachedThreadPool();

	public static int UDPport; // porta multicast
	public static String multicastAddress; // indirizzo di multicast
	public static DatagramSocket datagramSocket;

	public static int persistencePeriod; // periodo di tempo tra un salvataggio delle strutture dati in json e l'altro, in secondi
	public static int rankingPeriod; // periodo di tempo tra un ricalcolo della classifica locale e l'altro, in secondi
	public static int sameReviewerSameHotelPeriod; // periodo di tempo tra le recensioni dello stesso utente per lo stesso hotel, in secondi

	// hash map che ha come chiave la città e valore la lista degli hotel presenti in quella città
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap = new ConcurrentHashMap<>();

	// hash map che ha come chiave 'nomeHotel_città' e valore la lista di recensioni di quell'hotel
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap = new ConcurrentHashMap<>();

	// hash map degli utenti registrati
	public static ConcurrentHashMap<String, User> usersMap;

	public static void main(String[] args)
	{
		try
		{
			readConfig();
		}
		catch(Exception e)
		{
			System.err.println("[SERVER] Errore durante la lettura del file di configurazione");
			e.printStackTrace();
			System.exit(1);
		}

		try
		{
			loadCitiesFromJson(); // carico le città in hotelsByCityMap dal file json
			loadHotelsFromJson(); // carico gli hotel in hotelsByCityMap dal file json
			loadReviewsFromJson(); // carico le recensioni in reviewsMap dal file json

			// ordino, per ogni città, la lista di hotel in modo decrescente in base al rate
			hotelsByCityMap.forEach((city, list) ->
				list.sort(Comparator.comparingDouble(Hotel::getRate).reversed()));
		}
		catch(Exception e)
		{
			System.err.println("[SERVER] Errore durante il caricamento da file json");
			e.printStackTrace();
			System.exit(1);
		}

		/*
		 * RMI PER LA REGISTRAZIONE DI UN UTENTE
		 */
		Registry r = null;
		try
		{
			// creo e inizializzo la hash map degli utenti (oggetto remoto)
			UserHashMap users = new UserHashMap();

			// prendo un riferimento
			usersMap = users.getUsersMap();

			try
			{	// carico gli utenti in usersMap dal file json
				loadUsersFromJson();
			}
			catch(Exception e)
			{
				System.err.println("[SERVER] Errore durante il caricamento da file json");
				e.printStackTrace();
				System.exit(1);
			}

			// esporto l'oggetto ottenendo lo stub corrispondente
			UserHashMapInterface stub = (UserHashMapInterface) UnicastRemoteObject.exportObject(users, 0);

			// creo un registry sulla porta specificata
			LocateRegistry.createRegistry(RMIport);
			r = LocateRegistry.getRegistry(RMIport);

			// pubblico lo stub nel registry
			r.rebind(RMIserviceNameRegUser, stub);
		}
		catch(RemoteException e)
		{
			System.err.println("[SERVER] Errore: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		/*
		 * PERSISTENZA DELLE STRUTTURE DATI SUI FILE JSON
		 */
		DataPersistenceTask dataPersistenceTask = new DataPersistenceTask(
			usersMap, usersJsonPath, reviewsMap, reviewsJsonPath, hotelsByCityMap, hotelsJsonPath);

		// persisto le strutture dati ogni 'persistencePeriod' secondi
		if(persistencePeriod <= 0)
			persistencePeriod = DEFAULT_PERIOD;
		ScheduledExecutorService schedulerDataPersistence = Executors.newSingleThreadScheduledExecutor();
		schedulerDataPersistence.scheduleAtFixedRate(
			dataPersistenceTask, persistencePeriod, persistencePeriod, TimeUnit.SECONDS);

		// alla terminazione del server persisto i dati un'ultima volta
		Runtime.getRuntime().addShutdownHook(new Thread(dataPersistenceTask));

		/*
		 * RMI PER IL SERVIZIO DI NOTIFICA
		 */
		NotifyServerImpl server = null;
		try
		{
			// creo l'oggetto remoto
			server = new NotifyServerImpl();

			// esporto l'oggetto ottenendo lo stub corrispondente
			NotifyServerInterface stub = (NotifyServerInterface) UnicastRemoteObject.exportObject(server, 0);

			// pubblico lo stub nel registry
			r.rebind(RMIserviceNameNotify, stub);
		}
		catch(Exception e)
		{
			System.err.println("[SERVER] Errore: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		/*
		 * MULTICAST UDP
		 * PER RICALCOLARE PERIODICAMENTE LE CLASSIFICHE LOCALI
		 */
		try
		{
			datagramSocket = new DatagramSocket();

			// ottengo l'indirizzo del gruppo e ne controllo la validità
			InetAddress group = InetAddress.getByName(multicastAddress);
			if(!group.isMulticastAddress())
			{
				datagramSocket.close();
				throw new IllegalArgumentException("Indirizzo di multicast non valido " + group.getHostAddress());
			}

			// ricalcolo le classifiche locali ogni 'rankingPeriod' secondi
			if(rankingPeriod <= 0)
				rankingPeriod = DEFAULT_PERIOD;
			ScheduledExecutorService schedulerLocalRanking = Executors.newSingleThreadScheduledExecutor();
			schedulerLocalRanking.scheduleAtFixedRate(
				new LocalRankingUpdater(
					datagramSocket, group, UDPport,
					reviewsMap, hotelsByCityMap,
					server),
				1, rankingPeriod, TimeUnit.SECONDS);
		}
		catch(Exception e)
		{
			System.err.println("[SERVER] Errore: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		/*
		 * TCP
		 * PER INTERAGIRE CON IL CLIENT SECONDO IL MODELLO RICHIESTA/RISPOSTA
		 */
		try
		{
			serverSocket = new ServerSocket(TCPport);

			// chiusura ordinata del pool di thread e del server socket quando il runtime Java viene terminato
			Runtime.getRuntime().addShutdownHook(new TerminationHandler(maxDelay, pool, serverSocket));
			System.out.printf("[SERVER] Pronto\n");

			while(true)
			{
				Socket socket = null;
				try
				{	// accetto le richieste provenienti dai client
					socket = serverSocket.accept();
				}
				catch(SocketException e)
				{
					break;
				}
				// eseguo un nuovo task Worker per gestire la connessione con il client
				pool.execute(new Worker(socket, usersMap, hotelsByCityMap, reviewsMap, sameReviewerSameHotelPeriod));
			}
		}
		catch(Exception e)
		{
			System.err.println("[SERVER] Errore: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Legge le città da un file json e le inserisce come chiave in hotelsByCityMap. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private static void loadCitiesFromJson() throws Exception
	{
		JsonReader reader = new JsonReader(new FileReader(citiesJsonPath));
		reader.beginArray(); // [

		// continuo a leggere finché ci sono elementi nell'array
		while(reader.hasNext()) // inserisco la città come chiave in hotelsByCityMap
			hotelsByCityMap.put(reader.nextString(), new CopyOnWriteArrayList<>());

		reader.endArray(); // ]
		reader.close();
	}

	/**
	 * Legge gli hotel da un file json. Ogni hotel viene aggiunto alla lista, valore di hotelsByCityMap,
	 * che ha come chiave la città in cui esso si trova. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private static void loadHotelsFromJson() throws Exception
	{
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(new FileReader(hotelsJsonPath));
		reader.beginArray(); // [

		// continuo a leggere finché ci sono elementi nell'array
		while(reader.hasNext())
		{
			// deserializzo ogni hotel
			Hotel hotel = gson.fromJson(reader, Hotel.class);

			// inserisco l'hotel in hotelsByCityMap
			hotelsByCityMap.computeIfPresent(hotel.getCity(), (k, list) -> {
				list.add(hotel);
				return list;
			});
		}
		reader.endArray(); // ]
	}

	/**
	 * Legge le recensioni da un file json. Ogni recensione viene aggiunta alla lista, valore di reviewsMap,
	 * che ha come chiave 'nomeHotel_città' dell'hotel di cui è stata fatta la recensione. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private static void loadReviewsFromJson() throws Exception
	{
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(new FileReader(reviewsJsonPath));
		reader.beginArray(); // [

		// continuo a leggere finché ci sono elementi nell'array
		while(reader.hasNext())
		{
			// deserializzo ogni recensione
			Review review = gson.fromJson(reader, Review.class);

			// inserisco la recensione in reviewsMap
			String key = review.getHotelName() + "_" + review.getCity();
			reviewsMap.compute(key, (k, list) -> {
				if(list == null)
					list = new CopyOnWriteArrayList<>();
				list.add(review);
				return list;
			});
		}
		reader.endArray(); // ]
	}

	/**
	 * Legge gli utenti da un file json e li inserisce in usersMap con chiave 'username'. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private static void loadUsersFromJson() throws Exception
	{
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(new FileReader(usersJsonPath));
		reader.beginArray(); // [

		// continuo a leggere finché ci sono elementi nell'array
		while(reader.hasNext())
		{
			// deserializzo ogni utente
			User user = gson.fromJson(reader, User.class);

			// inserisco l'utente in usersMap
			usersMap.put(user.getUsername(), user);
		}
		reader.endArray(); // ]
	}

	/**
	 * Legge il file di configurazione del server.
	 */
	private static void readConfig() throws FileNotFoundException, IOException
	{
		// il file di cofigurazione si trova nel path assoluto del progetto
		File projectAbsolutePath = new File(Paths.get("").toAbsolutePath().toString());
		URL[] url = {projectAbsolutePath.toURI().toURL()};

		try(// creo un URLClassLoader per caricare il file di configurazione
			URLClassLoader classLoader = new URLClassLoader(url);
			InputStream input = classLoader.getResourceAsStream(configFile))
		{
			Properties prop = new Properties();
			prop.load(input);
			RMIport = Integer.parseInt(prop.getProperty("RMIport"));
			RMIserviceNameRegUser = prop.getProperty("RMIserviceNameRegUser");
			RMIserviceNameNotify = prop.getProperty("RMIserviceNameNotify");
			TCPport = Integer.parseInt(prop.getProperty("TCPport"));
			maxDelay = Integer.parseInt(prop.getProperty("maxDelay"));
			persistencePeriod = Integer.parseInt(prop.getProperty("persistencePeriod"));
			rankingPeriod = Integer.parseInt(prop.getProperty("rankingPeriod"));
			UDPport = Integer.parseInt(prop.getProperty("UDPport"));
			multicastAddress = prop.getProperty("multicastAddress");
			sameReviewerSameHotelPeriod = Integer.parseInt(prop.getProperty("sameReviewerSameHotelPeriod"));
		}
	}
}