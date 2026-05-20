package com.workorder.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;

/**
 * 大模型客户端配置属性，统一管理所有 LLM 相关配置项。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {

    /** 是否启用 LLM，默认 false —— 需配置 api-key 后手动开启 */
    private boolean enabled = false;

    /** OpenAI 兼容格式的 API 地址 */
    private String apiUrl;

    /** API 密钥 */
    private String apiKey;

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 连接超时（秒） */
    private long connectTimeout = 10;

    /** 读取超时（秒） */
    private long readTimeout = 60;

    /** 最大 Token 数 */
    private int maxTokens = 2048;

    /** 生成温度 0-2，越低越确定 */
    private double temperature = 0.3;

    /** 最大重试次数 */
    private int maxRetries = 2;

    /** 重试间隔（毫秒） */
    private int retryDelayMs = 1000;
}
