package com.workorder.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.*;
import com.workorder.agent.dto.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.service.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.beans.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/agent/work/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 知识点新增/编辑
     * POST /agent/work/knowledge/save
     */
    @PostMapping("/save")
    public ApiResponse<WorkKnowledge> save(@Valid @RequestBody KnowledgeSaveDTO dto) {
        WorkKnowledge knowledge = new WorkKnowledge();
        BeanUtils.copyProperties(dto, knowledge);
        if (dto.getId() == null) {
            knowledge.setCreateTime(LocalDateTime.now());
        }
        knowledgeService.save(knowledge);
        return ApiResponse.ok(knowledge);
    }

    /**
     * 知识点列表
     * GET /agent/work/knowledge/list
     */
    @GetMapping("/list")
    public ApiResponse<Page<WorkKnowledge>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module) {
        Page<WorkKnowledge> result = knowledgeService.list(page, pageSize, keyword, module);
        return ApiResponse.ok(result);
    }

    /**
     * 知识点详情
     * GET /agent/work/knowledge/detail/{id}
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<WorkKnowledge> detail(@PathVariable Long id) {
        WorkKnowledge knowledge = knowledgeService.getById(id);
        if (knowledge == null) {
            return ApiResponse.fail("知识点不存在");
        }
        return ApiResponse.ok(knowledge);
    }

    /**
     * 删除知识点
     * DELETE /agent/work/knowledge/delete/{id}
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 刷新知识库索引
     * POST /agent/work/knowledge/refresh
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        knowledgeService.refreshIndex();
        return ApiResponse.ok();
    }
}
