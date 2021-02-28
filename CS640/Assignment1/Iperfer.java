import java.net.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.IOException;

class Iperfer
{
    private static String host_name;
    private static int port_number;
    private static long start_time;
    private static long end_time;
    private static long time_span;
    //System.currentTimeMillis()
    public static void main (String[] args){
        //System.out.println("1");
        try {
            if (args[0].equals("-c")){
                
                if(args[2]!= null && args[4]!=null && args[6]!=null){
                    host_name = args[2];
                    port_number = Integer.parseInt(args[4]);
                    time_span = Integer.parseInt(args[6]);
                    if(port_number < 1024 || port_number > 65535){
                        throw new IOException(); 
                    }
                    //System.out.println("10");
                    client_mode();                  
                }
                else{throw new Exception();}
            } else if(args[0].equals("-s")){
                if(args[2] == null){
                    throw new Exception();
                }else{
                    port_number = Integer.parseInt(args[2]);
                    if(port_number < 1024 || port_number > 65535){
                        throw new IOException(); 
                    }
                }
                server_mode();
            }
        }catch(IOException n){
            System.out.println("Error: port number must be in the range 1024 to 65535");
        } catch (Exception e) {
            //TODO: handle exception
            System.out.println("Error: missing or additional arguments");
        } 
    }

    private static void client_mode(){
        long current_time = 0;
        int count=0;
        try {
            //System.out.println("30");
            Socket client_socket = new Socket(host_name, port_number);
            //System.out.println("40");
            OutputStream out = client_socket.getOutputStream();
            //System.out.println("50");
            
            start_time = System.currentTimeMillis();
            //System.out.println("3");
            while(true){
                byte[] input = new byte[1000];
                out.write(input);
                count++;
                //System.out.println("4");
                current_time = System.currentTimeMillis();
                if(current_time-start_time > time_span*1000) break;
            }
            
            //System.out.println("5");
            out.close();
            client_socket.close();
            double total_time = (double)(current_time-start_time)/1000;
            System.out.printf("sent=%d KB rate=%f Mbps\n", count, (double)(count*8/1000)/total_time);
        } catch (Exception e) {
            //TODO: handle exception
            e.printStackTrace();
            System.out.println(e);
        }
    }
    private static void server_mode(){
        long current_time = 0;
        byte[] buffer = new byte[1000];
        int count=0;
        int readsize=0;
        try {
            ServerSocket server_socket = new ServerSocket(port_number);
            Socket clientSocket = server_socket.accept();
            InputStream in = clientSocket.getInputStream();
            if (in.read()!=-1){
                start_time = System.currentTimeMillis();
                while((readsize = in.read(buffer))!=-1){
                    count+=readsize;
                }
                count++;
            }
            current_time = System.currentTimeMillis();
            in.close();
            server_socket.close();
            clientSocket.close();
            double total_time = (double)(current_time-start_time)/1000;
            System.out.printf("received=%d KB rate=%f Mbps\n", count/1000, (double)(count*8/1000000)/total_time);

        } catch (Exception e) {
            //TODO: handle exception
            e.printStackTrace();
            System.out.println(e);
        }
    }
}