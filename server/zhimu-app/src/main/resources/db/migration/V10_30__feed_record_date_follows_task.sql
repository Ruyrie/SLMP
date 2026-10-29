-- 饲喂执行情况：柱子有投喂量、点开按日明细却“没有饲喂任务”（M13）。
-- 原因：示例脚本 V9_26 给已完成任务补投喂记录时，记录登记的平移锚点（2026-10-25）与所属任务的锚点
-- （V9_20 登记 2026-10-19）不同；全新库平移后记录比任务少移 6 天，投喂日期与任务日期错开
-- （例：ZM2610-031 10/22 的两条记录挂在 10/28 的任务上）。
-- 修复：投喂日期以所属任务日期为准（业务写入时本来就是 feed_date = task_date）；创建时间同步移动且不晚于现在（北京时间），
-- 更新时间保留真实修改时间、只保证不早于创建时间；有演示平移登记表时，把记录的锚点对齐到所属任务，后续平移两者一起移动。
-- 只处理日期不一致的记录，可在已有数据上增量执行；生产库没有演示登记表时只做第一步。

DROP TEMPORARY TABLE IF EXISTS tmp_feed_record_realign;
CREATE TEMPORARY TABLE tmp_feed_record_realign
(
    id          BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    feed_date   DATE            NOT NULL,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL
);

INSERT INTO tmp_feed_record_realign (id, feed_date, created_at, updated_at)
SELECT x.id, x.task_date, x.new_created, GREATEST(x.updated_at, x.new_created)
FROM (SELECT r.id,
             t.task_date,
             r.updated_at,
             LEAST(r.created_at + INTERVAL DATEDIFF(t.task_date, r.feed_date) DAY,
                   UTC_TIMESTAMP() + INTERVAL 8 HOUR) AS new_created
      FROM t_feed_record r
               JOIN t_feed_task t ON t.id = r.task_id
      WHERE r.feed_date <> t.task_date) x;

UPDATE t_feed_record r
    JOIN tmp_feed_record_realign x ON x.id = r.id
SET r.feed_date  = x.feed_date,
    r.created_at = x.created_at,
    r.updated_at = x.updated_at;

DROP TEMPORARY TABLE IF EXISTS tmp_feed_record_realign;

SET @zmt_has_demo_registry := (SELECT COUNT(*)
                               FROM information_schema.TABLES
                               WHERE TABLE_SCHEMA = DATABASE()
                                 AND TABLE_NAME = 't_demo_sample_row');
SET @zmt_sql := IF(@zmt_has_demo_registry > 0,
                   'UPDATE t_demo_sample_row rr
                        JOIN t_feed_record r ON rr.table_name = ''t_feed_record'' AND rr.row_id = r.id
                        JOIN t_demo_sample_row tr ON tr.table_name = ''t_feed_task'' AND tr.row_id = r.task_id
                    SET rr.anchor_date = tr.anchor_date
                    WHERE rr.anchor_date <> tr.anchor_date',
                   'DO 0');
PREPARE zmt_stmt FROM @zmt_sql;
EXECUTE zmt_stmt;
DEALLOCATE PREPARE zmt_stmt;
