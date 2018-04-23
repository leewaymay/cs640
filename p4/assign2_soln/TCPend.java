import java.io.*;

public class TCPend {
	public static void main(String[] args) throws IOException{
		if (args.length == 12) {
			if (args[0].equals("-p") && args[2].equals("-s") && args[4].equals("-a") && args[6].equals("-f") &&
					args[8].equals("-m") && args[10].equals("-c")) {
				try {
					int port = Integer.parseInt(args[1]);
					String remote_IP = args[3];
					int remote_port = Integer.parseInt(args[5]);
					String filename = args[7];
					int mtu = Integer.parseInt(args[9]);
					int sws = Integer.parseInt(args[11]);
					// start sender application
					TCPSenderThread sender = new TCPSenderThread(port, remote_IP, remote_port, filename, mtu, sws);
					sender.start();
				} catch (NumberFormatException e) {
					System.err.println("Error in parsing argument into integers!");
					System.err.println("port, remote_port, mtu and sws should be integers.");
					print_args_error();
				}
			} else {
				print_args_error();
			}

		} else if (args.length == 6) {
			if (args[0].equals("-p") && args[2].equals("-m") && args[4].equals("-c")) {
				try {
					int port = Integer.parseInt(args[1]);
					int mtu = Integer.parseInt(args[3]);
					int sws = Integer.parseInt(args[5]);
					// start sender application
					new TCPReceiverThread().start();
				} catch (NumberFormatException e) {
					System.err.println("Error in parsing argument into integers!");
					System.err.println("port, mtu and sws should be integers.");
					print_args_error();
				}
			} else {
				print_args_error();
			}
		} else {
			print_args_error();
		}
	}

	private static void print_args_error() {
		System.err.println("Please provide the correct arguments at the sender side:");
		System.err.println("java TCPend -p <port> -s <remote-IP> -a <remote-port> –f <file name> -m <mtu> -c <sws>");
		System.err.println("or at the receiver side:");
		System.err.println("java TCPend -p <port> -m <mtu> -c <sws>");
		System.exit(0);
	}
}