package com.my.netty.client;

import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import com.my.netty.content.ActionTypeEnum;
import com.my.netty.vo.ActivityMsgVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultClientBusinessService {

    Gson gson = new Gson();

    private static final Logger log = LoggerFactory.getLogger(DefaultClientBusinessService.class);
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
     * 连接建立立马发送一个授权指令
     * @param ctx
     */
    public void authMsgSend(ChannelHandlerContext ctx,String delimiter,String clientId,String md5Key) {

        String msgId = UUID.randomUUID().toString();
        ActivityMsgVo<String> msg = new ActivityMsgVo<String>().setMsgId(msgId)
                .setActionType(ActionTypeEnum.AUTH.getCode()).setClientId(clientId).setBizSign(signMd5(clientId,msgId, md5Key));

        ByteBuf buf = Unpooled.copiedBuffer(gson.toJson(msg).concat(delimiter), CharsetUtil.UTF_8);
        ctx.writeAndFlush(buf);
    }

    /**
     * 心跳检测次数超过最大预设值后的处理实现
     * @param ctx
     * @param idleState
     */
    public void userEventTriggeredSend(ChannelHandlerContext ctx, IdleState idleState, String delimiter, AtomicInteger writeFailedHeartbeatCount) {

        ActivityMsgVo<String> msg = new ActivityMsgVo<String>()
                .setActionType(ActionTypeEnum.KEEPALIVE.getCode());

        ByteBuf buf = Unpooled.copiedBuffer(gson.toJson(msg).concat(delimiter), CharsetUtil.UTF_8);

        ctx.writeAndFlush(buf).addListener(future -> {
            if (!future.isSuccess()) {
                log.error("心跳包发送失败（不是网络断开，只是写出失败）:{},{}",idleState, future.cause().getMessage(), future.cause());
                // 可选：这里不重置计数
            } else {
                writeFailedHeartbeatCount.set(0);
            }
        });
    }


    /**
     * 接收服务端发送过来的业务消息后的业务处理
     * @param msgDto
     * @param ctx
     * @param delimiter
     * @param md5Key
     */
    public void doBusiness(ActivityMsgVo msgDto, ChannelHandlerContext ctx,String delimiter,String md5Key) {

        //最终调用ClientSendMessageProxy.sendBusinessMessage(ActivityMsgVo vo, Channel channel)
        //todo
    }

    /**
     * 根据clientId发送消息给服务端
     * @param msgDto
     * @param delimiter
     * @param md5Key
     * @param clientId
     */
    public void doBusinessByClientId(ActivityMsgVo msgDto, String delimiter,String md5Key,String clientId) {

        //根据clientId 从ClientContent的CHANNEL_CONCURRENT_HASH_MAP获取channel
        //最终调用ClientSendMessageProxy.sendBusinessMessage(ActivityMsgVo vo, Channel channel)
        //todo
    }

    /**
     * 根据clientIdList发送消息给服务端
     * @param msgDto
     * @param delimiter
     * @param md5Key
     * @param clientIdList
     */
    public void doBusinessByClientIdList(ActivityMsgVo msgDto, String delimiter, String md5Key, List<String> clientIdList) {
        //根据clientIdList 从ClientContent的CHANNEL_CONCURRENT_HASH_MAP获取channel
    }


    /**
     * 生成MD5签名
     * @param clientId
     * @param msgId
     * @param md5Key
     * @return
     */
    public String signMd5(String clientId,String msgId, String md5Key){
        StringBuilder builder = new StringBuilder(StringUtils.EMPTY);
        builder.append(msgId).append(clientId).append(md5Key);
        return  DigestUtil.md5Hex(builder.toString());
    }

}
