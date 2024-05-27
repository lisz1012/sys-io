package com.bjmashibing.system.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SocketMultiplexingThreads {

    private ServerSocketChannel server = null;
    // 多个Selector相当于Kafka的多个Partition
    // 每个Selector可以自己处理读到的结果，亦可以再甩出一个线程池，如果有CPU密集的计算，还可以把读到的结果
    // 放到一个队列，然后其他Service来消费进行计算。这样当前的Service就可以专注于IO，而下游的消费者专注于
    // 计算，两个Service不但业务解耦、异步，还可以配置不同性能偏重的机器分别优化硬件性能（这也是微服务的优点）
    private  Selector selector1 = null;
    private  Selector selector2 = null;
    private  Selector selector3 = null;
    int port = 9090;

    public void initServer() {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            selector1 = Selector.open();
            selector2 = Selector.open();
            selector3 = Selector.open();
            server.register(selector1, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SocketMultiplexingThreads service = new SocketMultiplexingThreads();
        service.initServer();
        NioThread T1 = new NioThread(service.selector1 ,2);
        NioThread T2 = new NioThread(service.selector2);
        NioThread T3 = new NioThread(service.selector3);

        T1.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        T2.start();
        T3.start();

        System.out.println("服务器启动了。。。。。");

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

/*
Boss和worker同属NioThread类型，但是boss中维护了两条队列，给workers分配任务
 */
class NioThread extends Thread {
    Selector selector = null;
    static int selectors = 0;

    int id = 0;

    volatile static BlockingQueue<SocketChannel>[] queue;

    static AtomicInteger idx = new AtomicInteger();

    NioThread(Selector sel, int n) {
        this.selector = sel;
        this.selectors = n;

        queue = new LinkedBlockingQueue[selectors];
        for (int i = 0; i < n; i++) {
            queue[i] = new LinkedBlockingQueue<>();
        }
        System.out.println("Boss 启动");
    }

    NioThread(Selector sel){
        this.selector = sel;
        id = idx.getAndIncrement() % selectors  ;
        System.out.println("worker: " + id + " 启动");
    }

    @Override
    public void run() {
        try {
            while (true) {
                while (selector.select(10) > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectedKeys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.isAcceptable()) { // 只有 selector1 注册了 Accept 事件, 其他的 selector 都只走下面的分支
                            acceptHandler(key);
                        } else if (key.isReadable()) {
                            readHandler(key);
                        }
                    }
                }
                if( ! queue[id].isEmpty()) {
                    ByteBuffer buffer = ByteBuffer.allocate(8192);
                    SocketChannel client = queue[id].take();
                    client.register(selector, SelectionKey.OP_READ, buffer);
                    System.out.println("-------------------------------------------");
                    System.out.println("新客户端：" + client.socket().getPort()+"分配到："+ (id));
                    System.out.println("-------------------------------------------");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void acceptHandler(SelectionKey key) {
        try {
            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
            SocketChannel client = ssc.accept();
            client.configureBlocking(false);
            int num = idx.getAndIncrement() % selectors;

            queue[num].add(client);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readHandler(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.clear();
        int read = 0;
        try {
            while (true) {
                read = client.read(buffer);
                if (read > 0) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        client.write(buffer);
                    }
                    buffer.clear();
                } else if (read == 0) {
                    break;
                } else {
                    client.close();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}



