import java.io.*;
import java.net.*;
import java.util.Date;

public class Iperfer{

	public static void main(String[] args){
         
        if (args.length < 1 || (!args[0].equals("-c") && !args[0].equals("-s"))) {
            System.err.println("Error: missing or additional arguments");
            System.exit(1);
        }

        if (args[0].equals("-c")) {
        	// run client
        	if (args.length != 7 || !args[1].equals("-h") || !args[3].equals("-p") || !args[5].equals("-t")) {
        		System.err.println("Error: missing or additional arguments");
        		System.exit(1);
        	}

        	String hostname = args[2];
        	int serverPort = Integer.parseInt(args[4]);
        	if (serverPort < 1024 || serverPort > 65535) {
        		System.err.println("Error: port number must be in the range 1024 to 65535");
        		System.exit(1);
        	}
        	int time = Integer.parseInt(args[6]);

        	runClient(hostname, serverPort, time);

        } else {
        	// run server
        	if (args.length != 3 || !args[1].equals("-p")) {
        		System.err.println("Error: missing or additional arguments");
        		System.exit(1);
        	}
        	int listenPort = Integer.parseInt(args[2]);
        	if (listenPort < 1024 || listenPort > 65535) {
        		System.err.println("Error: port number must be in the range 1024 to 65535");
        		System.exit(1);
        	}
        	runServer(listenPort);
        }
    }

    private static void runClient(String hostName, int portNumber, int time) {
        try (
            Socket echoSocket = new Socket(hostName, portNumber);
            OutputStream out = echoSocket.getOutputStream();
        ) {
        	byte[] bytes = new byte[1000];
            int numPackets = 0;
            long startTime = System.currentTimeMillis();
            long elapsedTime = 0L;
            while (elapsedTime < time*1000) {
            	out.write(bytes);
            	numPackets++;
            	elapsedTime = (new Date()).getTime() - startTime;
            }
            double rate = (double)numPackets*8/elapsedTime;
            System.out.format("sent=%d KB rate=%.3f Mbps%n", numPackets, rate);
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostName);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + hostName);
            System.exit(1);
        } 
    }

    private static void runServer(int portNumber) {
        try (
            ServerSocket serverSocket =
                new ServerSocket(portNumber);
            Socket clientSocket = serverSocket.accept();                      
            InputStream in = clientSocket.getInputStream();
        ) {
            byte[] bytes = new byte[1000];
            int tot = 0;
            long startTime = System.currentTimeMillis();
            int sz = in.read(bytes);
            while (sz != -1) {
                tot += sz;
                sz = in.read(bytes);
            }
            int numPackets = tot/1000;
            long elapsedTime = ((new Date()).getTime() - startTime);
            double rate = (double)numPackets*8/elapsedTime;
            System.out.format("received=%d KB rate=%.3f Mbps%n", numPackets, rate);
        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port "
                + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }
	
}