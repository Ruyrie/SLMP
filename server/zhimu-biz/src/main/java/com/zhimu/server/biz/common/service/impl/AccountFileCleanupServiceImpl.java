package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.api.AccountFileCleanupService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable object erasure without deleting storage objects inside a rollbackable business transaction. */
@Service
public class AccountFileCleanupServiceImpl implements AccountFileCleanupService {
    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final TransactionTemplate progress;

    public AccountFileCleanupServiceImpl(JdbcTemplate jdbc, ObjectStorage storage,
                                        PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.progress = new TransactionTemplate(manager);
        this.progress.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void queuePersonalFiles(Long userId) {
        requireCancelled(userId);
        List<Long> ids = jdbc.queryForList("SELECT id FROM t_file WHERE created_by=? "
                + "AND uploader_type='USER' AND scene IN ('AVATAR','AI_IMAGE') AND deleted=0 ORDER BY id",
                Long.class, userId);
        queue(userId, null, ids);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void queueFarmFiles(Long userId, Long farmId, Collection<Long> fileIds) {
        requireCancelled(userId);
        // The caller locks and verifies the expired, cancelled-owner farm before deleting its references.
        if (farmId == null) throw new IllegalArgumentException("Missing cleanup farm");
        queue(userId, farmId, fileIds);
    }

    private void queue(Long userId, Long farmId, Collection<Long> ids) {
        Set<Long> visited = new HashSet<>();
        if (ids == null) return;
        for (Long id : ids.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            retire(userId, farmId, id, visited);
        }
    }

    private void retire(Long userId, Long farmId, Long id, Set<Long> visited) {
        if (!visited.add(id)) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM t_file WHERE id=? AND deleted=0 FOR UPDATE", id);
        if (rows.isEmpty()) return;
        Map<String, Object> file = rows.getFirst();
        String scene = (String) file.get("scene");
        if (!storage.privateBucket().equals(file.get("bucket")) || scene == null
                || scene.startsWith("COURSE_") || scene.startsWith("TOPIC_")
                || scene.startsWith("NOTICE_") || scene.startsWith("KNOWLEDGE_")) return;
        if (referenced(id)) return;
        String bucket = (String) file.get("bucket");
        enqueue(userId, farmId, id, bucket, (String) file.get("object_key"));
        for (String key : jdbc.queryForList("SELECT object_key FROM t_file_variant WHERE file_id=?",
                String.class, id)) {
            enqueue(userId, farmId, id, bucket, key);
        }
        jdbc.update("UPDATE t_file SET deleted=1,bound=0,updated_at=? WHERE id=? AND deleted=0",
                BeijingTime.now(), id);
        // Generated cover candidates are independent file rows; never retire a shared cover.
        List<Long> children = jdbc.queryForList(
                "SELECT id FROM t_file WHERE parent_file_id=? AND deleted=0 ORDER BY id", Long.class, id);
        for (Long child : children) retire(userId, farmId, child, visited);
    }

    private boolean referenced(Long id) {
        return count("SELECT COUNT(*) FROM t_biz_media WHERE file_id=? AND deleted=0", id) > 0
                || count("SELECT COUNT(*) FROM t_user WHERE avatar_file_id=?", id) > 0
                || count("SELECT COUNT(*) FROM t_course WHERE cover_file_id=? OR video_file_id=?", id, id) > 0
                || count("SELECT COUNT(*) FROM t_course_topic WHERE cover_file_id=?", id) > 0
                || count("SELECT COUNT(*) FROM t_notice WHERE image_file_id=?", id) > 0
                || count("SELECT COUNT(*) FROM t_ai_knowledge_paragraph WHERE image_file_id=?", id) > 0
                || count("SELECT COUNT(*) FROM t_biz_report_export WHERE file_id=?", id) > 0
                || count("SELECT COUNT(*) FROM t_file WHERE cover_file_id=? AND deleted=0", id) > 0;
    }

    private void enqueue(Long userId, Long farmId, Long fileId, String bucket, String key) {
        if (bucket == null || key == null || key.isBlank()) return;
        jdbc.update("INSERT IGNORE INTO t_account_file_cleanup "
                        + "(user_id,farm_id,file_id,bucket,object_key,object_hash,status,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,'PENDING',?,?)", userId, farmId, fileId, bucket, key,
                digest(bucket + "\n" + key), BeijingTime.now(), BeijingTime.now());
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void purgeQueued(Long userId) {
        requireCancelled(userId);
        for (Map<String, Object> row : jdbc.queryForList("SELECT id,file_id,bucket,object_key "
                + "FROM t_account_file_cleanup WHERE user_id=? AND status='PENDING' ORDER BY id LIMIT 200",
                userId)) {
            Long id = ((Number) row.get("id")).longValue();
            Long fileId = ((Number) row.get("file_id")).longValue();
            String bucket = (String) row.get("bucket");
            String key = (String) row.get("object_key");
            try {
                requireCancelled(userId);
                if (count("SELECT COUNT(*) FROM t_file WHERE id=? AND deleted=1", fileId) != 1
                        || referenced(fileId)) throw new IllegalStateException("Retired file referenced");
                // Deduplicated storage keys can be shared even when their file IDs differ.
                boolean shared = count("SELECT COUNT(*) FROM t_file WHERE bucket=? AND object_key=? AND deleted=0",
                        bucket, key) > 0
                        || count("SELECT COUNT(*) FROM t_file_variant v JOIN t_file f ON f.id=v.file_id "
                        + "WHERE f.bucket=? AND v.object_key=? AND f.deleted=0", bucket, key) > 0;
                if (!shared) storage.remove(bucket, key); // idempotent when a previous attempt already removed it
                progress.executeWithoutResult(status -> jdbc.update("UPDATE t_account_file_cleanup "
                        + "SET status=?,last_error=NULL,completed_at=?,updated_at=? WHERE id=? AND user_id=?",
                        shared ? "SHARED" : "DONE", BeijingTime.now(), BeijingTime.now(), id, userId));
            } catch (RuntimeException error) {
                progress.executeWithoutResult(status -> jdbc.update("UPDATE t_account_file_cleanup "
                        + "SET retry_count=retry_count+1,last_error=?,updated_at=? WHERE id=? AND user_id=?",
                        error.getClass().getSimpleName(), BeijingTime.now(), id, userId));
                throw new IllegalStateException("Account file cleanup pending");
            }
        }
    }

    @Override
    public boolean hasPending(Long userId) {
        return count("SELECT COUNT(*) FROM t_account_file_cleanup WHERE user_id=? AND status='PENDING'", userId) > 0;
    }

    private void requireCancelled(Long userId) {
        if (userId == null || count("SELECT COUNT(*) FROM t_user u JOIN t_account_cancel c ON c.user_id=u.id "
                + "WHERE u.id=? AND u.status='CANCELLED' AND u.cancelled_at=c.created_at", userId) != 1) {
            throw new IllegalStateException("Cancellation account mismatch");
        }
    }

    private long count(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
