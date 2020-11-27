package com.bjmashibing.system.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class TestSocket {
	public static void main(String[] args) throws Exception {
		ServerSocket server = new ServerSocket(8090);
		System.out.println("step1: new ServerSocket(8090)");
		while (true) {
			Socket client = server.accept();
			System.out.println("step2: client\t" + client.getPort());
			new Thread(new Runnable() {
				Socket ss;
				public Runnable setSS(Socket s) {
					ss = s;
					return this;
				}
				@Override
				public void run() {
					try {
						InputStream in = ss.getInputStream();
						BufferedReader br = new BufferedReader(new InputStreamReader(in));
						while (true) {
							System.out.println(br.readLine());
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.setSS(client)).start();
		}
	}
}
