-- 管理端-03 平台初始化数据：育肥猪巡检模板（ADM-D39–D48 由 3B 做，本脚本由 3A 按 App Mock 落数据）
-- 模板 id=11、version=2、items 101–103、options 1011–1034，与 App Mock
-- module_inspect/src/debug/assets/mock/inspect/form_2001_registered.json 完全一致（用户端 API-U13 由 3B 提供）。
-- 已发布版本不再修改；模块07 巡检结果保存 item_id、option_id 与名称快照。

INSERT INTO t_inspect_template (id, category_id, name, online_version_no, has_draft,
                                created_by, updated_by, version, created_at, updated_at)
VALUES (11, 101, '育肥猪巡检模板', 2, 0, 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

INSERT INTO t_inspect_template_version (id, template_id, version_no, status, draft_flag, change_note, item_count,
                                        published_by, published_at,
                                        created_by, updated_by, version, created_at, updated_at)
VALUES (1101, 11, 2, 'PUBLISHED', NULL, '初始化育肥猪巡检项目：精神状态、采食、粪便', 3,
        1, '2026-09-01 10:00:00', 0, 1, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

INSERT INTO t_inspect_item (id, template_id, version_id, source_item_id, change_type, sort_no, item_name,
                            check_note, enabled, disable_reason, created_by, created_at, updated_at)
VALUES (101, 11, 1101, NULL, 'NONE', 1, '精神状态', '观察是否沉郁、兴奋不安', 1, NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (102, 11, 1101, NULL, 'NONE', 2, '采食', '看栏内饲料剩余与采食速度', 1, NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (103, 11, 1101, NULL, 'NONE', 3, '粪便', '看栏内粪便形状和颜色', 1, NULL, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00');

INSERT INTO t_inspect_option (id, item_id, sort_no, option_name, is_abnormal, created_by, created_at, updated_at)
VALUES (1011, 101, 1, '正常', 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1012, 101, 2, '沉郁', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1013, 101, 3, '兴奋不安', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1021, 102, 1, '正常', 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1022, 102, 2, '减少', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1023, 102, 3, '废绝', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1031, 103, 1, '正常', 0, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1032, 103, 2, '稀软', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1033, 103, 3, '水样', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00'),
       (1034, 103, 4, '带血', 1, 0, '2026-09-01 10:00:00', '2026-09-01 10:00:00');
