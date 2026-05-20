package com.workorder.agent.config;

import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.*;

import java.util.concurrent.*;

/**
 * Agent 异步任务线程池配置
 * 隔离 AI 解析任务，不阻塞主业务流程
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Value("${agent.thread-pool.core-size:4}")
    private int coreSize;

    @Value("${agent.thread-pool.max-size:8}")
    private int maxSize;

    @Value("${agent.thread-pool.queue-capacity:200}")
    private int queueCapacity;

    @Value("${agent.thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("agent-task-");
        // 拒绝策略：调用者线程执行，保证不丢失任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("Agent 异步线程池初始化: core={}, max={}, queue={}", coreSize, maxSize, queueCapacity);
        return executor;
    }
}
