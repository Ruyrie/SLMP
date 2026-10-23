-- 管理端-04 用户与系统：管理员、角色、权限、角色权限、站内消息、账号日活跃、平台日统计
-- 归属：第 2 批 4A 管理端基础层；表结构按管理端-04 服务端技术准备文档 2.3 节。
-- 全部 InnoDB、utf8mb4_0900_ai_ci、不建外键；管理员不删除，只停用（t_admin 无 deleted 列）。

CREATE TABLE t_admin
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员 ID',
    username               VARCHAR(32)     NOT NULL COMMENT '登录账号：4–20 位字母和数字，创建后不能修改，例如 zhoumin',
    name                   VARCHAR(20)     NOT NULL COMMENT '姓名，例如 周敏',
    phone                  VARCHAR(11)     NOT NULL COMMENT '手机号：接收初始密码和重置密码短信，接口输出时打码为 139****5874',
    password_hash          VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希（强度 10）',
    role_id                BIGINT UNSIGNED NOT NULL COMMENT '角色 ID（t_admin_role），一人一个角色',
    status                 VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL 正常 / DISABLED 已停用；已锁定为按 locked_until 派生的状态',
    must_change_password   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否需要修改密码：新增、重置后为 1，改密后为 0',
    temp_password_expire_at DATETIME       NULL COMMENT '初始密码或临时密码的过期时间（签发后 24 小时）；超级管理员种子为 NULL',
    password_updated_at    DATETIME        NULL COMMENT '最近一次修改密码时间',
    login_fail_count       INT             NOT NULL DEFAULT 0 COMMENT '连续密码错误次数，与 Redis admin:login:fail:{username} 同步',
    locked_until           DATETIME        NULL COMMENT '锁定截止时间；大于当前时间即已锁定，到期自动解除，无需任务',
    token_version          INT             NOT NULL DEFAULT 0 COMMENT '会话版本：停用、重置密码、改密时加 1，使旧会话失效',
    last_login_at          DATETIME        NULL COMMENT '最近登录时间',
    last_login_ip          VARCHAR(45)     NULL COMMENT '最近登录 IP（兼容 IPv6）',
    last_login_region      VARCHAR(50)     NULL COMMENT '最近登录 IP 属地，例如 内蒙古赤峰',
    disabled_at            DATETIME        NULL COMMENT '停用时间',
    version                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by             BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人管理员 ID，初始化脚本为 0',
    created_at             DATETIME        NOT NULL COMMENT '创建时间',
    updated_at             DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username),
    UNIQUE KEY uk_admin_phone (phone),
    KEY idx_admin_role_status (role_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='平台管理员（管理端登录账号，不删除只停用）';

CREATE TABLE t_admin_role
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色 ID',
    role_code          VARCHAR(20)     NOT NULL COMMENT '角色编码（ADMIN_ROLE）：SUPER_ADMIN / AUDITOR / OPERATOR',
    role_name          VARCHAR(20)     NOT NULL COMMENT '角色名称，例如 审核员',
    description        VARCHAR(100)    NULL COMMENT '角色说明，例如 审核中心全部审核，查看用户账号和养殖场',
    builtin            TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否内置角色：三个内置角色不可删除、不可新增',
    editable           TINYINT(1)      NOT NULL COMMENT '权限是否可修改：SUPER_ADMIN 为 0（权限固定为全部）',
    default_perm_codes JSON            NOT NULL COMMENT '默认权限码数组，“恢复默认”用',
    sort_no            INT             NOT NULL COMMENT '列表顺序：1、2、3',
    version            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：保存权限的并发控制',
    created_by         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '创建人管理员 ID，初始化脚本为 0',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_role_code (role_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理员角色（内置超级管理员、审核员、内容运营）';

CREATE TABLE t_admin_permission
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '权限点 ID',
    perm_code   VARCHAR(40)     NOT NULL COMMENT '权限码：页面编号:动作，例如 WA-R01:HANDLE',
    module_code VARCHAR(20)     NOT NULL COMMENT '所属功能块：OVERVIEW / AUDIT / CONTENT / BASE_DATA / USER_SYSTEM',
    page_code   VARCHAR(10)     NOT NULL COMMENT '页面编号，例如 WA-R01',
    page_name   VARCHAR(20)     NOT NULL COMMENT '页面名称，例如 养殖场注册审核',
    perm_action VARCHAR(20)     NOT NULL COMMENT '动作：VIEW 查看 / HANDLE 处理',
    handle_desc VARCHAR(100)    NULL COMMENT '“处理”说明：通过、驳回',
    super_only  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否超管专属：1 表示只能属于超级管理员，其他角色锁定',
    sort_no     INT             NOT NULL COMMENT '菜单与权限表顺序',
    created_at  DATETIME        NOT NULL COMMENT '创建时间，由初始化脚本写入',
    updated_at  DATETIME        NOT NULL COMMENT '最后修改时间，由初始化脚本写入；界面不能增删权限点',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_permission_code (perm_code),
    KEY idx_admin_permission_module (module_code, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理员权限点（19 个页面、37 个权限点，初始化写入）';

CREATE TABLE t_admin_role_permission
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色权限 ID',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 ID（t_admin_role）',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限点 ID（t_admin_permission）',
    created_by    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '保存人管理员 ID，初始化脚本为 0',
    created_at    DATETIME        NOT NULL COMMENT '保存时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rp_role_perm (role_id, permission_id),
    KEY idx_rp_permission (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色权限（保存权限时按角色整体替换；超级管理员不写本表，直接视为拥有全部权限）';

CREATE TABLE t_admin_message
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    admin_id     BIGINT UNSIGNED NOT NULL COMMENT '接收人管理员 ID（t_admin），每个接收人一行',
    msg_type     VARCHAR(30)     NOT NULL COMMENT '消息类型（ADMIN_MSG_TYPE），例如 MARKET_PRICE_UNPUBLISHED',
    level        VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT '级别：NORMAL 普通 / IMPORTANT 重要',
    title        VARCHAR(100)    NOT NULL COMMENT '标题，例如 今天还有 5 项行情未发布',
    summary      VARCHAR(200)    NULL COMMENT '摘要，例如 黑龙江 · 育肥猪 · 出栏价等 5 项',
    biz_type     VARCHAR(30)     NULL COMMENT '业务类型（BIZ_TYPE），例如 MARKET_PRICE、KNOWLEDGE、FARM_AUDIT',
    biz_id       BIGINT UNSIGNED NULL COMMENT '业务记录 ID',
    route_page   VARCHAR(10)     NULL COMMENT '点击打开的页面编号，例如 WA-C02',
    route_params JSON            NULL COMMENT '打开时的筛选或抽屉参数，例如未发布标签页、要打开的申请 ID',
    merge_key    VARCHAR(100)    NULL COMMENT '去重键，例如 AUDIT_OVERDUE:FARM_AUDIT:1207；同一接收人同一键只保留一条',
    read_at      DATETIME        NULL COMMENT '已读时间；为空表示未读',
    created_by   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发送人管理员 ID，系统发送为 0',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除；保留 180 天',
    PRIMARY KEY (id),
    UNIQUE KEY uk_am_admin_merge (admin_id, merge_key),
    KEY idx_am_admin_read (admin_id, read_at, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理端站内消息（顶栏铃铛，每个接收管理员一行）';

CREATE TABLE t_user_active_day
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日活跃记录 ID',
    stat_date    DATE            NOT NULL COMMENT '统计日期，例如 2026-10-18',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（t_user）',
    farm_id      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '请求带的 X-Farm-Id；0 表示未进入养殖场的请求',
    op_count     INT             NOT NULL DEFAULT 0 COMMENT '当日已登录请求数',
    record_count INT             NOT NULL DEFAULT 0 COMMENT '当日生产登记条数',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_uad_date_user_farm (stat_date, user_id, farm_id),
    KEY idx_uad_farm_date (farm_id, stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='账号日活跃（按绝对值覆盖写入，可重复执行；保留 400 天）';

CREATE TABLE t_platform_daily_stat
(
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台日统计 ID',
    stat_date              DATE            NOT NULL COMMENT '统计日期，例如 2026-10-15',
    active_user_count      INT             NOT NULL COMMENT '当日活跃账号数',
    new_user_count         INT             NOT NULL COMMENT '当日注册账号数',
    production_record_count INT            NOT NULL COMMENT '当日生产登记条数合计',
    active_farm_count      INT             NOT NULL COMMENT '当日有生产登记的养殖场数',
    created_at             DATETIME        NOT NULL COMMENT '创建时间',
    updated_at             DATETIME        NOT NULL COMMENT '最后修改时间，重算时覆盖',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pds_stat_date (stat_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='平台日统计（概览趋势数据源，重算时覆盖）';
