package com.zhimu.slmp.herd;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.ui.mypens.MyPensFragment;
import com.zhimu.slmp.herd.ui.production.ProductionFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块04 栏舍与存栏组件入口：创建 HerdComponent，注册底部导航“生产”（所有者、场长）与“我的栏舍”（员工）。
 */
public class HerdModuleEntry implements IModuleEntry {

    /** debug 包中托管一级页 Fragment 的调试页（src/debug，单独运行时由 src/alone 清单声明） */
    public static final String DEBUG_TAB_ACTION = "com.zhimu.slmp.herd.debug.TAB";
    public static final String DEBUG_EXTRA_TAB = "tab";

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        HerdInjector.init(core);
        TabRegistry.register(TabRegistry.PRODUCTION, ProductionFragment::new);
        TabRegistry.register(TabRegistry.MY_PENS, MyPensFragment::new);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("L01 生产页（以所有者登录）", DEBUG_TAB_ACTION, tab(TabRegistry.PRODUCTION)));
        pages.add(new DebugPage("L02 我的栏舍（以员工登录）", DEBUG_TAB_ACTION, tab(TabRegistry.MY_PENS)));
        pages.add(new DebugPage("L03 存栏总览 · 按栏舍", RouterPath.L03));
        pages.add(new DebugPage("L03 存栏总览 · 按批次", RouterPath.L03, str(HerdExtras.EXTRA_VIEW_TYPE,
                HerdExtras.VIEW_BATCH)));
        pages.add(new DebugPage("L04 栏舍详情 · 3 号育肥舍（pen_id=2003）", RouterPath.L04,
                id(RouterPath.EXTRA_PEN_ID, 2003)));
        pages.add(new DebugPage("L04 栏舍详情 · 5 号育肥舍空栏（pen_id=2005）", RouterPath.L04,
                id(RouterPath.EXTRA_PEN_ID, 2005)));
        pages.add(new DebugPage("L05 新增栏舍", RouterPath.L05));
        pages.add(new DebugPage("L05 编辑栏舍（pen_id=2003）", RouterPath.L05, id(RouterPath.EXTRA_PEN_ID, 2003)));
        pages.add(new DebugPage("L06 批次档案 · ZM2610-031（batch_id=3001）", RouterPath.L06,
                id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("L06 批次档案 · 可删除新批次 ZM2610-058（batch_id=3058）", RouterPath.L06,
                id(RouterPath.EXTRA_BATCH_ID, 3058)));
        Bundle cross = id(RouterPath.EXTRA_BATCH_ID, 3201);
        cross.putLong(HerdExtras.EXTRA_FROM_FARM_ID, 1001);
        cross.putString(HerdExtras.EXTRA_FROM_FARM_NAME, "东山猪场");
        cross.putString(HerdExtras.EXTRA_CROSS_FARM_NAME, "西岭鸡场");
        pages.add(new DebugPage("L06 批次档案 · 跨场扫码打开（batch_id=3201）", RouterPath.L06, cross));
        pages.add(new DebugPage("L07 扫码查看档案", RouterPath.L07));
        pages.add(new DebugPage("L08 批次入栏", RouterPath.L08));
        Bundle editEntry = id(RouterPath.EXTRA_BATCH_ID, 3058);
        editEntry.putLong(HerdExtras.EXTRA_ENTRY_EVENT_ID, 958);
        pages.add(new DebugPage("L08 修改入栏记录（batch_id=3058，entry_event_id=958）", RouterPath.L08, editEntry));
        pages.add(new DebugPage("L10 转栏登记（batch_id=3001）", RouterPath.L10, id(RouterPath.EXTRA_BATCH_ID, 3001)));
        Bundle editTransfer = id(RouterPath.EXTRA_BATCH_ID, 3001);
        editTransfer.putLong(HerdExtras.EXTRA_EVENT_ID, 907);
        pages.add(new DebugPage("L10 修改转栏记录（event_id=907）", RouterPath.L10, editTransfer));
        pages.add(new DebugPage("L11 死淘登记（batch_id=3001）", RouterPath.L11, id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("L11 死淘登记（未带批次）", RouterPath.L11));
        Bundle editDeath = id(RouterPath.EXTRA_BATCH_ID, 3001);
        editDeath.putLong(HerdExtras.EXTRA_EVENT_ID, 905);
        pages.add(new DebugPage("L11 修改死淘记录（event_id=905）", RouterPath.L11, editDeath));
        return pages;
    }

    private static Bundle tab(String key) {
        return str(DEBUG_EXTRA_TAB, key);
    }

    private static Bundle str(String key, String value) {
        Bundle b = new Bundle();
        b.putString(key, value);
        return b;
    }

    private static Bundle id(String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }
}
