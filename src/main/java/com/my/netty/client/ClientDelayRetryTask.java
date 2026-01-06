package com.my.netty.client;

import io.netty.channel.Channel;
import lombok.Data;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
public class ClientDelayRetryTask implements Delayed {

    private final String msgId;
    private final Channel channel;
    private final String message;
    private final int retryCount;
    private final long executeTime;

    public ClientDelayRetryTask(String msgId, String message,
                                int retryCount, long delay, TimeUnit unit, Channel channel) {
        this.msgId = msgId;
        this.channel = channel;
        this.message = message;
        this.retryCount = retryCount;
        this.executeTime = System.nanoTime() + unit.toNanos(delay);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(executeTime - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) return 0;
        if (o instanceof ClientDelayRetryTask) {
            return Long.compare(this.executeTime, ((ClientDelayRetryTask) o).executeTime);
        }
        return 0;
    }
}