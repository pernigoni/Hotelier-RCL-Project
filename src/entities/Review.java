package entities;

/**
 * Classe che rappresenta una recensione di un hotel.
 */
public class Review
{
	private String hotelName; // nome dell'hotel recensito
	private String city; // città in cui si trova l'hotel recensito
	private String reviewer; // username dell'utente che ha fatto la recensione

	private int rate; // punteggio sintetico, da 0 a 5
	private Ratings ratings; // punteggi delle categorie (cleaning, position, services, quality), da 0 a 5

	private String dateTime; // data e ora in cui è stata inserita la recensione

	public Review(String reviewer, String hotelName, String city, int rate, Ratings ratings, String dateTime)
	{
		this.reviewer = reviewer;
		this.hotelName = hotelName;
		this.city = city;
		this.rate = rate;
		this.ratings = ratings;
		this.dateTime = dateTime;
	}

	public String getReviewer() {
		return reviewer;
	}

	public String getHotelName() {
		return hotelName;
	}

	public String getCity() {
		return city;
	}

	public int getRate() {
		return rate;
	}

	public Ratings getRatings() {
		return new Ratings(ratings);
	}

	public String getDateTime() {
		return dateTime;
	}

	public String toString()
	{
		return "Review{" +
			"hotelName=\"" + hotelName + "\"" +
			", city=\"" + city + "\"" +
			", reviewer=\"" + reviewer + "\"" +
			", rate=" + rate +
			", ratings=" + ratings +
			", dateTime=" + dateTime +
			"}";
	}
}