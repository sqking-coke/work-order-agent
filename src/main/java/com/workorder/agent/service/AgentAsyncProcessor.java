package com.workorder.agent.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.workorder.agent.dto.AiParseResult;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.entity.WorkOrderFlowLog;
import com.workorder.agent.mapper.WorkOrderFlowLogMapper;
import com.workorder.agent.mapper.WorkOrderMapper;
import com.workorder.agent.tool.DuplicateCheckTool;
import com.workorder.agent.tool.IntelligentParserTool;
import com.workorder.agent.tool.OrderAssignTool;
import com.workorder.agent.tool.RagKnowledgeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Agent 异步处理器，将 AI 解析、去重、决策、执行等耗时操作放入独立线程池执行。
 * 独立组件确保 Spring AOP 能正确拦截 @Async 和 @Transactional。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAsyncProcessor {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderFlowLogMapper flowLogMapper;
    private final IntelligentParserTool parserTool;
    private final DuplicateCheckTool duplicateCheckTool;
    private final OrderAssignTool orderAssignTool;
    private final RagKnowledgeTool ragKnowledgeTool;

    @Async("agentTaskExecutor")
    @Transactional
    public void process(WorkOrder order) {
        log.info("开始异步处理工单: orderNo={}", order.getOrderNo());

        try {
            // 第1步：AI 智能解析
            AiParseResult parseResult = parserTool.parse(order.getTitle(), order.getContent());
            if (parseResult == null) {
                log.warn("AI 解析返回空，保留默认状态: orderNo={}", order.getOrderNo());
                return;
            }

            order.setWorkType(parseResult.getWorkType());
            order.setPriority(parseResult.getPriority() != null ? parseResult.getPriority() : 3);
            order.setModule(parseResult.getModule());
            order.setAiParseResult(JSON.toJSONString(parseResult));

            addFlowLog(order.getId(), order.getOrderNo(), "parse", "AI Agent",
                    String.format("AI解析完成: 类型=%s, 优先级=%d, 复杂度=%s, 可自动办结=%s",
                            parseResult.getWorkType(), parseResult.getPriority(),
                            parseResult.getComplexity(), parseResult.getCanAutoFinish()));

            // 第2步：重复工单检测
            DuplicateCheckTool.DuplicateResult dupResult =
                    duplicateCheckTool.check(order.getTitle(), order.getContent());
            if (dupResult.isDuplicate() && dupResult.getSimilarOrder() != null) {
                order.setIsDuplicate(1);
                order.setDuplicateOrderId(dupResult.getSimilarOrder().getId());
                addFlowLog(order.getId(), order.getOrderNo(), "parse", "AI Agent",
                        "检测到重复工单: orderId=" + dupResult.getSimilarOrder().getId()
                                + ", 相似度=" + String.format("%.2f", dupResult.getSimilarity()));
            }

            // 第3步：策略决策 + 工具执行
            if (Boolean.TRUE.equals(parseResult.getCanAutoFinish())) {
                executeAutoFinish(order, parseResult);
            } else {
                executeAssign(order, parseResult);
            }

            workOrderMapper.updateById(order);

            log.info("工单处理完成: orderNo={}, status={}, isAutoFinish={}",
                    order.getOrderNo(), order.getStatus(), order.getIsAutoFinish());

        } catch (Exception e) {
            log.error("AI 处理工单异常: orderNo={}", order.getOrderNo(), e);
            order.setStatus(0);
            order.setIsAutoFinish(0);
            order.setWorkType("consult");
            order.setPriority(3);
            workOrderMapper.updateById(order);

            addFlowLog(order.getId(), order.getOrderNo(), "parse", "AI Agent",
                    "AI处理异常，已兜底转人工: " + e.getMessage());
        }
    }

    private void executeAutoFinish(WorkOrder order, AiParseResult parseResult) {
        log.info("执行自动办结流程: orderNo={}", order.getOrderNo());

        String question = order.getTitle() + " " + order.getContent();
        String answer = ragKnowledgeTool.answer(question);

        if (StrUtil.isBlank(answer)) {
            log.info("RAG 无匹配结果，降级人工处理: orderNo={}", order.getOrderNo());
            executeAssign(order, parseResult);
            return;
        }

        order.setAiAnswer(answer);
        order.setIsAutoFinish(1);
        order.setStatus(2);
        order.setFinishTime(LocalDateTime.now());
        order.setDeptName("AI Agent");
        order.setHandlerUserName("AI智能助手");

        addFlowLog(order.getId(), order.getOrderNo(), "auto_finish", "AI Agent",
                "AI自动办结，RAG知识库答疑完成");
    }

    private void executeAssign(WorkOrder order, AiParseResult parseResult) {
        log.info("执行人工分派流程: orderNo={}", order.getOrderNo());

        OrderAssignTool.AssignResult assignResult =
                orderAssignTool.assign(parseResult.getWorkType(), parseResult.getModule());

        order.setDeptName(assignResult.getDeptName());
        order.setHandlerUserId(assignResult.getHandlerUserId());
        order.setHandlerUserName(assignResult.getHandlerUserName());
        order.setStatus(0);
        order.setIsAutoFinish(0);

        addFlowLog(order.getId(), order.getOrderNo(), "assign", "AI Agent",
                String.format("工单已分派至: %s / %s",
                        assignResult.getDeptName(), assignResult.getHandlerUserName()));
    }

    private void addFlowLog(Long orderId, String orderNo, String action,
                            String operator, String content) {
        WorkOrderFlowLog log = new WorkOrderFlowLog();
        log.setOrderId(orderId);
        log.setOrderNo(orderNo);
        log.setAction(action);
        log.setOperator(operator);
        log.setContent(content);
        log.setCreateTime(LocalDateTime.now());
        flowLogMapper.insert(log);
    }
}
