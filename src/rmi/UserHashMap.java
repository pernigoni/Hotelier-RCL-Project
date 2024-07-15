package rmi;

import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import entities.StatusRegistration;
import entities.User;
import utils.PasswordUtils;

public class UserHashMap implements UserHashMapInterface
{
	// hash map degli utenti registrati
	private ConcurrentHashMap<String, User> usersMap = new ConcurrentHashMap<>();

	public StatusRegistration register(String username, String password) throws RemoteException, NoSuchAlgorithmException
	{
		if(username == null || username.isBlank())
			return StatusRegistration.BLANK_USERNAME;

		if(password == null || password.isBlank())
			return StatusRegistration.BLANK_PASSWORD;

		if(username.length() > 32 || password.length() > 32)
			return StatusRegistration.TOO_LONG;

		String salt = PasswordUtils.generateSalt();
		String hashedPassword = PasswordUtils.hashPassword(password, salt);

		// se l'username è libero inserisco un nuovo utente in 'usersMap'
		User existingUser = usersMap.putIfAbsent(
			username, new User(username, salt, hashedPassword));
		if(existingUser != null)
			return StatusRegistration.USERNAME_TAKEN;

		return StatusRegistration.SUCCESS;
	}

	// metodo non esposto nell'interfaccia
	public ConcurrentHashMap<String, User> getUsersMap()
	{
		return usersMap;
	}
}