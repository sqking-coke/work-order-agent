package com.workorder.agent.service;

import com.workorder.agent.entity.*;

import java.util.*;

public interface ReportService {

    /**
     * 生成指定周期的复盘报告
     */
    WorkOrderReport generateReport(String reportType, String reportPeriod);

    /**
     * 获取最新报告
     */
    WorkOrderReport getLatest(String reportType, String reportPeriod);

    /**
     * 所有报告列表
     */
    List<WorkOrderReport> listAll();

    /**
     * 单工单复盘
     */
    String reviewSingleOrder(Long orderId, String extraPrompt);
}
