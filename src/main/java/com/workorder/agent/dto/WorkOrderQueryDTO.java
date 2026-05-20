package com.workorder.agent.dto;

import lombok.*;

@Data
public class WorkOrderQueryDTO {

    /** 工单类型 */
    private String workType;

    /** 优先级 1紧急/2高/3中/4低 */
    private Integer priority;

    /** 状态 0待处理/1处理中/2已办结/3已关闭 */
    private Integer status;

    /** 业务模块 */
    private String module;

    /** 处理人ID */
    private Long handlerUserId;

    /** 责任部门 */
    private String deptName;

    /** 关键词搜索 */
    private String keyword;

    /** 是否AI自动办结 */
    private Integer isAutoFinish;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 页码 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer pageSize = 20;
}
