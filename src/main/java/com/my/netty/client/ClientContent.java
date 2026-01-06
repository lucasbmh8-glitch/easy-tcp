package com.my.netty.client;

import java.util.concurrent.ConcurrentHashMap;
import io.netty.channel.Channel;

public class ClientContent {
    public final static ConcurrentHashMap<String, Long> PENDING_ACK_MESSAGES = new ConcurrentHashMap<>();

    //存储客户ID和channel的关系
    public final static ConcurrentHashMap<String,Channel> CHANNEL_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
}
