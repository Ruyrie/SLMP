-- 管理端-02 内容运营（养殖课堂 C03、知识库 C04）与 ADM-S34–S38 媒体能力。
-- 用户端模块11 已建 t_course* / t_ai_knowledge*，本脚本只补管理端字段与配套表，不重建、不移动已有数据。
-- 已执行的脚本不再修改；本脚本只新增列、索引与表，并对旧数据做幂等回填。

-- ===== 1. 课程 =====
ALTER TABLE t_course
    ADD COLUMN intro            VARCHAR(200)    NULL COMMENT '简介' AFTER title,
    ADD COLUMN video_file_id    BIGINT UNSIGNED NULL COMMENT '原片 t_file（scene = COURSE_VIDEO）',
    ADD COLUMN transcode_status VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'TRANSCODE_STATUS，冗余自 t_file',
    ADD COLUMN cover_file_id    BIGINT UNSIGNED NULL COMMENT '封面 t_file（COURSE_COVER 或自动截取）',
    ADD COLUMN cover_source     VARCHAR(20)     NULL COMMENT 'COVER_SOURCE：AUTO / UPLOAD',
    ADD COLUMN on_shelf_at      DATETIME        NULL COMMENT '上架时间',
    ADD COLUMN on_shelf_by      BIGINT UNSIGNED NULL COMMENT '上架管理员',
    ADD COLUMN off_shelf_at     DATETIME        NULL COMMENT '下架时间',
    ADD COLUMN off_shelf_by     BIGINT UNSIGNED NULL COMMENT '下架管理员',
    ADD COLUMN off_reason       VARCHAR(100)    NULL COMMENT '下架原因（只给管理员看）',
    ADD COLUMN play_count       INT             NOT NULL DEFAULT 0 COMMENT '累计播放次数',
    ADD COLUMN viewer_count     INT             NOT NULL DEFAULT 0 COMMENT '累计观看人数',
    ADD COLUMN finish_count     INT             NOT NULL DEFAULT 0 COMMENT '累计学完人数',
    ADD COLUMN created_by       BIGINT UNSIGNED NULL COMMENT '创建管理员（系统为 0）',
    ADD COLUMN updated_by       BIGINT UNSIGNED NULL COMMENT '最后修改管理员',
    ADD COLUMN version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    ADD COLUMN deleted          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    ADD INDEX idx_course_video_file (video_file_id),
    ADD INDEX idx_course_transcode (transcode_status),
    ADD INDEX idx_course_status_updated (status, updated_at);

-- 旧课程（模块11 示例数据）已有可播放媒体，转码视为完成，避免管理端显示 NONE
UPDATE t_course c
SET c.transcode_status = 'SUCCESS'
WHERE c.status IN ('ON_SHELF', 'OFF_SHELF')
   OR EXISTS (SELECT 1 FROM t_course_media m WHERE m.course_id = c.id AND m.status = 'SUCCESS');

UPDATE t_course c
SET c.play_count   = (SELECT COUNT(*) FROM t_course_play_record r WHERE r.course_id = c.id),
    c.viewer_count = (SELECT COUNT(DISTINCT r.user_id) FROM t_course_play_record r WHERE r.course_id = c.id),
    c.finish_count = (SELECT COUNT(*) FROM t_course_progress p WHERE p.course_id = c.id AND p.completed = 1);

-- ===== 2. 课程专题 =====
ALTER TABLE t_course_topic
    ADD COLUMN cover_file_id BIGINT UNSIGNED NULL COMMENT '方形封面 t_file（TOPIC_COVER）',
    ADD COLUMN off_reason    VARCHAR(100)    NULL COMMENT '下架原因',
    ADD COLUMN created_by    BIGINT UNSIGNED NULL COMMENT '创建管理员',
    ADD COLUMN updated_by    BIGINT UNSIGNED NULL COMMENT '最后修改管理员',
    ADD COLUMN version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁';

-- ===== 3. 课程与知识分类（复用 t_course_category） =====
ALTER TABLE t_course_category
    ADD COLUMN created_by BIGINT UNSIGNED NULL COMMENT '创建管理员',
    ADD COLUMN updated_by BIGINT UNSIGNED NULL COMMENT '最后修改管理员',
    ADD COLUMN version    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁';

-- ===== 4. 知识条目 =====
ALTER TABLE t_ai_knowledge
    ADD COLUMN category_id       BIGINT UNSIGNED NULL COMMENT '分类 → t_course_category' AFTER category_name,
    ADD COLUMN source_ref_id     BIGINT UNSIGNED NULL COMMENT '来源引用：兽药目录条目 ID',
    ADD COLUMN keywords          VARCHAR(100)    NULL COMMENT '线上版本关键词，顿号分隔',
    ADD COLUMN has_video         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '线上版本正文含课堂视频',
    ADD COLUMN online_version_id BIGINT UNSIGNED NULL COMMENT '线上版本 t_ai_knowledge_version.id',
    ADD COLUMN cite_count_30d    INT             NOT NULL DEFAULT 0 COMMENT '近 30 天被 AI 引用次数',
    ADD COLUMN useful_rate_30d   DECIMAL(5, 4)   NULL COMMENT '近 30 天有用率',
    ADD COLUMN stat_at           DATETIME        NULL COMMENT '统计时间',
    ADD COLUMN published_by      BIGINT UNSIGNED NULL COMMENT '发布管理员',
    ADD COLUMN off_reason        VARCHAR(100)    NULL COMMENT '下架原因',
    ADD COLUMN off_at            DATETIME        NULL COMMENT '下架时间',
    ADD COLUMN off_by            BIGINT UNSIGNED NULL COMMENT '下架管理员',
    ADD COLUMN created_by        BIGINT UNSIGNED NULL COMMENT '创建管理员',
    ADD COLUMN updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改管理员',
    ADD COLUMN version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    ADD COLUMN deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    ADD INDEX idx_ai_knowledge_category_id (category_id, status),
    ADD INDEX idx_ai_knowledge_updated (updated_at);

UPDATE t_ai_knowledge k
    JOIN t_course_category c ON c.name = k.category_name
SET k.category_id = c.id;

-- ===== 5. 知识段落：补管理端字段 =====
ALTER TABLE t_ai_knowledge_paragraph
    ADD COLUMN version_no      INT             NULL COMMENT '所属已发布版本号' AFTER para_no,
    ADD COLUMN image_file_id   BIGINT UNSIGNED NULL COMMENT 'IMAGE 段落的 t_file',
    ADD COLUMN cite_count_30d  INT             NOT NULL DEFAULT 0 COMMENT '近 30 天被引用次数',
    ADD COLUMN is_online       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '线上版本段落，AI 只检索为 1 的段落';

UPDATE t_ai_knowledge_paragraph p
    JOIN t_ai_knowledge k ON k.id = p.knowledge_id
SET p.version_no = k.version_no,
    p.is_online  = CASE WHEN k.status = 'ON_SHELF' THEN 1 ELSE 0 END;

-- ===== 6. 知识版本（含草稿） =====
CREATE TABLE t_ai_knowledge_version
(
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '版本 ID',
    knowledge_id            BIGINT UNSIGNED NOT NULL COMMENT '知识条目 ID',
    version_no              INT             NULL COMMENT '已发布版本号；草稿为空',
    status                  VARCHAR(20)     NOT NULL COMMENT 'VERSION_STATUS：DRAFT / PUBLISHED / SUPERSEDED',
    draft_flag              TINYINT(1)      NULL COMMENT '草稿为 1，发布后置空；唯一键保证每个条目最多一条草稿',
    title                   VARCHAR(100)    NOT NULL COMMENT '版本标题快照',
    category_id             BIGINT UNSIGNED NULL COMMENT '版本分类快照',
    display_type            VARCHAR(20)     NOT NULL DEFAULT 'ARTICLE' COMMENT 'KNOWLEDGE_DISPLAY_TYPE',
    is_major_epidemic       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '重大动物疫病',
    applicable_category_ids VARCHAR(100)    NULL COMMENT '适用品类快照，逗号分隔；空为全部',
    keywords                VARCHAR(100)    NULL COMMENT '关键词，顿号分隔',
    content                 MEDIUMTEXT      NOT NULL COMMENT '受限 HTML 正文（带 data-para-key）',
    content_text            MEDIUMTEXT      NULL COMMENT '纯文字正文',
    change_note             VARCHAR(100)    NULL COMMENT '修改说明 / 回退说明',
    rollback_from_no        INT             NULL COMMENT '回退来源版本号',
    source_task_id          BIGINT UNSIGNED NULL COMMENT '来源知识补充任务',
    published_by            BIGINT UNSIGNED NULL COMMENT '发布管理员',
    published_at            DATETIME        NULL COMMENT '发布时间',
    created_by              BIGINT UNSIGNED NULL COMMENT '草稿保存人 / 创建人',
    updated_by              BIGINT UNSIGNED NULL COMMENT '最后保存人',
    created_at              DATETIME        NOT NULL COMMENT '创建时间',
    updated_at              DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_knowledge_version (knowledge_id, version_no),
    UNIQUE KEY uk_ai_knowledge_draft (knowledge_id, draft_flag),
    KEY idx_ai_knowledge_version_status (knowledge_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识条目版本（含草稿）';

-- 旧数据补一条当前线上版本：正文由现有段落合成，段落键保留，回退时可直接复制
SET SESSION group_concat_max_len = 4194304;

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
GROUP BY k.id;

UPDATE t_ai_knowledge k
    JOIN t_ai_knowledge_version v
    ON v.knowledge_id = k.id AND v.version_no = k.version_no AND v.status = 'PUBLISHED'
SET k.online_version_id = v.id;

UPDATE t_ai_knowledge k
SET k.has_video = EXISTS(SELECT 1 FROM t_ai_knowledge_paragraph p
                         WHERE p.knowledge_id = k.id AND p.para_type = 'VIDEO');

UPDATE t_ai_knowledge k
SET k.cite_count_30d = (SELECT COUNT(DISTINCT c.message_id)
                        FROM t_ai_citation c
                        WHERE c.knowledge_id = k.id
                          AND c.created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY));

-- ===== 7. 知识补充任务 =====
CREATE TABLE t_ai_knowledge_task
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
    title               VARCHAR(40)     NOT NULL COMMENT '任务标题',
    source_type         VARCHAR(20)     NOT NULL COMMENT 'TASK_SOURCE：AI_QA / CORRECTION / MANUAL',
    source_message_id   BIGINT UNSIGNED NULL COMMENT '来源问答 t_ai_message.id',
    mode                VARCHAR(20)     NOT NULL COMMENT 'TASK_MODE：NEW_ENTRY / SUPPLEMENT',
    target_knowledge_id BIGINT UNSIGNED NULL COMMENT 'SUPPLEMENT 时的目标条目',
    category_id         BIGINT UNSIGNED NOT NULL COMMENT '分类 → t_course_category',
    assignee_id         BIGINT UNSIGNED NOT NULL COMMENT '被指派管理员',
    due_date            DATE            NOT NULL COMMENT '期望完成日期',
    points              VARCHAR(200)    NOT NULL COMMENT '补充要点',
    similar_count       INT             NOT NULL DEFAULT 0 COMMENT '近 30 天相似问题数',
    status              VARCHAR(20)     NOT NULL COMMENT 'TASK_STATUS：PENDING / DONE / CLOSED',
    result_knowledge_id BIGINT UNSIGNED NULL COMMENT '完成时的条目',
    result_version_no   INT             NULL COMMENT '完成时的版本号',
    completed_at        DATETIME        NULL COMMENT '完成时间',
    close_reason        VARCHAR(100)    NULL COMMENT '关闭原因',
    closed_by           BIGINT UNSIGNED NULL COMMENT '关闭人',
    closed_at           DATETIME        NULL COMMENT '关闭时间',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT UNSIGNED NULL COMMENT '创建管理员',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_ai_knowledge_task_status (status, assignee_id, due_date),
    KEY idx_ai_knowledge_task_target (target_knowledge_id),
    KEY idx_ai_knowledge_task_source (source_message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识补充任务';

-- ===== 8. 内容适用品类 =====
CREATE TABLE t_content_applicable
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联 ID',
    biz_type    VARCHAR(20)     NOT NULL COMMENT 'CONTENT_BIZ_TYPE：COURSE / KNOWLEDGE',
    biz_id      BIGINT UNSIGNED NOT NULL COMMENT '课程 ID 或知识条目 ID',
    category_id BIGINT UNSIGNED NOT NULL COMMENT '品类 → t_category',
    created_at  DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_applicable (biz_type, biz_id, category_id),
    KEY idx_content_applicable_category (category_id, biz_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='内容适用品类；没有记录表示适用全部品类';

-- ===== 9. 课程每日统计 =====
CREATE TABLE t_course_stat_daily
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '统计 ID',
    course_id            BIGINT UNSIGNED NOT NULL COMMENT '课程 ID',
    stat_date            DATE            NOT NULL COMMENT '统计日期',
    play_count           INT             NOT NULL DEFAULT 0 COMMENT '当日播放次数',
    viewer_count         INT             NOT NULL DEFAULT 0 COMMENT '观看人数',
    finish_count         INT             NOT NULL DEFAULT 0 COMMENT '当日首次学完人数',
    watch_seconds        INT             NOT NULL DEFAULT 0 COMMENT '当日实际观看秒数合计',
    app_play_count       INT             NOT NULL DEFAULT 0 COMMENT 'App 播放次数',
    web_play_count       INT             NOT NULL DEFAULT 0 COMMENT '网页播放次数',
    p720_play_count      INT             NOT NULL DEFAULT 0 COMMENT '720P 播放次数',
    p480_play_count      INT             NOT NULL DEFAULT 0 COMMENT '480P 播放次数',
    mobile_play_count    INT             NOT NULL DEFAULT 0 COMMENT '移动网络播放次数',
    ask_ai_count         INT             NOT NULL DEFAULT 0 COMMENT '点“问 AI”次数',
    knowledge_click_count INT            NOT NULL DEFAULT 0 COMMENT '点“图文原文”次数',
    created_at           DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_stat_daily (course_id, stat_date),
    KEY idx_course_stat_daily_date (stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程每日统计（00:30 汇总，重跑覆盖）';
