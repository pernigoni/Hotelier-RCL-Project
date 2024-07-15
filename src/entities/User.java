package entities;

/**
 * Classe che rappresenta un utente registrato.
 */
public class User
{
	private String username;
	private String salt; // valore di salt da aggiungere alla password
	private String hashedPassword; // hash della password
	private int experienceLevel; // livello di esperienza dell'utente
	private int numReviews; // numero di recensioni inserite dall'utente

	public User(String username, String salt, String hashedPassword)
	{
		this.username = username;
		this.salt = salt;
		this.hashedPassword = hashedPassword;
		this.experienceLevel = 0; // livello iniziale
		this.numReviews = 0; // numero di recensioni iniziale
	}

	public String getUsername() {
		return username;
	}

	public String getSalt() {
		return salt;
	}

	public String getHashedPassword() {
		return hashedPassword;
	}

	public int getExperienceLevel() {
		return experienceLevel;
	}

	public int getNumReviews() {
		return numReviews;
	}

	/**
	 * Incrementa di uno il numero di recensioni inserite dall'utente e, in base ad esso, aggiorna il
	 * suo livello di esperienza.
	 */
	public synchronized void incrNumReviews()
	{
		numReviews++;
		if(numReviews >= 20)
			experienceLevel = 5; // contributore super
		else if(numReviews >= 15)
			experienceLevel = 4; // contributore esperto
		else if(numReviews >= 10)
			experienceLevel = 3; // contributore
		else if(numReviews >= 5)
			experienceLevel = 2; // recensore esperto
		else if(numReviews >= 1)
			experienceLevel = 1; // recensore
	}
}