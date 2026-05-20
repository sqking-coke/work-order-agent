package com.workorder.agent.dto;

import lombok.*;

import java.util.*;

/**
 * AI 智能解析结果
 */
@Data
public class AiParseResult {

    /** 工单意图分类 */
    private String workType;

    /** 分类置信度 0-1 */
    private Double typeConfidence;

    /** 优先级 1紧急/2高/3中/4低 */
    private Integer priority;

    /** 优先级判定理由 */
    private String priorityReason;

    /** 所属业务模块 */
    private String module;

    /** 问题关键词 */
    private List<String> keywords;

    /** 问题摘要（一句话） */
    private String summary;

    /** 用户核心诉求 */
    private String userDemand;

    /** 工单复杂度：simple/complex */
    private String complexity;

    /** 是否可自动办结 */
    private Boolean canAutoFinish;

    /** 自动办结判断理由 */
    private String autoFinishReason;

    /** 建议处理部门 */
    private String suggestDept;

    /** 是否需要紧急处理 */
    private Boolean needUrgent;
}
