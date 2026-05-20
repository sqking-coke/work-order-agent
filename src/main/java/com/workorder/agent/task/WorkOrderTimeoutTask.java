package com.workorder.agent.task;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.entity.WorkOrderFlowLog;
import com.workorder.agent.mapper.WorkOrderFlowLogMapper;
import com.workorder.agent.mapper.WorkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 工单超时预警定时任务，定期巡检未办结工单，对超时工单触发催办。
 */
@Slf4j
@Component
public class WorkOrderTimeoutTask {

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderFlowLogMapper flowLogMapper;
    private final int timeoutHours;

    public WorkOrderTimeoutTask(WorkOrderMapper workOrderMapper,
                                WorkOrderFlowLogMapper flowLogMapper,
                                @Value("${agent.task.timeout-hours:4}") int timeoutHours) {
        this.workOrderMapper = workOrderMapper;
        this.flowLogMapper = flowLogMapper;
        this.timeoutHours = timeoutHours;
    }

    /**
     * 每 5 分钟巡检一次超时工单。
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
            long count = flowLogMapper.selectCount(
                    new LambdaQueryWrapper<WorkOrderFlowLog>()
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
