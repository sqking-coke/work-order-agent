package com.workorder.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workorder.agent.entity.WorkKnowledge;
import com.workorder.agent.mapper.WorkKnowledgeMapper;
import com.workorder.agent.service.KnowledgeService;
import com.workorder.agent.tool.RagKnowledgeTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final WorkKnowledgeMapper knowledgeMapper;
    private final RagKnowledgeTool ragKnowledgeTool;

    @Override
    public WorkKnowledge save(WorkKnowledge knowledge) {
        if (knowledge.getId() != null) {
            knowledgeMapper.updateById(knowledge);
        } else {
            knowledge.setStatus(knowledge.getStatus() != null ? knowledge.getStatus() : 1);
            knowledgeMapper.insert(knowledge);
        }
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
