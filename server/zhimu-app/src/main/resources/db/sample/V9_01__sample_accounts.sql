-- 模块01 示例账号（只在 dev、test 的 db/sample 下执行）：密码统一为 Zmt12345（BCrypt 强度 10）
-- t_user 与 t_recovery_code 均显式插入 ID；不依赖自增，不建外键，可与 V9_02 分开执行。

INSERT INTO t_user (id, phone, password_hash, name, status, token_version, must_change_password,
                    password_updated_at, last_farm_id, register_client, agreement_version, agreed_at,
                    last_login_at, last_login_client, profile_version, version, created_by, created_at, updated_at)
VALUES (10001, '13800006620', '$2a$10$7N.Tnvp6XuJg9yKDI2yLVexpRtS3O8fiGw/Cd5s8xwB/xlxxyzRFq', '王建国', 'NORMAL', 0, 0,
        '2026-10-01 08:00:00', 1001, 'APP_ANDROID', '2026-10-01', '2026-02-20 08:00:00',
        '2026-10-19 07:52:00', 'APP_ANDROID', 3, 0, 10001, '2026-02-20 08:00:00', '2026-10-19 07:52:00'),
       (10002, '13900001234', '$2a$10$7N.Tnvp6XuJg9yKDI2yLVexpRtS3O8fiGw/Cd5s8xwB/xlxxyzRFq', '刘芳', 'NORMAL', 0, 0,
        '2026-10-02 08:00:00', 1001, 'APP_ANDROID', '2026-10-01', '2026-09-02 08:00:00',
        '2026-10-19 07:55:00', 'APP_ANDROID', 1, 0, 10002, '2026-09-02 08:00:00', '2026-10-19 07:55:00'),
       (10003, '13700005678', '$2a$10$7N.Tnvp6XuJg9yKDI2yLVexpRtS3O8fiGw/Cd5s8xwB/xlxxyzRFq', '李明', 'NORMAL', 0, 0,
        '2026-10-05 08:00:00', 1001, 'APP_ANDROID', '2026-10-01', '2026-09-05 08:00:00',
        '2026-10-19 08:02:00', 'APP_ANDROID', 1, 0, 10003, '2026-09-05 08:00:00', '2026-10-19 08:02:00');

-- 王建国一条已确认的有效恢复码（明文按文档示例，只用于本机演示；密码与恢复码明文都不写日志）
INSERT INTO t_recovery_code (id, user_id, code_hash, status, scene, is_regenerated, client_type, live_flag,
                             confirmed_at, created_by, created_at, updated_at)
VALUES (501, 10001, '$2a$10$VsHVOVIj1ouEE6i6Mz1UZeJIbcZwwqsj8WU1VTd2eAwnRwVbLyJ4.', 'ACTIVE', 'REGISTER', 0, 'APP_ANDROID', 1,
        '2026-03-01 10:15:00', 10001, '2026-03-01 10:12:00', '2026-03-01 10:15:00');
