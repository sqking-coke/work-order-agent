package com.workorder.agent.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.entity.WorkOrderFlowLog;
import com.workorder.agent.mapper.WorkOrderFlowLogMapper;
import com.workorder.agent.mapper.WorkOrderMapper;
import com.workorder.agent.service.AgentAsyncProcessor;
import com.workorder.agent.service.AgentCoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Agent 核心调度实现，负责工单全生命周期的智能调度：
 * 接入 → 创建 → 触发异步解析 → 人工办结/关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCoreServiceImpl implements AgentCoreService {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderFlowLogMapper flowLogMapper;
    private final AgentAsyncProcessor asyncProcessor;

    @Override
    @Transactional
    public WorkOrder submitAndProcess(String title, String content, String submitUserName) {
        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        order.setTitle(title);
        order.setContent(content);
        order.setSubmitUserName(StrUtil.blankToDefault(submitUserName, "匿名用户"));
        order.setStatus(0);
        order.setIsAutoFinish(0);
        order.setIsDuplicate(0);
        order.setPriority(4);
        order.setCreateTime(LocalDateTime.now());

        workOrderMapper.insert(order);

        addFlowLog(order.getId(), order.getOrderNo(), "submit",
                order.getSubmitUserName(), "用户提交工单: " + title);

        log.info("工单创建成功: orderNo={}, title={}", order.getOrderNo(), title);

        // 通过独立组件触发异步处理，确保 @Async 生效
        asyncProcessor.process(order);

        return order;
    }

    @Override
    @Transactional
    public WorkOrder manualFinish(Long orderId, String handlerName, String finishContent) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }

        order.setStatus(2);
        order.setFinishTime(LocalDateTime.now());
        order.setIsAutoFinish(0);
        if (StrUtil.isNotBlank(handlerName)) {
            order.setHandlerUserName(handlerName);
        }
        if (StrUtil.isNotBlank(finishContent)) {
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

        order.setStatus(3);
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
