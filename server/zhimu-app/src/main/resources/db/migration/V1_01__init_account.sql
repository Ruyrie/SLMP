-- 模块01 账号与个人中心：t_user、t_recovery_code、t_account_recovery、t_recovery_verify_log、t_login_log、t_account_cancel
-- 依据模块01 服务端技术准备文档 2.3 节与 05 号文档 4.2 节；InnoDB、utf8mb4_0900_ai_ci、不建外键。
-- t_user 不设 deleted：注销用 status = CANCELLED 表示；注销满 30 天后 phone 置空。

CREATE TABLE t_user
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    phone                  VARCHAR(11)     NULL COMMENT '登录手机号；找回通过时换绑为新手机号，注销满 30 天置空',
    password_hash          VARCHAR(100)    NULL COMMENT 'BCrypt 摘要；注销时置空',
    name                   VARCHAR(20)     NOT NULL COMMENT '姓名；注册默认“用户”+手机号后 4 位，注销后为“已注销用户”',
    status                 VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '账号状态（USER_STATUS）：NORMAL 正常 / DISABLED 已停用 / CANCELLED 已注销',
    token_version          INT             NOT NULL DEFAULT 0 COMMENT '访问令牌版本号；加 1 使已签发令牌全部失效',
    token_revoke_reason    VARCHAR(30)     NULL COMMENT '最近一次令牌失效原因（TOKEN_REVOKE_REASON），作为 40104 的 data.reason',
    token_revoked_at       DATETIME        NULL COMMENT '最近一次令牌失效时间',
    must_change_password   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必须修改密码：1 表示当前密码是找回临时密码',
    temp_password_expire_at DATETIME       NULL COMMENT '临时密码过期时间（发出后 24 小时）',
    password_updated_at    DATETIME        NULL COMMENT '最近一次修改密码时间',
    recovered_at           DATETIME        NULL COMMENT '最近一次账号找回完成时间',
    recovery_fail_count    INT             NOT NULL DEFAULT 0 COMMENT '恢复码连续填错次数',
    recovery_fail_first_at DATETIME        NULL COMMENT '本轮第一次填错时间（24 小时窗口起点）',
    recovery_lock_until    DATETIME        NULL COMMENT '找回锁定解除时间',
    last_farm_id           BIGINT          NULL COMMENT '上次使用的养殖场 ID（由 API-F03 写入）',
    register_client        VARCHAR(20)     NOT NULL COMMENT '注册客户端：APP_ANDROID / WEB',
    agreement_version      VARCHAR(20)     NOT NULL COMMENT '注册时同意的协议组合版本，例如 2026-10-01',
    agreed_at              DATETIME        NOT NULL COMMENT '同意协议时间',
    last_login_at          DATETIME        NULL COMMENT '最近登录时间',
    last_login_client      VARCHAR(20)     NULL COMMENT '最近登录客户端：APP_ANDROID / WEB',
    last_active_at         DATETIME        NULL COMMENT '最近活跃时间（鉴权通过时按小时节流更新）',
    disabled_at            DATETIME        NULL COMMENT '平台停用时间',
    disabled_by            BIGINT          NULL COMMENT '停用操作的管理员 ID',
    disabled_reason        VARCHAR(200)    NULL COMMENT '停用原因',
    cancelled_at           DATETIME        NULL COMMENT '注销时间',
    profile_version        INT             NOT NULL DEFAULT 0 COMMENT '资料版本号：修改姓名时加 1，供 API-A16 乐观锁使用',
    version                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by             BIGINT          NULL COMMENT '创建人 ID（注册用户为本人 ID）',
    created_at             DATETIME        NOT NULL COMMENT '创建时间',
    updated_at             DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_status_created (status, created_at),
    KEY idx_user_last_active (last_active_at),
    KEY idx_user_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户账号（全局表，不带 farm_id）';

CREATE TABLE t_recovery_code
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '恢复码 ID',
    user_id       BIGINT          NOT NULL COMMENT '用户 ID',
    code_hash     VARCHAR(100)    NOT NULL COMMENT '恢复码 BCrypt 摘要；明文不入库、不写日志',
    status        VARCHAR(20)     NOT NULL COMMENT '恢复码状态（RECOVERY_CODE_STATUS）：PENDING_CONFIRM 待确认 / ACTIVE 有效 / REVOKED 已作废',
    scene         VARCHAR(20)     NOT NULL COMMENT '生成场景（RECOVERY_CODE_SCENE）：REGISTER 注册后 / RECOVERY 找回后 / REGENERATE 重新生成',
    is_regenerated TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否因上次未确认保存而重新生成：1 是',
    client_type   VARCHAR(20)     NOT NULL COMMENT '生成时客户端：APP_ANDROID / WEB，用于判定强制步骤',
    live_flag     TINYINT(1)      NULL COMMENT '未作废时为 1，作废时置空；唯一索引保证同一账号最多 1 条未作废的码',
    confirmed_at  DATETIME        NULL COMMENT '确认已保存时间',
    revoked_at    DATETIME        NULL COMMENT '作废时间',
    revoke_reason VARCHAR(30)     NULL COMMENT '作废原因（RECOVERY_CODE_REVOKE_REASON）',
    created_by    BIGINT          NULL COMMENT '创建人 ID',
    created_at    DATETIME        NOT NULL COMMENT '生成时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rc_user_live (user_id, live_flag),
    KEY idx_rc_user_status (user_id, status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='恢复码（只存 BCrypt 摘要，明文只出现在当次响应中）';

CREATE TABLE t_account_recovery
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '找回申请 ID',
    user_id                BIGINT          NOT NULL COMMENT '被找回的账号 ID',
    old_phone              VARCHAR(11)     NOT NULL COMMENT '原手机号',
    new_phone              VARCHAR(11)     NOT NULL COMMENT '新手机号（审核通过后换绑）',
    new_phone_verified_at  DATETIME        NOT NULL COMMENT '新手机号短信验证时间',
    description            VARCHAR(200)    NOT NULL COMMENT '情况说明',
    code_attempt_no        INT             NOT NULL COMMENT '提交时恢复码是第几次尝试',
    code_fail_count        INT             NOT NULL COMMENT '提交时连续填错次数',
    submit_channel         VARCHAR(20)     NOT NULL COMMENT '提交渠道（SUBMIT_CHANNEL）：APP_ANDROID / WEB / CS_PROXY',
    proxy_admin_id         BIGINT          NULL COMMENT '客服代登记的管理员 ID',
    submit_ip              VARCHAR(45)     NULL COMMENT '提交 IP',
    submit_ip_region       VARCHAR(50)     NULL COMMENT '提交 IP 属地',
    submit_device          VARCHAR(100)    NULL COMMENT '提交设备描述，例如 App（Android）',
    status                 VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态（AUDIT_STATUS）：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
    pending_flag           TINYINT(1)      NULL COMMENT '待审核时为 1，处理后置空；唯一索引保证同一账号最多 1 条待审核',
    verify_method          VARCHAR(30)     NULL COMMENT '核实方式（RECOVERY_VERIFY_METHOD）',
    handle_note            VARCHAR(200)    NULL COMMENT '处理说明；驳回时为驳回原因',
    handled_by_type        VARCHAR(20)     NULL COMMENT '处理人类型：ADMIN / SYSTEM',
    handled_by             BIGINT          NULL COMMENT '处理管理员 ID',
    handled_at             DATETIME        NULL COMMENT '处理时间',
    result_sms_status      VARCHAR(20)     NULL COMMENT '结果短信状态（SMS_SEND_STATUS）：PENDING / SENT / FAILED',
    result_sms_retry_count INT             NOT NULL DEFAULT 0 COMMENT '结果短信自动重发次数（上限 3）',
    result_sms_next_at     DATETIME        NULL COMMENT '下次重发时间',
    version                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by             BIGINT          NULL COMMENT '创建人 ID，自助提交为空',
    created_at             DATETIME        NOT NULL COMMENT '提交时间',
    updated_at             DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ar_user_pending (user_id, pending_flag),
    KEY idx_ar_status_created (status, created_at),
    KEY idx_ar_new_phone (new_phone),
    KEY idx_ar_sms (result_sms_status, result_sms_next_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='凭恢复码找回账号申请（用户提交与客服代登记共用）';

CREATE TABLE t_recovery_verify_log
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '校验记录 ID',
    user_id            BIGINT          NULL COMMENT '原手机号对应账号 ID；未注册时为空',
    old_phone          VARCHAR(11)     NOT NULL COMMENT '填写的原手机号（展示时打码）',
    result             VARCHAR(30)     NOT NULL COMMENT '校验结果（RECOVERY_VERIFY_RESULT）：CODE_CORRECT / CODE_WRONG / LOCKED / PHONE_NOT_REGISTERED',
    fail_count_after   INT             NOT NULL COMMENT '本次之后的连续填错次数；正确时为 0',
    remaining_attempts INT             NULL COMMENT '不正确时的剩余次数',
    lock_until         DATETIME        NULL COMMENT '已锁定时的解除时间',
    recovery_id        BIGINT          NULL COMMENT '正确并生成申请时关联的申请 ID',
    submit_channel     VARCHAR(20)     NOT NULL COMMENT '提交渠道：APP_ANDROID / WEB / CS_PROXY',
    proxy_admin_id     BIGINT          NULL COMMENT '客服代登记的管理员 ID',
    ip                 VARCHAR(45)     NULL COMMENT '请求 IP',
    ip_region          VARCHAR(50)     NULL COMMENT 'IP 属地',
    created_at         DATETIME        NOT NULL COMMENT '核对时间（只追加，不修改）',
    PRIMARY KEY (id),
    KEY idx_rvl_created (created_at),
    KEY idx_rvl_user_created (user_id, created_at),
    KEY idx_rvl_old_phone (old_phone),
    KEY idx_rvl_result_created (result, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='恢复码校验记录（只追加）';

CREATE TABLE t_login_log
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '登录日志 ID',
    user_id      BIGINT          NULL COMMENT '用户 ID；手机号未注册时为空',
    phone        VARCHAR(11)     NOT NULL COMMENT '登录时填写的手机号',
    login_type   VARCHAR(20)     NOT NULL COMMENT '登录方式（LOGIN_TYPE）：PASSWORD / SMS / TEMP_PASSWORD / REGISTER',
    client_type  VARCHAR(20)     NOT NULL COMMENT '客户端：APP_ANDROID / WEB',
    device_id    VARCHAR(64)     NULL COMMENT 'App 安装标识或浏览器随机标识',
    device_model VARCHAR(50)     NULL COMMENT '设备机型',
    os_version   VARCHAR(30)     NULL COMMENT '系统版本',
    app_version  VARCHAR(20)     NULL COMMENT 'App 版本',
    user_agent   VARCHAR(255)    NULL COMMENT 'Web 浏览器信息',
    ip           VARCHAR(45)     NULL COMMENT '请求 IP',
    ip_region    VARCHAR(50)     NULL COMMENT 'IP 属地',
    result       VARCHAR(20)     NOT NULL COMMENT '结果：SUCCESS / FAIL',
    fail_reason  VARCHAR(30)     NULL COMMENT '失败原因（LOGIN_FAIL_REASON）',
    created_at   DATETIME        NOT NULL COMMENT '登录时间（只追加）',
    PRIMARY KEY (id),
    KEY idx_ll_user_created (user_id, created_at),
    KEY idx_ll_phone_created (phone, created_at),
    KEY idx_ll_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录日志（保留 180 天）';

CREATE TABLE t_account_cancel
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '注销记录 ID',
    user_id             BIGINT          NOT NULL COMMENT '已注销账号 ID',
    phone_masked        VARCHAR(20)     NOT NULL COMMENT '注销手机号（打码），例如 138****6620',
    phone_hash          CHAR(64)        NOT NULL COMMENT '加盐 SHA-256 手机号摘要，仅用于审计核对',
    retained_farms_json JSON            NULL COMMENT '停用后保留的养殖场快照',
    deleted_farms_json  JSON            NULL COMMENT '随注销删除的未通过审核养殖场快照',
    exited_farms_json   JSON            NULL COMMENT '退出的养殖场与角色快照',
    withdrawn_json      JSON            NULL COMMENT '撤回或驳回的转让、邀请、修改申请、找回申请数量',
    client_type         VARCHAR(20)     NULL COMMENT '注销所用客户端：APP_ANDROID / WEB',
    ip                  VARCHAR(45)     NULL COMMENT '注销请求 IP',
    ip_region           VARCHAR(50)     NULL COMMENT 'IP 属地',
    phone_release_at    DATETIME        NOT NULL COMMENT '手机号可重新注册时间（注销时间 + 30 天）',
    farm_cleanup_at     DATETIME        NULL COMMENT '停用养殖场清理时间（停用时间 + 180 天；没有保留的养殖场时为空）',
    personal_cleaned_at DATETIME        NULL COMMENT '个人数据清理完成时间',
    phone_released_at   DATETIME        NULL COMMENT '手机号释放完成时间',
    farm_cleaned_at     DATETIME        NULL COMMENT '保留养殖场清理完成时间',
    cleanup_status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '清理状态（CLEANUP_STATUS）：PENDING / RUNNING / WAITING / DONE / FAILED',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '清理失败重试次数',
    last_error          VARCHAR(500)    NULL COMMENT '最近一次清理失败原因',
    next_run_at         DATETIME        NULL COMMENT '下次清理时间',
    created_by          BIGINT          NULL COMMENT '创建人 ID',
    created_at          DATETIME        NOT NULL COMMENT '注销时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ac_user (user_id),
    KEY idx_ac_cleanup (cleanup_status, next_run_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='账号注销记录与分阶段清理进度';
