package com.taipei.iot.eventrule.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * event-rule 非同步執行器配置（沿用 audit 模組同一模式）。
 *
 * <p>
 * {@code @EnableAsync} 可複數設定無副作用；Spring 只啟用一次 async 支援。
 */
@Configuration
@EnableAsync
public class EventRuleAsyncConfig {

	@Bean("eventRuleExecutor")
	public TaskExecutor eventRuleExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("evtrule-async-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

}
