package com.zhimu.slmp.account.model.request;

/**
 * 绑定头像：PUT api/v1/account/avatar，JSON {@code {"file_id":123}}（Gson 下划线策略）。
 */
public class AvatarBindRequest {

    private final long fileId;

    public AvatarBindRequest(long fileId) {
        this.fileId = fileId;
    }

    public long getFileId() {
        return fileId;
    }
}
