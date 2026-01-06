# easy-tcp

`easy-tcp` 是一个基于 Netty 的轻量级 TCP 通信框架，支持快速接入、自动重连、粘包拆包处理，适用于客户端 / 服务端 TCP 场景。

---

## 快速接入使用

### Maven 引入（JitPack）

#### Repository
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

#### Dependency
```xml
<dependency>
  <groupId>com.github.lucasbmh8-glitch</groupId>
  <artifactId>easy-tcp</artifactId>
  <version>v0.1.0</version>
</dependency>
```

---

## 客户端示例

```java
public class ClientTest {

    public static void main(String[] args) {

        // 业务处理类，主要需要实现的代码
        DefaultClientBusinessService bizService = new DefaultClientBusinessService();

        // 业务用户唯一标识
        // 一个 clientId 可以独占一个 channel，也可以多个 clientId 共用一个 channel
        String clientId = "Test001";

        TcpClient client = new TcpClient.Builder()
                .withoutProxy()                 // 不使用代理
                // .enableProxy(String proxyHost, int proxyPort) // 使用代理
                // .proxyType(TcpProxyEnum)       // 代理类型
                .delimiter("@")                 // 粘包 / 拆包分隔符
                .maxFrameLength(500_000)        // 最大数据长度（字节）
                .connectTimeoutMillis(3000)     // 连接超时（毫秒）
                .writeTimeoutSeconds(1)         // 写超时（秒）
                .workerThreads(6)               // Worker 线程数
                .maxRetry(8)                    // 重连次数（失败后指数退避）
                .businessService(bizService)
                .md5Key("abc")
                .clientId(clientId)
                .build();

        client.connect("127.0.0.1", 7334);
    }
}
```

---

## 服务端示例

```java
public class ServerTest {

    // 若由 Spring 管理，可使用 @Autowired 注入
    /*
    @Autowired
    private DefaultServerBusinessServiceImpl defaultServerBusinessServiceImpl;
    */

    public static void main(String[] args) {

        DefaultServerBusinessService service = new DefaultServerBusinessService();

        // 使用默认配置启动 TCP 服务
        TcpServer server = new TcpServer.Builder()
                .port(7334)
                .delimiter("@")
                .md5Key("abc")
                .serviceBusiness(service)
                .build();

        // 示例：服务端主动向客户端发送消息
        /*
        Channel channel = service.getChannel();
        if (Objects.nonNull(channel)) {
            String msgId = UUID.randomUUID().toString();
            ActivityMsgVo vo = new ActivityMsgVo();
            vo.setData("你好啊，我是服务端给你发送的消息");
            vo.setActionType("BUSINESS");
            vo.setMsgId(msgId);

            SendMessageProxy.sendBusinessMessage(vo, channel);
        }
        */
    }
}
```

---

## 参数说明（Builder 常用配置）

| 参数 | 说明 |
|----|----|
| port | 服务端监听端口 |
| delimiter | TCP 粘包/拆包分隔符 |
| maxFrameLength | 最大单帧数据长度 |
| connectTimeoutMillis | 客户端连接超时时间 |
| writeTimeoutSeconds | 写数据超时时间 |
| workerThreads | Netty Worker 线程数 |
| maxRetry | 断线重连最大次数 |
| md5Key | 通信签名密钥 |
| clientId | 客户端唯一标识 |

---

## 版本说明

- `v0.1.0`：基础功能版本，适合内部或项目级使用
