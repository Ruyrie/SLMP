-- 模块05 繁育管理（模块05 服务端技术准备文档 2.3 节）
-- 7 张表：t_breeding_animal 种畜档案、t_breeding_cycle 繁殖周期、t_mating_record 配种、
--   t_pregnancy_check 妊检、t_farrowing_record 产仔、t_weaning_record 断奶、t_incubation 孵化。
-- 本模块没有删除入口，各表不设 deleted；种禽群复用模块04 的 t_batch（purpose = BREEDING）。
-- 不建外键；字符集 utf8mb4 / utf8mb4_0900_ai_ci；养殖场表均带 farm_id，多租户插件按 FarmContext 自动过滤。
-- 公共字段：created_at / updated_at 由框架自动填充；created_by 由框架填当前用户（系统为 0）。
-- 已执行的脚本不再修改；结构变更另起 V1_05_1、V1_05_2…

-- 2.3.1 种畜档案
CREATE TABLE t_breeding_animal
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '种畜 ID，示例 4102–4182',
    farm_id             BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID（t_farm）',
    ear_tag             VARCHAR(20)     NOT NULL COMMENT '耳号，例如 0161；字母、数字、短横线，本场唯一且不复用',
    breed_id            BIGINT UNSIGNED NOT NULL COMMENT '品种 ID（t_breed），例如 1201 长大二元',
    birth_date          DATE            NULL COMMENT '出生日期，例如 2024-06-08',
    source              VARCHAR(20)     NOT NULL COMMENT '来源（ANIMAL_SOURCE）：PURCHASED 外购 / SELF_BRED 自繁',
    pen_id              BIGINT UNSIGNED NOT NULL COMMENT '所在栏舍 ID（t_pen），例如 2006 母猪舍',
    filing_date         DATE            NOT NULL COMMENT '建档日期，新增时取当天',
    initial_parity      INT             NOT NULL DEFAULT 0 COMMENT '建档时胎次 0–30',
    parity              INT             NOT NULL DEFAULT 0 COMMENT '当前胎次 = initial_parity + 系统内产仔次数',
    status              VARCHAR(20)     NOT NULL DEFAULT 'EMPTY' COMMENT '状态（BREEDING_STATUS）：EMPTY 空怀 / MATED 已配种 / PREGNANT 妊娠 / LACTATING 哺乳 / CULLED 已淘汰 / DEAD 已死亡',
    current_cycle_id    BIGINT UNSIGNED NULL COMMENT '未结束的繁殖周期 ID（t_breeding_cycle）',
    last_wean_date      DATE            NULL COMMENT '最近断奶日期',
    last_cycle_end_date DATE            NULL COMMENT '上一周期结束日期（断奶、妊检阴性、活仔为 0 的产仔），配种日期下限',
    exit_type           VARCHAR(20)     NULL COMMENT '死亡淘汰类型（EXIT_TYPE）：CULL 淘汰 / DEATH 死亡',
    exit_date           DATE            NULL COMMENT '死亡淘汰日期',
    exit_reason         VARCHAR(50)     NULL COMMENT '死亡淘汰原因，最多 50 字',
    status_before_exit  VARCHAR(20)     NULL COMMENT '登记前状态，修改申请撤销登记时恢复',
    exit_by             BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    exit_at             DATETIME        NULL COMMENT '登记时间',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本：档案编辑、状态流转、死亡淘汰时比较',
    created_by          BIGINT UNSIGNED NULL COMMENT '创建人用户 ID，系统为 0',
    updated_by          BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at          DATETIME        NOT NULL COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_breeding_animal_ear (farm_id, ear_tag),
    KEY idx_breeding_animal_status (farm_id, status, pen_id),
    KEY idx_breeding_animal_pen (farm_id, pen_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='种畜档案（模块05）';

-- 2.3.2 繁殖周期
CREATE TABLE t_breeding_cycle
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '繁殖周期 ID，示例 5131',
    farm_id              BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    animal_id            BIGINT UNSIGNED NOT NULL COMMENT '种畜 ID（t_breeding_animal）',
    first_mating_date    DATE            NOT NULL COMMENT '本情期首次配种日期，例如 2026-06-27',
    last_mating_date     DATE            NOT NULL COMMENT '本情期最后一次配种日期',
    mating_count         INT             NOT NULL DEFAULT 1 COMMENT '本情期配种次数',
    gestation_days       INT             NOT NULL COMMENT '品种妊娠期快照，例如 114',
    pregnancy_check_days INT             NOT NULL COMMENT '品种待妊检天数快照，品种未设置时为 21',
    check_due_date       DATE            NOT NULL COMMENT '待妊检日 = first_mating_date + pregnancy_check_days',
    expected_due_date    DATE            NOT NULL COMMENT '预产期 = first_mating_date + gestation_days',
    status               VARCHAR(20)     NOT NULL COMMENT '状态（CYCLE_STATUS）：MATED 待妊检 / PREGNANT 妊娠 / FAILED 未孕 / FARROWED 哺乳中 / WEANED 已断奶',
    preg_check_id        BIGINT UNSIGNED NULL COMMENT '本周期妊检记录 ID（t_pregnancy_check）',
    farrowing_id         BIGINT UNSIGNED NULL COMMENT '本周期产仔记录 ID（t_farrowing_record）',
    weaning_id           BIGINT UNSIGNED NULL COMMENT '本周期断奶记录 ID（t_weaning_record）',
    remind_before_sent_at DATETIME       NULL COMMENT '预产期前 3 天提醒发送时间',
    remind_due_sent_at   DATETIME        NULL COMMENT '预产期当天提醒发送时间',
    end_date             DATE            NULL COMMENT '周期结束日期',
    end_reason           VARCHAR(20)     NULL COMMENT '结束原因（CYCLE_END_REASON）：NEGATIVE 妊检阴性 / WEANED 断奶 / NO_LIVE 活仔为 0 / EXIT 死亡淘汰',
    version              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by           BIGINT UNSIGNED NULL COMMENT '创建人用户 ID',
    updated_by           BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at           DATETIME        NOT NULL COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_breeding_cycle_due (farm_id, status, expected_due_date),
    KEY idx_breeding_cycle_check (farm_id, status, check_due_date),
    KEY idx_breeding_cycle_animal (animal_id, first_mating_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='繁殖周期（模块05）';

-- 2.3.3 配种记录
CREATE TABLE t_mating_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配种记录 ID，示例 71002',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    animal_id         BIGINT UNSIGNED NOT NULL COMMENT '种畜 ID',
    cycle_id          BIGINT UNSIGNED NOT NULL COMMENT '繁殖周期 ID',
    mating_date       DATE            NOT NULL COMMENT '配种日期',
    mating_method     VARCHAR(20)     NOT NULL COMMENT '配种方式（MATING_METHOD）：ARTIFICIAL 人工授精 / NATURAL 本交',
    boar_info         VARCHAR(20)     NULL COMMENT '公猪耳号或精液批号，最多 20 字',
    is_first          TINYINT(1)      NOT NULL COMMENT '是否本情期首次配种：1 是',
    client_request_id VARCHAR(64)     NOT NULL COMMENT '请求头 Idempotency-Key 的值，数据库唯一索引兜底防重',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间（登记时间）',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mating_request (farm_id, client_request_id),
    KEY idx_mating_cycle (cycle_id),
    KEY idx_mating_animal (animal_id, mating_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='配种记录（模块05）';

-- 2.3.4 妊检记录
CREATE TABLE t_pregnancy_check
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '妊检记录 ID，示例 71001',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    animal_id         BIGINT UNSIGNED NOT NULL COMMENT '种畜 ID',
    cycle_id          BIGINT UNSIGNED NOT NULL COMMENT '繁殖周期 ID',
    check_date        DATE            NOT NULL COMMENT '妊检日期',
    result            VARCHAR(20)     NOT NULL COMMENT '结果（PREG_RESULT）：POSITIVE 阳性 / NEGATIVE 阴性',
    check_method      VARCHAR(20)     NOT NULL DEFAULT 'ULTRASOUND' COMMENT '方式（PREG_CHECK_METHOD）：ULTRASOUND B 超 / OBSERVATION 观察返情 / OTHER 其他',
    client_request_id VARCHAR(64)     NOT NULL COMMENT '幂等键',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_preg_check_cycle (cycle_id),
    UNIQUE KEY uk_preg_check_request (farm_id, client_request_id),
    KEY idx_preg_check_animal (animal_id, check_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='妊检记录（模块05）';

-- 2.3.5 产仔记录
CREATE TABLE t_farrowing_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '产仔记录 ID，示例 8118',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    animal_id         BIGINT UNSIGNED NOT NULL COMMENT '种畜 ID',
    cycle_id          BIGINT UNSIGNED NOT NULL COMMENT '繁殖周期 ID',
    parity_no         INT             NOT NULL COMMENT '本次为第几胎 = 产仔前 parity + 1',
    farrow_date       DATE            NOT NULL COMMENT '产仔日期',
    total_born        INT             NOT NULL COMMENT '总产仔数 1–50',
    live_born         INT             NOT NULL COMMENT '活仔数 0–total_born',
    stillborn         INT             NOT NULL COMMENT '死胎数（木乃伊计入死胎）',
    transfer_to_batch TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否转为新批次：1 是',
    target_pen_id     BIGINT UNSIGNED NULL COMMENT '入栏栏舍 ID，默认母猪所在栏舍',
    offspring_breed_id BIGINT UNSIGNED NULL COMMENT '后代品种 ID',
    capacity_confirmed TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否确认超容量：1 是',
    batch_id          BIGINT UNSIGNED NULL COMMENT '生成的自繁批次 ID（t_batch）',
    client_request_id VARCHAR(64)     NOT NULL COMMENT '幂等键',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_farrowing_cycle (cycle_id),
    UNIQUE KEY uk_farrowing_request (farm_id, client_request_id),
    KEY idx_farrowing_animal (farm_id, animal_id, farrow_date),
    KEY idx_farrowing_batch (batch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='产仔记录（模块05）';

-- 2.3.6 断奶记录
CREATE TABLE t_weaning_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '断奶记录 ID，示例 71155',
    farm_id           BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    animal_id         BIGINT UNSIGNED NOT NULL COMMENT '种畜 ID',
    cycle_id          BIGINT UNSIGNED NOT NULL COMMENT '繁殖周期 ID',
    farrowing_id      BIGINT UNSIGNED NOT NULL COMMENT '产仔记录 ID',
    wean_date         DATE            NOT NULL COMMENT '断奶日期',
    weaned_count      INT             NOT NULL COMMENT '断奶仔猪数，0 至产仔时活仔数',
    litter_weight     DECIMAL(10, 2)  NULL COMMENT '断奶窝重（千克），最多 1 位小数，不超过 9,999.9',
    client_request_id VARCHAR(64)     NOT NULL COMMENT '幂等键',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by        BIGINT UNSIGNED NULL COMMENT '登记人用户 ID',
    updated_by        BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at        DATETIME        NOT NULL COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_weaning_cycle (cycle_id),
    UNIQUE KEY uk_weaning_request (farm_id, client_request_id),
    KEY idx_weaning_animal (farm_id, animal_id, wean_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='断奶记录（模块05）';

-- 2.3.7 孵化记录（入孵、照蛋、出雏三步同一行）
CREATE TABLE t_incubation
(
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '孵化记录 ID，示例 6001',
    farm_id              BIGINT UNSIGNED NOT NULL COMMENT '养殖场 ID',
    breeding_batch_id    BIGINT UNSIGNED NOT NULL COMMENT '种用批次 ID（t_batch，purpose = BREEDING）',
    pen_id               BIGINT UNSIGNED NOT NULL COMMENT '入孵时种用批次所在栏舍 ID（取在栏数最多者），用于员工范围',
    breed_id             BIGINT UNSIGNED NOT NULL COMMENT '种用批次品种 ID',
    incubation_days      INT             NOT NULL COMMENT '孵化期快照，例如 21',
    set_date             DATE            NOT NULL COMMENT '入孵日期',
    set_count            INT             NOT NULL COMMENT '入孵蛋数 1–999,999',
    expected_hatch_date  DATE            NOT NULL COMMENT '预计出雏日 = set_date + incubation_days',
    candle_date          DATE            NULL COMMENT '照蛋日期',
    fertile_count        INT             NULL COMMENT '受精蛋数',
    candle_by            BIGINT UNSIGNED NULL COMMENT '照蛋登记人用户 ID',
    candle_at            DATETIME        NULL COMMENT '照蛋登记时间',
    hatch_date           DATE            NULL COMMENT '出雏日期',
    hatched_count        INT             NULL COMMENT '出雏数',
    healthy_count        INT             NULL COMMENT '健雏数',
    weak_count           INT             NULL COMMENT '弱雏数 = 出雏数 − 健雏数',
    transfer_to_batch    TINYINT(1)      NULL COMMENT '出雏时是否转为新批次：1 是',
    target_pen_id        BIGINT UNSIGNED NULL COMMENT '入栏鸡舍 ID',
    offspring_breed_id   BIGINT UNSIGNED NULL COMMENT '后代品种 ID',
    capacity_confirmed   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否确认超容量：1 是',
    batch_id             BIGINT UNSIGNED NULL COMMENT '生成的自繁批次 ID（t_batch）',
    hatch_by             BIGINT UNSIGNED NULL COMMENT '出雏登记人用户 ID',
    hatch_at             DATETIME        NULL COMMENT '出雏登记时间',
    status               VARCHAR(20)     NOT NULL COMMENT '状态（INCUBATION_STATUS）：SET 已入孵 / CANDLED 已照蛋 / HATCHED 已出雏',
    remind_before_sent_at DATETIME       NULL COMMENT '出雏前 1 天提醒发送时间',
    remind_due_sent_at   DATETIME        NULL COMMENT '出雏当天提醒发送时间',
    set_request_id       VARCHAR(64)     NOT NULL COMMENT '入孵幂等键',
    hatch_request_id     VARCHAR(64)     NULL COMMENT '出雏幂等键',
    version              INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本；入孵登记人与时间取 created_by、created_at',
    created_by           BIGINT UNSIGNED NULL COMMENT '入孵登记人用户 ID',
    updated_by           BIGINT UNSIGNED NULL COMMENT '最后修改人用户 ID',
    created_at           DATETIME        NOT NULL COMMENT '创建时间（入孵登记时间）',
    updated_at           DATETIME        NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_incubation_set (farm_id, set_request_id),
    UNIQUE KEY uk_incubation_hatch (farm_id, hatch_request_id),
    KEY idx_incubation_due (farm_id, status, expected_hatch_date),
    KEY idx_incubation_flock (farm_id, breeding_batch_id),
    KEY idx_incubation_batch (batch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='孵化记录（模块05）';
