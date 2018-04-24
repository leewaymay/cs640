all: TCPend.class
TCPSenderThread.class: TCPSenderThread.java TCPPacket.class
	javac TCPSenderThread.java
TCPReceiverThread.class: TCPReceiverThread.java TCPPacket.class
	javac TCPReceiverThread.java
TCPPacket.class: TCPPacket.java
	javac TCPPacket.java
TCPend.class: TCPend.java TCPSenderThread.class TCPReceiverThread.class
	javac TCPend.java
clean:
	rm TCPend.class
	rm TCPReceiverThread.class
	rm TCPSenderThread.class
