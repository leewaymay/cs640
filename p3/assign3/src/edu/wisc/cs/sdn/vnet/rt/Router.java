package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;

import java.awt.*;
import java.util.List;
import java.util.LinkedList;

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
	private List<RIPv2Entry> ripEntries;

	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.ripEntries = new LinkedList<>();
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
				sendRipPacket(etherPacket, iface);
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
			synchronized (ripEntries) {
				List<RIPv2Entry> toRemove = new LinkedList<>();
				for (RIPv2Entry e : ripEntries) {
					if (((System.currentTimeMillis() - e.getTimestamp()) > MAX_TIME) && !e.isPermenent()) {
						routeTable.remove((e.getAddress()&e.getSubnetMask()), e.getSubnetMask());
						toRemove.add(e);
					}
				}
				for (RIPv2Entry e : toRemove) {
					// DEBUG
					System.out.println("removed a time-out rip entry");
					ripEntries.remove(e);
				}
			}
		}
	}

	private void requestRip() {
		Ethernet etherPacket = preparePacket(true, "224.0.0.9", "FF:FF:FF:FF:FF:FF");
		for (Iface iface : interfaces.values()) {
			sendRipPacket(etherPacket, iface);
		}
	}

	private void sendRipPacket(Ethernet etherPacket, Iface iface) {
		etherPacket.setSourceMACAddress(iface.getMacAddress().toString());
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		ipPacket.setSourceAddress(iface.getIpAddress());
		sendPacket(etherPacket, iface);
		// Debug
		System.out.println("*** -> Sent RIP packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
	}

	private Ethernet preparePacket(Boolean isRequest, String destIp, String destMAC) {
		RIPv2 ripPacket = new RIPv2();
		if (isRequest) {
			ripPacket.setCommand(RIPv2.COMMAND_REQUEST);
		} else {
			ripPacket.setCommand(RIPv2.COMMAND_RESPONSE);
			List<RIPv2Entry> ripEntryList = new LinkedList<>();
			synchronized (this.ripEntries) {
				ripEntryList.addAll(this.ripEntries);
			}
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
			RIPv2Entry ripEntry = new RIPv2Entry(subnet, 0, iface.getSubnetMask(), 0);
			ripEntry.setPermenent(true);
			synchronized (this.ripEntries) {
				ripEntries.add(ripEntry);
			}
			routeTable.insert(subnet,0, iface.getSubnetMask(),iface);
		}
		// Send Rip request
		this.requestRip();
		// start two threads as timer
		Thread ripResponder = new Thread(new RipResponder());
		Thread ripCleaner = new Thread(new RipCleaner());
		// Send unsolicited RIP response every 10 seconds
		ripResponder.start();
		// start ripCleaner, scan the rip entries every second
		ripCleaner.start();
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
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		}

	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();

        // Check if it is a RIP packet
		if (checkRIP(ipPacket, inIface)) {
			handleRipPacket(etherPacket, inIface);
			return;
		}

		System.out.println("Handle IP packet");

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

	private Boolean checkRIP(IPv4 ipPacket, Iface inIface) {
		if ((ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9") ||
				ipPacket.getDestinationAddress() == inIface.getIpAddress()) &&
				ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
			UDP udpPacket = (UDP) ipPacket.getPayload();
			return udpPacket.getDestinationPort() == UDP.RIP_PORT;
		}
		return false;
	}

	private void handleRipPacket(Ethernet etherPacket, Iface inIface) {
		System.out.println("Handle RIP packet");
		int MAX_COST = 16;
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		UDP udpPacket = (UDP) ipPacket.getPayload();
		RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
		if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST) {
			// handle request
			String srcIp = IPv4.fromIPv4Address(ipPacket.getSourceAddress());
			String srcMAC = etherPacket.getSourceMAC().toString();
			// DEBUG
			System.out.println("received request from ip " + srcIp);
			Ethernet replyPacket = preparePacket(false, srcIp, srcMAC);
			sendRipPacket(replyPacket, inIface);
		} else {
			// handle response
			List<RIPv2Entry> inEntries = ripPacket.getEntries();
			// set nextHop to be the one who send the packet
			int nextHop = ipPacket.getSourceAddress();
			synchronized (this.ripEntries) {
				for (RIPv2Entry e : inEntries) {
					int destIp = e.getAddress();
					int mask = e.getSubnetMask();
					int metric = e.getMetric() + 1;
					if (metric > MAX_COST) {
						// set MAX_COST to be infinity
						metric = MAX_COST;
					}
					int subnet = destIp & mask;
					// skip the entry connected to the router itself
					if ((inIface.getIpAddress() & mask) == subnet) {
						continue;
					}
					Boolean foundEntry = false;
					for (RIPv2Entry m : this.ripEntries) {
						if (((m.getAddress() & m.getSubnetMask()) == subnet) && (m.getSubnetMask() == mask)) {
							foundEntry = true;
							// update the entry stored
							if (m.getNextHopAddress() == nextHop) {
								if (metric < 16) m.update();
								m.setMetric(metric);
							} else {
								if (m.getMetric() >= metric) {
									m.setNextHopAddress(nextHop);
									m.setMetric(metric);
									if (metric < 16) m.update();
									this.routeTable.update(subnet, mask, nextHop, inIface);
								}
							}
							break;
						}
					}
					if (!foundEntry && metric < 16) {
						RIPv2Entry newEntry = new RIPv2Entry(subnet,nextHop, mask, metric);
						this.ripEntries.add(newEntry);
						this.routeTable.insert(subnet, nextHop, mask, inIface);
					}
				}
			}
			System.out.println(this.routeTable.toString());
		}
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
