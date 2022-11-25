package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	Map<MACAddress, info> SwitchTable;
	TableChecker check;
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.SwitchTable = new HashMap<MACAddress, info>();
		this.check = new TableChecker(SwitchTable);
		check.start();
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
		System.out.println("**Source"+inIface.toString()+"**");
		
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		//1. record incoming link, MAC address of sending host 
		//2. index switch table using MAC destination address
		//3. ifentry found for destination then {
		//	 if destination on segment from which frame arrived 
		//	 then drop frame else forward frame on interface indicated by entry 
		//	}else flood  /* forward on all interfaces except arriving                           interface */ 
		/********************************************************************/

		SwitchTable.put(etherPacket.getSourceMAC(),new info(inIface, (long) System.currentTimeMillis()));
		System.out.println(SwitchTable.toString());
		info entry = SwitchTable.get(etherPacket.getDestinationMAC());
		if (entry != null){
			if (entry.inter_face == inIface)
				return;
			else
				sendPacket(etherPacket, entry.inter_face);
		} else {
			Iterator it = interfaces.entrySet().iterator();
    		while (it.hasNext()) {
        	Map.Entry pair = (Map.Entry)it.next();
        	if(!pair.getValue().equals(inIface)){
				sendPacket(etherPacket,(Iface)pair.getValue());
				System.out.println("**flood to "+((Iface)pair.getValue()).toString()+"**");
			}
    	}
		}
		return;
	}	
}

class info
{
	public Iface inter_face;
	public long time;
	public info(Iface inter_face,long time){
		this.inter_face = inter_face;
		this.time = time;
	}
	@Override
    public String toString(){
		return "interface:"+inter_face.toString()+"time:"+time+"";
	}
}

class TableChecker extends Thread
{
	boolean running;
	private Map<MACAddress, info> SwitchTable;
	public TableChecker(Map<MACAddress, info> SwitchTable){
		this.SwitchTable = SwitchTable;
		this.running = true;
	}
	@Override
	public void run()
	{
		while(running){
		Iterator it = SwitchTable.entrySet().iterator();
    	while (it.hasNext()) {
        	Map.Entry pair = (Map.Entry)it.next();
        	if((long)System.currentTimeMillis()-((info)pair.getValue()).time > 15000){
				System.out.println("Removed!!");
				it.remove();
			}
    	}
		try{
			sleep(1000);
		} catch(Exception e){

		}
		}
	}
	public void setRunning(){
		this.running = false;
	}
}
