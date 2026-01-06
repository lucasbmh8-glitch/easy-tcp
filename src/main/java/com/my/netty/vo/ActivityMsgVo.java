package com.my.netty.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class ActivityMsgVo<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一 ID */
    private String msgId;

    /** 客户端ID ID,消息类型为BIZ和AUTH才需要 */
    private String clientId;

    /** 操作类型 */
    private String actionType;
    /**
     * 业务数据签名，消息类型为BIZ才需要
     */
    private String bizSign;

    /** 泛型承载业务数据 */
    private T data;
}