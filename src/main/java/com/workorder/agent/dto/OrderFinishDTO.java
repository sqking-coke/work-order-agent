package com.workorder.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单手动办结请求。
 */
@Data
public class OrderFinishDTO {

    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    /** 处理人姓名 */
    private String handlerName;

    /** 办结备注 */
    private String finishContent;
}
