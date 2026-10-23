-- 模块06 饲喂与生长称重（模块06 服务端技术准备文档 2.3 节）
-- 9 张表：饲喂方案、方案阶段、阶段餐次、饲喂任务、投喂记录、称重记录、逐头体重、批次生长汇总/称重周期、执行汇总日聚合。
-- 不建外键；字符集 utf8mb4 / utf8mb4_0900_ai_ci；养殖场表均带 farm_id，多租户插件按 FarmContext 自动过滤。
-- 公共字段：created_at / updated_at 由框架自动填充；created_by 由框架填当前用户（系统为 0）。
-- 已执行的脚本不再修改；结构变更另起 V1_06_1、V1_06_2…
-- 投喂记录不做逻辑删除：status 只有 NORMAL → VOID，作废后 active_task_id 置空，保证一餐只有一条有效记录。
-- 称重记录不提供删除；同一批次同一称重日期只能一条，修改走业务接口。
-- 模块09 尚未落地：t_feed_record.stock_flow_id 允许为空（FeedStockPort 当前为 no-op 实现，接入后回写）。

-- 2.3.1 饲喂方案
CREATE TABLE t_feed_plan
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '方案 ID，也是修改记录的 biz_id（biz_type = FEED_PLAN）',
    farm_id            BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    batch_id           BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）；每批次一份',
    breed_id           BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（t_breed）；建方案时快照',
    std_version_id     BIGINT UNSIGNED NULL COMMENT '最近一次套用的饲喂标准版本 ID（t_feed_std_version）；与品种当前生效版本不同即“标准已更新”',
    std_applied_at     DATETIME        NULL COMMENT '最近一次套用标准的时间',
    std_applied_by     BIGINT UNSIGNED NULL COMMENT '最近一次套用标准的用户 ID',
    abnormal_threshold DECIMAL(5, 4)   NOT NULL DEFAULT 0.1000 COMMENT '投喂异常判定比例，0.0100–1.0000；默认取标准 deviation_rate，无标准时 0.1000',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（保存方案时比较）',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID，系统为 0',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_plan_batch (batch_id),
    KEY idx_feed_plan_farm (farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂方案（模块06）';

-- 2.3.2 方案生长阶段
CREATE TABLE t_feed_plan_stage
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '方案阶段 ID；任务中快照的 stage_id',
    farm_id            BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    plan_id            BIGINT UNSIGNED NOT NULL COMMENT '方案 ID（t_feed_plan）',
    batch_id           BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（冗余，便于按批次查询）',
    sort_no            INT             NOT NULL COMMENT '阶段顺序，从 1 起，按日龄升序',
    stage_name         VARCHAR(20)     NOT NULL COMMENT '阶段名称，例如 育肥前期',
    day_age_start      INT             NOT NULL COMMENT '起始日龄，正整数',
    day_age_end        INT             NULL COMMENT '结束日龄；为空表示至出栏，只允许最后一段',
    feed_id            BIGINT UNSIGNED NOT NULL COMMENT '饲料 ID（模块09 t_feed）',
    daily_qty_per_head DECIMAL(10, 3)  NOT NULL COMMENT '每日每头投喂量（千克），大于 0',
    meals_per_day      TINYINT         NOT NULL COMMENT '每日餐次，1–4',
    std_stage_id       BIGINT UNSIGNED NULL COMMENT '来源标准阶段 ID（t_feed_std_stage）',
    std_feed_id        BIGINT UNSIGNED NULL COMMENT '套用标准时匹配到的本场饲料 ID',
    std_feed_name      VARCHAR(20)     NULL COMMENT '标准建议饲料名称快照，例如 育肥全价料',
    std_daily_qty      DECIMAL(10, 3)  NULL COMMENT '标准每日每头投喂量快照（千克）',
    std_meals_per_day  TINYINT         NULL COMMENT '标准每日餐次快照',
    std_meal_times     VARCHAR(30)     NULL COMMENT '标准投喂时刻快照，逗号分隔，例如 07:00,11:30,17:00',
    deleted            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已移除（保存时被移除的阶段，已生成任务仍可追溯）',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID，系统为 0',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_fps_plan (plan_id, deleted, sort_no),
    KEY idx_fps_batch (batch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂方案生长阶段（模块06）';

-- 2.3.3 阶段餐次
CREATE TABLE t_feed_plan_meal
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '阶段餐次 ID',
    farm_id    BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    plan_id    BIGINT UNSIGNED NOT NULL COMMENT '方案 ID（t_feed_plan）',
    stage_id   BIGINT UNSIGNED NOT NULL COMMENT '方案阶段 ID（t_feed_plan_stage）',
    meal_no    TINYINT         NOT NULL COMMENT '餐次序号，1–4，按投喂时间排序',
    meal_code  VARCHAR(20)     NOT NULL COMMENT '餐次编码（MEAL_CODE）：BREAKFAST / LUNCH / DINNER / EXTRA',
    meal_name  VARCHAR(10)     NOT NULL COMMENT '餐次名称：早餐 / 午餐 / 晚餐 / 加餐',
    feed_time  TIME            NOT NULL COMMENT '投喂时刻，例如 11:30:00',
    created_at DATETIME        NOT NULL COMMENT '创建时间',
    updated_at DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fpm_stage_no (stage_id, meal_no),
    UNIQUE KEY uk_fpm_stage_time (stage_id, feed_time),
    KEY idx_fpm_plan (plan_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂方案阶段餐次（模块06）';

-- 2.3.4 饲喂任务
CREATE TABLE t_feed_task
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '饲喂任务 ID，也是待办 biz_id（biz_type = FEED_TASK）',
    farm_id            BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id           BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    pen_id             BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen）',
    plan_id            BIGINT UNSIGNED NOT NULL COMMENT '生成时所用方案 ID',
    stage_id           BIGINT UNSIGNED NOT NULL COMMENT '生成时所用方案阶段 ID',
    task_date          DATE            NOT NULL COMMENT '任务日期（北京时间）',
    meal_no            TINYINT         NOT NULL COMMENT '餐次序号快照，1–4',
    meal_code          VARCHAR(20)     NOT NULL COMMENT '餐次编码快照（MEAL_CODE）',
    meal_name          VARCHAR(10)     NOT NULL COMMENT '餐次名称快照',
    feed_time          TIME            NOT NULL COMMENT '计划投喂时刻快照',
    feed_at            DATETIME        NOT NULL COMMENT '计划投喂时间（task_date + feed_time），提醒与逾期从此算',
    deadline_at        DATETIME        NOT NULL COMMENT '逾期时间 = feed_at + 2 小时',
    backfill_end_at    DATETIME        NOT NULL COMMENT '补登截止 = 任务日期次日 24:00',
    feed_id            BIGINT UNSIGNED NOT NULL COMMENT '计划饲料 ID（模块09 t_feed）',
    head_count         INT             NOT NULL COMMENT '该批次在该栏舍的在栏数快照',
    daily_qty_per_head DECIMAL(10, 3)  NOT NULL COMMENT '每日每头投喂量快照（千克）',
    meals_per_day      TINYINT         NOT NULL COMMENT '每日餐次快照，1–4',
    plan_qty           DECIMAL(10, 2)  NOT NULL COMMENT '本餐计划量（千克）= 每头日投喂量 × 在栏数 ÷ 餐次，四舍五入 2 位',
    assignee_id        BIGINT UNSIGNED NULL COMMENT '负责员工 ID（该栏舍第一位负责员工）；空为无负责员工',
    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '任务状态（FEED_TASK_STATUS）：PENDING / OVERDUE / DONE / MANUAL_DONE / IGNORED / MISSED / CANCELLED',
    record_id          BIGINT UNSIGNED NULL COMMENT '当前有效投喂记录 ID',
    todo_id            BIGINT UNSIGNED NULL COMMENT '模块03 待办 ID',
    overdue_notified   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已发 FEED_OVERDUE 消息：0 否，1 是',
    close_reason       VARCHAR(50)     NULL COMMENT '关闭原因：批次已结束 / 批次已转出该栏舍 / 超过补登期限 / 忽略原因摘要',
    handled_by         BIGINT UNSIGNED NULL COMMENT '手动完成或忽略的操作人用户 ID',
    handled_at         DATETIME        NULL COMMENT '手动完成或忽略的时间',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（防止两人同时登记）',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_task (batch_id, pen_id, task_date, meal_no),
    KEY idx_ft_farm_date (farm_id, task_date, status),
    KEY idx_ft_pen_date (pen_id, task_date),
    KEY idx_ft_deadline (status, deadline_at),
    KEY idx_ft_backfill (status, backfill_end_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂任务（模块06）';

-- 2.3.5 投喂记录
CREATE TABLE t_feed_record
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '投喂记录 ID',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id            BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    pen_id              BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen）',
    task_id             BIGINT UNSIGNED NOT NULL COMMENT '饲喂任务 ID',
    active_task_id      BIGINT UNSIGNED NULL COMMENT '有效记录时等于 task_id，作废时置空；唯一索引保证一餐只有一条有效记录',
    feed_date           DATE            NOT NULL COMMENT '投喂日期（任务日期）',
    meal_no             TINYINT         NOT NULL COMMENT '餐次序号快照，1–4',
    meal_name           VARCHAR(10)     NOT NULL COMMENT '餐次名称快照',
    feed_id             BIGINT UNSIGNED NOT NULL COMMENT '饲料 ID（模块09 t_feed）',
    plan_qty            DECIMAL(10, 2)  NOT NULL COMMENT '计划量快照（千克）',
    actual_qty          DECIMAL(10, 2)  NOT NULL COMMENT '实际投喂量（千克），大于 0',
    deviation_ratio     DECIMAL(6, 4)   NOT NULL COMMENT '偏差比例 =（实投 − 计划）÷ 计划，保留 4 位，例如 -0.0125',
    abnormal_type       VARCHAR(20)     NOT NULL COMMENT '投喂异常（ABNORMAL_TYPE）：NONE / UNDER / OVER',
    abnormal_threshold  DECIMAL(5, 4)   NOT NULL COMMENT '登记时异常判定阈值快照，例如 0.1000',
    executor_id         BIGINT UNSIGNED NOT NULL COMMENT '实际执行人用户 ID',
    is_proxy            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '登记人不是执行人：0 否，1 是',
    is_backfill         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '补登：登记时刻晚于任务 deadline_at 为 1',
    remark              VARCHAR(100)    NULL COMMENT '备注，最多 100 字',
    stock_flow_id       BIGINT UNSIGNED NULL COMMENT '模块09 FEED_USE 库存流水 ID；模块09 未接入时为空',
    status              VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '记录状态（RECORD_STATUS）：NORMAL 有效 / VOID 已作废',
    void_by             BIGINT UNSIGNED NULL COMMENT '作废人用户 ID',
    void_at             DATETIME        NULL COMMENT '作废时间',
    request_id          VARCHAR(64)     NOT NULL COMMENT '请求头 Idempotency-Key 的值',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by          BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at          DATETIME        NOT NULL COMMENT '创建时间（登记时间）',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fr_active_task (active_task_id),
    UNIQUE KEY uk_fr_request (created_by, request_id),
    KEY idx_fr_farm_date (farm_id, feed_date),
    KEY idx_fr_batch_date (batch_id, feed_date),
    KEY idx_fr_task (task_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='投喂记录（模块06）';

-- 2.3.6 称重记录
CREATE TABLE t_weigh_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '称重记录 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id          BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    weigh_date        DATE            NOT NULL COMMENT '称重日期；修改时不可改',
    day_age           INT             NOT NULL COMMENT '称重日日龄',
    stock_count       INT             NOT NULL COMMENT '称重日存栏快照（抽样上限）',
    mode              VARCHAR(20)     NOT NULL COMMENT '录入方式（WEIGH_MODE）：SAMPLE 抽样汇总 / INDIVIDUAL 逐头录入',
    sample_count      INT             NOT NULL COMMENT '抽样头数，≥ 1；逐头录入时为录入头数',
    total_weight      DECIMAL(10, 2)  NOT NULL COMMENT '总重（千克）；逐头录入为合计',
    avg_weight        DECIMAL(10, 2)  NOT NULL COMMENT '平均体重（千克）= 总重 ÷ 抽样头数',
    suggested_sample  INT             NOT NULL COMMENT '建议抽样数 = 称重日存栏 × 15%，向上取整',
    sample_low        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '抽样偏少：1 是（仍可保存）',
    std_version_id    BIGINT UNSIGNED NULL COMMENT '计算所用标准体重版本 ID（标准体重快照）',
    std_weight        DECIMAL(10, 2)  NULL COMMENT '标准体重（千克）；无标准或超出日龄点范围为空',
    allowed_deviation DECIMAL(5, 4)   NULL COMMENT '允许偏差快照，例如 0.1000',
    deviation_ratio   DECIMAL(6, 4)   NULL COMMENT '偏离比例 =（平均体重 − 标准体重）÷ 标准体重，保留 4 位',
    growth_result     VARCHAR(20)     NOT NULL COMMENT '生长结果（GROWTH_STATUS）：NORMAL / SLOW / FAST / NO_STD',
    gain_base_type    VARCHAR(20)     NOT NULL COMMENT '日增重基准（GAIN_BASE_TYPE）：PREV_WEIGH / INTAKE / NONE',
    gain_base_date    DATE            NULL COMMENT '日增重基准日期',
    gain_base_weight  DECIMAL(10, 2)  NULL COMMENT '日增重基准体重（千克）',
    daily_gain        DECIMAL(10, 2)  NULL COMMENT '日增重（千克/天），保留 2 位',
    request_id        VARCHAR(64)     NOT NULL COMMENT '请求头 Idempotency-Key 的值',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间（登记时间）',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wr_batch_date (batch_id, weigh_date),
    UNIQUE KEY uk_wr_request (created_by, request_id),
    KEY idx_wr_farm_date (farm_id, weigh_date),
    KEY idx_wr_std (std_version_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='称重记录（模块06）';

-- 2.3.7 逐头体重明细
CREATE TABLE t_weigh_record_item
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '逐头体重明细 ID',
    farm_id         BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    weigh_record_id BIGINT UNSIGNED NOT NULL COMMENT '称重记录 ID（t_weigh_record）',
    seq_no          INT             NOT NULL COMMENT '第几头，从 1 起',
    weight          DECIMAL(10, 2)  NOT NULL COMMENT '体重（千克），大于 0',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wri (weigh_record_id, seq_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='逐头体重明细（模块06）';

-- 2.3.8 批次生长汇总与称重周期
CREATE TABLE t_batch_growth
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次生长汇总 ID，也是称重待办 biz_id（biz_type = WEIGH_CYCLE）',
    farm_id               BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id              BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）；批次创建时建立',
    weigh_cycle_per_month TINYINT         NOT NULL DEFAULT 2 COMMENT '称重周期（次 / 月），1–8',
    cycle_customized      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '周期已手工修改：1 是（不再显示品种建议值，标准新版本不覆盖）',
    cycle_updated_by      BIGINT UNSIGNED NULL COMMENT '最近修改周期人用户 ID',
    cycle_updated_at      DATETIME        NULL COMMENT '最近修改周期时间',
    last_weigh_id         BIGINT UNSIGNED NULL COMMENT '按称重日期最新一条记录 ID',
    last_weigh_date       DATE            NULL COMMENT '最近称重日期',
    last_avg_weight       DECIMAL(10, 2)  NULL COMMENT '最近平均体重（千克）',
    last_deviation        DECIMAL(6, 4)   NULL COMMENT '最近偏离比例',
    last_daily_gain       DECIMAL(10, 2)  NULL COMMENT '最近日增重（千克/天）',
    growth_status         VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT '批次生长标记（GROWTH_STATUS）：NONE / NORMAL / SLOW / FAST / NO_STD',
    growth_status_at      DATETIME        NULL COMMENT '生长标记变化时间',
    next_weigh_date       DATE            NULL COMMENT '下次称重日期',
    weigh_todo_id         BIGINT UNSIGNED NULL COMMENT '当前未完成的 WEIGHING 待办 ID',
    std_recalc_at         DATETIME        NULL COMMENT '标准新版本重算时间；7 天内 G06 显示提示',
    version               INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（称重周期修改）',
    created_at            DATETIME        NOT NULL COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bg_batch (batch_id),
    KEY idx_bg_farm_next (farm_id, next_weigh_date),
    KEY idx_bg_farm_status (farm_id, growth_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次生长汇总与称重设置（模块06）';

-- 2.3.9 执行汇总日聚合（模块06 2.5.5 执行情况）
-- 汇总接口以该表为读模型：任务生成、投喂登记、修改、作废、逾期/未执行状态变化时按 (batch_id, stat_date) 增量刷新；
-- 保留一张表便于大范围统计和后续导出，明细仍以 t_feed_task / t_feed_record 为准。
CREATE TABLE t_feed_exec_daily
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '执行汇总日聚合 ID',
    farm_id        BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id       BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    stat_date      DATE            NOT NULL COMMENT '统计日期',
    planned_meals  INT             NOT NULL DEFAULT 0 COMMENT '应执行餐数（排除 CANCELLED、IGNORED）',
    done_meals     INT             NOT NULL DEFAULT 0 COMMENT '已执行餐数（DONE + MANUAL_DONE）',
    plan_qty       DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '计划量合计（千克）',
    actual_qty     DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '有效记录实投合计（千克）',
    abnormal_count INT             NOT NULL DEFAULT 0 COMMENT '投喂异常记录数（abnormal_type 不为 NONE）',
    refreshed_at   DATETIME        NULL COMMENT '最近刷新时间；为空表示尚未聚合',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fed_batch_date (batch_id, stat_date),
    KEY idx_fed_farm_date (farm_id, stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂执行汇总日聚合（模块06）';
