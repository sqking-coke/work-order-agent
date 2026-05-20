package com.workorder.agent.tool;

import com.workorder.agent.dto.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

/**
 * 工单智能解析工具
 * 调用大模型完成工单分类、优先级分级、关键信息抽取、复杂度判定
 */
@Slf4j
@Component
public class IntelligentParserTool {

    @Autowired
    private LLMClient llmClient;

    private static final String PARSE_SYSTEM_PROMPT = """
            你是一个企业智能工单处理系统的分析引擎。请对用户提交的工单进行智能解析，严格按JSON格式输出分析结果。

            ## 工单类型定义（workType 字段）：
            - consult：咨询类（用户提问、使用咨询、流程询问）
            - fault：故障类（系统不可用、功能异常、报错）
            - appeal：申诉类（用户投诉、退款申诉、纠纷）
            - suggest：建议类（功能建议、优化建议）
            - ops_error：运维报错类（服务器错误、数据库报错、中间件异常）
            - func_error：功能异常类（功能不按预期工作、界面bug）

            ## 优先级定义（priority 字段）：
            - 1=紧急：影响核心业务、大量用户受影响、系统瘫痪
            - 2=高：重要功能不可用、影响范围较大
            - 3=中：部分用户影响、有临时替代方案
            - 4=低：咨询类、建议类、边缘场景

            ## 复杂度定义（complexity 字段）：
            - simple：简单咨询、常见FAQ可覆盖、无需深入排查
            - complex：需要技术排查、涉及多系统、需要人工介入

            ## canAutoFinish 判定规则：
            - 咨询类 + 简单问题 → true
            - 故障类 / 运维报错类 / 功能异常类 → false
            - 申诉类 → false（需人工审核）
            - 建议类 → false（需产品评估）

            返回格式必须是纯 JSON：
            {
              "workType": "fault",
              "typeConfidence": 0.92,
              "priority": 2,
              "priorityReason": "影响支付核心功能",
              "module": "支付模块",
              "keywords": ["支付", "超时", "订单"],
              "summary": "用户反馈支付时出现超时报错",
              "userDemand": "希望尽快恢复支付功能",
              "complexity": "complex",
              "canAutoFinish": false,
              "autoFinishReason": "故障类需技术排查定位",
              "suggestDept": "技术运维部",
              "needUrgent": false
            }
            """;

    /**
     * 智能解析工单
     */
    public AiParseResult parse(String title, String content) {
        String userMessage = String.format("请解析以下工单：\n标题：%s\n内容：%s", title, content);
        log.info("开始智能解析工单: {}", title);

        try {
            AiParseResult result = llmClient.chatForObject(PARSE_SYSTEM_PROMPT, userMessage, AiParseResult.class);
            if (result != null) {
                log.info("解析完成: type={}, priority={}, complexity={}, canAutoFinish={}",
                        result.getWorkType(), result.getPriority(), result.getComplexity(), result.getCanAutoFinish());
            }
            return result;
        } catch (Exception e) {
            log.error("智能解析失败，使用默认值", e);
            // 兜底：默认中优先级人工处理
            AiParseResult fallback = new AiParseResult();
            fallback.setWorkType("consult");
            fallback.setTypeConfidence(0.5);
            fallback.setPriority(3);
            fallback.setPriorityReason("解析异常兜底");
            fallback.setModule("综合");
            fallback.setKeywords(java.util.Collections.emptyList());
            fallback.setSummary(title);
            fallback.setUserDemand(content);
            fallback.setComplexity("complex");
            fallback.setCanAutoFinish(false);
            fallback.setAutoFinishReason("AI解析异常，需人工处理");
            fallback.setSuggestDept("客服部");
            fallback.setNeedUrgent(false);
            return fallback;
        }
    }
}
