-- 模块11 AI 养殖助手与养殖课堂（模块11 服务端技术准备文档 2.3 节）
-- 13 张表：AI 会话、轮次、引用、更正说明；知识条目、段落、相关条目；课程分类、专题、课程、媒体、进度、播放记录
-- 模块11 在隔离 worktree 中实现时，管理端-02 的 t_knowledge / t_course 尚未落地，因此本模块自带只读内容表，
-- 表名统一加 ai_ / course_ 前缀，后续管理端-02 合并时以其表结构为准做一次数据迁移。
-- 已执行的脚本不再修改；结构变更另起 V1_11_1、V1_11_2…

CREATE TABLE t_ai_session
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话 ID',
    farm_id                  BIGINT UNSIGNED NOT NULL COMMENT '提问时所在养殖场',
    user_id                  BIGINT UNSIGNED NOT NULL COMMENT '提问人',
    title                    VARCHAR(50)     NOT NULL COMMENT '标题：首问去标点与空白后截前 50 字；只附图时为“图片提问”',
    topic                    VARCHAR(20)     NOT NULL DEFAULT 'OTHER' COMMENT 'AI_TOPIC：MEDICATION / FEEDING / DISEASE / IMMUNE / DISINFECT / OTHER',
    batch_id                 BIGINT UNSIGNED NULL COMMENT '当前关联批次；空为未关联（模块04 未接入时只保存快照）',
    batch_snapshot_json      JSON            NULL COMMENT '关联批次快照：batch_no、category_name、stage_name、breed_name、pen_id、pen_name、day_age、current_count、count_unit_text',
    batch_link_state         VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT 'BATCH_LINK_STATE：NONE / VALID / NOT_IN_STOCK / NO_PERMISSION',
    round_count              INT             NOT NULL DEFAULT 0 COMMENT '轮数（含超时、失败轮次）',
    context_truncated        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '历史轮次超过 10 轮时为 1',
    last_asked_at            DATETIME        NOT NULL COMMENT '最后一次提问时间',
    has_correction           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '会话中存在已发布更正说明的轮次时为 1',
    has_epidemic_alert       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '出现过疑似重大疫病回答即为 1，不回退',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_ai_session_user (farm_id, user_id, last_asked_at),
    KEY idx_ai_session_batch (batch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 会话';

CREATE TABLE t_ai_message
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '轮次 ID',
    session_id               BIGINT UNSIGNED NOT NULL COMMENT '所属会话',
    farm_id                  BIGINT UNSIGNED NOT NULL COMMENT '冗余：养殖场',
    user_id                  BIGINT UNSIGNED NOT NULL COMMENT '冗余：提问人',
    round_no                 INT             NOT NULL COMMENT '会话内序号，从 1 起',
    client_msg_id            VARCHAR(64)     NOT NULL COMMENT '请求头 Idempotency-Key',
    client_type              VARCHAR(20)     NOT NULL DEFAULT 'APP_ANDROID' COMMENT 'CLIENT_TYPE',
    question_text            VARCHAR(500)    NULL COMMENT '问题文字；只附图时为空',
    image_file_ids           JSON            NULL COMMENT '附图 file_id 数组，0–9 张，按顺序',
    batch_id                 BIGINT UNSIGNED NULL COMMENT '本轮关联批次',
    batch_no                 VARCHAR(32)     NULL COMMENT '本轮关联批次编号快照',
    context_course_id        BIGINT UNSIGNED NULL COMMENT '从视频“问 AI”带入的课程',
    context_course_title     VARCHAR(100)    NULL COMMENT '带入课程标题快照',
    context_knowledge_id     BIGINT UNSIGNED NULL COMMENT '带入课程关联的知识条目',
    status                   VARCHAR(20)     NOT NULL COMMENT 'AI_MESSAGE_STATUS：ANSWERING / ANSWERED / INTERRUPTED / TIMEOUT / FAILED',
    answer_type              VARCHAR(20)     NULL COMMENT 'AI_ANSWER_TYPE：NORMAL / NO_BASIS / OFF_TOPIC / BANNED_DRUG / EPIDEMIC',
    answer_text              TEXT            NULL COMMENT '轻量格式正文，含 [^n] 引用标记',
    error_code               INT             NULL COMMENT '50401 超时、50201 AI 不可用',
    error_message            VARCHAR(200)    NULL COMMENT '错误提示原文',
    retry_count              INT             NOT NULL DEFAULT 0 COMMENT '重新生成次数',
    rating                   VARCHAR(20)     NULL COMMENT 'AI_RATING：USEFUL / USELESS，空为未评价',
    rated_at                 DATETIME        NULL COMMENT '评价时间',
    handle_status            VARCHAR(20)     NULL COMMENT 'AI_HANDLE_STATUS：PENDING / CORRECTED / TASKED / NO_ACTION',
    model_name               VARCHAR(50)     NULL COMMENT '实际模型',
    prompt_tokens            INT             NULL COMMENT '提示词 token 数',
    completion_tokens        INT             NULL COMMENT '输出 token 数',
    latency_ms               INT             NULL COMMENT '生成耗时（毫秒）',
    asked_at                 DATETIME        NOT NULL COMMENT '提问时间',
    started_at               DATETIME        NOT NULL COMMENT '本次生成开始时间，重试时更新',
    answered_at              DATETIME        NULL COMMENT '生成结束时间',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_message_round (session_id, round_no),
    UNIQUE KEY uk_ai_message_client (user_id, client_msg_id),
    KEY idx_ai_message_asked (asked_at),
    KEY idx_ai_message_status (status, started_at),
    KEY idx_ai_message_answer_type (answer_type, asked_at),
    KEY idx_ai_message_handle (handle_status, asked_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 问答轮次';

CREATE TABLE t_ai_citation
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '引用 ID',
    message_id               BIGINT UNSIGNED NOT NULL COMMENT '所属轮次',
    session_id               BIGINT UNSIGNED NOT NULL COMMENT '所属会话（冗余）',
    seq_no                   TINYINT         NOT NULL COMMENT '角标序号，按回答中首次出现顺序',
    knowledge_id             BIGINT UNSIGNED NOT NULL COMMENT '被引用知识条目',
    knowledge_version_no     INT             NOT NULL COMMENT '引用时线上版本号',
    para_key                 VARCHAR(32)     NOT NULL COMMENT '被引用段落键',
    source_type              VARCHAR(20)     NOT NULL COMMENT 'KNOWLEDGE_SOURCE',
    icon_type                VARCHAR(20)     NOT NULL COMMENT 'CITATION_ICON：STANDARD / ARTICLE',
    title_snapshot           VARCHAR(100)    NOT NULL COMMENT '条目标题快照',
    category_name_snapshot   VARCHAR(20)     NOT NULL COMMENT '分类名称快照',
    quote_text               VARCHAR(1000)   NOT NULL COMMENT '被引用段落原文快照',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_citation_seq (message_id, seq_no),
    KEY idx_ai_citation_knowledge (knowledge_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 引用来源';

CREATE TABLE t_ai_correction
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '更正说明 ID',
    message_id               BIGINT UNSIGNED NOT NULL COMMENT '所属轮次',
    content                  VARCHAR(500)    NOT NULL COMMENT '更正内容',
    status                   VARCHAR(20)     NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED / WITHDRAWN',
    published_at             DATETIME        NULL COMMENT '发布时间',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人（管理员 ID），系统为 0',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_ai_correction_message (message_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 回答的更正说明';

CREATE TABLE t_ai_knowledge
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识条目 ID',
    title                    VARCHAR(100)    NOT NULL COMMENT '标题',
    category_name            VARCHAR(20)     NOT NULL COMMENT '分类名称，例如 用药规范',
    source_type              VARCHAR(20)     NOT NULL COMMENT 'KNOWLEDGE_SOURCE：PLATFORM / DISEASE / DRUG_CATALOG',
    display_type             VARCHAR(20)     NOT NULL DEFAULT 'STANDARD' COMMENT 'CITATION_ICON：STANDARD / ARTICLE',
    is_major_epidemic        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否重大动物疫病条目',
    status                   VARCHAR(20)     NOT NULL DEFAULT 'ON_SHELF' COMMENT 'ON_SHELF / OFF_SHELF',
    version_no               INT             NOT NULL DEFAULT 1 COMMENT '线上版本号',
    share_url                VARCHAR(200)    NULL COMMENT '分享地址',
    embedded_course_id       BIGINT UNSIGNED NULL COMMENT '内嵌教学视频的课程 ID',
    sort_no                  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    published_at             DATETIME        NULL COMMENT '首次发布时间',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_ai_knowledge_category (category_name, status),
    KEY idx_ai_knowledge_status (status, version_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识条目（管理端-02 未落地前的只读内容表）';

CREATE TABLE t_ai_knowledge_paragraph
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '段落 ID',
    knowledge_id             BIGINT UNSIGNED NOT NULL COMMENT '知识条目 ID',
    para_key                 VARCHAR(32)     NOT NULL COMMENT '段落键，跨版本不变',
    para_no                  INT             NOT NULL COMMENT '段落顺序',
    para_type                VARCHAR(20)     NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT / HEADING / QUOTE / LIST / IMAGE / VIDEO',
    content_text             TEXT            NULL COMMENT '纯文字',
    content_html             TEXT            NULL COMMENT '可含白名单 HTML',
    image_url                VARCHAR(500)    NULL COMMENT '图片段落地址',
    course_id                BIGINT UNSIGNED NULL COMMENT 'VIDEO 段落的课程 ID',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_knowledge_para (knowledge_id, para_key),
    KEY idx_ai_knowledge_para_order (knowledge_id, para_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识条目段落';

CREATE TABLE t_ai_knowledge_related
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '相关条目关联 ID',
    knowledge_id             BIGINT UNSIGNED NOT NULL COMMENT '知识条目 ID',
    related_id               BIGINT UNSIGNED NOT NULL COMMENT '相关条目 ID',
    sort_no                  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_knowledge_related (knowledge_id, related_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='知识条目相关条目';

CREATE TABLE t_course_category
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程分类 ID',
    name                     VARCHAR(20)     NOT NULL COMMENT '分类名称',
    sort_no                  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status                   VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程分类';

CREATE TABLE t_course_topic
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '专题 ID',
    name                     VARCHAR(50)     NOT NULL COMMENT '专题名称',
    category_id              BIGINT UNSIGNED NULL COMMENT '分类 ID',
    category_name            VARCHAR(20)     NULL COMMENT '分类名称',
    intro                    VARCHAR(500)    NULL COMMENT '专题简介',
    cover_url                VARCHAR(500)    NULL COMMENT '封面地址',
    sort_no                  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status                   VARCHAR(20)     NOT NULL DEFAULT 'ON_SHELF' COMMENT 'ON_SHELF / OFF_SHELF',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_course_topic_status (status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程专题';

CREATE TABLE t_course
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程 ID',
    title                    VARCHAR(100)    NOT NULL COMMENT '课程标题',
    category_id              BIGINT UNSIGNED NOT NULL COMMENT '分类 ID',
    category_name            VARCHAR(20)     NOT NULL COMMENT '分类名称',
    topic_id                 BIGINT UNSIGNED NULL COMMENT '所属专题 ID',
    topic_name               VARCHAR(50)     NULL COMMENT '所属专题名称',
    section_no               INT             NULL COMMENT '专题内节次',
    knowledge_id             BIGINT UNSIGNED NULL COMMENT '关联知识条目',
    duration_sec             INT             NOT NULL DEFAULT 0 COMMENT '时长（秒）',
    source_label             VARCHAR(50)     NOT NULL DEFAULT '平台知识库' COMMENT '来源文字',
    cover_url                VARCHAR(500)    NULL COMMENT '封面地址',
    share_url                VARCHAR(200)    NULL COMMENT '分享地址',
    status                   VARCHAR(20)     NOT NULL DEFAULT 'ON_SHELF' COMMENT 'DRAFT / ON_SHELF / OFF_SHELF',
    published_at             DATETIME        NULL COMMENT '上架时间',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_course_status (status, published_at),
    KEY idx_course_topic (topic_id, section_no),
    KEY idx_course_category (category_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='养殖课堂课程';

CREATE TABLE t_course_media
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程媒体 ID',
    course_id                BIGINT UNSIGNED NOT NULL COMMENT '课程 ID',
    media_type               VARCHAR(20)     NOT NULL DEFAULT 'HLS' COMMENT 'HLS / MP4',
    quality                  VARCHAR(20)     NULL COMMENT 'VIDEO_QUALITY：P720 / P480；非视频为空',
    url                      VARCHAR(500)    NOT NULL COMMENT '可直接播放的地址',
    size_bytes               BIGINT          NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    status                   VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / PROCESSING / FAILED',
    sort_no                  INT             NOT NULL DEFAULT 0 COMMENT '排序',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_course_media_course (course_id, status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程媒体（播放地址与清晰度）';

CREATE TABLE t_course_progress
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '进度 ID',
    user_id                  BIGINT UNSIGNED NOT NULL COMMENT '账号 ID（不带 farm_id）',
    course_id                BIGINT UNSIGNED NOT NULL COMMENT '课程 ID',
    position_sec             INT             NOT NULL DEFAULT 0 COMMENT '上次位置',
    max_position_sec         INT             NOT NULL DEFAULT 0 COMMENT '最远到达位置',
    watched_sec              INT             NOT NULL DEFAULT 0 COMMENT '累计实际观看内容秒数',
    duration_sec             INT             NOT NULL DEFAULT 0 COMMENT '课程时长快照',
    progress_ratio           DECIMAL(5, 4)   NOT NULL DEFAULT 0.0000 COMMENT '最远位置 ÷ 时长，已学完为 1',
    completed                TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '已学完',
    completed_at             DATETIME        NULL COMMENT '学完时间',
    last_watched_at          DATETIME        NOT NULL COMMENT '最近观看时间（继续观看、观看记录排序）',
    last_client_type         VARCHAR(20)     NOT NULL DEFAULT 'APP_ANDROID' COMMENT 'CLIENT_TYPE',
    last_quality             VARCHAR(20)     NULL COMMENT '最近清晰度',
    last_client_time         DATETIME        NOT NULL COMMENT '乱序判断',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_progress_user (user_id, course_id),
    KEY idx_course_progress_watch (user_id, last_watched_at),
    KEY idx_course_progress_completed (course_id, completed)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程学习进度';

CREATE TABLE t_course_play_record
(
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '播放记录 ID',
    user_id                  BIGINT UNSIGNED NOT NULL COMMENT '账号 ID',
    course_id                BIGINT UNSIGNED NOT NULL COMMENT '课程 ID',
    play_token               VARCHAR(64)     NOT NULL COMMENT '客户端每次开始播放生成',
    entry_type               VARCHAR(20)     NOT NULL DEFAULT 'COURSE' COMMENT 'PLAY_ENTRY',
    client_type              VARCHAR(20)     NOT NULL DEFAULT 'APP_ANDROID' COMMENT 'CLIENT_TYPE',
    network_type             VARCHAR(20)     NULL COMMENT 'NETWORK_TYPE',
    quality                  VARCHAR(20)     NULL COMMENT 'VIDEO_QUALITY',
    start_position_sec       INT             NOT NULL DEFAULT 0 COMMENT '起始位置',
    end_position_sec         INT             NULL COMMENT '结束位置',
    max_position_sec         INT             NOT NULL DEFAULT 0 COMMENT '最远位置',
    watched_sec              INT             NOT NULL DEFAULT 0 COMMENT '本次播放累计观看秒数',
    ask_ai_clicked           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '播放期间点过“问 AI”',
    knowledge_clicked        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '播放期间点过“图文原文”',
    started_at               DATETIME        NOT NULL COMMENT '开始时间',
    last_report_at           DATETIME        NOT NULL COMMENT '最后上报时间',
    last_client_time         DATETIME        NOT NULL COMMENT '重复与乱序上报判断',
    ended_at                 DATETIME        NULL COMMENT '结束时间',
    created_at               DATETIME        NOT NULL COMMENT '创建时间',
    updated_at               DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_play_token (user_id, play_token),
    KEY idx_course_play_course (course_id, started_at),
    KEY idx_course_play_started (started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程播放记录';
