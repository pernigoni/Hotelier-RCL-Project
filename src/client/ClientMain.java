package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import entities.StatusClient;
import entities.StatusRegistration;
import rmi.NotifyClientImpl;
import rmi.NotifyClientInterface;
import rmi.NotifyServerInterface;
import rmi.UserHashMapInterface;

public class ClientMain
{
	public static final String configFile = "client.properties";

	public static int RMIport; // porta per il registry RMI
	public static String RMIserviceNameRegUser; // nome del servizio RMI offerto dal server (registrazione utente)
	public static String RMIserviceNameNotify; // nome del servizio RMI offerto dal server (notifica aggiornamento classifica)

	public static String hostname; // host su cui risiede il server
	public static int TCPport; // porta di ascolto del server

	public static String multicastAddress; // indirizzo di multicast
	public static int UDPport; // porta multicast

	public static void main(String[] args)
	{
		try
		{
			readConfig();
		}
		catch(Exception e)
		{
			System.err.println("Errore durante la lettura del file di configurazione");
			e.printStackTrace();
			System.exit(1);
		}

		Scanner inputScanner = null;
		try(Socket socket = new Socket(hostname, TCPport))
		{	/*
			 * SOCKET TCP E RELATIVI STREAM DI INPUT/OUTPUT
			 */
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

			/*
			 * MULTICAST UDP
			 * CODA
			 */
			/* Coda per gestire in modo asincrono le notifiche riguardanti il cambiamento della prima
			 * posizione delle classifiche locali.
			 * Le notifiche sono stringhe e vengono inserite in questa coda dal thread 'localRankingReceiver'. */
			LinkedBlockingQueue<String> notifyRankedFirstQueue = new LinkedBlockingQueue<>();
			LocalRankingReceiver localRankingReceiver = new LocalRankingReceiver(multicastAddress, UDPport, notifyRankedFirstQueue);
			int countLogin = 0;

			/*
			 * RMI PER LA REGISTRAZIONE DI UN UTENTE
			 */
			// ottengo il riferimento al registry e alla hash map remota
			Registry r = LocateRegistry.getRegistry(RMIport);
			UserHashMapInterface u = (UserHashMapInterface) r.lookup(RMIserviceNameRegUser);

			/*
			 * RMI PER IL SERVIZIO DI NOTIFICA
			 */
			/* Hash map che contiene gli aggiornamenti delle classifiche locali.
			 * Ha come chiave la città della classifica locale e valore la lista che contiene i nomi di,
			 * al massimo, i primi 3 hotel della nuova classifica in ordine decrescente di rate. */
			ConcurrentHashMap<String, CopyOnWriteArrayList<String>> updatedRankingMap = new ConcurrentHashMap<>();

			/* Lista che contiene i nomi delle città di cui l'utente, immediatamente dopo il login,
			 * ha espresso l'interesse di seguirne la classifica. */
			CopyOnWriteArrayList<String> followedCities = new CopyOnWriteArrayList<>();

			// ottengo il riferimento al server remoto
			NotifyServerInterface server = (NotifyServerInterface) r.lookup(RMIserviceNameNotify);

			// creo l'oggetto remoto callback client e lo esporto ottenendo lo stub corrispondente
			NotifyClientInterface callbackObj = new NotifyClientImpl(updatedRankingMap);
			NotifyClientInterface stub = (NotifyClientInterface) UnicastRemoteObject.exportObject(callbackObj, 0);

			/*
			 * SCAMBIO DI MESSAGGI CON IL SERVER
			 * 
			 * Comandi supportati dal client:
			 *   help
			 *   exit
			 *   register <username> <password>
			 *   login <username> <password>
			 *   logout <username>
			 *   searchHotel <nomeHotel> <città>
			 *   searchAllHotels <città>
			 *   insertReview <nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>
			 *   showMyBadges
			 *   myRankings
			 * 
			 * Formato dei messaggi di risposta ricevuti dal server: [stato],[contenuto]\n
			 *   [stato] indica lo stato dell'utente sul client.
			 *   [contenuto] indica il testo del messaggio di risposta.
			 */
			StatusClient status = StatusClient.USER_NOT_LOGGED; // stato iniziale dell'utente sul client
			inputScanner = new Scanner(System.in);
			while(true)
			{
				if(status.equals(StatusClient.USER_LOGGED))
					while(!notifyRankedFirstQueue.isEmpty())
					{
						// prendo e rimuovo ogni notifica dalla coda e la stampo
						// (queste sono le notifiche arrivate dal gruppo di multicast)
						String notification = notifyRankedFirstQueue.poll();
						if(notification != null)
							System.out.println(notification);
					}

				System.out.printf("> ");

				// leggo l'input dell'utente
				String line = inputScanner.nextLine();
				if(line.isBlank())
					continue;

				String[] parts = line.split(" ");

				// interpreto il comando 'register <username> <password>'
				if(parts[0].equals("register"))
				{
					// da loggato non posso registrare utenti
					if(status.equals(StatusClient.USER_LOGGED))
					{
						System.err.println("Errore: operazione non consentita dopo il login");
						continue;
					}

					if(parts.length == 3)
					{
						String username = parts[1];
						String password = parts[2];
						try
						{
							// invoco il metodo remoto per registrare l'utente
							StatusRegistration res = u.register(username, password);
							switch(res)
							{
								case SUCCESS:
									System.out.println("Registrazione avvenuta con successo");
									break;
								case USERNAME_TAKEN:
									System.err.println("Errore: l'username inserito non è disponibile");
									break;
								case BLANK_USERNAME:
									System.err.println("Errore: l'username non può essere vuoto");
									break;
								case BLANK_PASSWORD:
									System.err.println("Errore: la password non può essere vuota");
									break;
								case TOO_LONG:
									System.err.println("Errore: l'username o la password superano la lunghezza massima consentita");
									break;
							}
						}
						catch(RemoteException e)
						{
							System.err.println("Errore lato server: " + e.getMessage());
							e.printStackTrace();
						}
					}
					else
						System.err.println("Errore, usare: register <username> <password>");
				}
				// interpreto il comando 'myRankings'
				else if(parts[0].equals("myRankings"))
				{
					/* Il comando 'myRankings' permette all'utente loggato di vedere gli aggiornamenti
					 * delle prime 3 posizioni delle classifiche delle città a cui ha espresso il proprio
					 * interesse dopo il login. */

					if(!status.equals(StatusClient.USER_LOGGED))
					{
						System.err.println("Errore: operazione non consentita prima del login");
						continue;
					}
					if(followedCities.isEmpty())
					{
						System.out.println("Non segui alcuna città");
						continue;
					}
					if(updatedRankingMap.isEmpty())
					{
						System.out.println("Nessun aggiornamento dal momento del tuo login");
						continue;
					}

					updatedRankingMap.forEach((city, hotelNames) -> {
						if(followedCities.contains(city))
						{
							System.out.println(city);
							for(int i = 0; i < hotelNames.size(); i++)
								System.out.println("  (" + (i + 1) + ") " + hotelNames.get(i));
						}
					});
				}
				// altri comandi
				else
				{
					out.println(line); // invio l'input dell'utente al server
					String reply = in.readLine(); // leggo la risposta del server

					// [stato],[contenuto]\n
					String[] replyParts = reply.split(",", 2);

					// aggiorno lo stato dell'utente sul client
					StatusClient previousStatus = status; // stato precedente
					if(replyParts[0].equals(StatusClient.USER_LOGGED.name()))
						status = StatusClient.USER_LOGGED;
					else if(replyParts[0].equals(StatusClient.USER_NOT_LOGGED.name()))
						status = StatusClient.USER_NOT_LOGGED;
					else
						status = StatusClient.EXIT;

					// stampo il contenuto del messaggio di risposta (sostituisco "*\n*" con new line)
					replyParts[1] = replyParts[1].replace("*\\n*", "\n");
					if(replyParts[1].startsWith("Errore"))
						System.err.println(replyParts[1]);
					else
						System.out.println(replyParts[1]);

					/*
					 * NOTIFICHE RMI E NOTIFICHE MULTICAST UDP
					 */
					if(!previousStatus.equals(StatusClient.USER_LOGGED) && status.equals(StatusClient.USER_LOGGED))
					{
						/* Se entro qui vuol dire che l'utente ha appena effettuato il login con successo.
						 * 
						 * A questo punto l'utente deve inserire le città che vuole seguire per riceverne
						 * gli aggiornamenti sulla classifica.
						 * Le città sono richieste tutte su una riga, con uno spazio tra una e l'altra.
						 * 
						 * Il server risponde nel seguente modo:
						 * ("%s,Città seguite: %s\n", status.name(), followedCities)
						 */

						System.out.printf("> ");
						line = inputScanner.nextLine();

						out.println(line); // invio l'input dell'utente al server
						reply = in.readLine(); // leggo la risposta del server

						// [stato],[contenuto]\n
						replyParts = reply.split(",", 2);

						// stampo il contenuto del messaggio di risposta
						System.out.println(replyParts[1]);

						// estraggo le città che seguo correttamente dal messaggio del server...
						replyParts = reply.split(" ");
						// ...e le aggiungo alla lista delle città che seguo
						for(int i = 2; i < replyParts.length; i++)
							if(!replyParts[i].equalsIgnoreCase("nessuna"))
								followedCities.add(replyParts[i]);

						if(!followedCities.isEmpty())
							try
							{	// mi registro al servizio di notifica RMI del server
								server.registerForCallback(stub);
							}
							catch(RemoteException e)
							{
								System.err.println("Errore: " + e.getMessage());
								e.printStackTrace();
							}

						/* Dato che l'utente ha appena fatto il login, avvio anche il thread che si iscrive
						 * al gruppo di multicast per ricevere notifiche riguardanti il cambiamento della prima
						 * posizione delle classifiche locali. */
						countLogin++;
						if(countLogin > 1)
							localRankingReceiver = new LocalRankingReceiver(multicastAddress, UDPport, notifyRankedFirstQueue);
						localRankingReceiver.start();
					}
					if(previousStatus.equals(StatusClient.USER_LOGGED) && !status.equals(StatusClient.USER_LOGGED))
					{
						// Se entro qui vuol dire che l'utente ha appena effettuato il logout con successo.

						try
						{	// cancello la registrazione al servizio di notifica RMI del server
							server.unregisterForCallback(stub);
						}
						catch(Exception e)
						{ }

						// arresto il thread 'localRankingReceiver'
						localRankingReceiver.shutdown();

						// rimuovo tutti gli elementi dalle strutture dati che servono a gestire le notifiche
						updatedRankingMap.clear();
						notifyRankedFirstQueue.clear();
						followedCities.clear();
					}

					if(status.equals(StatusClient.EXIT))
						break; // esco dal ciclo
				}
			}

			UnicastRemoteObject.unexportObject(callbackObj, true);
		}
		catch(Exception e)
		{
			System.err.println("Errore: " + e.getMessage());
			e.printStackTrace();
		}
		finally
		{
			if(inputScanner != null)
				inputScanner.close();
			System.out.println("[CLIENT] Terminato");
		}
	}

	/**
	 * Legge il file di configurazione del client.
	 */
	public static void readConfig() throws FileNotFoundException, IOException
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
			hostname = prop.getProperty("hostname");
			UDPport = Integer.parseInt(prop.getProperty("UDPport"));
			multicastAddress = prop.getProperty("multicastAddress");
		}
	}
}