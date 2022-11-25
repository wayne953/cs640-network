

public class TCPend 
{
	private static final short DEFAULT_PORT = 8888;
	private static final String DEFAULT_SERVER = "localhost";
	
    private static final int Sender_Length = 12;
    private static final int Receive_Length = 8;

	public static void main(String[] args)
	{
		short port = DEFAULT_PORT;
		String remoteIPAddress = null;
		short remoteport = DEFAULT_PORT;
		String filename = null;
		int mtu = 0;
		int sws = 0;

        try{
            if(args.length <= 0){
                throw new IllegalArgumentException();
            }
            else if(args.length == Sender_Length){

                // Parse arguments
                for(int i = 0; i < args.length; i++)
                {
                    String arg = args[i];
                    if (arg.equals("-p")){
                        port = Short.parseShort(args[++i]);
                    }
                    else if(arg.equals("-s"))
                    {remoteIPAddress = (args[++i]);}
                    else if(arg.equals("-a"))
                    {remoteport = Short.parseShort(args[++i]);}
                    else if(arg.equals("-f"))
                    {filename = args[++i];}
                    else if(arg.equals("-m"))
                    {mtu = Integer.parseInt(args[++i]);}
                    else if(arg.equals("-c"))
                    {sws = Integer.parseInt(args[++i]);}
                }
                Sender sender = new Sender(port, remoteIPAddress, remoteport, filename, mtu, sws);
            }

            else if(args.length == Receive_Length){
                // Parse arguments
                for(int i = 0; i < args.length; i++)
                {
                    String arg = args[i];
                    if (arg.equals("-p")){
                        port = Short.parseShort(args[++i]);
                    }
                    else if(arg.equals("-f"))
                    {filename = args[++i];}
                    else if(arg.equals("-m"))
                    {mtu = Integer.parseInt(args[++i]);}
                    else if(arg.equals("-c"))
                    {sws = Integer.parseInt(args[++i]);}
                }
                Receiver receiver = new Receiver(port, filename, mtu, sws);
            }

        }
        catch (Exception e) {
            //TODO: handle exception
            e.printStackTrace();
            System.out.println(e);
        }

    }
}