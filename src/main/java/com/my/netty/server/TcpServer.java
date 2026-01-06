package com.my.netty.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import javax.annotation.PreDestroy;

public class TcpServer {
    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);
    private static final int DEFAULT_WORKER_THREADS = Runtime.getRuntime().availableProcessors();

    // 默认值
    private static final int DEFAULT_BOSS_GROUP = 1;
    private static final int DEFAULT_WORK_CPU_MULTIPLE = 1;
    private static final int DEFAULT_MAX_FRAME_LENGTH = 200_000;
    private static final int DEFAULT_WRITE_TIMEOUT = 1;
    private static final int DEFAULT_SO_BACKLOG = 1024;
    private static final String DEFAULT_DELIMITER = "@";

    private final int port;
    private final int bossGroupNum;
    private final int workCpuMultiple;
    private final int maxFrameLength;
    private final int writeTimeoutSeconds;
    private final String delimiterStr;
    private final String md5Key;
    private final DefaultServerBusinessService serviceBusiness;
    private final int soBacklog;

    private ChannelFuture channelFuture = null;
    private NioEventLoopGroup bossGroup = null;
    private NioEventLoopGroup workerGroup = null;
    private final ServerBootstrap serverBootstrap = new ServerBootstrap();

    private TcpServer(Builder builder) {
        this.port = builder.port;
        this.bossGroupNum = builder.bossGroupNum;
        this.workCpuMultiple = builder.workCpuMultiple;
        this.maxFrameLength = builder.maxFrameLength;
        this.writeTimeoutSeconds = builder.writeTimeoutSeconds;
        this.delimiterStr = builder.delimiterStr;
        this.md5Key =builder.md5Key;
        ServerSendMessageProxy.SERVER_DELIMITER=builder.delimiterStr;
        this.serviceBusiness = builder.serviceBusiness;
        this.soBacklog = builder.soBacklog;
    }

    /** Builder 链式构建器 **/
    public static class Builder {
        private int port;
        private int bossGroupNum = DEFAULT_BOSS_GROUP;
        private int workCpuMultiple = DEFAULT_WORK_CPU_MULTIPLE;
        private int maxFrameLength = DEFAULT_MAX_FRAME_LENGTH;
        private int writeTimeoutSeconds = DEFAULT_WRITE_TIMEOUT;
        private String delimiterStr = DEFAULT_DELIMITER;
        private String md5Key;
        private DefaultServerBusinessService serviceBusiness;
        private int soBacklog = DEFAULT_SO_BACKLOG;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder bossGroupNum(int bossGroupNum) {
            this.bossGroupNum = bossGroupNum;
            return this;
        }

        public Builder workCpuMultiple(int multiple) {
            this.workCpuMultiple = multiple;
            return this;
        }

        public Builder maxFrameLength(int length) {
            this.maxFrameLength = length;
            return this;
        }

        public Builder writeTimeoutSeconds(int seconds) {
            this.writeTimeoutSeconds = seconds;
            return this;
        }

        public Builder delimiter(String delimiter) {
            this.delimiterStr = delimiter;
            return this;
        }

        public Builder md5Key(String md5Key) {
            this.md5Key = md5Key;
            return this;
        }

        public Builder serviceBusiness(DefaultServerBusinessService service) {
            this.serviceBusiness = service;
            return this;
        }

        public Builder soBacklog(int backlog) {
            this.soBacklog = backlog;
            return this;
        }

        public TcpServer build() {
            if (port <= 0) {
                throw new IllegalArgumentException("Port must be set and > 0");
            }
            if (serviceBusiness == null) {
                throw new IllegalArgumentException("ServerBusinessService must be set");
            }
            return new TcpServer(this);
        }
    }

    /** 启动 Netty Server **/
    public void run() throws Exception {
        bossGroup = new NioEventLoopGroup(bossGroupNum);
        workerGroup = new NioEventLoopGroup(DEFAULT_WORKER_THREADS * workCpuMultiple);

        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new NettyServerInitializer());

        channelFuture = serverBootstrap.bind(port).sync();

        channelFuture.addListener((GenericFutureListener<Future<? super Void>>) future -> {
            if (!future.isSuccess()) {
                log.error("Server服务器启动失败, port: {}", port);
            }
        });
    }

    /** 初始化 Channel（只处理接收端粘包/拆包，发送端不处理） **/
    private class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel socketChannel) {
            ByteBuf delimiter = Unpooled.copiedBuffer(delimiterStr, CharsetUtil.UTF_8);
            ChannelPipeline pipeline = socketChannel.pipeline();
            pipeline.addLast(new IdleStateHandler(4, 0, 0));
            pipeline.addLast(new WriteTimeoutHandler(writeTimeoutSeconds));
            pipeline.addLast(new DelimiterBasedFrameDecoder(maxFrameLength, delimiter));
            pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
            pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
            pipeline.addLast(new TcpServerHandler(serviceBusiness, delimiterStr,md5Key));
        }
    }

    /** 优雅关闭 Netty Server **/
    @PreDestroy
    public void stopNettyServer() {
        ServerSendMessageProxy.shutdown();
        if (channelFuture != null) {
            channelFuture.channel().close().awaitUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.warn("Server服务器关闭，释放资源...");
    }
}
