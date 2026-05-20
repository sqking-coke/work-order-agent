package com.workorder.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workorder.agent.dto.ApiResponse;
import com.workorder.agent.dto.OrderFinishDTO;
import com.workorder.agent.dto.OrderReviewDTO;
import com.workorder.agent.dto.WorkOrderQueryDTO;
import com.workorder.agent.dto.WorkOrderSubmitDTO;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.service.AgentCoreService;
import com.workorder.agent.service.ReportService;
import com.workorder.agent.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单管理控制器，提供工单提交、查询、办结、关闭、复盘等 REST 接口。
 */
@RestController
@RequestMapping("/agent/work/order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final AgentCoreService agentCoreService;
    private final WorkOrderService workOrderService;
    private final ReportService reportService;

    /**
     * 提交工单（AI 自动解析）。
     */
    @PostMapping("/submit")
    public ApiResponse<WorkOrder> submit(@Valid @RequestBody WorkOrderSubmitDTO dto) {
        WorkOrder order = agentCoreService.submitAndProcess(
                dto.getTitle(), dto.getContent(), dto.getSubmitUserName());
        return ApiResponse.ok(order);
    }

    /**
     * 工单列表查询。
     */
    @GetMapping("/list")
    public ApiResponse<Page<WorkOrder>> list(WorkOrderQueryDTO queryDTO) {
        Page<WorkOrder> page = workOrderService.list(queryDTO);
        return ApiResponse.ok(page);
    }

    /**
     * 工单详情 + AI 分析结果。
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
     * 工单手动办结。
     */
    @PostMapping("/finish")
    public ApiResponse<WorkOrder> finish(@Valid @RequestBody OrderFinishDTO dto) {
        String handlerName = dto.getHandlerName() != null ? dto.getHandlerName() : "管理员";
        String finishContent = dto.getFinishContent() != null ? dto.getFinishContent() : "";
        WorkOrder order = agentCoreService.manualFinish(dto.getOrderId(), handlerName, finishContent);
        return ApiResponse.ok(order);
    }

    /**
     * 关闭工单。
     */
    @PostMapping("/close/{id}")
    public ApiResponse<WorkOrder> close(@PathVariable Long id,
                                         @RequestParam(defaultValue = "管理员") String operator) {
        WorkOrder order = agentCoreService.closeOrder(id, operator);
        return ApiResponse.ok(order);
    }

    /**
     * AI 工单调优复盘。
     */
    @PostMapping("/review")
    public ApiResponse<String> review(@RequestBody OrderReviewDTO dto) {
        String review = reportService.reviewSingleOrder(dto.getOrderId(), dto.getExtraPrompt());
        return ApiResponse.ok(review);
    }
}
