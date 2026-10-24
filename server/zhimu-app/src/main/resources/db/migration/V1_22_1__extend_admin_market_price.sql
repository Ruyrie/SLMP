-- 管理端-02 内容运营：市场行情发布（WA-C02，ADM-C11–C21）
-- 在模块10 已交付的 t_market_price / t_market_price_config 上补充管理端字段，不改变用户端读取逻辑。
-- 已执行的脚本不再修改。

ALTER TABLE t_market_price_config
    ADD COLUMN default_unit VARCHAR(20) NOT NULL DEFAULT 'YUAN_PER_KG'
        COMMENT '默认计价单位（PRICE_UNIT）' AFTER price_type;

UPDATE t_market_price_config
SET default_unit = 'YUAN_PER_HEAD'
WHERE price_type = 'SEEDLING';

ALTER TABLE t_market_price
    ADD COLUMN prev_price      DECIMAL(12, 2) NULL COMMENT '上一个有数据日期的已发布价' AFTER price,
    ADD COLUMN prev_price_date DATE           NULL COMMENT '上一个有数据日期' AFTER prev_price,
    ADD COLUMN change_rate     DECIMAL(7, 4)  NULL COMMENT '较上一个有数据日期的涨跌，0.0400 = 4%' AFTER prev_price_date,
    ADD COLUMN is_need_review  TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '涨跌绝对值 > 20% 需复核' AFTER change_rate,
    ADD COLUMN reviewer_id     BIGINT UNSIGNED NULL COMMENT '复核管理员 ID' AFTER is_need_review,
    ADD COLUMN reviewed_at     DATETIME       NULL COMMENT '复核时间' AFTER reviewer_id,
    ADD COLUMN review_result   VARCHAR(20)    NULL COMMENT 'REVIEW_RESULT：PASS / RETURN' AFTER reviewed_at,
    ADD COLUMN review_note     VARCHAR(100)   NULL COMMENT '复核说明' AFTER review_result,
    ADD COLUMN entered_by      BIGINT UNSIGNED NULL COMMENT '录入管理员 ID' AFTER review_note,
    ADD COLUMN entered_at      DATETIME       NULL COMMENT '录入时间' AFTER entered_by,
    ADD COLUMN remark          VARCHAR(100)   NULL COMMENT '备注（复核人可见）' AFTER entered_at,
    ADD COLUMN version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER remark;

CREATE INDEX idx_market_price_date_status ON t_market_price (price_date, status);

CREATE TABLE t_market_price_correction
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '更正记录 ID',
    price_id          BIGINT UNSIGNED NOT NULL COMMENT '价格行 ID（t_market_price）',
    price_date        DATE            NOT NULL COMMENT '价格日期',
    old_price         DECIMAL(12, 2)  NOT NULL COMMENT '更正前价格',
    new_price         DECIMAL(12, 2)  NOT NULL COMMENT '更正后价格',
    change_rate_after DECIMAL(7, 4)   NULL COMMENT '更正后较上一个有数据日期',
    reason            VARCHAR(100)    NOT NULL COMMENT '更正原因（仅管理员可见）',
    status            VARCHAR(20)     NOT NULL COMMENT 'CORRECTION_STATUS：PENDING_REVIEW / APPLIED / RETURNED',
    pending_flag      TINYINT(1)      NULL COMMENT '待复核为 1，处理后置空；唯一键保证一条价格只有一条待复核更正',
    corrected_by      BIGINT UNSIGNED NOT NULL COMMENT '更正管理员 ID',
    corrected_at      DATETIME        NOT NULL COMMENT '更正时间',
    reviewer_id       BIGINT UNSIGNED NULL COMMENT '复核管理员 ID',
    reviewed_at       DATETIME        NULL COMMENT '复核时间',
    review_note       VARCHAR(100)    NULL COMMENT '复核说明',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_market_correction_pending (price_id, pending_flag),
    KEY idx_market_correction_status (status, corrected_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='行情更正记录（管理端-02）';
