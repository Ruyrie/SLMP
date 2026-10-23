-- 模块03 今日首页、日历待办与消息中心（模块03 服务端技术准备文档 2.3 节、05 号文档）
-- 7 张表：t_todo、t_todo_remind、t_todo_handle_log、t_message、t_reminder_setting、t_push_device、t_push_record
-- 已执行的脚本不再修改；结构变更另起 V1_03_1、V1_03_2…
-- 不建外键；t_todo / t_todo_remind 属于养殖场（带 farm_id，实体标 @FarmScoped），消息表按用户读写，不参与多租户插件。
-- pen_name、batch_no、assignee_id、assignee_name 是显示快照：模块04（栏舍/批次）未接入时由来源或示例数据写入，
-- 模块04 接入后由 TodoService.upsert 同步更新，列表/详情不再反查模块04 的表。

CREATE TABLE t_todo
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '待办 ID',
    farm_id              BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    todo_type            VARCHAR(20)     NOT NULL COMMENT '待办类型（TODO_TYPE）：FEEDING / DISINFECTION / IMMUNIZATION 等 13 种',
    category             VARCHAR(20)     NOT NULL COMMENT '待办类别（TODO_CATEGORY）：WORK 作业 / WARNING 预警 / BUSINESS 经营',
    pen_id               BIGINT UNSIGNED NULL COMMENT '栏舍 ID（t_pen，模块04）；库存、待收款类为空',
    batch_id             BIGINT UNSIGNED NULL COMMENT '批次 ID（t_batch，模块04）；预产、库存类为空',
    pen_name             VARCHAR(30)     NULL COMMENT '栏舍名称快照（显示用，模块04 接入后同步）',
    batch_no             VARCHAR(30)     NULL COMMENT '批次号快照（显示用，模块04 接入后同步）',
    related_batch_id     BIGINT UNSIGNED NULL COMMENT '详情“关联批次”ID 快照，可不同于列表项 batch_id（例如消毒任务关联批次）',
    related_batch_no     VARCHAR(30)     NULL COMMENT '详情“关联批次”批次号快照',
    related_batch_desc   VARCHAR(100)    NULL COMMENT '详情“关联批次”说明，例如 保育猪 · 存栏 200 头',
    related_batch_tag    VARCHAR(20)     NULL COMMENT '详情“关联批次”标签，例如 休药中',
    biz_type             VARCHAR(30)     NOT NULL COMMENT '来源对象类型（BIZ_TYPE），例如 DISINFECT_TASK',
    biz_id               BIGINT UNSIGNED NOT NULL COMMENT '来源对象 ID，例如消毒任务 70112',
    dedup_key            VARCHAR(64)     NOT NULL COMMENT '来源去重键，例如 DISINFECT_TASK:70112；库存预警带触发序号',
    title                VARCHAR(60)     NOT NULL COMMENT '标题，例如 2 号保育舍 · 消毒',
    content_text         VARCHAR(100)    NULL COMMENT '作业内容，例如 聚维酮碘溶液',
    status_note          VARCHAR(20)     NULL COMMENT '来源状态说明，例如 漏免',
    detail_label         VARCHAR(20)     NULL COMMENT '详情类型专属行标签，例如 消毒要求',
    detail_value         VARCHAR(100)    NULL COMMENT '详情类型专属行内容，例如 聚维酮碘溶液 1:500 · 喷雾',
    amount               DECIMAL(12, 2)  NULL COMMENT '金额（仅待收款），管理视图字段',
    receiver_scope       VARCHAR(20)     NOT NULL COMMENT '执行人范围（RECEIVER_SCOPE）：PEN_STAFF / PEN_ALL / MANAGERS',
    assignee_id          BIGINT UNSIGNED NULL COMMENT '执行人用户 ID 快照（PEN_STAFF 时由来源或模块02 栏舍员工给出）',
    assignee_name        VARCHAR(20)     NULL COMMENT '执行人姓名快照',
    due_date             DATE            NOT NULL COMMENT '截止日期（归日依据），例如 2026-10-19',
    time_type            VARCHAR(20)     NOT NULL COMMENT '时间类型（TODO_TIME_TYPE）：ALL_DAY / AT_TIME / BEFORE_TIME',
    due_time             TIME            NULL COMMENT '截止时刻；全天为空',
    overdue_at           DATETIME        NULL COMMENT '逾期判定时刻；预警类、休药期到期为空表示不逾期',
    sort_time            DATETIME        NOT NULL COMMENT '排序时间：due_date + due_time，全天取 00:00',
    status               VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '待办状态（TODO_STATUS）：PENDING / DONE / IGNORED；OVERDUE 不落库',
    overdue_done         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逾期完成：1 是',
    completed_at         DATETIME        NULL COMMENT '完成时间',
    completed_by         BIGINT UNSIGNED NULL COMMENT '完成人用户 ID；系统为 0',
    complete_way         VARCHAR(20)     NULL COMMENT '完成方式（COMPLETE_WAY）：REGISTER / MANUAL / AUTO',
    complete_record_type VARCHAR(30)     NULL COMMENT '完成依据记录类型，例如 DISINFECT_RECORD',
    complete_record_id   BIGINT UNSIGNED NULL COMMENT '完成依据记录 ID',
    ignored_at           DATETIME        NULL COMMENT '忽略时间',
    ignored_by           BIGINT UNSIGNED NULL COMMENT '忽略人用户 ID；系统为 0',
    ignore_reason        VARCHAR(100)    NULL COMMENT '忽略原因（用户填写 1–100 字或系统文案）',
    ignore_way           VARCHAR(20)     NULL COMMENT '忽略方式（IGNORE_WAY）：MANUAL / AUTO',
    remind_count         INT             NOT NULL DEFAULT 0 COMMENT '已发提醒次数',
    last_remind_at       DATETIME        NULL COMMENT '最近一次提醒时间',
    version              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：来源记录删除、计划取消、批次删除时为 1',
    created_by           BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    created_at           DATETIME        NOT NULL COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_todo_dedup (farm_id, todo_type, dedup_key),
    KEY idx_todo_farm_status_due (farm_id, status, due_date),
    KEY idx_todo_farm_status_overdue (farm_id, status, overdue_at),
    KEY idx_todo_farm_pen_due (farm_id, pen_id, due_date),
    KEY idx_todo_batch_status (batch_id, status),
    KEY idx_todo_biz (biz_type, biz_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='待办（来源模块生成，本模块展示与处理）';

CREATE TABLE t_todo_remind
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提醒计划 ID',
    todo_id        BIGINT UNSIGNED NOT NULL COMMENT '待办 ID（t_todo）',
    farm_id        BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID，冗余便于按场取消',
    remind_stage   VARCHAR(20)     NOT NULL COMMENT '提醒阶段（REMIND_STAGE）：ADVANCE / DUE / OVERDUE / REPEAT',
    cycle_key      VARCHAR(20)     NOT NULL COMMENT '去重周期：ADVANCE/DUE/OVERDUE 取截止日期 20261018，REPEAT 取提醒日期',
    remind_way     VARCHAR(20)     NOT NULL COMMENT '提醒方式（REMIND_WAY）：PLAN 计划 / TRIGGER 来源任务即时触发',
    remind_at      DATETIME        NOT NULL COMMENT '计划提醒时刻',
    receiver_scope VARCHAR(20)     NOT NULL COMMENT '接收人范围（RECEIVER_SCOPE）：PEN_STAFF / PEN_ALL / MANAGERS',
    msg_type       VARCHAR(30)     NOT NULL DEFAULT 'TODO_REMIND' COMMENT '提醒消息类型（MSG_TYPE）',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PLANNED' COMMENT '提醒状态（REMIND_STATUS）：PLANNED / SENT / CANCELLED',
    cancel_reason  VARCHAR(20)     NULL COMMENT '取消原因：CLOSED / DELETED / RESCHEDULED / FARM_INACTIVE / EXPIRED',
    sent_at        DATETIME        NULL COMMENT '发送时间',
    receiver_count INT             NOT NULL DEFAULT 0 COMMENT '实际接收人数',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tdr_cycle (todo_id, remind_stage, cycle_key),
    KEY idx_tdr_status_at (status, remind_at),
    KEY idx_tdr_farm_status (farm_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='待办提醒计划';

CREATE TABLE t_todo_handle_log
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '处理记录 ID',
    todo_id      BIGINT UNSIGNED NOT NULL COMMENT '待办 ID（t_todo）',
    farm_id      BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    action       VARCHAR(20)     NOT NULL COMMENT '操作：COMPLETE 标记完成 / IGNORE 忽略 / REVERT 撤销完成',
    way          VARCHAR(20)     NULL COMMENT '方式（COMPLETE_WAY / IGNORE_WAY）：REGISTER / MANUAL / AUTO',
    operator_id  BIGINT UNSIGNED NULL COMMENT '操作人用户 ID；系统为 0',
    reason       VARCHAR(100)    NULL COMMENT '忽略原因',
    overdue_done TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逾期完成：1 是',
    created_at   DATETIME        NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_thl_todo (todo_id, created_at),
    KEY idx_thl_farm_action (farm_id, action, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='待办处理记录（完成、忽略与撤销完成）';

CREATE TABLE t_message
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息 ID（每个接收人一行）',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '接收人用户 ID（t_user）',
    farm_id       BIGINT UNSIGNED NULL COMMENT '消息所属养殖场；平台通知、账号找回为空',
    receiver_role VARCHAR(20)     NULL COMMENT '发送时接收人在该场的角色（FARM_ROLE）',
    category      VARCHAR(20)     NOT NULL COMMENT '消息分类（MSG_CATEGORY）：TODO / AI / AUDIT / MEMBER / LOT / NOTICE / OTHER',
    msg_type      VARCHAR(30)     NOT NULL COMMENT '消息类型（MSG_TYPE）',
    level         VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '级别（MSG_LEVEL）：NORMAL / IMPORTANT / URGENT',
    title         VARCHAR(60)     NOT NULL COMMENT '标题',
    summary       VARCHAR(100)    NULL COMMENT '摘要（按接收人生成）',
    tag_code      VARCHAR(20)     NULL COMMENT '标签编码快照（MSG_TAG）',
    tag_text      VARCHAR(20)     NULL COMMENT '标签文字快照，例如 关注、已停用',
    biz_type      VARCHAR(30)     NULL COMMENT '业务对象类型（BIZ_TYPE）',
    biz_id        BIGINT UNSIGNED NULL COMMENT '业务对象 ID',
    todo_id       BIGINT UNSIGNED NULL COMMENT '待办提醒类消息对应的待办 ID',
    route         VARCHAR(20)     NULL COMMENT '默认去向 App 页面编号，例如 I06；待办消息打开时按实时状态重算',
    extra_json    JSON            NULL COMMENT '类型快照：正文、邀请角色、驳回原因、报表期次等（键为 snake_case）',
    merge_key     VARCHAR(64)     NULL COMMENT '合并键：待办提醒为 TODO:{todo_id}，其余为空',
    merge_count   INT             NOT NULL DEFAULT 1 COMMENT '已合并提醒次数',
    sort_time     DATETIME        NOT NULL COMMENT '排序时间（最近一次提醒或发送时间）',
    read_at       DATETIME        NULL COMMENT '已读时间；空为未读',
    hidden        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '隐藏：通知撤回、被移出养殖场',
    hidden_reason VARCHAR(20)     NULL COMMENT '隐藏原因：NOTICE_REVOKED / MEMBER_REMOVED',
    deleted       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '用户删除；60 秒内可撤销',
    deleted_at    DATETIME        NULL COMMENT '删除时间',
    created_by    BIGINT UNSIGNED NULL COMMENT '发送人用户 ID，系统为 0',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_merge (user_id, merge_key),
    KEY idx_msg_user_sort (user_id, deleted, hidden, sort_time),
    KEY idx_msg_user_farm_cat (user_id, farm_id, category, sort_time),
    KEY idx_msg_biz (biz_type, biz_id),
    KEY idx_msg_todo (todo_id),
    KEY idx_msg_sort (sort_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='站内消息（每个接收人一行）';

CREATE TABLE t_reminder_setting
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提醒设置 ID',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户 ID，每账号一行，覆盖全部养殖场',
    todo_remind_enabled TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '待办提醒推送：1 开启',
    digest_enabled      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '每日待办汇总：1 开启',
    digest_time         TIME            NOT NULL DEFAULT '07:00:00' COMMENT '汇总时间，分钟为 5 的倍数',
    quiet_enabled       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '免打扰：1 开启',
    quiet_start         TIME            NOT NULL DEFAULT '22:00:00' COMMENT '免打扰开始',
    quiet_end           TIME            NOT NULL DEFAULT '07:00:00' COMMENT '免打扰结束；早于开始表示次日',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rs_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='提醒设置（每账号一行）';

CREATE TABLE t_push_device
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推送设备 ID',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '当前绑定用户 ID',
    platform          VARCHAR(20)     NOT NULL COMMENT '平台：ANDROID',
    registration_id   VARCHAR(64)     NOT NULL COMMENT '极光 RegistrationID',
    jpush_alias       VARCHAR(40)     NOT NULL COMMENT '极光别名：u{用户 ID}',
    alias_bound       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '极光别名绑定成功：1 是',
    device_brand      VARCHAR(40)     NULL COMMENT '设备品牌',
    device_model      VARCHAR(40)     NULL COMMENT '设备型号',
    os_version        VARCHAR(40)     NULL COMMENT '系统版本',
    app_version       VARCHAR(40)     NULL COMMENT 'App 版本',
    notify_permission TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '系统通知权限：1 已授权',
    status            VARCHAR(20)     NOT NULL COMMENT '设备状态（DEVICE_STATUS）：ACTIVE / UNBOUND',
    last_login_at     DATETIME        NULL COMMENT '最近登录时间',
    last_active_at    DATETIME        NULL COMMENT '最近活跃时间',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pd_reg (registration_id),
    KEY idx_pd_user_status (user_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='推送设备与极光别名绑定';

CREATE TABLE t_push_record
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推送记录 ID',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '接收人用户 ID',
    farm_id        BIGINT UNSIGNED NULL COMMENT '养殖场 ID',
    message_id     BIGINT UNSIGNED NULL COMMENT '消息 ID；每日汇总为空',
    todo_id        BIGINT UNSIGNED NULL COMMENT '待办 ID',
    push_type      VARCHAR(20)     NOT NULL COMMENT '推送类型（PUSH_TYPE）：TODO_REMIND / DIGEST / MESSAGE',
    channel        VARCHAR(20)     NOT NULL COMMENT '通道（PUSH_CHANNEL）：JPUSH / WEB',
    notify_channel VARCHAR(20)     NOT NULL COMMENT 'Android 通知渠道：zm_todo / zm_urgent / zm_system',
    title          VARCHAR(60)     NOT NULL COMMENT '通知标题',
    content        VARCHAR(120)    NOT NULL COMMENT '通知正文',
    route          VARCHAR(20)     NOT NULL COMMENT '目标 App 页面编号',
    biz_type       VARCHAR(30)     NULL COMMENT '业务对象类型',
    biz_id         BIGINT UNSIGNED NULL COMMENT '业务对象 ID',
    bypass_quiet   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '不受免打扰：1 是',
    dedup_key      VARCHAR(64)     NULL COMMENT '去重键，例如 DIGEST:20261019',
    plan_send_at   DATETIME        NOT NULL COMMENT '计划发送时间（免打扰时为时段结束时刻）',
    status         VARCHAR(20)     NOT NULL COMMENT '状态（PUSH_STATUS）：WAITING / DEFERRED / SENT / FAILED / CANCELLED / SKIPPED',
    retry_count    INT             NOT NULL DEFAULT 0 COMMENT '重试次数，最多 2 次',
    sent_at        DATETIME        NULL COMMENT '发送时间',
    clicked_at     DATETIME        NULL COMMENT '点开时间',
    jpush_msg_id   VARCHAR(64)     NULL COMMENT '极光消息 ID',
    fail_reason    VARCHAR(200)    NULL COMMENT '失败或跳过原因',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_dedup (user_id, channel, dedup_key),
    KEY idx_pr_status_plan (status, plan_send_at),
    KEY idx_pr_user_channel (user_id, channel, status, plan_send_at),
    KEY idx_pr_message (message_id),
    KEY idx_pr_todo (todo_id),
    KEY idx_pr_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='推送记录与延后队列（本批只落站内通知与设备绑定，Web/汇总后续接）';
