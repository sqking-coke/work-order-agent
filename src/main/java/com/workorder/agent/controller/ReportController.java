package com.workorder.agent.controller;

import com.workorder.agent.dto.ApiResponse;
import com.workorder.agent.entity.WorkOrderReport;
import com.workorder.agent.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 复盘报告控制器，提供报告查询和手动生成接口。
 */
@RestController
@RequestMapping("/agent/work/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 获取周期复盘报告。
     */
    @GetMapping("/get")
    public ApiResponse<WorkOrderReport> get(@RequestParam(defaultValue = "day") String reportType,
                                             @RequestParam(required = false) String reportPeriod) {
        WorkOrderReport report = reportService.getLatest(reportType, reportPeriod);
        if (report == null) {
            return ApiResponse.fail("未找到对应报告");
        }
        return ApiResponse.ok(report);
    }

    /**
     * 手动生成复盘报告。
     */
    @PostMapping("/generate")
    public ApiResponse<WorkOrderReport> generate(@RequestParam(defaultValue = "day") String reportType,
                                                  @RequestParam(required = false) String reportPeriod) {
        if (reportPeriod == null) {
            reportPeriod = LocalDate.now().toString();
        }
        WorkOrderReport report = reportService.generateReport(reportType, reportPeriod);
        return ApiResponse.ok(report);
    }

    /**
     * 所有报告列表。
     */
    @GetMapping("/list")
    public ApiResponse<List<WorkOrderReport>> list() {
        return ApiResponse.ok(reportService.listAll());
    }
}
