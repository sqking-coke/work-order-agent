package com.workorder.agent.task;

import cn.hutool.core.date.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.*;

import java.time.*;
import java.util.*;

/**
 * 工单超时预警定时任务
 * 定期巡检未办结工单，对超时工单触发催办
 */
@Slf4j
@Component
public class WorkOrderTimeoutTask {

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderFlowLogMapper flowLogMapper;

    @Value("${agent.task.timeout-hours:4}")
    private int timeoutHours;

    /**
     * 每5分钟巡检一次超时工单
     */
    @Scheduled(cron = "${agent.task.timeout-cron:0 */5 * * * ?}")
    public void checkTimeoutOrders() {
        log.debug("开始巡检超时工单...");

        String deadline = DateUtil.formatDateTime(
                DateUtil.offsetHour(new Date(), -timeoutHours));

        List<WorkOrder> timeoutOrders = workOrderMapper.findTimeoutOrders(deadline);

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.warn("发现 {} 个超时未处理工单", timeoutOrders.size());

        for (WorkOrder order : timeoutOrders) {
            // 记录超时预警日志（避免重复记录）
            long count = flowLogMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkOrderFlowLog>()
                            .eq(WorkOrderFlowLog::getOrderId, order.getId())
                            .eq(WorkOrderFlowLog::getAction, "timeout_warn")
                            .ge(WorkOrderFlowLog::getCreateTime,
                                    DateUtil.formatDateTime(DateUtil.offsetHour(new Date(), -1)))
            );

            if (count == 0) {
                WorkOrderFlowLog flowLog = new WorkOrderFlowLog();
                flowLog.setOrderId(order.getId());
                flowLog.setOrderNo(order.getOrderNo());
                flowLog.setAction("timeout_warn");
                flowLog.setOperator("SYSTEM");
                flowLog.setContent(String.format("工单超时预警：已超过%d小时未处理，优先级=%d，当前处理人=%s",
                        timeoutHours, order.getPriority(),
                        order.getHandlerUserName() != null ? order.getHandlerUserName() : "未分配"));
                flowLog.setCreateTime(LocalDateTime.now());
                flowLogMapper.insert(flowLog);
            }
        }
    }
}
