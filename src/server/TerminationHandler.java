package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Classe che rappresenta il thread che gestisce la terminazione controllata del server. <p>
 * 
 * Chiude il server socket in modo da sbloccare il server bloccato sulla accept() e non accettare nuove
 * connessioni. <p>
 * Avvia la terminazione del pool di thread. Se esso non termina entro 'maxDelay' millisecondi, lo fa
 * terminare forzatamente. <p>
 */
public class TerminationHandler extends Thread
{
	private int maxDelay;
	private ExecutorService pool;
	private ServerSocket serverSocket;

	public TerminationHandler(int maxDelay, ExecutorService pool, ServerSocket serverSocket)
	{
		this.maxDelay = maxDelay;
		this.pool = pool;
		this.serverSocket = serverSocket;
	}

	public void run()
	{
		// avvio la procedura di terminazione del server
		System.out.println("[TERM-HANDLER] Avvio terminazione...");

		try
		{	// chiudo il server socket in modo tale da non accettare più nuove connessioni
			serverSocket.close();
		}
		catch(IOException e)
		{
			System.err.printf("[TERM-HANDLER] Errore: %s\n", e.getMessage());
		}

		// faccio terminare il pool di thread
		pool.shutdown();
		try
		{
			if(!pool.awaitTermination(maxDelay, TimeUnit.MILLISECONDS))
				pool.shutdownNow();
		}
		catch(InterruptedException e)
		{
			pool.shutdownNow();
		}
		System.out.println("[TERM-HANDLER] Terminato");
	}
}