package com.bin.util;

import java.net.DatagramPacket;

public class GoBackNPackage {
    public final static byte CONNECTION_TYPE = 0;//请求连接分组
    public final static byte ACK_TYPE = 1;//ack确认包
    public final static byte DATA_TYPE = 2;//发送的数据包
    public final static byte DISCONNECTION_TYPE = 3;//断开连接分组  
    
    //分组的类型，序列号，包含的数据均为一个字节,为了简单起见，规定每个分组的大小为3个字节，
    private byte type;
    private byte sequenceNum;
    private byte data;
    //分组超时时间
    private long timeout;
    
    public GoBackNPackage(byte type,byte sequenceNum,byte charData) {
        
        this.type = type;
        this.sequenceNum = sequenceNum;
        this.data = (byte)charData;
    }
    public DatagramPacket getDatagramPacket() {
        //创建一个字节数组
        byte[] bytes = new byte[] {this.type,this.sequenceNum,this.data};
        return new DatagramPacket(bytes, bytes.length);
    }
    public GoBackNPackage (DatagramPacket pack) {
        byte[] data = pack.getData();
        this.type = data[0];
        this.sequenceNum = data[1];
        this.data = data[2];
    }
    public boolean isConnecPack() {
        return this.type == GoBackNPackage.CONNECTION_TYPE;
    }
    public boolean isDataPack() {
        return this.type == GoBackNPackage.DATA_TYPE;
    }
    public boolean isAckPack() {
        return this.type == GoBackNPackage.ACK_TYPE;
    }
    public boolean isDisConnecPack() {
        return this.type == GoBackNPackage.DISCONNECTION_TYPE;
    }
    public byte getType() {
        return type;
    }
    public void setType(byte type) {
        this.type = type;
    }
    public byte getSequenceNum() {
        return sequenceNum;
    }
    public void setSequenceNum(byte sequenceNum) {
        this.sequenceNum = sequenceNum;
    }
    public byte getData() {
        return data;
    }
    public void setData(byte data) {
        this.data = data;
    }
    @Override
    public String toString() {
        return "GoBackNPackage [type=" + type + ", sequenceNum=" + sequenceNum + ", data=" + (char)data + "]";
    }
    public long getTimeout() {
        return timeout;
    }
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    
    
}
