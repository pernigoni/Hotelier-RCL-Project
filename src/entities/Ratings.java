package entities;

import java.util.Locale;

/**
 * Classe che rappresenta i punteggi relativi alle categorie cleaning, position, services, quality.
 */
public class Ratings
{
	private double cleaning;
	private double position;
	private double services;
	private double quality;

	public Ratings(double cleaning, double position, double services, double quality)
	{
		this.cleaning = cleaning;
		this.position = position;
		this.services = services;
		this.quality = quality;
	}

	public Ratings(Ratings other) // copia
	{
		this.cleaning = other.cleaning;
		this.position = other.position;
		this.services = other.services;
		this.quality = other.quality;
	}

	public double getCleaning() {
		return cleaning;
	}

	public void setCleaning(double cleaning) {
		this.cleaning = cleaning;
	}

	public double getPosition() {
		return position;
	}

	public void setPosition(double position) {
		this.position = position;
	}

	public double getServices() {
		return services;
	}

	public void setServices(double services) {
		this.services = services;
	}

	public double getQuality() {
		return quality;
	}

	public void setQuality(double quality) {
		this.quality = quality;
	}

	public String toString()
	{
		return "[" + cleaning + ", " + position + ", " + services + ", " + quality + "]";
	}

	public String toStringWithApproximation()
	{
		return "["
			+ String.format(Locale.US, "%.2f", cleaning) + ", "
			+ String.format(Locale.US, "%.2f", position) + ", "
			+ String.format(Locale.US, "%.2f", services) + ", "
			+ String.format(Locale.US, "%.2f", quality) + "]";
	}
}