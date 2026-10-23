-- 模块07 日常巡检与异常上报 AI 诊断（服务端技术准备文档 2.3 节、05 号文档）
-- 9 张表：t_inspection_setting、t_inspection、t_inspection_item_result、t_abnormal_report、
--         t_abnormal_report_symptom、t_ai_diagnosis、t_ai_diagnosis_cause、t_ai_diagnosis_advice、t_diagnosis_handle
-- 不建外键；字符集 utf8mb4 / utf8mb4_0900_ai_ci；养殖场表均带 farm_id，多租户插件按 FarmContext 自动过滤。
-- 公共字段：created_at / updated_at 由框架自动填充；created_by 由框架填当前用户（系统为 0）。
-- 已执行的脚本不再修改；结构变更另起 V1_07_1、V1_07_2…

-- 2.3.1 巡检设置（每场一行；没有行时按 18:00 处理，首次保存时插入）
CREATE TABLE t_inspection_setting
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检设置 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm），每场一行',
    deadline_time     TIME            NOT NULL DEFAULT '18:00:00' COMMENT '巡检截止时刻，06:00–23:00 的整分钟',
    remind_before_min INT             NOT NULL DEFAULT 60 COMMENT '截止前提醒分钟数，固定 60，不开放修改',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_setting_farm (farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检设置（模块07，每场一行）';

-- 2.3.2 巡检记录（每栏舍每天一行，含未巡、漏巡占位行）
CREATE TABLE t_inspection
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检记录 ID；占位行生成时即有 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    pen_id            BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen）',
    batch_id          BIGINT UNSIGNED NULL COMMENT '栏舍只有一个在栏批次时为该批次；多个在栏批次时为空',
    inspect_date      DATE            NOT NULL COMMENT '巡检归属日期（北京时间）',
    inspect_status    VARCHAR(20)     NOT NULL COMMENT '巡检状态（INSPECT_STATUS）：PENDING / DONE / MISSED / CANCELLED',
    template_id       BIGINT UNSIGNED NULL COMMENT '登记时使用的巡检模板 ID（t_inspect_template）',
    template_version  INT             NULL COMMENT '登记时使用的模板版本号',
    stock_snapshot    INT             NULL COMMENT '登记时栏舍在栏数快照',
    registrant_id     BIGINT UNSIGNED NULL COMMENT '登记（补录）账号用户 ID；占位行为空',
    executor_id       BIGINT UNSIGNED NULL COMMENT '实际执行人用户 ID；员工登记时等于登记人',
    registered_at     DATETIME        NULL COMMENT '首次登记时间；修改不变；补录时为补录时间',
    client_request_id VARCHAR(64)     NULL COMMENT '首次登记或补录的请求编号（API-I25）',
    deadline_time     TIME            NULL COMMENT '登记时生效的截止时刻快照',
    is_overdue        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '超时：首次登记晚于巡检日期 + 截止时刻为 1',
    is_backfill       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '补录：1 是',
    location_status   VARCHAR(20)     NULL COMMENT '位置判定（LOCATION_STATUS）：ON_SITE / OFF_SITE / NO_LOCATION',
    latitude          DECIMAL(10, 7)  NULL COMMENT 'GCJ-02 纬度',
    longitude         DECIMAL(10, 7)  NULL COMMENT 'GCJ-02 经度',
    location_accuracy_m INT           NULL COMMENT '定位精度（米）',
    distance_m        INT             NULL COMMENT '距养殖场距离（米），球面距离取整',
    is_abnormal       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '检查结果异常或手动标记；1 是',
    abnormal_source   VARCHAR(20)     NULL COMMENT '异常来源（ABNORMAL_SOURCE）：CHECK 检查结果异常 / MANUAL 手动标记',
    abnormal_summary  VARCHAR(100)    NULL COMMENT '异常项拼接，例如“粪便 水样”；多项用顿号分隔',
    is_manual_marked  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '打开了“标记为异常”：1 是',
    manual_reason     VARCHAR(100)    NULL COMMENT '手动标记原因',
    manual_marked_by  BIGINT UNSIGNED NULL COMMENT '手动标记人用户 ID',
    manual_marked_at  DATETIME        NULL COMMENT '手动标记时间',
    remark            VARCHAR(200)    NULL COMMENT '巡检说明',
    report_id         BIGINT UNSIGNED NULL COMMENT '转上报后的上报 ID（t_abnormal_report）',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：只在批次被删除时级联置 1',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建人用户 ID；占位行为 0（系统）',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_pen_date (farm_id, pen_id, inspect_date),
    UNIQUE KEY uk_insp_client_req (farm_id, client_request_id),
    KEY idx_insp_farm_date_status (farm_id, inspect_date, inspect_status),
    KEY idx_insp_batch_date (batch_id, inspect_date),
    KEY idx_insp_status_date (inspect_status, inspect_date),
    KEY idx_insp_report (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检记录（模块07，每栏舍每天一行）';

-- 2.3.3 巡检项结果（修改时整组替换）
CREATE TABLE t_inspection_item_result
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检项结果 ID',
    farm_id         BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    inspection_id   BIGINT UNSIGNED NOT NULL COMMENT '巡检记录 ID（t_inspection）',
    item_id         BIGINT UNSIGNED NOT NULL COMMENT '模板项目 ID（t_inspect_item）',
    item_name       VARCHAR(20)     NOT NULL COMMENT '项目名称快照，例如 粪便',
    option_id       BIGINT UNSIGNED NOT NULL COMMENT '结果选项 ID（t_inspect_option）',
    option_name     VARCHAR(20)     NOT NULL COMMENT '结果名称快照，例如 水样',
    option_abnormal TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '结果是否判定异常：1 异常',
    sort_no         INT             NOT NULL COMMENT '按模板顺序',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insp_item (inspection_id, item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检项结果（模块07，名称快照）';

-- 2.3.4 异常上报（诊断主记录）
CREATE TABLE t_abnormal_report
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '异常上报 ID',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    pen_id              BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen）',
    batch_id            BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    inspection_id       BIGINT UNSIGNED NULL COMMENT '来源巡检 ID（t_inspection）',
    source              VARCHAR(20)     NOT NULL COMMENT '上报来源（REPORT_SOURCE）：INSPECTION 巡检转上报 / DIRECT 直接上报',
    client_request_id   VARCHAR(64)     NOT NULL COMMENT '请求编号（API-I26）',
    reporter_id         BIGINT UNSIGNED NOT NULL COMMENT '上报人用户 ID',
    reported_at         DATETIME        NOT NULL COMMENT '上报时间（北京时间）',
    report_date         DATE            NOT NULL COMMENT '上报日期（北京时间）',
    sick_count          INT             NOT NULL COMMENT '发病数量，≥ 1',
    stock_snapshot      INT             NOT NULL COMMENT '提交时该批次在该栏舍的在栏数',
    sick_rate           DECIMAL(5, 4)   NOT NULL COMMENT '发病率 = sick_count / stock_snapshot，四位小数',
    description         VARCHAR(500)    NULL COMMENT '病情描述',
    same_day_seq        INT             NOT NULL COMMENT '同批次同一北京日期第几次上报，从 1 起',
    status              VARCHAR(20)     NOT NULL COMMENT '上报状态（REPORT_STATUS）：PENDING / DIAGNOSED / HANDLED / CLOSED',
    ai_state            VARCHAR(20)     NOT NULL COMMENT 'AI 状态（AI_STATE）：DIAGNOSING / UNAVAILABLE / FAILED / DONE',
    auto_retry_used     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '已用过“服务恢复后自动重试一次”：1 是',
    unavailable_since   DATETIME        NULL COMMENT '进入暂不可用的时间',
    current_diagnosis_id BIGINT UNSIGNED NULL COMMENT '最近一次成功的诊断 ID（t_ai_diagnosis）',
    urgency             VARCHAR(20)     NULL COMMENT '冗余最终紧急程度（URGENCY）',
    is_major_epidemic   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '冗余：命中重大动物疫病',
    top_cause_name      VARCHAR(50)     NULL COMMENT '冗余：可能性最高的病因名称',
    notified_names      VARCHAR(200)    NULL COMMENT '最近一次诊断结果的通知名单，例如“李明、王建国、刘芳”',
    diagnosed_at        DATETIME        NULL COMMENT '最近一次诊断完成时间',
    handled_at          DATETIME        NULL COMMENT '首次登记处理结果时间',
    closed_at           DATETIME        NULL COMMENT '结案时间',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：批次删除时级联置 1',
    created_by          BIGINT UNSIGNED NULL COMMENT '创建人（上报人）用户 ID',
    updated_by          BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rpt_client_req (farm_id, client_request_id),
    UNIQUE KEY uk_rpt_inspection (inspection_id),
    KEY idx_rpt_farm_time (farm_id, reported_at),
    KEY idx_rpt_farm_reporter (farm_id, reporter_id, reported_at),
    KEY idx_rpt_batch_date (batch_id, report_date),
    KEY idx_rpt_ai_state (ai_state, auto_retry_used)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='异常上报（模块07，诊断主记录）';

-- 2.3.5 上报症状（名称快照）
CREATE TABLE t_abnormal_report_symptom
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '上报症状 ID',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    report_id    BIGINT UNSIGNED NOT NULL COMMENT '上报 ID（t_abnormal_report）',
    symptom_id   BIGINT UNSIGNED NOT NULL COMMENT '症状字典项 ID（t_dict_item，dict_type = SYMPTOM）',
    symptom_name VARCHAR(20)     NOT NULL COMMENT '症状名称快照，例如 腹泻',
    sort_no      INT             NOT NULL COMMENT '字典顺序',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rpt_sym (report_id, symptom_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='异常上报症状（模块07）';

-- 2.3.6 AI 诊断（每次调用一行）
CREATE TABLE t_ai_diagnosis
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'AI 诊断 ID',
    farm_id         BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    report_id       BIGINT UNSIGNED NOT NULL COMMENT '上报 ID（t_abnormal_report）',
    attempt_no      INT             NOT NULL COMMENT '同一上报第几次诊断，从 1 起',
    trigger_type    VARCHAR(20)     NOT NULL COMMENT '触发方式（AI_TRIGGER）：SUBMIT / AUTO_RETRY / MANUAL_RETRY',
    status          VARCHAR(20)     NOT NULL COMMENT '尝试状态（AI_ATTEMPT_STATUS）：RUNNING / SUCCESS / TIMEOUT / FAILED',
    requested_by    BIGINT UNSIGNED NULL COMMENT '手动重诊的用户 ID；提交与自动重试为空',
    requested_at    DATETIME        NOT NULL COMMENT '请求时间',
    finished_at     DATETIME        NULL COMMENT '完成时间',
    latency_ms      INT             NULL COMMENT '耗时（毫秒）',
    fail_reason     VARCHAR(30)     NULL COMMENT '失败原因（AI_FAIL_REASON）：TIMEOUT / SERVICE_UNAVAILABLE / INVALID_OUTPUT',
    model_name      VARCHAR(50)     NULL COMMENT '模型名称',
    prompt_version  VARCHAR(50)     NULL COMMENT '提示词版本',
    ai_urgency      VARCHAR(20)     NULL COMMENT '模型给出的紧急程度',
    urgency         VARCHAR(20)     NULL COMMENT '兜底后的最终紧急程度',
    urgency_basis   VARCHAR(20)     NULL COMMENT '紧急程度依据（URGENCY_BASIS）',
    conclusion      VARCHAR(200)    NULL COMMENT '结论句',
    is_major_epidemic TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '命中重大动物疫病：1 是',
    suggest_vet     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '建议联系执业兽医：1 是',
    input_snapshot  JSON            NULL COMMENT '发给模型的输入（不含姓名、手机号）',
    output_snapshot JSON            NULL COMMENT '模型原始输出',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_diag_attempt (report_id, attempt_no),
    KEY idx_diag_status_time (status, requested_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 诊断（模块07，每次调用一行）';

-- 2.3.7 可能病因
CREATE TABLE t_ai_diagnosis_cause
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '诊断病因 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    diagnosis_id      BIGINT UNSIGNED NOT NULL COMMENT '诊断 ID（t_ai_diagnosis）',
    report_id         BIGINT UNSIGNED NOT NULL COMMENT '上报 ID',
    rank_no           INT             NOT NULL COMMENT '排序，1–3',
    disease_name      VARCHAR(50)     NOT NULL COMMENT '疾病名称，例如 猪流行性腹泻',
    knowledge_id      BIGINT UNSIGNED NULL COMMENT '病症知识条目 ID',
    confidence        DECIMAL(5, 4)   NOT NULL DEFAULT 0 COMMENT '置信度，0.7200；不对用户显示数值',
    likelihood        VARCHAR(20)     NOT NULL COMMENT '可能性（LIKELIHOOD）：HIGH / MEDIUM / LOW',
    is_major_epidemic TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '按重大动物疫病标记复核后的结果',
    reason            VARCHAR(200)    NULL COMMENT '模型判断依据，存档不显示',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_diag_cause (diagnosis_id, rank_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 诊断可能病因（模块07）';

-- 2.3.8 处理建议
CREATE TABLE t_ai_diagnosis_advice
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '处理建议 ID',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    diagnosis_id BIGINT UNSIGNED NOT NULL COMMENT '诊断 ID（t_ai_diagnosis）',
    seq_no       INT             NOT NULL COMMENT '排序，1–6',
    advice_type  VARCHAR(20)     NULL COMMENT '建议类型（ADVICE_TYPE）：ISOLATE / MEDICATE / DISINFECT / LAB_TEST / OTHER',
    content      VARCHAR(100)    NOT NULL COMMENT '建议内容，只写药品类别不写剂量',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_diag_adv (diagnosis_id, seq_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 诊断处理建议（模块07）';

-- 2.3.9 处理结果（每条上报一行）
CREATE TABLE t_diagnosis_handle
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '处理结果 ID',
    farm_id       BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    report_id     BIGINT UNSIGNED NOT NULL COMMENT '上报 ID（t_abnormal_report），每条上报一行',
    diagnostician VARCHAR(20)     NOT NULL COMMENT '诊断人，例如“赵磊（执业兽医）”',
    treatment     VARCHAR(200)    NOT NULL COMMENT '处理方式',
    used_drug     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '使用药品：1 是',
    cured_count   INT             NOT NULL DEFAULT 0 COMMENT '治愈数',
    culled_count  INT             NOT NULL DEFAULT 0 COMMENT '死淘数',
    ai_match      VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'AI 诊断是否相符（AI_MATCH）：MATCH / NOT_MATCH / NONE',
    handled_by    BIGINT UNSIGNED NOT NULL COMMENT '首次登记人用户 ID',
    handled_at    DATETIME        NOT NULL COMMENT '首次登记时间',
    updated_by    BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by    BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_diag_handle_report (report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='诊断处理结果（模块07）';
