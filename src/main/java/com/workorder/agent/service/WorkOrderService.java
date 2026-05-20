package com.workorder.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.workorder.agent.dto.*;
import com.workorder.agent.entity.*;

public interface WorkOrderService {

    /**
     * 分页查询工单列表
     */
    Page<WorkOrder> list(WorkOrderQueryDTO queryDTO);

    /**
     * 获取工单详情
     */
    WorkOrder detail(Long id);

    /**
     * 根据工单编号查询
     */
    WorkOrder getByOrderNo(String orderNo);
}
