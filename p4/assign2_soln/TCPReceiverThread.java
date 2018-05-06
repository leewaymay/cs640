import java.io.*;
import java.net.*;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class TCPReceiverThread extends TCPThread {

	protected PrintWriter out = null;
	private String default_filename = null;
	private String received_filename = null;

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
		this.receiveQ = new PriorityQueue<>(sws, new Comparator<TCPPacket>() {
			@Override
			public int compare(TCPPacket tcpPacket, TCPPacket t1) {
				return tcpPacket.getSeq()-t1.getSeq();
			}
		});
		this.unAckedQ = new PriorityBlockingQueue<>(sws, new Comparator<TCPPacket>() {
			@Override
			public int compare(TCPPacket tcpPacket, TCPPacket t1) {
				return tcpPacket.getExpAck() - t1.getExpAck();
			}
		});

		this.default_filename = "tcp_tmp.txt";

		try {
			out = new PrintWriter(new FileWriter(this.default_filename));
		} catch (IOException e) {
			System.err.println("Could not write to the file given the filename.");
		}
		this.incomingMonitor = new IncomingMonitor();
	}

	@Override
	public void run() {
		System.out.println("listening to a SYN packet for building connection");
		// Start incoming monitor
		incomingMonitor.start();
	}

	@Override
	protected void customize_close() {
		System.out.println("Amount of Data Received:                   " + dataReceived);
		System.out.println("Amount of Packets Received:                " + packetReceived);
		System.out.println("No of Packets discarded (out of sequence): " + outOfSeq);
		System.out.println("No of Packets discarded (wrong checksum):  " + wrongChecksum);
		System.out.println("No of Retransmissions:                     " + retranTime);
		System.out.println("No of Duplicate Acknowledgements:          " + dupAck);
		// flush output
		if (out != null) {
			out.flush();
			out.close();
		}

		// change the filename to the one sent
		// File (or directory) with old name
		if (this.received_filename != null) {
			File file = new File(this.default_filename);

			// File (or directory) with new name
			File file2 = new File(this.received_filename);

//			if (file2.exists())
//				System.out.println("Overwrite existing files.");

			// Rename file (or directory)
			boolean success = file.renameTo(file2);

			if (!success) {
				System.out.println("rename was not successful!");
			}
		}
	}

	@Override
	protected void writeData(TCPPacket tcpPacket) {
		byte[] data = tcpPacket.getData();
		String s = new String(data,0, tcpPacket.getLength());
//		System.out.println("\n*** seq: " + tcpPacket.getSeq() + " *****\n" + s + "\n************\n");
		if (tcpPacket.getSeq() == 1) {
			// this is the filename
			received_filename = s;
		} else {
			out.print(s);
		}
	}
}