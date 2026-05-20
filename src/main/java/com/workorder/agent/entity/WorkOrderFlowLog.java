package com.workorder.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

@Data
@TableName("work_order_flow_log")
public class WorkOrderFlowLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单ID */
    private Long orderId;

    /** 关联工单编号 */
    private String orderNo;

    /** 操作类型 submit/parse/assign/auto_finish/manual_finish/close/timeout_warn/review */
    private String action;

    /** 操作人 */
    private String operator;

    /** 操作内容说明 */
    private String content;

    /** 操作时间 */
    private LocalDateTime createTime;
}
