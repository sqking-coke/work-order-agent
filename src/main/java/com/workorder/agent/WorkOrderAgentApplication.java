package com.workorder.agent;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.*;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class WorkOrderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderAgentApplication.class, args);
    }
}
