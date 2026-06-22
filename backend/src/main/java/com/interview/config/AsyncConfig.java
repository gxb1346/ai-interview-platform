package com.interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("scoreExecutor")
    public ExecutorService scoreExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean("evaluationExecutor")
    public ExecutorService evaluationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}