package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A16 修改姓名（乐观锁 version 取 A15 的 profile_version）。
 */
public class ProfileUpdateRequest {

    private final String name;
    private final int version;

    public ProfileUpdateRequest(@NonNull String name, int version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }
}
