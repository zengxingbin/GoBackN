package com.bin.gobackreceiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

import com.bin.util.GoBackNPackage;

public class GoBackReceiver {
    // ���ն�socket
    private DatagramSocket receiverSocket;
    // ���Ͷ˶˿ں͵�ַ
    private InetAddress senderAddress;
    private int senderPort;
    //�ڴ��յ�����һ������ı��
    private byte expectedSeqNum;
    StringBuffer message = new StringBuffer();
    // ����һ��GoBackReceiver
    public GoBackReceiver(int port) throws SocketException {
        this.receiverSocket = new DatagramSocket(port);// ��ָ���Ķ˿�
        // �ȴ����Ͷ�����
        waitForConnection();
    }

    private void waitForConnection() {
        System.out.println("���ն��������ȴ����ӡ�����");
        byte[] buf = null;
        DatagramPacket datagramPacket = null;
        while (true) {
            // �������ݻ�������
            buf = new byte[3];
            // ����udp����
            datagramPacket = new DatagramPacket(buf, buf.length);
            try {
                // ���ܴӷ��Ͷ˷��������ݱ�
                receiverSocket.receive(datagramPacket);
                // ��������
                GoBackNPackage pack = new GoBackNPackage(datagramPacket);
                System.out.println(pack);
                if(!pack.isConnecPack()) {//����������������ģ�����������ģ�ʲôҲ����,�����ȴ�����
                    System.out.println("����������");
                    continue;
                }
                else 
                    break;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            
        }
        //�������������л�ȡ���Ͷ˵�ַ�Ͷ˿ڣ�һ�����Ͷ˷���ackȷ�ϰ�
        this.senderAddress = datagramPacket.getAddress();
        this.senderPort = datagramPacket.getPort();
        System.out.println(this.senderAddress + ":" + this.senderPort + "�����ӣ�");
        this.expectedSeqNum = 0;
        //����ȷ�ϰ�
        sendAckPack(this.expectedSeqNum);
    }

    private void sendAckPack(byte expectedSeqNum) {
        GoBackNPackage ackPack = new GoBackNPackage(GoBackNPackage.ACK_TYPE, expectedSeqNum, (byte)'o');
        //����udp���ݰ�
        DatagramPacket dataPack = ackPack.getDatagramPacket();
        dataPack.setAddress(this.senderAddress);
        dataPack.setPort(this.senderPort);
        //����ȷ�ϰ�
        try {
            this.receiverSocket.send(dataPack);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public void receiveMessage() {
        System.out.println("��ʼ���շ��Ͷ˷��顣����");
        //���嶪����,�����ʣ�ʧ����,�ڽ��ն���ģ����Ķ�ʧ���𻵣�ʧ��
        double packetLoss = 0.2;
        double badPacketRate = 0.2;
        double disorderRate = 0.2;
        Random rand = new Random();
        double percentage;
        boolean flag = true;
        while(true) {
            byte[] buf = new byte[3];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                receiverSocket.receive(p);
                GoBackNPackage goNPackage = new GoBackNPackage(p);
                if(goNPackage.getType() == GoBackNPackage.DISCONNECTION_TYPE) {
                    //������ϣ���ӡ���յ�����Ϣ
                    System.out.println(message.toString());
                    return;
                }
                //ģ�����ʧ�򡣡�����ʹ�ö��̣߳����յ��İ�����������������ȷ��
                
                
                //ģ����Ķ�ʧ����30%�Ŀ��ܰ����ڴ�������ж�ʧ����ʱ���ն���ȻʲôҲ��֪�������Ͷ˷��͵ķ���ͻ�ò���ȷ�϶���ʱ�ش�
                //����һ�������
                percentage = rand.nextInt(11) / 10.0;
                if(percentage <= packetLoss) {
                    //�������������ʲôҲ����
                    System.out.println("����" + goNPackage.getSequenceNum() + "�ڴ�������ж�ʧ");
                    continue;
                }
                    
                
                if(goNPackage.getSequenceNum() == expectedSeqNum) {
                    /**
                     * �յ����ڴ��ķ��飬���÷����ڴ�������б����ˣ�����Ҫ�ش��ķ������ķ���
                     * �����������������ʲôҲ����
                     * 
                     */
                    percentage = rand.nextInt(11) / 10.0;
                    if(percentage <= badPacketRate) {
                        
                        System.out.println("�յ���" + goNPackage.getSequenceNum() + "������,���˷������𻵣�");
                        continue;
                        
                    }
                    System.out.println("��ȷ�յ�����" + goNPackage);
                    //�յ��ķ�������һ����ƴ���������γ�����������
                    message.append((char)goNPackage.getData());
                    //����ȷ�Ϸ���
                    sendAckPack(expectedSeqNum);
                    //�ڴ��յ�����һ������
                    expectedSeqNum++;
                }else {
                    /**
                     * 1.�յ��Ŀ������ظ��ķ��飨��Ϊ��Щ�����Ѿ������ն���ȷ�������Ѿ�����ȷ�ϣ���ȷ�϶�ʧ�ˣ����·��Ͷ����´��͵ķ��飩����ʱ����
                     * �����һ����С��expectedSeqNum��
                     * 2.Ҳ�п����Ƿ��Ͷ˷��͵�ĳһ�������ڴ�������ж�ʧ�ˣ��յ�����expectedSeqNum֮��ķ���
                     * ��ʱ���͹����ķ�������һ������expectedSeqNum�ģ�
                     * �ʷ��������
                     */
                    if(goNPackage.getSequenceNum() < expectedSeqNum) {
                        //�յ������ظ��ķ���
                        System.out.println("�յ��ظ�����:" + goNPackage);
                    }else if(expectedSeqNum < goNPackage.getSequenceNum()) {
                        System.out.println("�ڴ��յ���" + expectedSeqNum + "�����飬���յ���" + goNPackage.getSequenceNum()
                        + "������:" + goNPackage + "�����˷���,��" + expectedSeqNum + "~" + (goNPackage.getSequenceNum()-1) + 
                        "�����鶪ʧ����δ���򵽴��������");
                        
                    }
                    //���Ͷ���һ�������ȷ��
                    sendAckPack(--expectedSeqNum);
                    //�ڴ��յ���һ������
                    expectedSeqNum++;
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    public static void main(String[] args) throws SocketException {
        //����һ��GoBackReceiver
        GoBackReceiver receiver = new GoBackReceiver(8086);
        receiver.receiveMessage();
    }
}
