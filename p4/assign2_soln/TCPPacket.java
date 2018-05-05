import java.nio.ByteBuffer;
import java.util.Arrays;

public class TCPPacket {

	public enum Status {
		Default, Sent, Ack, Lost
	}



	private int seq = 0;
	private int ack = 0;
	private long timeStamp = 0;
	private int length = 0;
	private int SYN = 0;
	private int FIN = 0;
	private int ACK = 0;
	public static final int header_sz = 6*4;
	private byte[] data = new byte[0];
	private short checksum = 0;
	private int mtu = 0;
	private Status status = Status.Default;

	private volatile int ack_times = 0;

	public TCPPacket(int mtu) {
		this.mtu = mtu;
	}

	public TCPPacket(int mtu, int seq, int ack, int SYN, int FIN, int ACK) {
		this.mtu = mtu;
		this.seq = seq;
		this.ack = ack;
		this.SYN = SYN;
		this.FIN = FIN;
		this.ACK = ACK;
		if (mtu < header_sz) {
			System.out.println("mtu is too small to fit a TCP header!");
		}
	}

	public void addData(byte[] data) {
		//check data length
		if (data.length > (mtu-header_sz)) {
			System.out.println("data is too large to fit into a TCP segment!");
		} else {
			this.data = data.clone();
			this.length = data.length;
		}
	}

	public int increaseAckTimes() {
		this.ack_times++;
		return this.ack_times;
	}

	public byte[] getData() {
		return this.data;
	}

	public long getTimeStamp() {
		return this.timeStamp;
	}

	public int getLength() {
		return this.length;
	}

	public int getSeq() {
		return this.seq;
	}

	public int getAck() {
		return this.ack;
	}

	public boolean isSYN() {
		return this.SYN == 1;
	}

	public boolean isFIN() {
		return this.FIN == 1;
	}

	public boolean isACK() {
		return this.ACK == 1 && this.length != 0;
	}

	public boolean isDATA() {
		return this.ACK == 1 && this.length == 0;
	}

	public synchronized void setStatus(Status s) {
		this.status = s;
	}

	public synchronized Status getStatus() {
		return this.status;
	}

	public byte[] serialize() {
		return serialize(System.nanoTime());
	}

	public byte[] serialize(long segTimeStamp) {
		byte[] tcp_seg = new byte[mtu];
		ByteBuffer bb = ByteBuffer.wrap(tcp_seg);
		bb.putInt(seq);
		bb.putInt(ack);
		this.timeStamp = segTimeStamp;
		bb.putLong(segTimeStamp);
		int length_flags = length << 3 | SYN << 2 | FIN << 1 | ACK;
		bb.putInt(length_flags);
		bb.putShort((short)0);
		cal_checksum(bb);
		bb.putShort(checksum);
		bb.put(data);
		return tcp_seg;
	}

	public TCPPacket deserialize(byte[] tcp_seg) {
		ByteBuffer bb = ByteBuffer.wrap(tcp_seg);
		this.seq = bb.getInt();
		this.ack = bb.getInt();
		this.timeStamp = bb.getLong();
		int length_flags = bb.getInt();
		this.length = length_flags >>> 3;
		this.SYN = length_flags >>> 2 & 1;
		this.FIN = length_flags >>> 1 & 1;
		this.ACK = length_flags & 1;
		short all_zeros = bb.getShort();
		this.checksum = bb.getShort();
		this.data = Arrays.copyOfRange(tcp_seg, bb.position(), bb.limit());
		this.mtu = tcp_seg.length;
		return this;
	}

	private void cal_checksum(ByteBuffer bb) {
		this.checksum = (short)0;
	}

	public String print_msg(long startTime) {
		char[] flag_list = new char[4];
		flag_list[0] = isSYN() ? 'S' : '-';
		flag_list[1] = isACK() ? 'A' : '-';
		flag_list[2] = isFIN() ? 'F' : '-';
		flag_list[0] = isDATA() ? 'D' : '-';
		return String.format("%.3f %s %d %d, %d", (System.nanoTime() - startTime) / 1e9, String.valueOf(flag_list), seq, length, ack);
	}
}