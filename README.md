# easy-tcp
快速接入使用，客户端：
public class ClientTest {

    public static void main(String args[]){
        //业务处理类，主要需要实现的代码
        DefaultClientBusinessService bizService = new DefaultClientBusinessService();
        String clientId ="Test001";//业务用户唯一表示ID，一个clientId可以一个单独的channel通道，也可以共用一个channel通道
        TcpClient client = new TcpClient.Builder()
                .withoutProxy()//不使用代理方式，
                //.enableProxy(String proxyHost, int proxyPort) 使用代理方式
                //.proxyType(TcpProxyEnum) //代理类型
                .delimiter("@")//处理粘包和拆包的关键字
                .maxFrameLength(500_000)//最大数据长度，字节
                .connectTimeoutMillis(3000)//连接超时(秒)
                .writeTimeoutSeconds(1)//发送消息写入超时(秒)
                .workerThreads(6)//WorkGroup工作线程数
                .maxRetry(8)//TCP端口重连次数，每隔1秒钟重连一次，达到此配置的8次依然无法重连成功则衰减到10秒后再重试，直到连接成功为止
                .businessService(bizService).md5Key("abc").clientId(clientId)
                .build();

        client.connect("127.0.0.1", 7334);
    }


}


快速接入使用，服务端：

public class ServerTest {
    //若为spring管理bean，则可以使用spring 方式获取业务bean

   /* @Autowired
    private DefaultServerBusinessServiceImpl defaultServerBusinessServiceImpl;*/

    public static void main(String args[]){

        DefaultServerBusinessService service = new DefaultServerBusinessService();

       // 使用默认值
       TcpServer server = new TcpServer.Builder()
                .port(7334).delimiter("@").md5Key("abc")
                .serviceBusiness(service)
                .build();
        try{
            server.run();
            Thread.sleep(4000);
        }catch (Exception ex){
            ex.printStackTrace();
        }

        //模拟服务发送消息给一个 客户端

      /*  Channel channel = service.getChannel();
        if(Objects.nonNull(channel)){
            //ActivityMsgVo vo, Channel channel
            String msgId= UUID.randomUUID().toString();
            ActivityMsgVo vo = new ActivityMsgVo();
            vo.setData("你好啊，我是服务端给你发送的消息");
            vo.setActionType("BUSINESS");
            vo.setMsgId(msgId);
            SendMessageProxy.sendBusinessMessage(vo,channel);
        }*/
    }


}
