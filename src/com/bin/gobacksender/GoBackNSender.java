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
    // 发送端socket。使用udp进行传输
    private DatagramSocket senderSocket;
    // 接收端地址和端口
    private InetAddress receiverAddress;
    private int receiverPort;
    // 发送的信息
    private String message;
    // 滑动窗口大小
    private static int sizeOfWindow = 5;
    // 当前窗口的起始位置
    private int start;
    // 下一个要发送的分组的编号
    private int nextSeqNum;
    // 判断传输是否结束，用来发送线程以及接收线程之间的交流
    private boolean isOver = false;
    // 分组备份，已发送等待确认
    private ConcurrentLinkedQueue<GoBackNPackage> packageBak = new ConcurrentLinkedQueue<>();
    private Thread sendThread = new Thread() {

        @Override
        public void run() {
            System.out.println("开始发送分组。。。");
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
                            System.out.println("窗口已满！");
                        }

                    }
                }

            }
            // 发送断开连接请求分组
            sendData(GoBackNPackage.DISCONNECTION_TYPE, (byte) nextSeqNum, (byte) '$');
            System.out.println("发送结束！");
        }

    };
    private Thread receiveThread = new Thread() {
        @Override
        public void run() {
            System.out.println("开始接收ack确认包。。。");
            int counter = 0;// 对确认分组进行计数，当确认分组个数等于发送的总分组数时结束确认
            double ackLoss = 0.2;// 确认丢失的可能为0.2
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
                // System.out.println("死循环");
                synchronized (GoBackNPackage.class) {

                    if (!packageBak.isEmpty()) {

                        try {
                            // 不管发来的是待确认分组队列中第几个分组的确认，总之不能第一个待确认分组不能超时，否则要进行重传第一个（包括第一个）待确认分组之后的分组(第一个都超时了，后面的肯定都超时了)
                            if (System.currentTimeMillis() > packageBak.peek().getTimeout())
                                throw new SocketTimeoutException();
                            byte seqNum = receiveAckPack(packageBak.peek().getTimeout() - System.currentTimeMillis());
                            // 模拟确认丢失的情况,确认丢失不必重传
                            /*
                             * percentage = rand.nextInt(11) / 10.0; if
                             * (percentage <= 0.2) {
                             * System.out.println(percentage + "丢失确认" + seqNum);
                             * continue; }
                             */

                            // 发来的可能是重复确认，即上一个已经确认的分组的编号，此时不管这个重复确认
                            /*
                             * *可能会发生发送端发送的确认迟到，确认丢失的情况，以及发送端发送累积确认（
                             * 即不必对每一个分组发送确认，
                             * 在收到几个分组后，对收到的最后一个分组，发送确认，表示这个分组之后的分组都已经正确收到了）
                             * 1.对于确认迟到，收到迟到的确认丢弃这个确认，其他什么也不做
                             * 2.对于确认丢失的情况，：比如前一个（或前几个）待确认分组的的确认丢失了，只要发送端在
                             * 前一个（或前几个）待确认的分组未超时的情况下，发送了下一个（或下几个）待分组的确认，
                             * 则表示前面的分组 都已经正常收到了，则对前面确认丢失的分组不必再进行重传
                             * 
                             */
                            // 判断此确认，是待确认分组队列中的哪一个分组的确认
                            Iterator<GoBackNPackage> iterator = packageBak.iterator();
                            boolean flag = false;// 发送过来的确认，是待确认分组队列中的其中一个确认
                            while (iterator.hasNext()) {
                                GoBackNPackage goBackNPackage = iterator.next();
                                if (seqNum == goBackNPackage.getSequenceNum()) {
                                    flag = true;
                                    break;
                                }
                            }
                            // System.out.println("确认分组" + start + "~" +
                            // seqNum);
                            if (flag) {
                                // 每收到一个分组的确认，窗口向前滑动一个位置
                                while (start < seqNum + 1) {
                                    start++;// 窗口移动
                                    counter++;// 确认个数加一
                                    // 从待确认的分组队列中移除已经确认的分组
                                    if (!packageBak.isEmpty()) {
                                        GoBackNPackage goBackNPackage = packageBak.remove();
                                        goBackNPackage.setType(GoBackNPackage.ACK_TYPE);
                                        System.out.println("确认分组:" + goBackNPackage);
                                    }

                                }

                            } else {
                                // 如果不是待确认队列中分组的确认（可能是迟到的确认），什么也不做，即丢弃这个确认
                            }
                            if (counter >= message.length()) {
                                // 发送完毕
                                isOver = true;
                                break;
                            }

                        } catch (SocketTimeoutException e) {
                            // 超时重传
                            // 获取待确认队列里第一个超时的分组
                            GoBackNPackage goBackNPackage = packageBak.peek();
                            System.out.println("在规定时间内未收到第 " + goBackNPackage.getSequenceNum() + "个分组的确认，将超时重传第"
                                    + goBackNPackage.getSequenceNum() + "之后的分组");
                            // 清空待确认队列
                            packageBak.clear();
                            // 从第一个超时的分组开始，重传后面所有超时的分组
                            // start = goBackNPackage.getSequenceNum();
                            nextSeqNum = start;

                        }
                    }

                }

            }
            System.out.println("确认结束，发送成功！");
        }
    };

    public GoBackNSender() {
    }

    // 构造一个GoBackNSender
    public GoBackNSender(String IP, int receiverPort) throws SocketException {
        System.out.println("发送端启动，正在连接。。。");
        try {
            this.receiverAddress = InetAddress.getByName(IP);
        } catch (UnknownHostException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            System.out.println("IP is not found");
        }
        this.receiverPort = receiverPort;

        this.senderSocket = new DatagramSocket();

        // 创建一个goback-package
        GoBackNPackage pack = new GoBackNPackage(GoBackNPackage.CONNECTION_TYPE, (byte) -1, (byte) '^');
        // 获取udp数据包
        DatagramPacket connectPack = pack.getDatagramPacket();
        // 设置接收端的地址和端口
        connectPack.setAddress(this.receiverAddress);
        connectPack.setPort(this.receiverPort);
        // 发送连接请求报文
        try {
            System.out.println("发送请求连接分组:" + pack);
            this.senderSocket.send(connectPack);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // 等待连接请求的确认包
        try {
            receiveAckPack(1000);
            System.out.println("连接成功！");
            // 收到确认包，初始化滑动窗口起始位置，以下下一个要发送的包的位置
            this.start = 0;
            this.nextSeqNum = 0;
        } catch (SocketTimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void sendData(byte type, byte seqNum, byte charData) {
        // 创建GoBackNPackage
        GoBackNPackage goNPackage = new GoBackNPackage(type, seqNum, charData);
        goNPackage.setTimeout(System.currentTimeMillis() + 250);// 设置超时时间
        System.out.println("发送分组" + goNPackage);
        // 加入已发送待确认队列
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
        // 字节缓冲数组
        byte[] buf = new byte[3];
        // 创建upd用户数据报，用于接收数据
        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
        try {
            // 设置超时时间
            this.senderSocket.setSoTimeout((int) timeout);
            this.senderSocket.receive(datagramPacket);
        } catch (IOException e) {
            if (e instanceof SocketTimeoutException)
                throw (SocketTimeoutException) e;
            else {
                System.out.println("发送时遇到了一个错误！");
                System.exit(1);
            }
        }
        // 获取goback数据包
        GoBackNPackage pack = new GoBackNPackage(datagramPacket);
        // 打印ack包
        // System.out.println("确认分组" + pack);
        if (!pack.isAckPack()) {
            // 收到的包不是ack确认包
            System.out.println("没有收到确认包");

        }
        return pack.getSequenceNum();
    }

    public void sendMessage(String message) {
        this.message = message;
        // 启动发送线程
        this.sendThread.start();
    }

    public Thread getSendThread() {
        return sendThread;
    }

    public Thread getReceiveThread() {
        return receiveThread;
    }

    public static void main(String[] args) throws SocketException {
        // 创建一个GoBackSender对象
        GoBackNSender sender = new GoBackNSender("172.16.32.88", 8086);
        // 开始向接收端发送分组
        sender.sendMessage("hello wrold!");
        // 在发送分组的同时，创建另一个线程接受来自接收端的ack确认分组
        sender.getReceiveThread().start();
    }
}
