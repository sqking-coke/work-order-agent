package com.workorder.agent.service.impl;

import cn.hutool.core.util.*;
import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import com.workorder.agent.service.*;
import com.workorder.agent.tool.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private WorkKnowledgeMapper knowledgeMapper;

    @Autowired
    private RagKnowledgeTool ragKnowledgeTool;

    @Override
    public WorkKnowledge save(WorkKnowledge knowledge) {
        if (knowledge.getId() != null) {
            knowledgeMapper.updateById(knowledge);
        } else {
            knowledge.setStatus(knowledge.getStatus() != null ? knowledge.getStatus() : 1);
            knowledgeMapper.insert(knowledge);
        }
        // 更新后刷新索引
        ragKnowledgeTool.refreshIndex();
        return knowledge;
    }

    @Override
    public Page<WorkKnowledge> list(Integer page, Integer pageSize, String keyword, String module) {
        LambdaQueryWrapper<WorkKnowledge> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(WorkKnowledge::getTitle, keyword)
                    .or().like(WorkKnowledge::getContent, keyword)
                    .or().like(WorkKnowledge::getKeywords, keyword));
        }
        if (StrUtil.isNotBlank(module)) {
            wrapper.eq(WorkKnowledge::getModule, module);
        }
        wrapper.orderByDesc(WorkKnowledge::getCreateTime);
        Page<WorkKnowledge> p = new Page<>(page, pageSize);
        return knowledgeMapper.selectPage(p, wrapper);
    }

    @Override
    public void delete(Long id) {
        knowledgeMapper.deleteById(id);
        ragKnowledgeTool.refreshIndex();
    }

    @Override
    public WorkKnowledge getById(Long id) {
        return knowledgeMapper.selectById(id);
    }

    @Override
    public void refreshIndex() {
        ragKnowledgeTool.refreshIndex();
    }
}
