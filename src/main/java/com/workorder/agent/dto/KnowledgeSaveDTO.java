package com.workorder.agent.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
public class KnowledgeSaveDTO {

    private Long id;

    @NotBlank(message = "知识点标题不能为空")
    private String title;

    @NotBlank(message = "知识点内容不能为空")
    private String content;

    private String module;

    private String keywords;

    private Integer status;
}
