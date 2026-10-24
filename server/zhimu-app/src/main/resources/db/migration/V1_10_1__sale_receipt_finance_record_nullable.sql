-- 收款先插入 t_sale_receipt 生成 ID，再创建收入流水并回填 finance_record_id。
-- 原列为 NOT NULL + UNIQUE，插入时还没有流水 ID；改为可空（唯一索引允许多个 NULL，回填后仍唯一）。
ALTER TABLE t_sale_receipt
    MODIFY COLUMN finance_record_id BIGINT UNSIGNED NULL COMMENT '关联收入流水（t_finance_record），插入后回填';
