package com.zhimu.slmp.account;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.mine.MineFragment;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IAccountService;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TabRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * 模块01 账号与个人中心组件入口：创建 AccountComponent，注册 IAccountService 与底部导航“我的”标签（A07）。
 */
public class AccountModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        AccountInjector.init(core);
        ServiceHub.register(IAccountService.class, AccountInjector.component().accountService());
        TabRegistry.register(TabRegistry.MINE, MineFragment::new);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        Bundle phone = new Bundle();
        phone.putString(RouterPath.EXTRA_PHONE, "13800006620");
        Bundle relogin = new Bundle();
        relogin.putString(RouterPath.EXTRA_REASON, "PASSWORD_CHANGED");
        relogin.putString(RouterPath.EXTRA_PHONE, "13800006620");
        Bundle feedRecord = new Bundle();
        feedRecord.putString(RouterPath.EXTRA_BIZ_TYPE, "FEED_RECORD");
        feedRecord.putLong(RouterPath.EXTRA_BIZ_ID, 7001L);
        return Arrays.asList(
                new DebugPage("A01 登录", RouterPath.A01, phone),
                new DebugPage("A01 登录（登录失效横幅）", RouterPath.A01, relogin),
                new DebugPage("A02 注册账号", RouterPath.A02),
                new DebugPage("A03 重置密码", RouterPath.A03, phone),
                new DebugPage("A04 保存恢复码（A12 重新生成）", RouterPath.A04),
                new DebugPage("A05 找回账号", RouterPath.A05, phone),
                new DebugPage("A06 找回后首次登录", RouterPath.A06),
                new DebugPage("A07 我的（MainActivity“我的”标签）", RouterPath.MAIN),
                new DebugPage("A08 账号与安全", RouterPath.A08),
                new DebugPage("A08 修改密码页", RouterPath.A08_PASSWORD),
                new DebugPage("A09 注销账号", RouterPath.A09),
                new DebugPage("关于智牧通", RouterPath.A07_ABOUT),
                new DebugPage("U01 提交修改申请（投喂记录）", RouterPath.U01, feedRecord),
                new DebugPage("U02 我的修改申请", RouterPath.U02));
    }
}
