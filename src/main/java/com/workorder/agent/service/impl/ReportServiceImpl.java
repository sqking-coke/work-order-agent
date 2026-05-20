package com.workorder.agent.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.entity.WorkOrderReport;
import com.workorder.agent.mapper.WorkOrderMapper;
import com.workorder.agent.mapper.WorkOrderReportMapper;
import com.workorder.agent.service.ReportService;
import com.workorder.agent.tool.ReviewReportTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final WorkOrderReportMapper reportMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ReviewReportTool reviewReportTool;

    @Override
    public WorkOrderReport generateReport(String reportType, String reportPeriod) {
        WorkOrderReport existing = reportMapper.findByPeriod(reportType, reportPeriod);
        if (existing != null) {
            return existing;
        }

        LocalDateTime start = calculateStart(reportType, reportPeriod);
        LocalDateTime end = calculateEnd(reportType, reportPeriod);

        String reportContent = reviewReportTool.generateReport(reportType, reportPeriod, start, end);

        List<WorkOrder> allOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .ge(WorkOrder::getCreateTime, start)
                        .le(WorkOrder::getCreateTime, end)
        );

        int total = allOrders.size();
        int finishCount = (int) allOrders.stream().filter(o -> o.getStatus() == 2).count();
        int aiAutoCount = (int) allOrders.stream().filter(o -> o.getIsAutoFinish() == 1).count();
        int duplicateCount = (int) allOrders.stream().filter(o -> o.getIsDuplicate() == 1).count();

        WorkOrderReport report = new WorkOrderReport();
        report.setReportNo("RPT" + DateUtil.format(new Date(), "yyyyMMdd")
                + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
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
        return switch (reportType) {
            case "day" -> LocalDateTime.parse(reportPeriod + "T00:00:00");
            case "week" -> parseWeekStart(reportPeriod).atStartOfDay();
            case "month" -> LocalDateTime.parse(reportPeriod + "-01T00:00:00");
            default -> LocalDateTime.now().minusDays(1);
        };
    }

    private LocalDateTime calculateEnd(String reportType, String reportPeriod) {
        return switch (reportType) {
            case "day" -> LocalDateTime.parse(reportPeriod + "T23:59:59");
            case "week" -> parseWeekStart(reportPeriod).plusDays(6).atTime(23, 59, 59);
            case "month" -> {
                LocalDateTime start = LocalDateTime.parse(reportPeriod + "-01T00:00:00");
                yield start.plusMonths(1).minusSeconds(1);
            }
            default -> LocalDateTime.now();
        };
    }

    private LocalDate parseWeekStart(String reportPeriod) {
        String[] parts = reportPeriod.split("-W");
        int year = Integer.parseInt(parts[0]);
        int week = Integer.parseInt(parts[1]);
        return LocalDate.of(year, 1, 1).plusWeeks(week - 1).with(DayOfWeek.MONDAY);
    }
}
