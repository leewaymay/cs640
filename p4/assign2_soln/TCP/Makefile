Source=src/
Bin=./


all: TCPend.class
TCPend.class: $(Source)TCPend.java $(Source)TCPThread.java $(Source)TCPSenderThread.java $(Source)TCPReceiverThread.java $(Source)TCPPacket.java
	javac $(Source)TCPPacket.java -d $(Bin)
	javac $(Source)TCPThread.java -d $(Bin)
	javac $(Source)TCPSenderThread.java -d $(Bin)
	javac $(Source)TCPReceiverThread.java -d $(Bin)
	javac $(Source)TCPend.java -d $(Bin)
clean:
	rm $(Bin)*.class
