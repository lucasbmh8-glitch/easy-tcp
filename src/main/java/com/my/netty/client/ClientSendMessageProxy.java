package com.my.netty.client;

import com.google.gson.Gson;
import com.my.netty.vo.ActivityMsgVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.channel.Channel;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public class ClientSendMessageProxy {

    private static final Gson sendGson = new Gson();
    private static final Logger log = LoggerFactory.getLogger(ClientSendMessageProxy.class);

    public static  String SERVER_DELIMITER= StringUtils.EMPTY;

    private static final int MAX_ACK_BROADCAST_RETRY = 10;

    // 消息超时时间，单位毫秒，20秒
    private static final long MSG_ACK_TIMEOUT_MS = 20_000;

    // 使用 DelayQueue 代替 schedule()
    private static final DelayQueue<ClientDelayRetryTask> retryQueue = new DelayQueue<>();
    //适合执行“立即执行”的异步任务，且任务之间顺序执行，不并行，DelayQueue 里 take() 出来的重试任务，处理这些任务是串行的，一次只执行一个任务，且任务执行时间不可预测
    private static final ExecutorService retryExecutor = Executors.newSingleThreadExecutor();

    //这是一个单线程的 调度线程池，支持基于时间的周期性或延迟任务调度（比如 scheduleAtFixedRate），单独调度线程，定期清理超时msgId，避免内存泄漏，有可能ACK就是无法到达，实际上已经超过了20秒了，这个时候就需要把msgId移除
    private static final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int DEFAULT_WORKER_THREADS = Runtime.getRuntime().availableProcessors();
    //这里不用单线程池，是因为这里存在并发场景
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(DEFAULT_WORKER_THREADS*2);

    static {
        // 重试任务线程
        retryExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ClientDelayRetryTask task = retryQueue.take();
                    handleRetry(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("发送消息，处理重试任务异常", e);
                }
            }
        });

        // 定时清理超时未ACK的消息，避免 msgIdArrayMap 堆积，20秒执行一次
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<String, Long>> it = ClientContent.PENDING_ACK_MESSAGES.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Long> entry = it.next();
                if (now - entry.getValue() > MSG_ACK_TIMEOUT_MS) {
                    it.remove();
                }
            }
        }, MSG_ACK_TIMEOUT_MS, MSG_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public static void sendBusinessMessage(ActivityMsgVo vo, Channel channel) {
        ClientContent.PENDING_ACK_MESSAGES.put(vo.getMsgId(), System.currentTimeMillis());
        String message = sendGson.toJson(vo);
        String fullMsg = message.concat(SERVER_DELIMITER);
        writeAndFlushBatch(fullMsg, vo.getMsgId(), channel);

        retryQueue.offer(new ClientDelayRetryTask(vo.getMsgId(), message, 1, 2, TimeUnit.SECONDS,channel));

    }

    private static void handleRetry(ClientDelayRetryTask task) {
        String msgId = task.getMsgId();

        // 如果ACK已收到，停止重试
        if (!ClientContent.PENDING_ACK_MESSAGES.containsKey(msgId)) {
            return;
        }

        if (task.getRetryCount() > MAX_ACK_BROADCAST_RETRY) {
            ClientContent.PENDING_ACK_MESSAGES.remove(msgId);
            return;
        }

        // 重试时更新时间戳
        ClientContent.PENDING_ACK_MESSAGES.put(msgId, System.currentTimeMillis());

        String fullMsg = task.getMessage().concat(SERVER_DELIMITER);
        writeAndFlushBatch(fullMsg, msgId, task.getChannel());

        // 继续加入队列进行下一轮重试
        retryQueue.offer(new ClientDelayRetryTask(msgId, task.getMessage(), task.getRetryCount() + 1, 2, TimeUnit.SECONDS,task.getChannel()));
    }

    private static void writeAndFlushBatch(String content, String msgId, Channel channel) {
        if(Objects.isNull(channel)){
          return;
        }
        if (!channel.isOpen() || !channel.isActive()) {
            try {
                channel.close();
            } catch (Exception e) {
                log.error("发送消息，关闭不可用通道异常: {}", e.getMessage());
            }
        } else {
            channel.writeAndFlush(content);
        }
    }

    /**
     * 收到客户端ACK调用此方法，移除msgId
     */
    public static void receiveClientAck(String msgId) {
        ClientContent.PENDING_ACK_MESSAGES.remove(msgId);
    }
    public static void shutdown() {
        // 先发起关闭线程池
        retryExecutor.shutdownNow();
        cleanupScheduler.shutdownNow();
        scheduler.shutdownNow();

        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("清理对象，retryExecutor 未能及时关闭");
            }
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("清理对象，cleanupScheduler 未能及时关闭");
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("清理对象，scheduler 未能及时关闭");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("清理对象，线程池关闭等待被中断");
        }

    }

}
