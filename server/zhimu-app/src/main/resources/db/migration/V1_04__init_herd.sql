-- 模块04 栏舍与存栏（模块04 服务端技术准备文档 2.3 节、05 号文档 4.5 节）
-- 6 张表：t_pen、t_batch、t_batch_pen、t_batch_event、t_batch_timeline、t_batch_stock_daily
-- 已执行的脚本不再修改；结构变更另起 V1_04_1、V1_04_2…
-- 不建外键；字符集 utf8mb4 / utf8mb4_0900_ai_ci；养殖场表均带 farm_id，多租户插件按 FarmContext 自动过滤。
-- 公共字段：created_at / updated_at 由框架自动填充；created_by 由框架填当前用户（系统为 0）。
-- 栏舍不提供删除，不设 deleted；批次、流水、时间线用 deleted 逻辑删除。

-- 2.3.1 栏舍
CREATE TABLE t_pen
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '栏舍 ID，示例 2001–2007',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    pen_code     VARCHAR(10)     NOT NULL COMMENT '栏舍编号：1–10 位字母、数字、短横线，保存为大写，本场唯一且不可修改',
    pen_name     VARCHAR(20)     NOT NULL COMMENT '栏舍名称，例如 3 号育肥舍；不要求唯一',
    pen_type     VARCHAR(20)     NOT NULL COMMENT '栏舍类型（PEN_TYPE 字典）：NURSERY 保育舍 / FATTENING 育肥舍 / SOW 母猪舍 / FARROWING 产房 / BROODING 育雏舍 / BROILER 肉鸡舍 / BREEDER 种鸡舍',
    category_id  BIGINT UNSIGNED NOT NULL COMMENT '适用品类 ID（t_category）；品类 is_breeding = 1 即为种畜栏舍',
    capacity     INT             NOT NULL COMMENT '设计容量，1–999,999，全部栏舍必填',
    stock_count  INT             NOT NULL DEFAULT 0 COMMENT '当前在栏数，等于本栏舍各批次 t_batch_pen.stock_count 之和，同事务原子更新',
    status       VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '栏舍状态（BASE_STATUS）：ENABLED 启用 / DISABLED 已停用',
    disabled_at  DATETIME        NULL COMMENT '停用时间；启用时清空',
    disabled_by  BIGINT UNSIGNED NULL COMMENT '停用人用户 ID；启用时清空',
    version      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本：资料编辑、停用、启用时比较；存栏变化不加 version',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by   BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID，系统为 0',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pen_code (farm_id, pen_code),
    KEY idx_pen_farm_status (farm_id, status, pen_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='栏舍（模块04）';

-- 2.3.2 批次
CREATE TABLE t_batch
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次 ID，示例 3001–3004',
    farm_id            BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    batch_no           VARCHAR(16)     NOT NULL COMMENT '批次号：ZM + 生成时北京年月 YYMM + “-” + 全平台当月流水号（3 位补零），全平台唯一、删除后不复用',
    no_month           CHAR(4)         NOT NULL COMMENT '生成时的北京年月，例如 2610；取号与补号用',
    no_seq             INT             NOT NULL COMMENT '全平台当月流水号，例如 31；落库便于 Redis 丢失后恢复',
    qr_token           CHAR(10)        NOT NULL COMMENT '二维码短链接令牌：10 位安全随机串（去掉易混淆字符），不可推测',
    category_id        BIGINT UNSIGNED NOT NULL COMMENT '品类 ID，由入栏品种带出',
    breed_id           BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（t_breed）',
    purpose            VARCHAR(20)     NOT NULL COMMENT '用途（BATCH_PURPOSE）：COMMERCIAL 商品 / BREEDING 种用；缺省按品类 is_breeding 取值',
    source             VARCHAR(20)     NOT NULL COMMENT '来源（BATCH_SOURCE）：PURCHASED 外购 / SELF_BRED 自繁',
    source_record_type VARCHAR(20)     NULL COMMENT '来源记录类型（SOURCE_RECORD_TYPE）：FARROWING 产仔转入 / INCUBATION 出雏转入；手工入栏为空',
    source_record_id   BIGINT UNSIGNED NULL COMMENT '来源记录 ID；手工入栏为空',
    entry_pen_id       BIGINT UNSIGNED NOT NULL COMMENT '入栏栏舍 ID（t_pen）',
    entry_date         DATE            NOT NULL COMMENT '入栏日期，例如 2026-10-01',
    entry_age_days     INT             NOT NULL COMMENT '入栏日龄，0–3,650，例如 78',
    entry_count        INT             NOT NULL COMMENT '入栏数量，1–999,999，例如 205',
    entry_avg_weight   DECIMAL(10, 2)  NULL COMMENT '入栏平均体重（千克），≤ 9,999.99',
    intro_unit_price   DECIMAL(12, 2)  NULL COMMENT '引种单价（元/头），外购必填、自繁存空；管理视图字段',
    current_count      INT             NOT NULL COMMENT '当前存栏 = entry_count − 有效死淘 − 有效出栏；等于该批次 t_batch_pen.stock_count 之和',
    death_count        INT             NOT NULL DEFAULT 0 COMMENT '有效死淘累计',
    sold_count         INT             NOT NULL DEFAULT 0 COMMENT '有效出栏累计',
    status             VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '批次状态（BATCH_STATUS）：ACTIVE 在养 / ENDED 已结束',
    end_type           VARCHAR(20)     NULL COMMENT '结束方式（BATCH_END_TYPE）：ALL_SOLD 全部出栏 / ALL_DEAD 全部死淘',
    end_date           DATE            NULL COMMENT '结束业务日期',
    ended_at           DATETIME        NULL COMMENT '结束时间',
    client_request_id  VARCHAR(64)     NULL COMMENT '入栏请求的 Idempotency-Key，数据库唯一索引兜底防重',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本：入栏修改、批次删除时比较；任何存栏变化加 1',
    deleted            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人（入栏登记人）用户 ID，系统为 0',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    UNIQUE KEY uk_batch_qr (qr_token),
    UNIQUE KEY uk_batch_req (created_by, client_request_id),
    KEY idx_batch_farm_status (farm_id, status, entry_date),
    KEY idx_batch_farm_cat (farm_id, category_id, purpose, status),
    KEY idx_batch_source (source_record_type, source_record_id),
    KEY idx_batch_month_seq (no_month, no_seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次（模块04）';

-- 2.3.3 批次栏舍分布
CREATE TABLE t_batch_pen
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次栏舍分布 ID',
    farm_id       BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id      BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    pen_id        BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen）',
    stock_count   INT             NOT NULL DEFAULT 0 COMMENT '该批次在该栏舍的在栏数，≥ 0；降为 0 时保留行',
    first_in_date DATE            NOT NULL COMMENT '最近一次从 0 变为正数的业务日期；转栏、死淘日期不得早于它',
    last_out_date DATE            NULL COMMENT '降为 0 的业务日期；用于“最后所在栏舍”和空栏说明',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_pen (batch_id, pen_id),
    KEY idx_batch_pen_pen (farm_id, pen_id, stock_count),
    KEY idx_batch_pen_batch (batch_id, stock_count)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次栏舍分布（模块04）';

-- 2.3.4 存栏变动流水
CREATE TABLE t_batch_event
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '存栏变动流水 ID，例如 901–909',
    farm_id               BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id              BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    event_type            VARCHAR(20)     NOT NULL COMMENT '变动类型（BATCH_EVENT_TYPE）：ENTRY 入栏 / TRANSFER 转栏 / DEATH 死淘 / SALE 出栏',
    event_date            DATE            NOT NULL COMMENT '业务日期',
    pen_id                BIGINT UNSIGNED NOT NULL COMMENT '入栏 / 转出 / 死淘 / 出栏栏舍 ID',
    to_pen_id             BIGINT UNSIGNED NULL COMMENT '转栏转入栏舍 ID',
    quantity              INT             NOT NULL COMMENT '变动数量，≥ 1',
    reason_code           VARCHAR(20)     NULL COMMENT '死淘原因（DEATH_REASON）：SICK / CULL / ACCIDENT / OTHER；死淘必填',
    remark                VARCHAR(200)    NULL COMMENT '备注：转栏 ≤ 100 字，死淘说明 ≤ 200 字',
    executor_user_id      BIGINT UNSIGNED NOT NULL COMMENT '实际执行人用户 ID；死淘为李明等，其他类型同登记人',
    source_biz_type       VARCHAR(30)     NULL COMMENT '来源业务类型：SALE_ORDER / FARROWING / INCUBATION',
    source_biz_id         BIGINT UNSIGNED NULL COMMENT '来源业务记录 ID',
    capacity_confirmed    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '入栏、转入超容量时已确认：1 是',
    over_capacity_count   INT             NOT NULL DEFAULT 0 COMMENT '登记时超出容量数快照，例如 20',
    status                VARCHAR(20)     NOT NULL DEFAULT 'EFFECTIVE' COMMENT '流水状态（BATCH_EVENT_STATUS）：EFFECTIVE 有效 / VOIDED 已作废（只有出栏会作废）',
    client_request_id     VARCHAR(64)     NULL COMMENT '登记请求的 Idempotency-Key',
    version               INT             NOT NULL DEFAULT 0 COMMENT '记录编辑乐观锁',
    deleted               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除（随批次删除或源记录删除）',
    created_by            BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by            BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID（直接修改或修改申请回写）',
    created_at            DATETIME        NOT NULL COMMENT '创建时间（登记时间）',
    updated_at            DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_event_batch (batch_id, event_date, id),
    KEY idx_event_farm_type (farm_id, event_type, event_date),
    KEY idx_event_pen (pen_id, event_date),
    KEY idx_event_to_pen (to_pen_id),
    UNIQUE KEY uk_event_req (created_by, client_request_id),
    KEY idx_event_source (source_biz_type, source_biz_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='存栏变动流水（模块04）';

-- 2.3.5 批次时间线索引
CREATE TABLE t_batch_timeline
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '时间线索引 ID',
    farm_id          BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id         BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    pen_id           BIGINT UNSIGNED NULL COMMENT '记录所属栏舍 ID',
    record_type      VARCHAR(20)     NOT NULL COMMENT '记录类型（TIMELINE_RECORD_TYPE）：ENTRY / TRANSFER / DEATH / SALE / FEED / WEIGH / IMMUNE / DRUG / INSPECT',
    biz_type         VARCHAR(30)     NOT NULL COMMENT '全局 BIZ_TYPE：BATCH_ENTRY、BATCH_TRANSFER、BATCH_DEATH、SALE_ORDER、FEED_RECORD、WEIGH_RECORD、IMMUNE_RECORD、MEDICATION_RECORD、INSPECTION',
    biz_id           BIGINT UNSIGNED NOT NULL COMMENT '来源记录 ID',
    biz_date         DATE            NOT NULL COMMENT '业务日期',
    occurred_at      DATETIME        NOT NULL COMMENT '发生时间；只有日期的记录取业务日期 + 登记时刻',
    title            VARCHAR(50)     NOT NULL COMMENT '标题，例如 死淘 · 2 头 · 病死；不得含金额',
    subtitle         VARCHAR(100)    NULL COMMENT '副标题，例如 3 号育肥舍 · 实际执行人 李明；不得含金额',
    detail_json      JSON            NULL COMMENT '展开详情键值列表（登记人、登记时间、执行人、备注），不得含金额',
    operator_user_id BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    deleted          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：源记录删除或作废时置 1',
    created_at       DATETIME        NOT NULL COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_timeline_biz (batch_id, biz_type, biz_id),
    KEY idx_timeline_list (batch_id, deleted, occurred_at, id),
    KEY idx_timeline_type (batch_id, record_type, deleted, occurred_at),
    KEY idx_timeline_src (biz_type, biz_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次时间线索引（模块04，其他模块写入标题快照）';

-- 2.3.6 批次日末存栏快照（模块10 报表读取；本批只建表与提供查询）
CREATE TABLE t_batch_stock_daily
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日末快照 ID',
    farm_id     BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id    BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '品类 ID，便于按品类、按头/只分组',
    purpose     VARCHAR(20)     NOT NULL COMMENT '用途（BATCH_PURPOSE）：COMMERCIAL / BREEDING',
    stat_date   DATE            NOT NULL COMMENT '统计日期',
    stock_count INT             NOT NULL COMMENT '当日 24:00 的批次存栏 = entry_count − 业务日期不晚于当日的有效死淘与出栏；转栏不影响',
    created_at  DATETIME        NOT NULL COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL COMMENT '最后修改时间（回溯重建时更新）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily (batch_id, stat_date),
    KEY idx_daily_farm (farm_id, stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次日末存栏快照（模块04 写，模块10 读）';
