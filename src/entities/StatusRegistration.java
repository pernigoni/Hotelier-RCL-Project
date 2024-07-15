package entities;

/**
 * Valore di ritorno per il metodo remoto {@code register(username, password)}.
 */
public enum StatusRegistration
{
	SUCCESS,
	USERNAME_TAKEN,
	BLANK_PASSWORD,
	BLANK_USERNAME,
	TOO_LONG
}