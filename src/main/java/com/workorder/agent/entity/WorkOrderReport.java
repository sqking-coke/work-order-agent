package com.workorder.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

@Data
@TableName("work_order_report")
public class WorkOrderReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报告编号 */
    private String reportNo;

    /** 报告类型 day/week/month */
    private String reportType;

    /** 报告周期 */
    private String reportPeriod;

    /** 工单总数 */
    private Integer totalCount;

    /** 办结工单数 */
    private Integer finishCount;

    /** AI自动办结数 */
    private Integer aiAutoCount;

    /** 超时工单数 */
    private Integer timeoutCount;

    /** 重复工单数 */
    private Integer duplicateCount;

    /** AI复盘报告内容 */
    private String reportContent;

    /** 生成时间 */
    private LocalDateTime createTime;
}
