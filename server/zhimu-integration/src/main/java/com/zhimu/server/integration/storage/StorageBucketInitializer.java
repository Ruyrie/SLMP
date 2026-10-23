package com.zhimu.server.integration.storage;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 启动完成后在虚拟线程中检查存储桶；MinIO 不可用只打 WARN，不影响启动。
 */
public class StorageBucketInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MinioObjectStorage storage;

    public StorageBucketInitializer(MinioObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Thread.ofVirtual().name("minio-bucket-init").start(storage::ensureBuckets);
    }
}
