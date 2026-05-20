package com.workorder.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号（唯一） */
    private String orderNo;

    /** 工单标题 */
    private String title;

    /** 工单详细内容 */
    private String content;

    /** 工单类型 consult/fault/appeal/suggest/ops_error/func_error */
    private String workType;

    /** 优先级 1紧急/2高/3中/4低 */
    private Integer priority;

    /** 所属业务模块 */
    private String module;

    /** 状态 0待处理/1处理中/2已办结/3已关闭 */
    private Integer status;

    /** 处理人ID */
    private Long handlerUserId;

    /** 处理人姓名 */
    private String handlerUserName;

    /** 责任部门 */
    private String deptName;

    /** AI自动答复内容 */
    private String aiAnswer;

    /** AI解析完整结果JSON */
    private String aiParseResult;

    /** 是否AI自动办结 0否 1是 */
    private Integer isAutoFinish;

    /** 是否重复工单 0否 1是 */
    private Integer isDuplicate;

    /** 关联的重复工单ID */
    private Long duplicateOrderId;

    /** 提交人姓名 */
    private String submitUserName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 办结时间 */
    private LocalDateTime finishTime;
}
