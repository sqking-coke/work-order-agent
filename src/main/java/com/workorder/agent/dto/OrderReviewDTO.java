package com.workorder.agent.dto;

import lombok.*;

/**
 * AI 工单调优复盘请求
 */
@Data
public class OrderReviewDTO {

    /** 待复盘的工单ID */
    private Long orderId;

    /** 额外提示 */
    private String extraPrompt;
}
