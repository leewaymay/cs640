import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class TCPReceiverThread extends Thread {

	private static final int MAX_RESENT = 16;

	private DatagramSocket socket = null;
	protected PrintWriter out = null;

	private volatile boolean connected = false;
	private volatile boolean closed = false;
	private volatile boolean receivedSYN = false;
	private volatile boolean receivedFIN = false;
	private volatile long timeOUT = (long)5*1000*1000*1000;
	private ConcurrentHashMap<Integer, TCPPacket> sentTCPs = new ConcurrentHashMap<>();

	private IncomingMonitor incomingMonitor;
	private InetAddress sender_address;
	private int sender_port;

	private int port;
	private int mtu;
	private int sws;

	private volatile int seq_num = 0;


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

		try{
			socket = new DatagramSocket(port);
		} catch (Exception e) {
			//do nothing
		}
		System.out.println("listening to a SYN packet for building connection");
		// Start incoming monitor
		incomingMonitor.start();

		System.out.println("wait for TCP connect to close.");

		if (connected) {
			// send request
			byte[] buf = new byte[256];

			DatagramPacket packet = new DatagramPacket(buf, buf.length, sender_address, sender_port);
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
		}

		socket.close();
	}

	private void sendAck(int ack, InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack, 0, 0, 1);
		byte[] buf = seg.serialize();
		DatagramPacket out_packet = new DatagramPacket(buf, buf.length, address, port);
		try {
			socket.send(out_packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void safeSend(int ack, int SYN, int FIN, int ACK, InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack, SYN, FIN, ACK);
		SafeSender sender = new SafeSender(seg, address, port);
		sender.start();
	}

	private class SafeSender extends Thread {
		private TCPPacket seg;
		private InetAddress out_address;
		private int out_port;

		public SafeSender(TCPPacket tcpPacket, InetAddress out_address, int out_port) {
			this.seg = tcpPacket;
			this.out_address = out_address;
			this.out_port = out_port;
		}

		public void run() {
			boolean successfullySent = false;
			int remainTimes = MAX_RESENT;
			while (!successfullySent && remainTimes > 0) {
				byte[] buf = seg.serialize();
				DatagramPacket packet = new DatagramPacket(buf, buf.length, out_address, out_port);
				try {
					socket.send(packet);
				} catch (IOException e) {
					e.printStackTrace();
				}
				seg.setStatus(TCPPacket.Status.Sent);
				sentTCPs.put(seg.getSeq(), seg);
				long TO = getTO();
				try {
					Thread.sleep(TO/1000000);
				} catch (InterruptedException e) {
					System.err.println("sender stopped working");
				}
				//check whether this packet has been acknowledged
				if (seg.getStatus() == TCPPacket.Status.Ack) {
					successfullySent = true;
				} else {
					sentTCPs.remove(seg.getSeq());
					remainTimes--;
				}
			}
			if (!successfullySent) {
				System.err.println("Maximum number of retransmission has reached! Still cannot send. Stop sending!");
				System.err.println("TCP segment: "+seg.print_msg());
			} else {
				if (seg.isSYN()) {
					connected = true;
					sender_address = out_address;
					sender_port = out_port;
				} else if (seg.isFIN()) {
					closed = true;
					// close the incoming monitor
					incomingMonitor.interrupt();
				}
			}
		}
	}

	private synchronized long getTO() {
		return timeOUT;
	}

	private class IncomingMonitor extends Thread {

		public void run() {
			System.out.println("Monitoring TCP delivery");
			TCPPacket tcpPacket = new TCPPacket(mtu);
			byte[] buf = tcpPacket.serialize();
			DatagramPacket packet = new DatagramPacket(buf, buf.length);

			while (!Thread.interrupted()) {
				try {
					socket.receive(packet);
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Error in receiving a udp packet");
				}
				byte[] received = packet.getData();
				tcpPacket.deserialize(received);
				// TODO compute checksum
				if (tcpPacket.isACK() && (System.nanoTime()-tcpPacket.getTimeStamp()) <= getTO()) {
					System.out.println("received an acknowledgement!");
					// lookup sent TCPs
					// we assume the ack is 1+sent_tcp_seq
					if (sentTCPs.containsKey(tcpPacket.getAck() - 1)) {
						TCPPacket sent = sentTCPs.get(tcpPacket.getSeq());
						sent.setStatus(TCPPacket.Status.Ack);
						//TODO update timeout
					}
				} else if (tcpPacket.isSYN()){
					System.out.println("received an SYN application!");
					receivedSYN = true;
					System.out.println("sending an ACK for SYN!");
					safeSend(tcpPacket.getSeq()+1, 1, 0, 1, packet.getAddress(), packet.getPort());
				} else if (tcpPacket.isFIN()) {
					System.out.println("received an FIN application!");
					receivedFIN = true;
					System.out.println("sending an ACK for FIN!");
					sendAck(tcpPacket.getSeq()+1, packet.getAddress(), packet.getPort());
					// No data to sent, send FIN back
					safeSend(tcpPacket.getSeq()+1, 0, 1, 0, packet.getAddress(), packet.getPort());

				} else {
					System.out.println("Received a package unhandled!");
				}
				// when receivedSYN and packet has data, record data now
				if (receivedSYN && tcpPacket.isACK() && tcpPacket.getAck() == (seq_num+1) && tcpPacket.getLength() > 0) {
					byte[] data = tcpPacket.getData();
					String s = new String(data,0, tcpPacket.getLength());
					// TODO keep a buffered window and write to text
					System.out.println(s);
				}
			}
			System.out.println("Stop running packet receiver thread...");
		}
	}
}