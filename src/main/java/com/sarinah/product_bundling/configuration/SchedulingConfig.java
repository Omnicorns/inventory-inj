package com.sarinah.product_bundling.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);                 // kecil dulu, naikkan pelan
        s.setThreadNamePrefix("sched-");
        s.setAwaitTerminationSeconds(60);
        s.setWaitForTasksToCompleteOnShutdown(true);
        return s;
    }
}
