package com.workorder.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.agent.entity.WorkDeptConfig;
import com.workorder.agent.mapper.WorkDeptConfigMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 工单智能分派工具，根据工单类型和业务模块自动匹配责任部门与处理人。
 */
@Slf4j
@Component
public class OrderAssignTool {

    private final WorkDeptConfigMapper deptConfigMapper;
    private final String fallbackDeptName;
    private final Long fallbackHandlerUserId;
    private final String fallbackHandlerUserName;

    public OrderAssignTool(WorkDeptConfigMapper deptConfigMapper,
                           @Value("${agent.fallback-assign.dept-name:客服部}") String fallbackDeptName,
                           @Value("${agent.fallback-assign.handler-user-id:1002}") Long fallbackHandlerUserId,
                           @Value("${agent.fallback-assign.handler-user-name:李四}") String fallbackHandlerUserName) {
        this.deptConfigMapper = deptConfigMapper;
        this.fallbackDeptName = fallbackDeptName;
        this.fallbackHandlerUserId = fallbackHandlerUserId;
        this.fallbackHandlerUserName = fallbackHandlerUserName;
    }

    /**
     * 根据工单类型和模块匹配责任部门与处理人。
     *
     * @param workType 工单类型
     * @param module   业务模块
     * @return 分派结果，未匹配到返回兜底配置
     */
    public AssignResult assign(String workType, String module) {
        WorkDeptConfig config = findBestMatch(workType, module);

        if (config == null) {
            config = findBestMatch(null, module);
        }
        if (config == null) {
            config = findBestMatch(workType, null);
        }
        if (config == null) {
            config = findBestMatch(null, null);
        }

        if (config != null) {
            log.info("工单分派: type={}, module={} → dept={}, handler={}",
                    workType, module, config.getDeptName(), config.getHandlerUserName());
            return new AssignResult(
                    config.getDeptName(),
                    config.getHandlerUserId(),
                    config.getHandlerUserName()
            );
        }

        log.warn("未找到匹配的部门配置，使用兜底分派");
        return new AssignResult(fallbackDeptName, fallbackHandlerUserId, fallbackHandlerUserName);
    }

    private WorkDeptConfig findBestMatch(String workType, String module) {
        LambdaQueryWrapper<WorkDeptConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkDeptConfig::getStatus, 1);
        if (workType != null) {
            wrapper.eq(WorkDeptConfig::getModule, workType);
        }
        if (module != null) {
            wrapper.eq(WorkDeptConfig::getModule, module);
        }
        if (workType == null && module == null) {
            wrapper.isNull(WorkDeptConfig::getModule);
        }
        wrapper.orderByAsc(WorkDeptConfig::getPriority);
        wrapper.last("LIMIT 1");
        return deptConfigMapper.selectOne(wrapper);
    }

    @Data
    @AllArgsConstructor
    public static class AssignResult {
        private String deptName;
        private Long handlerUserId;
        private String handlerUserName;
    }
}
