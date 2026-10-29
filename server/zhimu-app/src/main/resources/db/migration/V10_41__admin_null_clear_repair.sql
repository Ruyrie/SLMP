-- 2026-10-29 管理端“想清空却没清空”（updateById 跳过 null）留下的历史数据修复。
-- 只按能明确判定的不一致状态清理，可在云端已有数据上重复执行，无匹配时不改任何行。
-- 不能判定的（清空过的课程简介、知识关键词、专题简介等）无法还原，不处理。

-- 管理员：改过密码后临时密码有效期残留；启用后停用时间残留
UPDATE t_admin
SET temp_password_expire_at = NULL
WHERE must_change_password = 0
  AND temp_password_expire_at IS NOT NULL;

UPDATE t_admin
SET disabled_at = NULL
WHERE status = 'NORMAL'
  AND disabled_at IS NOT NULL;

-- 巡检项目：重新启用后停用原因残留
UPDATE t_inspect_item
SET disable_reason = NULL
WHERE enabled = 1
  AND disable_reason IS NOT NULL;

-- 免疫模板项目：按日龄 / 按日期切换后另一组字段残留
UPDATE t_immune_template_item
SET plan_month   = NULL,
    plan_day     = NULL,
    advance_days = NULL
WHERE schedule_type = 'BY_DAY_AGE'
  AND (plan_month IS NOT NULL OR plan_day IS NOT NULL OR advance_days IS NOT NULL);

UPDATE t_immune_template_item
SET day_age = NULL
WHERE schedule_type = 'BY_DATE'
  AND day_age IS NOT NULL;

-- 视频转码：重试后等待中的清晰度还带着上次的错误和起止时间；未失败的文件残留转码错误
UPDATE t_file_variant
SET error_message = NULL,
    started_at    = NULL,
    finished_at   = NULL
WHERE status = 'WAITING'
  AND (error_message IS NOT NULL OR started_at IS NOT NULL OR finished_at IS NOT NULL);

UPDATE t_file
SET transcode_error = NULL
WHERE transcode_status IN ('WAITING', 'PROCESSING', 'SUCCESS')
  AND transcode_error IS NOT NULL;
