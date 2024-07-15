package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import entities.Hotel;
import entities.Ratings;
import entities.Review;
import entities.StatusClient;
import entities.User;
import utils.PasswordUtils;

/**
 * Classe che rappresenta il thread worker che si occupa di interagire con un client su una connessione TCP. <p>
 * 
 * 1) Riceve un comando dal client. <p>
 * 2) Esegue l'azione richiesta. <p>
 * 3) Invia la risposta al client.
 */

/*
 * Comandi gestiti dal worker:
 *   help
 *   exit
 *   login <username> <password>
 *   logout <username>
 *   searchHotel <nomeHotel> <città>
 *   searchAllHotels <città>
 *   insertReview <nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>
 *   showMyBadges
 * 
 * Formato dei messaggi di risposta inviati al client: [stato],[contenuto]\n
 *   [stato] indica lo stato dell'utente sul client.
 *   [contenuto] indica il testo del messaggio di risposta.
 */

public class Worker implements Runnable
{
	// stato dell'utente sul client, inizialmente è non loggato
	private StatusClient status = StatusClient.USER_NOT_LOGGED;

	// se l'utente è loggato contiene il suo username, altrimenti è vuoto
	private StringBuilder usernameLogged = new StringBuilder();

	// socket e relativi stream di input/output
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;

	// riferimento alla hash map degli utenti registrati
	private ConcurrentHashMap<String, User> usersMap;

	// riferimento alla hash map che ha come chiave la città e valore la lista degli hotel presenti in quella città
	private ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap;

	// riferimento alla hash map che ha come chiave 'nomeHotel_città' e valore la lista di recensioni di quell'hotel
	private ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap;

	// periodo di tempo tra le recensioni dello stesso utente per lo stesso hotel, in secondi
	private int sameReviewerSameHotelPeriod;

	public Worker(
		Socket socket,
		ConcurrentHashMap<String, User> usersMap,
		ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap,
		ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap, 
		int sameReviewerSameHotelPeriod)
	{
		this.socket = socket;
		this.usersMap = usersMap;
		this.hotelsByCityMap = hotelsByCityMap;
		this.reviewsMap = reviewsMap;
		this.sameReviewerSameHotelPeriod = sameReviewerSameHotelPeriod;
	}

	public void run()
	{
		try
		{
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);

			/*
			 * SCAMBIO DI MESSAGGI CON IL CLIENT SULLA CONNESSIONE TCP
			 */
			while(true)
			{
				String line = in.readLine(); // leggo l'input inviato dal client
				String[] parts = line.split(" ");

				// il client ha inviato il comando 'exit'
				if(parts[0].equals("exit"))
				{
					/* Se l'utente è loggato faccio in automatico il logout.
					 * In ogni caso setto lo stato dell'utente sul client a StatusClient.EXIT e invio
					 * la risposta. */

					if(usernameLogged.length() > 0)
					{
						// c'è un utente loggato
						String[] ul = {"logout", usernameLogged.toString()};
						logout(ul, true);
						status = StatusClient.EXIT;
						out.printf("%s,Logout automatico*\\n*Esco dal client\n", status.name());
						break; // esco dal ciclo
					}
					status = StatusClient.EXIT;
					out.printf("%s,Esco dal client\n", status.name());
					break; // esco dal ciclo
				}

				// gestione degli altri comandi
				switch(parts[0])
				{
					case "help":
						// invio un messaggio di aiuto al client
						String helpMsg =
							"Comandi supportati:\n" +
							"  help\n" +
							"  exit\n" +
							"  register <username> <password>\n" +
							"  login <username> <password>\n" +
							"  logout <username>\n" +
							"  searchHotel <nomeHotel> <città>\n" +
							"  searchAllHotels <città>\n" +
							"  insertReview <nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>\n" +
							"  showMyBadges\n" +
							"  myRankings";
						helpMsg = helpMsg.replace("\n", "*\\n*");
						out.printf("%s,%s\n", status.name(), helpMsg);
						break;
					case "login":
						if(login(parts) != 0)
							break; // login fallito, esco dallo switch

						/* Se il login è stato effettuato con successo, il prossimo messaggio del client
						 * conterrà le città che vuole seguire per riceverne gli aggiornamenti sulla
						 * classifica.
						 * Le città sono richieste tutte su una riga, con uno spazio tra una e l'altra. */

						// leggo le città inviate dal client
						String secondLine = in.readLine();
						String[] secondParts = secondLine.split(" ");

						// invio al client un messaggio che contiene le città seguite correttamente,
						// ovvero quelle che, tra quelle che ha inserito, esistono in 'hotelsByCityMap'
						StringBuilder followedCities = new StringBuilder();
						for(int i = 0; i < secondParts.length; i++)
							if(hotelsByCityMap.containsKey(secondParts[i]))
								followedCities.append(secondParts[i] + " ");
						if(followedCities.length() == 0)
							out.printf("%s,Città seguite: nessuna\n", status.name());
						else
							out.printf("%s,Città seguite: %s\n", status.name(), followedCities);
						break;
					case "logout":
						logout(parts, false);
						break;
					case "searchHotel":
						searchHotel(parts);
						break;
					case "searchAllHotels":
						searchAllHotels(parts);
						break;
					case "insertReview":
						insertReview(parts);
						break;
					case "showMyBadges":
						showMyBadges(parts);
						break;
					default:
						out.printf("%s,Errore: comando non valido\n", status.name());
						break;
				}
			}

			in.close();
			out.close();
			socket.close();
		}
		catch(Exception e)
		{
			System.err.printf("[WORKER] Errore: %s\n", e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Tenta di effettuare il login dell'utente.
	 * @param parts array di stringhe contenente il comando di login,
	 * formato atteso: {"login", "username", "password"}
	 * @return 0 se il login ha successo, -1 se si verifica un errore
	 */
	private int login(String[] parts) throws NoSuchAlgorithmException
	{
		if(parts.length != 3)
		{
			out.printf("%s,Errore, usare: login <username> <password>\n", status.name());
			return -1;
		}
		if(status == StatusClient.USER_LOGGED)
		{
			out.printf("%s,Errore: login già effettuato\n", status.name());
			return -1;
		}

		String username = parts[1];
		String password = parts[2];
		User user = usersMap.get(username);
		if(user == null)
		{
			out.printf("%s,Errore: username non registrato\n", status.name());
			return -1;
		}
		// verifico la correttezza della password
		if(!PasswordUtils.verifyPassword(password, user.getHashedPassword(), user.getSalt()))
		{
			out.printf("%s,Errore: password sbagliata\n", status.name());
			return -1;
		}

		usernameLogged.append(username); // salvo l'username dell'utente che ha fatto il login
		status = StatusClient.USER_LOGGED; // setto il nuovo stato da inviare al client
		out.printf("%s,Login avvenuto con successo*\\n*"
			+ "Inserisci le città di cui vuoi seguire la classifica "
			+ "(tutte nella prossima riga, ognuna seguita da uno spazio)\n", status.name());
		return 0; // successo
	}

	/**
	 * Tenta di effettuare il logout dell'utente.
	 * @param parts array di stringhe contenente il comando di logout,
	 * formato atteso: {"logout", "username"}
	 * @param automaticLogout true se il logout deve essere effettuato in automatico (quando l'utente
	 * loggato invia il comando 'exit'), false quando l'utente chiede esplicitamente di fare il logout
	 */
	private void logout(String[] parts, boolean automaticLogout)
	{
		if(parts.length != 2)
		{
			out.printf("%s,Errore, usare: logout <username>\n", status.name());
			return ;
		}
		if(status != StatusClient.USER_LOGGED)
		{
			out.printf("%s,Errore: operazione non consentita prima del login\n", status.name());
			return ;
		}
		String username = parts[1];
		if(!username.equals(usernameLogged.toString()))
		{
			out.printf("%s,Errore: username sbagliato\n", status.name());
			return ;
		}

		usernameLogged.setLength(0); // cancello l'username dell'utente che era loggato
		status = StatusClient.USER_NOT_LOGGED; // setto il nuovo stato da inviare al client

		// invio il messaggio di risposta solo quando è stato l'utente a chiedere esplicitamente il logout
		if(!automaticLogout)
			out.printf("%s,Hai effettuato il logout\n", status.name());
	}

	/**
	 * Cerca e invia al client i dati dell'hotel richiesto.
	 * @param parts array di stringhe contenente il comando searchHotel,
	 * formato atteso: {"searchHotel", "nomeHotel", "città"}, il nome dell'hotel può contenere spazi,
	 * mentre "città" è sempre una singola stringa senza spazi. <p>
	 * e.g. Se il comando inserito è {@code searchHotel Hotel Milano 7 Milano}, parts conterrà
	 * {"searchHotel", "Hotel", "Milano", "7", "Milano"}
	 */
	private void searchHotel(String[] parts)
	{
		// il nome della città è l'ultima stringa
		String city = parts[parts.length - 1];
		if(city == null || city.isBlank())
		{
			out.printf("%s,Errore, usare: searchHotel <nomeHotel> <città>\n", status.name());
			return ;
		}

		// ricostruisco il nome dell'hotel prendendo le stringhe dopo 'searchHotel' e prima di 'città'
		StringBuilder hotelNameBuilder = new StringBuilder();
		for(int i = 1; i < parts.length - 1; i++)
		{
			hotelNameBuilder.append(parts[i]);
			if(i < parts.length - 2)
				hotelNameBuilder.append(" ");
		}
		String hotelName = hotelNameBuilder.toString();
		if(hotelName == null || hotelName.isBlank())
		{
			out.printf("%s,Errore, usare: searchHotel <nomeHotel> <città>\n", status.name());
			return ;
		}

		// controllo se la città esiste in 'hotelsByCityMap'
		if(!hotelsByCityMap.containsKey(city))
		{
			out.printf("%s,Errore: %s non è una città capoluogo italiana\n", status.name(), city);
			return ;
		}

		// cerco il nome dell'hotel richiesto dal client nella lista di 'hotelsByCityMap' con chiave 'city'
		CopyOnWriteArrayList<Hotel> hotelsInCity = hotelsByCityMap.get(city);
		if(hotelsInCity != null)
			for(Hotel hotel : hotelsInCity)
				if(hotel.getName().equals(hotelName))
				{
					// ho trovato l'hotel, invio i dati al client
					String msg = "*\\n*" + hotel.getName() + "*\\n*"
						+ "  \"" + hotel.getDescription() + "\"*\\n*"
						+ "  phone=" + hotel.getPhone() + "*\\n*"
						+ "  services=" + hotel.getServices() + "*\\n*"
						+ "  rate=" + String.format(Locale.US, "%.2f", hotel.getRate()) + "*\\n*"
						+ "  ratings=" + hotel.getRatings().toStringWithApproximation() + "*\\n*";
					out.printf("%s,%s\n", status.name(), msg);
					return ;
				}
		// se arrivo qui vuol dire che l'hotel non è stato trovato
		out.printf("%s,Hotel %s non trovato a %s\n", status.name(), hotelName, city);
	}

	/**
	 * Cerca e invia al client i dati di tutti gli hotel presenti nella città richiesta.
	 * @param parts array di stringhe contenente il comando searchAllHotels,
	 * formato atteso: {"searchAllHotels", "città"}, "città" è sempre una singola stringa senza spazi
	 */
	private void searchAllHotels(String[] parts)
	{
		if(parts.length != 2)
		{
			out.printf("%s,Errore, usare: searchAllHotels <città>\n", status.name());
			return ;
		}

		String city = parts[1];
		if(city == null || city.isBlank())
		{
			out.printf("%s,Errore, usare: searchAllHotels <città>\n", status.name());
			return ;
		}
		if(!hotelsByCityMap.containsKey(city))
		{
			out.printf("%s,Errore: %s non è una città capoluogo italiana\n", status.name(), city);
			return ;
		}
		if(hotelsByCityMap.get(city).isEmpty())
		{
			out.printf("%s,Nessun hotel a %s\n", status.name(), city);
			return ;
		}

		// costruisco una stringa che contiene i dati di tutti gli hotel presenti nella città 'city'
		StringBuilder msgBuilder = new StringBuilder("*\\n*");
		CopyOnWriteArrayList<Hotel> hotelsInCity = hotelsByCityMap.get(city);
		hotelsInCity.sort(Comparator.comparingDouble(Hotel::getRate).reversed());
		int i = 0;
		for(Hotel hotel : hotelsInCity)
		{
			msgBuilder.append("(" + (i + 1) + ") " + hotel.getName() + "*\\n*")
				.append("  \"" + hotel.getDescription() + "\"*\\n*")
				.append("  phone=" + hotel.getPhone() + "*\\n*")
				.append("  services=" + hotel.getServices() + "*\\n*")
				.append("  rate=" + String.format(Locale.US, "%.2f", hotel.getRate()) + "*\\n*")
				.append("  ratings=" + hotel.getRatings().toStringWithApproximation() + "*\\n*");
			if(i < hotelsInCity.size() - 1)
				msgBuilder.append("*\\n*");
			i++;
		}
		// invio la risposta al client
		out.printf("%s,%s\n", status.name(), msgBuilder.toString());
	}

	/**
	 * Tenta di inserire la recensione di un hotel che l'utente loggato ha chiesto di inserire e gli
	 * comunica l'esito dell'operazione.
	 * @param parts array di stringhe contenente il comando insertReview, formato atteso:
	 * {"insertReview", "nomeHotel", "città", "rate", "cleaning", "position", "services", "quality"};
	 * il nome dell'hotel può contenere spazi; "città" è sempre una singola stringa senza spazi;
	 * "rate", "cleaning", "position", "services", "quality" sono interi compresi tra 0 e 5 che
	 * rappresentano rispettivamente il punteggio sintetico e i punteggi relativi alle categorie. <p>
	 * e.g. Se il comando inserito è {@code insertReview Hotel Milano 7 Milano 4 5 4 4 3}, parts conterrà
	 * {"insertReview", "Hotel", "Milano", "7", "Milano", "4", "5", "4", "4", "3"}
	 */
	private void insertReview(String[] parts)
	{
		if(status != StatusClient.USER_LOGGED)
		{
			out.printf("%s,Errore: operazione non consentita prima del login\n", status.name());
			return ;
		}
		if(parts.length < 8) // almeno 8 stringhe per come è fatto il comando
		{
			out.printf("%s,Errore, usare: insertReview " +
				"<nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>\n", status.name());
			return ;
		}

		// parsing dei punteggi
		int rate, cleaning, position, services, quality;
		try
		{
			rate = Integer.parseInt(parts[parts.length - 5]);
			cleaning = Integer.parseInt(parts[parts.length - 4]);
			position = Integer.parseInt(parts[parts.length - 3]);
			services = Integer.parseInt(parts[parts.length - 2]);
			quality = Integer.parseInt(parts[parts.length - 1]);
		}
		catch(NumberFormatException e)
		{
			out.printf("%s,Errore, usare: insertReview " +
				"<nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>\n", status.name());
			return ;
		}

		// controllo se ogni punteggio è compreso tra 0 e 5
		int[] check05 = {rate, cleaning, position, services, quality};
		for(int n : check05)
			if(n < 0 || n > 5)
			{
				out.printf("%s,Errore: ogni punteggio deve essere compreso tra 0 e 5\n", status.name());
				return ;
			}

		// il nome della città è in posizione [parts.length - 6]
		String city = parts[parts.length - 6];
		if(city == null || city.isBlank())
		{
			out.printf("%s,Errore, usare: insertReview " +
				"<nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>\n", status.name());
			return ;
		}

		// ricostruisco il nome dell'hotel prendendo le stringhe dopo 'insertReview' e prima di 'città'
		StringBuilder hotelNameBuilder = new StringBuilder();
		for(int i = 1; i < parts.length - 6; i++)
		{
			hotelNameBuilder.append(parts[i]);
			if(i < parts.length - 7)
				hotelNameBuilder.append(" ");
		}
		String hotelName = hotelNameBuilder.toString();
		if(hotelName == null || hotelName.isBlank())
		{
			out.printf("%s,Errore, usare: insertReview " +
				"<nomeHotel> <città> <rate> <cleaning> <position> <services> <quality>\n", status.name());
			return ;
		}

		// controllo se la città esiste in 'hotelsByCityMap'
		if(!hotelsByCityMap.containsKey(city))
		{
			out.printf("%s,Errore: %s non è una città capoluogo italiana\n", status.name(), city);
			return ;
		}

		// controllo se l'hotel 'hotelName' esiste nella città 'city'
		CopyOnWriteArrayList<Hotel> hotelsInCity = hotelsByCityMap.get(city);
		if(hotelsInCity.isEmpty() || hotelsInCity == null)
		{
			out.printf("%s,Errore: nessun hotel a %s\n", status.name(), city);
			return ;
		}
		boolean found = false;
		for(Hotel hotel : hotelsInCity)
			if(hotel.getName().equals(hotelName))
			{
				found = true;
				break;
			}
		if(!found)
		{
			out.printf("%s,Errore: non esiste l'hotel %s a %s\n", status.name(), hotelName, city);
			return ;
		}

		LocalDateTime currentDateTime = LocalDateTime.now();
		String key = hotelName + "_" + city;

		// l'utente può recensire più volte lo stesso hotel a patto che siano passati 'sameReviewerSameHotelPeriod' secondi
		if(reviewsMap.get(key) != null)
			for(Review review : reviewsMap.get(key))
			{
				if(review.getReviewer().equals(usernameLogged.toString()))
					if(Duration.between(LocalDateTime.parse(review.getDateTime()), currentDateTime).getSeconds() < sameReviewerSameHotelPeriod)
					{
						out.printf("%s,Errore: puoi recensire più volte lo stesso hotel dopo almeno %d secondi\n",
							status.name(), sameReviewerSameHotelPeriod);
						return ;
					}
			}

		// inserisco la recensione in 'reviewsMap'
		reviewsMap.compute(key, (k, list) -> {
			if(list == null)
				list = new CopyOnWriteArrayList<>();
			list.add(new Review(
				usernameLogged.toString(), hotelName, city, rate,
				new Ratings(cleaning, position, services, quality),
				currentDateTime.toString()));
			return list;
		});

		// incremento di uno il numero di recensioni inserite dall'utente
		usersMap.get(usernameLogged.toString()).incrNumReviews();

		// invio la risposta di avvenuto inserimento al client
		out.printf("%s,Recensione inserita correttamente\n", status.name());
	}

	/**
	 * Mostra all'utente loggato il distintivo corrispondente al suo livello di esperienza. Il distintivo
	 * è una stringa.
	 * @param parts array di stringhe contenente il comando showMyBadges,
	 * formato atteso: {"showMyBadges"}
	 */
	private void showMyBadges(String[] parts)
	{
		if(parts.length != 1)
		{
			out.printf("%s,Errore, usare: showMyBadges\n", status.name());
			return ;
		}
		if(status != StatusClient.USER_LOGGED)
		{
			out.printf("%s,Errore: operazione non consentita prima del login\n", status.name());
			return ;
		}

		// ottengo il livello di esperienza dell'utente loggato e trovo il distintivo corrispondente
		int expLvl = usersMap.get(usernameLogged.toString()).getExperienceLevel();
		String msg = null;
		if(expLvl == 0)
			msg = "Nessun distintivo";
		else if(expLvl == 1)
			msg = "--- Recensore ---";
		else if(expLvl == 2)
			msg = "--- Recensore Esperto ---";
		else if(expLvl == 3)
			msg = "--- Contributore ---";
		else if(expLvl == 4)
			msg = "--- Contributore Esperto ---";
		else if(expLvl == 5)
			msg = "--- Contributore Super ---";

		// invio la risposta al client
		out.printf("%s,%s\n", status.name(), msg);
	}
}