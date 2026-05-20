package com.workorder.agent.service;

import com.workorder.agent.entity.WorkOrder;

/**
 * Agent 核心调度服务，负责工单全生命周期的智能调度：
 * 接收工单 → 触发异步 AI 处理 → 人工办结/关闭。
 */
public interface AgentCoreService {

    /**
     * 提交工单并触发 AI 智能处理流程（异步）。
     */
    WorkOrder submitAndProcess(String title, String content, String submitUserName);

    /**
     * 手动办结工单。
     */
    WorkOrder manualFinish(Long orderId, String handlerName, String finishContent);

    /**
     * 关闭工单。
     */
    WorkOrder closeOrder(Long orderId, String operator);
}
