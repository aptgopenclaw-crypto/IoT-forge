package com.taipei.iot.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration class for asynchronous audit tasks. Defines a custom TaskExecutor for
 * handling audit-related operations.
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig {

	// @Bean("auditExecutor")：在 Spring 容器中註冊一個名為 auditExecutor 的 Bean。
	// 當其他 Service 需要使用這個專屬線程池時，可以透過 @Async("auditExecutor") 來指定使用它。
	@Bean("auditExecutor")
	public TaskExecutor auditExecutor() {

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		// 核心線程數 = 2：線程池初始化時會建立 2 個核心線程。即使這些線程處於空閒狀態，預設也不會被銷毀（除非設定了
		// allowCoreThreadTimeOut）。
		// 這保證了隨時有基本的處理能力來應對審計日誌。
		executor.setCorePoolSize(2);

		// 最大線程數 = 8：線程池中允許的最大線程數量。當核心線程全部忙碌且隊列已滿時，會創建新的線程來處理任務，直到達到最大線程數。
		executor.setMaxPoolSize(8);

		// 緩衝隊列容量 = 500：當所有核心線程都在忙碌時，新的任務會被放入這個隊列中等待執行。
		executor.setQueueCapacity(500);

		// 線程名稱前綴 = "audit-async-"：這有助於在日誌中識別這些線程，方便調試和監控。
		executor.setThreadNamePrefix("audit-async-");

		// 拒絕策略 = CallerRunsPolicy：這是最關鍵的配置之一。當線程數達到最大（8個）且隊列也滿了（500個）時，新提交的任務會被拒絕。使用
		// CallerRunsPolicy 策略意味著：誰調用這個異步方法，就由誰（通常是主業務線程）自己來執行這個任務。
		// 好處：審計日誌通常要求不能丟失。這個策略不僅保證了日誌任務絕對不會被丟棄，還能產生「背壓（Back-pressure）」效果——讓主業務線程自己去寫日誌，主線程就會變慢，從而自動減緩新任務提交的速度，給線程池時間消化隊列中的任務。
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		// 初始化線程池：在 Spring 容器啟動時，這個方法會被調用，並初始化線程池，使其準備好處理異步任務。
		executor.initialize();
		return executor;
	}

}
