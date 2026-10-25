-- 兽药批号结存与库存流水对平。
-- 历史种子 V9_10 的批号初始结存与流水是分别手填的：6001 少 100、6103 少 1500、6111 多 800、6301 多 10。
-- 以 NORMAL 流水为准重算每个批号的结存、金额与每条流水的 after 值；6111 有一笔 10-01 出库早于 10-07 入库，
-- 补一条 100 的期初结存流水，保证流水按时间排序后余额不为负。

-- 1) 6111 期初结存（只在不存在时插入）
INSERT INTO t_stock_flow (id, farm_id, material_type, drug_id, drug_lot_id, flow_type, direction, qty,
                          unit_price, amount, stock_qty_after, stock_amount_after, biz_type, biz_id,
                          related_desc, occur_at, biz_date, is_batch_cost, is_feed_usage,
                          origin_flow_id, status, created_at, updated_at)
SELECT 290001, 1001, 'DRUG', 5202, 6111, 'INBOUND', 'IN', 100.000,
       0.1400, 14.00, 100.000, 14.00, 'OPENING', 6111,
       '期初结存', '2026-09-30 08:30:00', '2026-09-30', 0, 0,
       290001, 'NORMAL', '2026-09-30 08:30:00', '2026-09-30 08:30:00'
WHERE NOT EXISTS (SELECT 1 FROM t_stock_flow WHERE id = 290001);

-- 2) 按 occur_at、id 重算每条 NORMAL 流水的结存与金额
DROP TEMPORARY TABLE IF EXISTS tmp_flow_balance;
CREATE TEMPORARY TABLE tmp_flow_balance AS
SELECT id,
       SUM(qty_signed) OVER (PARTITION BY drug_lot_id ORDER BY occur_at, id)    AS qty_after,
       SUM(amount_signed) OVER (PARTITION BY drug_lot_id ORDER BY occur_at, id) AS amount_after
FROM (SELECT id, drug_lot_id, occur_at,
             CASE WHEN direction = 'IN' THEN qty ELSE -qty END       AS qty_signed,
             CASE WHEN direction = 'IN' THEN amount ELSE -amount END AS amount_signed
      FROM t_stock_flow
      WHERE status = 'NORMAL' AND drug_lot_id IS NOT NULL) flows;
UPDATE t_stock_flow f
    JOIN tmp_flow_balance b ON b.id = f.id
SET f.stock_qty_after = b.qty_after,
    f.stock_amount_after = b.amount_after;
DROP TEMPORARY TABLE tmp_flow_balance;

-- 3) 批号结存/金额/均价取全量 NORMAL 流水净额
UPDATE t_drug_lot l
    JOIN (SELECT drug_lot_id,
                 SUM(CASE WHEN direction = 'IN' THEN qty ELSE -qty END)       AS qty,
                 SUM(CASE WHEN direction = 'IN' THEN amount ELSE -amount END) AS amount
          FROM t_stock_flow
          WHERE status = 'NORMAL' AND drug_lot_id IS NOT NULL
          GROUP BY drug_lot_id) f ON f.drug_lot_id = l.id
SET l.stock_qty = f.qty,
    l.stock_amount = f.amount,
    l.avg_price = CASE WHEN f.qty > 0 THEN ROUND(f.amount / f.qty, 4) ELSE l.avg_price END,
    l.updated_at = NOW();
