import java.io.*;
import java.net.*;
import java.util.*;

public class TCPReceiverThread extends Thread {

	protected DatagramSocket socket = null;
	protected BufferedReader out = null;
	protected boolean moreQuotes = true;

	int port;
	int mtu;
	int sws;

	public TCPReceiverThread(){
	}

	public TCPReceiverThread(int port, int mtu, int sws){
		socket = new DatagramSocket(port);
		this.port = port;
		this.mtu = mtu;
		this.sws = sws;

		try {
			out = new PrintWriter(new FileWriter("two-liners.txt"));
		} catch (IOException e) {
			System.err.println("Could not write to the file given the filename.");
		}
	}

	public void run() {

		while (moreQuotes) {
			try {
				byte[] buf = new byte[256];

				// receive request
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);

				// figure out response
				String dString = null;
				if (in == null)
					dString = new Date().toString();
				else
					dString = getNextQuote();

				buf = dString.getBytes();

				// send the response to the client at "address" and "port"
				InetAddress address = packet.getAddress();
				int port = packet.getPort();
				packet = new DatagramPacket(buf, buf.length, address, port);
				socket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
				moreQuotes = false;
			}
		}
		socket.close();
	}

	protected String getNextQuote() {
		String returnValue = null;
		try {
			if ((returnValue = in.readLine()) == null) {
				in.close();
				moreQuotes = false;
				returnValue = "No more quotes. Goodbye.";
			}
		} catch (IOException e) {
			returnValue = "IOException occurred in server.";
		}
		return returnValue;
	}
}