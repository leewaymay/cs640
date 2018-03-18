package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;


/**
 * An entry in a switch table.
 * @author Wei Li
 */
public class SwitchEntry {
	/** host MAC address */
	private MACAddress hostMACAddress;

	/** Switch interface out which packets should be sent to reach
	 * the destination */
	private Iface iface;

	/** time stamp of the entry insertion **/
	private long timeStamp;

	/** default time to live is 15s **/
	private static long TTL = 15*1000;

	/**
	 * Create a new switch table entry.
	 * @param hostMACAddress host MAC address
	 * @param iface the router interface out which packets should
	 *        be sent to reach the destination or gateway
	 */
	public SwitchEntry(MACAddress hostMACAddress, Iface iface) {
		this.hostMACAddress = hostMACAddress;
		this.iface = iface;
		this.timeStamp = System.currentTimeMillis();
	}

	public boolean isValid() {
		return (System.currentTimeMillis() - timeStamp) <= TTL;
	}

	public Iface getIface() {
		return this.iface;
	}

	public String toString() {
		return String.format("%s\t%s\t%d", hostMACAddress.toString(), iface.toString(), timeStamp/1000);
	}
}
