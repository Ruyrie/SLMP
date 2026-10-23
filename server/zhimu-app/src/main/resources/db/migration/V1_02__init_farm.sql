-- 模块02 养殖场与成员管理（模块02 服务端技术准备文档 2.3 节、05 号文档 4.3 节）
-- 8 张表：t_farm、t_farm_audit、t_farm_change、t_farm_member、t_farm_invite、t_farm_invite_pen、t_member_pen、t_farm_transfer
-- 已执行的脚本不再修改；结构变更另起 V1_02_1、V1_02_2…
-- 本模块不建 t_pen / t_category：栏舍与品类分别由模块04、管理端-03 提供，第 1 批用临时服务。

CREATE TABLE t_farm
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '养殖场 ID',
    name              VARCHAR(20)     NOT NULL COMMENT '当前生效名称，例如 东山猪场',
    owner_user_id     BIGINT UNSIGNED NOT NULL COMMENT '当前所有者用户 ID（与成员表中的 OWNER 一致）',
    main_category_id  BIGINT UNSIGNED NOT NULL COMMENT '主要品类 ID（t_category，例如 101 育肥猪）',
    scale             INT             NOT NULL COMMENT '设计存栏规模，单位取品类 count_unit：1500',
    contact_name      VARCHAR(20)     NOT NULL COMMENT '联系人姓名',
    contact_phone     VARCHAR(11)     NOT NULL COMMENT '联系电话（11 位手机号）',
    intro             VARCHAR(200)    NULL COMMENT '养殖场简介',
    address           VARCHAR(200)    NOT NULL COMMENT '完整地址（含手动补充的门牌）',
    province          VARCHAR(30)     NOT NULL COMMENT '省 / 直辖市',
    city              VARCHAR(30)     NOT NULL COMMENT '市；直辖市用省名补齐',
    district          VARCHAR(30)     NOT NULL COMMENT '区县',
    township          VARCHAR(30)     NULL COMMENT '乡镇 / 街道',
    adcode            CHAR(6)         NOT NULL COMMENT '区县行政区划代码，例如 150404',
    longitude         DECIMAL(10, 7)  NOT NULL COMMENT '经度（GCJ-02），例如 118.8516000',
    latitude          DECIMAL(10, 7)  NOT NULL COMMENT '纬度（GCJ-02），例如 42.3025000',
    coord_type        VARCHAR(20)     NOT NULL DEFAULT 'GCJ02' COMMENT '坐标系：GCJ02 高德坐标',
    loc_accuracy      INT             NULL COMMENT '选点时设备定位精度（米）',
    audit_status      VARCHAR(20)     NOT NULL COMMENT '注册审核状态（FARM_AUDIT_STATUS）：PENDING 待审核 / REJECTED 已驳回 / APPROVED 已通过',
    latest_audit_id   BIGINT UNSIGNED NULL COMMENT '最近一条注册审核记录 ID（t_farm_audit）',
    approved_at       DATETIME        NULL COMMENT '首次审核通过时间',
    disabled          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '所有者停用：1 已停用，0 正常',
    disabled_at       DATETIME        NULL COMMENT '停用时间',
    disabled_by       BIGINT UNSIGNED NULL COMMENT '停用操作人用户 ID',
    frozen            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '平台冻结：1 已冻结，0 正常',
    frozen_at         DATETIME        NULL COMMENT '冻结时间',
    frozen_reason     VARCHAR(200)    NULL COMMENT '冻结原因原文',
    frozen_by         BIGINT UNSIGNED NULL COMMENT '冻结管理员 ID（t_admin）',
    delete_type       VARCHAR(20)     NULL COMMENT '删除类型（FARM_DELETE_TYPE）：OWNER_DELETE 所有者删除 / ACCOUNT_CANCEL 注销删除',
    deleted_at        DATETIME        NULL COMMENT '删除时间；注销删除的数据保留 180 天后由模块01 清理',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建人用户 ID（首任所有者），系统为 0',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID，系统为 0',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_farm_owner (owner_user_id, deleted),
    KEY idx_farm_audit (audit_status, created_at),
    KEY idx_farm_region (province, district)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='养殖场（当前生效信息）';

CREATE TABLE t_farm_audit
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '注册审核记录 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    submit_no         INT             NOT NULL COMMENT '第几次提交，从 1 起',
    status            VARCHAR(20)     NOT NULL COMMENT '审核状态（AUDIT_STATUS）：PENDING / APPROVED / REJECTED / CANCELED',
    pending_flag      TINYINT(1)      NULL COMMENT '待审核时为 1，处理后置空；配合唯一索引保证每场最多 1 条待审核',
    submitted_at      DATETIME        NOT NULL COMMENT '本次提交时间；待审核期间修改时更新',
    snapshot_json     JSON            NOT NULL COMMENT '本次注册信息快照：名称、品类、规模、联系人、电话、地址拆分字段、经纬度、精度、资质图 file_id 顺序',
    change_summary    VARCHAR(200)    NULL COMMENT '与上一次提交比较的变更摘要；首次提交为空',
    reshoot_file_ids  JSON            NULL COMMENT '驳回时审核员勾选的需重拍 file_id 列表',
    audit_opinion     VARCHAR(200)    NULL COMMENT '审核通过意见',
    reject_reason     VARCHAR(200)    NULL COMMENT '驳回原因原文',
    audited_by        BIGINT UNSIGNED NULL COMMENT '审核管理员 ID（t_admin）',
    audited_at        DATETIME        NULL COMMENT '审核时间',
    cancel_reason     VARCHAR(100)    NULL COMMENT '系统撤销原因：所有者删除养殖场、所有者注销账号',
    request_id        VARCHAR(64)     NULL COMMENT '提交时的 Idempotency-Key，Redis 失效时兜底防重',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_audit_pending (farm_id, pending_flag),
    UNIQUE KEY uk_farm_audit_request (request_id),
    KEY idx_farm_audit_farm (farm_id, submit_no),
    KEY idx_farm_audit_queue (status, submitted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='注册审核记录（每次提交一行）';

CREATE TABLE t_farm_change
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资料变更申请 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    change_no         INT             NOT NULL COMMENT '本场第几次资料变更，从 1 起',
    status            VARCHAR(20)     NOT NULL COMMENT '审核状态（AUDIT_STATUS）：PENDING / APPROVED / REJECTED / CANCELED',
    pending_flag      TINYINT(1)      NULL COMMENT '待审核时为 1，处理后置空；配合唯一索引保证每场最多 1 条待审核',
    name_changed      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '名称变更：1 是',
    address_changed   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '地址变更：1 是',
    qual_changed      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '资质图变更：1 是',
    new_name          VARCHAR(20)     NOT NULL COMMENT '变更后名称，未改时等于原值',
    new_address       VARCHAR(200)    NOT NULL COMMENT '变更后完整地址',
    new_province      VARCHAR(30)     NOT NULL COMMENT '变更后省 / 直辖市',
    new_city          VARCHAR(30)     NOT NULL COMMENT '变更后市；直辖市用省名补齐',
    new_district      VARCHAR(30)     NOT NULL COMMENT '变更后区县',
    new_township      VARCHAR(30)     NULL COMMENT '变更后乡镇 / 街道',
    new_adcode        CHAR(6)         NOT NULL COMMENT '变更后区县行政区划代码',
    new_longitude     DECIMAL(10, 7)  NOT NULL COMMENT '变更后经度（GCJ-02）',
    new_latitude      DECIMAL(10, 7)  NOT NULL COMMENT '变更后纬度（GCJ-02）',
    new_loc_accuracy  INT             NULL COMMENT '变更后定位精度（米）',
    remark            VARCHAR(100)    NULL COMMENT '变更说明',
    before_json       JSON            NOT NULL COMMENT '提交时当前生效的名称、地址、经纬度、资质图 file_id 快照',
    audit_opinion     VARCHAR(200)    NULL COMMENT '审核通过意见',
    reject_reason     VARCHAR(200)    NULL COMMENT '驳回原因',
    audited_by        BIGINT UNSIGNED NULL COMMENT '审核管理员 ID（t_admin）',
    audited_at        DATETIME        NULL COMMENT '审核时间',
    cancel_reason     VARCHAR(100)    NULL COMMENT '系统撤销原因：养殖场已删除',
    request_id        VARCHAR(64)     NULL COMMENT '提交时的 Idempotency-Key',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by        BIGINT UNSIGNED NULL COMMENT '提交人用户 ID，系统为 0',
    created_at        DATETIME        NOT NULL COMMENT '创建时间（即提交时间）',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_change_pending (farm_id, pending_flag),
    UNIQUE KEY uk_farm_change_request (request_id),
    KEY idx_farm_change_farm (farm_id, status),
    KEY idx_farm_change_queue (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='资料变更申请（名称、地址、资质图）';

CREATE TABLE t_farm_member
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员记录 ID',
    farm_id        BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（t_user）',
    role           VARCHAR(20)     NOT NULL COMMENT '角色（FARM_ROLE）：OWNER 所有者 / MANAGER 场长 / STAFF 员工',
    status         VARCHAR(20)     NOT NULL COMMENT '成员状态（MEMBER_STATUS）：ACTIVE / REMOVED / QUIT / ACCOUNT_CANCELED',
    active_flag    TINYINT(1)      NULL COMMENT 'ACTIVE 时为 1，离开后置空；配合唯一索引保证同一账号在同一养殖场只有 1 条在场记录',
    join_type      VARCHAR(20)     NOT NULL COMMENT '加入方式（JOIN_TYPE）：CREATE 创建 / INVITE 邀请加入',
    invite_id      BIGINT UNSIGNED NULL COMMENT '加入所用的邀请 ID（t_farm_invite）',
    joined_at      DATETIME        NOT NULL COMMENT '加入时间',
    left_at        DATETIME        NULL COMMENT '移出、退出、注销时间',
    removed_by     BIGINT UNSIGNED NULL COMMENT '移出操作人用户 ID',
    last_switch_at DATETIME        NULL COMMENT '最近切换到本场的时间，F01 排序用',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（角色与负责栏舍）',
    created_by     BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by     BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID，系统为 0',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_member_active (farm_id, user_id, active_flag),
    KEY idx_farm_member_user (user_id, status),
    KEY idx_farm_member_role (farm_id, status, role)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='养殖场成员（被移出后再次加入新增一行、保留历史）';

CREATE TABLE t_farm_invite
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邀请 ID',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    invitee_phone     VARCHAR(11)     NOT NULL COMMENT '被邀请人手机号',
    invitee_user_id   BIGINT UNSIGNED NULL COMMENT '被邀请人用户 ID；未注册时为空，注册后补写',
    role              VARCHAR(20)     NOT NULL COMMENT '邀请角色（FARM_ROLE）：MANAGER / STAFF',
    status            VARCHAR(20)     NOT NULL COMMENT '邀请状态（INVITE_STATUS）：PENDING / ACCEPTED / REJECTED / EXPIRED / CANCELED',
    pending_flag      TINYINT(1)      NULL COMMENT 'PENDING 时为 1，处理后置空',
    expires_at        DATETIME        NOT NULL COMMENT '过期时间：发送时刻 + 7 天；冻结期间顺延',
    handled_at        DATETIME        NULL COMMENT '同意、拒绝、过期、作废时间',
    resend_of_id      BIGINT UNSIGNED NULL COMMENT '重新邀请时指向旧邀请 ID',
    member_id         BIGINT UNSIGNED NULL COMMENT '同意后生成的成员记录 ID（t_farm_member）',
    cancel_reason     VARCHAR(100)    NULL COMMENT '作废原因：已重新邀请、对方已是成员、养殖场已删除、对方账号已注销',
    request_id        VARCHAR(64)     NULL COMMENT '发送时的 Idempotency-Key',
    created_by        BIGINT UNSIGNED NULL COMMENT '邀请人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间（即邀请时间）',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_invite_pending (farm_id, invitee_phone, pending_flag),
    UNIQUE KEY uk_farm_invite_request (request_id),
    KEY idx_farm_invite_farm (farm_id, status, created_at),
    KEY idx_farm_invite_phone (invitee_phone, status),
    KEY idx_farm_invite_expire (status, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='成员邀请（7 天有效）';

CREATE TABLE t_farm_invite_pen
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邀请预分配栏舍 ID',
    invite_id  BIGINT UNSIGNED NOT NULL COMMENT '邀请 ID（t_farm_invite）',
    farm_id    BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    pen_id     BIGINT UNSIGNED NOT NULL COMMENT '预分配栏舍 ID（t_pen，模块04）',
    created_by BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    created_at DATETIME        NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_pen (invite_id, pen_id),
    KEY idx_invite_pen_farm_pen (farm_id, pen_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='邀请预分配栏舍（只对员工邀请写入）';

CREATE TABLE t_member_pen
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '员工负责栏舍 ID',
    farm_id    BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    member_id  BIGINT UNSIGNED NOT NULL COMMENT '成员记录 ID（t_farm_member，只对在场员工）',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（冗余，便于各模块按用户取范围）',
    pen_id     BIGINT UNSIGNED NOT NULL COMMENT '栏舍 ID（t_pen，模块04）',
    created_by BIGINT UNSIGNED NULL COMMENT '分配操作人用户 ID',
    created_at DATETIME        NOT NULL COMMENT '分配时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_pen (member_id, pen_id),
    KEY idx_member_pen_pen (farm_id, pen_id),
    KEY idx_member_pen_user (user_id, farm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='员工负责栏舍（调整时物理删除后重建）';

CREATE TABLE t_farm_transfer
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '所有权转让 ID',
    farm_id       BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    from_user_id  BIGINT UNSIGNED NOT NULL COMMENT '发起人（原所有者）用户 ID',
    to_user_id    BIGINT UNSIGNED NOT NULL COMMENT '受让人用户 ID',
    to_member_id  BIGINT UNSIGNED NOT NULL COMMENT '受让人的成员记录 ID（t_farm_member）',
    status        VARCHAR(20)     NOT NULL COMMENT '转让状态（TRANSFER_STATUS）：PENDING / ACCEPTED / REJECTED / CANCELED / EXPIRED',
    pending_flag  TINYINT(1)      NULL COMMENT 'PENDING 时为 1，处理后置空；同一养殖场同时最多 1 条待确认',
    expires_at    DATETIME        NOT NULL COMMENT '过期时间：发起时刻 + 7 天；冻结期间顺延',
    handled_at    DATETIME        NULL COMMENT '接受、拒绝、撤回、过期时间',
    cancel_reason VARCHAR(100)    NULL COMMENT '取消原因：所有者撤回、对方已不是本场场长、养殖场已删除、账号已注销',
    request_id    VARCHAR(64)     NULL COMMENT '发起时的 Idempotency-Key',
    created_by    BIGINT UNSIGNED NULL COMMENT '发起人用户 ID',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_transfer_pending (farm_id, pending_flag),
    UNIQUE KEY uk_farm_transfer_request (request_id),
    KEY idx_farm_transfer_to (to_user_id, status),
    KEY idx_farm_transfer_expire (status, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='所有权转让（7 天有效）';
