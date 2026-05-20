package com.workorder.agent.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
public class WorkOrderSubmitDTO {

    /** 工单标题 */
    @NotBlank(message = "工单标题不能为空")
    private String title;

    /** 工单详细内容 */
    @NotBlank(message = "工单内容不能为空")
    private String content;

    /** 提交人姓名 */
    private String submitUserName;
}
