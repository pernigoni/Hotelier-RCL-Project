package rmi;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotifyClientImpl extends RemoteObject implements NotifyClientInterface
{
	/* Riferimento alla hash map che contiene gli aggiornamenti delle classifiche locali.
	 * Ha come chiave la città della classifica locale e valore la lista che contiene i nomi di,
	 * al massimo, i primi 3 hotel della nuova classifica in ordine decrescente di rate. */
	private ConcurrentHashMap<String, CopyOnWriteArrayList<String>> updatedRankingMap;

	/**
	 * Crea un nuovo callback client (oggetto remoto).
	 */
	public NotifyClientImpl(ConcurrentHashMap<String, CopyOnWriteArrayList<String>> updatedRankingMap) throws RemoteException
	{
		super();
		this.updatedRankingMap = updatedRankingMap;
	}

	public void notifyUpdatedRanking(String city, CopyOnWriteArrayList<String> hotelNames) throws RemoteException
	{
		// inserisco la classifica aggiornata
		updatedRankingMap.put(city, hotelNames);
		// System.out.println(updatedRankingMap);
	}
}