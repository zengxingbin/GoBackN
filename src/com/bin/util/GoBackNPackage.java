package com.bin.util;

import java.net.DatagramPacket;

public class GoBackNPackage {
    public final static byte CONNECTION_TYPE = 0;//�������ӷ���
    public final static byte ACK_TYPE = 1;//ackȷ�ϰ�
    public final static byte DATA_TYPE = 2;//���͵����ݰ�
    public final static byte DISCONNECTION_TYPE = 3;//�Ͽ����ӷ���  
    
    //��������ͣ����кţ����������ݾ�Ϊһ���ֽ�,Ϊ�˼�������涨ÿ������Ĵ�СΪ3���ֽڣ�
    private byte type;
    private byte sequenceNum;
    private byte data;
    //���鳬ʱʱ��
    private long timeout;
    
    public GoBackNPackage(byte type,byte sequenceNum,byte charData) {
        
        this.type = type;
        this.sequenceNum = sequenceNum;
        this.data = (byte)charData;
    }
    public DatagramPacket getDatagramPacket() {
        //����һ���ֽ�����
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
