package com.workorder.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.*;

@Data
@TableName("work_knowledge")
public class WorkKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识点标题 */
    private String title;

    /** 知识点内容 */
    private String content;

    /** 所属业务模块 */
    private String module;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 状态 0禁用 1启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
