package server;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.stream.JsonWriter;

import entities.Hotel;
import entities.Review;
import entities.User;

/**
 * Classe che rappresenta il task che gestisce il salvataggio dei dati in formato json.
 */
public class DataPersistenceTask implements Runnable
{
	private final String usersJsonPath;
	private final ConcurrentHashMap<String, User> usersMap;

	private final String reviewsJsonPath;
	private final ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap;

	private final String hotelsJsonPath;
	private final ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap;

	public DataPersistenceTask(
		ConcurrentHashMap<String, User> usersMap, String usersJsonPath,
		ConcurrentHashMap<String, CopyOnWriteArrayList<Review>> reviewsMap, String reviewsJsonPath,
		ConcurrentHashMap<String, CopyOnWriteArrayList<Hotel>> hotelsByCityMap, String hotelsJsonPath)
	{
		this.usersMap = usersMap;
		this.usersJsonPath = usersJsonPath;
		this.reviewsMap = reviewsMap;
		this.reviewsJsonPath = reviewsJsonPath;
		this.hotelsByCityMap = hotelsByCityMap;
		this.hotelsJsonPath = hotelsJsonPath;
	}

	public void run()
	{
		try
		{
			persistUsers(); // salvo i dati degli utenti
			persistReviews(); // salvo i dati delle recensioni
			persistHotels(); // salvo i dati degli hotel
		}
		catch(Exception e)
		{
			System.err.println("[DATA-PERSISTENCE] Errore: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Salva i dati degli utenti (presenti in usersMap) in un file json. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private void persistUsers() throws Exception
	{
		try(JsonWriter writer = new JsonWriter(new FileWriter(usersJsonPath)))
		{
			writer.setIndent("	");
			writer.beginArray(); // [

			// scorro la hash map degli utenti
			usersMap.forEach((key, user) -> {
				try
				{
					writer.beginObject(); // {
					writer.name("username").value(user.getUsername());
					writer.name("salt").value(user.getSalt());
					writer.name("hashedPassword").value(user.getHashedPassword());
					writer.name("experienceLevel").value(user.getExperienceLevel());
					writer.name("numReviews").value(user.getNumReviews());
					writer.endObject(); // }
				}
				catch(IOException e)
				{
					throw new RuntimeException(e);
				}
			});
			writer.endArray(); // ]
			writer.flush();
		}
	}

	/**
	 * Salva i dati delle recensioni (presenti in reviewsMap) in un file json. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private void persistReviews() throws Exception
	{
		try(JsonWriter writer = new JsonWriter(new FileWriter(reviewsJsonPath)))
		{
			writer.setIndent("	");
			writer.beginArray(); // [

			// scorro la hash map delle recensioni
			reviewsMap.forEach((key, list) -> {
				// scorro la lista di recensioni dell'hotel identificato da 'key'
				for(Review review : list)
				{
					try
					{
						writer.beginObject(); // {
						writer.name("hotelName").value(review.getHotelName());
						writer.name("city").value(review.getCity());
						writer.name("reviewer").value(review.getReviewer());
						writer.name("rate").value(review.getRate());
						writer.name("ratings");
							writer.beginObject(); // {
							writer.name("cleaning").value(review.getRatings().getCleaning());
							writer.name("position").value(review.getRatings().getPosition());
							writer.name("services").value(review.getRatings().getServices());
							writer.name("quality").value(review.getRatings().getQuality());
							writer.endObject(); // }
						writer.name("dateTime").value(review.getDateTime());
						writer.endObject(); // }
					}
					catch(IOException e)
					{
						throw new RuntimeException(e);
					}
				}
			});
			writer.endArray(); // ]
			writer.flush();
		}
	}

	/**
	 * Salva i dati degli hotel (presenti in hotelsByCityMap) in un file json. <p>
	 * Utilizza il meccanismo Gson Streaming API.
	 */
	private void persistHotels() throws Exception
	{
		try(JsonWriter writer = new JsonWriter(new FileWriter(hotelsJsonPath)))
		{
			writer.setIndent("	");
			writer.beginArray(); // [

			// scorro la hash map degli hotel
			hotelsByCityMap.forEach((city, list) -> {
				// scorro la lista di hotel presenti nella città 'city'
				for(Hotel hotel : list)
				{
					try
					{
						writer.beginObject(); // {
						writer.name("id").value(hotel.getId());
						writer.name("name").value(hotel.getName());
						writer.name("description").value(hotel.getDescription());
						writer.name("city").value(hotel.getCity());
						writer.name("phone").value(hotel.getPhone());
						writer.name("services");
							writer.beginArray(); // [
							for(String service : hotel.getServices())
								writer.value(service);
							writer.endArray(); // ]
						writer.name("rate").value(hotel.getRate());
						writer.name("ratings");
							writer.beginObject(); // {
							writer.name("cleaning").value(hotel.getRatings().getCleaning());
							writer.name("position").value(hotel.getRatings().getPosition());
							writer.name("services").value(hotel.getRatings().getServices());
							writer.name("quality").value(hotel.getRatings().getQuality());
							writer.endObject(); // }
						writer.endObject(); // }
					}
					catch(IOException e)
					{
						throw new RuntimeException(e);
					}
				}
			});
			writer.endArray(); // ]
			writer.flush();
		}
	}
}