package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.vo.AccountUserVO;

/**
 * 用户真实头像：绑定本人上传的 AVATAR 图片、清除头像，以及为账号响应补头像临时地址。
 * 上传仍走 POST /api/v1/files?scene=AVATAR，本服务只做文件归属与场景校验。
 */
public interface AvatarService {

    /** 绑定头像并返回带 presigned URL 的当前账号；文件不存在 40401，非本人 40303，非头像图片 40001 */
    AccountUserVO updateAvatar(Long fileId);

    /** 清除头像并返回当前账号（avatar_url 为 null） */
    AccountUserVO clearAvatar();

    /** 头像文件临时地址；fileId 为空或文件已删除时返回 null，不阻断账号响应 */
    String avatarUrl(Long avatarFileId);
}
