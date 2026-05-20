package com.workorder.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 线程池配置属性，映射 application.yml 中 agent.thread-pool 前缀。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent.thread-pool")
public class AgentConfig {

    /** 核心线程数 */
    private int coreSize = 4;

    /** 最大线程数 */
    private int maxSize = 8;

    /** 队列容量 */
    private int queueCapacity = 200;

    /** 空闲线程存活时间（秒） */
    private int keepAliveSeconds = 60;

    /** 线程名前缀 */
    private String threadNamePrefix = "agent-task-";

    /** 关闭时等待任务完成的秒数 */
    private int awaitTerminationSeconds = 30;
}
