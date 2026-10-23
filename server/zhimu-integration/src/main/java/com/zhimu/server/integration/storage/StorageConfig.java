package com.zhimu.server.integration.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MinioProperties.class, HlsProperties.class})
public class StorageConfig {

    @Bean
    public MinioObjectStorage objectStorage(MinioProperties minio, HlsProperties hls) {
        return new MinioObjectStorage(minio, hls);
    }

    @Bean
    public StorageBucketInitializer storageBucketInitializer(MinioObjectStorage storage) {
        return new StorageBucketInitializer(storage);
    }
}
