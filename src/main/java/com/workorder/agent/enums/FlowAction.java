package com.workorder.agent.enums;

import lombok.Getter;

/**
 * 工单流转操作类型枚举。
 */
@Getter
public enum FlowAction {

    SUBMIT("submit", "提交"),
    PARSE("parse", "AI解析"),
    ASSIGN("assign", "分派"),
    AUTO_FINISH("auto_finish", "AI自动办结"),
    MANUAL_FINISH("manual_finish", "人工办结"),
    CLOSE("close", "关闭"),
    TIMEOUT_WARN("timeout_warn", "超时预警"),
    REVIEW("review", "复盘");

    private final String code;
    private final String label;

    FlowAction(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
