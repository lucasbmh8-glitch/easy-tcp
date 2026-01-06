package com.my.netty.client;

import com.my.netty.content.TcpProxyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TcpClient {
    private static final Logger log = LoggerFactory.getLogger(TcpClient.class);

    /** 默认值 **/
    private static final String DEFAULT_DELIMITER = "@";
    private static final int DEFAULT_MAX_RETRY = 10;
    private static final int DEFAULT_MAX_FRAME_LENGTH = 200_000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int DEFAULT_WRITE_TIMEOUT = 1;
    private static final int DEFAULT_WORKER_THREADS = 6;

    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyType;

    private final String clientId;

    private final String md5Key;

    private final String delimiterStr;
    private final int maxRetry;
    private final int maxFrameLength;
    private final int connectTimeoutMillis;
    private final int writeTimeoutSeconds;
    private final int workerThreads;

    private final AtomicInteger retryCounter = new AtomicInteger(0);
    private final Lock reconnectLock = new ReentrantLock();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private volatile boolean shuttingDown = false;
    private final ByteBuf delimiterByteBuf;

    private final NioEventLoopGroup group;
    private final Bootstrap bootstrap;
    private final EventLoop defaultEventLoop;
    private final DefaultClientBusinessService clientBusinessService;

    private TcpClient(Builder builder) {
        this.proxyEnabled = builder.proxyEnabled;
        this.proxyHost = builder.proxyHost;
        this.proxyPort = builder.proxyPort;
        this.proxyType = builder.proxyType;
        this.clientId = builder.clientId;
        this.md5Key = builder.md5Key;
        this.clientBusinessService = builder.clientBusinessService;

        this.maxRetry = builder.maxRetry;
        this.maxFrameLength = builder.maxFrameLength;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.writeTimeoutSeconds = builder.writeTimeoutSeconds;
        this.workerThreads = builder.workerThreads;

        this.delimiterStr = builder.delimiter;
        this.delimiterByteBuf = Unpooled.copiedBuffer(delimiterStr, CharsetUtil.UTF_8);

        this.group = new NioEventLoopGroup(workerThreads);
        this.bootstrap = new Bootstrap()
                .group(this.group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);

        this.defaultEventLoop = group.next();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /** Builder **/
    public static class Builder {
        private boolean proxyEnabled = false;
        private String clientId;
        private String md5Key;
        private String proxyType;
        private String proxyHost;
        private int proxyPort;
        private String delimiter = DEFAULT_DELIMITER;
        private DefaultClientBusinessService clientBusinessService;

        private int maxRetry = DEFAULT_MAX_RETRY;
        private int maxFrameLength = DEFAULT_MAX_FRAME_LENGTH;
        private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
        private int writeTimeoutSeconds = DEFAULT_WRITE_TIMEOUT;
        private int workerThreads = DEFAULT_WORKER_THREADS;

        public Builder withoutProxy() {
            this.proxyEnabled = false;
            return this;
        }

        public Builder enableProxy(String proxyHost, int proxyPort) {
            this.proxyEnabled = true;
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            return this;
        }

        public Builder proxyType(String proxyType) {
            this.proxyType = proxyType;
            return this;
        }

        public Builder delimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder md5Key(String md5Key) {
            this.md5Key = md5Key;
            return this;
        }

        public Builder maxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
            return this;
        }

        public Builder maxFrameLength(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
            return this;
        }

        public Builder connectTimeoutMillis(int millis) {
            this.connectTimeoutMillis = millis;
            return this;
        }

        public Builder writeTimeoutSeconds(int seconds) {
            this.writeTimeoutSeconds = seconds;
            return this;
        }

        public Builder workerThreads(int threads) {
            this.workerThreads = threads;
            return this;
        }

        public Builder businessService(DefaultClientBusinessService service) {
            this.clientBusinessService = service;
            return this;
        }

        public TcpClient build() {
            if (clientBusinessService == null) {
                throw new IllegalArgumentException("ClientBusinessService cannot be null");
            }
            if (proxyEnabled) {
                if (proxyHost == null || proxyHost.isEmpty()) {
                    throw new IllegalArgumentException("proxy host must be set when proxy is enabled");
                }
                if (proxyPort <= 0) {
                    throw new IllegalArgumentException("proxy port must be valid");
                }
            }
            return new TcpClient(this);
        }
    }

    /** 连接方法 **/
    public void connect( String ip, int port) {
        if (shuttingDown || connected.get()){
            return;
        }

        boolean lockAcquired = false;
        try {
            lockAcquired = reconnectLock.tryLock(200, TimeUnit.MILLISECONDS);
            if (!lockAcquired) return;
            TcpProxyEnum proxyTypeEnum = TcpProxyEnum.fromCode(proxyType);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    if (proxyEnabled) {
                        ProxyHandler proxyHandler;
                        switch (Objects.requireNonNull(proxyTypeEnum)) {
                            case SOCKET5:
                                proxyHandler = new Socks5ProxyHandler(new InetSocketAddress(proxyHost, proxyPort));
                                break;
                            case HTTP:
                                proxyHandler = new HttpProxyHandler(new InetSocketAddress(proxyHost, proxyPort));
                                break;
                            default:
                                throw new IllegalArgumentException("Unsupported proxy type");
                        }
                        proxyHandler.connectFuture().addListener(future -> {
                            if (!future.isSuccess()){
                                log.error("代理连接失败: {}", future.cause().getMessage(), future.cause());
                            }
                        });
                        ch.pipeline().addLast(proxyHandler);
                    }

                    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxFrameLength, delimiterByteBuf));
                    ch.pipeline().addLast(new StringDecoder());
                    ch.pipeline().addLast(new IdleStateHandler(0, 2, 0, TimeUnit.SECONDS));
                    ch.pipeline().addLast(new WriteTimeoutHandler(writeTimeoutSeconds));
                    ch.pipeline().addLast(new StringEncoder());
                    ch.pipeline().addLast(new TcpClientHandler(clientBusinessService, delimiterStr,clientId,md5Key));
                }
            }).option(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.connect(ip, port);

            future.addListener((ChannelFuture f) -> {
                if (f.isSuccess()) {
                    // 连接成功
                    connected.set(true);
                    retryCounter.set(0);
                    Channel channel = f.channel();
                    // 注册 closeFuture 用于监听断线
                    channel.closeFuture().addListener(cf -> {
                        log.warn("连接服务连接关闭，准备重连");
                        connected.set(false);
                        triggerReconnect(ip, port, channel.eventLoop());
                    });

                } else {
                    // 连接失败，立即重连
                    connected.set(false);
                    log.error("连接失败 {}:{}, 原因: {}", ip, port, f.cause().getMessage(), f.cause());
                    triggerReconnect(ip, port, defaultEventLoop);
                }
            });

        } catch (InterruptedException e) {
            log.error("获取重连锁中断", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lockAcquired) reconnectLock.unlock();
        }
    }

    private void triggerReconnect(String ip, Integer port, EventLoop eventLoop) {
        log.warn("断开连接重连");

        if (shuttingDown) {
            log.warn("断开连接重连, 客户端已关闭，停止重连");
            return;
        }
        int currentRetry = retryCounter.incrementAndGet();
        if (currentRetry > maxRetry) {
            log.error("断开连接重连,  超过最大重连次数 {}，10秒以后再尝试重连", maxRetry);
            retryCounter.set(0);
            eventLoop.schedule(() -> connect(ip, port), 10, TimeUnit.SECONDS);
        } else {
            eventLoop.schedule(() -> connect(ip, port), 1, TimeUnit.SECONDS);
        }
    }


    public void shutdown() {
        shuttingDown = true;
        connected.set(false);
        log.warn("正在关闭客户端...");
        // Gracefully shutdown the group and release resources
        group.shutdownGracefully().addListener(future -> {
            if (future.isSuccess()) {
                log.warn("客户端已优雅关闭");
            } else {
                log.error("客户端关闭失败");
            }
        });
    }

}
