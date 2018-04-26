import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;

public class TCPThread extends Thread {
	protected volatile boolean connected = false;
	protected volatile boolean closed = false;
	protected volatile boolean receivedSYN = false;
	protected volatile boolean receivedFIN = false;
	protected static final int MAX_RESENT = 16;

	protected volatile long timeOUT = (long)5*1000*1000*1000;
	protected ConcurrentHashMap<Integer, TCPPacket> sentTCPs = new ConcurrentHashMap<>();
	protected IncomingMonitor incomingMonitor;

	protected DatagramSocket socket;
	protected InetAddress remote_address = null;
	protected int remote_port;
	protected int port;
	protected int mtu;
	protected int sws;
	protected volatile int seq_num = 0;
	protected volatile int ack_num = 0;
	
	public void run() {
		// do nothing
	}

	protected void sendAck(InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, 0, 0, 1);
		byte[] buf = seg.serialize();
		DatagramPacket out_packet = new DatagramPacket(buf, buf.length, address, port);
		try {
			socket.send(out_packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void safeSend(int SYN, int FIN, int ACK, InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, SYN, FIN, ACK);
		if (SYN == 1 || FIN == 1) {
			seq_num++;
		}
		SafeSender sender = new SafeSender(seg, address, port);
		sender.start();
	}

	protected void sendData() {
		// do nothing
		System.out.println("TCP thread for sending data, should be implemented in child class!");
	}

	protected void recordData(TCPPacket tcpPacket) {
		// do nothing
		System.out.println("TCP thread for receiving data, should be implemented in child class!");
	}

	protected class SafeSender extends Thread {
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
					remote_address = out_address;
					remote_port = out_port;
				} else if (seg.isFIN()) {
					closed = true;
					close_connection();
				}
			}
		}
	}

	protected long getTO() {
		return timeOUT;
	}

	protected void close_connection() {
		customize_close();
		// close the incoming monitor
		incomingMonitor.interrupt();

		System.out.println("closing connection!");
		socket.close();
	}

	protected void customize_close() {

	}

	protected class IncomingMonitor extends Thread {

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
					}
					if (tcpPacket.isSYN()) {
						System.out.println("received an SYN+ACK!");
						receivedSYN = true;
						connected = true;
						System.out.println("sending an ACK for SYN+ACK!");
						// set ack_num to received packet seq_num + 1
						ack_num = tcpPacket.getSeq() + 1;
						sendAck(packet.getAddress(), packet.getPort());
						// have set up the connection, can send data now.
						sendData();
					}
					//TODO update timeout
				} else if (tcpPacket.isSYN()){
					System.out.println("received an SYN application!");
					receivedSYN = true;
					System.out.println("sending an ACK+SYN for SYN!");
					safeSend(1, 0, 1, packet.getAddress(), packet.getPort());
				} else if (tcpPacket.isFIN()) {
					System.out.println("received an FIN application!");
					receivedFIN = true;
					System.out.println("sending an ACK for FIN!");
					ack_num = tcpPacket.getSeq() + 1;
					sendAck(packet.getAddress(), packet.getPort());
					// No data to sent, send FIN back
					safeSend(0, 1, 0, packet.getAddress(), packet.getPort());
				} else {
					System.out.println("Received a package unhandled!");
				}
				// when receivedSYN and packet has data, record data now
				if (receivedSYN && tcpPacket.isACK() && tcpPacket.getAck() == (seq_num+1) && tcpPacket.getLength() > 0) {
					recordData(tcpPacket);
				}
			}
			System.out.println("Stop running packet receiver thread...");
		}
	}
	
}
