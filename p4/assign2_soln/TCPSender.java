import java.io.*;

public class TCPSender {
	public static void main(String[] args) throws IOException {
		new TCPSenderThread().start();
	}
}