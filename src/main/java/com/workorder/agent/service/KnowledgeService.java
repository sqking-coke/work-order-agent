package com.workorder.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.workorder.agent.entity.*;

public interface KnowledgeService {

    /**
     * 保存知识点（新增/编辑）
     */
    WorkKnowledge save(WorkKnowledge knowledge);

    /**
     * 分页查询知识点
     */
    Page<WorkKnowledge> list(Integer page, Integer pageSize, String keyword, String module);

    /**
     * 删除知识点
     */
    void delete(Long id);

    /**
     * 获取知识点详情
     */
    WorkKnowledge getById(Long id);

    /**
     * 刷新知识库索引
     */
    void refreshIndex();
}
