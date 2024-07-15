package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

import entities.StatusRegistration;

public interface UserHashMapInterface extends Remote
{
	/**
	 * Metodo esportato dal server e invocato dal client. <p>
	 * Registra un nuovo utente inserendolo nella hash map degli utenti registrati.
	 * @param username
	 * @param password
	 * @return {@code BLANK_USERNAME} se l'username è vuoto, {@code BLANK_PASSWORD} se la password è vuota,
	 * {@code USERNAME_TAKEN} se l'username è già in uso, {@code SUCCESS} se il nuovo utente è stato
	 * registrato correttamente, {@code TOO_LONG} se l'username o la password superano la lunghezza massima consentita.
	 */
	public StatusRegistration register(String username, String password) throws RemoteException, NoSuchAlgorithmException;
}