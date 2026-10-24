-- 管理端-02 内容运营：平台通知（WA-C01，ADM-C01–C10）
-- 表结构按《管理端-02-内容运营-服务端技术准备文档（上）》2.3.1–2.3.3。
-- 已执行的脚本不再修改；后续结构变更另起 V1_22_1、V1_22_2…

CREATE TABLE t_notice
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知 ID',
    title               VARCHAR(50)     NOT NULL COMMENT '标题，1–50 字',
    level               VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NOTICE_LEVEL：IMPORTANT 重要 / NORMAL 普通',
    content             MEDIUMTEXT      NULL COMMENT '受限 HTML：段落、换行、加粗、编号列表、项目列表',
    content_length      INT             NOT NULL DEFAULT 0 COMMENT '去标签后的正文字数',
    image_file_id       BIGINT UNSIGNED NULL COMMENT '配图 1 张 → t_file（scene = NOTICE_IMAGE）',
    scope_type          VARCHAR(20)     NOT NULL DEFAULT 'ALL' COMMENT 'NOTICE_SCOPE：ALL / CONDITION / FARM',
    send_mode           VARCHAR(20)     NOT NULL DEFAULT 'NOW' COMMENT 'SEND_MODE：NOW / SCHEDULED',
    scheduled_at        DATETIME        NULL COMMENT '定时发送时间',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'NOTICE_STATUS：DRAFT / SCHEDULED / SENT / REVOKED / CANCELED',
    est_account_count   INT             NOT NULL DEFAULT 0 COMMENT '保存时预计送达账号数',
    est_farm_count      INT             NOT NULL DEFAULT 0 COMMENT '保存时预计送达养殖场数',
    dispatch_status     VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'DISPATCH_STATUS：NONE / PENDING / RUNNING / DONE',
    delivered_count     INT             NOT NULL DEFAULT 0 COMMENT '实际送达账号数',
    read_count          INT             NOT NULL DEFAULT 0 COMMENT '已读账号数（统计缓存）',
    push_open_count     INT             NOT NULL DEFAULT 0 COMMENT '推送点开账号数（统计缓存）',
    read_before_revoke  INT             NULL COMMENT '撤回时的已读数',
    stat_at             DATETIME        NULL COMMENT '统计时间',
    sent_at             DATETIME        NULL COMMENT '发送时间',
    sent_by             BIGINT UNSIGNED NULL COMMENT '发送人管理员 ID（定时为设定定时的管理员）',
    revoked_at          DATETIME        NULL COMMENT '撤回时间',
    revoked_by          BIGINT UNSIGNED NULL COMMENT '撤回人管理员 ID',
    revoke_reason       VARCHAR(100)    NULL COMMENT '撤回原因（仅管理员可见）',
    canceled_at         DATETIME        NULL COMMENT '取消时间',
    canceled_by         BIGINT UNSIGNED NULL COMMENT '取消人管理员 ID',
    cancel_reason       VARCHAR(100)    NULL COMMENT '取消原因（仅管理员可见）',
    copied_from_id      BIGINT UNSIGNED NULL COMMENT '复制来源通知 ID',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：只删草稿',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          BIGINT UNSIGNED NULL COMMENT '创建人管理员 ID',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_by          BIGINT UNSIGNED NULL COMMENT '最后修改人管理员 ID',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_notice_status_updated (status, updated_at),
    KEY idx_notice_status_scheduled (status, scheduled_at),
    KEY idx_notice_dispatch (dispatch_status, sent_at),
    KEY idx_notice_sent (sent_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='平台通知（管理端-02）';

CREATE TABLE t_notice_target
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '条件 ID',
    notice_id    BIGINT UNSIGNED NOT NULL COMMENT '通知 ID（t_notice）',
    target_type  VARCHAR(20)     NOT NULL COMMENT 'NOTICE_TARGET_TYPE：ROLE / PROVINCE / CATEGORY / FARM',
    target_value VARCHAR(32)     NOT NULL COMMENT '角色编码、省级码、品类 ID 或养殖场 ID',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_notice_target (notice_id, target_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='通知接收条件';

CREATE TABLE t_notice_recipient
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '送达明细 ID',
    notice_id      BIGINT UNSIGNED NOT NULL COMMENT '通知 ID',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '接收账号 ID',
    role_snapshot  VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'NOTICE_ROLE：OWNER / MANAGER / STAFF / NONE',
    farm_id        BIGINT UNSIGNED NULL COMMENT '指定养殖场时用户端标签所用的养殖场',
    message_id     BIGINT UNSIGNED NULL COMMENT '站内消息 ID（t_message）',
    push_record_id BIGINT UNSIGNED NULL COMMENT '推送记录 ID（t_push_record）',
    read_at        DATETIME        NULL COMMENT '已读时间（从 t_message.read_at 同步）',
    push_opened_at DATETIME        NULL COMMENT '推送点开时间（从 t_push_record.clicked_at 同步）',
    is_hidden      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '撤回时未读置 1',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_recipient (notice_id, user_id),
    KEY idx_notice_recipient_role (notice_id, role_snapshot, read_at),
    KEY idx_notice_recipient_message (message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='通知送达明细';
