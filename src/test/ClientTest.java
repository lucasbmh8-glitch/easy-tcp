import com.my.netty.client.DefaultClientBusinessService;
import com.my.netty.client.TcpClient;

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
