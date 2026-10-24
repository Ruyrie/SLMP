-- 消毒计划覆盖栏舍补 updated_at：实体继承 AuditEntity，MyBatis-Plus 选中该列，原表只有 created_at。
ALTER TABLE t_disinfect_plan_pen
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;
