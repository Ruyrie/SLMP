package com.zhimu.server.integration.storage;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * MinIO 配置（zhimu.integration.minio）。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.minio")
public class MinioProperties {

    /** 应用访问 MinIO 的内网地址 */
    private String endpoint = "http://127.0.0.1:9000";
    /** 生成临时下载、分片上传地址用的公网入口 */
    private String publicEndpoint = "http://127.0.0.1:9000";
    private String accessKey;
    @ToString.Exclude
    private String secretKey;
    private String privateBucket = "zmt-private";
    private String courseBucket = "zmt-course";
    private Duration presignTtl = Duration.ofHours(2);
    private Duration exportPresignTtl = Duration.ofMinutes(10);
    private DataSize multipartPartSize = DataSize.ofMegabytes(10);
}
