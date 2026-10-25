package com.zhimu.server.demo;

import com.zhimu.server.integration.storage.MinioObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 开发演示图片走真实私有文件存储链路；正式环境不创建模拟图片。 */
@Slf4j
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class SampleQualificationImages implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final MinioObjectStorage storage;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var rows = jdbc.queryForList("""
                    SELECT id,bucket,object_key FROM t_file
                    WHERE id IN (240101,240102,240103,240104) AND deleted=0
                      AND object_key LIKE 'demo/farm-qualifications/%'
                    """);
            if (rows.isEmpty() || !storage.ensureBuckets()) return;
            for (var row : rows) {
                String key = (String) row.get("object_key");
                String filename = key.substring(key.lastIndexOf('/') + 1);
                if (!List.of("permit-before.png", "permit-after.png", "registration.png", "layout.png").contains(filename)) continue;
                String bucket = (String) row.get("bucket");
                byte[] bytes = new ClassPathResource("demo/farm-qualifications/" + filename).getContentAsByteArray();
                if (!storage.exists(bucket, key)) {
                    try (var in = new ByteArrayInputStream(bytes)) {
                        storage.put(bucket, key, in, bytes.length, "image/png");
                    }
                }
                String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                jdbc.update("UPDATE t_file SET file_size=?,sha256=?,upload_status='READY',bound=1 WHERE id=?",
                        bytes.length, sha256, row.get("id"));
            }
        } catch (Exception e) {
            // 与存储桶初始化保持一致：MinIO 暂不可用不阻止启动，恢复后重启会补齐对象。
            log.warn("演示资质图初始化未完成，请检查文件存储后重启：{}", e.getClass().getSimpleName());
        }
    }
}
