
import java.nio.ByteBuffer;

/**
 *
 * @author shudong.zhou@bigswitch.com
 */
public class TCPPackage {
    protected int sequenceNumber;
    protected int acknowledgement;
    protected long timestamp;
    protected int length;
    protected byte SYN = 0;
    protected byte ACK = 0;
    protected byte FIN = 0;
    protected short checksum;
    protected byte[] data;

    /**
     * @return the SequenceNumber
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * @param SequenceNumber the SequenceNumber to set
     */
    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * @return the acknowledgement
     */
    public int getAcknowledgement() {
        return acknowledgement;
    }


    /**
     * @param acknowledgement the acknowledgement to set
     */
    public void setAcknowledgement(int acknowledgement) {
        this.acknowledgement = acknowledgement;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }


    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return the length
     */
    public int getLength() {
        return length;
    }


    /**
     * @param length the length to set
     */
    public void setLength(int length) {
        this.length = length;
    }


        /**
     * @return the SYN
     */
    public byte getSYN() {
        return SYN;
    }


    /**
     * @param SYN the SYN to set
     */
    public void setSYN(byte SYN) {
        this.SYN = SYN;
    }

        /**
     * @return the ACK
     */
    public byte getACK() {
        return ACK;
    }


    /**
     * @param ACK the ACK to set
     */
    public void setACK(byte ACK) {
        this.ACK = ACK;
    }

        /**
     * @return the FIN
     */
    public int getFIN() {
        return FIN;
    }


    /**
     * @param FIN the FIN to set
     */
    public void setFIN(byte FIN) {
        this.FIN = FIN;
    }
    
    public void setData(byte[] data){
        this.data = data;
    }

    public byte[] getData(){
        return this.data;
    }

    public short getChecksum(){
        return this.checksum;
    }

    public void setChecksum(short checksum){
        this.checksum = checksum;
    }
   
// ////////////////////////////////////////////////////////////////
//     /**
//      * @param destinationPort the destinationPort to set
//      */
//     public TCP setDestinationPort(short destinationPort) {
//         this.destinationPort = destinationPort;
//         return this;
//     }

//     /**
//      * @return the checksum
//      */
//     public short getChecksum() {
//         return checksum;
//     }
    
//     public int getSequence() {
//         return this.sequence;
//     }
//     public TCP setSequence(int seq) {
//         this.sequence = seq;
//         return this;
//     }
//     public int getAcknowledge() {
//         return this.acknowledge;
//     }
//     public TCP setAcknowledge(int ack) {
//         this.acknowledge = ack;
//         return this;
//     }
//     public byte getDataOffset() {
//         return this.dataOffset;
//     }
//     public TCP setDataOffset(byte offset) {
//         this.dataOffset = offset;
//         return this;
//     }
//     public short getFlags() {
//         return this.flags;
//     }
//     public TCP setFlags(short flags) {
//         this.flags = flags;
//         return this;
//     }
//     public short getWindowSize() {
//         return this.windowSize;
//     }
//     public TCP setWindowSize(short windowSize) {
//         this.windowSize = windowSize;
//         return this;
//     }
//     public short getTcpChecksum() {
//         return this.checksum;
//     }
//     public TCP setTcpChecksum(short checksum) {
//         this.checksum = checksum;
//         return this;
//     }
    
//     @Override
//     public void resetChecksum() {
//         this.checksum = 0;
//         super.resetChecksum();
//     }
    
//     public short getUrgentPointer(short urgentPointer) {
//         return this.urgentPointer;
//     }
//     public TCP setUrgentPointer(short urgentPointer) {
//         this.urgentPointer= urgentPointer;
//         return this;
//     }
//     public byte[] getOptions() {
//         return this.options;
//     }
//     public TCP setOptions(byte[] options) {
//         this.options = options;
//         this.dataOffset = (byte) ((20 + options.length + 3) >> 2);
//         return this;
//     }
//     /**
//      * @param checksum the checksum to set
//      */
//     public TCP setChecksum(short checksum) {
//         this.checksum = checksum;
//         return this;
//     }

public short calchecksum(byte[] data){
    int datalength = data.length;
    int count = 0;
    short value = 0;
    while(count + 1 < datalength ){  
        short top = (short) (data[count] << 8 );
        short bottom = (short) (data[count+1] & 0x00ff);
        int temp = (int) (top | bottom);
        if((value<0 && temp<0 )){
            value = (short)(((int)value + (int)temp)+1);
        }else{
            value = (short)((short)value + (short)temp);
        }
        count = count + 2;
    }
    if(count == (datalength-1)){
        short top = (short) (data[count] << 8 );
        short bottom = (short) (0);
        int temp = (int) (top | bottom);
        if((value<0 && temp<0 )){
            value = (short)(((int)value + (int)temp)+1);
        }else{
            value = (short)((short)value + (short)temp);
        }
    }
    value = (short)(~value);

    return value;
}

    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     *      -length : 0
     */
    public byte[] serialize(int length) {
        
        int headerlength;
        int dataOffset = 6;  // default header length
        headerlength = dataOffset << 2;
        byte[] buffer = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(buffer);

        bb.putInt(this.sequenceNumber);
        bb.putInt(this.acknowledgement);
        bb.putLong(this.timestamp);
        bb.putInt((int) (((this.SYN & 0x1) << 2)|((this.FIN & 0x1) << 1)|(this.ACK & 0x1) |(this.length << 3)));
        bb.putShort((short)0);
        bb.putShort(this.checksum);
        
        if (this.data != null)
            bb.put(this.data);

        // // compute checksum if needed
        // if (this.checksum == 0) {
        //     bb.rewind();
        //     int accumulation = 0;

        //     // compute pseudo header mac
        //     if (this.parent != null && this.parent instanceof IPv4) {
        //         IPv4 ipv4 = (IPv4) this.parent;
        //         accumulation += ((ipv4.getSourceAddress() >> 16) & 0xffff)
        //                 + (ipv4.getSourceAddress() & 0xffff);
        //         accumulation += ((ipv4.getDestinationAddress() >> 16) & 0xffff)
        //                 + (ipv4.getDestinationAddress() & 0xffff);
        //         accumulation += ipv4.getProtocol() & 0xff;
        //         accumulation += length & 0xffff;
        //     }

        //     for (int i = 0; i < length / 2; ++i) {
        //         accumulation += 0xffff & bb.getShort();
        //     }
        //     // pad to an even number of shorts
        //     if (length % 2 > 0) {
        //         accumulation += (bb.get() & 0xff) << 8;
        //     }

        //     accumulation = ((accumulation >> 16) & 0xffff)
        //             + (accumulation & 0xffff);
        //     this.checksum = (short) (~accumulation & 0xffff);
        //     bb.putShort(16, this.checksum);
        // }
        return buffer;
    }

    // /* (non-Javadoc)
    //  * @see java.lang.Object#hashCode()
    //  */
    // @Override
    // public int hashCode() {
    //     final int prime = 5807;
    //     int result = super.hashCode();
    //     result = prime * result + checksum;
    //     result = prime * result + destinationPort;
    //     result = prime * result + sourcePort;
    //     return result;
    // }

    // /* (non-Javadoc)
    //  * @see java.lang.Object#equals(java.lang.Object)
    //  */
    // @Override
    // public boolean equals(Object obj) {
    //     if (this == obj)
    //         return true;
    //     if (!super.equals(obj))
    //         return false;
    //     if (!(obj instanceof TCP))
    //         return false;
    //     TCP other = (TCP) obj;
    //     // May want to compare fields based on the flags set
    //     return (checksum == other.checksum) &&
    //            (destinationPort == other.destinationPort) &&
    //            (sourcePort == other.sourcePort) &&
    //            (sequence == other.sequence) &&
    //            (acknowledge == other.acknowledge) &&
    //            (dataOffset == other.dataOffset) &&
    //            (flags == other.flags) &&
    //            (windowSize == other.windowSize) &&
    //            (urgentPointer == other.urgentPointer) &&
    //            (dataOffset == 5 || options.equals(other.options));
    // }

    
    public TCPPackage deserialize(byte[] data) {
        int dataOffset = 6;
        ByteBuffer bb = ByteBuffer.wrap(data, 0, (dataOffset << 2));
        this.sequenceNumber = bb.getInt();
        this.acknowledgement = bb.getInt();
        this.timestamp = bb.getLong();
        int combine = bb.getInt();
        this.length = (int)((combine >> 3) & 0x1fffffff);
        this.SYN = (byte)((combine & 0x4) >> 2);
        this.FIN = (byte)((combine & 0x2) >> 1);
        this.ACK = (byte)(combine & 0x1);
        bb.getShort();
        this.checksum = bb.getShort();
        //
        ByteBuffer bb_data = ByteBuffer.wrap(data, (dataOffset << 2), this.length);
        this.data = new byte[this.length];
        bb_data.get(this.data,0,length);
        return this;
    }
}