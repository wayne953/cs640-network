package edu.wisc.cs.sdn.vnet.rt;
import java.nio.ByteBuffer;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.IPv4;
import java.util.Iterator;
import net.floodlightcontroller.packet.Ethernet;
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
