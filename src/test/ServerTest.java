import com.my.netty.server.DefaultServerBusinessService;
import com.my.netty.server.TcpServer;

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
