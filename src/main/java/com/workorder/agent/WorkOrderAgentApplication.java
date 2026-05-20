package com.workorder.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能工单处理Agent 启动类。
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class WorkOrderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderAgentApplication.class, args);
    }
}
