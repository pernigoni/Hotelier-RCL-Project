package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyServerInterface extends Remote
{
	/**
	 * Registrazione per la callback (la callback è lo stub del client, oggetto che consente al server
	 * di invocare un metodo sul client).
	 */
	public void registerForCallback(NotifyClientInterface clientInterface) throws RemoteException;

	/**
	 * Cancella la registrazione per la callback (la callback è lo stub del client, oggetto che consente
	 * al server di invocare un metodo sul client).
	 */
	public void unregisterForCallback(NotifyClientInterface clientInterface) throws RemoteException;
}