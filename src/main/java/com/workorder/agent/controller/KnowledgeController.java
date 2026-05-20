package com.workorder.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workorder.agent.dto.ApiResponse;
import com.workorder.agent.dto.KnowledgeSaveDTO;
import com.workorder.agent.entity.WorkKnowledge;
import com.workorder.agent.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 知识库管理控制器，提供知识点的增删改查和索引刷新接口。
 */
@RestController
@RequestMapping("/agent/work/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 知识点新增/编辑。
     */
    @PostMapping("/save")
    public ApiResponse<WorkKnowledge> save(@Valid @RequestBody KnowledgeSaveDTO dto) {
        WorkKnowledge knowledge = new WorkKnowledge();
        knowledge.setId(dto.getId());
        knowledge.setTitle(dto.getTitle());
        knowledge.setContent(dto.getContent());
        knowledge.setModule(dto.getModule());
        knowledge.setKeywords(dto.getKeywords());
        knowledge.setStatus(dto.getStatus());
        if (dto.getId() == null) {
            knowledge.setCreateTime(LocalDateTime.now());
        }
        knowledgeService.save(knowledge);
        return ApiResponse.ok(knowledge);
    }

    /**
     * 知识点列表。
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
     * 知识点详情。
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
     * 删除知识点。
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 刷新知识库索引。
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        knowledgeService.refreshIndex();
        return ApiResponse.ok();
    }
}
