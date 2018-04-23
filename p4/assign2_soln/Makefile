all: TCPend.class
TCPSenderThread.class: TCPSenderThread.java
	javac TCPSenderThread.java
TCPReceiverThread.class: TCPReceiverThread.java
	javac TCPReceiverThread.java
TCPend.class: TCPend.java TCPSenderThread.class TCPReceiverThread.class
	javac TCPend.java
clean:
	rm TCPend.class
	rm TCPReceiverThread.class
	rm TCPSenderThread.class
