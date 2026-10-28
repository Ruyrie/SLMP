-- 管理端-04：平台没有通知类短信模板，账号找回改为“页面显示临时密码、线下告知”。
-- 权限说明里的“重发短信”改为“重新生成临时密码”。只改文案，可在已有数据上重复执行。
UPDATE t_admin_permission
SET handle_desc = '通过、驳回、代为登记、重新生成临时密码、查看完整号码'
WHERE perm_code = 'WA-R04:HANDLE'
  AND handle_desc LIKE '%重发短信%';
