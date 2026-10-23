-- 管理端-03 基础数据核心（管理端-03 服务端技术准备文档 2.3 节）
-- 13 张全局表：品类、品种、标准体重、饲喂标准、巡检模板、字典、症状关联病症。
-- 全局表没有 farm_id，不参与多租户；InnoDB、utf8mb4_0900_ai_ci、无外键。
-- 已执行的脚本不再修改；结构变更另起 V1_23_2…（V1_23_1 归 3B 药品/免疫模板）。
-- 本模块不做逻辑删除：记录不存在返回 40401，因此没有 deleted 列。

CREATE TABLE t_category
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '品类 ID，例如 101 育肥猪',
    name           VARCHAR(10)     NOT NULL COMMENT '品类名称，全局唯一，例如 育肥猪',
    species        VARCHAR(20)     NOT NULL COMMENT '畜种（SPECIES）：PIG / POULTRY / CATTLE / SHEEP',
    count_unit     VARCHAR(20)     NOT NULL COMMENT '计数单位（COUNT_UNIT）：HEAD 头 / BIRD 只；由 species 推出',
    is_breeding    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '种畜品类：1 是（种猪），只设繁育参数与免疫模板',
    sort_no        INT             NOT NULL COMMENT '树中顺序',
    status         VARCHAR(20)     NOT NULL COMMENT '启用状态（BASE_STATUS）：ENABLED / DISABLED',
    disable_reason VARCHAR(100)    NULL COMMENT '停用原因，停用必填',
    created_by     BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by     BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at     DATETIME        NOT NULL COMMENT '创建时间',
    updated_at     DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name),
    KEY idx_category_species (species, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='品类（隶属畜种，决定计数单位）';

CREATE TABLE t_breed
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '品种 ID，例如 1101 杜长大',
    category_id           BIGINT UNSIGNED NOT NULL COMMENT '品类 ID（t_category）',
    name                  VARCHAR(20)     NOT NULL COMMENT '品种名称，品类内唯一，例如 杜长大',
    description           VARCHAR(100)    NULL COMMENT '品种说明，例如 杜洛克 × 长白 × 大白三元杂交',
    gestation_days        INT             NULL COMMENT '妊娠期（天），1–400；非禽类，种畜品类必填；长大二元 114',
    incubation_days       INT             NULL COMMENT '孵化期（天），1–400；禽类必填；白羽肉鸡 21',
    pregnancy_check_days  INT             NULL DEFAULT 21 COMMENT '配种后满多少天进入待妊检，1 至妊娠期减 1，默认 21',
    offspring_breed_id    BIGINT UNSIGNED NULL COMMENT '后代默认品种 ID（t_breed），为空取母本品种；须同畜种、启用',
    sort_no               INT             NOT NULL COMMENT '品类内顺序',
    status                VARCHAR(20)     NOT NULL COMMENT '启用状态（BASE_STATUS）：ENABLED / DISABLED',
    disable_reason        VARCHAR(100)    NULL COMMENT '停用原因，停用必填',
    created_by            BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by            BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version               INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at            DATETIME        NOT NULL COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_breed_category_name (category_id, name),
    KEY idx_breed_status (status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='品种（品类内唯一；繁育参数、后代默认品种）';

CREATE TABLE t_std_weight_version
(
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标准体重版本 ID',
    breed_id              BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（t_breed）',
    version_no            INT             NULL COMMENT '版本号，品种内从 1 递增；草稿为空',
    status                VARCHAR(20)     NOT NULL COMMENT '版本状态（VERSION_STATUS）：DRAFT / SCHEDULED / PUBLISHED / SUPERSEDED',
    draft_flag            TINYINT(1)      NULL COMMENT '草稿为 1，其余为空；配合唯一索引保证每个品种最多一条草稿',
    tolerance_rate        DECIMAL(5, 4)   NOT NULL COMMENT '允许偏差，0.0100–0.5000，例如 0.1000（±10%）',
    suggested_weigh_times INT             NOT NULL DEFAULT 2 COMMENT '建议每月称重次数，1–8；28 ÷ 次数 = 称重间隔',
    effective_mode        VARCHAR(20)     NOT NULL COMMENT '生效方式（EFFECTIVE_MODE）：IMMEDIATE / ON_DATE',
    effective_date        DATE            NULL COMMENT '指定生效日期，ON_DATE 必填，生效日 00:05 切换',
    effective_at          DATETIME        NULL COMMENT '实际生效时刻，立即生效时有值',
    update_note           VARCHAR(100)    NULL COMMENT '更新说明，发布必填',
    rollback_from_no      INT             NULL COMMENT '回退来源版本号',
    published_by          BIGINT UNSIGNED NULL COMMENT '发布管理员 ID（t_admin）',
    published_at          DATETIME        NULL COMMENT '发布时间',
    recalculated_at       DATETIME        NULL COMMENT '模块06 重算完成时间；为空表示重算未完成',
    created_by            BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by            BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version               INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（草稿比对）',
    created_at            DATETIME        NOT NULL COMMENT '创建时间',
    updated_at            DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_swv_breed_no (breed_id, version_no),
    UNIQUE KEY uk_swv_breed_draft (breed_id, draft_flag),
    KEY idx_swv_status (status, effective_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='标准体重版本（草稿/待生效/线上/历史）';

CREATE TABLE t_std_weight_point
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标准体重日龄点 ID',
    version_id  BIGINT UNSIGNED NOT NULL COMMENT '版本 ID（t_std_weight_version）',
    day_age     INT             NOT NULL COMMENT '日龄，1–999，例如 90',
    std_weight  DECIMAL(10, 2)  NOT NULL COMMENT '标准体重（千克），> 0，随日龄不减，例如 62.00',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at  DATETIME        NOT NULL COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_swp_version_age (version_id, day_age)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='标准体重日龄点（下限、上限、日增重不存储，由 StdConvert 计算）';

CREATE TABLE t_feed_std_version
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '饲喂标准版本 ID',
    breed_id         BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（t_breed）',
    version_no       INT             NULL COMMENT '版本号，品种内从 1 递增；草稿为空',
    status           VARCHAR(20)     NOT NULL COMMENT '版本状态（VERSION_STATUS）：DRAFT / PUBLISHED / SUPERSEDED',
    draft_flag       TINYINT(1)      NULL COMMENT '草稿为 1，其余为空；配合唯一索引保证每个品种最多一条草稿',
    deviation_rate   DECIMAL(5, 4)   NOT NULL COMMENT '投喂异常判定默认值，0.0100–0.5000，例如 0.1000',
    update_note      VARCHAR(100)    NULL COMMENT '更新说明，发布必填',
    rollback_from_no INT             NULL COMMENT '回退来源版本号',
    published_by     BIGINT UNSIGNED NULL COMMENT '发布管理员 ID（t_admin）',
    published_at     DATETIME        NULL COMMENT '发布时间',
    created_by       BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by       BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（草稿比对）',
    created_at       DATETIME        NOT NULL COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fsv_breed_no (breed_id, version_no),
    UNIQUE KEY uk_fsv_breed_draft (breed_id, draft_flag),
    KEY idx_fsv_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂标准版本（草稿/线上/历史）';

CREATE TABLE t_feed_std_stage
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '饲喂标准阶段 ID',
    version_id          BIGINT UNSIGNED NOT NULL COMMENT '版本 ID（t_feed_std_version）',
    sort_no             INT             NOT NULL COMMENT '阶段顺序，从 1 起',
    stage_name          VARCHAR(10)     NOT NULL COMMENT '阶段名称，例如 育肥前期',
    stage_short_name    VARCHAR(10)     NOT NULL COMMENT '阶段简称，例如 保育猪、育肥猪；批次标题与存栏构成显示',
    day_age_start       INT             NOT NULL COMMENT '起始日龄，首段 ≥ 1',
    day_age_end         INT             NULL COMMENT '结束日龄，为空表示至出栏，只允许最后一段',
    feed_type_code      VARCHAR(32)     NOT NULL COMMENT '饲料类型（字典 FEED_TYPE）：COMPLETE 等',
    suggested_feed_name VARCHAR(20)     NOT NULL COMMENT '建议饲料名称，例如 育肥全价料',
    daily_feed          DECIMAL(10, 3)  NOT NULL COMMENT '每日每头建议投喂量（千克），> 0',
    meals_per_day       TINYINT         NOT NULL COMMENT '每日餐次，1–4',
    meal_times          VARCHAR(30)     NOT NULL COMMENT '投喂时刻，逗号分隔，例如 07:00,11:30,17:00',
    created_by          BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_fss_version (version_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='饲喂标准阶段（日龄连续不重叠）';

CREATE TABLE t_inspect_template
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检模板 ID，例如 11',
    category_id        BIGINT UNSIGNED NOT NULL COMMENT '品类 ID（t_category），每个品类一份',
    name               VARCHAR(20)     NOT NULL COMMENT '模板名称，例如 育肥猪巡检模板',
    online_version_no  INT             NULL COMMENT '线上版本号；从未发布为空',
    has_draft          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '有未发布修改：1 是',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by         BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version            INT             NOT NULL DEFAULT 0 COMMENT '模板乐观锁版本号',
    created_at         DATETIME        NOT NULL COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspect_template_category (category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检项目模板（每个品类一份）';

CREATE TABLE t_inspect_template_version
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检模板版本 ID',
    template_id   BIGINT UNSIGNED NOT NULL COMMENT '模板 ID（t_inspect_template）',
    version_no    INT             NULL COMMENT '版本号，从 1 递增；草稿为空',
    status        VARCHAR(20)     NOT NULL COMMENT '版本状态（VERSION_STATUS）：DRAFT / PUBLISHED / SUPERSEDED',
    draft_flag    TINYINT(1)      NULL COMMENT '草稿为 1，其余为空；配合唯一索引保证最多一条草稿',
    change_note   VARCHAR(100)    NULL COMMENT '发布修改说明',
    item_count    INT             NOT NULL DEFAULT 0 COMMENT '本版项目数',
    published_by  BIGINT UNSIGNED NULL COMMENT '发布管理员 ID（t_admin）',
    published_at  DATETIME        NULL COMMENT '发布时间',
    created_by    BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by    BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（草稿比对）',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_itv_template_no (template_id, version_no),
    UNIQUE KEY uk_itv_template_draft (template_id, draft_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检模板版本（草稿/线上/历史）';

CREATE TABLE t_inspect_item
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检项目 ID，例如 101 精神状态',
    template_id     BIGINT UNSIGNED NOT NULL COMMENT '模板 ID（t_inspect_template）',
    version_id      BIGINT UNSIGNED NOT NULL COMMENT '版本 ID（t_inspect_template_version）',
    source_item_id  BIGINT UNSIGNED NULL COMMENT '草稿项复制自线上哪一项，用于对比',
    change_type     VARCHAR(20)     NOT NULL DEFAULT 'NONE' COMMENT '变化（ITEM_CHANGE）：NONE / ADDED / MODIFIED / DELETED',
    sort_no         INT             NOT NULL COMMENT '用户端显示顺序',
    item_name       VARCHAR(6)      NOT NULL COMMENT '项目名称，例如 粪便；版本内唯一',
    check_note      VARCHAR(50)     NULL COMMENT '管理端检查备注，用户端不显示',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '停用的项目用户端不显示：1 启用，0 停用',
    disable_reason  VARCHAR(100)    NULL COMMENT '停用原因，停用必填',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspect_item_version_name (version_id, item_name),
    KEY idx_inspect_item_version (version_id, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检项目（按模板版本，历史版本不再修改）';

CREATE TABLE t_inspect_option
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '巡检结果选项 ID，例如 1011 正常',
    item_id       BIGINT UNSIGNED NOT NULL COMMENT '项目 ID（t_inspect_item）',
    sort_no       INT             NOT NULL COMMENT '显示顺序，第一个结果必须为正常',
    option_name   VARCHAR(6)      NOT NULL COMMENT '结果名称，例如 水样',
    is_abnormal   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '异常结果：1 异常，0 正常',
    created_by    BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at    DATETIME        NOT NULL COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inspect_option_item_name (item_id, option_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='巡检结果选项（模块07 保存名称快照，历史可追溯）';

CREATE TABLE t_dict_item
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '字典项 ID，例如 301 添加剂（按类型分段）',
    dict_type       VARCHAR(20)     NOT NULL COMMENT '字典类型（DICT_TYPE）：PEN_TYPE / DEATH_REASON / SYMPTOM / FEED_TYPE / VACCINATE_METHOD / DISINFECT_METHOD / SCRAP_REASON / PAYMENT_METHOD',
    code            VARCHAR(32)     NOT NULL COMMENT '业务编码，新增时系统生成，不可修改；业务表只存 code',
    name            VARCHAR(10)     NOT NULL COMMENT '显示名称，同类型同适用范围唯一',
    description     VARCHAR(50)     NULL COMMENT '说明',
    icon            VARCHAR(40)     NULL COMMENT '图标名，预留',
    remark_required TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '选中后说明必填：1 是（死淘原因“其他”）',
    is_fallback     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '兜底项，不能停用',
    is_system       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '系统项，被程序逻辑依赖，不能停用',
    sort_no         INT             NOT NULL COMMENT '用户端从左到右顺序',
    status          VARCHAR(20)     NOT NULL COMMENT '启用状态（BASE_STATUS）：ENABLED / DISABLED',
    disable_reason  VARCHAR(100)    NULL COMMENT '停用原因，停用必填',
    disabled_at     DATETIME        NULL COMMENT '停用时间',
    disabled_by     BIGINT UNSIGNED NULL COMMENT '停用管理员 ID（t_admin）',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    updated_by      BIGINT UNSIGNED NULL COMMENT '最后修改管理员 ID（t_admin），系统为 0',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_code (dict_type, code),
    KEY idx_dict_type_status (dict_type, status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='通用字典项（栏舍、死淘、症状、饲料、接种、消毒、报废、收款）';

CREATE TABLE t_dict_item_scope
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '字典项适用范围 ID',
    dict_item_id BIGINT UNSIGNED NOT NULL COMMENT '字典项 ID（t_dict_item）',
    scope_type   VARCHAR(20)     NOT NULL COMMENT '范围类型（DICT_SCOPE）：SPECIES 按畜种 / CATEGORY 按品类',
    species      VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '畜种（SPECIES）；按品类时为空字符串',
    category_id  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '品类 ID（t_category）；按畜种时为 0',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at   DATETIME        NOT NULL COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_scope (dict_item_id, scope_type, species, category_id),
    KEY idx_dict_scope_category (category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='字典项适用范围（可空列用空字符串/0，避免 NULL 唯一索引失效）';

CREATE TABLE t_symptom_disease
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '症状关联病症 ID',
    symptom_item_id  BIGINT UNSIGNED NOT NULL COMMENT '症状字典项 ID（t_dict_item，dict_type = SYMPTOM）',
    knowledge_id     BIGINT UNSIGNED NOT NULL COMMENT '知识条目 ID（t_knowledge，管理端-02；source_type = DISEASE 且已上架）',
    sort_no          INT             NOT NULL COMMENT '显示顺序',
    created_by       BIGINT UNSIGNED NULL COMMENT '创建管理员 ID（t_admin），系统为 0',
    created_at       DATETIME        NOT NULL COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_symptom_disease (symptom_item_id, knowledge_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='症状关联常见病症（本批不写数据，知识库随管理端-02）';
