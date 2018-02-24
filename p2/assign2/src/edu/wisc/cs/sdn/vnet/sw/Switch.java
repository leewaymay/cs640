package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	/** switch table **/
	private Map<MACAddress, SwitchEntry> switchTable;


	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{

		super(host,logfile);
		switchTable = new HashMap<>();
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

		MACAddress hostMACAddress = etherPacket.getSourceMAC();
		MACAddress destMACAddress = etherPacket.getDestinationMAC();

		SwitchEntry newEntry = new SwitchEntry(hostMACAddress, inIface);
		switchTable.put(hostMACAddress, newEntry);
		SwitchEntry retrieveEntry = switchTable.get(destMACAddress);
		if (retrieveEntry != null && retrieveEntry.isValid()) {
			if (retrieveEntry.getIface().getName().equals(inIface.getName())) {
				// drop the frame, do nothing
			} else {
				sendPacket(etherPacket, retrieveEntry.getIface())
			}
		} else {
			for (Map.Entry<String, Iface> e : interfaces.entrySet()) {
				if (!e.getKey().equals(inIface.getName())) {
					sendPacket(etherPacket, e.getValue());
				}
			}
		}
		// clean expired switch entry
		if (retrieveEntry != null && !retrieveEntry.isValid()) {
			switchTable.remove(destMACAddress);
		}
	}
}
