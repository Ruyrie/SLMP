-- 公共表（05 号文档第五节）：t_file、t_file_variant、t_biz_media、t_change_request、t_operation_log
-- 已执行的脚本不再修改；结构变更另起 V1_00_1、V1_00_2…

CREATE TABLE t_file
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件 ID',
    bucket              VARCHAR(63)     NOT NULL COMMENT '存储桶：zmt-private（用户上传与导出文件）、zmt-course（课程视频、HLS 与封面）',
    object_key          VARCHAR(255)    NOT NULL COMMENT '对象键，例如 inspection/20261019/{uuid}.mp4',
    original_name       VARCHAR(255)    NULL COMMENT '原文件名，例如 冬季鸡舍通风与保温.mp4',
    file_kind           VARCHAR(20)     NOT NULL COMMENT '文件种类：IMAGE 图片 / VIDEO 视频 / AUDIO 音频 / DOCUMENT 导出文档',
    scene               VARCHAR(20)     NOT NULL COMMENT '上传场景（FILE_SCENE），决定校验规则、存储桶和保留期',
    mime_type           VARCHAR(100)    NULL COMMENT 'MIME 类型，例如 video/mp4',
    file_ext            VARCHAR(10)     NULL COMMENT '扩展名，例如 mp4',
    file_size           BIGINT          NOT NULL COMMENT '文件大小（字节）',
    sha256              CHAR(64)        NULL COMMENT 'SHA-256 摘要，完整性校验',
    width               INT             NULL COMMENT '图片与视频宽度（像素）',
    height              INT             NULL COMMENT '图片与视频高度（像素）',
    duration_ms         INT             NULL COMMENT '视频与语音时长（毫秒）；巡检视频不超过 30000',
    cover_file_id       BIGINT UNSIGNED NULL COMMENT '视频封面文件 ID（指向另一条 t_file）',
    parent_file_id      BIGINT UNSIGNED NULL COMMENT '原视频文件 ID：自动截取的封面候选指向原视频',
    upload_status       VARCHAR(20)     NOT NULL COMMENT '上传状态：UPLOADING 上传中 / READY 已上传 / FAILED 上传失败',
    multipart_upload_id VARCHAR(200)    NULL COMMENT '课程视频分片上传任务编号',
    transcode_status    VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT '转码状态：NONE 无需转码 / WAITING 等待中 / PROCESSING 转码中 / SUCCESS 已完成 / FAILED 转码失败',
    transcode_error     VARCHAR(500)    NULL COMMENT '转码失败原因',
    farm_id             BIGINT UNSIGNED NULL COMMENT '养殖场 ID：用户端上传时的当前养殖场；管理端上传为空',
    uploader_type       VARCHAR(20)     NOT NULL COMMENT '上传人类型：USER 用户 / ADMIN 管理员 / SYSTEM 系统',
    client_type         VARCHAR(20)     NULL COMMENT '客户端：APP_ANDROID / WEB / ADMIN_WEB',
    shot_at             DATETIME        NULL COMMENT '拍摄时间；没有时取上传时间',
    bound               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已被业务记录引用：1 是；24 小时仍为 0 的文件由定时任务清理',
    expire_at           DATETIME        NULL COMMENT '导出文件的过期时间',
    created_by          BIGINT UNSIGNED NULL COMMENT '上传人 ID：按 uploader_type 指向用户或管理员，系统为 0',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_bucket_key (bucket, object_key),
    KEY idx_file_bound_status_created (bound, upload_status, created_at),
    KEY idx_file_farm_scene (farm_id, scene),
    KEY idx_file_parent (parent_file_id),
    KEY idx_file_expire (expire_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件（MinIO 对象：图片、视频、音频、导出文件）';

CREATE TABLE t_file_variant
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '派生版本 ID',
    file_id       BIGINT UNSIGNED NOT NULL COMMENT '原文件 ID（t_file）',
    variant_code  VARCHAR(20)     NOT NULL COMMENT '派生版本：HLS_720P / HLS_480P / MP4_720P / THUMB 缩略图',
    object_key    VARCHAR(255)    NULL COMMENT '派生文件对象键，例如 course/20261019/{uuid}/720p/index.m3u8',
    status        VARCHAR(20)     NOT NULL COMMENT '转码状态：WAITING 等待中 / PROCESSING 转码中 / SUCCESS 已完成 / FAILED 转码失败',
    progress      DECIMAL(5, 4)   NOT NULL DEFAULT 0 COMMENT '转码进度 0–1，0.6200 表示 62%',
    width         INT             NULL COMMENT '宽度（像素），例如 1280',
    height        INT             NULL COMMENT '高度（像素），例如 720',
    bitrate_kbps  INT             NULL COMMENT '码率（kbps），例如 1500',
    file_size     BIGINT          NULL COMMENT '派生文件大小（字节）',
    retry_count   INT             NOT NULL DEFAULT 0 COMMENT '自动重试次数（最多 3 次）',
    error_message VARCHAR(500)    NULL COMMENT '失败原因',
    started_at    DATETIME        NULL COMMENT '开始处理时间',
    finished_at   DATETIME        NULL COMMENT '处理完成时间',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_variant_file_code (file_id, variant_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件派生版本与转码（课程 HLS 两档、Web 巡检视频压缩版、图片缩略图）';

CREATE TABLE t_biz_media
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联 ID',
    biz_type        VARCHAR(30)     NOT NULL COMMENT '业务记录类型（BIZ_TYPE），例如 INSPECTION、ABNORMAL_REPORT、BATCH_DEATH、FARM、FARM_CHANGE',
    biz_id          BIGINT UNSIGNED NOT NULL COMMENT '业务记录 ID',
    file_id         BIGINT UNSIGNED NOT NULL COMMENT '文件 ID（t_file）',
    farm_id         BIGINT UNSIGNED NULL COMMENT '养殖场 ID（冗余，便于权限判断）',
    sort_no         INT             NOT NULL COMMENT '排序号：从 1 开始，按添加顺序，可拖动调整',
    source_biz_type VARCHAR(30)     NULL COMMENT '文件最初来源的业务类型；巡检转上报时为 INSPECTION',
    source_biz_id   BIGINT UNSIGNED NULL COMMENT '文件最初来源的业务记录 ID；巡检转上报时为巡检 ID',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人 ID（用户或管理员），系统为 0',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '最后修改时间',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_media_biz_file (biz_type, biz_id, file_id),
    KEY idx_biz_media_biz_sort (biz_type, biz_id, sort_no),
    KEY idx_biz_media_file (file_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='业务记录与文件关联';

CREATE TABLE t_change_request
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '修改申请 ID',
    request_no           VARCHAR(20)     NOT NULL COMMENT '申请编号：CR + 年月日 + 4 位序号，例如 CR2610190008',
    farm_id              BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    biz_type             VARCHAR(30)     NOT NULL COMMENT '原记录类型（BIZ_TYPE），例如 FEED_RECORD',
    biz_id               BIGINT UNSIGNED NOT NULL COMMENT '原记录 ID',
    source_module        VARCHAR(20)     NOT NULL COMMENT '来源模块：PEN_STOCK / BREEDING / FEEDING / INSPECTION / HEALTH / INVENTORY / SALE',
    batch_id             BIGINT UNSIGNED NULL COMMENT '批次 ID，便于定位和按批次号搜索',
    pen_id               BIGINT UNSIGNED NULL COMMENT '栏舍 ID，便于定位',
    record_locator       VARCHAR(200)    NOT NULL COMMENT '原记录定位文字快照，例如 3 号育肥舍 · ZM2610-031 · 10月18日 晚餐',
    change_type          VARCHAR(20)     NOT NULL COMMENT '申请类型：UPDATE 修改 / DELETE 删除 / VOID 作废',
    field_code           VARCHAR(50)     NULL COMMENT '主修改项编码，例如 actual_qty；删除和作废时为空',
    field_name           VARCHAR(50)     NULL COMMENT '主修改项名称，例如 实际投喂量',
    value_unit           VARCHAR(20)     NULL COMMENT '主修改项单位，例如 千克',
    before_display       VARCHAR(200)    NULL COMMENT '修改前显示值，例如 140 千克',
    after_display        VARCHAR(200)    NULL COMMENT '修改后显示值，例如 160 千克',
    before_json          JSON            NOT NULL COMMENT '原记录快照：原记录卡与全部可改字段的原值',
    after_json           JSON            NULL COMMENT '主修改项的新值和联动字段',
    record_version       VARCHAR(50)     NOT NULL COMMENT '提交时原记录的 version（没有 version 的表用 updated_at），审核通过时比对',
    reason               VARCHAR(200)    NOT NULL COMMENT '修改原因，1–200 字',
    impact_stock         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '影响存栏：1 是',
    impact_cost          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '影响成本：1 是',
    impact_inventory     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '影响库存：1 是',
    impact_sellable_date TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '影响可出栏日期：1 是',
    impact_income        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '影响收入：1 是',
    impact_stats         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '影响统计：1 是',
    recalc_tags          VARCHAR(200)    NULL COMMENT '通过后需要重算的项目，例如 头均采食量、料肉比、饲料成本、经营报表',
    status               VARCHAR(20)     NOT NULL COMMENT '申请状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回 / CANCELED 已撤销',
    pending_flag         TINYINT(1)      NULL COMMENT '待审核时为 1，处理后置空；配合唯一索引保证同一记录只有一条待审核申请',
    submitted_by         BIGINT UNSIGNED NOT NULL COMMENT '提交人用户 ID',
    submitter_role       VARCHAR(20)     NOT NULL COMMENT '提交人角色：OWNER / MANAGER / STAFF',
    submitted_at         DATETIME        NOT NULL COMMENT '提交时间',
    submit_channel       VARCHAR(20)     NOT NULL COMMENT '提交渠道：APP_ANDROID / WEB',
    resubmit_of_id       BIGINT UNSIGNED NULL COMMENT '驳回后重新提交时指向原申请 ID',
    reviewed_by          BIGINT UNSIGNED NULL COMMENT '审核管理员 ID',
    reviewed_at          DATETIME        NULL COMMENT '审核时间',
    review_comment       VARCHAR(200)    NULL COMMENT '通过意见',
    reject_reason        VARCHAR(200)    NULL COMMENT '驳回原因（驳回时必填）',
    impact_json          JSON            NULL COMMENT '审核时的影响范围与系统校验结果快照',
    recalc_summary       VARCHAR(100)    NULL COMMENT '重算结果摘要，例如 已重新计算批次收益',
    recalc_count         INT             NULL COMMENT '重算项目数',
    cancel_reason        VARCHAR(100)    NULL COMMENT '系统撤销原因：提交人注销、养殖场已删除',
    version              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by           BIGINT UNSIGNED NULL COMMENT '创建人 ID，系统为 0',
    created_at           DATETIME        NOT NULL COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_change_request_pending (biz_type, biz_id, pending_flag),
    UNIQUE KEY uk_change_request_no (request_no),
    KEY idx_change_request_status_submitted (status, submitted_at),
    KEY idx_change_request_farm_status (farm_id, status),
    KEY idx_change_request_submitter (submitted_by, status, submitted_at),
    KEY idx_change_request_batch (batch_id),
    KEY idx_change_request_module_status (source_module, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='修改申请（全部模块共用）';

CREATE TABLE t_operation_log
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志 ID',
    operator_type     VARCHAR(20)     NOT NULL COMMENT '操作人类型：ADMIN 管理员 / USER 用户 / SYSTEM 系统',
    operator_id       BIGINT UNSIGNED NULL COMMENT '管理员或用户 ID；登录失败且账号不存在时为空',
    operator_name     VARCHAR(32)     NULL COMMENT '操作人姓名快照，例如 吴昊',
    operator_role     VARCHAR(100)    NULL COMMENT '操作人角色快照：管理员为角色编码（多个用逗号分隔），用户端为 OWNER / MANAGER / STAFF',
    op_type           VARCHAR(20)     NOT NULL COMMENT '操作类别，例如 FARM_AUDIT；用户端修改记录为 RECORD_EDIT',
    op_action         VARCHAR(20)     NOT NULL COMMENT '动作，例如 APPROVE、REJECT、UPDATE',
    result            VARCHAR(20)     NOT NULL COMMENT '结果：SUCCESS 成功 / FAIL 失败',
    biz_type          VARCHAR(30)     NULL COMMENT '被操作记录类型（BIZ_TYPE）',
    biz_id            BIGINT UNSIGNED NULL COMMENT '被操作记录 ID',
    target_name       VARCHAR(200)    NULL COMMENT '对象描述快照，手机号写入时即打码',
    farm_id           BIGINT UNSIGNED NULL COMMENT '养殖场 ID，便于按养殖场检索',
    search_text       VARCHAR(255)    NULL COMMENT '检索词：养殖场名、账号姓名、批次号',
    reason            VARCHAR(500)    NULL COMMENT '原因原文（驳回、停用、冻结、删除等必填）',
    extra_text        VARCHAR(500)    NULL COMMENT '补充说明',
    notify_text       VARCHAR(500)    NULL COMMENT '已发送的通知摘要',
    before_json       JSON            NULL COMMENT '变更前的字段值',
    after_json        JSON            NULL COMMENT '变更后的字段值，用于显示“140 千克 → 160 千克”',
    change_request_id BIGINT UNSIGNED NULL COMMENT '因修改申请通过而改写记录时关联的申请 ID',
    client_type       VARCHAR(20)     NULL COMMENT '客户端：APP_ANDROID / WEB / ADMIN_WEB',
    user_agent        VARCHAR(500)    NULL COMMENT '客户端 User-Agent',
    ip                VARCHAR(45)     NULL COMMENT '客户端 IP（兼容 IPv6）',
    ip_region         VARCHAR(64)     NULL COMMENT 'IP 属地',
    created_at        DATETIME        NOT NULL COMMENT '操作时间（日志只追加，不修改、不删除）',
    PRIMARY KEY (id),
    KEY idx_operation_log_operator_type (operator_type, created_at),
    KEY idx_operation_log_operator (operator_id, created_at),
    KEY idx_operation_log_op_type (op_type, created_at),
    KEY idx_operation_log_biz (biz_type, biz_id, created_at),
    KEY idx_operation_log_farm (farm_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作留痕（管理端操作日志与用户端修改记录，用 operator_type 区分）；保留 3 年，生产环境可按月分区';
