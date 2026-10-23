-- 管理端-03 基础数据（3B）：免疫程序模板、兽药疫苗目录、禁用药、批号停用通知（管理端-03 2.3.6–2.3.8 节）。
-- 9 张全局表（t_drug_lot_recall_farm、t_banned_drug_hit 带 farm_id，仅作归属记录，不参与多租户自动过滤）。
-- InnoDB、utf8mb4_0900_ai_ci、无外键。t_banned_drug_hit 只追加：无 updated_at/version，不继承 BaseEntity。
-- 已执行的脚本不再修改；结构变更另起 V1_23_2…（V2_05/06/07 为平台初始化数据，归 3B）。

CREATE TABLE t_immune_template
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '免疫程序模板 ID',
    breed_id           BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（t_breed），每个品种一份',
    name               VARCHAR(30)     NOT NULL COMMENT '模板名称，例如 杜长大育肥猪',
    online_version_no  INT             NULL COMMENT '线上版本号；从未发布为空',
    has_draft          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '有未发布修改：1 是',
    has_by_date        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '线上版本含按日期项目：1 是',
    copied_from_id     BIGINT UNSIGNED NULL COMMENT '复制来源模板 ID（t_immune_template）',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version            INT             NOT NULL DEFAULT 0 COMMENT '模板乐观锁版本号',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_immune_template_breed (breed_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='免疫程序模板（每个品种一份，按品种免疫程序生成批次计划）';

CREATE TABLE t_immune_template_version
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '免疫模板版本 ID',
    template_id   BIGINT UNSIGNED NOT NULL COMMENT '模板 ID（t_immune_template）',
    version_no    INT             NULL COMMENT '版本号，从 1 递增；草稿为空',
    status        VARCHAR(20)     NOT NULL COMMENT '版本状态（VERSION_STATUS）：DRAFT / PUBLISHED / SUPERSEDED',
    draft_flag    TINYINT(1)      NULL COMMENT '草稿为 1，其余为空；配合唯一索引保证最多一条草稿',
    change_note   VARCHAR(100)    NULL COMMENT '发布修改说明',
    item_count    INT             NOT NULL DEFAULT 0 COMMENT '本版项目数',
    published_by  BIGINT UNSIGNED NULL COMMENT '发布管理员 ID（t_admin）',
    published_at  DATETIME        NULL COMMENT '发布时间',
    created_by    BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by    BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（草稿比对）',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_itv_immune_template_no (template_id, version_no),
    UNIQUE KEY uk_itv_immune_template_draft (template_id, draft_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='免疫模板版本（草稿/线上/历史；已发布版本不再修改）';

CREATE TABLE t_immune_template_item
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '免疫模板项目 ID',
    template_id            BIGINT UNSIGNED NOT NULL COMMENT '模板 ID（t_immune_template）',
    version_id             BIGINT UNSIGNED NOT NULL COMMENT '版本 ID（t_immune_template_version）',
    source_item_id         BIGINT UNSIGNED NULL COMMENT '草稿项复制自线上哪一项，用于对比',
    change_type            VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT '变化（ITEM_CHANGE）：NONE / ADDED / MODIFIED / DELETED',
    sort_no                INT             NOT NULL COMMENT '同日龄按序号提醒',
    vaccine_id             BIGINT UNSIGNED NOT NULL COMMENT '疫苗目录 ID（t_drug_catalog，drug_category = VACCINE）',
    dose_round             VARCHAR(10)     NOT NULL COMMENT '免次，例如 首免、二免、加强',
    schedule_type          VARCHAR(20)     NOT NULL COMMENT '计划类型（SCHEDULE_TYPE）：BY_DAY_AGE / BY_DATE',
    day_age                INT             NULL COMMENT '按日龄必填，1–999',
    plan_month             TINYINT         NULL COMMENT '按日期必填，1–12',
    plan_day               TINYINT         NULL COMMENT '按日期必填，1–31（不允许 2 月 29 日）',
    advance_days           INT             NULL COMMENT '按日期必填，到期前多少天生成计划，1–60',
    dose                   DECIMAL(8, 2)   NOT NULL COMMENT '接种剂量，> 0',
    dose_unit              VARCHAR(20)     NOT NULL COMMENT '剂量单位（VACCINE_DOSE_UNIT）：DOSE_PER_HEAD / ML_PER_HEAD / DOSE_PER_BIRD',
    vaccinate_method_code  VARCHAR(32)     NOT NULL COMMENT '接种方式（字典 VACCINATE_METHOD）：IM 肌肉注射',
    remark                 VARCHAR(100)    NULL COMMENT '备注，例如 妊娠后期母猪顺延到产后 7 天接种',
    created_by             BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at             DATETIME        NOT NULL COMMENT '创建时间',
    updated_at             DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_immune_item_vaccine_round (version_id, vaccine_id, dose_round),
    KEY idx_immune_item_version_sort (version_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='免疫模板项目（按模板版本；已发布版本不再修改）';

CREATE TABLE t_drug_catalog
(
    id                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '兽药疫苗目录 ID，例如 7201 阿莫西林可溶性粉',
    drug_category                 VARCHAR(20)     NOT NULL COMMENT '类别（DRUG_CATEGORY）：VET_DRUG / VACCINE / DISINFECTANT；已有养殖场建档后不能改',
    name                          VARCHAR(40)     NOT NULL COMMENT '通用名称，例如 氟苯尼考粉；类别内唯一，不能与禁用药同名',
    alias                         VARCHAR(100)    NULL COMMENT '别名，顿号分隔，用户端搜索别名也能命中',
    dosage_form                   VARCHAR(20)     NOT NULL COMMENT '剂型，例如 粉剂、注射液、灭活疫苗',
    spec                          VARCHAR(20)     NOT NULL COMMENT '规格，例如 100 克/袋',
    measure_unit                  VARCHAR(10)     NOT NULL COMMENT '记账单位代码（MEASURE_UNIT）：G / ML / L / DOSE / KG；库存一律按它记账',
    pack_unit                     VARCHAR(10)     NOT NULL COMMENT '包装单位，例如 袋、瓶、桶',
    pack_size                     DECIMAL(10, 3)  NOT NULL COMMENT '每个包装折合的记账单位数，> 0；疫苗即每瓶头份',
    vaccine_dose_unit             VARCHAR(20)     NULL COMMENT '疫苗剂量单位（VACCINE_DOSE_UNIT）：DOSE_PER_HEAD / ML_PER_HEAD / DOSE_PER_BIRD；疫苗必填',
    default_vaccinate_method_code VARCHAR(32)     NULL COMMENT '疫苗默认接种方式（字典 VACCINATE_METHOD）',
    concentration                 VARCHAR(20)     NULL COMMENT '消毒剂推荐浓度，例如 1:500',
    disinfect_method_codes        VARCHAR(100)    NULL COMMENT '消毒剂适用消毒方式 code，逗号分隔',
    status                        VARCHAR(20)     NOT NULL COMMENT '启用状态（BASE_STATUS）：ENABLED / DISABLED',
    disable_reason                VARCHAR(100)    NULL COMMENT '停用原因，停用必填',
    banned                        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '已列入禁用药：1 是（读取时与 t_banned_drug 派生合并）',
    banned_drug_id                BIGINT UNSIGNED NULL COMMENT '禁用药 ID（t_banned_drug）',
    last_change_note              VARCHAR(100)    NULL COMMENT '最近一次修改说明，例如 补充鸡的休药期',
    created_by                    BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by                    BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version                       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at                    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at                    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_catalog_category_name (drug_category, name),
    KEY idx_drug_catalog_status (drug_category, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='兽药疫苗目录（平台维护；用户端 U11 只读搜索，养殖场档案可覆盖包装）';

CREATE TABLE t_drug_catalog_usage
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '目录按畜种用量 ID',
    catalog_id        BIGINT UNSIGNED NOT NULL COMMENT '目录 ID（t_drug_catalog）',
    species           VARCHAR(20)     NOT NULL COMMENT '畜种（SPECIES）：PIG / POULTRY / CATTLE / SHEEP',
    applicable        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '适用于该畜种：1 是；0 时用户端要求填写用药说明',
    admin_route       VARCHAR(20)     NULL COMMENT '给药途径（ADMIN_ROUTE）：MIX_FEED / MIX_WATER / IM / SC / IV / ORAL / EXTERNAL',
    recommended_dose  DECIMAL(10, 3)  NULL COMMENT '推荐剂量，> 0；适用于该畜种时必填',
    dose_unit         VARCHAR(20)     NULL COMMENT '剂量单位（DRUG_DOSE_UNIT）：G_PER_T_FEED / G_PER_L_WATER / ML_PER_10KG / MG_PER_KG / ML_PER_HEAD / G_PER_HEAD',
    course_days       INT             NULL COMMENT '建议疗程天数，1–60；适用于该畜种时必填',
    withdrawal_days   INT             NULL COMMENT '休药期天数，0–999；适用于该畜种时必填',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_usage_catalog_species (catalog_id, species)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='兽药疫苗目录按畜种用量与休药期（兽药、疫苗每畜种一行，消毒剂不设）';

CREATE TABLE t_banned_drug
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '禁用药 ID，例如 9901 盐酸克仑特罗',
    name            VARCHAR(40)     NOT NULL COMMENT '禁用药名称，全局唯一',
    alias           VARCHAR(100)    NULL COMMENT '别名，顿号分隔，例如 克伦特罗、瘦肉精',
    banned_class    VARCHAR(20)     NOT NULL COMMENT '禁用类别（BANNED_CLASS）：BETA_AGONIST / ANTIBACTERIAL / NITROFURAN / HORMONE / SEDATIVE / PESTICIDE / OTHER',
    catalog_id      BIGINT UNSIGNED NULL COMMENT '从目录标记时指向目录项（t_drug_catalog）',
    reason          VARCHAR(100)    NOT NULL COMMENT '列入原因，只给管理员看',
    listed_at       DATETIME        NOT NULL COMMENT '列入时间',
    listed_by       BIGINT UNSIGNED NOT NULL COMMENT '列入管理员 ID（t_admin），系统为 0',
    sort_no         INT             NOT NULL DEFAULT 0 COMMENT '管理端显示顺序',
    hit_count_30d   INT             NOT NULL DEFAULT 0 COMMENT '近 30 天命中次数；BaseStatJob 每小时刷新',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_banned_drug_name (name),
    KEY idx_banned_drug_class (banned_class, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='禁用药清单（平台维护；U11、模块08/09/11 按名称或别名命中拦截）';

CREATE TABLE t_banned_drug_hit
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '禁用药命中记录 ID（只追加，不修改）',
    banned_drug_id BIGINT UNSIGNED NOT NULL COMMENT '禁用药 ID（t_banned_drug）',
    hit_source     VARCHAR(20)     NOT NULL COMMENT '命中来源（BANNED_HIT_SOURCE）：SEARCH / ARCHIVE / STOCK_IN / AI_QA',
    farm_id        BIGINT UNSIGNED NULL COMMENT '养殖场 ID（t_farm）；无上下文时为空',
    user_id        BIGINT UNSIGNED NULL COMMENT '命中用户 ID（t_user）',
    keyword        VARCHAR(40)     NULL COMMENT '命中的搜索词或名称，例如 瘦肉精',
    ref_id         BIGINT UNSIGNED NULL COMMENT '关联业务 ID（入库单、AI 消息 ID），可空',
    hit_at         DATETIME        NOT NULL COMMENT '命中时间',
    PRIMARY KEY (id),
    KEY idx_banned_hit_drug_time (banned_drug_id, hit_at),
    KEY idx_banned_hit_farm_time (farm_id, hit_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='禁用药命中拦截记录（只追加：无 updated_at/version，不继承 BaseEntity）';

CREATE TABLE t_drug_lot_recall
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批号停用通知 ID',
    catalog_id   BIGINT UNSIGNED NOT NULL COMMENT '目录 ID（t_drug_catalog）',
    lot_no       VARCHAR(30)     NOT NULL COMMENT '批号，去首尾空格后保存，例如 2025047',
    expire_date  DATE            NULL COMMENT '有效期，取养殖场入库记录',
    reason       VARCHAR(100)    NOT NULL COMMENT '停用原因，原样显示在用户端',
    farm_count   INT             NOT NULL DEFAULT 0 COMMENT '停用时仍有结存的养殖场数',
    total_qty    DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '停用时结存合计',
    qty_unit     VARCHAR(10)     NOT NULL COMMENT '结存单位（目录记账单位）',
    recalled_at  DATETIME        NOT NULL COMMENT '停用时间',
    recalled_by  BIGINT UNSIGNED NOT NULL COMMENT '操作管理员 ID（t_admin）',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_lot_recall_catalog_lot (catalog_id, lot_no),
    KEY idx_drug_lot_recall_time (recalled_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批号停用通知（无状态字段，停用后不能恢复；模块09 t_drug_lot.recall_id 指向本表）';

CREATE TABLE t_drug_lot_recall_farm
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批号停用通知养殖场处理 ID',
    recall_id      BIGINT UNSIGNED NOT NULL COMMENT '批号停用通知 ID（t_drug_lot_recall）',
    farm_id        BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    qty_at_recall  DECIMAL(12, 2)  NOT NULL COMMENT '停用时该场结存数量',
    qty_unit       VARCHAR(10)     NOT NULL COMMENT '结存单位（目录记账单位）',
    scrap_status   VARCHAR(20)     NOT NULL COMMENT '报废进度（RECALL_SCRAP_STATUS）：UNSCRAPPED / PARTIAL / SCRAPPED',
    scrapped_qty   DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '已报废数量',
    scrapped_at    DATE            NULL COMMENT '全部报废的日期',
    message_id     BIGINT UNSIGNED NULL COMMENT '发给所有者的 LOT_RECALLED 消息 ID',
    created_by     BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_lot_recall_farm (recall_id, farm_id),
    KEY idx_drug_lot_recall_farm_farm (farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批号停用通知各养殖场处理进度（模块09 登记报废时更新）';
