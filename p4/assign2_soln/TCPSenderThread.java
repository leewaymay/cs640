import java.io.*;
import java.net.*;
import java.util.*;

public class TCPSenderThread extends TCPThread {

	private BufferedReader in = null;
	private boolean moreData = true;
	private String filename = null;

	public TCPSenderThread(int port, String remote_IP, int remote_port, String filename, int mtu, int sws) {
		this.port = port;
		try{
			socket = new DatagramSocket(port);
		} catch (Exception e) {
			//do nothing
			System.err.println("Error in building a TCP socket!");
		}
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
			this.filename = filename;
		} catch (FileNotFoundException e) {
			moreData = false;
			System.err.println("Could not open given file.");
		}

		this.seq_num = 0;
		this.incomingMonitor = new IncomingMonitor();
	}

	@Override
	public void run() {
		// start packet receiver thread
		incomingMonitor.start();
		if (in == null) {
			System.err.println("Error in open the given file. Stop connection!");
		}
		// build connection
		connect_remote();
	}

	private void connect_remote() {
		System.out.println("sending a SYN to connect!");
		safeSend(1, 0, 0, remote_address, remote_port);
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

	@Override
	protected void sendData() {
		// send the filename
		sendFilename();

		// TODO send data to the receiver through safe sender
		// use CWND and slow start to control the number of packages
		// use buffer window size to control the flow
		while (moreData) {
			try {
				String dString = getNextData();
				byte[] buf = dString.getBytes();
				// send the response to the client at "address" and "port"
				DatagramPacket packet = new DatagramPacket(buf, buf.length, remote_address, remote_port);
				socket.send(packet);
				try {
					Thread.sleep(1*1000);
				} catch (InterruptedException e) {

				}
			} catch (IOException e) {
				e.printStackTrace();
				moreData = false;
			}
		}
		// when finished sending data, send FIN to close the transfer
		startClose();
	}

	private void sendFilename() {
		// send the filename to the receiver
	}

	private void startClose() {
		System.out.println("sending a FIN to close!");
		safeSend(0, 1, 0, remote_address, remote_port);
	}

	private void safeSendData(int SYN, int FIN, int ACK, InetAddress address, int port, byte[] data) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, SYN, FIN, ACK);
		seg.addData(data);
		seq_num += data.length;
		SafeSender sender = new SafeSender(seg, address, port);
		sender.start();
	}
}