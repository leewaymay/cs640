all: TCPend.class
TCPThread.class: TCPThread.java TCPPacket.class
	javac TCPThread.java
TCPSenderThread.class: TCPSenderThread.java TCPPacket.class TCPThread.class
	javac TCPSenderThread.java
TCPReceiverThread.class: TCPReceiverThread.java TCPPacket.class TCPThread.class
	javac TCPReceiverThread.java
TCPPacket.class: TCPPacket.java
	javac TCPPacket.java
TCPend.class: TCPend.java TCPSenderThread.class TCPReceiverThread.class
	javac TCPend.java
clean:
	rm TCPend.class
	rm TCPReceiverThread.class
	rm TCPSenderThread.class
	rm TCPThread.class
	rm TCPPacket.class
