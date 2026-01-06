package com.my.netty.server;

import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import com.my.netty.content.ActionTypeEnum;
import com.my.netty.vo.ActivityMsgVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultServerBusinessService {

    Gson gson = new Gson();


    private static final Logger log = LoggerFactory.getLogger(DefaultServerBusinessService.class);
    /**
     * 手动ACK确认消息收到
     * @param ctx
     */
    public void ackMsg(ActivityMsgVo msgDto, ChannelHandlerContext ctx,String delimiter) {

        ActivityMsgVo<String> msg = new ActivityMsgVo<String>()
                .setMsgId(msgDto.getMsgId())
                .setActionType(ActionTypeEnum.ACK.getCode());

        ByteBuf buf = Unpooled.copiedBuffer(gson.toJson(msg).concat(delimiter), CharsetUtil.UTF_8);
        ctx.writeAndFlush(buf);
    }

    /**
     * 客户端发现心跳实现
     * @param ctx
     */
    public void keeplivedMsgSend(ChannelHandlerContext ctx,String delimiter) {

        ActivityMsgVo<String> msg = new ActivityMsgVo<String>()
                .setActionType(ActionTypeEnum.KEEPALIVE.getCode());

        ByteBuf buf = Unpooled.copiedBuffer(gson.toJson(msg).concat(delimiter), CharsetUtil.UTF_8);
        ctx.writeAndFlush(buf);
    }

    /**
     * 心跳检测次数超过最大预设值后的处理实现
     * @param ctx
     * @param idleState
     */
    public void userEventTriggeredSend(ChannelHandlerContext ctx, IdleState idleState, String delimiter, AtomicInteger readFailedHeartbeatCount) {

        ActivityMsgVo<String> msg = new ActivityMsgVo<String>()
                .setActionType(ActionTypeEnum.KEEPALIVE.getCode());

        ByteBuf buf = Unpooled.copiedBuffer(gson.toJson(msg).concat(delimiter), CharsetUtil.UTF_8);

        ctx.writeAndFlush(buf).addListener(future -> {
            if (!future.isSuccess()) {
                log.error("Server服务心跳包发送失败（不是网络断开，只是写出失败）:{},{}",idleState, future.cause().getMessage(), future.cause());
                // 可选：这里不重置计数
            } else {
                readFailedHeartbeatCount.set(0);
            }
        });
    }
    /**
     * 接收到来自客户端的业务消息后发送消息给此客户端
     * 业务类型消息必须md5签名：
     *   StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
     *   builder.append(msgDto.getMsgId()).append(msgDto.getClientId()).append(md5Key);
     * @param msgDto
     * @param ctx
     */
    public void doBusiness(ActivityMsgVo msgDto, ChannelHandlerContext ctx, String delimite,String md5Key) {
        //最终调用ServerSendMessageProxy.sendBusinessMessage(ActivityMsgVo vo, Channel channel)
    }

    /**
     * 给指定clientId客户端发送消息
     * 业务类型消息必须md5签名：
     *   StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
     *   builder.append(msgDto.getMsgId()).append(msgDto.getClientId()).append(md5Key);
     * @param clientId
     * @param delimite
     * @param md5Key
     */
    public void doBusinessByClientId(String clientId, String delimite,String md5Key){

        Channel channel1 = ServerContent.CHANNEL_CONCURRENT_HASH_MAP.get(clientId);
        if(Objects.nonNull(channel1)){
            //todo
            //最终调用ServerSendMessageProxy.sendBusinessMessage(ActivityMsgVo vo, Channel channel)
        }
    }

    /**
     * 给指定clientId集合客户端发送消息
     * 业务类型消息必须md5签名：
     *   StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
     *   builder.append(msgDto.getMsgId()).append(msgDto.getClientId()).append(md5Key);
     * @param clientIdList
     * @param delimite
     * @param md5Key
     */
    public void doBusinessByClientList(List<String> clientIdList, String delimite, String md5Key){
        //clientIdList 从ClientContent的 ServerContent.clientMap获取channel
        //最终调用ServerSendMessageProxy.sendBusinessMessage(ActivityMsgVo vo, Channel channel)
        //todo
    }

    /**
     * 生成MD5签名
     * @param clientId
     * @param msgId
     * @param md5Key
     * @return
     */
    private String signMd5(String clientId,String msgId, String md5Key){
        StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
        builder.append(msgId).append(clientId).append(md5Key);
        return  DigestUtil.md5Hex(builder.toString());
    }
}
