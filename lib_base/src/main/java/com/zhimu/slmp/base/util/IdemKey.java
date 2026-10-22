package com.zhimu.slmp.base.util;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * 幂等键（请求头 Idempotency-Key）：用户点击提交时生成，同一次提交的重试沿用；
 * 业务错误后用户修改内容重新提交时重新生成（04 号文档 7.5 节）。
 */
public final class IdemKey {

    private IdemKey() {
    }

    @NonNull
    public static String newKey() {
        return UUID.randomUUID().toString();
    }
}
