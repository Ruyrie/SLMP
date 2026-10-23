-- 模块10 收支、出栏、行情与报表（模块10 服务端技术准备文档 2.3 节、05 号文档 4.11 节）
-- 12 张归属表 + 行情价与筛选配置 2 张（行情不接外部源，先用 V9_12 种子）。
-- t_finance_record_log、t_sale_order_log、t_batch_settlement_log 不建表，统一写 t_operation_log。
-- 不建外键；字符集 utf8mb4 / utf8mb4_0900_ai_ci；养殖场表均带 farm_id，多租户插件按 FarmContext 自动过滤。
-- 公共字段：created_at / updated_at 由框架自动填充；created_by 由框架填当前用户（系统为 0）。

-- 2.3.1 收支一级分类（全局表，管理端-03 维护，用户端只读）
CREATE TABLE t_finance_category
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类 ID，示例 101–107、201–204',
    direction    VARCHAR(20)     NOT NULL COMMENT '方向（FINANCE_DIRECTION）：EXPENSE 支出 / INCOME 收入',
    code         VARCHAR(20)     NOT NULL COMMENT '开放编码，不可改：FEED / DRUG / UTILITY / LABOR / SEEDLING / REPAIR / OTHER / SALE / BYPRODUCT / SUBSIDY / OTHER_INCOME；管理员新增为 EXP_/INC_ 加序号',
    name         VARCHAR(10)     NOT NULL COMMENT '分类名称，例如 饲料采购',
    icon         VARCHAR(40)     NULL COMMENT '图标名，例如 inventory_2',
    sort_no      INT             NOT NULL COMMENT '排序号，1 开始',
    status       VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态（BASE_STATUS）：ENABLED 启用 / DISABLED 停用；停用后记账不再显示',
    auto_source  VARCHAR(20)     NULL COMMENT '自动记账来源：FEED_INBOUND / DRUG_INBOUND / SALE_RECEIPT；手工分类为空',
    mark_only    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1：关联批次只做标记、不计入批次成本（饲料、兽药、引种）；收入分类无意义',
    is_fallback  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1：“其他”兜底分类，不能停用',
    disabled_at  DATETIME        NULL COMMENT '停用时间',
    disable_reason VARCHAR(100)  NULL COMMENT '停用原因',
    version      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本：管理端维护时比较',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建人管理员 ID，系统为 0',
    updated_by   BIGINT UNSIGNED NULL COMMENT '最后修改人管理员 ID',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_category_code (code),
    KEY idx_finance_category_dir (direction, status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='收支一级分类（模块10）';

-- 2.3.2 支出子类（按养殖场共享，所有者与场长同一套）
CREATE TABLE t_expense_subcategory
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '支出子类 ID，示例 1011',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    category_id  BIGINT UNSIGNED NOT NULL COMMENT '支出一级分类 ID（t_finance_category，只限 EXPENSE）',
    name         VARCHAR(10)     NOT NULL COMMENT '子类名称：去首尾空格后 1–10 字；同场同分类未删除不重名',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除；已有账目按 ID 关联仍显示原名称',
    deleted_at   DATETIME        NULL COMMENT '删除时间',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_expense_sub_farm (farm_id, category_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='支出子类（模块10）';

-- 2.3.3 收支流水
CREATE TABLE t_finance_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收支记录 ID，示例 8201–8208',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    record_type       VARCHAR(20)     NOT NULL COMMENT '类型（FINANCE_DIRECTION）：EXPENSE 支出 / INCOME 收入',
    source_type       VARCHAR(20)     NOT NULL COMMENT '来源（FINANCE_SOURCE_TYPE）：MANUAL 手工 / FEED_INBOUND 饲料入库 / DRUG_INBOUND 兽药入库 / SALE_RECEIPT 出栏收款',
    source_id         BIGINT UNSIGNED NULL COMMENT '入库单 ID 或收款 ID；手工为空',
    source_no         VARCHAR(32)     NULL COMMENT '原单据编号快照：入库单号、出栏单号',
    sale_order_id     BIGINT UNSIGNED NULL COMMENT '出栏收款所属出栏单 ID，供“原单据”跳转',
    category_id       BIGINT UNSIGNED NOT NULL COMMENT '一级分类 ID（t_finance_category）',
    subcategory_id    BIGINT UNSIGNED NULL COMMENT '支出子类 ID（t_expense_subcategory），只限支出',
    amount            DECIMAL(12, 2)  NOT NULL COMMENT '金额，> 0，不带符号；方向看 record_type',
    record_date       DATE            NOT NULL COMMENT '记账日期，按它归月；收款流水为收款日期',
    batch_id          BIGINT UNSIGNED NULL COMMENT '关联批次 ID（t_batch）',
    note              VARCHAR(30)     NULL COMMENT '收入说明，例如 能繁母猪补贴',
    detail_text       VARCHAR(100)    NULL COMMENT '自动记录明细：育肥全价料 1,000 千克；ZM2609-012 · 银行转账',
    voucher_count     INT             NOT NULL DEFAULT 0 COMMENT '凭证张数冗余（t_biz_media，biz_type = FINANCE_RECORD）',
    status            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '状态（RECORD_STATUS）：NORMAL 正常 / VOID 已作废',
    void_source       VARCHAR(20)     NULL COMMENT '作废来源（VOID_SOURCE）：MANUAL 手工 / SOURCE_VOID 原单据作废 / CHANGE_REQUEST 经审核作废',
    voided_at         DATETIME        NULL COMMENT '作废时间',
    voided_by         BIGINT UNSIGNED NULL COMMENT '作废人用户 ID；系统作废为 0',
    client_request_id VARCHAR(64)     NULL COMMENT '手工记录的 Idempotency-Key',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建人用户 ID；自动记录为 0',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_record_source (source_type, source_id),
    UNIQUE KEY uk_finance_record_req (farm_id, client_request_id),
    KEY idx_finance_record_farm_date (farm_id, record_date),
    KEY idx_finance_record_batch (farm_id, batch_id),
    KEY idx_finance_record_cat_date (farm_id, category_id, record_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='收支流水（模块10）';

-- 2.3.4 出栏单
CREATE TABLE t_sale_order
(
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出栏单 ID，示例 9106',
    farm_id                   BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id                  BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    sale_no                   VARCHAR(20)     NOT NULL COMMENT '出栏单号：CL + 登记日期 + 3 位全平台当日流水；界面不显示，导出用',
    pen_id                    BIGINT UNSIGNED NOT NULL COMMENT '主出栏栏舍 ID；多栏舍为出栏数最多的栏舍',
    pen_allocations           JSON            NOT NULL COMMENT '各栏舍出栏数快照 [{pen_id, pen_name, quantity}]',
    category_id               BIGINT UNSIGNED NOT NULL COMMENT '品类 ID（登记时快照）',
    breed_id                  BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（登记时快照）',
    sale_date                 DATE            NOT NULL COMMENT '出栏日期',
    quantity                  INT             NOT NULL COMMENT '出栏数量，> 0',
    total_weight              DECIMAL(10, 2)  NOT NULL COMMENT '总重（千克）',
    avg_weight                DECIMAL(10, 1)  NOT NULL COMMENT '均重（千克）= 总重 ÷ 数量，一位小数',
    pricing_method            VARCHAR(20)     NOT NULL COMMENT '计价方式（PRICING_METHOD）：BY_WEIGHT 按重量 / BY_HEAD 按头',
    unit_price                DECIMAL(12, 2)  NOT NULL COMMENT '单价（元/千克或元/头）',
    ref_unit_price            DECIMAL(12, 2)  NULL COMMENT '登记时当日行情价快照（元/千克）',
    sale_amount               DECIMAL(12, 2)  NOT NULL COMMENT '销售金额，四舍五入到元',
    buyer_name                VARCHAR(30)     NOT NULL COMMENT '收购商名称',
    due_date                  DATE            NULL COMMENT '约定收款日',
    effective_due_date        DATE            NOT NULL COMMENT '约定日；未填为出栏日期 + 7 天',
    received_amount           DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '已收金额',
    unpaid_amount             DECIMAL(12, 2)  NOT NULL COMMENT '未收金额 = 销售金额 − 已收金额',
    receipt_count             INT             NOT NULL DEFAULT 0 COMMENT '收款笔数',
    payment_status            VARCHAR(20)     NOT NULL COMMENT '收款状态（PAYMENT_STATUS）：UNPAID / PARTIAL / PAID / VOIDED',
    paid_off_date             DATE            NULL COMMENT '收齐日 = 有效收款中最晚的收款日期',
    pending_change_request_id BIGINT UNSIGNED NULL COMMENT '待审核修改申请 ID，有值即“待审核”',
    ended_batch               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '本单使批次全部出栏',
    content_version           INT             NOT NULL DEFAULT 0 COMMENT '内容版本：内容或作废变化时加 1，收款不变；作为修改申请的 record_version',
    void_reason               VARCHAR(200)    NULL COMMENT '作废原因',
    void_source               VARCHAR(20)     NULL COMMENT '作废来源（VOID_SOURCE）：DIRECT 直接作废 / CHANGE_REQUEST 经审核作废',
    voided_at                 DATETIME        NULL COMMENT '作废时间',
    voided_by                 BIGINT UNSIGNED NULL COMMENT '作废人用户 ID',
    client_request_id         VARCHAR(64)     NULL COMMENT 'Idempotency-Key',
    version                   INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本；每次更新加 1（含收款）',
    created_by                BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by                BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at                DATETIME        NOT NULL COMMENT '创建时间',
    updated_at                DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_order_no (sale_no),
    UNIQUE KEY uk_sale_order_req (farm_id, client_request_id),
    KEY idx_sale_order_farm_date (farm_id, sale_date),
    KEY idx_sale_order_pay_due (farm_id, payment_status, effective_due_date),
    KEY idx_sale_order_batch (batch_id),
    KEY idx_sale_order_buyer (farm_id, buyer_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='出栏单（模块10）';

-- 2.3.5 出栏收款
CREATE TABLE t_sale_receipt
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收款 ID，示例 5201',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    sale_order_id     BIGINT UNSIGNED NOT NULL COMMENT '出栏单 ID（t_sale_order）',
    batch_id          BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch）',
    amount            DECIMAL(12, 2)  NOT NULL COMMENT '收款金额，> 0',
    receipt_date      DATE            NOT NULL COMMENT '收款日期',
    payment_method    VARCHAR(20)     NOT NULL COMMENT '收款方式（PAYMENT_METHOD）：BANK_TRANSFER / WECHAT / ALIPAY / CASH',
    finance_record_id BIGINT UNSIGNED NOT NULL COMMENT '生成的“出栏销售”收入流水 ID',
    status            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL 正常 / VOID 已作废（出栏单经审核作废时）',
    voided_at         DATETIME        NULL COMMENT '作废时间',
    client_request_id VARCHAR(64)     NOT NULL COMMENT 'Idempotency-Key',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_receipt_finance (finance_record_id),
    UNIQUE KEY uk_sale_receipt_req (farm_id, client_request_id),
    KEY idx_sale_receipt_order (sale_order_id, receipt_date),
    KEY idx_sale_receipt_operator (farm_id, created_by, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='出栏收款（模块10）';

-- 2.3.6 批次核算（一个批次一行）
CREATE TABLE t_batch_settlement
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '核算 ID，示例 6001',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id          BIGINT UNSIGNED NOT NULL COMMENT '批次 ID（t_batch），一个批次一行',
    status            VARCHAR(20)     NOT NULL COMMENT '状态（SETTLEMENT_STATUS）：RUNNING 在养实时 / LOCKED 已锁定快照',
    settled_at        DATETIME        NULL COMMENT '锁定时间',
    sale_income       DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '销售收入：未作废出栏单销售金额合计',
    cost_feed         DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '饲料成本（模块09 物资成本 FEED）',
    feed_kg           DECIMAL(10, 2)  NOT NULL DEFAULT 0 COMMENT '饲料用量（千克）',
    cost_intro        DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '引种成本 = 入栏头数 × 引种单价；自繁为 0',
    intro_count       INT             NOT NULL DEFAULT 0 COMMENT '引种头数',
    intro_unit_price  DECIMAL(12, 2)  NULL COMMENT '引种单价；自繁为空',
    cost_drug         DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '兽药疫苗成本（模块09 物资成本 DRUG）',
    cost_other        DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '其他费用：关联批次、未作废、mark_only = 0 的手工支出',
    cost_total        DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '总成本 = 饲料 + 引种 + 兽药疫苗 + 其他',
    profit            DECIMAL(12, 2)  NOT NULL DEFAULT 0 COMMENT '盈亏 = 销售收入 − 总成本',
    sold_count        INT             NOT NULL DEFAULT 0 COMMENT '出栏头数',
    cost_per_head     DECIMAL(12, 2)  NULL COMMENT '单头成本：锁定按出栏头数，在养按当前存栏；基数为 0 时为空',
    profit_per_head   DECIMAL(12, 2)  NULL COMMENT '单头盈亏；出栏头数为 0 时为空',
    intro_weight_kg   DECIMAL(10, 2)  NULL COMMENT '入栏总重（千克）',
    sold_weight_kg    DECIMAL(10, 2)  NULL COMMENT '已出栏总重（千克）',
    net_gain_kg       DECIMAL(10, 2)  NULL COMMENT '净增重（千克）',
    fcr               DECIMAL(6, 2)   NULL COMMENT '料肉比 = 饲料千克 ÷ 净增重；净增重 ≤ 0 时为空',
    calc_at           DATETIME        NOT NULL COMMENT '最近计算时间',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本：确认补录时比较',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_settlement_batch (batch_id),
    KEY idx_batch_settlement_farm (farm_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次核算（模块10）';

-- 2.3.7 批次补录待确认
CREATE TABLE t_batch_settlement_supplement
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '补录 ID，示例 401',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id          BIGINT UNSIGNED NOT NULL COMMENT '批次 ID',
    settlement_id     BIGINT UNSIGNED NOT NULL COMMENT '核算 ID（t_batch_settlement）',
    source_type       VARCHAR(20)     NOT NULL COMMENT '来源：FINANCE_RECORD 手工收支 / MATERIAL_COST 模块09 物资成本 / BATCH_ENTRY 入栏引种',
    source_id         BIGINT UNSIGNED NOT NULL COMMENT '来源记录 ID',
    change_action     VARCHAR(20)     NOT NULL COMMENT '变化（SUPPLEMENT_ACTION）：ADD 新增 / EDIT 修改 / VOID 作废',
    biz_date          DATE            NOT NULL COMMENT '业务日期',
    title             VARCHAR(50)     NOT NULL COMMENT '标题，例如 运输费',
    cost_group        VARCHAR(20)     NOT NULL COMMENT '成本项（COST_GROUP）：FEED / INTRO / DRUG / OTHER',
    amount            DECIMAL(12, 2)  NOT NULL COMMENT '该成本项的变化额，可为负',
    qty_delta         DECIMAL(10, 2)  NULL COMMENT '饲料千克变化（cost_group = FEED），用于重算料肉比',
    change_request_id BIGINT UNSIGNED NULL COMMENT '由其他模块修改申请通过引起时关联',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态（SUPPLEMENT_STATUS）：PENDING / CONFIRMED / CANCELED',
    confirmed_by      BIGINT UNSIGNED NULL COMMENT '确认人用户 ID',
    confirmed_at      DATETIME        NULL COMMENT '确认时间',
    cancel_reason     VARCHAR(50)     NULL COMMENT '取消原因：来源已作废；批次已恢复在养',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_supplement_batch_status (batch_id, status),
    KEY idx_supplement_farm_status (farm_id, status),
    KEY idx_supplement_source (source_type, source_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='批次补录待确认（模块10）';

-- 2.3.8 AI 出栏建议
CREATE TABLE t_sale_advice
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '建议 ID，示例 7301',
    farm_id              BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    batch_id             BIGINT UNSIGNED NOT NULL COMMENT '批次 ID',
    status               VARCHAR(20)     NOT NULL COMMENT '状态（AI_GEN_STATUS）：GENERATING / SUCCESS / FAILED',
    gen_mode             VARCHAR(20)     NOT NULL COMMENT '生成方式（AI_GEN_MODE）：AI 调用模型 / RULE 休药期内按规则生成',
    conclusion           VARCHAR(20)     NULL COMMENT '结论（ADVICE_CONCLUSION）：SELL_SOON / KEEP_FEEDING / NOT_SELLABLE',
    window_start         DATE            NULL COMMENT '建议出栏窗口开始',
    window_end           DATE            NULL COMMENT '建议出栏窗口结束',
    sellable_date        DATE            NULL COMMENT '可出栏日期（休药期结束）',
    in_withdrawal        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '生成时是否在休药期内',
    withdrawal_days_left INT             NULL COMMENT '距可出栏日期天数',
    reason_withdrawal    VARCHAR(100)    NULL COMMENT '理由：休药',
    reason_age           VARCHAR(100)    NULL COMMENT '理由：日龄与存栏',
    reason_weight        VARCHAR(100)    NULL COMMENT '理由：体重',
    reason_price         VARCHAR(100)    NULL COMMENT '理由：行情',
    basis_insufficient   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '依据不足',
    basis_note           VARCHAR(100)    NULL COMMENT '依据不足说明',
    input_snapshot_json  JSON            NOT NULL COMMENT '生成时的输入快照（不含姓名、手机号、收购商）',
    ai_model             VARCHAR(50)     NULL COMMENT '模型名',
    prompt_version       VARCHAR(20)     NULL COMMENT '提示词版本',
    error_message        VARCHAR(200)    NULL COMMENT '失败原因',
    started_at           DATETIME        NULL COMMENT '开始生成时间',
    generated_at         DATETIME        NULL COMMENT '生成完成时间',
    created_by           BIGINT UNSIGNED NULL COMMENT '触发人用户 ID',
    created_at           DATETIME        NOT NULL COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_sale_advice_farm_gen (farm_id, generated_at),
    KEY idx_sale_advice_batch (batch_id, created_at),
    KEY idx_sale_advice_status (status, started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 出栏建议（模块10）';

CREATE TABLE t_sale_advice_risk
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '风险条目 ID',
    advice_id  BIGINT UNSIGNED NOT NULL COMMENT '建议 ID（t_sale_advice）',
    sort_no    INT             NOT NULL COMMENT '排序号 1–3',
    content    VARCHAR(60)     NOT NULL COMMENT '风险内容，单条 ≤ 60 字',
    created_at DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sale_advice_risk (advice_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 出栏建议风险条目（模块10）';

-- 2.3.9 报表期次
CREATE TABLE t_biz_report
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报表期次 ID，示例 6001',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    report_type  VARCHAR(20)     NOT NULL COMMENT '类型（REPORT_TYPE）：WEEKLY 周报 / MONTHLY 月报',
    period_start DATE            NOT NULL COMMENT '统计期开始',
    period_end   DATE            NOT NULL COMMENT '统计期结束',
    generated_at DATETIME        NOT NULL COMMENT '生成时间',
    notified     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'REPORT_READY 消息已发送',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_report_period (farm_id, report_type, period_start),
    KEY idx_biz_report_notified (notified, generated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报表期次（模块10）';

-- 2.3.10 报表 AI 摘要
CREATE TABLE t_biz_report_summary
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '摘要 ID',
    view_key       VARCHAR(160)    NOT NULL COMMENT '视图键：scope/report_type/period/range 拼接',
    scope_type     VARCHAR(20)     NOT NULL COMMENT '范围（REPORT_SCOPE）：FARM / ALL',
    farm_id        BIGINT UNSIGNED NULL COMMENT 'ALL 时为空',
    user_id        BIGINT UNSIGNED NULL COMMENT 'ALL 时必填；FARM 时为空，所有者与场长共用',
    report_type    VARCHAR(20)     NOT NULL COMMENT 'WEEKLY / MONTHLY / TO_DATE',
    period_start   DATE            NOT NULL COMMENT '统计期开始',
    period_end     DATE            NOT NULL COMMENT '统计期结束；TO_DATE 为生成当天',
    range_type     VARCHAR(20)     NOT NULL COMMENT '范围（REPORT_RANGE）：FARM / CATEGORY / BATCH',
    range_ref_id   BIGINT UNSIGNED NULL COMMENT '品类 ID 或批次 ID；整场为空',
    highlight      VARCHAR(200)    NULL COMMENT '亮点，≤ 100 字',
    problem        VARCHAR(200)    NULL COMMENT '问题，≤ 100 字',
    suggestion     VARCHAR(200)    NULL COMMENT '建议，≤ 100 字',
    data_hash      CHAR(64)        NOT NULL COMMENT '生成时指标快照的 SHA-256',
    status         VARCHAR(20)     NOT NULL COMMENT '状态（AI_GEN_STATUS）：GENERATING / SUCCESS / FAILED',
    error_message  VARCHAR(200)    NULL COMMENT '失败原因',
    ai_model       VARCHAR(50)     NULL COMMENT '模型名',
    prompt_version VARCHAR(20)     NULL COMMENT '提示词版本',
    started_at     DATETIME        NULL COMMENT '开始时间',
    generated_at   DATETIME        NULL COMMENT '生成时间',
    created_by     BIGINT UNSIGNED NULL COMMENT '创建人；定时任务为 0',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_report_summary_view (view_key),
    KEY idx_biz_report_summary_farm (farm_id, report_type, period_start),
    KEY idx_biz_report_summary_status (status, started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报表 AI 摘要（模块10）';

-- 2.3.11 报表导出记录
CREATE TABLE t_biz_report_export
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '导出记录 ID，示例 8801',
    scope_type   VARCHAR(20)     NOT NULL COMMENT '范围（REPORT_SCOPE）：FARM / ALL',
    farm_id      BIGINT UNSIGNED NULL COMMENT 'ALL 时为空',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '导出人用户 ID',
    report_type  VARCHAR(20)     NOT NULL COMMENT 'WEEKLY / MONTHLY / TO_DATE',
    period_start DATE            NOT NULL COMMENT '统计期开始',
    period_end   DATE            NOT NULL COMMENT '统计期结束',
    range_type   VARCHAR(20)     NOT NULL COMMENT 'REPORT_RANGE：FARM / CATEGORY / BATCH',
    range_ref_id BIGINT UNSIGNED NULL COMMENT '品类 ID 或批次 ID',
    file_format  VARCHAR(10)     NOT NULL COMMENT '格式（EXPORT_FORMAT）：XLSX / PDF',
    file_id      BIGINT UNSIGNED NULL COMMENT '文件 ID（t_file，scene = EXPORT）',
    status       VARCHAR(20)     NOT NULL COMMENT 'SUCCESS / FAILED',
    error_message VARCHAR(200)   NULL COMMENT '失败原因',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_biz_report_export_user (user_id, created_at),
    KEY idx_biz_report_export_farm (farm_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报表导出记录（模块10）';

-- 行情筛选发布配置（管理端-02 发布；本模块只读启用项）
CREATE TABLE t_market_price_config
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置 ID',
    province_code VARCHAR(10)     NOT NULL COMMENT '省份行政区划代码，例如 150000',
    province_name VARCHAR(20)     NOT NULL COMMENT '省份名称，例如 内蒙古',
    category_id   BIGINT UNSIGNED NOT NULL COMMENT '品类 ID（t_category）',
    breed_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '品种 ID；0 为全部品种',
    price_type    VARCHAR(20)     NOT NULL COMMENT '价格类型（MARKET_PRICE_TYPE）：SLAUGHTER 出栏价 / SEEDLING 种苗价',
    status        VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态（BASE_STATUS）：ENABLED 已启用 / DISABLED 已停用',
    sort_no       INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_market_config (province_code, category_id, breed_id, price_type),
    KEY idx_market_config_status (status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='行情筛选发布配置（模块10）';

-- 行情每日价格（只读 status = PUBLISHED 的行；breed_id = 0 为全部品种）
CREATE TABLE t_market_price
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '行情价 ID',
    province_code  VARCHAR(10)     NOT NULL COMMENT '省份行政区划代码',
    province_name  VARCHAR(20)     NOT NULL COMMENT '省份名称',
    category_id    BIGINT UNSIGNED NOT NULL COMMENT '品类 ID',
    breed_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '品种 ID；0 为全部品种',
    price_type     VARCHAR(20)     NOT NULL COMMENT 'SLAUGHTER / SEEDLING',
    price_date     DATE            NOT NULL COMMENT '价格日期',
    price          DECIMAL(10, 2)  NOT NULL COMMENT '价格',
    price_unit     VARCHAR(20)     NOT NULL COMMENT '计价单位：YUAN_PER_KG / YUAN_PER_HEAD / YUAN_PER_BIRD',
    price_unit_text VARCHAR(20)    NOT NULL COMMENT '单位文字：元/千克、元/头、元/只',
    corrected      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '价格是否被修正过',
    corrected_at   DATETIME        NULL COMMENT '修正时间',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED 已发布 / OFFLINE 已下线',
    source         VARCHAR(30)     NULL COMMENT '数据来源；第一批不接外部源，种子为 SEED',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_market_price_day (province_code, category_id, breed_id, price_type, price_date),
    KEY idx_market_price_lookup (province_code, category_id, breed_id, price_type, status, price_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='行情每日价格（模块10）';
