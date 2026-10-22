package com.zhimu.slmp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.app.PrivacyManager;
import com.zhimu.slmp.common.service.IAccountService;
import com.zhimu.slmp.common.service.ServiceHub;

/**
 * 启动页（模块01 2.6.2 节）：保持系统启动画面；未同意隐私政策时先弹第 5 级对话框；
 * 再交给 IAccountService.checkSession 分发（模块01 实现并负责 finish 启动页），服务不存在时直接进入 MainActivity。
 */
public class SplashActivity extends BaseActivity {

    private boolean dispatched;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        PrivacyManager.ensureAgreed(this, this::dispatch);
    }

    @Override
    protected boolean applyWindowInsetsToRoot() {
        return false;
    }

    private void dispatch() {
        if (dispatched) {
            return;
        }
        dispatched = true;
        IAccountService account = ServiceHub.get(IAccountService.class);
        if (account != null) {
            account.checkSession(this);
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
