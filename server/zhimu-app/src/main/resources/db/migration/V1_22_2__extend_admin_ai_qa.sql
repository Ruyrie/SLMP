-- 管理端-02 内容运营：AI 问答与更正（WA-C05，ADM-C57–C65）
-- t_ai_message 由模块11 维护；本脚本只为管理端补充更正说明字段、抽查记录表与查询索引，不改变用户端读取逻辑。
-- 已执行的脚本不再修改。

ALTER TABLE t_ai_correction
    ADD COLUMN session_id         BIGINT UNSIGNED NULL COMMENT '所属会话（t_ai_session）' AFTER message_id,
    ADD COLUMN farm_id            BIGINT UNSIGNED NULL COMMENT '提问人所在养殖场' AFTER session_id,
    ADD COLUMN user_id            BIGINT UNSIGNED NULL COMMENT '提问人' AFTER farm_id,
    ADD COLUMN published_flag     TINYINT(1)      NULL COMMENT '已发布为 1，撤回后置空；唯一键保证同一回答最多一条已发布' AFTER status,
    ADD COLUMN first_published_at DATETIME        NULL COMMENT '首次发布时间' AFTER published_at,
    ADD COLUMN published_by       BIGINT UNSIGNED NULL COMMENT '发布管理员 ID' AFTER first_published_at,
    ADD COLUMN withdrawn_at       DATETIME        NULL COMMENT '撤回时间' AFTER published_by,
    ADD COLUMN withdrawn_by       BIGINT UNSIGNED NULL COMMENT '撤回管理员 ID' AFTER withdrawn_at,
    ADD COLUMN withdraw_reason    VARCHAR(100)    NULL COMMENT '撤回原因（仅管理员可见）' AFTER withdrawn_by,
    ADD COLUMN version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER withdraw_reason;

UPDATE t_ai_correction c
    JOIN t_ai_message m ON m.id = c.message_id
SET c.session_id         = m.session_id,
    c.farm_id            = m.farm_id,
    c.user_id            = m.user_id,
    c.published_flag     = IF(c.status = 'PUBLISHED', 1, NULL),
    c.first_published_at = c.published_at,
    c.published_by       = c.created_by
WHERE c.session_id IS NULL;

-- 历史数据可能同一回答存在多条 PUBLISHED（旧表没有唯一键）：保留最早一条，其余改为已撤回，避免唯一键创建失败
UPDATE t_ai_correction c
    JOIN (SELECT message_id, MIN(id) AS keep_id
          FROM t_ai_correction
          WHERE published_flag = 1
          GROUP BY message_id
          HAVING COUNT(*) > 1) d ON d.message_id = c.message_id
SET c.published_flag  = NULL,
    c.status          = 'WITHDRAWN',
    c.withdrawn_at    = CURRENT_TIMESTAMP,
    c.withdraw_reason = '历史重复更正说明，迁移时合并'
WHERE c.id <> d.keep_id
  AND c.published_flag = 1;

ALTER TABLE t_ai_correction
    ADD UNIQUE KEY uk_ai_correction_published (message_id, published_flag);

CREATE TABLE t_ai_qa_review
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '抽查与处理记录 ID',
    message_id          BIGINT UNSIGNED NOT NULL COMMENT '问答轮次 ID（t_ai_message）',
    session_id          BIGINT UNSIGNED NOT NULL COMMENT '会话 ID（t_ai_session）',
    is_spot_check       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '抽查规则命中',
    spot_check_rule     VARCHAR(30)     NULL COMMENT 'SPOT_CHECK_RULE：MULTI_WITHDRAWAL_DRUG / SELLABLE_DATE_MISMATCH',
    spot_check_title    VARCHAR(50)     NULL COMMENT '抽查提示标题',
    spot_check_content  VARCHAR(300)    NULL COMMENT '抽查提示正文',
    spot_checked_at     DATETIME        NULL COMMENT '抽查时间',
    handled_by          BIGINT UNSIGNED NULL COMMENT '最后处理管理员 ID',
    handled_at          DATETIME        NULL COMMENT '最后处理时间',
    last_action         VARCHAR(20)     NULL COMMENT 'CORRECT / TASK / NO_ACTION / WITHDRAW',
    knowledge_task_id   BIGINT UNSIGNED NULL COMMENT '关联知识补充任务 ID（管理端-02 下篇开放后写入）',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_qa_review_message (message_id),
    KEY idx_ai_qa_review_spot (is_spot_check, spot_checked_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 问答抽查与处理记录（管理端-02）';
