-- 库存流水“经办人”补齐（只改 operator_id 仍为空的行，可在已有数据上增量、重复执行）。
-- 入库、报损 / 报废、盘点、其他出库 / 退货的流水此前没写经办人，按单据登记人补齐；领用流水保留领用人。
-- 报废流水的关联文字“批号 xxx”保持不变（App 在客户端去重）。
UPDATE t_stock_flow f
    JOIN t_stock_inbound i ON f.biz_type = 'INBOUND' AND f.biz_id = i.id
SET f.operator_id = i.created_by
WHERE f.operator_id IS NULL
  AND i.created_by IS NOT NULL
  AND i.created_by > 0;

UPDATE t_stock_flow f
    JOIN t_stock_loss l ON f.biz_type = 'LOSS' AND f.biz_id = l.id
SET f.operator_id = l.created_by
WHERE f.operator_id IS NULL
  AND l.created_by IS NOT NULL
  AND l.created_by > 0;

UPDATE t_stock_flow f
    JOIN t_stocktake s ON f.biz_type = 'STOCKTAKE' AND f.biz_id = s.id
SET f.operator_id = s.created_by
WHERE f.operator_id IS NULL
  AND s.created_by IS NOT NULL
  AND s.created_by > 0;

UPDATE t_stock_flow f
    JOIN t_stock_outbound o ON f.biz_type = 'OUTBOUND' AND f.biz_id = o.id
SET f.operator_id = COALESCE(o.receiver_id, o.created_by)
WHERE f.operator_id IS NULL
  AND COALESCE(o.receiver_id, o.created_by) IS NOT NULL
  AND COALESCE(o.receiver_id, o.created_by) > 0;
