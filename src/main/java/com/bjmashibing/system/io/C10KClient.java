package com.bjmashibing.system.io;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class C10KClient {
	public static void main(String[] args) {
		List<SocketChannel> clients = new ArrayList<>();
		InetSocketAddress serverAddr = new InetSocketAddress("192.168.1.99", 9090);

		for (int i = 10000; i < 65000; i++) {
			try {
				SocketChannel client1 = SocketChannel.open();
				client1.bind(new InetSocketAddress("192.168.1.102", i));
				client1.connect(serverAddr);
				boolean c1 = client1.isOpen();
				clients.add(client1);

				SocketChannel client2 = SocketChannel.open();
				client2.bind(new InetSocketAddress("192.168.1.120", i));
				client2.connect(serverAddr);
				boolean c2 = client1.isOpen();
				clients.add(client2);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Clients: " + clients.size());

	}
}
