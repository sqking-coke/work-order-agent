package com.workorder.agent.service.impl;

import cn.hutool.core.date.*;
import cn.hutool.core.util.*;
import com.alibaba.fastjson2.*;
import com.workorder.agent.dto.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import com.workorder.agent.service.*;
import com.workorder.agent.tool.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/**
 * Agent 核心调度实现
 *
 * 完整闭环：接入 → 解析 → 去重 → 决策 → 执行（自动办结/分派） → 记录
 */
@Slf4j
@Service
public class AgentCoreServiceImpl implements AgentCoreService {

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderFlowLogMapper flowLogMapper;

    @Autowired
    private IntelligentParserTool parserTool;

    @Autowired
    private DuplicateCheckTool duplicateCheckTool;

    @Autowired
    private OrderAssignTool orderAssignTool;

    @Autowired
    private RagKnowledgeTool ragKnowledgeTool;

    @Override
    @Transactional
    public WorkOrder submitAndProcess(String title, String content, String submitUserName) {
        // 1. 创建工单
        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        order.setTitle(title);
        order.setContent(content);
        order.setSubmitUserName(StrUtil.blankToDefault(submitUserName, "匿名用户"));
        order.setStatus(0); // 待处理
        order.setIsAutoFinish(0);
        order.setIsDuplicate(0);
        order.setPriority(4); // 默认低优先级，AI 后续更新
        order.setCreateTime(LocalDateTime.now());

        workOrderMapper.insert(order);

        // 2. 记录流转日志
        addFlowLog(order.getId(), order.getOrderNo(), "submit",
                order.getSubmitUserName(), "用户提交工单: " + title);

        log.info("工单创建成功: orderNo={}, title={}", order.getOrderNo(), title);

        // 3. 异步触发 AI 解析处理
        asyncParseAndProcess(order);

        return order;
    }

    @Async("agentTaskExecutor")
    @Override
    @Transactional
    public void asyncParseAndProcess(WorkOrder order) {
        log.info("开始异步处理工单: orderNo={}", order.getOrderNo());

        try {
            // ==================== 第1步：AI 智能解析 ====================
            AiParseResult parseResult = parserTool.parse(order.getTitle(), order.getContent());
            if (parseResult == null) {
                log.warn("AI 解析返回空，保留默认状态: orderNo={}", order.getOrderNo());
                return;
            }

            // 更新工单解析结果
            order.setWorkType(parseResult.getWorkType());
            order.setPriority(parseResult.getPriority() != null ? parseResult.getPriority() : 3);
            order.setModule(parseResult.getModule());
            order.setAiParseResult(JSON.toJSONString(parseResult));

            addFlowLog(order.getId(), order.getOrderNo(), "parse", "AI Agent",
                    String.format("AI解析完成: 类型=%s, 优先级=%d, 复杂度=%s, 可自动办结=%s",
                            parseResult.getWorkType(), parseResult.getPriority(),
                            parseResult.getComplexity(), parseResult.getCanAutoFinish()));

            // ==================== 第2步：重复工单检测 ====================
            DuplicateCheckTool.DuplicateResult dupResult =
                    duplicateCheckTool.check(order.getTitle(), order.getContent());
            if (dupResult.isDuplicate() && dupResult.getSimilarOrder() != null) {
                order.setIsDuplicate(1);
                order.setDuplicateOrderId(dupResult.getSimilarOrder().getId());
                addFlowLog(order.getId(), order.getOrderNo(), "parse", "AI Agent",
                        "检测到重复工单: orderId=" + dupResult.getSimilarOrder().getId()
                                + ", 相似度=" + String.format("%.2f", dupResult.getSimilarity()));
            }

            // ==================== 第3步：策略决策 + 工具执行 ====================
            if (Boolean.TRUE.equals(parseResult.getCanAutoFinish())) {
                // 咨询类 + 简单问题 → RAG 自动答疑 → 自动办结
                executeAutoFinish(order, parseResult);
            } else {
                // 故障/运维/申诉/建议 → 自动分派 → 进入人工流转
                executeAssign(order, parseResult);
            }

            // 持久化
            workOrderMapper.updateById(order);

            log.info("工单处理完成: orderNo={}, status={}, isAutoFinish={}",
                    order.getOrderNo(), order.getStatus(), order.getIsAutoFinish());

        } catch (Exception e) {
            log.error("AI 处理工单异常: orderNo={}", order.getOrderNo(), e);
            // 兜底：标记为人工处理
            order.setStatus(0);
            order.setIsAutoFinish(0);
            order.setWorkType("consult");
            order.setPriority(3);
            workOrderMapper.updateById(order);

            addFlowLog(order.getId(), order.getOrderNo(), "parse", "AI Agent",
                    "AI处理异常，已兜底转人工: " + e.getMessage());
        }
    }

    /**
     * 自动办结：RAG 答疑 → 生成答复 → 更新工单
     */
    private void executeAutoFinish(WorkOrder order, AiParseResult parseResult) {
        log.info("执行自动办结流程: orderNo={}", order.getOrderNo());

        // RAG 检索 + LLM 生成答复
        String question = order.getTitle() + " " + order.getContent();
        String answer = ragKnowledgeTool.answer(question);

        if (StrUtil.isBlank(answer)) {
            // RAG 无匹配 → 降级为人工处理
            log.info("RAG 无匹配结果，降级人工处理: orderNo={}", order.getOrderNo());
            executeAssign(order, parseResult);
            return;
        }

        order.setAiAnswer(answer);
        order.setIsAutoFinish(1);
        order.setStatus(2); // 已办结
        order.setFinishTime(LocalDateTime.now());
        order.setDeptName("AI Agent");
        order.setHandlerUserName("AI智能助手");

        addFlowLog(order.getId(), order.getOrderNo(), "auto_finish", "AI Agent",
                "AI自动办结，RAG知识库答疑完成");
    }

    /**
     * 人工流转：匹配部门 → 分配处理人 → 进入待处理
     */
    private void executeAssign(WorkOrder order, AiParseResult parseResult) {
        log.info("执行人工分派流程: orderNo={}", order.getOrderNo());

        OrderAssignTool.AssignResult assignResult =
                orderAssignTool.assign(parseResult.getWorkType(), parseResult.getModule());

        order.setDeptName(assignResult.getDeptName());
        order.setHandlerUserId(assignResult.getHandlerUserId());
        order.setHandlerUserName(assignResult.getHandlerUserName());
        order.setStatus(0); // 待处理（等待人工介入）
        order.setIsAutoFinish(0);

        addFlowLog(order.getId(), order.getOrderNo(), "assign", "AI Agent",
                String.format("工单已分派至: %s / %s",
                        assignResult.getDeptName(), assignResult.getHandlerUserName()));
    }

    @Override
    @Transactional
    public WorkOrder manualFinish(Long orderId, String handlerName, String finishContent) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }

        order.setStatus(2); // 已办结
        order.setFinishTime(LocalDateTime.now());
        order.setIsAutoFinish(0);
        if (StrUtil.isNotBlank(handlerName)) {
            order.setHandlerUserName(handlerName);
        }
        if (StrUtil.isNotBlank(finishContent)) {
            // 追加到 AI 答复后面
            String existing = order.getAiAnswer() != null ? order.getAiAnswer() : "";
            order.setAiAnswer(existing + "\n\n【人工处理备注】" + finishContent);
        }
        workOrderMapper.updateById(order);

        addFlowLog(order.getId(), order.getOrderNo(), "manual_finish",
                handlerName, "人工办结工单: " + finishContent);

        log.info("工单人工办结: orderNo={}, handler={}", order.getOrderNo(), handlerName);
        return order;
    }

    @Override
    @Transactional
    public WorkOrder closeOrder(Long orderId, String operator) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }

        order.setStatus(3); // 已关闭
        workOrderMapper.updateById(order);

        addFlowLog(order.getId(), order.getOrderNo(), "close",
                operator, "关闭工单");

        log.info("工单已关闭: orderNo={}", order.getOrderNo());
        return order;
    }

    // ==================== 辅助方法 ====================

    private String generateOrderNo() {
        String date = DateUtil.format(new Date(), "yyyyMMdd");
        String uuid = IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
        return "WO" + date + uuid;
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
