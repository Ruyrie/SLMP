-- 管理端-02 课程/知识库示例数据补口（dev / test 的 db/sample，V9_06 之后执行）。
-- V9_06 已插入课程、专题、知识条目与段落，但那时管理端字段尚未建好；
-- 本脚本只补分类 ID、版本、转码状态与统计缓存，不修改任何已执行的 V1/V2/V9 脚本。

SET SESSION group_concat_max_len = 4194304;

UPDATE t_ai_knowledge k
    JOIN t_course_category c ON c.name = k.category_name
SET k.category_id = c.id
WHERE k.category_id IS NULL;

UPDATE t_ai_knowledge_paragraph p
    JOIN t_ai_knowledge k ON k.id = p.knowledge_id
SET p.version_no = k.version_no,
    p.is_online  = CASE WHEN k.status = 'ON_SHELF' THEN 1 ELSE 0 END;

UPDATE t_course c
SET c.transcode_status = 'SUCCESS'
WHERE EXISTS (SELECT 1 FROM t_course_media m WHERE m.course_id = c.id AND m.status = 'SUCCESS');

UPDATE t_course c
SET c.play_count   = (SELECT COUNT(*) FROM t_course_play_record r WHERE r.course_id = c.id),
    c.viewer_count = (SELECT COUNT(DISTINCT r.user_id) FROM t_course_play_record r WHERE r.course_id = c.id),
    c.finish_count = (SELECT COUNT(*) FROM t_course_progress p WHERE p.course_id = c.id AND p.completed = 1);

UPDATE t_ai_knowledge k
SET k.has_video = EXISTS(SELECT 1 FROM t_ai_knowledge_paragraph p
                         WHERE p.knowledge_id = k.id AND p.para_type = 'VIDEO');

INSERT INTO t_ai_knowledge_version (knowledge_id, version_no, status, draft_flag, title, category_id, display_type,
                                    is_major_epidemic, applicable_category_ids, keywords, content, content_text,
                                    change_note, rollback_from_no, source_task_id, published_by, published_at,
                                    created_by, updated_by, created_at, updated_at)
SELECT k.id,
       k.version_no,
       'PUBLISHED',
       NULL,
       k.title,
       k.category_id,
       k.display_type,
       k.is_major_epidemic,
       NULL,
       NULL,
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
       '创建条目',
       NULL,
       NULL,
       k.published_by,
       k.published_at,
       k.created_by,
       NULL,
       COALESCE(k.created_at, CURRENT_TIMESTAMP),
       COALESCE(k.updated_at, CURRENT_TIMESTAMP)
FROM t_ai_knowledge k
         LEFT JOIN t_ai_knowledge_paragraph p ON p.knowledge_id = k.id
WHERE k.version_no >= 1
  AND NOT EXISTS (SELECT 1 FROM t_ai_knowledge_version v WHERE v.knowledge_id = k.id)
GROUP BY k.id;

UPDATE t_ai_knowledge k
    JOIN t_ai_knowledge_version v
    ON v.knowledge_id = k.id AND v.version_no = k.version_no AND v.status = 'PUBLISHED'
SET k.online_version_id = v.id
WHERE k.online_version_id IS NULL;
