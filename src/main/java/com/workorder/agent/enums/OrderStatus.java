package com.workorder.agent.enums;

import lombok.Getter;

/**
 * 工单状态枚举。
 */
@Getter
public enum OrderStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    FINISHED(2, "已办结"),
    CLOSED(3, "已关闭");

    private final int code;
    private final String label;

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return PENDING;
    }
}
