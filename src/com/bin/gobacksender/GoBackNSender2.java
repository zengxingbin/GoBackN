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
                
             // 发送断开连接请求分组
                goBackNSender.sendData(GoBackNPackage.DISCONNECTION_TYPE, (byte) goBackNSender.getNextSeqNum(), (byte) '$');
                System.out.println("发送结束！");
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
                // 发送完毕
                goBackNSender.setOver(true);
                break;
            }
        }
    }
    
}
public class GoBackNSender2 {
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
    // 统计已确认分组的个数
    private int counter = 0;
    // 分组备份，已发送等待确认
    private ConcurrentLinkedQueue<GoBackNPackage> packageBak = new ConcurrentLinkedQueue<>();
    
    public GoBackNSender2() {
        
    }

    // 构造一个GoBackNSender
    public GoBackNSender2(String IP, int receiverPort) throws SocketException {
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
    
    public void setMessage(String message) {
        this.message = message;
    }
    // 发送分组的方法
    public synchronized void sendMsg() {
        if (nextSeqNum <= message.length() - 1) {
            if (nextSeqNum >= start + sizeOfWindow) {
                try {
                    System.out.println("窗口已满！发送者等待！");
                    this.wait();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            System.out.println("Sender开始发送第" + nextSeqNum + "分组");
            sendData(GoBackNPackage.DATA_TYPE, (byte) nextSeqNum, (byte) message.charAt(nextSeqNum));
            nextSeqNum++;
            this.notify();

        }
    }

    public synchronized void reciveMsg() {
        if (packageBak.isEmpty()) {
            System.out.println("没有可确认的分组，接收者等待！");
            try {
                this.wait();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        try {
            // 待确认的分组超时，需要重传
            if (System.currentTimeMillis() > packageBak.peek().getTimeout())
                throw new SocketTimeoutException();
            byte seqNum = receiveAckPack(packageBak.peek().getTimeout() - System.currentTimeMillis());
            // ---这里可以模拟确认丢失

            // -------------
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
                        this.notify();
                    }

                }

            } else {
                // 如果不是待确认队列中分组的确认（可能是迟到的确认），什么也不做，即丢弃这个确认
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
            this.notify();//唤醒发送者进行重传
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
        // 创建一个GoBackSender对象
        GoBackNSender2 goBackNSender = new GoBackNSender2("172.16.32.88", 8086);
        // 设置发送的信息
        goBackNSender.setMessage("Hello world!");
        
        //创建线程任务
        Sender sender = new Sender(goBackNSender);
        Receiver receiver = new Receiver(goBackNSender);
        
        //创建线程对象
        Thread sendThread = new Thread(sender);
        Thread receiveThread = new Thread(receiver);
        
        //启动线程
        sendThread.start();
        receiveThread.start();
    }
}


