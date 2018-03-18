package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
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
		// check packets
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			// drop the packet
			return;
		}
		IPv4 p = (IPv4) etherPacket.getPayload();
		// checksum
		short oldChecksum = p.getChecksum();
		p.setChecksum((short)0);
		byte[] data = p.serialize();
		p.deserialize(data,0, data.length);
		short newChecksum = p.getChecksum();
		if (oldChecksum != newChecksum) {
			return;
		}
		// TTL
		int ttl = p.getTtl() - 1;
		if (ttl == 0) {
			return;
		}
		p.setTtl((byte)ttl);
		// recalculate checksum after decrementing ttl
		p.setChecksum((short)0);
		data = p.serialize();
		p.deserialize(data,0, data.length);

		// Decide the interface
		int destIp = p.getDestinationAddress();
		for (Map.Entry<String, Iface> e : this.interfaces.entrySet()) {
			if (e.getValue().getIpAddress() == destIp) {
				return;
			}
		}
		RouteEntry e = routeTable.lookup(destIp);
		if (e != null) {
			int nextIp = e.getGatewayAddress();
			if (nextIp == 0) nextIp = destIp;
			ArpEntry arpEntry = arpCache.lookup(nextIp);
			etherPacket.setDestinationMACAddress(arpEntry.getMac().toString());
			etherPacket.setSourceMACAddress(e.getInterface().getMacAddress().toString());
			sendPacket(etherPacket, e.getInterface());
		}
	}
}
