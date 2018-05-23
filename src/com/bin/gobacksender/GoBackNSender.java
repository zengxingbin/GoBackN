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

public class GoBackNSender {
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
    // ���鱸�ݣ��ѷ��͵ȴ�ȷ��
    private ConcurrentLinkedQueue<GoBackNPackage> packageBak = new ConcurrentLinkedQueue<>();
    private Thread sendThread = new Thread() {

        @Override
        public void run() {
            System.out.println("��ʼ���ͷ��顣����");
            while (!isOver) {

                try {
                    Thread.currentThread().sleep(20);
                } catch (InterruptedException e) { // TODO Auto-generated catch
                                                   // block
                    e.printStackTrace();
                }

                synchronized (GoBackNPackage.class) {

                    System.out.print("sender");
                    if (nextSeqNum <= message.length() - 1) {

                        if (nextSeqNum < start + sizeOfWindow) { //
                            System.out.println("in send" + nextSeqNum);
                            sendData(GoBackNPackage.DATA_TYPE, (byte) nextSeqNum, (byte) message.charAt(nextSeqNum));
                            nextSeqNum++;
                        } else {
                            System.out.println("����������");
                        }

                    }
                }

            }
            // ���ͶϿ������������
            sendData(GoBackNPackage.DISCONNECTION_TYPE, (byte) nextSeqNum, (byte) '$');
            System.out.println("���ͽ�����");
        }

    };
    private Thread receiveThread = new Thread() {
        @Override
        public void run() {
            System.out.println("��ʼ����ackȷ�ϰ�������");
            int counter = 0;// ��ȷ�Ϸ�����м�������ȷ�Ϸ���������ڷ��͵��ܷ�����ʱ����ȷ��
            double ackLoss = 0.2;// ȷ�϶�ʧ�Ŀ���Ϊ0.2
            Random rand = new Random();
            double percentage;
            while (true) {
                try {
                    Thread.currentThread().sleep(50);
                } catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                // System.out.println("receiver");
                // System.out.println("��ѭ��");
                synchronized (GoBackNPackage.class) {

                    if (!packageBak.isEmpty()) {

                        try {
                            // ���ܷ������Ǵ�ȷ�Ϸ�������еڼ��������ȷ�ϣ���֮���ܵ�һ����ȷ�Ϸ��鲻�ܳ�ʱ������Ҫ�����ش���һ����������һ������ȷ�Ϸ���֮��ķ���(��һ������ʱ�ˣ�����Ŀ϶�����ʱ��)
                            if (System.currentTimeMillis() > packageBak.peek().getTimeout())
                                throw new SocketTimeoutException();
                            byte seqNum = receiveAckPack(packageBak.peek().getTimeout() - System.currentTimeMillis());
                            // ģ��ȷ�϶�ʧ�����,ȷ�϶�ʧ�����ش�
                            /*
                             * percentage = rand.nextInt(11) / 10.0; if
                             * (percentage <= 0.2) {
                             * System.out.println(percentage + "��ʧȷ��" + seqNum);
                             * continue; }
                             */

                            // �����Ŀ������ظ�ȷ�ϣ�����һ���Ѿ�ȷ�ϵķ���ı�ţ���ʱ��������ظ�ȷ��
                            /*
                             * *���ܻᷢ�����Ͷ˷��͵�ȷ�ϳٵ���ȷ�϶�ʧ��������Լ����Ͷ˷����ۻ�ȷ�ϣ�
                             * �����ض�ÿһ�����鷢��ȷ�ϣ�
                             * ���յ���������󣬶��յ������һ�����飬����ȷ�ϣ���ʾ�������֮��ķ��鶼�Ѿ���ȷ�յ��ˣ�
                             * 1.����ȷ�ϳٵ����յ��ٵ���ȷ�϶������ȷ�ϣ�����ʲôҲ����
                             * 2.����ȷ�϶�ʧ�������������ǰһ������ǰ��������ȷ�Ϸ���ĵ�ȷ�϶�ʧ�ˣ�ֻҪ���Ͷ���
                             * ǰһ������ǰ��������ȷ�ϵķ���δ��ʱ������£���������һ�������¼������������ȷ�ϣ�
                             * ���ʾǰ��ķ��� ���Ѿ������յ��ˣ����ǰ��ȷ�϶�ʧ�ķ��鲻���ٽ����ش�
                             * 
                             */
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
                            // System.out.println("ȷ�Ϸ���" + start + "~" +
                            // seqNum);
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
                                    }

                                }

                            } else {
                                // ������Ǵ�ȷ�϶����з����ȷ�ϣ������ǳٵ���ȷ�ϣ���ʲôҲ���������������ȷ��
                            }
                            if (counter >= message.length()) {
                                // �������
                                isOver = true;
                                break;
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

                        }
                    }

                }

            }
            System.out.println("ȷ�Ͻ��������ͳɹ���");
        }
    };

    public GoBackNSender() {
    }

    // ����һ��GoBackNSender
    public GoBackNSender(String IP, int receiverPort) throws SocketException {
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

    public void sendMessage(String message) {
        this.message = message;
        // ���������߳�
        this.sendThread.start();
    }

    public Thread getSendThread() {
        return sendThread;
    }

    public Thread getReceiveThread() {
        return receiveThread;
    }

    public static void main(String[] args) throws SocketException {
        // ����һ��GoBackSender����
        GoBackNSender sender = new GoBackNSender("172.16.32.88", 8086);
        // ��ʼ����ն˷��ͷ���
        sender.sendMessage("hello wrold!");
        // �ڷ��ͷ����ͬʱ��������һ���߳̽������Խ��ն˵�ackȷ�Ϸ���
        sender.getReceiveThread().start();
    }
}
