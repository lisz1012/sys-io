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
   这时候只要Server端跳过System.in.read()的阻塞，就可以执行accept(),然后就会分配一个文件描述符，把内核里的socket拿过来。Java的Socket
   类的对象包装了一个socket文件描述符, 文件描述符是属于某个进程的。现在在服务端运行的Java程序那里回车, 得到：
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
   再来一个问题：一个客户端的进程可不可以开启很多的线程连一台服务器？就是一个AIP的一个进程申请很多的端口号，建立很多的连接到server的同一个端口？
   这是可以的。就像一个浏览器开很多标签连Google一样。这个时候有一个维度不一样，就是客户端的端口号。所以在任意一端，至少有一个维度可以区分就行。  
   再来一个问题：客户端的同一个程序对一台服务器同一端口，已经连了65535个连接了，还能不能对着别的服务器或者端口再发起连接？还是可以的。因为
   socket是个四元组，只要有一个维度不同就可以。来回的数据包是根据四元组条目而不是端口号来找缓冲区放置的。  
   再来一个问题：把上面的问题反过来，一台服务器可不可以被多于65535个客户端来连接？可以（否则就不用学怎么处理高并发了），只要内存够就可以。
   还是因为四元组唯一就可以了。如果一个服务端进程想要访问使用这个socket四元组，操作系统会为这个socket开辟一个FD（文件描述符），如果再accept
   另外一个socket，那么操作系统还会另外再分配一个FD...。accept才会拿到FD。四元组是唯一的进程里面对不同的socket所分配的每一个文件描述符
   也是唯一的。文件描述符是java 程序使用流的时候所用到的那个代表。服务端不同进程开辟的FD互相隔离。说白了就是一个游戏：只要路标通信全部唯一正确，
   数据包就不会发乱。进程中的各个FD都是唯一的。  
   
   那么问题又来了：平常报的"端口号被占用"是什么情况？  
   如果是在服务端，当启动服务的时候会爆出这个错误，因为服务端要启动的是ServerSocket，相当于0.0.0.0:0 --> 0.0.0.0:80, 任何主机的任何端口，
   都可以连接本机的80端口，服务器要监听（Listen）在80端口上。如果两个进程都监听在80端口上，此时从一个IP:Port来一个数据包，你看看四元组还
   能不能区分开？这样就去分不出来了，所以就会报错。所以服务端启动的端口号（Listen）只有65535个。所以只要socket四元组不同，就能创建一个连接。  
   
   再说的详细一点，程序里面要持有一个文件描述符，文件描述符就是一个小索引，这个FD是抽象的，指向内核里面的socket条目，比如说192.168.1.98:123 --->
   192.168.1.99:80(本地)，这个socket条目下面还会挂一个buffer，供服务器进程和客户端进程交流数据。如果再来一个客户端：192.168.1.97:456，
   则又会在开辟一个新的buffer。开辟buffer一定是在传输控制层"三次握手"之后。缓冲区会被挂到一个socket条目:192.168.1.97:456 ---> 192.168.1.99:80.
   但是这个时候还没有accept，也就没有分配这个FD，只是连接有了。一旦accept就会从系统里面申请到一个文件描述符，就代表了这个socket。现在只是
   描述了服务端，客户端的内核里也有同样的socket、buffer和FD。
   
4. 演示Backlog
   关掉各个进程并重启他们。先重启服务端：
   ```
   javac SocketIOPropertites.java && java SocketIOPropertites
   ```
   然后在另一个标签页中查看：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...    
   tcp6       0      0 :::9090                 :::*                    LISTEN      2376/java 
   ```
   可见只有一个Java进程处于Listen状态，然后再启动一个标签页，用TCP抓包：
   ```
   [root@localhost ~]# tcpdump -nn -i ens33 port 9090
   tcpdump: verbose output suppressed, use -v or -vv for full protocol decode
   listening on ens33, link-type EN10MB (Ethernet), capture size 65535 bytes
   ```
   再启动一个客户端：
   ```
   javac SocketClient.java && java SocketClient
   ```
   tcpdump这里可以看到三次握手：
   ```
   20:20:08.107220 IP 192.168.1.98.59156 > 192.168.1.99.9090: Flags [S], seq 2781446513, win 29200, options [mss 1460,sackOK,TS val 692138 ecr 0,nop,wscale 7], length 0
   20:20:08.107255 IP 192.168.1.99.9090 > 192.168.1.98.59156: Flags [S.], seq 1687329244, ack 2781446514, win 1152, options [mss 1460,sackOK,TS val 698873 ecr 692138,nop,wscale 0], length 0
   20:20:08.107556 IP 192.168.1.98.59156 > 192.168.1.99.9090: Flags [.], ack 1, win 229, options [nop,nop,TS val 692138 ecr 698873], length 0
   ```
   查看netstat，可见还没有分配文件描述符：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...       
   tcp6       1      0 :::9090                 :::*                    LISTEN      2376/java           
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:59156      ESTABLISHED -  
   ```
   再启动一个客户端：
   ```
   javac SocketClient.java && java SocketClient
   ```
   他依然只是在内核里有，而并没有被应用程序接受并分配文件描述符：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...          
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:59156      ESTABLISHED -                   
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:59158      ESTABLISHED -
   ```
   两个客户端分别向服务器发送数据：
   ```
   [root@localhost testsocket]# javac SocketClient.java && java SocketClient
   123
   ```
   ```
   [root@localhost testsocket]# javac SocketClient.java && java SocketClient
   456
   ```
   tcpdump程序也能看见数据包：
   ```
   20:28:03.335835 IP 192.168.1.98.59156 > 192.168.1.99.9090: Flags [P.], seq 1:2, ack 1, win 229, options [nop,nop,TS val 1167369 ecr 698873], length 1
   20:28:03.335869 IP 192.168.1.99.9090 > 192.168.1.98.59156: Flags [.], ack 2, win 1151, options [nop,nop,TS val 1174102 ecr 1167369], length 0
   20:28:03.335910 IP 192.168.1.98.59156 > 192.168.1.99.9090: Flags [P.], seq 2:3, ack 1, win 229, options [nop,nop,TS val 1167370 ecr 698873], length 1
   20:28:03.336249 IP 192.168.1.98.59156 > 192.168.1.99.9090: Flags [P.], seq 3:4, ack 1, win 229, options [nop,nop,TS val 1167370 ecr 1174102], length 1
   20:28:03.346524 IP 192.168.1.98.59156 > 192.168.1.99.9090: Flags [P.], seq 3:4, ack 1, win 229, options [nop,nop,TS val 1167380 ecr 1174102], length 1
   20:28:03.346568 IP 192.168.1.99.9090 > 192.168.1.98.59156: Flags [.], ack 4, win 1149, options [nop,nop,TS val 1174112 ecr 1167370,nop,nop,sack 1 {3:4}], length 0
   20:28:08.017759 IP 192.168.1.98.59158 > 192.168.1.99.9090: Flags [P.], seq 1:2, ack 1, win 229, options [nop,nop,TS val 1172051 ecr 1094358], length 1
   20:28:08.017870 IP 192.168.1.99.9090 > 192.168.1.98.59158: Flags [.], ack 2, win 1151, options [nop,nop,TS val 1178784 ecr 1172051], length 0
   20:28:08.018039 IP 192.168.1.98.59158 > 192.168.1.99.9090: Flags [P.], seq 2:3, ack 1, win 229, options [nop,nop,TS val 1172051 ecr 1094358], length 1
   20:28:08.019269 IP 192.168.1.98.59158 > 192.168.1.99.9090: Flags [P.], seq 3:4, ack 1, win 229, options [nop,nop,TS val 1172052 ecr 1178784], length 1
   20:28:08.029174 IP 192.168.1.98.59158 > 192.168.1.99.9090: Flags [P.], seq 3:4, ack 1, win 229, options [nop,nop,TS val 1172063 ecr 1178784], length 1
   20:28:08.029272 IP 192.168.1.99.9090 > 192.168.1.98.59158: Flags [.], ack 4, win 1149, options [nop,nop,TS val 1178795 ecr 1172051,nop,nop,sack 1 {3:4}], length 0
   ```
   虽然此时没有进程去收养它们，但是缓冲区的数据已经收到了：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...        
   tcp6       2      0 :::9090                 :::*                    LISTEN      2376/java           
   tcp6       3      0 192.168.1.99:9090       192.168.1.98:59156      ESTABLISHED -                   
   tcp6       3      0 192.168.1.99:9090       192.168.1.98:59158      ESTABLISHED - 
   ```
   现在这两个都可以，如果再开第三、四个客户端呢？第三个还是那样，没有变化，但是第四个开启之后，再查看netstat：
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...        
   tcp        0      0 192.168.1.99:9090       192.168.1.98:59162      SYN_RECV    -                   
   ...    
   tcp6       3      0 :::9090                 :::*                    LISTEN      2376/java           
   tcp6       3      0 192.168.1.99:9090       192.168.1.98:59156      ESTABLISHED -                   
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:59160      ESTABLISHED -                   
   tcp6       3      0 192.168.1.99:9090       192.168.1.98:59158      ESTABLISHED -
   ```
   state就不对了。卡在了SYN_RECV上，代表我是来了，但是并没有回复确认（或者发丢了）。再开客户端也是这样了，因为BACKLOG设置了，后续队列里面，
   只放两个。限流保证维服务负载均衡，达到上限之后向别的服务器漂移。BACKLOG要合理设置。

三次握手讲解：确认的ack可以每发一个包就等对方的一个确认，这样的话就没有所谓的窗口的概念，是基本协议的规定。但是这样比较费劲。我们发的都是数据包
或者报文，他有多大呢？可以通过ifconfig查看：
```
ens33: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
```
可见mtu是1500，1.5k抓包的时候有个options[], 中括号里面的mss 1460约等于数据的真实大小。也就是1500刨去20个字节的IP和20个字节的端口号，
数据大小事1460个。数据如果比较大的话会切成好多个mtu往外发，手里会攒着很多想发的东西。可以每发一个就等一个确认，或者在三次握手的时候会协商一个
window的大小。两边有窗口的概念，两边有多少个格子、可以放多少个包，根据协商的包的大小。两边的队列不一样。，通讯的时候都汇报自己这边放了几个还剩多少。
然后对方就知道我方的窗口有多大、放多少个是没问题的，然后就把合适的包的个数发出去。最终根据确认了多少个，计算出还剩多少个格子，再继续发，
减少了确认等待的时间，解决了拥塞。拥塞就是：接收端窗口被填满了，回复的ack会告诉发送端：没有余量了。对方就先阻塞自己不发送了。内核处理了几个包之后，
再补发一个包，说又有余量了，发送端再接着发。这就是所谓的"拥塞控制"，既提升了性能，还留心别发爆了，因为本质上来说，全填满了的话，继续发就要开始丢弃了。
演示：
1. 跑服务端 2. 用nc连接，server却并不接受 3. 看server的netstat：
```
[root@localhost ~]# netstat -natp
Active Internet connections (servers and established)
Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
...        
tcp6       1      0 :::9090                 :::*                    LISTEN      3108/java           
tcp6       0      0 192.168.1.99:9090       192.168.1.98:59170      ESTABLISHED - 
```
内核的连接已经有了，只不过程序还没有接受。现在在客户端发送好多数据：
```
[root@localhost testsocket]# nc 192.168.1.99 9090
frghkdjfgd
sfgsdfgsdflkgsdfgh
dsflhgdfskgh
s
dfshsdflhjsdfksdfh
sdfghsdfhsfdhfsdhs
dsfhsfdhsdfjkdkfg
dsfgsdfkjghsdfkjghfkg
kjhkjhkjsdfghewiuewhrfwer
werkfhwerfgjgj
safkhasudfajkdfhsadkfh
asdjfgadjsfgsajdf
ashfgasjfgsajdfgajsf
'jsadgfjsdfgsadhf
asdfgjsjhfgsd
sdjfsdfgsjfg
1111111111
```
这时数据最多是顶到了内核的缓冲区里了。在netstat看的时候发现，接收队列一直在增长：  
```
[root@localhost ~]# netstat -natp
Active Internet connections (servers and established)
Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
...
...   
tcp6       1      0 :::9090                 :::*                    LISTEN      3108/java           
tcp6     271      0 192.168.1.99:9090       192.168.1.98:59170      ESTABLISHED -  
```
复制客户端数据，多发送几次，发现服务端队列涨到1152就不往上涨了：
```
[root@localhost ~]# netstat -natp
Active Internet connections (servers and established)
Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
...       
tcp6       1      0 :::9090                 :::*                    LISTEN      3108/java           
tcp6    1152      0 192.168.1.99:9090       192.168.1.98:59170      ESTABLISHED -
```
现在server接收一下：
```
...
safkhasudfajkdfhsadkfh
asdjfgadjsfgsajdf
ashfgasjfgsajdfgajsf
'jsadgfjsdfgsadhf
as
```
并没有最后的11111111，可见接受的是内核攒的头部的数据，剩下的就被丢弃了。所以如果控制不好一些参数会丢数据。
再次跑起来server然后抓包，改客户端的代码:
```
client.setSendBufferSize(20);
client.setTcpNoDelay(true);
```
改为：
```
client.setSendBufferSize(20);
client.setTcpNoDelay(false);
client.setOOBInline(false);
```
设置了发送的缓冲区大小是20。第二个配置是如果发的东西很小，宁可攒一攒在发，看看是不是严格按照20的大小发，还是能突破20这个限制，本地攒不攒那么多东西。
为什么？因为发送的时候是一个字节一个字节发送的，命令行无论敲了多少个字节，在IO使用层面，它是一个字节一个字节的调内核，而且不调用flush()。关键
是怎么用这两个参数控制传输频率的。可以一个包发100个字节，也可以按20分成5个包。客户端从短到长发送测试字符串
```
[root@localhost testsocket]# javac SocketClient.java && java SocketClient
1
123
12345678901234567890123
edfuewfgisadufhksdahfskahfsakjdfhksjafhkjsdafldksakghadfklsghdflsklfkjlsdkj
```
服务端接收到的情况：
```
[root@localhost testsocket]javac SocketIOPropertites.java && java SocketIOPropertites
server up use 9090!

client port: 59172
client read some data is :1 val :1
client read some data is :1 val :1
client read some data is :2 val :23
client read some data is :1 val :1
client read some data is :22 val :2345678901234567890123
client read some data is :1 val :e
client read some data is :74 val :dfuewfgisadufhksdahfskahfsakjdfhksjafhkjsdafldksakghadfklsghdflsklfkjlsdkj
```
以上是优化的情况，现在不优化了,缓冲区满了就触发，或者能发就赶紧发：
```
client.setTcpNoDelay(true);
```
然后重启server和client，server并且接受。客户端发送的数据少的时候看不出来，发得多的时候并不攒成一个包一口气发过来，而是根据内核调度，每次
该发的就赶紧发出去了。不走优化的场景就是平时传的东西不是很大、复用同一个连接、传很多互相独立的东西，就不开启优化了，把第二个属性射程true。
比如：使用ssh执行ls，这个传输数据就很小。别小看这个等数据的延时，这会很影响吞吐量。  
现在把第三个参数(不重要)设置为true：
```
[root@localhost testsocket]# javac SocketIOPropertites.java && java SocketIOPropertites
server up use 9090!

client port: 59176
client read some data is :2 val :12
client read some data is :1 val :3
client read some data is :2 val :12
client read some data is :2 val :34
client read some data is :2 val :56
client read some data is :2 val :sh
client read some data is :2 val :df
client read some data is :2 val :sd
client read some data is :1 val :f
client read some data is :2 val :sd
client read some data is :1 val :f
client read some data is :2 val :sd
client read some data is :2 val :fs
client read some data is :1 val :g
client read some data is :2 val :dg
client read some data is :2 val :dj
client read some data is :2 val :fk
client read some data is :1 val :s
```
再把后两个选项设置回false看看，
```
client.setTcpNoDelay(false);
client.setOOBInline(false);
```
再次启动两端并且接受、发送数据，可以看到服务端接收到的数据：
```
[root@localhost testsocket]# javac SocketClient.java && java SocketClient
fdshgjgjgdg
fdgfkdj
dfjgdfj
dfjf
```
服务端
```
[root@localhost testsocket]# javac SocketIOPropertites.java && java SocketIOPropertites
server up use 9090!

client port: 59178
client read some data is :1 val :f
client read some data is :10 val :dshgjgjgdg
client read some data is :1 val :f
client read some data is :6 val :dgfkdj
client read some data is :1 val :d
client read some data is :6 val :fjgdfj
client read some data is :1 val :d
client read some data is :3 val :fjf
```
第一个包先急着发出去。  

演示keepalive：服务端的代码改一下：
```
private static final boolean CLI_KEEPALIVE = true;
```
然后重启server和客户端，连接上，然后看到三次握手：
```
[root@localhost ~]# tcpdump -nn -i ens33 port 9090
tcpdump: verbose output suppressed, use -v or -vv for full protocol decode
listening on ens33, link-type EN10MB (Ethernet), capture size 65535 bytes
23:18:12.069305 IP 192.168.1.98.59180 > 192.168.1.99.9090: Flags [S], seq 3538636781, win 29200, options [mss 1460,sackOK,TS val 11376102 ecr 0,nop,wscale 7], length 0
23:18:12.069453 IP 192.168.1.99.9090 > 192.168.1.98.59180: Flags [S.], seq 987354626, ack 3538636782, win 1152, options [mss 1460,sackOK,TS val 11382835 ecr 11376102,nop,wscale 0], length 0
23:18:12.070105 IP 192.168.1.98.59180 > 192.168.1.99.9090: Flags [.], ack 1, win 229, options [nop,nop,TS val 11376104 ecr 11382835], length 0
```
然后就等在这里。keepalive是TCP协议里规定的：双方如果建立了连接，但是很久都不说话，能确定对方还活着吗？是不能的。在http里面也有一个keepalive，
在负载均衡里面还有一个高可用进程叫keepalived，这三个东西不能弄混了，不同层级的。keepalive的时候在传输控制层要互相发一些"心跳"，来确认对方还活着。
周期性做这件事，会控制资源和效率，连接挂掉的时候还保持连接会消耗资源。

## 网络IO变化 模型

### BIO
什么是同步异步、阻塞非阻塞？没有异步阻塞模型。异步在Linux中没有实现。接下来用到的指令是 `strace -ff -o out cmd` Windows中不是程序自己读的，
Linux中只是知道能读了，但还需要程序自己调用读，程序还得守在那里，同步的。只不过Linux中可以是阻塞的或者非阻塞的，可以一直等在那里直到来数据，
也可以读不到的时候先继续忙别的，一会儿再来读一下试试。  

ServerSocket.accept()的时候，java是怎么阻塞的？java只是一堆字节码，泡在Linux的JVM进程中，JVM用到了内核中系统调用的accpt，靠着内核的
accept阻塞，而java代码也被阻塞了。accept语意就是接收，接收的是文件描述符3，监听的是8090端口。这个3是通过早先调用socket系统调用返回来的。
accept换成内核的流程就是：1.调用socket返回文件描述符3 2.绑定（bind系统调用）3这个文件描述符到8090端口 3.listen（系统调用）。任何程序，
如果作服务端，都必然要调用这三个。当有客户端链接过来的时候，在accept的括号里面会有一个客户端的随机端口号，client.getPort();就会返回这个。
而且还有对方的IP地址。并且还有一个文件描述符5，他就是一个新的连接了，是一个四元组，是联通的状态。新的线程在底层是怎么来的？通过clone系统调用，
然后会返回一个数字，这就是新的子进程（线程）的进程ID。子进程会共享父进程打开的资源和文件描述符。clone和fork有什么区别？fork是本质，其他的
是基于不同参数的包装。主线程只是疯狂的等待客户端链接，，来了客户端就抛出线程，线程创建的时候有很多共享的flag，所以子线程能访问共享的东西。查看
子线程的out文件，可以看到recv(5, 读取5这个文件描述符，因为它已经被共享了。应用程序通过网络IO和外界通信,都要经历一下这几个系统调用：
1. socket得到文件描述符fd3 2. bind(fd3, 8090) 3. listen(fd3) 到此为止，netstat -natp 可以看见: `0.0.0.0:8090  0.0.0.0:* LISTEN`
LISTEN的时候会连进一个客户端，这个时候LISTEN会变成ESTABLISHED，并且可以接受client来的消息，在内核里。
4. accept(fd3, 阻塞,客户端连过来之后得到 ---> fd5、6... 接下来clone子线程，它想要读取fd5：recv(fd5, 又进入阻塞状态。这就有两个阻塞了，
用多线程的方式拆开这两类阻塞。


```

```