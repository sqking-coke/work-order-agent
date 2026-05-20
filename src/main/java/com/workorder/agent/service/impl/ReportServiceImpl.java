package com.workorder.agent.service.impl;

import cn.hutool.core.date.*;
import cn.hutool.core.util.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import com.workorder.agent.service.*;
import com.workorder.agent.tool.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.time.*;
import java.util.*;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private WorkOrderReportMapper reportMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private ReviewReportTool reviewReportTool;

    @Override
    public WorkOrderReport generateReport(String reportType, String reportPeriod) {
        // 检查是否已有相同周期报告
        WorkOrderReport existing = reportMapper.findByPeriod(reportType, reportPeriod);
        if (existing != null) {
            return existing; // 已存在则直接返回
        }

        // 计算时间范围
        LocalDateTime start = calculateStart(reportType, reportPeriod);
        LocalDateTime end = calculateEnd(reportType, reportPeriod);

        // AI 生成复盘报告
        String reportContent = reviewReportTool.generateReport(reportType, reportPeriod, start, end);

        // 统计基础数据
        LocalDateTime startTime = start;
        LocalDateTime endTime = end;
        List<WorkOrder> allOrders = workOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkOrder>()
                        .ge(WorkOrder::getCreateTime, startTime)
                        .le(WorkOrder::getCreateTime, endTime)
        );

        int total = allOrders.size();
        int finishCount = (int) allOrders.stream().filter(o -> o.getStatus() == 2).count();
        int aiAutoCount = (int) allOrders.stream().filter(o -> o.getIsAutoFinish() == 1).count();
        int duplicateCount = (int) allOrders.stream().filter(o -> o.getIsDuplicate() == 1).count();

        // 保存报告
        WorkOrderReport report = new WorkOrderReport();
        report.setReportNo("RPT" + DateUtil.format(new Date(), "yyyyMMdd") + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        report.setReportType(reportType);
        report.setReportPeriod(reportPeriod);
        report.setTotalCount(total);
        report.setFinishCount(finishCount);
        report.setAiAutoCount(aiAutoCount);
        report.setTimeoutCount(0);
        report.setDuplicateCount(duplicateCount);
        report.setReportContent(reportContent);
        report.setCreateTime(LocalDateTime.now());

        reportMapper.insert(report);
        return report;
    }

    @Override
    public WorkOrderReport getLatest(String reportType, String reportPeriod) {
        if (StrUtil.isNotBlank(reportPeriod)) {
            return reportMapper.findByPeriod(reportType, reportPeriod);
        }
        // 返回最新一条
        List<WorkOrderReport> list = reportMapper.listAll();
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<WorkOrderReport> listAll() {
        return reportMapper.listAll();
    }

    @Override
    public String reviewSingleOrder(Long orderId, String extraPrompt) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("工单不存在");
        }
        return reviewReportTool.reviewSingleOrder(order, extraPrompt);
    }

    private LocalDateTime calculateStart(String reportType, String reportPeriod) {
        // reportPeriod 格式：
        // day: "2026-05-20"  week: "2026-W21"  month: "2026-05"
        if ("day".equals(reportType)) {
            return LocalDateTime.parse(reportPeriod + "T00:00:00");
        } else if ("week".equals(reportType)) {
            // 解析年份和周数
            String[] parts = reportPeriod.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            // 简单估算该周第一天
            java.time.LocalDate jan1 = java.time.LocalDate.of(year, 1, 1);
            java.time.LocalDate startOfWeek = jan1.plusWeeks(week - 1).with(java.time.DayOfWeek.MONDAY);
            return startOfWeek.atStartOfDay();
        } else if ("month".equals(reportType)) {
            return LocalDateTime.parse(reportPeriod + "-01T00:00:00");
        }
        return LocalDateTime.now().minusDays(1);
    }

    private LocalDateTime calculateEnd(String reportType, String reportPeriod) {
        if ("day".equals(reportType)) {
            return LocalDateTime.parse(reportPeriod + "T23:59:59");
        } else if ("week".equals(reportType)) {
            String[] parts = reportPeriod.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            java.time.LocalDate jan1 = java.time.LocalDate.of(year, 1, 1);
            java.time.LocalDate startOfWeek = jan1.plusWeeks(week - 1).with(java.time.DayOfWeek.MONDAY);
            return startOfWeek.plusDays(6).atTime(23, 59, 59);
        } else if ("month".equals(reportType)) {
            LocalDateTime start = LocalDateTime.parse(reportPeriod + "-01T00:00:00");
            return start.plusMonths(1).minusSeconds(1);
        }
        return LocalDateTime.now();
    }
}
