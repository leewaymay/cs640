import java.io.*;
import java.net.*;
import java.util.*;

public class TCPSenderThread extends Thread {

	private DatagramSocket socket;
	private BufferedReader in = null;
	private InetAddress remote_address = null;
	private int remote_port;
	private int mtu;
	private int sws;
	private boolean moreData = true;
	private int seq_num;


	public TCPSenderThread(int port, String remote_IP, int remote_port, String filename, int mtu, int sws)
			throws IOException {

		socket = new DatagramSocket(port);
		try {
			remote_address = InetAddress.getByName(remote_IP);
			System.out.println("The host address is " + remote_address.toString());
		} catch (Exception e) {
			System.err.println("wrong address given.");
		}
		this.remote_port = remote_port;
		this.mtu = mtu;
		this.sws = sws;

		try {
			in = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException e) {
			moreData = false;
			System.err.println("Could not open given file.");
		}

		this.seq_num = 0;
	}

	public void run() {
		// start packet receiver thread
		PacketReceiver packetReceiver = new PacketReceiver();
		packetReceiver.start();

		// build connection
		connect_remote();

		while (moreData) {
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
					dString = getNextData();

				buf = dString.getBytes();

				// send the response to the client at "address" and "port"
				InetAddress address = packet.getAddress();
				int port = packet.getPort();
				packet = new DatagramPacket(buf, buf.length, address, port);
				socket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
				moreData = false;
			}
		}
		packetReceiver.interrupt();
		socket.close();
	}

	private void connect_remote() {
		TCPPacket seg = new TCPPacket(mtu, 0, 0, 1, 0, 0);
		byte[] buf = seg.serialize();
		DatagramPacket packet = new DatagramPacket(buf, buf.length, remote_address, remote_port);
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			moreData = false;
		}
	}


	private String getNextData() {
		String returnValue = null;
		try {
			if ((returnValue = in.readLine()) == null) {
				in.close();
				moreData = false;
				returnValue = "No more data. Goodbye.";
			}
		} catch (IOException e) {
			returnValue = "IOException occurred in server.";
		}
		return returnValue;
	}

	private class PacketReceiver extends Thread {
		private TCPPacket tcpPacket = new TCPPacket(mtu);
		private byte[] buf = tcpPacket.serialize();
		private DatagramPacket packet = new DatagramPacket(buf, buf.length);

		public void run() {
			System.out.println("Start running packet receiver thread...");
			while (!Thread.interrupted()) {
				try {
					socket.receive(packet);
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Error in receive a udp packet");
				}
				byte[] received = packet.getData();
				tcpPacket.deserialize(received);
				// TODO compute checksum
				if (tcpPacket.isACK()) {
					System.out.println("received an acknowledgement!");
				} else {
					System.out.println("received not an acknowledgement!");
				}

			}
			System.out.println("Stop running packet receiver thread...");
		}
	}
}