package com.my.netty.client;

import cn.hutool.crypto.digest.DigestUtil;
import com.my.netty.content.ActionTypeEnum;
import com.my.netty.vo.ActivityMsgVo;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import io.netty.handler.proxy.ProxyConnectException;

import java.util.concurrent.atomic.AtomicInteger;

public class TcpClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(TcpClientHandler.class);

    public static final int MAX_FAILED_HEARTBEATS = 2;

    private DefaultClientBusinessService clientBusinessService;

    private final String delimiter;

    private final Gson gson = new Gson();

    private final String clientId;

    private final String md5Key;

    private final AtomicInteger writeFailedHeartbeatCount = new AtomicInteger(0);

    public TcpClientHandler(DefaultClientBusinessService clientBusinessService, String delimiter, String clientId, String md5Key) {
        this.clientBusinessService = clientBusinessService;
        this.delimiter=delimiter;
        this.clientId=clientId;
        this.md5Key=md5Key;
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("通道非活动状态（断线）");
        ClientContent.CHANNEL_CONCURRENT_HASH_MAP.remove(clientId);
        super.channelInactive(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 当连接建立时发送消息,认证此tcp的业务唯一用户ID
        clientBusinessService.authMsgSend(ctx,delimiter,clientId,md5Key);
        writeFailedHeartbeatCount.set(0); // 有消息进来则重置心跳当前最大次数
        ClientContent.CHANNEL_CONCURRENT_HASH_MAP.put(clientId,ctx.channel());
        log.warn("新的连接建立");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        writeFailedHeartbeatCount.set(0); // 有消息进来则重置心跳当前最大次数
        if (msg instanceof String) {
            String received = (String) msg;
            try {
                ActivityMsgVo msgDto = gson.fromJson(received, ActivityMsgVo.class);
                if(StringUtils.equals(msgDto.getActionType(), ActionTypeEnum.KEEPALIVE.getCode())){
                    return;
                }
                if (StringUtils.equals(msgDto.getActionType(), ActionTypeEnum.ACK.getCode())) {
                    //收到服务端发送过来的业务消息ACK确认消息，移除待ACK确认消息对象
                    ClientSendMessageProxy.receiveClientAck(msgDto.getMsgId());
                }
                //每次收到业务数据消息就立马发送消息ID给服务端-这行代码是告诉服务端此消息已经收到无需重复发送
                if (StringUtils.isNotBlank(msgDto.getMsgId())) {
                    clientBusinessService.ackMsg(msgDto,ctx,delimiter);
                }
                if(StringUtils.equals(msgDto.getActionType(), ActionTypeEnum.BIZ.getCode())){
                    StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
                    builder.append(msgDto.getMsgId()).append(msgDto.getClientId()).append(md5Key);
                    if(!StringUtils.equals(DigestUtil.md5Hex(builder.toString()),msgDto.getBizSign())){
                        //但是这里不从ackMsgIdSet手动移除msgId，防止多次重复发送无法判断
                        log.error("收到服务数据, msgId:{},明文:{},bizSign:{},MD5验证失败,丢弃消息", msgDto.getMsgId(),builder.toString(),msgDto.getBizSign());
                        return;
                    }
                    clientBusinessService.doBusiness(msgDto,ctx,delimiter,md5Key);
                }

            } catch (Exception e) {
                log.error("收到服务数据,消息异常:{}", e.getMessage());
            }finally {
                ReferenceCountUtil.release(msg);
            }
        } else {
            log.warn("收到服务数据, received unexpected message type: " + msg.getClass());
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("连接失败, connection error",cause);

        if (cause instanceof ProxyConnectException) {
            log.error("连接失败，SOCKS5 代理连接失败，可能是代理地址无效、端口错误或代理服务未启动");
        } else if (cause instanceof java.net.ConnectException) {
            log.error("连接失败，远程服务器无法连接，可能未启动或端口未开放");
        } else if (cause instanceof java.io.IOException) {
            String msg = (StringUtils.isNotBlank(cause.getMessage())?cause.getMessage():StringUtils.EMPTY);
            if (msg.contains("Connection reset")) {
                log.warn("连接失败，连接被远程服务器重置，可能服务主动断开");
            } else if (msg.contains("Broken pipe")) {
                log.warn("接失败，写入失败，可能代理或网络断开");
            } else if (msg.contains("Connection timed out")) {
                log.warn("连接失败，连接超时，可能网络异常或目标服务器无响应");
            }
        }
        ClientContent.CHANNEL_CONCURRENT_HASH_MAP.remove(clientId);
        ctx.close();
    }


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            int count=0;
            if (event.state() == IdleState.WRITER_IDLE) {
                count = writeFailedHeartbeatCount.incrementAndGet(); // 写空闲就加1

                if (count >= MAX_FAILED_HEARTBEATS) {
                    ClientContent.CHANNEL_CONCURRENT_HASH_MAP.remove(clientId);
                    log.error("心跳连续空闲 {} 次，{},TCP 假死或无响应，关闭连接",  MAX_FAILED_HEARTBEATS,event.state());
                    ctx.close(); // 这里强制断开连接，触发上层重连
                    return;
                }
                // 主动发送心跳包及业务
                clientBusinessService.userEventTriggeredSend(ctx,event.state(),delimiter,writeFailedHeartbeatCount);
            }

        } else {
            super.userEventTriggered(ctx, evt);
        }
    }



    //资源释放的钩子
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
    }

}