package entities;

/**
 * Rappresenta lo stato dell'utente su un client. <p>
 * 
 * Può assumere uno dei seguenti valori: <p>
 * <ul>
 *   <li>{@code USER_NOT_LOGGED} - Nessun utente loggato sul client.</li>
 *   <li>{@code USER_LOGGED} - Utente loggato sul client.</li>
 *   <li>{@code EXIT} - L'utente, loggato o non loggato, vuole uscire dal sistema.</li>
 * </ul>
 */
public enum StatusClient
{
	USER_NOT_LOGGED,
	USER_LOGGED,
	EXIT
}