package com.my.netty.content;

public enum TcpProxyEnum {

    SOCKET5("SOCKET5"),
    HTTP("HTTP");

    private final String code;

    TcpProxyEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static TcpProxyEnum fromCode(String code) {
        for (TcpProxyEnum content : values()) {
            if (content.code.equals(code)) {
                return content;
            }
        }
        return null; // 或 throw IllegalArgumentException
    }
}
