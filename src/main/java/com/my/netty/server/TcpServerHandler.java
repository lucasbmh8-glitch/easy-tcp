package com.my.netty.server;

import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import com.my.netty.content.ActionTypeEnum;
import com.my.netty.vo.ActivityMsgVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicInteger;


/**
 *  服务端异常处理handler
 */
public class TcpServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(TcpServerHandler.class);
    public static final int MAX_FAILED_HEARTBEATS = 3;

    private final DefaultServerBusinessService serverBusinessService;

    private final String delimiter;

    private final String md5Key;

    private final Gson gson = new Gson();

    private final AtomicInteger readFailedHeartbeatCount = new AtomicInteger(0);

    public final AttributeKey<String> CLIENT_ID_KEY = AttributeKey.valueOf("clientId");


    public TcpServerHandler(DefaultServerBusinessService serverBusinessService, String delimiter, String md5Key) {
        this.serverBusinessService = serverBusinessService;
        this.delimiter=delimiter;
        this.md5Key=md5Key;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        serverBusinessService.keeplivedMsgSend(ctx,delimiter);
        readFailedHeartbeatCount.set(0); // 有消息进来则重置心跳当前最大次数
        ctx.flush();
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            // 打印日志，标明客户端与服务器主动断开连接
            String clientId = getClientId(ctx);
            if (clientId != null) {
                ServerContent.CHANNEL_CONCURRENT_HASH_MAP.remove(clientId);
            }
            // 检查连接是否已经关闭，避免重复关闭
            if (ctx.channel().isOpen()) {
                log.warn("Server服务断开——客户端与服务器主动断开TCP连接, 手动关闭当前的TCP连接");
                ctx.close();  // 关闭当前连接
            }
            // 调用父类的 channelInactive 方法进行默认清理操作
            super.channelInactive(ctx);
        } catch (Exception ex) {
            log.error("Server服务断开——客户端与服务器主动断开TCP连接: {}", ex.getMessage(), ex);
        }
    }

    //客户端连接成功后，必须立马发送一条验证身份的消息
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)  {
        try{
            String message = (String) msg;
            readFailedHeartbeatCount.set(0); // 有消息进来则重置心跳当前最大次数

            ActivityMsgVo msgDto = gson.fromJson(message, ActivityMsgVo.class);

            if (ActionTypeEnum.AUTH.getCode().equals(msgDto.getActionType())) {

                String clientId =  msgDto.getClientId();

                // 旧连接顶掉（可选）
                Channel oldChannel= ServerContent.CHANNEL_CONCURRENT_HASH_MAP.get(clientId);

                if (oldChannel != null && oldChannel != ctx.channel()) {
                    oldChannel.close();
                }

                ServerContent.CHANNEL_CONCURRENT_HASH_MAP.put(clientId, ctx.channel());

                ctx.channel().attr(CLIENT_ID_KEY).set(clientId);

                return;
            }


            if(StringUtils.equals(msgDto.getActionType(), ActionTypeEnum.KEEPALIVE.getCode())){
                return;
            }
            if (StringUtils.equals(msgDto.getActionType(), ActionTypeEnum.ACK.getCode())) {
                //收到发送过来的业务消息ACK确认消息，移除待ACK确认消息对象
                ServerSendMessageProxy.receiveClientAck(msgDto.getMsgId());
            }
            //每次收到业务数据消息就立马发送消息ID给服务端-这行代码是告诉发送端此消息已经收到无需重复发送
            if (StringUtils.isNotBlank(msgDto.getMsgId())) {
                serverBusinessService.ackMsg(msgDto,ctx,delimiter);
            }

            if(StringUtils.equals(msgDto.getActionType(), ActionTypeEnum.BIZ.getCode())){
                StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
                builder.append(msgDto.getMsgId()).append(msgDto.getClientId()).append(md5Key);
                if(!StringUtils.equals(DigestUtil.md5Hex(builder.toString()),msgDto.getBizSign())){
                    //但是这里不从ackMsgIdSet手动移除msgId，防止多次重复发送无法判断
                    return;
                }
                serverBusinessService.doBusiness(msgDto,ctx,delimiter,md5Key);
            }

        }catch (Exception ex){
            log.error("Server服务接收到客户端消息: {},error:{}", msg,ex.getMessage(),ex);
        }finally {
            ReferenceCountUtil.release(msg);
        }

    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause) {
        if (cause instanceof java.io.IOException) {
            // 大概率为客户端突然断开或网络问题
            log.warn("Server服务捕获异常,IO异常，可能是网络断开或客户端主动断开");
        } else {
            log.error("Server服务捕获异常,未知异常断开", cause);
        }
        String clientId = getClientId(ctx);
        if (clientId != null) {
            ServerContent.CHANNEL_CONCURRENT_HASH_MAP.remove(clientId);
        }
        log.error("Server服务捕获异常,并主动断开与客户端的连接:{}",cause.getCause(),cause);
        ctx.close(); // 主动关闭连接
    }
    /**
     * 通道 Read 读取 Complete 完成
     * 做刷新操作 ctx.flush()
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            int count=0;

            if (event.state() == IdleState.READER_IDLE) {
                count = readFailedHeartbeatCount.incrementAndGet(); // 读空闲就加1
                if (count >= MAX_FAILED_HEARTBEATS) {
                    log.error("Server服务心跳连续空闲 {} 次，{},TCP 假死或无响应",  MAX_FAILED_HEARTBEATS,event.state());
                    String clientId = getClientId(ctx);
                    if (clientId != null) {
                        ServerContent.CHANNEL_CONCURRENT_HASH_MAP.remove(clientId);
                    }
                    ctx.close(); // 这里强制断开连接，触发上层重连
                    return;
                }
                // 主动发送心跳包及业务
                serverBusinessService.userEventTriggeredSend(ctx,event.state(),delimiter,readFailedHeartbeatCount);
            }

        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private String getClientId(ChannelHandlerContext ctx) {
        return ctx.channel().attr(CLIENT_ID_KEY).get();
    }

}
