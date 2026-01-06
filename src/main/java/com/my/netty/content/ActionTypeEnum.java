package com.my.netty.content;

public enum ActionTypeEnum {
    AUTH("AUTH"),
    ACK("ACK"),
    KEEPALIVE("KEEPALIVE"),
    BIZ("BIZ");

    private final String code;

    ActionTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据 code 反查枚举（可选）
     */
    public static ActionTypeEnum fromCode(String code) {
        for (ActionTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null; // 或抛异常
    }
}
