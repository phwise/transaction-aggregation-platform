package com.capitec.base.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ProviderExecutorConfig {

    @Bean(name = "providerExecutor", destroyMethod = "shutdown")
    public ExecutorService providerExecutor() {
        int poolSize = 3;
        int queueCapacity = 30;

        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("transaction-provider-" + thread.threadId());
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
    @Bean
    public Duration providerTimeout() {
        return Duration.ofSeconds(5);
    }
}