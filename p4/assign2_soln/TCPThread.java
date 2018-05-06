import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;
import java.util.concurrent.PriorityBlockingQueue;

public class TCPThread extends Thread {
	protected volatile boolean connected = false;
	protected volatile boolean closed = false;
	protected volatile boolean receivedSYN = false;
	protected volatile boolean receivedFIN = false;
	protected static final int MAX_RESENT = 16;

	protected volatile long timeOUT = (long)5*1000*1000*1000;
	protected volatile long ertt = (long)5*1000*1000*1000;
	protected volatile long edev = (long)0;
	protected ConcurrentHashMap<Integer, TCPPacket> sentTCPs = new ConcurrentHashMap<>();
	protected ConcurrentHashMap<Integer, SafeSender> tcpSenders = new ConcurrentHashMap<>();
	protected IncomingMonitor incomingMonitor;

	protected DatagramSocket socket;
	protected InetAddress remote_address = null;
	protected int remote_port;
	protected int port;
	protected int mtu;
	protected int sws;
	protected volatile int initial_seq_num = 0;
	protected volatile int seq_num = 0;
	protected volatile int ack_num = 0;

	protected final static double A = 0.875;
	protected final static double B = 0.75;

	protected long dataSent = 0;
	protected long dataReceived = 0;
	protected int packetSent = 0;
	protected int packetReceived = 0;
	protected int outOfSeq = 0;
	protected int wrongChecksum = 0;
	protected int retranTime = 0;
	protected int dupAck = 0;
	protected ArrayDeque<TCPPacket> sendQ;
	protected PriorityQueue<TCPPacket> receiveQ;
	protected PriorityBlockingQueue<TCPPacket> unAckedQ;
	protected boolean moreData = false;
	protected long startTime = System.nanoTime();

	protected volatile boolean firstUpdateTO = true;

	
	public void run() {
		// do nothing
	}

	protected void sendAck(TCPPacket tcpPacket, InetAddress address, int port) {
		sendAck(ack_num, tcpPacket, address, port);
	}

	protected void sendAck(int ack_num, TCPPacket tcpPacket, InetAddress address, int port) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, 0, 0, 1);
		byte[] buf = seg.serialize(tcpPacket.getTimeStamp());
		DatagramPacket out_packet = new DatagramPacket(buf, buf.length, address, port);
		try {
			socket.send(out_packet);
			System.out.println("snd " + print_seg(seg));
			packetSent++;
		} catch (IOException e) {
		}
	}


	protected void safeSend(int SYN, int FIN, int ACK, InetAddress address, int port, long timeStamp) {
		safeSendData(SYN, FIN, ACK, address, port, MAX_RESENT, null, timeStamp);
	}

	protected void safeSend(int SYN, int FIN, int ACK, InetAddress address, int port, int maxTimes, long timeStamp) {
		safeSendData(SYN, FIN, ACK, address, port, maxTimes, null, timeStamp);
	}


	protected void safeSendData(int SYN, int FIN, int ACK, InetAddress address, int port, byte[] data, long timeStamp) {
		safeSendData(SYN, FIN, ACK, address, port, MAX_RESENT, data, timeStamp);
	}

	protected void safeSendData(int SYN, int FIN, int ACK, InetAddress address, int port, int maxTimes, byte[] data, long timeStamp) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, SYN, FIN, ACK);

		if (data != null && data.length > 0) {
			seg.addData(data);
			seq_num += seg.getLength();
			if (sendQ != null) sendQ.offer(seg);
		}
		if (SYN == 1) {
			seq_num = initial_seq_num + 1;
		} else if (FIN == 1) {
			seq_num ++;
		}

		SafeSender sender = new SafeSender(seg, address, port, maxTimes, timeStamp);
		sender.start();
		tcpSenders.put(seg.getSeq(), sender);
		dataSent += seg.getLength();
	}

	protected void sendData() {
		// do nothing
		if (moreData) System.out.println("TCP thread for sending data, should be implemented in child class!");
	}

	protected void recordData(TCPPacket tcpPacket,  InetAddress address, int port) {
		ack_num = tcpPacket.getSeq() + tcpPacket.getLength();
		sendAck(tcpPacket, address, port);
		dataReceived += tcpPacket.getLength();
		writeData(tcpPacket);
	}

	protected void writeData(TCPPacket tcpPacket) {
		// do nothing
		System.out.println("TCP thread for write data, should be implemented in child class!");
	}

	protected class SafeSender extends Thread {
		private TCPPacket seg;
		private InetAddress out_address;
		private int out_port;
		private int remainTimes;
		private boolean successfullySent;
		private int maxTimes;
		private long timeStamp;

		public SafeSender(TCPPacket tcpPacket, InetAddress out_address, int out_port, int maxTimes, long timeStamp) {
			this.seg = tcpPacket;
			this.out_address = out_address;
			this.out_port = out_port;
			this.maxTimes = maxTimes;
			this.remainTimes = maxTimes;
			this.successfullySent = false;
			seg.setStatus(TCPPacket.Status.Sent);
			sentTCPs.put(seg.getExpAck(), seg);
			unAckedQ.add(seg);
			this.timeStamp = timeStamp;
		}

		public TCPPacket getTcpSeg() {
			return seg;
		}

		public SafeSender makeClone() {
			return new SafeSender(seg, out_address, out_port, remainTimes, timeStamp);
		}

		public void run() {
			while (!Thread.interrupted() && !successfullySent && remainTimes > 0) {
				byte[] buf = seg.isACK() ? seg.serialize(timeStamp) : seg.serialize();
				DatagramPacket packet = new DatagramPacket(buf, buf.length, out_address, out_port);
				try {
					socket.send(packet);
					packetSent++;
					if (remainTimes != maxTimes) {
						retranTime++;
					}
					System.out.println("snd " + print_seg(seg));
				} catch (IOException e) {
					break;
				}
				long TO = getTO();
				try {
					Thread.sleep(TO/1000000);
				} catch (InterruptedException e) {
					return;
				}
				//check whether this packet has been acknowledged
				if (seg.getStatus() == TCPPacket.Status.Ack) {
					successfullySent = true;
					if (seg.isSYN()) {
						connected = true;
						remote_address = out_address;
						remote_port = out_port;
					} else if (seg.isFIN() && !closed) {
						closed = true;
						new CloseConnect().start();
					}
				} else {
					remainTimes--;
				}
			}

			if (Thread.interrupted()) return;

			if (!successfullySent) {
				if (remainTimes == 0) {
					System.err.println("Maximum number of retransmission has reached! Still cannot send. Stop sending!");
				}
				seg.setStatus(TCPPacket.Status.Lost);
				new CloseConnect().start();
			}
		}
	}

	protected long getTO() {
		return timeOUT;
	}

	protected class CloseConnect extends Thread {
		public void run() {
			closed = true;
			customize_close();
			// close the incoming monitor
			incomingMonitor.interrupt();
			socket.close();
			// reset the flags
			connected = false;
			closed = false;
			receivedSYN = false;
			receivedFIN = false;
		}
	}

	protected void customize_close() {

	}

	protected void fastRetransmit(SafeSender sender) {
		TCPPacket p = sender.getTcpSeg();
		if (p.getStatus() == TCPPacket.Status.Sent) {
			// This packet is still being transmitted
			// stop the sender
			sender.interrupt();
			SafeSender newSender = sender.makeClone();
			tcpSenders.put(p.getSeq(), newSender);
			retranTime++;
			newSender.start();
		}
		if (p.getStatus() == TCPPacket.Status.Ack) {
			System.out.println("This packet is already acked!");
			//ignore it
		}
	}

	protected class IncomingMonitor extends Thread {

		public void run() {
			TCPPacket tcpPacket = new TCPPacket(mtu);
			byte[] buf = tcpPacket.serialize();
			DatagramPacket packet = new DatagramPacket(buf, buf.length);

			while (!Thread.interrupted()) {
				try {
					socket.receive(packet);
					packetReceived++;
				} catch (IOException e) {
					break;
				}
				byte[] received = packet.getData();
				tcpPacket.deserialize(received);
				System.out.println("rcv " + print_seg(tcpPacket));
				short old_cks = tcpPacket.getChecksum();
				tcpPacket.cal_checksum();
				boolean correctChecksum = (old_cks == tcpPacket.getChecksum());
				if (!correctChecksum) {
					System.out.println("wrong checksum!");
					wrongChecksum++;
				} else {
					// If get later acks, all the previous ack should be acknowledged.
					if (tcpPacket.isACK() || tcpPacket.isDATA()) {
						while (unAckedQ.size() > 0 && unAckedQ.peek().getExpAck() <= tcpPacket.getAck()) {
							TCPPacket sent  = unAckedQ.poll();
							sent.setStatus(TCPPacket.Status.Ack);
						}
					}
					if (tcpPacket.isACK()) {
						updateTimeOut(tcpPacket);
						if (!tcpPacket.isSYN() && !tcpPacket.isFIN()) {
							if (connected) sendData();
						}
						// check duplicate acks
						if (sentTCPs.containsKey(tcpPacket.getAck())) {
							TCPPacket sent = sentTCPs.get(tcpPacket.getAck());
							//sent.setStatus(TCPPacket.Status.Ack);
							int num_acked = sent.increaseAckTimes();
							if (num_acked > 1) dupAck++;
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
							// set ack_num to received packet seq_num + 1
							if (!receivedSYN) ack_num = tcpPacket.getSeq() + 1;
							receivedSYN = true;
							connected = true;
							sendAck(tcpPacket.getSeq() + 1, tcpPacket, packet.getAddress(), packet.getPort());
							// have set up the connection, can send data now.
							sendData();
						}
						// when received FIN+ACK
						if (tcpPacket.isFIN() && !closed) {
							receivedFIN = true;
							closed = true;
							ack_num = tcpPacket.getSeq() + 1;
							sendAck(tcpPacket, packet.getAddress(), packet.getPort());
							new CloseConnect().start();
						}

					} else if (tcpPacket.isSYN()){
						receivedSYN = true;
						ack_num = tcpPacket.getSeq() + 1;
						safeSend(1, 0, 1, packet.getAddress(), packet.getPort(), tcpPacket.getTimeStamp());
					} else if (tcpPacket.isFIN()) {
						receivedFIN = true;
						ack_num = tcpPacket.getSeq() + 1;
						safeSend(0, 1, 1, packet.getAddress(), packet.getPort(), 3, tcpPacket.getTimeStamp());
					} else if (tcpPacket.isDATA()) {
						// when receivedSYN and packet has data, record data now
						if (receivedSYN && receiveQ != null) {
							synchronized (receiveQ) {
								if (!connected) connected = true;
								if (tcpPacket.getSeq() > ack_num + (sws-1)*(mtu-TCPPacket.header_sz)) {
									// drop the packet
									System.out.println("dropped packet:" + tcpPacket.getSeq());
									outOfSeq++;
								} else if (tcpPacket.getSeq() == ack_num) {
									recordData(tcpPacket, packet.getAddress(), packet.getPort());
									// swipe the receiveQ
									while (receiveQ.size() > 0 && receiveQ.peek().getSeq() <= ack_num) {
										TCPPacket tmp = receiveQ.poll();
										// discard the packet with sequence number smaller than ack_num
										// which indicates that this is already been acked.
										if (tmp.getSeq() == ack_num) {
											recordData(tmp, packet.getAddress(), packet.getPort());
										}
									}
								} else if (tcpPacket.getSeq() < ack_num) {
									// received a packet previously acknowledged.
									// probably due to the ack is dropped
									// just resend the ack
									sendAck(tcpPacket.getSeq()+tcpPacket.getLength(), tcpPacket, packet.getAddress(), packet.getPort());

								} else {
									// received a packet out of order
									// keep it in buffer
									// send the duplicate ack
									try {
										TCPPacket buffered = (TCPPacket) tcpPacket.clone();
										receiveQ.offer(buffered);
										sendAck(tcpPacket, packet.getAddress(), packet.getPort());
									} catch (CloneNotSupportedException e) {
										System.out.println("Error in clone the packet");
									}
								}
							}
						}
					} else {
						System.out.println("Received a packet unhandled!");
					}
				}
			}
		}
	}

	private String print_seg(TCPPacket seg) {
		return seg.print_msg(startTime);
	}

	private void updateTimeOut(TCPPacket ackPacket) {
		if (firstUpdateTO || ackPacket.getSeq() == 0) {
			firstUpdateTO = false;
			ertt = System.nanoTime() - ackPacket.getTimeStamp();
			edev = 0;
			timeOUT = 2*ertt;
		} else {
			long srtt = System.nanoTime() - ackPacket.getTimeStamp();
			long sdev = Math.abs(srtt - ertt);
			ertt = (long) (A*ertt + (1 - A)*srtt);
			edev = (long) (B*edev + (1 - B)*sdev);
			timeOUT = ertt + 4*edev;
		}
	}
	
}
