import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
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
	private static final int header_sz = 6*32;


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
		socket.close();
	}

	private void connect_remote() {
		byte[] buf = new byte[mtu];
		ByteBuffer bb = ByteBuffer.wrap(buf);
		prepareSegmentHeader(bb, 0, 0, 0, 1,0, 0);
		DatagramPacket packet = new DatagramPacket(buf, buf.length, remote_address, remote_port);
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			moreData = false;
		}
	}

	private void prepareSegmentHeader(ByteBuffer bb, int seq, int ack, int length, int SYS, int FIN, int ACK) {
		bb.putInt(seq);
		bb.putInt(ack);
		bb.putLong(System.nanoTime());
		int length_flags = length << 3 | SYS << 2 | FIN << 1 | ACK;
		bb.putInt(length_flags);
		bb.putShort((short)0);
		short checksum = cal_checksum(bb);
		bb.putShort(checksum);
	}

	private short cal_checksum(ByteBuffer bb) {
		return (short)0;
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
}