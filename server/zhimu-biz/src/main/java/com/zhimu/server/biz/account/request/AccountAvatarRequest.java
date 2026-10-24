package com.zhimu.server.biz.account.request;

/** 绑定头像：file_id 为 POST /api/v1/files?scene=AVATAR 返回的文件 ID。 */
public record AccountAvatarRequest(Long fileId) {
}
