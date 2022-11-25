
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.io.File;
import java.io.FileInputStream;
import java.lang.Math;
import java.util.Arrays;
import java.net.SocketTimeoutException;

public class Sender{
    private enum Stage {
        Init,Trans,Term
    }
    private static final int headerlength = 24; 
    private Stage stage;
    private DatagramSocket sendingSocket;
    private int sequenceNumber;
    private short port;
    private InetAddress remoteIPAddress;
    private short remoteport;
    private String filename;
    private int mtu; // maximum transfer unit
    private int sws; // sliding window size
    private int acknowledgeNumber;
    private int acknowledgeTimes;
    private int receiverSequence;
    private boolean receiveSYN = false;
    private boolean receiveFIN = false;
    private boolean receiveACK = false;
    private boolean duplicateACK = false;
    private ReadBuffer readBuffer;
    private Timer timer;
    private long totalDataAmount = 0;
    private int numPackage = 0;
    private int numWrongCheckSum = 0;
    private int numRetransmission = 0;
    private int numDuplicateAck = 0;

    class ReadBuffer{
        private byte[] buffer;
        private int lastSegAcked;
        private int lastSegSent;
        private int lastSegRead;
        private int segExpected;
        private int length;
        private int endIndex;
        private int sws;
        private int mtu;
        private FileInputStream fileInputStream;
        private boolean end = false;

        public ReadBuffer(int sws, int mtu, String filename) throws Exception{
            this.sws = sws;
            this.mtu = mtu;
            this.length = 2*sws*mtu;
            this.buffer = new byte[this.length];
            this.lastSegAcked = 0;
            this.lastSegSent = 0;
            this.lastSegRead = sws;
            this.endIndex = length+1;
            File file = new File(filename);
            //System.out.println("File length: "+ file.length());
            if(file.length()%(long)mtu!=0){
                this.segExpected =(int) (file.length()/(long)mtu+(long)1);
            } else {
                this.segExpected =(int) (file.length()/(long)mtu);
            }
            fileInputStream = new FileInputStream(file);
            int result = fileInputStream.read(buffer,0,sws*mtu);
            if (result<sws*mtu){
                this.endIndex = result;
            }
        }
        public byte[] getData() throws Exception{
            //System.out.println("get data::   last seg sent: "+ lastSegSent+"last seg acked"+lastSegAcked);
            if (((lastSegSent-lastSegAcked) == sws)|| (lastSegSent==segExpected)){
                //System.out.println("No data!");
                return null;
            } else {
                int index = lastSegSent%(2*sws);
                lastSegSent++;
                if (lastSegSent==segExpected){
                    //System.out.println("Data length: "+(endIndex-index*mtu));
                    return Arrays.copyOfRange(this.buffer, index*mtu, endIndex);
                } else {
                    //System.out.println("Data length: "+((index+1)*mtu-index*mtu));
                    return Arrays.copyOfRange(this.buffer, index*mtu, (index+1)*mtu);
                }
                // TODO: Delete this!!!!
                // byte[] test = new byte[(Math.min((index+1)*mtu,endIndex)-index*mtu)];
                // for (int i = 0; i < test.length; i++){
                //     test[i] = (byte)(i%10);
                // }
                // return test;
                
            }
        }
        public byte[] getLastData() throws Exception{
            //System.out.println("get last data::   last seg sent: "+ lastSegSent+"last seg acked"+lastSegAcked);
            int index = lastSegAcked % (2*sws);
            if ((lastSegAcked+1) == segExpected){
                return Arrays.copyOfRange(this.buffer, index*mtu, endIndex);
            } else {
                return Arrays.copyOfRange(this.buffer, index*mtu, (index+1)*mtu);
            }
        }
        public void acknowledge() throws Exception{
            if(lastSegSent==lastSegAcked){
                return;
            } else {
                int index = lastSegRead;
                lastSegRead = (lastSegRead+1)%(2*sws);
                lastSegAcked++;
                if (endIndex != length+1){
                    //System.out.println("ack::  last seg sent: "+ lastSegSent+"last seg acked"+lastSegAcked);
                    return;
                }
                int result = fileInputStream.read(buffer,index*mtu,mtu);
                if (result != mtu){
                    if (result == -1)
                        result = 0;
                    endIndex = index*mtu+result;
                   // System.out.println("End index"+ endIndex);
                }
                //System.out.println("ack::  last seg sent: "+ lastSegSent+"last seg acked"+lastSegAcked);
            }
            return;
        }
        public void destroy() throws Exception{
            fileInputStream.close();
        }
        public boolean checkEndOfFile(){
            return (this.endIndex != length+1) && (lastSegSent==segExpected);
        }
        
    }

    class Timer{
        private long[] timestamps;
        private int[] sequences;
        private int[] retransmissions;

        public Timer(int sws){
            timestamps = new long[2*sws];
            sequences = new int[2*sws];
            retransmissions = new int[2*sws];
            Arrays.fill(timestamps,0);
        }
        public void setRetrans(int sequence){
            retransmissions[sequence%(2*sws)]++;
        }
        public void set(int sequence){
            timestamps[sequence%(2*sws)] = System.currentTimeMillis();
            sequences[sequence%(2*sws)]  = sequence;
        }
        public void clear(int sequence){
            timestamps[sequence%(2*sws)] = 0;
        }
        public int check(){
            long currentTime = System.currentTimeMillis();
            long duration = 0;
            int lowestsequence = -1;
            for(int i = 0; i < 2*sws; i++){
                if(retransmissions[i]>=16){
                    //transmit too many times
                    return -2;
                }
                if((timestamps[i]!=0)&&((currentTime-timestamps[i])>5000)){
                    //System.out.println("Timer alert! "+ sequences[i]);
                    if(duration<(currentTime-timestamps[i])){
                        duration = currentTime-timestamps[i];
                        lowestsequence = sequences[i];
                    }
                }
            }
            if(lowestsequence!=-1){
                //timeout
               // System.out.println("Timer alert! "+ lowestsequence);
            }
            
            return lowestsequence;
        }
        public void printTimestamp(){
            System.out.println("------------Timer dump----------------");
            Timestamp time = new Timestamp(System.currentTimeMillis());
            System.out.println("Current time: "+time.toString());
            for(int i = 0; i < 2*sws; i++){
                time = new Timestamp(timestamps[i]);
                System.out.println("Sequence number "+ sequences[i]+"time: "+time.toString());
            }
            System.out.println("------------Timer end----------------");
        } 
    }

    public Sender (short port, String DestIPAddress, short remoteport, String filename, int mtu, int sws) throws Exception{
        this.port = port;
        this.remoteIPAddress = InetAddress.getByName(DestIPAddress);   
        this.remoteport = remoteport;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        this.sequenceNumber = 0;
        this.acknowledgeNumber = 0;
        this.acknowledgeTimes = 0;
        this.stage = Stage.Init;
        this.sendingSocket = new DatagramSocket(port);
        this.readBuffer = new ReadBuffer(sws,mtu,filename);
        this.timer = new Timer(sws);
        start();
    }
    
    private void receiveMessage() throws Exception{
        DatagramPacket receivedPackage = new DatagramPacket(new byte[mtu+headerlength],mtu+headerlength);
        this.sendingSocket.setSoTimeout(1000);
        try {
            sendingSocket.receive(receivedPackage);
        } catch (SocketTimeoutException e) {
            this.sendingSocket.setSoTimeout(0);
            return;
        }
        this.sendingSocket.setSoTimeout(0);
        TCPPackage received = new TCPPackage();
        received.deserialize(receivedPackage.getData());
        short oldChecksum = received.getChecksum();
        received.setChecksum((short)0);
        short newChecksum = received.calchecksum(received.serialize(headerlength+received.getLength()));
        if(oldChecksum!=newChecksum){
            this.numWrongCheckSum++;
            //System.out.println("oldchecksum is : "+oldChecksum+"newchecksum is : "+newChecksum);
            return;
        }
        short flag = 0;
        if (received.getACK() == 1){
            receiveACK = true;
            if(this.acknowledgeNumber == received.getAcknowledgement()){
                this.numDuplicateAck++;
                duplicateACK = true;
            }
            this.acknowledgeTimes = received.getAcknowledgement()-this.acknowledgeNumber;
            this.acknowledgeNumber = received.getAcknowledgement();
            this.receiverSequence = received.getSequenceNumber();
            flag |= (short)4;
        } 
        if (received.getFIN() == 1){
            receiveFIN = true;
            this.receiverSequence = received.getSequenceNumber();
            flag |= (short)2;
        }
        if (received.getSYN() == 1){
            receiveSYN = true;
            this.receiverSequence = received.getSequenceNumber();
            flag |= (short)8;
        }
        messageUpdate(false,System.currentTimeMillis(),flag,this.receiverSequence,received.getLength(),received.getAcknowledgement());
    }
    private void sendSYNMessage(int sequence) throws Exception{
        TCPPackage send = new TCPPackage();
        send.setSYN((byte)1);
        send.setSequenceNumber(sequence);
        send.setTimestamp(System.currentTimeMillis());
        send.setLength(0);
        send.setChecksum(send.calchecksum(send.serialize(headerlength)));
        DatagramPacket sendpackage = new DatagramPacket(new byte[this.mtu+headerlength],this.mtu+headerlength,this.remoteIPAddress, this.port);
        sendpackage.setData(send.serialize(headerlength));
        sendingSocket.send(sendpackage);
        messageUpdate(true,System.currentTimeMillis(),(short)0x8,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }

    private void sendDataMessage(byte[] data, boolean resend) throws Exception{
        this.numPackage ++;
        TCPPackage send = new TCPPackage();
        if (!resend){
            send.setSequenceNumber(this.sequenceNumber);
            this.sequenceNumber++;
        } else {
            //System.out.println("duplicative data sent!");
            this.numRetransmission++;
            send.setSequenceNumber(this.acknowledgeNumber);
        }
        send.setACK((byte)1);
        send.setAcknowledgement(1);
        send.setTimestamp(System.currentTimeMillis());
        send.setLength(data.length);
        send.setData(data);
        send.setChecksum(send.calchecksum(send.serialize(mtu+headerlength)));
        this.totalDataAmount += data.length;
        DatagramPacket sendpackage = new DatagramPacket(new byte[mtu+headerlength],mtu+headerlength,remoteIPAddress, port);
        sendpackage.setData(send.serialize(mtu+headerlength));
        sendingSocket.send(sendpackage);
        messageUpdate(true,System.currentTimeMillis(),(short)0x5,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }

    private void sendACKMessage() throws Exception{
        TCPPackage send = new TCPPackage();
        send.setACK((byte)1);
        send.setAcknowledgement(this.receiverSequence+1);
        send.setTimestamp(System.currentTimeMillis());
        send.setLength(0);
        send.setChecksum(send.calchecksum(send.serialize(headerlength)));
        DatagramPacket sendpackage = new DatagramPacket(new byte[this.mtu+headerlength],this.mtu+headerlength,this.remoteIPAddress, this.port);
        sendpackage.setData(send.serialize(headerlength));
        sendingSocket.send(sendpackage);
        messageUpdate(true,System.currentTimeMillis(),(short)0x4,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }

    private void sendFINMessage(int sequence) throws Exception{
        TCPPackage send = new TCPPackage();
        send.setFIN((byte)1);
        send.setSequenceNumber(sequence);
        send.setTimestamp(System.currentTimeMillis());
        send.setLength(0);
        send.setChecksum(send.calchecksum(send.serialize(headerlength)));
        DatagramPacket sendpackage = new DatagramPacket(new byte[this.mtu+headerlength],this.mtu+headerlength,this.remoteIPAddress, this.port);
        sendpackage.setData(send.serialize(headerlength));
        sendingSocket.send(sendpackage);
        messageUpdate(true,System.currentTimeMillis(),(short)0x2,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }

    private void start() throws Exception{
        // initial stage
        this.sequenceNumber = 0;
        timer.set(this.sequenceNumber);
        //timer.printTimestamp();
        sendSYNMessage(this.sequenceNumber++);
        while (!receiveACK){
            int resend = timer.check();
            if(resend==-2){
                close();
                System.out.println("Error: Maximum retransimission number is reached.");
                return;
            }
            if(resend!=-1){
                timer.setRetrans(resend);
                sendSYNMessage(resend);
                timer.set(resend);
            }
            receiveMessage();
        }
        timer.clear(this.sequenceNumber-1);
        //timer.printTimestamp();
        receiveACK = false;
        while (!receiveSYN){
            receiveMessage();
        }
        receiveSYN = false;
        sendACKMessage();
        // transmission stage
        while ((!this.readBuffer.checkEndOfFile())||(this.acknowledgeNumber<this.sequenceNumber)){
            //System.out.println("Initial ack: "+this.acknowledgeNumber+ "sequence: "+this.sequenceNumber);
            byte[] data = this.readBuffer.getData();
            while (data != null){
                timer.set(this.sequenceNumber);
                //timer.printTimestamp();
                sendDataMessage(data, false);
                data = this.readBuffer.getData();
            }
            
            while (!receiveACK){
                int resend = timer.check();
                if(resend==-2){
                    close();
                    System.out.println("Error: Maximum retransimission number is reached.");
                    return;
                }
                if(resend!=-1){
                    timer.setRetrans(this.acknowledgeNumber);
                    sendDataMessage(this.readBuffer.getLastData(), true);
                    timer.set(resend);
                }
                receiveMessage();
            }
            
            //timer.printTimestamp();
            receiveACK = false;
            if (duplicateACK){
                duplicateACK = false;
                //System.out.println("Receive second duplicate message!");
                while (!receiveACK){
                    receiveMessage();
                }
                receiveACK = false;
                if (duplicateACK){
                    //System.out.println("Receive third duplicate message!");
                    duplicateACK = false;
                    sendDataMessage(this.readBuffer.getLastData(), true);
                }else {
                    for(int i = 0; i<this.acknowledgeTimes; i++){
                        this.readBuffer.acknowledge();
                        timer.clear(this.acknowledgeNumber-i-1);
                    }
                        
                }
            } else {
                for(int i = 0; i<this.acknowledgeTimes; i++){
                    this.readBuffer.acknowledge();
                    timer.clear(this.acknowledgeNumber-i-1);
                }
                    
            }
            //System.out.println("Still in Data transmission");
            //timer.printTimestamp();
        }
        this.readBuffer.destroy();
        // finishe stage
        timer.set(this.sequenceNumber);
        sendFINMessage(this.sequenceNumber++);
        while((!receiveACK)||(this.sequenceNumber!=this.acknowledgeNumber)){
            int resend = timer.check();
            if(resend==-2){
                close();
                System.out.println("Error: Maximum retransimission number is reached.");
                return;
            }
            if(resend!=-1){
                timer.setRetrans(resend);
                sendFINMessage(resend);
                timer.set(resend);
            }
            receiveMessage();
        }
        timer.clear(this.acknowledgeNumber-1);
        receiveACK = false;
        receiveFIN = false;
        sendACKMessage();
        long closeTime = System.currentTimeMillis()+20000;
        while((closeTime-System.currentTimeMillis())>0){
            if((receiveACK)||(receiveFIN)){
                sendACKMessage();
            }
            receiveFIN = false;
            receiveACK = false;
        }
        
        close();

    }


    private void close(){
        sendingSocket.close();
        System.out.println("Amount of Data transferred(KiB): "+(double)this.totalDataAmount/1000);
        System.out.println("Number of packets sent: "+this.numPackage); 
        System.out.println("Number of packets discarded due to incorrect checksum: "+this.numWrongCheckSum);
        System.out.println("Number of retransmissions: "+this.numRetransmission);
        System.out.println("Number of duplicate acknowledgements: "+0);
    }
    
    private void messageUpdate(boolean send, long timestamp, short flag, int sequenceNumber, int length, int acknowledgeNumber){
        Timestamp time = new Timestamp(timestamp);
        if (send){
            System.out.print("snd  ");
        } else {
            System.out.print("rcv  ");
        }
		System.out.print(time.toString()+"  ");
        if(((flag>>3)&0x1)==1){
            System.out.print("S ");
        } else {
            System.out.print("- ");
        }
        
        if(((flag>>2)&0x1)==1){
            System.out.print("A ");
        } else {
            System.out.print("- ");
        }

        if(((flag>>1)&0x1)==1){
            System.out.print("F ");
        } else {
            System.out.print("- ");
        }

        if((flag&0x1)==1){
            System.out.print("D ");
        } else {
            System.out.print("- ");
        }
        System.out.println(sequenceNumber+"  "+ length+"  "+ acknowledgeNumber);
    }

}