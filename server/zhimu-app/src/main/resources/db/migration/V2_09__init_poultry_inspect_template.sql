-- 肉鸡基础巡检模板：独立于育肥猪模板，供用户端正常登记并可在管理端继续维护。
-- 只补启用品类 201 完全没有模板的情况；管理员已有草稿、线上或历史版本均不改动。
-- 不指定业务 ID，避免与管理员新增的数据冲突；整个初始化在同一事务中完成。
START TRANSACTION;

INSERT INTO t_inspect_template
    (category_id, name, online_version_no, has_draft, created_by, updated_by, version, created_at, updated_at)
SELECT c.id, '肉鸡基础巡检模板', 1, 0, 0, 0, 0, NOW(), NOW()
FROM t_category c
WHERE c.id = 201 AND c.species = 'POULTRY' AND c.status = 'ENABLED'
  AND NOT EXISTS (SELECT 1 FROM t_inspect_template t WHERE t.category_id = c.id);

SET @poultry_template_id = IF(ROW_COUNT() = 1, LAST_INSERT_ID(), NULL);

INSERT INTO t_inspect_template_version
    (template_id, version_no, status, draft_flag, change_note, item_count,
     published_by, published_at, created_by, updated_by, version, created_at, updated_at)
SELECT @poultry_template_id, 1, 'PUBLISHED', NULL, '初始化肉鸡基础巡检项目：精神状态、采食、粪便', 3,
       0, NOW(), 0, 0, 0, NOW(), NOW()
WHERE @poultry_template_id IS NOT NULL;

SET @poultry_version_id = IF(@poultry_template_id IS NOT NULL, LAST_INSERT_ID(), NULL);

INSERT INTO t_inspect_item
    (template_id, version_id, source_item_id, change_type, sort_no, item_name, check_note,
     enabled, disable_reason, created_by, created_at, updated_at)
SELECT @poultry_template_id, @poultry_version_id, NULL, 'NONE', s.sort_no, s.item_name, s.check_note,
       1, NULL, 0, NOW(), NOW()
FROM (
    SELECT 1 AS sort_no, '精神状态' AS item_name, '观察鸡群精神与活动情况' AS check_note
    UNION ALL SELECT 2, '采食', '观察采食情况与饲料剩余'
    UNION ALL SELECT 3, '粪便', '观察粪便形状和颜色'
) s
WHERE @poultry_version_id IS NOT NULL;

INSERT INTO t_inspect_option
    (item_id, sort_no, option_name, is_abnormal, created_by, created_at, updated_at)
SELECT i.id, s.sort_no, s.option_name, s.is_abnormal, 0, NOW(), NOW()
FROM t_inspect_item i
JOIN (
    SELECT '精神状态' AS item_name, 1 AS sort_no, '正常' AS option_name, 0 AS is_abnormal
    UNION ALL SELECT '精神状态', 2, '精神不振', 1
    UNION ALL SELECT '精神状态', 3, '活动减少', 1
    UNION ALL SELECT '采食', 1, '正常', 0
    UNION ALL SELECT '采食', 2, '减少', 1
    UNION ALL SELECT '采食', 3, '不采食', 1
    UNION ALL SELECT '粪便', 1, '正常', 0
    UNION ALL SELECT '粪便', 2, '稀软', 1
    UNION ALL SELECT '粪便', 3, '水样', 1
    UNION ALL SELECT '粪便', 4, '带血', 1
) s ON s.item_name = i.item_name
WHERE i.version_id = @poultry_version_id;

COMMIT;
