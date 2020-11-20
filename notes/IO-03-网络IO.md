# 网络IO

## BIO演示实验

### 准备
两台机器，一台作server（192.168.1.99:9090）一台作client(192.168.1.98).在server端跑 `SocketIOPropertites.java`，在client端跑
`SocketClient.java`, 这里要想不报错，要把两个代码文件中的package... 那一行删掉。同时在server端再启动一个监控程序：
`tcpdump -nn -i ens33 port 9090`

### 实操
1. 首先运行server端：`javac SocketIOPropertites.java && java SocketIOPropertites`
2. 然后启动监控程序：`tcpdump -nn -i ens33 port 9090`
3. 最后运行客户端：`javac SocketClient.java && java SocketClient`

