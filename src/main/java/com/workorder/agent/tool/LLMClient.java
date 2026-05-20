package com.workorder.agent.tool;

import cn.hutool.core.util.*;
import cn.hutool.http.*;
import com.alibaba.fastjson2.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

/**
 * 大模型统一调用客户端
 * 兼容 OpenAI 格式接口（DeepSeek / 通义千问 / Qwen / 星火 等）
 */
@Slf4j
@Component
public class LLMClient {

    @Value("${llm.api-url}")
    private String apiUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout:60000}")
    private int timeout;

    @Value("${llm.max-tokens:2048}")
    private int maxTokens;

    /**
     * 调用大模型（系统提示词 + 用户输入），返回纯文本结果
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, false);
    }

    /**
     * 调用大模型，可指定是否强制返回 JSON
     */
    public String chat(String systemPrompt, String userMessage, boolean jsonMode) {
        JSONArray messages = new JSONArray();

        if (StrUtil.isNotBlank(systemPrompt)) {
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.3);

        if (jsonMode) {
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", "json_object");
            body.put("response_format", responseFormat);
        }

        try {
            log.debug("LLM 请求: {}", body.toJSONString());
            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toJSONString())
                    .timeout(timeout)
                    .execute();

            if (response.getStatus() != 200) {
                log.error("LLM 调用失败 HTTP {}: {}", response.getStatus(), response.body());
                throw new RuntimeException("大模型调用失败: " + response.body());
            }

            JSONObject respJson = JSON.parseObject(response.body());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("大模型返回为空");
            }

            String content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            log.debug("LLM 返回: {}", content);
            return content;

        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            throw new RuntimeException("大模型调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * 调用大模型并直接解析为 JSONObject
     */
    public JSONObject chatForJson(String systemPrompt, String userMessage) {
        String raw = chat(systemPrompt, userMessage, true);
        // 提取 JSON（处理可能的 markdown 包裹）
        String json = extractJson(raw);
        return JSON.parseObject(json);
    }

    /**
     * 调用大模型并直接解析为指定 Java 类型
     */
    public <T> T chatForObject(String systemPrompt, String userMessage, Class<T> clazz) {
        String raw = chat(systemPrompt, userMessage, true);
        String json = extractJson(raw);
        return JSON.parseObject(json, clazz);
    }

    /**
     * 从 LLM 返回中提取纯 JSON（去除可能的 markdown 代码块包裹）
     */
    private String extractJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "{}";
        }
        raw = raw.trim();
        // 去除 ```json ... ``` 包裹
        if (raw.startsWith("```")) {
            int start = raw.indexOf("\n");
            int end = raw.lastIndexOf("```");
            if (start > 0 && end > start) {
                raw = raw.substring(start, end).trim();
            }
        }
        return raw;
    }
}
