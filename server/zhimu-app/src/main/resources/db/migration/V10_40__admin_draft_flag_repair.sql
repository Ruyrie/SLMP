-- 2026-10-29 回归修复 H9：知识库发布时 draft_flag 没有清空（updateById 跳过 null）。
-- 被影响的数据有两种：
--   1. 已发布 / 已替代的版本仍带 draft_flag = 1：直接清空标记。
--   2. 发布后又“存草稿”，线上版本那一行被改成了 DRAFT（version_no 还在、正文已是草稿内容）：
--      这一行恢复为纯草稿（version_no 置空），再按线上段落重建该版本号的线上版本，条目重新指向它。
-- 顺带：资讯价格更正复核后 pending_flag 同样没清；上架课程 / 专题残留的下架原因。
-- 只按标记识别，可在云端已有数据上重复执行，无匹配时不改任何行。

SET SESSION group_concat_max_len = 4194304;

DROP TEMPORARY TABLE IF EXISTS tmp_kv_broken;
CREATE TEMPORARY TABLE tmp_kv_broken AS
SELECT v.id AS version_id, v.knowledge_id, v.version_no, v.published_by, v.published_at
FROM t_ai_knowledge_version v
WHERE v.draft_flag = 1
  AND v.version_no IS NOT NULL
  AND v.status = 'DRAFT';

UPDATE t_ai_knowledge_version v
    JOIN tmp_kv_broken b ON b.version_id = v.id
SET v.version_no   = NULL,
    v.published_by = NULL,
    v.published_at = NULL;

INSERT INTO t_ai_knowledge_version (knowledge_id, version_no, status, draft_flag, title, category_id, display_type,
                                    is_major_epidemic, applicable_category_ids, keywords, content, content_text,
                                    change_note, rollback_from_no, source_task_id, published_by, published_at,
                                    created_by, updated_by, created_at, updated_at)
SELECT k.id,
       b.version_no,
       CASE WHEN k.version_no = b.version_no THEN 'PUBLISHED' ELSE 'SUPERSEDED' END,
       NULL,
       k.title,
       k.category_id,
       k.display_type,
       k.is_major_epidemic,
       (SELECT GROUP_CONCAT(a.category_id ORDER BY a.category_id SEPARATOR ',')
        FROM t_content_applicable a
        WHERE a.biz_type = 'KNOWLEDGE' AND a.biz_id = k.id),
       k.keywords,
       COALESCE(GROUP_CONCAT(
               CASE p.para_type
                   WHEN 'HEADING' THEN CONCAT('<h2 data-para-key="', p.para_key, '">',
                                              COALESCE(NULLIF(p.content_html, ''), p.content_text, ''), '</h2>')
                   WHEN 'QUOTE' THEN CONCAT('<blockquote data-para-key="', p.para_key, '">',
                                            COALESCE(NULLIF(p.content_html, ''), p.content_text, ''), '</blockquote>')
                   WHEN 'LIST' THEN CONCAT('<ul data-para-key="', p.para_key, '">',
                                           COALESCE(NULLIF(p.content_html, ''),
                                                    CONCAT('<li>', COALESCE(p.content_text, ''), '</li>')), '</ul>')
                   WHEN 'IMAGE' THEN CONCAT('<img data-para-key="', p.para_key, '" src="',
                                            COALESCE(p.image_url, ''), '"/>')
                   WHEN 'VIDEO' THEN CONCAT('<div data-type="video" data-course-id="',
                                            COALESCE(p.course_id, 0), '" data-para-key="', p.para_key, '"></div>')
                   ELSE CONCAT('<p data-para-key="', p.para_key, '">',
                               COALESCE(NULLIF(p.content_html, ''), p.content_text, ''), '</p>')
                   END ORDER BY p.para_no SEPARATOR ''), ''),
       COALESCE(GROUP_CONCAT(COALESCE(p.content_text, '') ORDER BY p.para_no SEPARATOR '\n'), ''),
       '数据修复：按线上段落还原',
       NULL,
       NULL,
       COALESCE(b.published_by, k.published_by),
       COALESCE(b.published_at, k.published_at),
       k.created_by,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM tmp_kv_broken b
         JOIN t_ai_knowledge k ON k.id = b.knowledge_id
         LEFT JOIN t_ai_knowledge_paragraph p ON p.knowledge_id = k.id
GROUP BY b.version_id, k.id, b.version_no, b.published_by, b.published_at;

UPDATE t_ai_knowledge k
    JOIN t_ai_knowledge_version v
    ON v.knowledge_id = k.id AND v.version_no = k.version_no AND v.status = 'PUBLISHED'
SET k.online_version_id = v.id
WHERE k.id IN (SELECT knowledge_id FROM tmp_kv_broken);

DROP TEMPORARY TABLE IF EXISTS tmp_kv_broken;

UPDATE t_ai_knowledge_version
SET draft_flag = NULL
WHERE draft_flag = 1
  AND version_no IS NOT NULL;

UPDATE t_market_price_correction
SET pending_flag = NULL
WHERE pending_flag = 1
  AND status <> 'PENDING_REVIEW';

UPDATE t_course
SET off_reason   = NULL,
    off_shelf_at = NULL,
    off_shelf_by = NULL
WHERE status = 'ON_SHELF'
  AND (off_reason IS NOT NULL OR off_shelf_at IS NOT NULL OR off_shelf_by IS NOT NULL);

UPDATE t_course_topic
SET off_reason = NULL
WHERE status = 'ON_SHELF'
  AND off_reason IS NOT NULL;
