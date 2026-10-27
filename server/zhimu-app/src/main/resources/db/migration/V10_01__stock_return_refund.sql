-- 实际到账后才确认退货退款；不回填任何历史退货单，不按库存成本生成收入。
ALTER TABLE t_stock_outbound
    ADD COLUMN refund_amount DECIMAL(12, 2) NULL COMMENT '实际到账退款金额',
    ADD COLUMN refund_date DATE NULL COMMENT '实际到账日期',
    ADD COLUMN refund_remark VARCHAR(200) NULL COMMENT '退款说明',
    ADD COLUMN refund_registered_at DATETIME NULL COMMENT '首次登记退款时间，更正不改变',
    ADD COLUMN refund_registered_by BIGINT UNSIGNED NULL COMMENT '首次登记退款人',
    ADD COLUMN refund_request_id VARCHAR(64) NULL COMMENT '首次登记退款幂等键',
    ADD COLUMN finance_record_id BIGINT UNSIGNED NULL COMMENT '唯一关联退款收入流水',
    ADD UNIQUE KEY uk_stock_outbound_refund_req (farm_id, refund_request_id),
    ADD UNIQUE KEY uk_stock_outbound_refund_finance (finance_record_id);

-- t_finance_record 已有 uk_finance_record_source(source_type,source_id)，确保一单一笔收入。
INSERT INTO t_finance_category
    (direction, code, name, icon, sort_no, status, auto_source, mark_only, is_fallback,
     version, created_by, updated_by, created_at, updated_at)
VALUES ('INCOME', 'DRUG_RETURN_REFUND', '药苗退货退款', 'assignment_return', 90, 'ENABLED',
        'DRUG_RETURN_REFUND', 0, 0, 0, 0, 0, NOW(), NOW());
