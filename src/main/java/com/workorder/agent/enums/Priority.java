package com.workorder.agent.enums;

import lombok.Getter;

/**
 * 工单优先级枚举。
 */
@Getter
public enum Priority {

    URGENT(1, "紧急"),
    HIGH(2, "高"),
    MEDIUM(3, "中"),
    LOW(4, "低");

    private final int code;
    private final String label;

    Priority(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static Priority of(int code) {
        for (Priority p : values()) {
            if (p.code == code) {
                return p;
            }
        }
        return MEDIUM;
    }
}
