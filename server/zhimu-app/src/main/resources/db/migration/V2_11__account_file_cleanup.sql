-- Only files retired by an authenticated account-cancellation workflow enter this outbox.
CREATE TABLE t_account_file_cleanup (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    farm_id BIGINT UNSIGNED NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    bucket VARCHAR(63) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    object_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_file_object (file_id, object_hash),
    KEY idx_account_file_pending (user_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
