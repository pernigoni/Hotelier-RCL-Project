package rmi;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotifyServerImpl extends RemoteObject implements NotifyServerInterface
{
	// lista dei client registrati (contiene gli stub)
	private List<NotifyClientInterface> clients;

	/**
	 * Crea un nuovo server (oggetto remoto).
	 */
	public NotifyServerImpl() throws RemoteException
	{
		super();
		clients = new ArrayList<NotifyClientInterface>();
	}

	public synchronized void registerForCallback(NotifyClientInterface clientInterface) throws RemoteException
	{
		if(!clients.contains(clientInterface))
			clients.add(clientInterface);
	}

	public synchronized void unregisterForCallback(NotifyClientInterface clientInterface) throws RemoteException
	{
		clients.remove(clientInterface);
	}

	/**
	 * Notifica la variazione della classifica con una callback per ogni client registrato.
	 * @param city città della classifica locale
	 * @param hotelNames lista che contiene i nomi di, al massimo, i primi 3 hotel della nuova classifica
	 * in ordine decrescente di rate
	 */
	public void update(String city, CopyOnWriteArrayList<String> hotelNames) throws RemoteException
	{
		doCallbacks(city, hotelNames);
	}

	/**
	 * Esegue le callback scorrendo la lista dei client registrati e invocando il metodo del client
	 * {@code notifyUpdatedRanking(city, hotelNames)}.
	 * @param city città della classifica locale
	 * @param hotelNames lista che contiene i nomi di, al massimo, i primi 3 hotel della nuova classifica
	 * in ordine decrescente di rate
	 */
	private synchronized void doCallbacks(String city, CopyOnWriteArrayList<String> hotelNames) throws RemoteException
	{
		Iterator<NotifyClientInterface> i = clients.iterator();
		while(i.hasNext())
		{
			NotifyClientInterface client = (NotifyClientInterface) i.next(); // stub
			client.notifyUpdatedRanking(city, hotelNames); // metodo del client
		}
		System.out.println("[NOTIFY-SERVER] Callback eseguite");
	}
}