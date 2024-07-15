package utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtils
{
	/**
	 * Genera un valore di salt casuale.
	 * @return una stringa contenente il salt
	 */
	public static String generateSalt()
	{
		SecureRandom random = new SecureRandom();
		byte[] salt = new byte[16];
		random.nextBytes(salt);
		// converto l'array di byte in una stringa codificata in Base64
		return Base64.getEncoder().encodeToString(salt);
	}

	/**
	 * Crea un hash della password.
	 * @param password password inserita
	 * @param salt valore di salt
	 * @return una stringa contenente l'hash della password
	 */
	public static String hashPassword(String password, String salt) throws NoSuchAlgorithmException
	{
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		String saltedPassword = password + salt; // aggiungo il salt alla password
		byte[] hashedBytes = md.digest(saltedPassword.getBytes()); // calcolo l'hash
		// converto l'array di byte in una stringa codificata in Base64
		return Base64.getEncoder().encodeToString(hashedBytes);
	}

	/**
	 * Verifica se la password inserita è corretta.
	 * @param inputPassword password inserita
	 * @param storedHash hash della password salvato precedentemente
	 * @param storedSalt salt salvato precedentemente
	 * @return true se la password inserita è corretta, false se è sbagliata
	 */
	public static boolean verifyPassword(String inputPassword, String storedHash, String storedSalt) throws NoSuchAlgorithmException
	{
		// calcolo l'hash della password inserita usando il salt salvato e lo confronto con l'hash salvato
		String hashedInputPassword = hashPassword(inputPassword, storedSalt);
		return hashedInputPassword.equals(storedHash);
	}
}