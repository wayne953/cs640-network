
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.sql.Timestamp;
import java.net.InetAddress;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math;
import java.util.Arrays;
import java.net.SocketTimeoutException;

public class Receiver{
    private enum Stage{
        Init, Trans, Term
    }
    private static final int headerlength = 24; 
    private Stage stage;
    private long timer;
    private DatagramSocket receivingSocket;
    private int sequenceNumber;
    private short port;
    private String filename;
    private int mtu; // maximum transfer unit
    private int sws; // sliding window size
    private int acknowledgeNumber;
    private int receiverSequence;
    private boolean receiveSYN = false;
    private boolean receiveFIN = false;
    private boolean receiveACK = false;
    private boolean receiveData = false;
    private boolean timeOut = false;
    private InetAddress sourceAddress; 
    private short sourcePort;
    private WriteBuffer writeBuffer;
    private long totalDataAmount = 0;
    private int numWrongCheckSum = 0;
    private int packetreceived = 0;
    private int numRetransmission = 0;
    private int numDuplicateAck = 0;

    class WriteBuffer{
        private byte[][] buffer;
        private int lastSegAcked;
        private int[] sequence;
        private int sws;
        private int mtu;
        private int length;
        private FileOutputStream fileOutputStream;
        //private int totalLength;
        

        public WriteBuffer(int sws, int mtu, String filename) throws Exception{
            this.sws = sws;
            this.mtu = mtu;
            this.length = 2*sws;
            this.buffer = new byte[length][mtu];
            this.sequence = new int[length];
            Arrays.fill(this.sequence,-1);
            this.sequence[length-1]=0;
            this.lastSegAcked = 0;
            //this.totalLength = 0;
            File file = new File(filename);
            fileOutputStream = new FileOutputStream(file);

        }
        public void writeData(int sequenceNum, byte[] data) throws Exception{
            //System.out.println("write sequence num: "+sequenceNum+"last seg ack: "+lastSegAcked+"last sequence num: "+this.sequence[(lastSegAcked+this.length-1)%this.length]);
            int index = (sequenceNum - this.sequence[(lastSegAcked+this.length-1)%this.length] + lastSegAcked - 1)%this.length;
            sequence[index] = sequenceNum;
            buffer[index] = data;
            //this.totalLength += data.length;
            commit();
            //print();
        }


        private void commit() throws Exception{
            while((this.sequence[(lastSegAcked+this.length-1)%this.length]==-1)
                ||(this.sequence[lastSegAcked] == this.sequence[(lastSegAcked+this.length-1)%this.length]+1)){
                    if(this.sequence[lastSegAcked]==-1)
                        return;
                    fileOutputStream.write(buffer[lastSegAcked]);
                    lastSegAcked = (lastSegAcked+1)%this.length;
                }
        }
        public int getNextAcknowledgeNumber(){
            return this.sequence[(lastSegAcked+this.length-1)%this.length]+1;
        }
        
        public void destroy() throws Exception{
            fileOutputStream.close();
        }
        public void print(){
            System.out.println("-----Sequence table----");
            for (int i = 0; i< this.length; i++){
                System.out.println("index"+i+": "+sequence[i]);
            }
            System.out.println("-----Sequence table end----");
            System.out.println("Last acknowledge: "+this.lastSegAcked);
        }
        
    }


    public Receiver (short port, String filename, int mtu, int sws) throws Exception{
        this.port = port;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        this.sequenceNumber = 0;
        this.stage = Stage.Init;
        this.receivingSocket = new DatagramSocket(port);
        this.writeBuffer = new WriteBuffer(sws,mtu,filename);
        start();
        
    }

    private void receiveMessage() throws Exception{
        //buffer has the size of mtu + header size
        DatagramPacket receivedPackage = new DatagramPacket(new byte[mtu+headerlength], mtu+headerlength);
        this.receivingSocket.setSoTimeout(5000);
        try {
            receivingSocket.receive(receivedPackage);
        } catch (SocketTimeoutException e) {
            this.timeOut = true;
            this.receivingSocket.setSoTimeout(0);
            return;
        }
        this.receivingSocket.setSoTimeout(0);
        this.sourceAddress = receivedPackage.getAddress();
        this.sourcePort = (short)receivedPackage.getPort();
        TCPPackage received = new TCPPackage();
        received.deserialize(receivedPackage.getData());
        this.totalDataAmount += received.getLength();
        short oldChecksum = received.getChecksum();
        received.setChecksum((short)0);
        short newChecksum = received.calchecksum(received.serialize(headerlength+received.getLength()));
        if(oldChecksum!=newChecksum){
            this.numWrongCheckSum++;
            System.out.println("oldchecksum is : "+oldChecksum+"newchecksum is : "+newChecksum);
            return;
        }
        short flag = 0;
        if(received.getACK() == 1){
            this.receiveACK = true;
            this.receiverSequence = received.getSequenceNumber();
            flag |= (short)4;
        }
        if(received.getFIN() == 1){
            this.receiveFIN = true;
            this.receiverSequence = received.getSequenceNumber();
            flag |= (short)2;
        }
        if(received.getSYN() == 1){
            this.receiveSYN = true;
            this.receiverSequence = received.getSequenceNumber();
            flag |= (short)8;
        }
        if(received.getLength()!=0){
            this.receiveACK = false;
            this.packetreceived++;
            if(this.writeBuffer.getNextAcknowledgeNumber() > received.getSequenceNumber()){
                sendACKMessage(this.writeBuffer.getNextAcknowledgeNumber());
                flag |= (short)1;
            } else {
                if(this.writeBuffer.getNextAcknowledgeNumber() > received.getSequenceNumber()){
                    this.receiveData = true;
                }
                //System.out.println("past sequence: "+this.receiverSequence+"now sequence: "+received.getSequenceNumber());
                this.receiverSequence = received.getSequenceNumber();
                flag |= (short)1;
                this.writeBuffer.writeData(received.getSequenceNumber(),received.getData());
                sendACKMessage(this.writeBuffer.getNextAcknowledgeNumber());
            }
        }else{}
        messageUpdate(false,System.currentTimeMillis(),flag,received.getSequenceNumber(),received.getLength(),this.acknowledgeNumber);
        
    }

    private void sendSYNMessage(int sequence)throws Exception{
        TCPPackage send = new TCPPackage();
        send.setSYN((byte)1);
        send.setSequenceNumber(sequence);
        DatagramPacket sendpackage = new DatagramPacket(new byte[this.mtu+headerlength],this.mtu+headerlength,this.sourceAddress,this.port);
        send.setTimestamp(System.currentTimeMillis());
        send.setChecksum(send.calchecksum(send.serialize(headerlength)));
        sendpackage.setData(send.serialize(headerlength));
        receivingSocket.send(sendpackage); 
        messageUpdate(true,System.currentTimeMillis(),(short)0x8,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }

    private void sendACKMessage(int acknowledgeNumber) throws Exception{
        TCPPackage send = new TCPPackage();
        send.setACK((byte)1);
        send.setAcknowledgement(acknowledgeNumber);
        DatagramPacket sendpackage = new DatagramPacket(new byte[this.mtu+headerlength],this.mtu+headerlength,this.sourceAddress, this.port);
        send.setTimestamp(System.currentTimeMillis());
        send.setChecksum(send.calchecksum(send.serialize(headerlength)));
        sendpackage.setData(send.serialize(headerlength));
        receivingSocket.send(sendpackage);
        messageUpdate(true,System.currentTimeMillis(),(short)0x4,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }   

    private void sendFINMessage(int sequence) throws Exception{
        TCPPackage send = new TCPPackage();
        send.setFIN((byte)1);
        send.setSequenceNumber(sequence);
        DatagramPacket sendpackage = new DatagramPacket(new byte[this.mtu+headerlength],this.mtu+headerlength,this.sourceAddress, this.port);
        send.setTimestamp(System.currentTimeMillis());
        send.setChecksum(send.calchecksum(send.serialize(headerlength)));
        sendpackage.setData(send.serialize(headerlength));
        receivingSocket.send(sendpackage);
        messageUpdate(true,System.currentTimeMillis(),(short)0x2,send.getSequenceNumber(),send.getLength(),send.getAcknowledgement());
    }

    private void start() throws Exception{
        // initial stage
        this.sequenceNumber = 0;
        while(!this.receiveSYN){
            receiveMessage();
            this.timeOut = false;
        }
        receiveSYN = false;
        sendACKMessage(this.receiverSequence+1);
        sendSYNMessage(this.sequenceNumber++);
        int count = 0;
        while (!receiveACK){
            if (this.timeOut){
                if(count==16){
                    System.out.println("Error: Maximum retransimission number is reached.");
                    close();
                    return;
                }
                count++;
                sendACKMessage(this.receiverSequence+1);
                sendSYNMessage(this.sequenceNumber-1);
                this.timeOut = false;
            }
            receiveMessage();
        }
        receiveACK = false;
        // transmission stage
        count = 0;
        while(!this.receiveFIN){
            if(this.timeOut){
                if(count==16){
                    System.out.println("Error: Maximum retransimission number is reached.");
                    close();
                    return;
                }
                count++;
                this.numDuplicateAck++;
                sendACKMessage(this.writeBuffer.getNextAcknowledgeNumber());
                this.timeOut = false;
            }
            receiveMessage();
            if(receiveData){
                count=0;
                receiveData = false;
            }
            
        }
        // finishing stage
        receiveFIN = false;
        sendFINMessage(this.sequenceNumber);
        sendACKMessage(this.receiverSequence+1);
        count = 0;
        while (!receiveACK){
            if (this.timeOut){
                if(count==16){
                    System.out.println("Error: Maximum retransimission number is reached.");
                    close();
                    return;
                }
                count++;
                sendFINMessage(this.sequenceNumber);
                sendACKMessage(this.receiverSequence+1);
                this.timeOut = false;
            }
            receiveMessage();
        }

       close();
    }

    private void close(){
        receivingSocket.close();
        System.out.println("Amount of Data received(KiB): " + (double)this.totalDataAmount/1000);
        System.out.println("Amount of packet received: " + this.packetreceived);
        System.out.println("Number of packets discarded due to incorrect checksum: "+this.numWrongCheckSum);
        System.out.println("Number of retransmissions: "+0);
        System.out.println("Number of duplicate acknowledgements: "+this.numDuplicateAck);
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