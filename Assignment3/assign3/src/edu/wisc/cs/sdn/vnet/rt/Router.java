package edu.wisc.cs.sdn.vnet.rt;
import java.nio.ByteBuffer;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.IPv4;
import java.util.Iterator;
import net.floodlightcontroller.packet.Ethernet;
import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.IPacket;
import java.sql.Timestamp;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	private RipTableChecker ripTableChecker;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.ripTableChecker = null;
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
	 * initialize route table without static table
	 */
	public void initRIPTable() {
		ripTableChecker = new RipTableChecker(this);
		ripTableChecker.start();//start to run thread
		System.out.println("start initialize rip");
	}

	/**
	 * update Routetable with respect to RIPtable
	 */
	public void updateRouteTable(RipTableEntry tableEntry){
		// if(routeTable.isempty()){
		// 	routeTable.insert(updateEntry.ripentry.getAddress(), updateEntry.ripentry.getNextHopAddress(),
		// 	 updateEntry.ripentry.getSubnetMask(), updateEntry.outinterface);
		// 	 return;
		// }
		// for (RouteEntry routeEntry: routeTable){
		// 	if(updateEntry.ripentry.getAddress()==routeEntry.getDestinationAddress()){
		// 		routeEntry.setGatewayAddress(updateEntry.getNextHopAddress());
		// 		routeEntry.setInterface(updateEntry.outinterface);
		// 		return;
		// 	}
		// }
		// routeTable.insert(updateEntry.ripentry.getAddress(), updateEntry.ripentry.getNextHopAddress(),
		// 	 updateEntry.ripentry.getSubnetMask(), updateEntry.outinterface);
		// 	 return;
		if (tableEntry.ripentry.getMetric()==16 && tableEntry.ripentry.getNextHopAddress()!=0)
			routeTable.remove(tableEntry.ripentry.getAddress(), tableEntry.ripentry.getSubnetMask());
		if (!routeTable.update(tableEntry.ripentry.getAddress(), tableEntry.ripentry.getSubnetMask(),
		 	tableEntry.ripentry.getNextHopAddress(), tableEntry.outinterface)){
			routeTable.insert(tableEntry.ripentry.getAddress(), tableEntry.ripentry.getNextHopAddress(),
			tableEntry.ripentry.getSubnetMask(), tableEntry.outinterface);
		}
		// System.out.println("--------RouteTable Update-----------------");
		// System.out.println(routeTable.toString());
		return;
	}

	/**
	 * send request.
	 * specific 
	 */
	public void requestRIPTable()
	{
		System.out.println("Request RIP table");
		Iterator it = interfaces.entrySet().iterator();
    	while (it.hasNext()) {
        	Map.Entry pair = (Map.Entry)it.next();
			Ethernet requestEthernet = new Ethernet();
			IPv4 requestIPV4 = new IPv4();
			UDP requestUDP = new UDP();
			RIPv2 requestRIP = new RIPv2();
			requestRIP.setCommand(RIPv2.COMMAND_REQUEST);
			// if (!ripTableChecker.RipTable.isempty()){
			// 	list<RIPv2Entry> entries = new list<RIPv2Entry>();
			// 	for(RipTableEntry entry: ripTableChecker.RipTable){
			// 		entries.insert(entry);
			// 	}
			// 	requestRIP.setEntries(entries);
			// }
			List<RIPv2Entry> entries = new LinkedList<RIPv2Entry>();
			entries.add(new RIPv2Entry(((Iface)pair.getValue()).getIpAddress(), ((Iface)pair.getValue()).getSubnetMask(),0));
			requestRIP.setEntries(entries);
			requestUDP.setSourcePort(UDP.RIP_PORT);
			requestUDP.setDestinationPort(UDP.RIP_PORT);
			requestIPV4.setTtl((byte)17);
			requestIPV4.setProtocol(IPv4.PROTOCOL_UDP);
			requestIPV4.setDestinationAddress("224.0.0.9");
			requestIPV4.setSourceAddress(((Iface)pair.getValue()).getIpAddress());
			requestEthernet.setEtherType(Ethernet.TYPE_IPv4);
			requestEthernet.setSourceMACAddress(((Iface)pair.getValue()).getMacAddress().toBytes());
			requestEthernet.setDestinationMACAddress(MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toBytes());
			requestUDP.setPayload(requestRIP);
			requestIPV4.setPayload(requestUDP);
			requestEthernet.setPayload(requestIPV4);
			sendPacket(requestEthernet, (Iface)pair.getValue());

			RIPv2Entry neighborEntry= new RIPv2Entry(((Iface)pair.getValue()).getIpAddress() & ((Iface)pair.getValue()).getSubnetMask(),
				 ((Iface)pair.getValue()).getSubnetMask(),0);
			ripTableChecker.addRIPTable(neighborEntry, (Iface)pair.getValue(), IPv4.toIPv4Address("0.0.0.0"));
    	}
	}

	/**
	 * send packet respond message
	 * boardcast
	 * unsolicited RIP responses
	 */
	public void sendUnsolicitedRIPResponses(){
		// List<RIPv2Entry> entries = new LinkedList<RIPv2Entry>();
		// for(RipTableEntry entry: ripTableChecker.RipTable){
		// 	if(entry.RouteChange){
		// 	 	entries.add(entry.ripentry);
		// 		entry.RouteChange = false;
		// 		entry.timeout = (long)System.currentTimeMillis();//update timeout when update
		// 	}
		// }
		Timestamp time = new Timestamp(System.currentTimeMillis());
		System.out.println("------------------Sending regular check: "+ time.toString()+"-----------");
		
		//send to all iface
		Iterator it = interfaces.entrySet().iterator();
    	while (it.hasNext()) {
        	Map.Entry pair = (Map.Entry)it.next();
			Ethernet respondEthernet = new Ethernet();
			IPv4 respondIPV4 = new IPv4();
			UDP respondUDP = new UDP();
			RIPv2 respondRIP = new RIPv2();
			respondRIP.setCommand(RIPv2.COMMAND_RESPONSE);
			// if (!ripTableChecker.RipTable.isempty()){
			//set new list for change
			List<RIPv2Entry> entries = ripTableChecker.tableToList(IPv4.toIPv4Address("224.0.0.9"));
			if (entries == null)
				return;
			respondRIP.setEntries(entries);
			
			respondUDP.setSourcePort(UDP.RIP_PORT);
			respondUDP.setDestinationPort(UDP.RIP_PORT);
			respondIPV4.setTtl((byte)17);
			respondIPV4.setProtocol(IPv4.PROTOCOL_UDP);
			respondIPV4.setDestinationAddress("224.0.0.9");
			respondIPV4.setSourceAddress(((Iface)pair.getValue()).getIpAddress());
			respondEthernet.setEtherType(Ethernet.TYPE_IPv4);
			respondEthernet.setSourceMACAddress(((Iface)pair.getValue()).getMacAddress().toBytes());
			respondEthernet.setDestinationMACAddress(MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toBytes());
			respondUDP.setPayload(respondRIP);
			respondIPV4.setPayload(respondUDP);
			respondEthernet.setPayload(respondIPV4);
			sendPacket(respondEthernet, (Iface)pair.getValue());
    	}
	}

	public void handleRIPPacket(Ethernet pktEthernet, Iface inIface) {

		IPv4 pktIPV4 = (IPv4)pktEthernet.getPayload();
		UDP pktUDP = (UDP)pktIPV4.getPayload();
		RIPv2 pktRIP = (RIPv2)pktUDP.getPayload();
		//System.out.println("*********Receive RIP message****************");
		//System.out.println(pktEthernet.toString());
		
		
		if(pktRIP.getCommand()==RIPv2.COMMAND_REQUEST){
			System.out.println("receive request message");
			// prepare the package sending routing table
			Ethernet respondEthernet = new Ethernet();
			IPv4 respondIPV4 = new IPv4();
			UDP respondUDP = new UDP();
			RIPv2 respondRIP = new RIPv2();
			respondRIP.setCommand(RIPv2.COMMAND_RESPONSE);
			respondRIP.setEntries(ripTableChecker.tableToList(pktIPV4.getSourceAddress()));
			// if (ripTableChecker.RipTable != null){
			// 	List<RIPv2Entry> entries = new LinkedList<RIPv2Entry>();
			// 	for(RipTableEntry entry: ripTableChecker.RipTable){
			// 		if(entry.ripentry.getNextHopAddress() == pktIPV4.getSourceAddress())
			// 			entry.ripentry.setMetric(16);
			// 		entries.add(entry.ripentry);
			// 	}
			// 	respondRIP.setEntries(entries);
			// } 
			// RIPv2Entry neighborEntry= ((LinkedList<RIPv2Entry>)pktRIP.getEntries()).getFirst();
			// neighborEntry.setMetric(neighborEntry.getMetric()+1);
			// ripTableChecker.addRIPTable(neighborEntry, inIface, pktIPV4.getSourceAddress());
			//set new list for change
			respondUDP.setSourcePort(UDP.RIP_PORT);
			respondUDP.setDestinationPort(UDP.RIP_PORT);
			respondIPV4.setTtl((byte)17);
			respondIPV4.setProtocol(IPv4.PROTOCOL_UDP);
			respondIPV4.setDestinationAddress(pktIPV4.getSourceAddress());
			respondIPV4.setSourceAddress(inIface.getIpAddress());
			respondEthernet.setEtherType(Ethernet.TYPE_IPv4);
			respondEthernet.setSourceMACAddress(inIface.getMacAddress().toBytes());
			respondEthernet.setDestinationMACAddress(pktEthernet.getSourceMACAddress());
			respondUDP.setPayload(respondRIP);
			respondIPV4.setPayload(respondUDP);
			respondEthernet.setPayload(respondIPV4);
			sendPacket(respondEthernet, inIface);
			System.out.println("package sent");
			
		}else if (pktRIP.getCommand() == RIPv2.COMMAND_RESPONSE){
			Timestamp time = new Timestamp(System.currentTimeMillis());
			System.out.println("------------------Receiving regular check: "+ time.toString()+"-----------");
			//System.out.println("receive response message");
			for (RIPv2Entry ripEntry: pktRIP.getEntries()){
				if(ripEntry.getMetric()<0 || ripEntry.getMetric()>16){
					System.out.println("Metric is wrong!!!!");
					continue;
				}
				if(ripEntry.getNextHopAddress()==inIface.getIpAddress()){
					continue;
				}
				ripEntry.setMetric((ripEntry.getMetric()+1>16) ? 16:(ripEntry.getMetric()+1));
				ripEntry.setAddress(ripEntry.getAddress() & ripEntry.getSubnetMask());
				ripTableChecker.addRIPTable(ripEntry,inIface, pktIPV4.getSourceAddress());
				
			}
			
		}
		return;
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
		//System.out.println("*** -> Received packet: " +
		//		etherPacket.toString().replace("\n", "\n\t"));
		//try{
		/********************************************************************/
		/* TODO: Handle packets                */
		// check package
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
      		//System.out.println("here");
			return;
      	}
		IPv4 header = (IPv4)etherPacket.getPayload();
    	//System.out.println(calchecksums(header)+"  "+header.getChecksum());
		if (!checksums(header)){
			//System.out.println("checksum wrong!");
			return;
		}
		header.setTtl((byte)(header.getTtl()-1));
		if (header.getTtl() <= 0){
			//System.out.println("TTL wrong!");
			return;
		}

		if(header.getProtocol()==IPv4.PROTOCOL_UDP){
			UDP headerUDP = (UDP)header.getPayload();
			if(headerUDP.getSourcePort()==UDP.RIP_PORT && 
				headerUDP.getDestinationPort()==UDP.RIP_PORT){
				handleRIPPacket(etherPacket, inIface);
				return;
			}
		}

			
		Iterator it = interfaces.entrySet().iterator();
    	while (it.hasNext()) {
        	Map.Entry pair = (Map.Entry)it.next();
        	if(((Iface)pair.getValue()).getIpAddress() == (header.getDestinationAddress())){
				return;
        
				//System.out.println("**flood to "+((Iface)pair.getValue()).toString()+"***");
			}
    	}
		// forward package
		RouteEntry outPort = routeTable.lookup(header.getDestinationAddress());
		//System.out.println("output interface: "+outPort.getInterface().toString());
		if (outPort == null)
			return;
		ArpEntry ArpOut = null;
		if (outPort.getGatewayAddress()==0)
			ArpOut = arpCache.lookup(header.getDestinationAddress());
		else
			ArpOut = arpCache.lookup(outPort.getGatewayAddress());
		
		etherPacket.setDestinationMACAddress(ArpOut.getMac().toBytes());
		etherPacket.setSourceMACAddress(outPort.getInterface().getMacAddress().toBytes());
		header.setChecksum(calchecksums(header));
		
   
    //System.out.println("*router IP:*"+IPv4.fromIPv4Address(outPort.getDestinationAddress())+"  Arp Mac"+ArpOut.getMac().toString());
    //System.out.println("*** -> Send packet: " +
		//		etherPacket.toString().replace("\n", "\n\t"));
    //System.out.flush();
    sendPacket(etherPacket, outPort.getInterface());
		/********************************************************************/
   //}
   //catch(Exception e){
   //  e.printStackTrace();
   //  System.out.println(e);
   //}
	}

	public short calchecksums(IPv4 header){
		short checksumCompute;
		byte[] data = new byte[header.getTotalLength()];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.put((byte) (((header.getVersion() & 0xf) << 4) | (header.getHeaderLength() & 0xf)));
        bb.put(header.getDiffServ());
        bb.putShort(header.getTotalLength());
        bb.putShort(header.getIdentification());
        bb.putShort((short) (((header.getFlags() & 0x7) << 13) | (header.getFragmentOffset() & 0x1fff)));
        bb.put(header.getTtl());
        bb.put(header.getProtocol());
        //bb.putShort(header.getChecksum());
        bb.putShort((short)0);
        bb.putInt(header.getSourceAddress());
        bb.putInt(header.getDestinationAddress());
        if (header.getOptions() != null)
            bb.put(header.getOptions());

        bb.rewind();
        int accumulation = 0;
        for (int i = 0; i < header.getHeaderLength() * 2; ++i) {
            accumulation += 0xffff & bb.getShort();
        }
        accumulation = ((accumulation >> 16) & 0xffff)
                + (accumulation & 0xffff);
        checksumCompute = (short) (~accumulation & 0xffff);
		return checksumCompute;
	}


	public boolean checksums (IPv4 header) {
		return (calchecksums(header)==header.getChecksum());
        
	}


}

class RipTableEntry
{
	RIPv2Entry ripentry;
	boolean RouteChange;
	Iface outinterface;
	//current time 
	long timeout;
	public RipTableEntry(RIPv2Entry ripentry, boolean RouteChange, long timeout, Iface outinterface){
		this.ripentry = ripentry;
		this.RouteChange = RouteChange;
		//current time 
		this.timeout = timeout;
		this.outinterface = outinterface;
	}
	public String toString(){
		String result = "RIPEntry: ";
		Timestamp time = new Timestamp(timeout);
		result = result + ripentry.toString() + ((RouteChange) ? "True": "False") + outinterface.getName() + " register time: "+ time.toString();
		return result;
	}
}

class RipTableChecker extends Thread
{
	boolean running;
	List<RipTableEntry> RipTable;
	Router parent;
	//for sending unsolicited update every 10 sec
	long timemark;

	public RipTableChecker(Router parent){
		this.RipTable = new LinkedList<RipTableEntry>();
		this.running = true;
		this.parent = parent;
		this.timemark = System.currentTimeMillis();

	}
	@Override
	public void run()
	{
		parent.requestRIPTable();
		System.out.println("rip table");
		
		while(running){
			long timecur = System.currentTimeMillis();
			if (RipTable != null){
				synchronized(this.RipTable){
				for (RipTableEntry tableEntry: RipTable){
					
					if((timecur-tableEntry.timeout)>30000 && tableEntry.ripentry.getNextHopAddress()!=0){
						tableEntry.ripentry.setMetric(16);
						tableEntry.RouteChange = true;
						// System.out.println("-------After checking timeout----------");
						// printTable();
					}

					if(tableEntry!=null && tableEntry.RouteChange){
						parent.updateRouteTable(tableEntry);
						
					}
				}
				
				}
				//if exceed time of 10s update
				if(timecur-timemark > 10000){
					parent.sendUnsolicitedRIPResponses();
					timemark = System.currentTimeMillis();
				}
				
				
			}
			
		}
    }
		
	public void setRunning(){
		this.running = false;
	}
	/**
	 *  newEntry: new entry to get added
	 * 	ip: the interface link to that route
	 * 	nexthopaddress: next hopping address
	 */
	public void addRIPTable(RIPv2Entry newEntry, Iface sendInterface, int nextHopAddress){
		synchronized(this.RipTable)
		{
			Timestamp time = new Timestamp(System.currentTimeMillis());
			System.out.println("------------------Processing add table: "+ time.toString()+"-----------");
		if(RipTable!=null){
			for(RipTableEntry entry: RipTable){
				//go through
				if(entry.ripentry.getAddress() == newEntry.getAddress()){

					if(entry.ripentry.getNextHopAddress() == nextHopAddress){

						entry.timeout = System.currentTimeMillis();

						if(entry.ripentry.getMetric() != newEntry.getMetric()){
							entry.ripentry.setMetric(newEntry.getMetric());
							entry.RouteChange = true;
							
						}

					} else if (entry.ripentry.getMetric()> newEntry.getMetric()){
						
						entry.ripentry.setMetric(newEntry.getMetric());
						entry.ripentry.setNextHopAddress(nextHopAddress);
						entry.outinterface = sendInterface;
						entry.RouteChange = true;
					}
					printTable();
					return;
				}	
			}
		}
		if (newEntry.getMetric()==16)
			return;
		
		newEntry.setNextHopAddress(nextHopAddress);
		RipTable.add(new RipTableEntry(newEntry,
		true,(long)System.currentTimeMillis(),sendInterface));
		printTable();
	}
	return;
	}

	public void printTable(){
		System.out.println("*********Print RIP table**************");
		Timestamp time = new Timestamp(System.currentTimeMillis());
		System.out.println("cuurent time: "+ time.toString());
		for(RipTableEntry entry: RipTable){
			System.out.println(entry.toString());
		}
	}
	
	public LinkedList<RIPv2Entry> tableToList(int ip){
		List<RIPv2Entry> entries = new LinkedList<RIPv2Entry>();
		synchronized(this.RipTable){
		if (RipTable != null){
			for(RipTableEntry entry: RipTable){
					RIPv2Entry newEntry = new RIPv2Entry(entry.ripentry.getAddress(),entry.ripentry.getSubnetMask(), entry.ripentry.getMetric());
				if(newEntry.getNextHopAddress() == ip)
					newEntry.setMetric(16);    //posion the ip on the same path
				//add all the rip table list into linkedlist					
				entries.add(newEntry);
			}
			//respondRIP.setEntries(entries);
		} 
		}	
		return (LinkedList<RIPv2Entry>)entries;
		
	}

	public LinkedList<RIPv2Entry> changedEntries(){
		List<RIPv2Entry> entries = new LinkedList<RIPv2Entry>();
		synchronized(this.RipTable){
		for(RipTableEntry entry: RipTable){
			//find the entries changed in the
			if(entry.RouteChange){
			 	entries.add(entry.ripentry);
				entry.RouteChange = false;
				entry.timeout = (long)System.currentTimeMillis();//update timeout when update
			}
		}
		}
		return (LinkedList<RIPv2Entry>)entries;
	}


}
