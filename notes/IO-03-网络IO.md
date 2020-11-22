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
   tcp6       4      0 192.168.1.99:9090       192.168.1.98:57356      ESTABLISHED -
   ```
   曾经只有一个服务端对于9090端口的监听进程，现在客户端连过来了，执行accept之后多了一个socket：192.168.1.99:9090 和 192.168.1.98:57356
   建立起来了连接，但是还没有把这个socket分配给任何进程去使用。
   
   ```
   [root@localhost ~]# netstat -natp
   Active Internet connections (servers and established)
   Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
   ...
   ...       
   tcp6       0      0 192.168.1.99:9090       192.168.1.98:57356      ESTABLISHED 2890/java
   ```

