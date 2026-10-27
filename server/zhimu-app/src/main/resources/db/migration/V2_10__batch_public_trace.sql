ALTER TABLE t_batch ADD COLUMN trace_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '公开只读溯源开关' AFTER qr_token;
