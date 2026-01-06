package com.my.netty.server;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerContent {

    //存储客户ID和channel的关系
    public static Map<String, Channel> CHANNEL_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();

    public final static ConcurrentHashMap<String, Long> PENDING_ACK_MESSAGES = new ConcurrentHashMap<>();
}
