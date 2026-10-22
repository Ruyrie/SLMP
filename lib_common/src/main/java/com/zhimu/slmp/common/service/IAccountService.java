package com.zhimu.slmp.common.service;

import android.app.Activity;

import androidx.annotation.NonNull;

/**
 * 账号服务（模块01 实现）。
 */
public interface IAccountService {

    /**
     * 冷启动分发（SplashActivity 调用）：没有 refresh_token 打开 A01；有则调 API-A08，
     * 按 next_step 进入 MainActivity、A04 或 A06。完成跳转后由实现方 finish 启动页。
     */
    void checkSession(@NonNull Activity splash);

    /** 按强制步骤打开页面（40309 的 next_step：SAVE_RECOVERY_CODE 进入 A04，RECOVERY_CHANGE_PASSWORD 进入 A06） */
    void openNextStep(@NonNull Activity from, @NonNull String nextStep);

    /** 退出登录：调用 API-A07（失败不阻断）、解绑推送、清除会话并回到 A01 */
    void logout();
}
