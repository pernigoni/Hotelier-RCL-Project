package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import entities.Hotel;
import entities.Ratings;
import entities.Review;
import rmi.NotifyServerImpl;

/**
 * Classe che rappresenta il task che: <p>
 * 
 * 1) Ricalcola e aggiorna 'rate' e 'ratings' degli hotel. <p>
 * 
 * 2) Invia una notifica riguardante il nuovo eventuale primo in classifica di ogni classifica locale ai
 *    client iscritti al gruppo di multicast. <p>
 * 
 * 3) Se almeno uno tra i primi 3 di una classifica locale è cambiato, notifica la variazione della
 *    classifica con una callback RMI a tutti i client registrati.
 */
public class LocalRankingUpdater implements Runnable
{
	private InetAddress group;
	private int UDPport;
	private DatagramSocket datagramSocket;

	// riferimento alla hash map che ha come chiave 'nomeHotel_città' e valore la lista di recensioni di quell'hotel
	private ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap;

	// riferimento alla hash map che ha come chiave la città e valore la lista degli hotel presenti in quella città
	private ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap;

	// riferimento all'oggetto remoto per il servizio di notifica
	private NotifyServerImpl server;

	public LocalRankingUpdater(
		DatagramSocket datagramSocket, InetAddress group, int UDPport,
		ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap,
		ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap,
		NotifyServerImpl server)
	{
		this.datagramSocket = datagramSocket;
		this.group = group;
		this.UDPport = UDPport;
		this.reviewsMap = reviewsMap;
		this.hotelsByCityMap = hotelsByCityMap;
		this.server = server;
	}

	public void run()
	{
		// deep copy delle prime 3 posizioni di ogni città di hotelsByCityMap
		// (serve per fare il confronto tra la vecchia e la nuova classifica)
		ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap_Old = new ConcurrentHashMap<>();
		hotelsByCityMap.forEach((city, list) -> {
			CopyOnWriteArrayList<Hotel> clonedList = new CopyOnWriteArrayList<>();
			int count = 0;
			for(Hotel hotel : list)
			{
				if(count == 3)
					break;
				clonedList.add(hotel.clone());
				count++;
			}
			hotelsByCityMap_Old.put(city, clonedList);
		});

		reviewsMap.forEach((key, list) -> {
			// calcolo il punteggio (rate) basato sui punteggi sintetici
			// delle recensioni dell'hotel identificato da 'key'
			CopyOnWriteArrayList<Review> listCopy = new CopyOnWriteArrayList<>(list);
			double totalScore = calculateTotalScore(listCopy);

			// calcolo le medie dei punteggi delle categorie (ratings)
			double avgCleaning = 0, avgPosition = 0, avgServices = 0, avgQuality = 0;
			double n = 1;
			for(Review review : listCopy)
			{
				// AVG_n = AVG_n-1 + (x_n - AVG_n-1) / n
				avgCleaning = avgCleaning + (review.getRatings().getCleaning() - avgCleaning) / n;
				avgPosition = avgPosition + (review.getRatings().getPosition() - avgPosition) / n;
				avgServices = avgServices + (review.getRatings().getServices() - avgServices) / n;
				avgQuality = avgQuality + (review.getRatings().getQuality() - avgQuality) / n;
				n++;
			}

			// reviewsMap ha come chiave 'nomeHotel_città'
			int lastUnderscore = key.lastIndexOf('_');
			String hotelName = key.substring(0, lastUnderscore);
			String city = key.substring(lastUnderscore + 1);

			// setto i nuovi valori di rate e ratings, appena ricalcolati,
			// relativi all'hotel identificato da 'key' in hotelsByCityMap
			CopyOnWriteArrayList<Hotel> hotelList = hotelsByCityMap.get(city);
			for(Hotel hotel : hotelList)
				if(hotel.getName().equals(hotelName))
				{
					hotel.setRate(totalScore);
					hotel.setRatings(new Ratings(avgCleaning, avgPosition, avgServices, avgQuality));
					break;
				}
		});

		hotelsByCityMap.forEach((city, list) -> {
			// ordino in modo decrescente 'list' (lista di hotel) in base al rate dell'hotel
			list.sort(Comparator.comparingDouble(Hotel::getRate).reversed());

			// se il primo in classifica è cambiato lo invio ai client iscritti al gruppo di multicast
			if(!list.isEmpty())
				// confronto gli id del nuovo e del vecchio hotel primo in classifica nella città 'city'
				if(list.get(0).getId() != hotelsByCityMap_Old.get(city).get(0).getId())
				{
					String msg = "[NOTIFICA] Il nuovo primo in classifica a " + city + " è: " + list.get(0).getName();
					byte[] content = msg.getBytes();
					DatagramPacket packet = new DatagramPacket(content, content.length, group, UDPport);
					try
					{	// invio il pacchetto
						datagramSocket.send(packet);
					}
					catch(IOException e)
					{
						System.err.println("[LOCAL-RANKING] Errore: " + e.getMessage());
						e.printStackTrace();
					}
				}

		// callback RMI //
			// lista che conterrà i nomi dei primi 3 hotel della nuova classifica in ordine decrescente di rate
			CopyOnWriteArrayList<String> hotelNames = new CopyOnWriteArrayList<>();
			boolean rankingChanged = false;
			for(int i = 0; i < Math.min(3, list.size()); i++)
			{
				if(list.get(i).getId() != hotelsByCityMap_Old.get(city).get(i).getId())
					rankingChanged = true;

				// inserisco il nome dell'hotel nella lista hotelNames
				hotelNames.add(list.get(i).getName());
			}
			// se almeno uno tra i primi 3 in classifica nella città 'city' è cambiato...
			if(rankingChanged)
				try
				{	// ...notifico la variazione della classifica con una callback a tutti i client registrati
					server.update(city, hotelNames);
				}
				catch(RemoteException e)
				{ }
		});
	}

	/**
	 * Calcola la qualità media di una lista di recensioni.
	 */
	public static double calculateAverageQuality(List<Review> reviews)
	{
		int total = 0;
		for(Review review : reviews)
			total += review.getRate();
		return (double) total / reviews.size();
	}

	/**
	 * Calcola il fattore di attualità di una recensione.
	 */
	public static double calculateRecencyFactor(Review review)
	{
		long daysDifference = ChronoUnit.DAYS.between(
			LocalDateTime.parse(review.getDateTime()), LocalDateTime.now());

		// peso inversamente proporzionale alla distanza temporale, con un limite minimo
		if(daysDifference >= 365)
			return 0.001;
		else
			return 1 - (double) daysDifference / 365;
	}

	/**
	 * Calcola il punteggio totale di una lista di recensioni.
	 */
	public static double calculateTotalScore(List<Review> reviews)
	{
		// qualità media
		double averageQuality = calculateAverageQuality(reviews);

		// quantità
		int quantity = reviews.size();

		// calcolo i pesi di attualità per ogni recensione
		List<Double> recencyWeights = new ArrayList<>();
		for(Review review : reviews)
			recencyWeights.add(calculateRecencyFactor(review));

		// media pesata dei punteggi usando pesi di attualità
		double weightedAvgRecency = 0;
		for(int i = 0; i < quantity; i++)
			weightedAvgRecency += reviews.get(i).getRate() * recencyWeights.get(i);
		weightedAvgRecency /= quantity;

		// combino qualità e quantità per il punteggio finale
		double totalScore = (averageQuality * 0.4) + (weightedAvgRecency * 0.4) + (quantity * 0.2);
		return totalScore;
	}
}