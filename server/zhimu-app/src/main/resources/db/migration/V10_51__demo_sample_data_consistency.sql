-- 2026-10-29 复测发现的示例数据问题（只改示例行：按示例行 ID 定位，且只在值仍是原示例值时才改；生产库没有这些行时不生效，可重复执行）。

-- 1) 示例出栏单 9106（ZM2610-019，60 头）开在休药期内：批次入栏当天开始用氟苯尼考粉（休药 20 天），出栏日与入栏只差 14 天，
--    无论怎么挪出栏日期或用药日期都会违反休药期或早于入栏；而且这张单从没扣过批次存栏（批次 152 入栏 / 2 死淘 / 150 存栏、
--    已出栏 0）。把它改成“已作废”：休药期内不再有有效出栏单，批次存栏、核算口径也一致；“休药中”示例与其他出栏单不受影响。
--    待收款待办由启动时的对账（ReceivableTodos.reconcileAll）按“出栏单已作废”关闭；在养批次核算打开详情时重算。
UPDATE t_sale_order
SET payment_status  = 'VOIDED',
    unpaid_amount   = 0.00,
    void_reason     = '批次在休药期内，不能出栏，登记作废',
    void_source     = 'DIRECT',
    voided_at       = LEAST(created_at + INTERVAL 20 MINUTE, UTC_TIMESTAMP() + INTERVAL 8 HOUR),
    voided_by       = 10001,
    content_version = content_version + 1,
    version         = version + 1,
    updated_by      = 10001,
    updated_at      = GREATEST(updated_at, LEAST(created_at + INTERVAL 20 MINUTE, UTC_TIMESTAMP() + INTERVAL 8 HOUR))
WHERE id = 9106
  AND client_request_id = 'sample-sale-9106'
  AND batch_id = 3002
  AND payment_status = 'UNPAID'
  AND quantity = 60
  AND received_amount = 0
  AND receipt_count = 0
  AND pending_change_request_id IS NULL;

-- 2) ZM2609-012（批次 3010）核算头部“出栏 99 头”，出栏单 9102 只有 98 头：出栏单、收款、核算（出栏 98 / 引种 100）互相一致，
--    错的是批次汇总的 sold_count = 99（为凑“入栏 100 − 死淘 1 − 出栏 99 = 0”）。改为出栏 98，差的 1 头记到那条
--    “出栏前死亡”死淘上（死淘 1 → 2），批次仍是全部出栏、存栏 0，金额都不变。
SET @zmt_fix_3010 := (SELECT COUNT(*)
                      FROM t_batch
                      WHERE id = 3010 AND batch_no = 'ZM2609-012' AND entry_count = 100 AND death_count = 1
                        AND sold_count = 99 AND current_count = 0 AND deleted = 0)
                     * (SELECT COUNT(*)
                        FROM t_batch_event
                        WHERE id = 910 AND batch_id = 3010 AND event_type = 'DEATH' AND quantity = 1
                          AND status = 'EFFECTIVE' AND deleted = 0)
                     * (SELECT IF(COALESCE(SUM(quantity), 0) = 98, 1, 0)
                        FROM t_sale_order
                        WHERE batch_id = 3010 AND payment_status <> 'VOIDED');

UPDATE t_batch_event
SET quantity = 2
WHERE id = 910 AND batch_id = 3010 AND quantity = 1 AND @zmt_fix_3010 = 1;

UPDATE t_batch_timeline
SET title = '死淘 · 2 头 · 其他'
WHERE id = 8109 AND batch_id = 3010 AND biz_id = 910 AND title = '死淘 · 1 头 · 其他' AND @zmt_fix_3010 = 1;

UPDATE t_batch
SET death_count = 2,
    sold_count  = 98
WHERE id = 3010 AND death_count = 1 AND sold_count = 99 AND @zmt_fix_3010 = 1;

-- 3) 饲喂执行“需要关注”把 −3%、−5% 的餐次标成少投：V9_26 补的投喂记录（id = 295000 + 任务 ID）按“低于 98%”写死了 UNDER，
--    阈值快照写的 5%，与代码口径（偏差绝对值大于阈值才异常、阈值取饲喂方案，示例方案都是 10%）不符。
--    按所属批次饲喂方案的阈值重写快照并重算异常类型；只处理仍是原示例值的记录。
UPDATE t_feed_record r
    JOIN t_feed_plan p ON p.batch_id = r.batch_id AND p.farm_id = r.farm_id
SET r.abnormal_threshold = p.abnormal_threshold,
    r.abnormal_type      = CASE
                               WHEN ABS(r.deviation_ratio) <= p.abnormal_threshold THEN 'NONE'
                               WHEN r.deviation_ratio < 0 THEN 'UNDER'
                               ELSE 'OVER' END
WHERE r.id = 295000 + r.task_id
  AND r.request_id = CONCAT('seed-feed-', r.task_id)
  AND r.version = 0
  AND r.abnormal_threshold = 0.0500
  AND r.abnormal_type = CASE WHEN r.deviation_ratio < -0.02 THEN 'UNDER' ELSE 'NONE' END;

-- 4) 示例批次 ZM2610-072（3272）时间线“入栏 · 8480 只 / 出栏 · 8480 只”：改成程序生成的写法并加千分位
--    （与其他批次的“入栏 152 头”“出栏 8,480 只”一致）。
UPDATE t_batch_timeline
SET title = '入栏 8,480 只'
WHERE id = 210205 AND batch_id = 3272 AND title = '入栏 · 8480 只';

UPDATE t_batch_timeline
SET title = '出栏 8,480 只'
WHERE id = 210206 AND batch_id = 3272 AND title = '出栏 · 8480 只';
