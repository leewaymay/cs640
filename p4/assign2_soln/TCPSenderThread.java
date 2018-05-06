import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

public class TCPSenderThread extends TCPThread {

	private BufferedInputStream in = null;
	private String filename = null;
	private boolean needSendFilename = true;
	private volatile boolean sentFIN = false;

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
		this.sendQ = new ArrayDeque<>(sws);
		this.unAckedQ = new PriorityBlockingQueue<>(sws, new Comparator<TCPPacket>() {
			@Override
			public int compare(TCPPacket tcpPacket, TCPPacket t1) {
				return tcpPacket.getExpAck() - t1.getExpAck();
			}
		});

		try {
			in = new BufferedInputStream(new FileInputStream(filename));
			this.filename = filename;
			moreData = true;
			needSendFilename = true;
		} catch (FileNotFoundException e) {
			moreData = false;
			needSendFilename = false;
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
		safeSend(1, 0, 0, remote_address, remote_port, System.nanoTime());
	}


	@Override
	protected void sendData() {
		if (sendQ != null) new DataSender().start();
	}

	protected class DataSender extends Thread {

		public void run() {
			synchronized (sendQ) {
				// clean the sliding window (sendQ)
				while (sendQ.size() > 0 && sendQ.peek().getStatus() == TCPPacket.Status.Ack) {
					sendQ.poll();
				}

				while (sendQ.size() < sws && (moreData || needSendFilename)){
					// can send data
					if (needSendFilename) {
						byte[] buf = filename.getBytes();
						safeSendData(0, 0, 1, remote_address, remote_port, buf, System.nanoTime());
						needSendFilename = false;
					} else {
						byte[] buf = new byte[mtu - TCPPacket.header_sz];
						int res = -1;
						try {
							res = in.read(buf);
						} catch (IOException e) {
							System.err.println("Error in trying to read data!");
						}
						if (res < buf.length) {
							moreData = false;
							if (res > 0) {
								buf = Arrays.copyOfRange(buf, 0, res);
							}
						}
						if (res > 0) safeSendData(0, 0, 1, remote_address, remote_port, buf, System.nanoTime());
					}
				}
			}
			// when finished sending data, send FIN to close the transfer
			if (!moreData && !needSendFilename && sendQ.size() == 0) startClose();
		}
	}

	private void startClose() {
		if (!sentFIN) {
			sentFIN = true;
			System.out.println("sending a FIN to close!");
			safeSend(0, 1, 0, remote_address, remote_port, System.nanoTime());
		}
	}

	@Override
	protected void customize_close() {
		System.out.println("Amount of Data Transferred:                " + dataSent);
		System.out.println("Amount of Packets Sent:                    " + packetSent);
		System.out.println("No of Packets discarded (out of sequence): " + outOfSeq);
		System.out.println("No of Packets discarded (wrong checksum):  " + wrongChecksum);
		System.out.println("No of Retransmissions:                     " + retranTime);
		System.out.println("No of Duplicate Acknowledgements:          " + dupAck);
	}
}