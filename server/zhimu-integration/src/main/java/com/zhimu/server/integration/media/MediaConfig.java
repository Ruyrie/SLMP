package com.zhimu.server.integration.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FfmpegProperties.class)
public class MediaConfig {

    public static final String TRANSCODE_EXECUTOR = "transcodeExecutor";

    @Bean
    public MediaProcessor mediaProcessor(FfmpegProperties properties) {
        return new FfmpegMediaProcessor(properties);
    }

    @Bean
    public ImageProcessor imageProcessor() {
        return new ImageProcessor();
    }

    /**
     * 课程视频转码线程池（transcode-threads 个线程）。不作为默认候选：不影响 Spring Boot 的 applicationTaskExecutor
     * 与 @Async，注入时用 {@code @Qualifier("transcodeExecutor")}。
     */
    @Bean(name = TRANSCODE_EXECUTOR, defaultCandidate = false)
    public ThreadPoolTaskExecutor transcodeExecutor(FfmpegProperties properties) {
        int threads = Math.max(1, properties.getTranscodeThreads());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("transcode-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
