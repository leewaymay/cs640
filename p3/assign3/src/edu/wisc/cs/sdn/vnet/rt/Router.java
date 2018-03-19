package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	/** RIP table learned from other routers*/
	private Map<Integer, RIPv2Entry> ripEntries;
	private Map<Integer, RIPv2Entry> ripStaticEntries;

	private Thread ripResponder;

	private Thread ripCleaner;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.ripEntries = new ConcurrentHashMap<>();
		this.ripStaticEntries = new ConcurrentHashMap<>();
		this.ripResponder = new Thread(new RipResponder());
		this.ripCleaner = new Thread(new RipCleaner());
	}

	private class RipResponder implements Runnable {
		@Override
		public void run() {
			while (true) {
				// request RIP
				this.unsolicitedResponse();
				try {
					Thread.sleep((long)1000*10);
				} catch (InterruptedException e) {
					System.err.println("rip unsolicited responder stopped working");
					break;
				}
			}
		}
		private void unsolicitedResponse() {
			// request rip
			Ethernet etherPacket = preparePacket(false, "224.0.0.9", "FF:FF:FF:FF:FF:FF");
			for (Iface iface : interfaces.values()) {
				etherPacket.setSourceMACAddress(iface.getMacAddress().toString());
				sendPacket(etherPacket, iface);
			}
		}
	}

	private class RipCleaner implements Runnable {
		private final long MAX_TIME = 30*1000;
		@Override
		public void run() {
			while (true) {
				// clean RIP
				this.clean();
				try {
					Thread.sleep((long)1000);
				} catch (InterruptedException e) {
					System.err.println("rip cleaner stopped working");
					break;
				}
			}
		}
		private void clean() {
			// clean rip entries
			for (Map.Entry<Integer, RIPv2Entry> e : ripEntries.entrySet()) {
				if ((System.currentTimeMillis() - e.getValue().getTimestamp()) > MAX_TIME) {
					routeTable.remove(e.getValue().getAddress(), e.getValue().getSubnetMask());
					ripEntries.remove(e.getKey());
				}
			}
		}
	}

	private void requestRip() {
		Ethernet etherPacket = preparePacket(true, "224.0.0.9", "FF:FF:FF:FF:FF:FF");
		for (Iface iface : interfaces.values()) {
			etherPacket.setSourceMACAddress(iface.getMacAddress().toString());
			sendPacket(etherPacket, iface);
		}
	}

	private Ethernet preparePacket(Boolean isRequest, String destIp, String destMAC) {
		RIPv2 ripPacket = new RIPv2();
		if (isRequest) {
			ripPacket.setCommand(RIPv2.COMMAND_REQUEST);
		} else {
			ripPacket.setCommand(RIPv2.COMMAND_RESPONSE);
			List<RIPv2Entry> ripEntryList = new LinkedList<>(ripEntries.values());
			ripEntryList.addAll(ripStaticEntries.values());
			ripPacket.setEntries(ripEntryList);
		}
		UDP udpPacket = new UDP();
		ripPacket.setParent(udpPacket);
		udpPacket.setPayload(ripPacket);
		udpPacket.setDestinationPort(UDP.RIP_PORT);
		udpPacket.setSourcePort(UDP.RIP_PORT);
		IPv4 ipPacket = new IPv4();
		ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
		ipPacket.setPayload(udpPacket);
		udpPacket.setParent(ipPacket);
		ipPacket.setDestinationAddress(destIp);
		Ethernet etherPacket = new Ethernet();
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);
		etherPacket.setPayload(ipPacket);
		ipPacket.setParent(etherPacket);
		etherPacket.setDestinationMACAddress(destMAC);
		return etherPacket;
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Start running RIP when a static route table is not provided
	 */
	public void buildRIP() {
		// Add entries to route table for subnets that are directly reachable via
		// the router's interface
		for (Iface iface : this.interfaces.values()) {
			int subnet = iface.getSubnetMask() & iface.getIpAddress();
			RIPv2Entry ripEntry = new RIPv2Entry(subnet, iface.getSubnetMask(), 1);
			ripStaticEntries.put(subnet, ripEntry);
			routeTable.insert(subnet,0, iface.getSubnetMask(),iface);
		}
		// Send Rip request
		this.requestRip();
		// Send unsolicited RIP response every 10 seconds
		this.ripResponder.start();
		// start ripCleaner every second
		this.ripCleaner.start();
	}
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Check if it is a RIP packet
		if (checkRIP(ipPacket)) {
			handleRipPacket(ipPacket);
			return;
		}

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { return; }
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{ return; }
        }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

	private Boolean checkRIP(IPv4 ipPacket) {
		// TODO: check if this is a RIP packet
		return false;
	}

	private void handleRipPacket(IPv4 ipPacket) {
		// TODO: handle RIP packet
		return;
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        { return; }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { return; }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
}
