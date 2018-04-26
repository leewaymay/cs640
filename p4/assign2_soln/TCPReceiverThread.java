import java.io.*;
import java.net.*;
import java.util.*;

public class TCPReceiverThread extends Thread {

	protected DatagramSocket socket = null;
	protected PrintWriter out = null;
	protected boolean moreQuotes = true;

	int port;
	int mtu;
	int sws;

	public TCPReceiverThread(){
	}

	public TCPReceiverThread(int port, int mtu, int sws){
		try{
			socket = new DatagramSocket(port);
		} catch (Exception e) {
			//do nothing
		}

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
		String hostname = "10.0.1.101";
		// get a datagram socket
		try{
			socket = new DatagramSocket(port);
		} catch (Exception e) {
			//do nothing
		}
		// receive the first SYN packet
		while (true) {
			TCPPacket tcpPacket = new TCPPacket(mtu);
			//TODO Error Buffer exceeded
			byte[] buf = tcpPacket.serialize();
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			try {
				socket.receive(packet);
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("Error in receive a udp packet");
			}
			byte[] received = packet.getData();
			tcpPacket.deserialize(received);
			// TODO compute checksum
			if (tcpPacket.isSYN()) {
				System.out.println("received an SYN application!");
				break;
			} else {
				System.out.println("received un undesired packet!");
			}
		}
		// send request
		byte[] buf = new byte[256];
		InetAddress address = null;
		try {
			address = InetAddress.getByName(hostname);
			System.out.println("The host address is " + address.toString());
		} catch (Exception e) {
			// do nothing
		}
		DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 4445);
		try {
			socket.send(packet);
		} catch (IOException e) {
			// do nothing
		}

		// get response
		packet = new DatagramPacket(buf, buf.length);
		try {
			socket.receive(packet);
		} catch (IOException e){
			// do nothing
		}

		// display response
		String received = new String(packet.getData(), 0, packet.getLength());
		System.out.println("Quote of the Moment: " + received);

		socket.close();
	}
}