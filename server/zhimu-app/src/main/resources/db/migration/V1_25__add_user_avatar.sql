-- 用户真实头像：t_user 增加头像文件 ID（t_file.id）
-- 头像文件由 POST /api/v1/files?scene=AVATAR 上传，再由 PUT /api/v1/account/avatar 绑定；
-- 已执行的 V1_01 不再修改，按约定新增 V1_25 结构脚本。

ALTER TABLE t_user ADD COLUMN avatar_file_id BIGINT NULL COMMENT '头像文件 ID' AFTER name;
