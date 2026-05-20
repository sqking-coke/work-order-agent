package com.workorder.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

/**
 * 工单智能分派工具
 * 根据工单类型和业务模块自动匹配责任部门与处理人
 */
@Slf4j
@Component
public class OrderAssignTool {

    @Autowired
    private WorkDeptConfigMapper deptConfigMapper;

    /**
     * 根据工单类型和模块匹配责任部门与处理人
     *
     * @param workType 工单类型
     * @param module   业务模块
     * @return [部门名称, 处理人ID, 处理人姓名]，未匹配到返回默认
     */
    public AssignResult assign(String workType, String module) {
        // 优先精确匹配：模块 + 类型
        WorkDeptConfig config = findBestMatch(workType, module);

        if (config == null) {
            // 仅按模块匹配
            config = findBestMatch(null, module);
        }
        if (config == null) {
            // 仅按类型匹配
            config = findBestMatch(workType, null);
        }
        if (config == null) {
            // 获取默认部门
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

        // 最终兜底
        log.warn("未找到匹配的部门配置，使用默认值");
        return new AssignResult("客服部", 1002L, "李四");
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

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AssignResult {
        private String deptName;
        private Long handlerUserId;
        private String handlerUserName;
    }
}
