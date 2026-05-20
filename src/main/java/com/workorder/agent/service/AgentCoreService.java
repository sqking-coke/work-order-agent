package com.workorder.agent.service;

import com.workorder.agent.entity.*;

/**
 * Agent 核心调度服务
 * 工单全生命周期智能调度：接收工单 → AI解析 → 决策 → 执行 → 持久化
 */
public interface AgentCoreService {

    /**
     * 提交工单并触发 AI 智能处理流程
     */
    WorkOrder submitAndProcess(String title, String content, String submitUserName);

    /**
     * AI 智能解析工单（异步）
     */
    void asyncParseAndProcess(WorkOrder order);

    /**
     * 手动处理办结工单
     */
    WorkOrder manualFinish(Long orderId, String handlerName, String finishContent);

    /**
     * 关闭工单
     */
    WorkOrder closeOrder(Long orderId, String operator);
}
