# 网络IO

## BIO演示实验

### 准备
两台机器，一台作server（192.168.1.99:9090）一台作client(192.168.1.98).在server端跑 `SocketIOPropertites.java`，在client端跑
`SocketClient.java`, 这里要想不报错，要把两个代码文件中的package... 那一行删掉。同时在server端再启动一个监控程序：
`tcpdump -nn -i ens33 port 9090`

### 实操
1. 然后在服务端启动监控程序：`tcpdump -nn -i ens33 port 9090` 监控抓包。
2. 首先运行server端：`javac SocketIOPropertites.java && java SocketIOPropertites`
   这时候，会在服务端显示状态：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...     
   tcp6       0      0 :::9090                 :::*                    LISTEN      2890/java
   ```
   这个时候，查看java进程的文件描述符：
   ```
   [root@localhost ~]# lsof -op 2890
   COMMAND  PID USER   FD   TYPE             DEVICE     OFFSET     NODE NAME
   java    2890 root  cwd    DIR                8,3            51320822 /root/testsocket
   java    2890 root  rtd    DIR                8,3                  64 /
   java    2890 root  txt    REG                8,3            50673356 /usr/java/jdk1.8.0_181-amd64/jre/bin/java
   ...
   ...
   java    2890 root    0u   CHR              136,1        0t0        4 /dev/pts/1
   java    2890 root    1u   CHR              136,1        0t0        4 /dev/pts/1
   java    2890 root    2u   CHR              136,1        0t0        4 /dev/pts/1
   java    2890 root    3r   REG                8,3 0t62216268   210251 /usr/java/jdk1.8.0_181-amd64/jre/lib/rt.jar
   java    2890 root    4u  unix 0xffff880036539c00        0t0    28347 socket
   java    2890 root    5u  IPv6              28349        0t0      TCP *:websm (LISTEN)
   ```
   多了个文件描述符5，是Listen状态，代表有一个进程已经在9090端口监听上了。客户端跟服务器通信的时候分为两个阶段：
   三次握手建立连接的时候找的是Listen的进程；但是这之后分配文件描述符、发送数据的时候走另外的一个环节。这时候步骤1启动的抓包进程还没有任何输出。
   这时候，服务端的代码卡在了accept之前，还没有执行accept。下面执行步骤3，看看启动客户端之后，能不能建立上连接。
3. 最后运行客户端：`javac SocketClient.java && java SocketClient` 在步骤1抓包这里，我们看到了"三次握手：
   ```
   00:28:12.740731 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [S], seq 2847314633, win 29200, options [mss 1460,sackOK,TS val 4270198 ecr 0,nop,wscale 7], length 0
   00:28:12.740770 IP 192.168.1.99.9090 > 192.168.1.98.57356: Flags [S.], seq 1691864370, ack 2847314634, win 1152, options [mss 1460,sackOK,TS val 4208992 ecr 4270198,nop,wscale 0], length 0
   00:28:12.741013 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [.], ack 1, win 229, options [nop,nop,TS val 4270202 ecr 4208992], length 0
   ```
   然后在服务端查看连接情况可得：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...       
   tcp6       1      0 :::9090                 :::*                    LISTEN      2890/java           
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:57356      ESTABLISHED - 
   ```
   曾经只有一个服务端对于9090端口的监听进程，服务端现在仍然阻塞，也没有执行accept。现在客户端连过来了，之后多了一个socket：192.168.1.99:9090
   到 192.168.1.98:57356 建立起来了连接，但是还没有把这个socket分配给任何进程去使用，还没有被谁接收，但是内核里面已经有他了。内核态里面有了，
   但是程序（应用层）还没有接受他，前面还有Recv-Q，先是有没有发来或者发出去的数据包的堆积，现在还没有。  
   
   现在从客户端到服务端发一点数据：
   ```
   [root@localhost testsocket]# javac SocketClient.java && java SocketClient
   1111
   ```
   这时候在抓包的那里还能看见，来回收发数据的数据包并确认：
   ```
   00:53:55.378396 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [P.], seq 1:2, ack 1, win 229, options [nop,nop,TS val 5812840 ecr 4208992], length 1
   00:53:55.378424 IP 192.168.1.99.9090 > 192.168.1.98.57356: Flags [.], ack 2, win 1151, options [nop,nop,TS val 5751630 ecr 5812840], length 0
   00:53:55.378449 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [P.], seq 2:3, ack 1, win 229, options [nop,nop,TS val 5812840 ecr 4208992], length 1
   00:53:55.378579 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [P.], seq 3:4, ack 1, win 229, options [nop,nop,TS val 5812840 ecr 5751630], length 1
   00:53:55.388319 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [P.], seq 3:4, ack 1, win 229, options [nop,nop,TS val 5812850 ecr 5751630], length 1
   00:53:55.388360 IP 192.168.1.99.9090 > 192.168.1.98.57356: Flags [.], ack 4, win 1149, options [nop,nop,TS val 5751640 ecr 5812840,nop,nop,sack 1 {3:4}], length 0
   00:53:55.388580 IP 192.168.1.98.57356 > 192.168.1.99.9090: Flags [P.], seq 4:5, ack 1, win 229, options [nop,nop,TS val 5812850 ecr 5751640], length 1
   00:53:55.431175 IP 192.168.1.99.9090 > 192.168.1.98.57356: Flags [.], ack 5, win 1148, options [nop,nop,TS val 5751682 ecr 5812850], length 0 
   ```
   再看网络状态：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...        
   tcp6       1      0 :::9090                 :::*                    LISTEN      2890/java           
   tcp6       4      0 192.168.1.99:9090       192.168.1.98:57356      ESTABLISHED -
   ```
   即便还没有分配个谁去使用，但是前面的Recv-Q会出现4个字节已经被内核收到了。进程虽然没有接受，但是内核已经完成了初步的使用，这个就是我们常说的：
   TCP协议是面向连接的走完三次握手，双方要开辟资源缓冲区，即便应用程序不要，内核里也有资源去接受或者等待。连接不是物理的，双方通过三次握手
   开辟了资源，可以为对方提供服务了，这个连接就已经有了。他是看不见摸不着的，但是是靠资源来代表的。再看进程的文件描述符：
   ```
   [root@localhost ~]# lsof -op 2890
   COMMAND  PID USER   FD   TYPE             DEVICE     OFFSET     NODE NAME
   ...
   java    2890 root    4u  unix 0xffff880036539c00        0t0    28347 socket
   java    2890 root    5u  IPv6              28349        0t0      TCP *:websm (LISTEN)
   ```
   状态还是LISTEN，也就是说程序不是通过Socket来使用socket，程序其实是在等一个文件描述符，但是这个accept还没被调用，所以文件描述符还没有。
   这时候只要Server端跳过System.in.read()的阻塞，就可以执行accept(),然后就会分配一个文件描述符。现在在服务端运行的Java程序那里回车, 得到：
   ```
   [root@localhost testsocket]# javac SocketIOPropertites.java && java SocketIOPropertites
   server up use 9090!
   
   client port: 57356
   client read some data is :4 val :1111
   ```
   代表程序收到连接和数据1111了，再次查看网络连接状态：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...       
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:57356      ESTABLISHED 2890/java
   ```
   可以看到，刚才没有被分配的那个ESTABLISHED socket连接已经被分配给了2890这个java进程。再次查看进程的文件描述符：
   ```
   [root@localhost ~]# lsof -op 2890
   COMMAND  PID USER   FD   TYPE             DEVICE     OFFSET     NODE NAME
   ...
   java    2890 root    4u  unix 0xffff880036539c00        0t0    28347 socket
   java    2890 root    6u  IPv6              36938        0t0      TCP localhost.localdomain:websm->192.168.1.98:57356 (ESTABLISHED)
   ```
   文件描述符6已经出现了，状态由LISTEN切换到了ESTABLISHED。  
   
   小结：TCP是面向连接的、可靠的传输协议。连接受限不是物理的，首先通过三次握手，关键是握手之后双方在内核级开辟资源，双方的这个资源，
   代表了所谓的"连接"。可靠性是靠ack确认机制来保证的。  
   
   Socket是什么？  
   它是一个四元组：客户端IP+客户端端口+服务端IP+服务端端口，任何一个维度不同，都是一个不同的socket。socket也是内核级的，即便不调用accpt，
   内核中也会建立连接和数据的接收，如何理解四元组？常见面试题：在网路终有一个客户端和一个服务端，客户端的IP是AIP，服务端的IP是XIP。服务端
   AIP可以起两个服务XPORT和YPORT，只不过端口号不能冲突。在客户端，启动了一个程序，会随机生成一个端口号CPORT：123，想跟服务端的80端口连接，
   这样就有一个四元组：AIP CPORT + XIP XPORT。三次握手之后，服务端需不需要再随机分配一个端口（80之外的）处理这个client连接？不需要。
   客户端链接进来之后，双方会开辟资源，这个四元组条目在客户端和服务端就都有了，而且基于这个条目两边还会开辟buffer。站在任意一方，只要这个纪录
   是个唯一标识，就没有必要再给她拼上一个端口号了。
   







```

```