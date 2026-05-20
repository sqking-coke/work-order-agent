package com.workorder.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 异步任务线程池配置，隔离 AI 解析任务，不阻塞主业务流程。
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor(AgentConfig config) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(config.getAwaitTerminationSeconds());
        executor.initialize();

        log.info("Agent 异步线程池初始化: core={}, max={}, queue={}, keepAlive={}s",
                config.getCoreSize(), config.getMaxSize(),
                config.getQueueCapacity(), config.getKeepAliveSeconds());
        return executor;
    }
}
