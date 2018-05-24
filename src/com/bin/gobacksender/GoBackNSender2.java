package com.bin.gobacksender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.bin.util.GoBackNPackage;
class Sender implements Runnable {
    private GoBackNSender2 goBackNSender;
    public Sender(GoBackNSender2 goBackNSender) {
        this.goBackNSender = goBackNSender;
    }
    @Override
    public void run() {
        while(true) {
            if(goBackNSender.isOver()) {
                
             // ���ͶϿ������������
                goBackNSender.sendData(GoBackNPackage.DISCONNECTION_TYPE, (byte) goBackNSender.getNextSeqNum(), (byte) '$');
                System.out.println("���ͽ�����");
                break;
            }
            goBackNSender.sendMsg();
        }
        
    }
    
}
class Receiver implements Runnable {
    private GoBackNSender2 goBackNSender;
    public Receiver(GoBackNSender2 goBackNSender) {
        this.goBackNSender = goBackNSender;
    }
    @Override
    public void run() {
        while(true) {
            goBackNSender.reciveMsg();
            if (goBackNSender.getCounter() >= goBackNSender.getMessage().length()) {
                // �������
                goBackNSender.setOver(true);
                break;
            }
        }
    }
    
}
public class GoBackNSender2 {
    // ���Ͷ�socket��ʹ��udp���д���
    private DatagramSocket senderSocket;
    // ���ն˵�ַ�Ͷ˿�
    private InetAddress receiverAddress;
    private int receiverPort;
    // ���͵���Ϣ
    private String message;
    // �������ڴ�С
    private static int sizeOfWindow = 5;
    // ��ǰ���ڵ���ʼλ��
    private int start;
    // ��һ��Ҫ���͵ķ���ı��
    private int nextSeqNum;
    // �жϴ����Ƿ���������������߳��Լ������߳�֮��Ľ���
    private boolean isOver = false;
    // ͳ����ȷ�Ϸ���ĸ���
    private int counter = 0;
    // ���鱸�ݣ��ѷ��͵ȴ�ȷ��
    private ConcurrentLinkedQueue<GoBackNPackage> packageBak = new ConcurrentLinkedQueue<>();
    
    public GoBackNSender2() {
        
    }

    // ����һ��GoBackNSender
    public GoBackNSender2(String IP, int receiverPort) throws SocketException {
        System.out.println("���Ͷ��������������ӡ�����");
        try {
            this.receiverAddress = InetAddress.getByName(IP);
        } catch (UnknownHostException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            System.out.println("IP is not found");
        }
        this.receiverPort = receiverPort;

        this.senderSocket = new DatagramSocket();

        // ����һ��goback-package
        GoBackNPackage pack = new GoBackNPackage(GoBackNPackage.CONNECTION_TYPE, (byte) -1, (byte) '^');
        // ��ȡudp���ݰ�
        DatagramPacket connectPack = pack.getDatagramPacket();
        // ���ý��ն˵ĵ�ַ�Ͷ˿�
        connectPack.setAddress(this.receiverAddress);
        connectPack.setPort(this.receiverPort);
        // ��������������
        try {
            System.out.println("�����������ӷ���:" + pack);
            this.senderSocket.send(connectPack);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // �ȴ����������ȷ�ϰ�
        try {
            receiveAckPack(1000);
            System.out.println("���ӳɹ���");
            // �յ�ȷ�ϰ�����ʼ������������ʼλ�ã�������һ��Ҫ���͵İ���λ��
            this.start = 0;
            this.nextSeqNum = 0;
        } catch (SocketTimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    // ���ͷ���ķ���
    public synchronized void sendMsg() {
        if (nextSeqNum <= message.length() - 1) {
            if (nextSeqNum >= start + sizeOfWindow) {
                try {
                    System.out.println("���������������ߵȴ���");
                    this.wait();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            System.out.println("Sender��ʼ���͵�" + nextSeqNum + "����");
            sendData(GoBackNPackage.DATA_TYPE, (byte) nextSeqNum, (byte) message.charAt(nextSeqNum));
            nextSeqNum++;
            this.notify();

        }
    }

    public synchronized void reciveMsg() {
        if (packageBak.isEmpty()) {
            System.out.println("û�п�ȷ�ϵķ��飬�����ߵȴ���");
            try {
                this.wait();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        try {
            // ��ȷ�ϵķ��鳬ʱ����Ҫ�ش�
            if (System.currentTimeMillis() > packageBak.peek().getTimeout())
                throw new SocketTimeoutException();
            byte seqNum = receiveAckPack(packageBak.peek().getTimeout() - System.currentTimeMillis());
            // ---�������ģ��ȷ�϶�ʧ

            // -------------
            // �жϴ�ȷ�ϣ��Ǵ�ȷ�Ϸ�������е���һ�������ȷ��
            Iterator<GoBackNPackage> iterator = packageBak.iterator();
            boolean flag = false;// ���͹�����ȷ�ϣ��Ǵ�ȷ�Ϸ�������е�����һ��ȷ��
            while (iterator.hasNext()) {
                GoBackNPackage goBackNPackage = iterator.next();
                if (seqNum == goBackNPackage.getSequenceNum()) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                // ÿ�յ�һ�������ȷ�ϣ�������ǰ����һ��λ��
                while (start < seqNum + 1) {
                    start++;// �����ƶ�
                    counter++;// ȷ�ϸ�����һ
                    // �Ӵ�ȷ�ϵķ���������Ƴ��Ѿ�ȷ�ϵķ���
                    if (!packageBak.isEmpty()) {
                        GoBackNPackage goBackNPackage = packageBak.remove();
                        goBackNPackage.setType(GoBackNPackage.ACK_TYPE);
                        System.out.println("ȷ�Ϸ���:" + goBackNPackage);
                        this.notify();
                    }

                }

            } else {
                // ������Ǵ�ȷ�϶����з����ȷ�ϣ������ǳٵ���ȷ�ϣ���ʲôҲ���������������ȷ��
            }

        } catch (SocketTimeoutException e) {
            // ��ʱ�ش�
            // ��ȡ��ȷ�϶������һ����ʱ�ķ���
            GoBackNPackage goBackNPackage = packageBak.peek();
            System.out.println("�ڹ涨ʱ����δ�յ��� " + goBackNPackage.getSequenceNum() + "�������ȷ�ϣ�����ʱ�ش���"
                    + goBackNPackage.getSequenceNum() + "֮��ķ���");
            // ��մ�ȷ�϶���
            packageBak.clear();
            // �ӵ�һ����ʱ�ķ��鿪ʼ���ش��������г�ʱ�ķ���
            // start = goBackNPackage.getSequenceNum();
            nextSeqNum = start;
            this.notify();//���ѷ����߽����ش�
        }

    }


    public void sendData(byte type, byte seqNum, byte charData) {
        // ����GoBackNPackage
        GoBackNPackage goNPackage = new GoBackNPackage(type, seqNum, charData);
        goNPackage.setTimeout(System.currentTimeMillis() + 250);// ���ó�ʱʱ��
        System.out.println("���ͷ���" + goNPackage);
        // �����ѷ��ʹ�ȷ�϶���
        packageBak.add(goNPackage);
        DatagramPacket datagramPacket = goNPackage.getDatagramPacket();
        datagramPacket.setAddress(this.receiverAddress);
        datagramPacket.setPort(this.receiverPort);
        try {
            senderSocket.send(datagramPacket);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public byte receiveAckPack(long timeout) throws SocketTimeoutException {
        // �ֽڻ�������
        byte[] buf = new byte[3];
        // ����upd�û����ݱ������ڽ�������
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
        try {
            // ���ó�ʱʱ��
            this.senderSocket.setSoTimeout((int) timeout);
            this.senderSocket.receive(datagramPacket);
        } catch (IOException e) {
            if (e instanceof SocketTimeoutException)
                throw (SocketTimeoutException) e;
            else {
                System.out.println("����ʱ������һ������");
                System.exit(1);
            }
        }
        // ��ȡgoback���ݰ�
        GoBackNPackage pack = new GoBackNPackage(datagramPacket);
        // ��ӡack��
        // System.out.println("ȷ�Ϸ���" + pack);
        if (!pack.isAckPack()) {
            // �յ��İ�����ackȷ�ϰ�
            System.out.println("û���յ�ȷ�ϰ�");

        }
        return pack.getSequenceNum();
    }

    
    public boolean isOver() {
        return isOver;
    }

    public void setOver(boolean isOver) {
        this.isOver = isOver;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public String getMessage() {
        return message;
    }
    
    public int getNextSeqNum() {
        return nextSeqNum;
    }

    public void setNextSeqNum(int nextSeqNum) {
        this.nextSeqNum = nextSeqNum;
    }

    public static void main(String[] args) throws SocketException {
        // ����һ��GoBackSender����
        GoBackNSender2 goBackNSender = new GoBackNSender2("172.16.32.88", 8086);
        // ���÷��͵���Ϣ
        goBackNSender.setMessage("Hello world!");
        
        //�����߳�����
        Sender sender = new Sender(goBackNSender);
        Receiver receiver = new Receiver(goBackNSender);
        
        //�����̶߳���
        Thread sendThread = new Thread(sender);
        Thread receiveThread = new Thread(receiver);
        
        //�����߳�
        sendThread.start();
        receiveThread.start();
    }
}


