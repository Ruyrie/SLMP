-- 用户端 biz 包：updateById 默认跳过 null，“置空”没有落库留下的残留数据。只修能从当前状态判定的行，可重复执行。

-- 繁育：空怀母猪不应再挂着当前周期（妊检阴性、断奶、产仔无活仔后 current_cycle_id 没清掉）
UPDATE t_breeding_animal
SET current_cycle_id = NULL
WHERE status = 'EMPTY'
  AND current_cycle_id IS NOT NULL;

-- 繁育：撤销死亡淘汰后仍在档的种畜残留离场信息
UPDATE t_breeding_animal
SET exit_type          = NULL,
    exit_date          = NULL,
    exit_reason        = NULL,
    status_before_exit = NULL,
    exit_by            = NULL,
    exit_at            = NULL
WHERE status NOT IN ('CULLED', 'DEAD')
  AND (exit_type IS NOT NULL OR exit_date IS NOT NULL OR exit_reason IS NOT NULL
    OR status_before_exit IS NOT NULL OR exit_by IS NOT NULL OR exit_at IS NOT NULL);

-- 繁育：进行中的周期（已配种 / 已妊娠）不应有结束信息（妊检阴性改阳性、撤销离场后没清）；
-- 已离场种畜的当前周期保留 EXIT 结束信息，供撤销时恢复
UPDATE t_breeding_cycle c
    JOIN t_breeding_animal a ON a.id = c.animal_id
SET c.end_date   = NULL,
    c.end_reason = NULL
WHERE c.status IN ('MATED', 'PREGNANT')
  AND (c.end_date IS NOT NULL OR c.end_reason IS NOT NULL)
  AND NOT (c.end_reason = 'EXIT' AND a.status IN ('CULLED', 'DEAD'));

-- 饲喂：作废的投喂记录要释放 active_task_id（唯一索引，否则这餐不能重新登记），任务不再指向作废记录
UPDATE t_feed_record
SET active_task_id = NULL
WHERE status = 'VOID'
  AND active_task_id IS NOT NULL;

UPDATE t_feed_task t
    JOIN t_feed_record r ON r.id = t.record_id
SET t.record_id = NULL
WHERE r.status = 'VOID';

-- 栏舍：启用中的栏舍残留停用时间 / 停用人
UPDATE t_pen
SET disabled_at = NULL,
    disabled_by = NULL
WHERE status = 'ENABLED'
  AND (disabled_at IS NOT NULL OR disabled_by IS NOT NULL);

-- 巡检：非异常记录残留异常来源 / 摘要；未手动标记的残留标记原因、标记人和时间
UPDATE t_inspection
SET abnormal_source  = NULL,
    abnormal_summary = NULL
WHERE is_abnormal = 0
  AND (abnormal_source IS NOT NULL OR abnormal_summary IS NOT NULL);

UPDATE t_inspection
SET manual_reason    = NULL,
    manual_marked_by = NULL,
    manual_marked_at = NULL
WHERE is_manual_marked = 0
  AND (manual_reason IS NOT NULL OR manual_marked_by IS NOT NULL OR manual_marked_at IS NOT NULL);

-- 异常上报：AI 诊断已完成的残留“暂不可用”开始时间
UPDATE t_abnormal_report
SET unavailable_since = NULL
WHERE ai_state = 'DONE'
  AND unavailable_since IS NOT NULL;

-- 出栏单：修改申请已结束仍显示“修改审核中”；未收齐的单子残留结清日期
UPDATE t_sale_order o
    JOIN t_change_request c ON c.id = o.pending_change_request_id
SET o.pending_change_request_id = NULL
WHERE c.status <> 'PENDING';

UPDATE t_sale_order
SET paid_off_date = NULL
WHERE payment_status IN ('UNPAID', 'PARTIAL')
  AND paid_off_date IS NOT NULL;

-- 免疫计划 / 消毒任务：待执行（含逾期）的不应再关联记录（审批作废记录后没解除）
UPDATE t_immune_plan
SET record_id = NULL
WHERE status = 'PENDING'
  AND record_id IS NOT NULL;

UPDATE t_disinfect_task
SET record_id = NULL
WHERE status IN ('PENDING', 'OVERDUE')
  AND record_id IS NOT NULL;

-- 兽药领用：领用对象在批次 / 栏舍之间改过后另一边残留（登记时只写其中一个）；流水同步
UPDATE t_stock_outbound
SET pen_id = NULL
WHERE target_type = 'BATCH'
  AND pen_id IS NOT NULL;

UPDATE t_stock_outbound
SET batch_id = NULL
WHERE target_type = 'PEN'
  AND batch_id IS NOT NULL;

UPDATE t_stock_flow f
    JOIN t_stock_outbound o ON o.flow_id = f.id
SET f.pen_id = NULL
WHERE o.target_type = 'BATCH'
  AND f.pen_id IS NOT NULL;

UPDATE t_stock_flow f
    JOIN t_stock_outbound o ON o.flow_id = f.id
SET f.batch_id = NULL
WHERE o.target_type = 'PEN'
  AND f.batch_id IS NOT NULL;

-- 盘点：明细指向已作废的差异流水（差异改为 0 后没解除）
UPDATE t_stocktake_item i
    JOIN t_stock_flow f ON f.id = i.flow_id
SET i.flow_id = NULL
WHERE f.status = 'VOID';

-- 转码成功的清晰度、经营报表 AI 摘要生成成功后残留上次的错误信息
UPDATE t_file_variant
SET error_message = NULL
WHERE status = 'SUCCESS'
  AND error_message IS NOT NULL;

UPDATE t_biz_report_summary
SET error_message = NULL
WHERE status = 'SUCCESS'
  AND error_message IS NOT NULL;
