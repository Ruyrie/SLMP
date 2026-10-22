package com.zhimu.slmp.common.service;

import android.app.Application;

import androidx.annotation.NonNull;

/**
 * 推送服务（模块03 实现，极光推送）：用户同意隐私政策后 init；登录后 syncDevice（设置别名、API-T20）；
 * 退出或登录失效时 unbindCurrent（删除别名、API-T21）。
 */
public interface IPushService {

    void init(@NonNull Application app);

    void syncDevice();

    void unbindCurrent();
}
