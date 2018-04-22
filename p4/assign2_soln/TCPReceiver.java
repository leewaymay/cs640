import java.io.*;
import java.net.*;
import java.util.*;

public class TCPReceiver {
	int port;
	int mtu;
	int sws;
	InetAddress address;
	DatagramSocket socket = null;
	DatagramPacket packet;

	public TCPReceiver(int port, int mtu, int sws) {
		this.port = port;
		this.mtu = mtu;
		this.sws = sws;
	}

	public static void main(String[] args) throws IOException {

		if (args.length != 1) {
			System.out.println("Usage: java QuoteClient <hostname>");
			return;
		}

		// get a datagram socket
		DatagramSocket socket = new DatagramSocket();

		// send request
		byte[] buf = new byte[256];
		InetAddress address = InetAddress.getByName(args[0]);
		System.out.println("The host address is " + address.toString());
		DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 4445);
		socket.send(packet);

		// get response
		packet = new DatagramPacket(buf, buf.length);
		socket.receive(packet);

		// display response
		String received = new String(packet.getData(), 0, packet.getLength());
		System.out.println("Quote of the Moment: " + received);

		socket.close();
	}
}
