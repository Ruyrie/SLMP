-- 模块08 免疫、用药与消毒（服务端技术准备文档 2.3 节、05 号数据库设计）
-- 8 张表：t_immune_plan、t_immune_record、t_medication_record、t_batch_withdrawal、
--         t_disinfect_plan、t_disinfect_plan_pen、t_disinfect_task、t_disinfect_record
-- 不建外键（关联只靠 ID 与索引）；引擎 InnoDB；字符集 utf8mb4 / utf8mb4_0900_ai_ci。
-- 养殖场表均带 farm_id，多租户插件按 FarmContext 自动过滤；t_batch_withdrawal.batch_id 为全库唯一。
-- 公共字段：created_at / updated_at 由框架自动填充；created_by 由框架填当前用户，系统生成为 0。
-- 剂量 DECIMAL(10,3)（禽用疫苗每羽份常不足 0.01 元，单价用 DECIMAL(12,4)）；金额 DECIMAL(12,2)。
-- 已执行的脚本不再修改；结构变更另起 V1_08_1、V1_08_2…
-- 平台基准数据依赖：t_immune_template_item（V1_23_1）、t_drug_catalog / t_drug_catalog_usage（V1_23_1），
-- 本场档案：t_drug、t_drug_lot（V1_09）。

-- 2.3.1 免疫计划项目（按模板生成或手工添加；批次项与种畜栏舍项共用本表）
CREATE TABLE t_immune_plan
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '免疫计划项目 ID，例如 3021',
    farm_id               BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    target_type           VARCHAR(20)     NOT NULL DEFAULT 'BATCH' COMMENT '免疫对象（IMMUNE_TARGET）：BATCH 批次 / BREEDING_PEN 种畜栏舍',
    batch_id              BIGINT UNSIGNED NULL COMMENT '批次 ID（t_batch）；种畜栏舍项为空',
    pen_id                BIGINT UNSIGNED NULL COMMENT '栏舍 ID（t_pen）；种畜栏舍项必填，批次项为空（栏舍按 t_batch_pen 分布取）',
    source                VARCHAR(20)     NOT NULL COMMENT '来源（PLAN_SOURCE）：TEMPLATE 模板生成 / MANUAL 手工添加',
    pre_entry             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '入栏前项目：1 = 入栏时日龄已超过的模板项目',
    template_id           BIGINT UNSIGNED NULL COMMENT '模板 ID（t_immune_template）',
    template_version      INT             NULL COMMENT '模板版本号（t_immune_template_version.version_no）',
    template_item_id      BIGINT UNSIGNED NULL COMMENT '模板项目 ID（t_immune_template_item）',
    catalog_id            BIGINT UNSIGNED NOT NULL COMMENT '疫苗目录 ID（t_drug_catalog，drug_category = VACCINE）',
    drug_id               BIGINT UNSIGNED NULL COMMENT '本场疫苗档案 ID（t_drug）；生成时按目录匹配，未建档为空',
    vaccine_name          VARCHAR(50)     NOT NULL COMMENT '疫苗名称快照，例如 猪瘟活疫苗',
    immune_round          VARCHAR(20)     NULL COMMENT '免次快照，取模板 dose_round，例如 加强免疫；手工添加选填',
    plan_mode             VARCHAR(20)     NOT NULL COMMENT '计划方式（PLAN_MODE）：BY_DAY_AGE 按日龄 / BY_DATE 按日期',
    plan_age_day          INT             NULL COMMENT '计划日龄，例如 96；按日期时为空',
    original_plan_date    DATE            NOT NULL COMMENT '首次生成的计划日期（不随改期变化），例如 2026-10-19',
    plan_date             DATE            NOT NULL COMMENT '当前计划日期，改期后为新日期',
    due_time              TIME            NOT NULL DEFAULT '18:00:00' COMMENT '截止时刻，默认 18:00:00，可按项目修改',
    dose                  DECIMAL(10, 3)  NOT NULL COMMENT '接种剂量，> 0，例如 1.000',
    dose_unit             VARCHAR(20)     NOT NULL COMMENT '剂量单位（VACCINE_DOSE_UNIT）：DOSE_PER_HEAD / ML_PER_HEAD / DOSE_PER_BIRD',
    method_code           VARCHAR(20)     NOT NULL COMMENT '接种方式（字典 VACCINATE_METHOD），例如 IM 肌肉注射',
    status                VARCHAR(20)     NOT NULL COMMENT '计划状态（IMMUNE_PLAN_STATUS）：PRE_ENTRY / PRE_ENTRY_DONE / PENDING / POSTPONED / MISSED / DONE / CANCELLED',
    postponed             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否延期过：1 是',
    postpone_from_date    DATE            NULL COMMENT '改期前的计划日期，例如 2026-10-26',
    postpone_reason       VARCHAR(50)     NULL COMMENT '延期原因，例如 部分猪只发热，暂缓接种',
    postponed_by          BIGINT UNSIGNED NULL COMMENT '改期操作人用户 ID，例如 刘芳',
    postponed_at          DATETIME        NULL COMMENT '改期时间',
    record_id             BIGINT UNSIGNED NULL COMMENT '有效接种记录 ID（t_immune_record）',
    manual_done           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '待办被手动标记完成、尚无接种记录：1 是',
    pre_entry_handled_by  BIGINT UNSIGNED NULL COMMENT '入栏前项目确认 / 加入的操作人用户 ID（撤销时限判断）',
    pre_entry_handled_at  DATETIME        NULL COMMENT '入栏前项目处理时间（10 分钟内可撤销）',
    missed_at             DATETIME        NULL COMMENT '标记漏免的时间（防疫日常写入）',
    missed_notified       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '漏免消息是否已发送：1 是',
    cancel_type           VARCHAR(20)     NULL COMMENT '取消方式（PLAN_CANCEL_TYPE）：USER_DELETED / BATCH_ENDED / PEN_EMPTY',
    cancelled_by          BIGINT UNSIGNED NULL COMMENT '取消操作人用户 ID；系统取消为 0',
    cancelled_at          DATETIME        NULL COMMENT '取消时间',
    version               INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：防止改期与登记并发',
    created_by            BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，模板生成时为 0（系统）',
    created_at            DATETIME        NOT NULL COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_immune_plan_batch_item (batch_id, template_item_id, original_plan_date),
    UNIQUE KEY uk_immune_plan_pen_catalog (pen_id, catalog_id, original_plan_date),
    KEY idx_immune_plan_batch (farm_id, batch_id, status, plan_date),
    KEY idx_immune_plan_pen (farm_id, pen_id, status, plan_date),
    KEY idx_immune_plan_status_date (farm_id, status, plan_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='免疫计划项目（模块08，按模板生成或手工添加；入栏前项目也在此表）';

-- 2.3.2 接种记录（每计划项目最多一条有效记录；作废置 deleted = 1）
CREATE TABLE t_immune_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '接种记录 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    batch_id          BIGINT UNSIGNED NULL COMMENT '批次 ID（t_batch）；种畜栏舍接种为空',
    pen_id            BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen）：批次主栏舍或种畜栏舍，例如 3 号育肥舍',
    plan_id           BIGINT UNSIGNED NOT NULL COMMENT '对应计划项目 ID（t_immune_plan）',
    drug_id           BIGINT UNSIGNED NOT NULL COMMENT '本场疫苗档案 ID（t_drug）',
    vaccine_name      VARCHAR(50)     NOT NULL COMMENT '疫苗名称快照，例如 猪瘟活疫苗',
    drug_lot_id       BIGINT UNSIGNED NOT NULL COMMENT '批号 ID（t_drug_lot）',
    lot_no            VARCHAR(50)     NOT NULL COMMENT '生产批号快照，例如 2025081',
    lot_expire_date   DATE            NOT NULL COMMENT '批号有效期至快照，例如 2026-12-30',
    dose              DECIMAL(10, 3)  NOT NULL COMMENT '接种剂量，> 0，例如 1.000',
    dose_unit         VARCHAR(20)     NOT NULL COMMENT '剂量单位（VACCINE_DOSE_UNIT）',
    quantity          INT             NOT NULL COMMENT '接种头数，例如 200',
    total_dose        DECIMAL(12, 3)  NOT NULL COMMENT '扣减量 = 剂量 × 头数（记账单位），例如 200.000 头份',
    method_code       VARCHAR(20)     NOT NULL COMMENT '接种方式（字典 VACCINATE_METHOD），例如 IM',
    vaccinate_date    DATE            NOT NULL COMMENT '接种日期（北京时间），例如 2026-10-19',
    executor_id       BIGINT UNSIGNED NOT NULL COMMENT '实际执行人用户 ID，例如 李明；员工登记为本人',
    backfill          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '补免：登记时计划为漏免则 1',
    unit_price        DECIMAL(12, 4)  NULL COMMENT '批号均价快照（元/记账单位，管理视图）',
    cost_amount       DECIMAL(12, 2)  NULL COMMENT '金额（元，管理视图）；批次项计入批次成本，种畜栏舍项只记金额',
    stock_flow_id     BIGINT UNSIGNED NULL COMMENT '模块09 出库流水 ID（t_stock_flow）',
    client_request_id VARCHAR(64)     NOT NULL COMMENT '请求编号（取 Idempotency-Key），数据库唯一索引兜底防重',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：作废置 1',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID，例如 王建国',
    created_at        DATETIME        NOT NULL COMMENT '创建时间（登记时间）',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_immune_rec_client_req (farm_id, client_request_id),
    KEY idx_immune_rec_batch (farm_id, batch_id, vaccinate_date),
    KEY idx_immune_rec_pen (farm_id, pen_id, vaccinate_date),
    KEY idx_immune_rec_plan (plan_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='接种记录（模块08，同一计划只有一条有效记录）';

-- 2.3.3 用药记录（每个批次一行；按栏舍登记时同一次登记生成多条、共用 group_no）
CREATE TABLE t_medication_record
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用药记录 ID，例如 5101',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    group_no            VARCHAR(32)     NOT NULL COMMENT '登记组号：同一次登记共用，例如 MG2610190003',
    target_type         VARCHAR(20)     NOT NULL COMMENT '用药对象（MEDICATION_TARGET）：BATCH 批次 / PEN 栏舍',
    pen_id              BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen），例如 1 号保育舍',
    batch_id            BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch），例如 ZM2610-045',
    catalog_id          BIGINT UNSIGNED NOT NULL COMMENT '药品目录 ID（t_drug_catalog）',
    drug_id             BIGINT UNSIGNED NOT NULL COMMENT '本场药品档案 ID（t_drug）',
    drug_name           VARCHAR(50)     NOT NULL COMMENT '药品名称快照，例如 阿莫西林可溶性粉',
    drug_lot_id         BIGINT UNSIGNED NOT NULL COMMENT '批号 ID（t_drug_lot）',
    lot_no              VARCHAR(50)     NOT NULL COMMENT '生产批号快照，例如 2025091',
    lot_expire_date     DATE            NOT NULL COMMENT '批号有效期至快照，例如 2027-08-31',
    dose                DECIMAL(10, 3)  NOT NULL COMMENT '用药剂量，> 0，例如 0.400',
    dose_unit           VARCHAR(20)     NOT NULL COMMENT '剂量单位（DRUG_DOSE_UNIT），例如 G_PER_L_WATER 克/升水',
    recommended_dose    DECIMAL(10, 3)  NULL COMMENT '推荐剂量快照，例如 0.300；目录未配置为空',
    route_code          VARCHAR(20)     NULL COMMENT '给药方式（ADMIN_ROUTE）：MIX_FEED 混饲 / MIX_WATER 混饮 等，取目录该畜种给药途径',
    course_days         INT             NOT NULL COMMENT '疗程天数，例如 5',
    first_date          DATE            NOT NULL COMMENT '首次用药日期，例如 2026-10-19',
    last_date           DATE            NOT NULL COMMENT '末次用药日期 = 首次 + 疗程 - 1，例如 2026-10-23',
    withdrawal_days     INT             NOT NULL COMMENT '休药天数，例如 14；目录修改后批量更新',
    withdrawal_end_date DATE            NOT NULL COMMENT '休药结束日期 = 末次用药 + 休药天数，例如 2026-11-06',
    over_dose           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '超推荐剂量：1 是（不拦截，须填说明）',
    over_dose_note      VARCHAR(100)    NULL COMMENT '超量说明，例如 腹泻较重，按执业兽医处方加量',
    category_mismatch   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '品类不符：1 = 目录对该畜种 applicable = 0',
    usage_note          VARCHAR(100)    NULL COMMENT '品类不符时的用药说明',
    reason              VARCHAR(100)    NOT NULL COMMENT '用药原因，例如 腹泻，防继发感染',
    estimated_qty       DECIMAL(12, 3)  NULL COMMENT '后端预计用量（补充）；按水或按料计量无法推算时为空',
    used_qty            DECIMAL(12, 3)  NOT NULL COMMENT '实际用量（记账单位，必填），例如 800.000 克；按栏舍时按在栏数分摊',
    used_unit           VARCHAR(10)     NOT NULL COMMENT '实际用量单位（药品记账单位），例如 G',
    report_id           BIGINT UNSIGNED NULL COMMENT '异常上报 ID（t_abnormal_report，模块07）',
    diagnosis_id        BIGINT UNSIGNED NULL COMMENT 'AI 诊断 ID（t_ai_diagnosis，模块07）',
    executor_id         BIGINT UNSIGNED NOT NULL COMMENT '实际执行人用户 ID，例如 张伟',
    unit_price          DECIMAL(12, 4)  NULL COMMENT '批号均价快照（元/记账单位，管理视图）',
    cost_amount         DECIMAL(12, 2)  NULL COMMENT '金额（元，管理视图）',
    stock_flow_id       BIGINT UNSIGNED NULL COMMENT '模块09 出库流水 ID（t_stock_flow）',
    client_request_id   VARCHAR(64)     NOT NULL COMMENT '请求编号（幂等键，同组共用）',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：1 已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          BIGINT UNSIGNED NULL COMMENT '登记人用户 ID，例如 王建国',
    created_at          DATETIME        NOT NULL COMMENT '创建时间（登记时间）',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_med_rec_client_req (farm_id, client_request_id, batch_id),
    KEY idx_med_rec_batch (farm_id, batch_id, deleted, first_date),
    KEY idx_med_rec_catalog (farm_id, catalog_id, deleted),
    KEY idx_med_rec_report (report_id),
    KEY idx_med_rec_group (group_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用药记录（模块08，每批次一行；按栏舍登记同组多条）';

-- 2.3.4 批次休药汇总（每批次一行；可出栏日期 = 有效用药记录 withdrawal_end_date 最大值）
CREATE TABLE t_batch_withdrawal
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '休药汇总 ID',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    batch_id            BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    release_date        DATE            NULL COMMENT '可出栏日期，例如 2026-10-29；无有效用药记录为空',
    decisive_record_id  BIGINT UNSIGNED NULL COMMENT '决定可出栏日期的用药记录 ID（t_medication_record）',
    decisive_drug_name  VARCHAR(50)     NULL COMMENT '决定项药品名称快照，例如 氟苯尼考粉',
    last_med_date       DATE            NULL COMMENT '决定项末次用药日期，例如 2026-10-09',
    withdrawal_days     INT             NULL COMMENT '决定项休药天数，例如 20',
    cycle_no            INT             NOT NULL DEFAULT 0 COMMENT '进入休药期的次数（补充）：用于休药期到期待办去重键',
    recalculated_at     DATETIME        NOT NULL COMMENT '最近重算时间（重算前加锁读取该行，保证并发登记正确）',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_withdrawal_batch (batch_id),
    KEY idx_withdrawal_farm_release (farm_id, release_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次休药汇总（模块08，供 L01/L03/T01/E12 与出栏校验读取）';

-- 2.3.5 消毒计划（不提供删除，不设 deleted）
CREATE TABLE t_disinfect_plan
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消毒计划 ID，例如 61',
    farm_id            BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    cycle_days         INT             NOT NULL COMMENT '执行周期天数，例如 7',
    start_date         DATE            NOT NULL COMMENT '开始日期，例如 2026-10-20',
    end_date           DATE            NOT NULL COMMENT '结束日期，例如 2026-12-31',
    due_time           TIME            NOT NULL DEFAULT '17:00:00' COMMENT '截止时刻，默认 17:00:00',
    catalog_id         BIGINT UNSIGNED NOT NULL COMMENT '消毒剂目录 ID（t_drug_catalog，drug_category = DISINFECTANT）',
    drug_id            BIGINT UNSIGNED NULL COMMENT '本场消毒剂档案 ID（t_drug）；未建档为空',
    disinfectant_name  VARCHAR(50)     NOT NULL COMMENT '消毒剂名称快照，例如 聚维酮碘溶液',
    concentration      VARCHAR(20)     NOT NULL COMMENT '计划浓度，例如 1:500',
    method_code        VARCHAR(20)     NOT NULL COMMENT '消毒方式（字典 DISINFECT_METHOD），例如 SPRAY 喷雾',
    status             VARCHAR(20)     NOT NULL COMMENT '计划状态（DISINFECT_PLAN_STATUS）：ACTIVE 启用中 / PAUSED 已暂停 / ENDED 已结束',
    pause_reason       VARCHAR(30)     NULL COMMENT '暂停原因，例如 10月13日起空栏',
    paused_by          BIGINT UNSIGNED NULL COMMENT '暂停人用户 ID，例如 刘芳',
    paused_at          DATETIME        NULL COMMENT '暂停时间',
    resumed_at         DATETIME        NULL COMMENT '最近启用时间',
    generated_until    DATE            NULL COMMENT '已生成任务的最晚日期（补充），滚动补齐的起点',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_disinfect_plan_status (farm_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消毒计划（模块08，不提供删除）';

-- 2.3.6 消毒计划覆盖栏舍（修改计划时整体替换）
CREATE TABLE t_disinfect_plan_pen
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '计划覆盖栏舍 ID',
    farm_id    BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    plan_id    BIGINT UNSIGNED NOT NULL COMMENT '消毒计划 ID（t_disinfect_plan）',
    pen_id     BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen），例如 母猪舍、产房',
    created_by BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_disinfect_plan_pen (plan_id, pen_id),
    KEY idx_disinfect_plan_pen_farm (farm_id, pen_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消毒计划覆盖栏舍（模块08，修改计划时整体替换）';

-- 2.3.7 消毒任务（每栏舍每次一行；取消后 active_flag 置空配合唯一索引）
CREATE TABLE t_disinfect_task
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消毒任务 ID，例如 9031',
    farm_id            BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    pen_id             BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen），例如 2 号保育舍',
    plan_id            BIGINT UNSIGNED NULL COMMENT '消毒计划 ID（t_disinfect_plan）；管理员下发的紧急任务为空',
    source             VARCHAR(20)     NOT NULL COMMENT '任务来源（DISINFECT_TASK_SOURCE）：PLAN / ADMIN_EMERGENCY（保留，无接口）',
    task_date          DATE            NOT NULL COMMENT '任务日期，例如 2026-10-18',
    due_time           TIME            NOT NULL COMMENT '截止时刻快照，默认 17:00:00',
    catalog_id         BIGINT UNSIGNED NOT NULL COMMENT '消毒剂目录 ID 快照（t_drug_catalog）',
    disinfectant_name  VARCHAR(50)     NOT NULL COMMENT '消毒剂名称快照：聚维酮碘溶液',
    concentration      VARCHAR(20)     NOT NULL COMMENT '计划浓度快照：1:500',
    method_code        VARCHAR(20)     NOT NULL COMMENT '消毒方式快照（字典 DISINFECT_METHOD）：SPRAY 喷雾',
    status             VARCHAR(20)     NOT NULL COMMENT '任务状态（DISINFECT_TASK_STATUS）：PENDING / OVERDUE / DONE / PAUSED / CANCELLED',
    active_flag        TINYINT(1)      NULL COMMENT '未取消为 1，取消后置空，配合唯一索引防重复生成',
    record_id          BIGINT UNSIGNED NULL COMMENT '消毒记录 ID（t_disinfect_record）',
    manual_done        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '待办被手动标记完成、无消毒记录：1 是',
    overdue_at         DATETIME        NULL COMMENT '标记逾期时间（防疫日常写入）',
    overdue_notified   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逾期消息是否已发送：1 是',
    cancelled_at       DATETIME        NULL COMMENT '取消时间',
    cancel_reason      VARCHAR(50)     NULL COMMENT '取消原因，例如 计划修改 / 栏舍停用',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人用户 ID；系统生成的任务为 0',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_disinfect_task_slot (plan_id, pen_id, task_date, active_flag),
    KEY idx_disinfect_task_status (farm_id, status, task_date),
    KEY idx_disinfect_task_pen (farm_id, pen_id, task_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消毒任务（模块08，每栏舍每次一行；执行人不落库，按栏舍负责员工显示）';

-- 2.3.8 消毒记录（计划任务 / 空栏消毒 / 紧急消毒；照片在 t_biz_media，biz_type = DISINFECT_RECORD）
CREATE TABLE t_disinfect_record
(
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消毒记录 ID，例如 7201',
    farm_id                 BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    pen_id                  BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen），例如 3 号育肥舍',
    record_type             VARCHAR(20)     NOT NULL COMMENT '记录类型（DISINFECT_RECORD_TYPE）：PLAN_TASK 计划任务 / EMPTY_PEN 空栏消毒 / EMERGENCY 紧急消毒',
    task_id                 BIGINT UNSIGNED NULL COMMENT '计划任务 ID（t_disinfect_task）；计划任务时非空',
    plan_concentration      VARCHAR(20)     NULL COMMENT '计划浓度快照，例如 1:300',
    actual_time             DATETIME        NOT NULL COMMENT '实际消毒时间，例如 2026-10-19 09:10:00',
    executor_id             BIGINT UNSIGNED NOT NULL COMMENT '实际执行人用户 ID，例如 李明',
    drug_id                 BIGINT UNSIGNED NOT NULL COMMENT '本场消毒剂档案 ID（t_drug）',
    disinfectant_name       VARCHAR(50)     NOT NULL COMMENT '消毒剂名称快照：聚维酮碘溶液',
    drug_lot_id             BIGINT UNSIGNED NOT NULL COMMENT '批号 ID（t_drug_lot）',
    lot_no                  VARCHAR(50)     NOT NULL COMMENT '生产批号快照，例如 2025033',
    usage_qty               DECIMAL(12, 3)  NOT NULL COMMENT '原液用量（记账单位），例如 0.500 升',
    usage_unit              VARCHAR(10)     NOT NULL COMMENT '用量单位（记账单位），例如 L',
    concentration           VARCHAR(20)     NOT NULL COMMENT '实际浓度，例如 1:200',
    concentration_confirmed TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '与计划浓度不一致时已确认：1 是',
    method_code             VARCHAR(20)     NOT NULL COMMENT '消毒方式（字典 DISINFECT_METHOD），例如 SPRAY',
    backfill                TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逾期补录：1 是',
    in_batch_cost           TINYINT(1)      NOT NULL COMMENT '是否计入批次成本：计划任务、紧急消毒为 1，空栏消毒为 0',
    in_exec_rate            TINYINT(1)      NOT NULL COMMENT '是否计入执行率：只有计划任务为 1',
    unit_price              DECIMAL(12, 4)  NULL COMMENT '批号均价快照（元/记账单位，管理视图）',
    cost_amount             DECIMAL(12, 2)  NULL COMMENT '金额（元，管理视图）',
    stock_flow_id           BIGINT UNSIGNED NULL COMMENT '模块09 出库流水 ID（t_stock_flow）',
    client_request_id       VARCHAR(64)     NOT NULL COMMENT '请求编号（幂等键）',
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：作废置 1',
    version                 INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by              BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    created_at              DATETIME        NOT NULL COMMENT '创建时间',
    updated_at              DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_disinfect_rec_client_req (farm_id, client_request_id),
    KEY idx_disinfect_rec_pen (farm_id, pen_id, actual_time),
    KEY idx_disinfect_rec_task (task_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消毒记录（模块08；消毒剂单独管理，保存时不扣药品库存）';
