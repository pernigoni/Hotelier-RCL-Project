package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Classe che rappresenta il thread che gestisce la ricezione, da un multicast socket, delle notifiche
 * riguardanti il cambiamento della prima posizione delle classifiche locali. <p>
 * Ogni pacchetto UDP ricevuto contiene una stringa che viene inserita in una coda.
 */
public class LocalRankingReceiver extends Thread
{
	private String multicastAddress;
	private int UDPport;
	private LinkedBlockingQueue<String> notifyQueue; // coda per gestire le notifiche in modo asincrono

	private final int size = 1024; // dimensione del buffer per la ricezione dei messaggi
	private volatile boolean running = true; // flag per uscire dal ciclo
	private MulticastSocket multicastSocket;
	private InetAddress group;

	public LocalRankingReceiver(String multicastAddress, int UDPport, LinkedBlockingQueue<String> notifyQueue)
	{
		this.multicastAddress = multicastAddress;
		this.UDPport = UDPport;
		this.notifyQueue = notifyQueue;
	}

	@SuppressWarnings("deprecation")
	public void run()
	{
		try
		{
			// apro un multicast socket per la ricezione dei messaggi
			multicastSocket = new MulticastSocket(UDPport);

			// ottengo l'indirizzo del gruppo e ne controllo la validità
			group = InetAddress.getByName(multicastAddress);
			if(!group.isMulticastAddress())
				throw new IllegalArgumentException("Indirizzo di multicast non valido " + group.getHostAddress());

			// mi unisco al gruppo di multicast
			multicastSocket.joinGroup(group);

			// setto il timeout a un 1 secondo
			// se receive() non riceve nulla entro 1 secondo solleva una SocketTimeoutException
			multicastSocket.setSoTimeout(1000);

			// ricevo i messaggi dal server
			while(running)
			{
				try
				{
					DatagramPacket packet = new DatagramPacket(new byte[size], size);

					// ricevo il pacchetto e lo inserisco nella coda
					multicastSocket.receive(packet);
					String received = new String(packet.getData(), packet.getOffset(), packet.getLength());
					notifyQueue.add(received);
				}
				catch(SocketTimeoutException e)
				{
					// ignoro il timeout scaduto
				}
				catch(IOException e)
				{
					if(running)
					{
						System.err.println("[LOCAL-RANKING-RECEIVER] Errore: " + e.getMessage());
						e.printStackTrace();
					}
					/* if(!running)
					 * SocketException, socket chiuso mentre il thread è bloccato sulla receive().
					 * InterruptedException, thread interrotto mentre è bloccato sulla receive(). */
					break;
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("[LOCAL-RANKING-RECEIVER] Errore: " + e.getMessage());
			e.printStackTrace();
		}
		finally
		{
			if(!multicastSocket.isClosed() && multicastSocket != null)
				try
				{	// lascio il gruppo di multicast e chiudo il socket
					multicastSocket.leaveGroup(group);
					multicastSocket.close();
				}
				catch(IOException e)
				{ }
		}
	}

	/**
	 * Arresta il thread.
	 */
	@SuppressWarnings("deprecation")
	public void shutdown()
	{
		running = false;
		try
		{
			multicastSocket.leaveGroup(group);
		}
		catch(IOException e)
		{ }
		multicastSocket.close();
		this.interrupt();
	}
}