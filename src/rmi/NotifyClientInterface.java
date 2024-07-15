package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.CopyOnWriteArrayList;

public interface NotifyClientInterface extends Remote
{
	/**
	 * Metodo esportato dal client. <p>
	 * Metodo invocato dal server per notificare la variazione di almeno uno tra i primi 3 posti di una
	 * classifica locale ad un client remoto.
	 * @param city città della classifica locale
	 * @param hotelNames lista che contiene i nomi di, al massimo, i primi 3 hotel della nuova classifica
	 * in ordine decrescente di rate
	 */
	public void notifyUpdatedRanking(String city, CopyOnWriteArrayList<String> hotelNames) throws RemoteException;
}