package com.zhimu.slmp.farm;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.IFarmInviteService;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.farm.di.FarmComponent;
import com.zhimu.slmp.farm.di.FarmInjector;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块02 养殖场与成员管理组件入口：创建 FarmComponent，向 ServiceHub 注册 IFarmContextService（F01 切换、上下文刷新与兜底）
 * 与 IFarmInviteService（邀请、转让的同意与拒绝，供模块03 调用）。本组件没有底部导航标签。
 */
public class FarmModuleEntry implements IModuleEntry {

    /** 调试入口：F01 面板没有 action，由调试页在宿主上打开 */
    public static final String ACTION_SWITCHER_DEMO = "com.zhimu.slmp.farm.debug.SWITCHER";

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        FarmInjector.init(core);
        FarmComponent component = FarmInjector.component();
        ServiceHub.register(IFarmContextService.class, component.farmContextService());
        ServiceHub.register(IFarmInviteService.class, component.farmInviteService());
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("F01 切换养殖场（底部面板）", ACTION_SWITCHER_DEMO));
        pages.add(new DebugPage("F02 新建养殖场", RouterPath.F02));
        pages.add(new DebugPage("F02 修改注册信息（北湾牛场）", RouterPath.F02, farm(1003, FarmExtras.MODE_EDIT)));
        pages.add(new DebugPage("F03 审核结果 · 已驳回（北湾牛场）", RouterPath.F03, farm(1003, null)));
        pages.add(new DebugPage("F03 审核结果 · 已通过（东山猪场，切换后进 F05）", RouterPath.F03, farm(1001, null)));
        pages.add(new DebugPage("F05 养殖场信息（东山猪场）", RouterPath.F05, farm(1001, null)));
        pages.add(new DebugPage("F06 资料变更（东山猪场，滚动到地址）", RouterPath.F06, change()));
        pages.add(new DebugPage("F07 成员管理（东山猪场）", RouterPath.F07, farm(1001, null)));
        pages.add(new DebugPage("F08 邀请成员", RouterPath.F08, farm(1001, null)));
        pages.add(new DebugPage("F09 成员详情 · 员工李明", RouterPath.F09, member(5003)));
        pages.add(new DebugPage("F09 成员详情 · 场长刘芳", RouterPath.F09, member(5002)));
        pages.add(new DebugPage("F10 转让所有权（东山猪场）", RouterPath.F10, farm(1001, null)));
        return pages;
    }

    @NonNull
    private static Bundle farm(long farmId, String mode) {
        Bundle b = new Bundle();
        b.putLong(RouterPath.EXTRA_FARM_ID, farmId);
        if (mode != null) {
            b.putString(RouterPath.EXTRA_MODE, mode);
        }
        return b;
    }

    @NonNull
    private static Bundle member(long memberId) {
        Bundle b = farm(1001, null);
        b.putLong(FarmExtras.EXTRA_MEMBER_ID, memberId);
        return b;
    }

    @NonNull
    private static Bundle change() {
        Bundle b = farm(1001, null);
        b.putString(FarmExtras.EXTRA_SCROLL_TARGET, FarmExtras.TARGET_ADDRESS);
        return b;
    }
}
