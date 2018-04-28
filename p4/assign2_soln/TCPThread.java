import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;

public class TCPThread extends Thread {
	protected volatile boolean connected = false;
	protected volatile boolean closed = false;
	protected volatile boolean receivedSYN = false;
	protected volatile boolean receivedFIN = false;
	protected static final int MAX_RESENT = 16;

	protected volatile long timeOUT = (long)30*1000*1000*1000;
	protected ConcurrentHashMap<Integer, TCPPacket> sentTCPs = new ConcurrentHashMap<>();
	protected ConcurrentHashMap<Integer, SafeSender> tcpSenders = new ConcurrentHashMap<>();
	protected IncomingMonitor incomingMonitor;

	protected DatagramSocket socket;
	protected InetAddress remote_address = null;
	protected int remote_port;
	protected int port;
	protected int mtu;
	protected int sws;
	protected volatile int seq_num = 0;
	protected volatile int ack_num = 0;

	protected long dataSent = 0;
	protected long dataReceived = 0;
	protected int packetSent = 0;
	protected int packetReceived = 0;
	protected int outOfSeq = 0;
	protected int wrongChecksum = 0;
	protected int retranTime = 0;
	protected int dupAck = 0;

	
	public void run() {
		// do nothing
	}

	protected void sendAck(TCPPacket tcpPacket, InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, 0, 0, 1);
		byte[] buf = seg.serialize(tcpPacket.getTimeStamp());
		DatagramPacket out_packet = new DatagramPacket(buf, buf.length, address, port);
		try {
			socket.send(out_packet);
			packetSent++;
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Send a acknowledge segment \n");
	}

	protected void safeSend(int SYN, int FIN, int ACK, InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, SYN, FIN, ACK);
		if (SYN == 1 || FIN == 1) {
			seq_num++;
		}
		SafeSender sender = new SafeSender(seg, address, port);
		sender.start();
		System.out.println("Send a segment \n" + seg.print_msg());
		tcpSenders.put(seg.getSeq(), sender);
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

		public TCPPacket getTcpSeg() {
			return seg;
		}

		public void run() {
			boolean successfullySent = false;
			int remainTimes = MAX_RESENT;
			while (!Thread.interrupted() && !successfullySent && remainTimes > 0) {
				byte[] buf = seg.serialize();
				DatagramPacket packet = new DatagramPacket(buf, buf.length, out_address, out_port);
				try {
					socket.send(packet);
					packetSent++;
				} catch (IOException e) {
					e.printStackTrace();
				}
				seg.setStatus(TCPPacket.Status.Sent);
				if (seg.getLength() == 0) {
					sentTCPs.put(seg.getSeq()+1, seg);
				} else {
					sentTCPs.put(seg.getSeq()+seg.getLength(), seg);
				}
				long TO = getTO();
				try {
					Thread.sleep(TO/1000000);
				} catch (InterruptedException e) {
					System.out.println("sender get interrupted");
				}
				//check whether this packet has been acknowledged
				if (seg.getStatus() == TCPPacket.Status.Ack) {
					successfullySent = true;
				} else {
					remainTimes--;
					if (seg.getLength() == 0) {
						sentTCPs.remove(seg.getSeq()+1);
					} else {
						sentTCPs.remove(seg.getSeq()+seg.getLength());
					}
					if (remainTimes > 0) retranTime++;
				}
			}
			if (!successfullySent) {
				System.err.println("Maximum number of retransmission has reached! Still cannot send. Stop sending!");
				System.err.println("TCP segment: "+seg.print_msg());
				seg.setStatus(TCPPacket.Status.Lost);
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

	protected void fastRetransmit(SafeSender sender) {
		System.out.println("Retransmit a packet due to fast retransmission.");
		TCPPacket p = sender.getTcpSeg();
		if (p.getStatus() == TCPPacket.Status.Sent) {
			// This packet is still being transmitted
			// stop the sender
			sender.interrupt();
			retranTime++;
			sender.start();
			System.out.println("Resend a segment \n" + p.print_msg());
		}
		if (p.getStatus() == TCPPacket.Status.Ack) {
			System.out.println("This packet is already acked!");
			//ignore it
		}
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
					packetReceived++;
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Error in receiving a udp packet");
				}
				byte[] received = packet.getData();
				tcpPacket.deserialize(received);
				System.out.println("received a tcp seg \n" + tcpPacket.print_msg());
				// TODO compute checksum and timeout and outOfSequence
				boolean correctChecksum = true;
				boolean inSequence = true;

				// DO not drop the timeout packet
				// Don't use the timeout for calculation
				if (correctChecksum && inSequence) {
					if (tcpPacket.isACK()) {
						// TODO Update timeout

						if (tcpPacket.getLength() == 0 && !tcpPacket.isSYN() && !tcpPacket.isFIN()) {
							System.out.println("received an acknowledgement!");
						}
						// lookup sent TCPs
						if (sentTCPs.containsKey(tcpPacket.getAck())) {
							System.out.println("received an acknowledgement for a sent tcp packet!");
							TCPPacket sent = sentTCPs.get(tcpPacket.getAck());
							sent.setStatus(TCPPacket.Status.Ack);
							int num_acked = sent.increaseAckTimes();
							if (num_acked == 4) {
								// received three duplicate acks
								// fast retransmission
								if (tcpSenders.containsKey(tcpPacket.getAck())) {
									SafeSender tcpSender = tcpSenders.get(tcpPacket.getAck());
									fastRetransmit(tcpSender);
								}
							}
						}

						if (tcpPacket.isSYN() && !connected) {
							System.out.println("received an SYN+ACK!");
							receivedSYN = true;
							connected = true;
							System.out.println("sending an ACK for SYN+ACK!");
							// set ack_num to received packet seq_num + 1
							ack_num = tcpPacket.getSeq() + 1;
							sendAck(tcpPacket, packet.getAddress(), packet.getPort());
							// have set up the connection, can send data now.
							sendData();
						}

						// when receivedSYN and packet has data, record data now
						if (receivedSYN && tcpPacket.getLength() > 0) {
							System.out.println("received an data segment!");
							ack_num = tcpPacket.getSeq() + tcpPacket.getLength();
							sendAck(tcpPacket, packet.getAddress(), packet.getPort());
							recordData(tcpPacket);
						}
					} else if (tcpPacket.isSYN()){
						System.out.println("received an SYN application!");
						receivedSYN = true;
						ack_num = tcpPacket.getSeq() + 1;
						System.out.println("sending an ACK+SYN for SYN!");
						safeSend(1, 0, 1, packet.getAddress(), packet.getPort());
					} else if (tcpPacket.isFIN()) {
						System.out.println("received an FIN application!");
						receivedFIN = true;
						System.out.println("sending an ACK for FIN!");
						ack_num = tcpPacket.getSeq() + 1;
						sendAck(tcpPacket, packet.getAddress(), packet.getPort());
						// No data to sent, send FIN back
						safeSend(0, 1, 0, packet.getAddress(), packet.getPort());
					} else {
						System.out.println("Received a package unhandled!");
					}
				}
			}
			System.out.println("Stop running packet receiver thread...");
		}
	}
	
}
