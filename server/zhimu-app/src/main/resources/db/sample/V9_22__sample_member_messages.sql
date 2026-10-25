-- 消息按真实接收人及业务对象补齐；不把所有者的私有财务消息复制给员工。
-- 固定基准日交给 V9_20 的显式样例注册表在启动时平移，不改用户自己产生的消息。
INSERT IGNORE INTO t_message
(id,user_id,farm_id,receiver_role,category,msg_type,level,title,summary,
 biz_type,biz_id,todo_id,route,extra_json,merge_key,merge_count,sort_time,
 hidden,deleted,created_by,created_at,updated_at)
SELECT 220001,10002,t.farm_id,'OWNER','TODO','TODO_REMIND','NORMAL',t.title,t.content_text,
 t.biz_type,t.biz_id,t.id,'G02',JSON_OBJECT('display_status','PENDING','pen_name',t.pen_name,
 'due_text','按今日饲喂时间登记'),CONCAT('TODO:',t.id),1,'2026-10-19 08:10:00',
 0,0,0,'2026-10-19 08:10:00','2026-10-19 08:10:00'
FROM t_todo t JOIN t_farm_member m ON m.farm_id=t.farm_id AND m.user_id=10002
WHERE t.id=210411 AND t.deleted=0 AND m.status='ACTIVE' AND m.active_flag=1;

INSERT IGNORE INTO t_message
(id,user_id,farm_id,receiver_role,category,msg_type,level,title,summary,
 biz_type,biz_id,todo_id,route,extra_json,merge_key,merge_count,sort_time,
 hidden,deleted,created_by,created_at,updated_at)
SELECT 220002,10002,t.farm_id,'OWNER','TODO','TODO_REMIND','NORMAL',t.title,t.content_text,
 t.biz_type,t.biz_id,t.id,'D03',JSON_OBJECT('display_status','PENDING','pen_name',t.pen_name,
 'due_text','按免疫计划完成登记'),CONCAT('TODO:',t.id),1,'2026-10-19 08:20:00',
 0,0,0,'2026-10-19 08:20:00','2026-10-19 08:20:00'
FROM t_todo t JOIN t_farm_member m ON m.farm_id=t.farm_id AND m.user_id=10002
WHERE t.id=210401 AND t.deleted=0 AND m.status='ACTIVE' AND m.active_flag=1;

INSERT IGNORE INTO t_message
(id,user_id,farm_id,receiver_role,category,msg_type,level,title,summary,
 biz_type,biz_id,todo_id,route,extra_json,merge_key,merge_count,sort_time,
 hidden,deleted,created_by,created_at,updated_at)
SELECT 220003,10003,t.farm_id,'STAFF','TODO','TODO_REMIND','NORMAL',t.title,t.content_text,
 t.biz_type,t.biz_id,t.id,'G02',JSON_OBJECT('display_status','PENDING','pen_name',t.pen_name,
 'due_text','按今日饲喂时间登记'),CONCAT('TODO:',t.id),1,'2026-10-19 08:15:00',
 0,0,0,'2026-10-19 08:15:00','2026-10-19 08:15:00'
FROM t_todo t JOIN t_farm_member m ON m.farm_id=t.farm_id AND m.user_id=10003
JOIN t_member_pen p ON p.member_id=m.id AND p.pen_id=t.pen_id
WHERE t.id=90108 AND t.deleted=0 AND m.status='ACTIVE' AND m.active_flag=1;

INSERT IGNORE INTO t_message
(id,user_id,farm_id,receiver_role,category,msg_type,level,title,summary,
 biz_type,biz_id,route,extra_json,merge_count,sort_time,
 hidden,deleted,created_by,created_at,updated_at)
SELECT 220004,10003,1001,'STAFF','TODO','STAFF_NOTIFY','NORMAL',
 '3 号育肥舍 · 用药与休药提醒','登记用药后及时查看批次休药状态',
 'BATCH',3001,'D05',JSON_OBJECT('batch_id',3001,'pen_name','3 号育肥舍',
 'content','请按实际用药记录核对休药状态，完成饲喂和巡检登记。'),1,'2026-10-19 08:25:00',
 0,0,0,'2026-10-19 08:25:00','2026-10-19 08:25:00'
FROM t_farm_member m JOIN t_member_pen p ON p.member_id=m.id AND p.pen_id=2003
WHERE m.farm_id=1001 AND m.user_id=10003 AND m.status='ACTIVE' AND m.active_flag=1;

-- 复用现有平台通知正文，每位收件人保留独立的已读状态。
INSERT IGNORE INTO t_message
(id,user_id,farm_id,receiver_role,category,msg_type,level,title,summary,tag_code,tag_text,
 biz_type,biz_id,route,extra_json,merge_count,sort_time,hidden,deleted,created_by,created_at,updated_at)
SELECT recipients.message_id,recipients.user_id,NULL,NULL,m.category,m.msg_type,m.level,m.title,m.summary,
 m.tag_code,m.tag_text,m.biz_type,m.biz_id,m.route,m.extra_json,1,'2026-10-18 16:00:00',
 0,0,0,'2026-10-18 16:00:00','2026-10-18 16:00:00'
FROM t_message m CROSS JOIN (SELECT 220005 message_id,10002 user_id UNION ALL SELECT 220006,10003) recipients
WHERE m.id=507;

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date)
SELECT 't_message',id,'SAMPLE','2026-10-19' FROM t_message
WHERE id IN (220001,220002,220003,220004,220005,220006);
