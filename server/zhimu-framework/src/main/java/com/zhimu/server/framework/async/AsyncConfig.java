package com.zhimu.server.framework.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池（@EnableAsync 在启动类上）。全部线程池都用 {@link ContextCopyingTaskDecorator} 传递请求上下文与 MDC。
 * <ul>
 *     <li>{@value #TASK_EXECUTOR}：默认（@Primary），虚拟线程；{@code @Async} 不写名称时使用；同时作为 Spring MVC 异步请求的执行器</li>
 *     <li>{@value #PUSH_EXECUTOR}：极光推送，平台线程池，队列满时由调用方线程执行</li>
 *     <li>{@value #AI_EXECUTOR}：AI 调用与 SSE 生成，虚拟线程（并发上限由 AI 网关控制）</li>
 *     <li>{@value #EXPORT_EXECUTOR}：导出 Excel / PDF，平台线程池，队列满时拒绝（抛 TaskRejectedException）</li>
 * </ul>
 * 事件约定：模块间解耦的事件在业务事务提交后再异步处理，避免读到未提交数据或事务回滚后仍触发副作用：
 * <pre>
 * &#64;Async
 * &#64;TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onBatchEntered(BatchEnteredEvent event) { ... }
 * </pre>
 * 监听方法里需要写库时自行开启新事务（{@code @Transactional(propagation = REQUIRES_NEW)} 或调用带事务的 Service）。
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    public static final String TASK_EXECUTOR = "taskExecutor";
    public static final String PUSH_EXECUTOR = "pushExecutor";
    public static final String AI_EXECUTOR = "aiExecutor";
    public static final String EXPORT_EXECUTOR = "exportExecutor";

    private final TaskDecorator taskDecorator = new ContextCopyingTaskDecorator();

    @Primary
    @Bean(name = {TASK_EXECUTOR, "applicationTaskExecutor"})
    public SimpleAsyncTaskExecutor taskExecutor() {
        return virtualThreadExecutor("zmt-async-");
    }

    @Bean(name = PUSH_EXECUTOR)
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = platformExecutor("zmt-push-", 4, 16, 2000);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean(name = AI_EXECUTOR)
    public SimpleAsyncTaskExecutor aiExecutor() {
        return virtualThreadExecutor("zmt-ai-");
    }

    @Bean(name = EXPORT_EXECUTOR)
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = platformExecutor("zmt-export-", 2, 4, 100);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("异步方法 {}.{} 执行失败",
                method.getDeclaringClass().getSimpleName(), method.getName(), ex);
    }

    private SimpleAsyncTaskExecutor virtualThreadExecutor(String threadNamePrefix) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(taskDecorator);
        executor.setTaskTerminationTimeout(30_000);
        return executor;
    }

    private ThreadPoolTaskExecutor platformExecutor(String threadNamePrefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(60);
        executor.setTaskDecorator(taskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
