package com.bin.gobackreceiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

import com.bin.util.GoBackNPackage;

public class GoBackReceiver {
    // 接收端socket
    private DatagramSocket receiverSocket;
    // 发送端端口和地址
    private InetAddress senderAddress;
    private int senderPort;
    //期待收到的下一个分组的编号
    private byte expectedSeqNum;
    StringBuffer message = new StringBuffer();
    // 构造一个GoBackReceiver
    public GoBackReceiver(int port) throws SocketException {
        this.receiverSocket = new DatagramSocket(port);// 绑定指定的端口
        // 等待发送端连接
        waitForConnection();
    }

    private void waitForConnection() {
        System.out.println("接收端启动，等待连接。。。");
        byte[] buf = null;
        DatagramPacket datagramPacket = null;
        while (true) {
            // 创建数据缓冲数组
            buf = new byte[3];
            // 创建udp报文
            datagramPacket = new DatagramPacket(buf, buf.length);
            try {
                // 接受从发送端发来的数据报
                receiverSocket.receive(datagramPacket);
                // 解析报文
                GoBackNPackage pack = new GoBackNPackage(datagramPacket);
                System.out.println(pack);
                if(!pack.isConnecPack()) {//如果不是连接请求报文，丢弃这个报文，什么也不做,继续等待连接
                    System.out.println("非连接请求");
                    continue;
                }
                else 
                    break;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            
        }
        //从连接请求报文中获取发送端地址和端口，一遍向发送端发送ack确认包
        this.senderAddress = datagramPacket.getAddress();
        this.senderPort = datagramPacket.getPort();
        System.out.println(this.senderAddress + ":" + this.senderPort + "已连接！");
        this.expectedSeqNum = 0;
        //发送确认包
        sendAckPack(this.expectedSeqNum);
    }

    private void sendAckPack(byte expectedSeqNum) {
        GoBackNPackage ackPack = new GoBackNPackage(GoBackNPackage.ACK_TYPE, expectedSeqNum, (byte)'o');
        //创建udp数据包
        DatagramPacket dataPack = ackPack.getDatagramPacket();
        dataPack.setAddress(this.senderAddress);
        dataPack.setPort(this.senderPort);
        //发送确认包
        try {
            this.receiverSocket.send(dataPack);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public void receiveMessage() {
        System.out.println("开始接收发送端分组。。。");
        //定义丢包率,坏包率，失序率,在接收端来模拟包的丢失，损坏，失序
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
                    //传输完毕，打印接收到的信息
                    System.out.println(message.toString());
                    return;
                }
                //模拟包的失序。。。，使用多线程，包收到的包缓存起来，待会再确认
                
                
                //模拟包的丢失：有30%的可能包会在传输过程中丢失，此时接收端自然什么也不知道，发送端发送的分组就会得不到确认而超时重传
                //产生一个随机数
                percentage = rand.nextInt(11) / 10.0;
                if(percentage <= packetLoss) {
                    //把这个包丢弃，什么也不做
                    System.out.println("分组" + goNPackage.getSequenceNum() + "在传输过程中丢失");
                    continue;
                }
                    
                
                if(goNPackage.getSequenceNum() == expectedSeqNum) {
                    /**
                     * 收到了期待的分组，但该分组在传输过程中被损坏了，所以要重传改分组后面的分组
                     * 丢弃这个坏包，其他什么也不做
                     * 
                     */
                    percentage = rand.nextInt(11) / 10.0;
                    if(percentage <= badPacketRate) {
                        
                        System.out.println("收到第" + goNPackage.getSequenceNum() + "个分组,但此分组已损坏！");
                        continue;
                        
                    }
                    System.out.println("正确收到分组" + goNPackage);
                    //收到的分组数据一个个拼接起来，形成完整的数据
                    message.append((char)goNPackage.getData());
                    //发送确认分组
                    sendAckPack(expectedSeqNum);
                    //期待收到的下一个分组
                    expectedSeqNum++;
                }else {
                    /**
                     * 1.收到的可能是重复的分组（因为这些分组已经被接收端正确接收且已经发送确认，但确认丢失了，导致发送端重新传送的分组），此时分组
                     * 的序号一定是小于expectedSeqNum的
                     * 2.也有可能是发送端发送的某一个分组在传输过程中丢失了，收到的是expectedSeqNum之后的分组
                     * 这时发送过来的分组的序号一定大于expectedSeqNum的，
                     * 故分两种情况
                     */
                    if(goNPackage.getSequenceNum() < expectedSeqNum) {
                        //收到的是重复的分组
                        System.out.println("收到重复分组:" + goNPackage);
                    }else if(expectedSeqNum < goNPackage.getSequenceNum()) {
                        System.out.println("期待收到第" + expectedSeqNum + "个分组，但收到第" + goNPackage.getSequenceNum()
                        + "个分组:" + goNPackage + "丢弃此分组,第" + expectedSeqNum + "~" + (goNPackage.getSequenceNum()-1) + 
                        "个分组丢失或者未按序到达或则已损坏");
                        
                    }
                    //发送对上一个分组的确认
                    sendAckPack(--expectedSeqNum);
                    //期待收到下一个分组
                    expectedSeqNum++;
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    public static void main(String[] args) throws SocketException {
        //创建一个GoBackReceiver
        GoBackReceiver receiver = new GoBackReceiver(8086);
        receiver.receiveMessage();
    }
}
