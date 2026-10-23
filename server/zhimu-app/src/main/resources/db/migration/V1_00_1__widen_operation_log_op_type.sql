-- 公共表结构补充：管理端基础数据的 op_type（BASE_INSPECT_TEMPLATE、BASE_FINANCE_CATEGORY）超过 20 字符，
-- 扩到 32，避免 OperationLogRecorderImpl 截断后 WA-S06 筛不到。V1_00 已执行，不能改原脚本。
ALTER TABLE t_operation_log
    MODIFY COLUMN op_type VARCHAR(32) NOT NULL COMMENT '操作类别，例如 FARM_AUDIT、BASE_INSPECT_TEMPLATE；用户端修改记录为 RECORD_EDIT';
