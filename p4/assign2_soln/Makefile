all: TCPSender.class TCPReceiver.class

TCPSender.class: TCPSender.java TCPSenderThread.java
	javac TCPSenderThread.java
	javac TCPSender.java
TCPReceiver.class: TCPReceiver.java
	javac TCPReceiver.java

clean:
	rm TCPSender.class
	rm TCPReceiver.class
	rm TCPSenderThread.class
