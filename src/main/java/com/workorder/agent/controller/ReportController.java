package com.workorder.agent.controller;

import com.workorder.agent.dto.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.service.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/agent/work/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 获取周期复盘报告
     * GET /agent/work/report/get
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
     * 手动生成复盘报告
     * POST /agent/work/report/generate
     */
    @PostMapping("/generate")
    public ApiResponse<WorkOrderReport> generate(@RequestParam(defaultValue = "day") String reportType,
                                                  @RequestParam(required = false) String reportPeriod) {
        if (reportPeriod == null) {
            // 默认今天
            reportPeriod = java.time.LocalDate.now().toString();
        }
        WorkOrderReport report = reportService.generateReport(reportType, reportPeriod);
        return ApiResponse.ok(report);
    }

    /**
     * 所有报告列表
     * GET /agent/work/report/list
     */
    @GetMapping("/list")
    public ApiResponse<List<WorkOrderReport>> list() {
        return ApiResponse.ok(reportService.listAll());
    }
}
