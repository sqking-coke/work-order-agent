package com.workorder.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

@Data
@TableName("work_dept_config")
public class WorkDeptConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部门名称 */
    private String deptName;

    /** 负责的业务模块 */
    private String module;

    /** 默认处理人ID */
    private Long handlerUserId;

    /** 默认处理人姓名 */
    private String handlerUserName;

    /** 部门处理优先级 数字越小越优先 */
    private Integer priority;

    /** 状态 0禁用 1启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
