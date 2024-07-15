package entities;

import java.util.List;

/**
 * Classe che rappresenta un hotel.
 */
public class Hotel implements Cloneable
{
	private int id; // id dell'hotel
	private String name; // nome dell'hotel
	private String description; // descrizione dell'hotel
	private String city; // città in cui si trova l'hotel
	private String phone; // numero di telefono dell'hotel
	private List<String> services; // lista di servizi offerti dall'hotel
	private double rate; // punteggio sintetico calcolato in base alle recensioni dell'hotel
	private Ratings ratings; // punteggi delle categorie calcolati in base alle recensioni dell'hotel

	public Hotel(int id, String name, String description, String city, String phone, List<String> services,
		double rate, Ratings ratings)
	{
		this.id = id;
		this.name = name;
		this.description = description;
		this.city = city;
		this.phone = phone;
		this.services = services;
		this.rate = rate;
		this.ratings = ratings;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getCity() {
		return city;
	}

	public String getPhone() {
		return phone;
	}

	public List<String> getServices() {
		return services;
	}

	public double getRate() {
		return rate;
	}

	public void setRate(double rate) {
		this.rate = rate;
	}

	public Ratings getRatings() {
		return new Ratings(ratings);
	}

	public void setRatings(Ratings ratings) {
		this.ratings = new Ratings(ratings);
	}

	public String toString()
	{
		return "Hotel{" +
			"id=" + id +
			", name=\"" + name + "\"" +
			", description=\"" + description + "\"" +
			", city=\"" + city + "\"" +
			", phone=\"" + phone + "\"" +
			", services=" + services +
			", rate=" + rate +
			", ratings=" + ratings +
			"}";
	}

	public Hotel clone()
	{
		try {
			return (Hotel) super.clone();
		}
		catch(CloneNotSupportedException e)
		{	// non dovrebbe mai succedere dato che ho implementato Cloneable
			throw new AssertionError();
		}
	}
}