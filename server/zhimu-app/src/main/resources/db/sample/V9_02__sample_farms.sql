-- 模块02 第 1 批示例数据（dev / test 的 db/sample，V9_01 之后执行；不建外键，允许单独执行）
-- 养殖场 1001 东山猪场 / 1002 西岭鸡场 / 1003 北湾牛场，成员 5001–5005，示例邀请 6001–6002
-- 用户 10001 王建国、10002 刘芳、10003 李明 由 V9_01 插入；本脚本不重复插入用户，也不建 t_pen / t_category。
-- 与 App Mock 对齐：1002 所有者是刘芳（10002），王建国（10001）是 1002 的场长（A19 的 exit_farms 包含 1002）。

INSERT INTO t_farm (id, name, owner_user_id, main_category_id, scale, contact_name, contact_phone, intro, address,
                    province, city, district, township, adcode, longitude, latitude, coord_type, loc_accuracy,
                    audit_status, latest_audit_id, approved_at, disabled, disabled_at, disabled_by,
                    frozen, frozen_at, frozen_reason, frozen_by, delete_type, deleted_at,
                    version, deleted, created_by, updated_by, created_at, updated_at)
VALUES (1001, '东山猪场', 10001, 101, 1500, '王建国', '13800006620',
        '以杜长大三元育肥猪为主，共 7 栋栏舍', '赤峰市松山区太平地镇',
        '内蒙古自治区', '赤峰市', '松山区', '太平地镇', '150404', 118.8516000, 42.3025000, 'GCJ02', NULL,
        'APPROVED', NULL, '2026-03-04 10:00:00', 0, NULL, NULL,
        0, NULL, NULL, NULL, NULL, NULL,
        3, 0, 10001, 10001, '2026-03-02 09:00:00', '2026-03-04 10:00:00'),
       (1002, '西岭鸡场', 10002, 201, 20000, '刘芳', '13900001234',
        '白羽肉鸡平养，共 4 栋鸡舍', '赤峰市元宝山区平庄镇',
        '内蒙古自治区', '赤峰市', '元宝山区', '平庄镇', '150403', 119.2876543, 42.0456789, 'GCJ02', NULL,
        'APPROVED', NULL, '2026-06-10 10:00:00', 0, NULL, NULL,
        0, NULL, NULL, NULL, NULL, NULL,
        1, 0, 10002, 10002, '2026-04-02 09:30:00', '2026-06-10 10:00:00'),
       (1003, '北湾牛场', 10001, 301, 120, '王建国', '13800006620',
        '西门塔尔肉牛育肥', '赤峰市松山区太平地镇 英金河北岸',
        '内蒙古自治区', '赤峰市', '松山区', '太平地镇', '150404', 118.9123456, 42.3412345, 'GCJ02', NULL,
        'PENDING', 3002, NULL, 0, NULL, NULL,
        0, NULL, NULL, NULL, NULL, NULL,
        2, 0, 10001, 10001, '2026-10-17 18:32:00', '2026-10-19 08:46:00');

INSERT INTO t_farm_audit (id, farm_id, submit_no, status, pending_flag, submitted_at, snapshot_json,
                          change_summary, reshoot_file_ids, audit_opinion, reject_reason, audited_by, audited_at,
                          cancel_reason, request_id, version, created_by, created_at, updated_at)
VALUES (3001, 1003, 1, 'REJECTED', NULL, '2026-10-17 18:32:00',
        '{"name":"北湾牛场","mainCategoryId":301,"scale":120,"contactName":"王建国","contactPhone":"13800006620","address":"赤峰市松山区太平地镇 英金河北岸","province":"内蒙古自治区","city":"赤峰市","district":"松山区","township":"太平地镇","adcode":"150404","longitude":118.9123456,"latitude":42.3412345,"locAccuracy":null,"qualificationFileIds":[]}',
        NULL, '[]', NULL, '资质图中的《动物防疫条件合格证》照片模糊，看不清证书编号和有效期。', 1, '2026-10-18 16:20:00',
        NULL, 'sample-audit-1003-1', 1, 10001, '2026-10-17 18:32:00', '2026-10-18 16:20:00'),
       (3002, 1003, 2, 'PENDING', 1, '2026-10-19 08:46:00',
        '{"name":"北湾牛场","mainCategoryId":301,"scale":120,"contactName":"王建国","contactPhone":"13800006620","address":"赤峰市松山区太平地镇 英金河北岸","province":"内蒙古自治区","city":"赤峰市","district":"松山区","township":"太平地镇","adcode":"150404","longitude":118.9123456,"latitude":42.3412345,"locAccuracy":null,"qualificationFileIds":[]}',
        '重新拍摄了被标为“需重拍”的 1 张资质图，其他注册信息未修改', '[]', NULL, NULL, NULL, NULL,
        NULL, 'sample-audit-1003-2', 0, 10001, '2026-10-19 08:46:00', '2026-10-19 08:46:00');

INSERT INTO t_farm_member (id, farm_id, user_id, role, status, active_flag, join_type, invite_id, joined_at,
                           left_at, removed_by, last_switch_at, version, created_by, updated_by, created_at, updated_at)
VALUES (5001, 1001, 10001, 'OWNER', 'ACTIVE', 1, 'CREATE', NULL, '2026-03-02 09:00:00',
        NULL, NULL, '2026-10-19 07:30:00', 3, 10001, 10001, '2026-03-02 09:00:00', '2026-10-19 07:30:00'),
       (5002, 1001, 10002, 'MANAGER', 'ACTIVE', 1, 'INVITE', NULL, '2026-03-12 10:00:00',
        NULL, NULL, '2026-10-16 18:10:00', 2, 10001, 10001, '2026-03-12 10:00:00', '2026-10-16 18:10:00'),
       (5003, 1001, 10003, 'STAFF', 'ACTIVE', 1, 'INVITE', NULL, '2026-05-06 10:00:00',
        NULL, NULL, '2026-10-19 06:50:00', 3, 10001, 10001, '2026-05-06 10:00:00', '2026-10-19 06:50:00'),
       (5004, 1002, 10002, 'OWNER', 'ACTIVE', 1, 'CREATE', NULL, '2026-04-02 09:30:00',
        NULL, NULL, '2026-10-18 09:20:00', 1, 10002, 10002, '2026-04-02 09:30:00', '2026-10-18 09:20:00'),
       (5005, 1002, 10001, 'MANAGER', 'ACTIVE', 1, 'INVITE', NULL, '2026-04-05 10:00:00',
        NULL, NULL, '2026-10-16 18:10:00', 1, 10002, 10002, '2026-04-05 10:00:00', '2026-10-16 18:10:00');

INSERT INTO t_member_pen (id, farm_id, member_id, user_id, pen_id, created_by, created_at)
VALUES (5201, 1001, 5003, 10003, 2003, 10001, '2026-05-06 10:05:00'),
       (5202, 1001, 5003, 10003, 2004, 10001, '2026-05-06 10:05:00');

INSERT INTO t_farm_invite (id, farm_id, invitee_phone, invitee_user_id, role, status, pending_flag, expires_at,
                           handled_at, resend_of_id, member_id, cancel_reason, request_id, created_by, created_at, updated_at)
VALUES (6001, 1001, '13904762051', NULL, 'STAFF', 'PENDING', 1, '2026-10-24 09:30:00',
        NULL, NULL, NULL, NULL, 'sample-invite-6001', 10001, '2026-10-17 09:30:00', '2026-10-17 09:30:00'),
       (6002, 1001, '13604768817', NULL, 'MANAGER', 'EXPIRED', NULL, '2026-10-10 10:00:00',
        '2026-10-10 10:00:00', NULL, NULL, NULL, 'sample-invite-6002', 10001, '2026-10-03 10:00:00', '2026-10-10 10:00:00');

INSERT INTO t_farm_invite_pen (id, invite_id, farm_id, pen_id, created_by, created_at)
VALUES (6101, 6001, 1001, 2005, 10001, '2026-10-17 09:30:00'),
       (6102, 6001, 1001, 2006, 10001, '2026-10-17 09:30:00');
