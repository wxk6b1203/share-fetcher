package com.github.ak.fetcher.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 通过 @EnableScheduling 启用定时任务支持
    // 具体任务在各个 @Component 类中定义
}
