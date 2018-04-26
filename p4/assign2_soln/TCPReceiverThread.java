import java.io.*;
import java.net.*;

public class TCPReceiverThread extends TCPThread {

	protected PrintWriter out = null;

	public TCPReceiverThread(int port, int mtu, int sws){
		try{
			socket = new DatagramSocket(port);
		} catch (Exception e) {
			//do nothing
			System.err.println("Error in building a TCP socket!");
		}
		this.port = port;
		this.mtu = mtu;
		this.sws = sws;

		try {
			out = new PrintWriter(new FileWriter("two-liners.txt"));
		} catch (IOException e) {
			System.err.println("Could not write to the file given the filename.");
		}
		this.incomingMonitor = new IncomingMonitor();
	}

	public void run() {
		System.out.println("listening to a SYN packet for building connection");
		// Start incoming monitor
		incomingMonitor.start();
	}
}