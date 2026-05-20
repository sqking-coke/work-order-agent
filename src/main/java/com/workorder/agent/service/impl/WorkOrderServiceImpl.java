package com.workorder.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workorder.agent.dto.WorkOrderQueryDTO;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.mapper.WorkOrderMapper;
import com.workorder.agent.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private final WorkOrderMapper workOrderMapper;

    @Override
    public Page<WorkOrder> list(WorkOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(queryDTO.getWorkType())) {
            wrapper.eq(WorkOrder::getWorkType, queryDTO.getWorkType());
        }
        if (queryDTO.getPriority() != null) {
            wrapper.eq(WorkOrder::getPriority, queryDTO.getPriority());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(WorkOrder::getStatus, queryDTO.getStatus());
        }
        if (StrUtil.isNotBlank(queryDTO.getModule())) {
            wrapper.eq(WorkOrder::getModule, queryDTO.getModule());
        }
        if (queryDTO.getHandlerUserId() != null) {
            wrapper.eq(WorkOrder::getHandlerUserId, queryDTO.getHandlerUserId());
        }
        if (StrUtil.isNotBlank(queryDTO.getDeptName())) {
            wrapper.eq(WorkOrder::getDeptName, queryDTO.getDeptName());
        }
        if (queryDTO.getIsAutoFinish() != null) {
            wrapper.eq(WorkOrder::getIsAutoFinish, queryDTO.getIsAutoFinish());
        }
        if (StrUtil.isNotBlank(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(WorkOrder::getTitle, queryDTO.getKeyword())
                    .or().like(WorkOrder::getContent, queryDTO.getKeyword()));
        }
        if (StrUtil.isNotBlank(queryDTO.getStartTime())) {
            wrapper.ge(WorkOrder::getCreateTime, queryDTO.getStartTime());
        }
        if (StrUtil.isNotBlank(queryDTO.getEndTime())) {
            wrapper.le(WorkOrder::getCreateTime, queryDTO.getEndTime());
        }

        wrapper.orderByDesc(WorkOrder::getCreateTime);

        Page<WorkOrder> page = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());
        return workOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public WorkOrder detail(Long id) {
        return workOrderMapper.selectById(id);
    }

    @Override
    public WorkOrder getByOrderNo(String orderNo) {
        return workOrderMapper.selectOne(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getOrderNo, orderNo)
        );
    }
}
