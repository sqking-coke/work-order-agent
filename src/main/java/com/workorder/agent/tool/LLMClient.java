package com.workorder.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.workorder.agent.config.LLMConfig;
import com.workorder.agent.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * LLM 客户端，通过 OpenAI 兼容 API 调用大模型。
 */
@Slf4j
@Component
public class LLMClient {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final LLMConfig config;
    private final OkHttpClient client;

    public LLMClient(LLMConfig config) {
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用大模型（系统提示词 + 用户输入），返回纯文本结果。
     * LLM 不可用时返回 null。
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, false);
    }

    /**
     * 调用大模型，可指定是否强制 JSON 输出模式。
     * LLM 不可用时返回 null。
     */
    public String chat(String systemPrompt, String userMessage, boolean jsonMode) {
        if (!config.isEnabled()) {
            log.debug("LLM 未启用，跳过调用");
            return null;
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("LLM API Key 未配置，跳过调用");
            return null;
        }

        JSONObject body = buildRequestBody(systemPrompt, userMessage, jsonMode);
        String responseBody = executeWithRetry(body);
        if (responseBody == null) {
            return null;
        }
        return extractContent(responseBody);
    }

    /**
     * 调用大模型并直接解析为 JSONObject。
     * LLM 不可用时返回 null。
     */
    public JSONObject chatForJson(String systemPrompt, String userMessage) {
        String raw = chat(systemPrompt, userMessage, true);
        if (raw == null) {
            return null;
        }
        return JSON.parseObject(extractJson(raw));
    }

    /**
     * 调用大模型并直接解析为指定 Java 类型。
     * LLM 不可用时返回 null。
     */
    public <T> T chatForObject(String systemPrompt, String userMessage, Class<T> clazz) {
        String raw = chat(systemPrompt, userMessage, true);
        if (raw == null) {
            return null;
        }
        return JSON.parseObject(extractJson(raw), clazz);
    }

    // ==================== 内部实现 ====================

    private JSONObject buildRequestBody(String systemPrompt, String userMessage, boolean jsonMode) {
        JSONArray messages = new JSONArray();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
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
        body.put("model", config.getModel());
        body.put("messages", messages);
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());

        if (jsonMode) {
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", "json_object");
            body.put("response_format", responseFormat);
        }

        return body;
    }

    private String executeWithRetry(JSONObject body) {
        int maxRetries = config.getMaxRetries();
        int retryDelayMs = config.getRetryDelayMs();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("LLM 重试第 {} 次", attempt);
                }

                Request request = new Request.Builder()
                        .url(config.getApiUrl())
                        .addHeader("Authorization", "Bearer " + config.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return response.body() != null ? response.body().string() : "";
                    }

                    String respBody = response.body() != null ? response.body().string() : "";
                    int code = response.code();

                    // 4xx 客户端错误不重试
                    if (code >= 400 && code < 500) {
                        log.error("LLM 客户端错误 HTTP {}: {}", code, respBody);
                        throw new BusinessException("大模型调用失败: HTTP " + code);
                    }

                    // 5xx 服务端错误，可重试
                    log.warn("LLM 服务端错误(第{}次) HTTP {}: {}", attempt + 1, code, respBody);
                    if (attempt < maxRetries) {
                        sleep(retryDelayMs);
                        continue;
                    }
                    throw new BusinessException("大模型服务异常，已重试" + maxRetries + "次仍失败");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (IOException e) {
                log.warn("LLM 网络异常(第{}次): {}", attempt + 1, e.getMessage());
                if (attempt < maxRetries) {
                    sleep(retryDelayMs);
                } else {
                    throw new BusinessException("大模型调用异常: " + e.getMessage(), e);
                }
            }
        }

        throw new BusinessException("大模型调用失败，已达最大重试次数");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("重试等待被中断", e);
        }
    }

    private String extractContent(String responseBody) {
        JSONObject respJson = JSON.parseObject(responseBody);
        JSONArray choices = respJson.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException("大模型返回结果为空");
        }

        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null) {
            throw new BusinessException("大模型返回消息体为空");
        }

        String content = message.getString("content");
        log.debug("LLM 返回: {}", content);
        return content;
    }

    /**
     * 从 LLM 返回中提取纯 JSON 文本，自动处理 markdown 代码块等包裹格式。
     */
    static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String trimmed = raw.trim();

        // 去除 ```json ... ``` 或 ``` ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }

        // 如果仍不是以 { 开头，尝试找到第一个 { 和最后一个 }
        if (!trimmed.startsWith("{")) {
            int jsonStart = trimmed.indexOf('{');
            int jsonEnd = trimmed.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                trimmed = trimmed.substring(jsonStart, jsonEnd + 1);
            }
        }

        return trimmed;
    }
}
