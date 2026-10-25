-- 修复北湾牛场演示记录：说明写着重拍，但原始快照图片列表为空。
-- 只补 sample-audit-* 的空图片列表；已有实际提交的图片不替换。
-- PNG 随服务端打包，由 SampleQualificationImages 在 dev/test 启动时写入私有存储桶。
INSERT IGNORE INTO t_file
(id,bucket,object_key,original_name,file_kind,scene,mime_type,file_ext,file_size,width,height,
 upload_status,transcode_status,uploader_type,client_type,bound,created_by,created_at,updated_at,deleted)
SELECT asset.id,'zmt-private',CONCAT('demo/farm-qualifications/',asset.name),asset.title,
       'IMAGE','FARM_QUAL','image/png','png',0,1200,840,
       'UPLOADING','NONE','USER','WEB',1,10001,a.submitted_at,a.submitted_at,0
FROM (
    SELECT 240101 AS id,'permit-before.png' AS name,'防疫条件合格证（模拟旧图）.png' AS title,3001 AS audit_id
    UNION ALL SELECT 240102,'registration.png','养殖场登记材料（模拟）.png',3001
    UNION ALL SELECT 240103,'layout.png','场区布局示意（模拟）.png',3001
    UNION ALL SELECT 240104,'permit-after.png','防疫条件合格证（模拟重拍）.png',3002
) asset JOIN t_farm_audit a ON a.id=asset.audit_id AND a.farm_id=1003
WHERE a.request_id IN ('sample-audit-1003-1','sample-audit-1003-2');

UPDATE t_farm_audit
SET snapshot_json=JSON_SET(snapshot_json,
        '$.qualificationFileIds',JSON_ARRAY(240101,240102,240103),
        '$.qualification_file_ids',JSON_ARRAY(240101,240102,240103)),
    reshoot_file_ids=JSON_ARRAY(240101)
WHERE id=3001 AND farm_id=1003 AND request_id='sample-audit-1003-1'
  AND COALESCE(JSON_LENGTH(snapshot_json,'$.qualificationFileIds'),0)=0
  AND COALESCE(JSON_LENGTH(snapshot_json,'$.qualification_file_ids'),0)=0;

UPDATE t_farm_audit
SET snapshot_json=JSON_SET(snapshot_json,
        '$.qualificationFileIds',JSON_ARRAY(240104,240102,240103),
        '$.qualification_file_ids',JSON_ARRAY(240104,240102,240103))
WHERE id=3002 AND farm_id=1003 AND request_id='sample-audit-1003-2'
  AND COALESCE(JSON_LENGTH(snapshot_json,'$.qualificationFileIds'),0)=0
  AND COALESCE(JSON_LENGTH(snapshot_json,'$.qualification_file_ids'),0)=0;

-- 同步当前养殖场的图片关联，保留重拍后的一张和沿用的两张。
INSERT IGNORE INTO t_biz_media
(id,biz_type,biz_id,file_id,farm_id,sort_no,created_by,created_at,updated_at,deleted)
SELECT link.id,'FARM',1003,link.file_id,1003,link.sort_no,10001,a.submitted_at,a.submitted_at,0
FROM (
    SELECT 240101 AS id,240104 AS file_id,1 AS sort_no
    UNION ALL SELECT 240102,240102,2
    UNION ALL SELECT 240103,240103,3
) link JOIN t_farm_audit a ON a.id=3002 AND a.request_id='sample-audit-1003-2'
JOIN t_farm f ON f.id=a.farm_id AND f.latest_audit_id=a.id AND f.deleted=0
WHERE JSON_CONTAINS(a.snapshot_json,CAST(link.file_id AS JSON),'$.qualificationFileIds');

INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date)
SELECT 't_file',id,'SAMPLE',(SELECT anchor_date FROM t_demo_anchor WHERE group_code='SAMPLE')
FROM t_file WHERE id IN (240101,240102,240103,240104) AND object_key LIKE 'demo/farm-qualifications/%';
INSERT IGNORE INTO t_demo_sample_row(table_name,row_id,group_code,anchor_date)
SELECT 't_biz_media',id,'SAMPLE',(SELECT anchor_date FROM t_demo_anchor WHERE group_code='SAMPLE')
FROM t_biz_media WHERE id IN (240101,240102,240103) AND biz_type='FARM' AND biz_id=1003;
