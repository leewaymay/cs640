import java.io.*;
import java.net.*;
import java.util.*;

public class TCPSenderThread extends TCPThread {

	private BufferedInputStream in = null;
	private String filename = null;
	private boolean needSendFilename = true;

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
		safeSend(1, 0, 0, remote_address, remote_port);
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
						safeSendData(0, 0, 1, remote_address, remote_port, buf);
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
						}
						if (res > 0) safeSendData(0, 0, 1, remote_address, remote_port, buf);
					}
				}
			}
			// when finished sending data, send FIN to close the transfer
			if (!moreData && !needSendFilename && sendQ.size() == 0) startClose();
		}
	}

	private void startClose() {
		System.out.println("sending a FIN to close!");
		safeSend(0, 1, 0, remote_address, remote_port);
	}

	private void safeSendData(int SYN, int FIN, int ACK, InetAddress address, int port, byte[] data) {
		TCPPacket seg = new TCPPacket(mtu, seq_num, ack_num, SYN, FIN, ACK);
		seg.addData(data);
		seq_num += seg.getLength();
		SafeSender sender = new SafeSender(seg, address, port);
		sender.start();
		sendQ.offer(seg);
	}
}