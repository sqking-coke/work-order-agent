package com.workorder.agent.enums;

import lombok.Getter;

/**
 * 工单类型枚举。
 */
@Getter
public enum WorkType {

    CONSULT("consult", "咨询类"),
    FAULT("fault", "故障类"),
    APPEAL("appeal", "申诉类"),
    SUGGEST("suggest", "建议类"),
    OPS_ERROR("ops_error", "运维报错类"),
    FUNC_ERROR("func_error", "功能异常类");

    private final String code;
    private final String label;

    WorkType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static WorkType of(String code) {
        for (WorkType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return CONSULT;
    }
}
