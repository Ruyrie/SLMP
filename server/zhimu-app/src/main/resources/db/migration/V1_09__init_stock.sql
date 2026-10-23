-- 模块09 饲料与兽药疫苗库存（模块09 服务端技术准备文档 2.3 节、05 号文档 4.9 节）。
-- 12 张表：t_feed、t_drug、t_drug_lot、t_stock_inbound、t_stock_outbound、t_stock_loss、
--           t_stocktake、t_stocktake_item、t_stock_flow、t_batch_material_cost、t_stock_partner、t_stock_setting。
-- 已执行的脚本不再修改；结构变更另起 V1_09_1、V1_09_2…（V9_10 为示例数据，只在 dev/test 执行）。
-- 不建外键；InnoDB、utf8mb4_0900_ai_ci；养殖场表一律带 farm_id，多租户插件按 FarmContext 自动过滤。
-- 数量一律存“记账单位”：饲料为千克（KG），药品为档案的记账单位（DOSE / G / ML / L）。
-- 文档给出的 DECIMAL(10,2) 容不下接口上限 99,999,999.99（8 位整数），数量统一放宽为 DECIMAL(14,3)、
--   金额 DECIMAL(16,2)，口径不变（饲料数量接口层仍限制 2 位小数）。
-- 公共字段：created_at / updated_at 由框架自动填充，created_by 填当前用户（系统为 0）；
--   档案与单据另带 updated_by（上次修改人，t_feed.updated_by_name 由该用户实时取名，不落冗余姓名）。
-- 单据作废只改 status（NORMAL → VOID），不删除；库存流水不删除，作废只改 status。

-- 2.3.1 饲料档案
CREATE TABLE t_feed
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '饲料 ID，示例 5101 育肥全价料',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    name              VARCHAR(20)     NOT NULL COMMENT '饲料名称，本场不重名，1–20 字',
    feed_type_code    VARCHAR(20)     NOT NULL COMMENT '饲料类型（字典 FEED_TYPE）：COMPLETE 全价料 / NURSERY 保育料 等',
    spec              VARCHAR(20)     NULL COMMENT '规格说明，例如 40 千克/袋',
    unit              VARCHAR(10)     NOT NULL DEFAULT 'KG' COMMENT '记账单位，饲料固定 KG（与模块06 投喂量一致）',
    pack_unit         VARCHAR(10)     NULL COMMENT '包装单位，例如 袋；与 pack_size 同时填写',
    pack_size         DECIMAL(10, 3)  NULL COMMENT '每包装折合记账单位数，例如 40.000（千克/袋），> 0',
    category_id       BIGINT UNSIGNED NULL COMMENT '适用品类 ID（t_category），可空',
    min_stock         DECIMAL(14, 3)  NULL COMMENT '库存下限（千克），≥ 0；空表示不按下限判断',
    warn_days         INT             NOT NULL DEFAULT 7 COMMENT '预警天数 1–365，默认 7；剩余可用天数低于它即预警',
    stock_qty         DECIMAL(14, 3)  NOT NULL DEFAULT 0 COMMENT '当前结存（千克），≥ 0，每次结存变化更新',
    stock_amount      DECIMAL(16, 2)  NOT NULL DEFAULT 0 COMMENT '当前库存金额（元）＝结存 × 均价，≥ 0',
    avg_price         DECIMAL(12, 4)  NOT NULL DEFAULT 0 COMMENT '移动加权平均成本（元/千克），4 位小数；结存为 0 时保留最后值',
    usage_7d_qty      DECIMAL(14, 3)  NOT NULL DEFAULT 0 COMMENT '近 7 个完整自然日领用量（不含今天）',
    usage_days        INT             NOT NULL DEFAULT 0 COMMENT '统计窗口内实际有领用的天数 0–7',
    daily_use_qty     DECIMAL(14, 3)  NULL COMMENT '日均领用量 ＝ 近 7 天领用 ÷ usage_days；无消耗为空',
    remaining_basis   VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT '剩余天数口径（REMAINING_BASIS）：PLAN 饲喂方案 / USAGE_7D 近 7 天 / NONE 暂无消耗',
    remaining_days    INT             NULL COMMENT '剩余可用天数，向下取整；暂无消耗为空',
    stat_date         DATE            NULL COMMENT '统计缓存日期（StockDailyJob 写入）',
    is_low_stock      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '库存不足：1 是（EMPTY / BELOW_MIN / BELOW_DAYS）',
    low_stock_reason  VARCHAR(20)     NULL COMMENT '不足原因（LOW_STOCK_REASON）：EMPTY / BELOW_MIN / BELOW_DAYS',
    low_stock_at      DATETIME        NULL COMMENT '进入预警的时刻；作为 FEED_LOW_STOCK 待办去重键',
    is_unit_locked    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '已产生流水：1 是，单位不可改',
    version           INT             NOT NULL DEFAULT 0 COMMENT '结存版本：每次结存变化 +1，手工登记以此判断“库存已变化”',
    edit_version      INT             NOT NULL DEFAULT 0 COMMENT '档案编辑乐观锁，与结存版本互不影响',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除（本版不提供删除）',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_farm_name (farm_id, name),
    KEY idx_feed_farm_low (farm_id, is_low_stock)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲料档案与结存（模块09）';

-- 2.3.2 药品档案（名称、类别、包装均取自平台目录快照）
CREATE TABLE t_drug
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '药品档案 ID，示例 5201 阿莫西林可溶性粉',
    farm_id          BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    category         VARCHAR(20)     NOT NULL COMMENT '类别（DRUG_CATEGORY）：VET_DRUG / VACCINE / DISINFECTANT；有流水后不可改',
    catalog_id       BIGINT UNSIGNED NOT NULL COMMENT '平台目录 ID（t_drug_catalog）',
    name             VARCHAR(50)     NOT NULL COMMENT '目录名称快照，例如 口蹄疫灭活疫苗',
    spec             VARCHAR(20)     NULL COMMENT '规格，默认取目录规格，例如 10 头份/瓶',
    unit             VARCHAR(10)     NOT NULL COMMENT '记账单位（MEASURE_UNIT）：DOSE / G / ML / L；疫苗固定 DOSE',
    pack_unit        VARCHAR(10)     NOT NULL COMMENT '包装单位，例如 瓶 / 袋 / 桶',
    pack_size        DECIMAL(10, 3)  NOT NULL COMMENT '每包装折合记账单位数，例如 10.000（头份/瓶），> 0',
    min_stock        DECIMAL(14, 3)  NULL COMMENT '库存下限，按记账单位折算后存储（App 以 PACK 录入时 × pack_size）',
    min_stock_unit   VARCHAR(20)     NULL COMMENT '下限录入单位（QTY_INPUT_UNIT）：PACK / MEASURE；回显时据此换算',
    is_low_stock     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '可用批号结存合计低于下限：1 是',
    low_stock_at     DATETIME        NULL COMMENT '进入预警的时刻；作为 DRUG_LOW_STOCK 待办去重键',
    is_unit_locked   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '已产生流水：1 是，类别、名称、单位、包装不可改',
    version          INT             NOT NULL DEFAULT 0 COMMENT '档案乐观锁版本号',
    deleted          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除（本版不提供删除）',
    created_by       BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by       BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at       DATETIME        NOT NULL COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_farm_catalog (farm_id, catalog_id),
    KEY idx_drug_farm_category (farm_id, category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='兽药疫苗档案（模块09）';

-- 2.3.3 药品批号结存（每个“药品 + 批号”单独结存与均价）
CREATE TABLE t_drug_lot
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批号 ID，示例 5201；示例数据占用 5201–6301，新批号从 7001 起',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    drug_id             BIGINT UNSIGNED NOT NULL COMMENT '药品档案 ID（t_drug）',
    catalog_id          BIGINT UNSIGNED NOT NULL COMMENT '目录 ID 冗余，平台停用按 catalog_id + lot_no 匹配',
    category            VARCHAR(20)     NOT NULL COMMENT '类别冗余（DRUG_CATEGORY）',
    lot_no              VARCHAR(30)     NOT NULL COMMENT '生产批号，去首尾空格后保存，例如 2025047',
    expire_date         DATE            NOT NULL COMMENT '有效期至；已过期批号只能报废',
    first_inbound_date  DATE            NOT NULL COMMENT '首次入库日期；领用、退货、报废日期的下限',
    inbound_count       INT             NOT NULL DEFAULT 0 COMMENT '有效入库单数',
    stock_qty           DECIMAL(14, 3)  NOT NULL DEFAULT 0 COMMENT '当前结存（记账单位），≥ 0',
    stock_amount        DECIMAL(16, 2)  NOT NULL DEFAULT 0 COMMENT '当前库存金额（元），≥ 0',
    avg_price           DECIMAL(12, 4)  NOT NULL DEFAULT 0 COMMENT '批号内移动加权平均成本（元/记账单位）',
    recall_id           BIGINT UNSIGNED NULL COMMENT '平台停用通知 ID（t_drug_lot_recall）；未停用为空',
    recalled_at         DATETIME        NULL COMMENT '被平台停用的时间',
    recall_reason       VARCHAR(100)    NULL COMMENT '停用原因，原样显示在用户端',
    recall_stock_qty    DECIMAL(14, 3)  NULL COMMENT '停用时结存快照，用于“只能报废”提示与处理进度',
    near_notified_at    DATETIME        NULL COMMENT '已生成临期待办的时刻',
    expired_notified_at DATETIME        NULL COMMENT '已生成过期待办的时刻',
    version             INT             NOT NULL DEFAULT 0 COMMENT '结存乐观锁版本号，每次结存变化 +1',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_farm_drug_no (farm_id, drug_id, lot_no),
    KEY idx_lot_farm_cat_exp (farm_id, category, expire_date),
    KEY idx_lot_catalog_no (catalog_id, lot_no),
    KEY idx_lot_drug_exp (drug_id, expire_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='药品批号结存（模块09；批号不删除，结存为 0 后不再出现在列表与下拉）';

-- 2.3.4 入库单（饲料与药品共用；药品带 drug_lot_id、lot_no）
CREATE TABLE t_stock_inbound
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '入库单 ID，示例 6201',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    inbound_no        VARCHAR(20)     NOT NULL COMMENT '入库单号：RK + yyMMdd + 4 位当日流水，例如 RK2610190001',
    material_type     VARCHAR(20)     NOT NULL COMMENT '物资类型（MATERIAL_TYPE）：FEED / DRUG',
    feed_id           BIGINT UNSIGNED NULL COMMENT '饲料 ID（material_type = FEED 时必填）',
    drug_id           BIGINT UNSIGNED NULL COMMENT '药品档案 ID（material_type = DRUG 时必填）',
    drug_lot_id       BIGINT UNSIGNED NULL COMMENT '药品批号 ID（material_type = DRUG 时必填）',
    lot_no            VARCHAR(30)     NULL COMMENT '批号快照（药品）',
    input_qty         DECIMAL(14, 3)  NOT NULL COMMENT '录入数量，> 0',
    input_unit        VARCHAR(20)     NOT NULL DEFAULT 'MEASURE' COMMENT '录入单位（QTY_INPUT_UNIT）：MEASURE 记账单位 / PACK 包装',
    qty               DECIMAL(14, 3)  NOT NULL COMMENT '记账数量 ＝ input_qty（MEASURE）或 input_qty × pack_size（PACK），> 0',
    unit_price        DECIMAL(12, 2)  NOT NULL COMMENT '录入单价（元/录入单位），> 0',
    measure_price     DECIMAL(12, 4)  NOT NULL COMMENT '折合记账单位单价（元/千克、元/头份），4 位小数',
    amount            DECIMAL(16, 2)  NOT NULL COMMENT '金额 ＝ input_qty × unit_price，保留到分',
    production_date   DATE            NULL COMMENT '生产日期（饲料），不晚于入库日期',
    shelf_life_date   DATE            NULL COMMENT '保质期至（饲料），晚于生产日期且不早于入库日期',
    expire_date       DATE            NULL COMMENT '有效期至（药品），不早于入库日期；同批号已存在时必须一致',
    supplier_name     VARCHAR(30)     NULL COMMENT '供应商名称（饲料必填，进入最近供应商）',
    inbound_date      DATE            NOT NULL COMMENT '入库日期，不晚于今天',
    is_sync_finance   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '同步记账：1 是（模块10 生成支出）',
    finance_record_id BIGINT UNSIGNED NULL COMMENT '模块10 收支流水 ID；未同步或模块10 未接入时为空',
    flow_id           BIGINT UNSIGNED NULL COMMENT '当前有效的入库流水 ID（t_stock_flow）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '单据状态（RECORD_STATUS）：NORMAL 有效 / VOID 已作废',
    void_at           DATETIME        NULL COMMENT '作废时间',
    void_by           BIGINT UNSIGNED NULL COMMENT '作废人用户 ID',
    client_request_id VARCHAR(64)     NULL COMMENT '提交时的 Idempotency-Key，数据库唯一索引兜底防重',
    version           INT             NOT NULL DEFAULT 0 COMMENT '单据乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID（当天修改）',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbound_no (inbound_no),
    UNIQUE KEY uk_inbound_req (farm_id, client_request_id),
    KEY idx_inbound_farm_type_date (farm_id, material_type, inbound_date),
    KEY idx_inbound_feed (feed_id),
    KEY idx_inbound_lot (drug_lot_id),
    KEY idx_inbound_farm_supplier (farm_id, supplier_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='入库单（模块09）';

-- 2.3.5 手工出库单（饲料其他出库、药品领用与退货；模块06/08 的自动扣减只写流水）
CREATE TABLE t_stock_outbound
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出库单 ID，示例 6501',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    outbound_no       VARCHAR(20)     NOT NULL COMMENT '出库单号：CK + yyMMdd + 4 位当日流水，例如 CK2610190001',
    material_type     VARCHAR(20)     NOT NULL COMMENT '物资类型（MATERIAL_TYPE）：FEED / DRUG',
    out_type          VARCHAR(20)     NOT NULL COMMENT '出库类型（OUTBOUND_TYPE）：OTHER_OUT 其他出库 / USE 领用 / RETURN 退货',
    feed_id           BIGINT UNSIGNED NULL COMMENT '饲料 ID（其他出库）',
    drug_id           BIGINT UNSIGNED NULL COMMENT '药品档案 ID（领用、退货）',
    drug_lot_id       BIGINT UNSIGNED NULL COMMENT '药品批号 ID（领用、退货）',
    input_qty         DECIMAL(14, 3)  NOT NULL COMMENT '录入数量，> 0',
    input_unit        VARCHAR(20)     NOT NULL DEFAULT 'MEASURE' COMMENT '录入单位（QTY_INPUT_UNIT）：MEASURE / PACK',
    qty               DECIMAL(14, 3)  NOT NULL COMMENT '记账数量（千克 / 记账单位），> 0',
    unit_price        DECIMAL(12, 4)  NOT NULL COMMENT '出库时均价（元/记账单位），4 位小数，不改均价',
    amount            DECIMAL(16, 2)  NOT NULL COMMENT '金额 ＝ 数量 × 当时均价，保留到分',
    max_qty           DECIMAL(14, 3)  NOT NULL COMMENT '登记时可出库上限（当时结存），用于修改时的数量上限校验',
    destination       VARCHAR(20)     NULL COMMENT '其他出库去向（OTHER_OUT_DEST）：RETURN_SUPPLIER / TRANSFER_FARM / SALE / OTHER',
    target_type       VARCHAR(20)     NULL COMMENT '领用关联对象（USE_TARGET_TYPE）：BATCH / PEN',
    batch_id          BIGINT UNSIGNED NULL COMMENT '关联批次 ID（target_type = BATCH）',
    pen_id            BIGINT UNSIGNED NULL COMMENT '关联栏舍 ID（target_type = PEN）',
    receiver_id       BIGINT UNSIGNED NULL COMMENT '领用人用户 ID（领用必填）',
    return_to         VARCHAR(30)     NULL COMMENT '退回对象（退货必填，进入最近退回对象）',
    return_reason     VARCHAR(20)     NULL COMMENT '退货原因（RETURN_REASON）：QUALITY / PACKAGE_DAMAGED / OVER_PURCHASE / OTHER',
    remark            VARCHAR(100)    NULL COMMENT '说明',
    out_date          DATE            NOT NULL COMMENT '出库日期，不晚于今天',
    flow_id           BIGINT UNSIGNED NULL COMMENT '当前有效的出库流水 ID（t_stock_flow）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '单据状态（RECORD_STATUS）：NORMAL / VOID',
    void_at           DATETIME        NULL COMMENT '作废时间',
    void_by           BIGINT UNSIGNED NULL COMMENT '作废人用户 ID',
    client_request_id VARCHAR(64)     NULL COMMENT '提交时的 Idempotency-Key',
    version           INT             NOT NULL DEFAULT 0 COMMENT '单据乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间（“登记当天”按它的北京日期判断）',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbound_no (outbound_no),
    UNIQUE KEY uk_outbound_req (farm_id, client_request_id),
    KEY idx_outbound_farm_type_date (farm_id, out_type, out_date),
    KEY idx_outbound_lot (drug_lot_id),
    KEY idx_outbound_feed (feed_id),
    KEY idx_outbound_farm_return_to (farm_id, return_to),
    KEY idx_outbound_batch (batch_id),
    KEY idx_outbound_pen (pen_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='手工出库单（饲料其他出库、药品领用与退货）';

-- 2.3.6 报损与报废（报损只针对饲料，报废只针对药品批号）
CREATE TABLE t_stock_loss
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报损/报废单 ID，示例 6401',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    loss_type         VARCHAR(20)     NOT NULL COMMENT '类型（LOSS_TYPE）：FEED_LOSS 报损 / DRUG_SCRAP 报废',
    feed_id           BIGINT UNSIGNED NULL COMMENT '饲料 ID（报损必填）',
    drug_id           BIGINT UNSIGNED NULL COMMENT '药品档案 ID（报废必填）',
    drug_lot_id       BIGINT UNSIGNED NULL COMMENT '药品批号 ID（报废必填）',
    input_qty         DECIMAL(14, 3)  NOT NULL COMMENT '录入数量，> 0',
    input_unit        VARCHAR(20)     NOT NULL DEFAULT 'MEASURE' COMMENT '录入单位（QTY_INPUT_UNIT）：MEASURE / PACK',
    qty               DECIMAL(14, 3)  NOT NULL COMMENT '记账数量，> 0，不超过当前结存',
    reason            VARCHAR(20)     NOT NULL COMMENT '报损原因（FEED_LOSS_REASON）或报废原因（字典 SCRAP_REASON）',
    remark            VARCHAR(100)    NULL COMMENT '说明；原因为 OTHER 时必填',
    loss_date         DATE            NOT NULL COMMENT '报损日期（固定当天）或报废日期（不早于批号首次入库日期）',
    unit_price        DECIMAL(12, 4)  NOT NULL COMMENT '发生时的均价（元/记账单位）',
    amount            DECIMAL(16, 2)  NOT NULL COMMENT '损失金额 ＝ 数量 × 当时均价，保留到分',
    flow_id           BIGINT UNSIGNED NULL COMMENT '当前有效的损失流水 ID（t_stock_flow）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '单据状态（RECORD_STATUS）：NORMAL / VOID',
    client_request_id VARCHAR(64)     NULL COMMENT '提交时的 Idempotency-Key',
    version           INT             NOT NULL DEFAULT 0 COMMENT '单据乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_loss_req (farm_id, client_request_id),
    KEY idx_loss_farm_type_date (farm_id, loss_type, loss_date),
    KEY idx_loss_lot (drug_lot_id),
    KEY idx_loss_feed (feed_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报损与报废（模块09）';

-- 2.3.7 盘点单
CREATE TABLE t_stocktake
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '盘点单 ID，示例 6301',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    stocktake_no      VARCHAR(20)     NOT NULL COMMENT '盘点单号：PD + yyMMdd + 4 位当日流水，例如 PD2610190001',
    stocktake_date    DATE            NOT NULL COMMENT '盘点日期，固定当天',
    item_count        INT             NOT NULL DEFAULT 0 COMMENT '盘点项数',
    gain_count        INT             NOT NULL DEFAULT 0 COMMENT '盘盈项数',
    loss_count        INT             NOT NULL DEFAULT 0 COMMENT '盘亏项数',
    gain_amount       DECIMAL(16, 2)  NOT NULL DEFAULT 0 COMMENT '盘盈金额合计（元）',
    loss_amount       DECIMAL(16, 2)  NOT NULL DEFAULT 0 COMMENT '盘亏金额合计（元，正数）',
    client_request_id VARCHAR(64)     NULL COMMENT '提交时的 Idempotency-Key',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '盘点人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_no (stocktake_no),
    UNIQUE KEY uk_stocktake_req (farm_id, client_request_id),
    KEY idx_stocktake_farm_date (farm_id, stocktake_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='盘点单（模块09，只盘饲料）';

-- 2.3.8 盘点明细
CREATE TABLE t_stocktake_item
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '盘点明细 ID',
    stocktake_id BIGINT UNSIGNED NOT NULL COMMENT '盘点单 ID（t_stocktake）',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID 冗余，便于按场统计',
    feed_id      BIGINT UNSIGNED NOT NULL COMMENT '饲料 ID（t_feed）',
    book_qty     DECIMAL(14, 3)  NOT NULL COMMENT '账面结存快照',
    actual_qty   DECIMAL(14, 3)  NOT NULL COMMENT '实盘数量，≥ 0',
    diff_qty     DECIMAL(14, 3)  NOT NULL COMMENT '差额 ＝ 实盘 − 账面，盘盈为正、盘亏为负',
    result       VARCHAR(20)     NOT NULL COMMENT '结果（STOCKTAKE_RESULT）：GAIN 盘盈 / LOSS 盘亏 / MATCH 相符',
    avg_price    DECIMAL(12, 4)  NOT NULL COMMENT '盘点时的均价',
    diff_amount  DECIMAL(16, 2)  NOT NULL COMMENT '差额金额，盘盈为正、盘亏为负',
    flow_id      BIGINT UNSIGNED NULL COMMENT '差额流水 ID（t_stock_flow）；相符时为空',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_stocktake_item_doc (stocktake_id),
    KEY idx_stocktake_item_feed (feed_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='盘点明细（模块09）';

-- 2.3.9 库存流水（唯一记账事实；数量、物资、来源写入后不改，成本类字段只由重放改写，作废只改 status）
CREATE TABLE t_stock_flow
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水 ID，示例 9301',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    material_type       VARCHAR(20)     NOT NULL COMMENT '物资类型（MATERIAL_TYPE）：FEED / DRUG',
    feed_id             BIGINT UNSIGNED NULL COMMENT '饲料 ID（饲料流水必填）',
    drug_id             BIGINT UNSIGNED NULL COMMENT '药品档案 ID（药品流水必填）',
    drug_lot_id         BIGINT UNSIGNED NULL COMMENT '药品批号 ID（药品流水必填）',
    flow_type           VARCHAR(20)     NOT NULL COMMENT '流水类型（FLOW_TYPE）：INBOUND / FEED_USE / VACCINE_USE / MEDICATION_USE / DISINFECT_USE / MANUAL_USE / RETURN / OTHER_OUT / LOSS / SCRAP / STOCKTAKE_GAIN / STOCKTAKE_LOSS',
    direction           VARCHAR(10)     NOT NULL COMMENT '方向（DIRECTION）：IN 入库 / OUT 出库',
    qty                 DECIMAL(14, 3)  NOT NULL COMMENT '记账数量，> 0',
    unit_price          DECIMAL(12, 4)  NOT NULL COMMENT '入库为入库单价、出库为当时均价（元/记账单位）',
    amount              DECIMAL(16, 2)  NOT NULL COMMENT '金额（元），保留到分',
    stock_qty_after     DECIMAL(14, 3)  NOT NULL COMMENT '发生后的结存快照（药品为批号结存）',
    stock_amount_after  DECIMAL(16, 2)  NOT NULL COMMENT '发生后的库存金额快照',
    biz_type            VARCHAR(30)     NOT NULL COMMENT '来源业务类型（BIZ_TYPE）：FEED_RECORD / IMMUNE_RECORD / MEDICATION_RECORD / DISINFECT_RECORD / INBOUND / OUTBOUND / LOSS / STOCKTAKE / CHANGE_REQUEST',
    biz_id              BIGINT UNSIGNED NOT NULL COMMENT '来源业务记录 ID',
    batch_id            BIGINT UNSIGNED NULL COMMENT '关联批次 ID（t_batch），可空',
    pen_id              BIGINT UNSIGNED NULL COMMENT '关联栏舍 ID（t_pen），可空',
    operator_id         BIGINT UNSIGNED NULL COMMENT '经办人 / 执行人 / 领用人用户 ID',
    related_desc        VARCHAR(100)    NULL COMMENT '关联说明快照，例如 ZM2610-031 · 3 号育肥舍 早餐',
    occur_at            DATETIME        NOT NULL COMMENT '发生时间（登记时间）',
    biz_date            DATE            NOT NULL COMMENT '业务日期，按北京时间',
    is_batch_cost       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '计入批次成本：1 是（饲喂、接种、用药、消毒、手工领用）',
    is_feed_usage       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '计入饲料用量：1 是（仅 FEED_USE）',
    origin_flow_id      BIGINT UNSIGNED NOT NULL COMMENT '记账顺序键：首次写入等于自身 ID，更正后的新流水沿用原值',
    status              VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '流水状态（RECORD_STATUS）：NORMAL / VOID',
    void_reason         VARCHAR(20)     NULL COMMENT '作废原因（VOID_REASON）：SOURCE_VOID 来源作废 / CORRECTED 被更正替换',
    void_at             DATETIME        NULL COMMENT '作废时间',
    recalc_at           DATETIME        NULL COMMENT '最近一次被重放改写成本的时刻',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_flow_farm_material_time (farm_id, material_type, occur_at),
    KEY idx_flow_feed_origin (feed_id, origin_flow_id),
    KEY idx_flow_lot_origin (drug_lot_id, origin_flow_id),
    KEY idx_flow_drug_time (drug_id, occur_at),
    KEY idx_flow_biz (biz_type, biz_id),
    KEY idx_flow_feed_usage (feed_id, is_feed_usage, biz_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='库存流水（模块09）';

-- 2.3.10 批次物资成本明细（领用与自动扣减按批次 / 栏舍分摊）
CREATE TABLE t_batch_material_cost
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次物资成本明细 ID',
    farm_id     BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    batch_id    BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    pen_id      BIGINT UNSIGNED NULL COMMENT '栏舍 ID（按栏舍分摊时记录）',
    flow_id     BIGINT UNSIGNED NOT NULL COMMENT '对应出库流水 ID（t_stock_flow）',
    source_type VARCHAR(30)     NOT NULL COMMENT '来源业务类型，与流水的 biz_type 相同',
    source_id   BIGINT UNSIGNED NOT NULL COMMENT '来源业务记录 ID，与流水的 biz_id 相同',
    cost_type   VARCHAR(20)     NOT NULL COMMENT '成本类型（COST_TYPE）：FEED / DRUG',
    qty         DECIMAL(14, 3)  NOT NULL COMMENT '分摊数量（记账单位）',
    head_count  INT             NULL COMMENT '分摊时的批次存栏（头/只）',
    ratio       DECIMAL(6, 4)   NOT NULL COMMENT '分摊比例，4 位小数，合计为 1',
    amount      DECIMAL(16, 2)  NOT NULL COMMENT '分摊金额（元），尾差计入最后一个批次',
    biz_date    DATE            NOT NULL COMMENT '业务日期',
    status      VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '状态（RECORD_STATUS）：NORMAL / VOID',
    created_at  DATETIME        NOT NULL COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_bmc_batch (batch_id, cost_type, status),
    KEY idx_bmc_flow (flow_id),
    KEY idx_bmc_source (source_type, source_id),
    KEY idx_bmc_farm_date (farm_id, biz_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次物资成本明细（模块09，模块10 结算读取）';

-- 2.3.11 往来单位（最近供应商 / 退回对象，M12 下拉；按最近使用时间倒序去重）
CREATE TABLE t_stock_partner
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '往来单位 ID',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    kind         VARCHAR(20)     NOT NULL COMMENT '类型（PARTNER_KIND）：SUPPLIER 供应商 / RETURN_TO 退回对象',
    name         VARCHAR(50)     NOT NULL COMMENT '名称，例如 松山区丰禾饲料经销部',
    use_count    INT             NOT NULL DEFAULT 1 COMMENT '使用次数',
    last_used_at DATETIME        NOT NULL COMMENT '最近使用时间，列表按它倒序',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_farm_kind_name (farm_id, kind, name),
    KEY idx_partner_farm_kind_time (farm_id, kind, last_used_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='库存往来单位（最近供应商 / 退回对象）';

-- 2.3.12 库存设置（每场一行；无记录时接口按默认 30 天返回）
CREATE TABLE t_stock_setting
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存设置 ID',
    farm_id                BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm），每场一行',
    near_days_vet          INT             NOT NULL DEFAULT 30 COMMENT '兽药临期天数 1–365',
    near_days_vaccine      INT             NOT NULL DEFAULT 30 COMMENT '疫苗临期天数 1–365',
    near_days_disinfectant INT             NOT NULL DEFAULT 30 COMMENT '消毒剂临期天数 1–365',
    version                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，首次保存为 0',
    created_by             BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    updated_by             BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at             DATETIME        NOT NULL COMMENT '创建时间',
    updated_at             DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_setting_farm (farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='库存设置（临期天数，模块09）';
