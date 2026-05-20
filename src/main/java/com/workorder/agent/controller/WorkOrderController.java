package com.workorder.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.workorder.agent.dto.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.service.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/agent/work/order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final AgentCoreService agentCoreService;
    private final WorkOrderService workOrderService;
    private final ReportService reportService;

    /**
     * 提交工单（AI 自动解析）
     * POST /agent/work/order/submit
     */
    @PostMapping("/submit")
    public ApiResponse<WorkOrder> submit(@Valid @RequestBody WorkOrderSubmitDTO dto) {
        WorkOrder order = agentCoreService.submitAndProcess(
                dto.getTitle(), dto.getContent(), dto.getSubmitUserName());
        return ApiResponse.ok(order);
    }

    /**
     * 工单列表查询
     * GET /agent/work/order/list
     */
    @GetMapping("/list")
    public ApiResponse<Page<WorkOrder>> list(WorkOrderQueryDTO queryDTO) {
        Page<WorkOrder> page = workOrderService.list(queryDTO);
        return ApiResponse.ok(page);
    }

    /**
     * 工单详情 + AI 分析结果
     * GET /agent/work/order/detail/{id}
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<WorkOrder> detail(@PathVariable Long id) {
        WorkOrder order = workOrderService.detail(id);
        if (order == null) {
            return ApiResponse.fail("工单不存在");
        }
        return ApiResponse.ok(order);
    }

    /**
     * 工单手动办结
     * POST /agent/work/order/finish
     */
    @PostMapping("/finish")
    public ApiResponse<WorkOrder> finish(@RequestBody Map<String, Object> params) {
        Long orderId = Long.valueOf(params.get("orderId").toString());
        String handlerName = (String) params.getOrDefault("handlerName", "管理员");
        String finishContent = (String) params.getOrDefault("finishContent", "");
        WorkOrder order = agentCoreService.manualFinish(orderId, handlerName, finishContent);
        return ApiResponse.ok(order);
    }

    /**
     * 关闭工单
     * POST /agent/work/order/close/{id}
     */
    @PostMapping("/close/{id}")
    public ApiResponse<WorkOrder> close(@PathVariable Long id,
                                         @RequestParam(defaultValue = "管理员") String operator) {
        WorkOrder order = agentCoreService.closeOrder(id, operator);
        return ApiResponse.ok(order);
    }

    /**
     * AI 工单调优复盘
     * POST /agent/work/order/review
     */
    @PostMapping("/review")
    public ApiResponse<String> review(@RequestBody OrderReviewDTO dto) {
        String review = reportService.reviewSingleOrder(dto.getOrderId(), dto.getExtraPrompt());
        return ApiResponse.ok(review);
    }
}
