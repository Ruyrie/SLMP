package com.zhimu.slmp.inspect;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.inspect.di.InspectInjector;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块07 日常巡检与异常上报 AI 诊断组件入口：创建 InspectComponent。本组件不注册底部导航标签与服务，
 * 页面由 T01、T02、T04、L01、T06 经 RouterPath 打开。相机录像与视频预览的调试入口在 lib_media 的 MediaModuleEntry。
 */
public class InspectModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        InspectInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("I01 今日巡检（所有者 / 员工按登录身份）", RouterPath.I01));
        pages.add(new DebugPage("I02 登记巡检 · 4 号育肥舍（未巡）", RouterPath.I02,
                bundle(RouterPath.EXTRA_PEN_ID, 2004L)));
        pages.add(new DebugPage("I02 登记巡检 · 2 号保育舍（养殖户代登记、手动标记异常）", RouterPath.I02,
                bundle(RouterPath.EXTRA_PEN_ID, 2002L)));
        Bundle modify = bundle(RouterPath.EXTRA_PEN_ID, 2003L);
        modify.putString(RouterPath.EXTRA_MODE, InspectExtras.MODE_MODIFY);
        modify.putLong(InspectExtras.EXTRA_INSPECTION_ID, 50121L);
        pages.add(new DebugPage("I02 修改巡检 · 3 号育肥舍（粪便水样，已自动标异常）", RouterPath.I02, modify));
        Bundle readOnly = bundle(RouterPath.EXTRA_PEN_ID, 2003L);
        readOnly.putString(RouterPath.EXTRA_MODE, InspectExtras.MODE_READONLY);
        readOnly.putLong(InspectExtras.EXTRA_INSPECTION_ID, 50121L);
        pages.add(new DebugPage("I02 巡检详情（只读）", RouterPath.I02, readOnly));
        Bundle backfill = bundle(RouterPath.EXTRA_PEN_ID, 2002L);
        backfill.putString(InspectExtras.EXTRA_INSPECT_DATE, "2026-10-18");
        pages.add(new DebugPage("I03 补录巡检 · 2 号保育舍 10月18日（所有者、场长）", RouterPath.I03, backfill));
        pages.add(new DebugPage("I04 巡检历史 · 3 号育肥舍", RouterPath.I04, bundle(RouterPath.EXTRA_PEN_ID, 2003L)));
        pages.add(new DebugPage("I05 异常上报 · 从巡检转上报（3 号育肥舍）", RouterPath.I05,
                bundle(InspectExtras.EXTRA_INSPECTION_ID, 50121L)));
        pages.add(new DebugPage("I05 异常上报 · 直接上报（T02）", RouterPath.I05));
        pages.add(new DebugPage("I06 诊断详情 · 已诊断 · 关注（6001）", RouterPath.I06,
                bundle(InspectExtras.EXTRA_REPORT_ID, 6001L)));
        pages.add(new DebugPage("I06 诊断详情 · 紧急 · 疑似重大疫病（6002）", RouterPath.I06,
                bundle(InspectExtras.EXTRA_REPORT_ID, 6002L)));
        pages.add(new DebugPage("I06 诊断详情 · 待诊断 · AI 不可用（6003，可重新诊断）", RouterPath.I06,
                bundle(InspectExtras.EXTRA_REPORT_ID, 6003L)));
        pages.add(new DebugPage("I06 诊断详情 · 已处理（6005）", RouterPath.I06,
                bundle(InspectExtras.EXTRA_REPORT_ID, 6005L)));
        pages.add(new DebugPage("I06 诊断详情 · 已结案（5988）", RouterPath.I06,
                bundle(InspectExtras.EXTRA_REPORT_ID, 5988L)));
        pages.add(new DebugPage("I07 登记处理结果（6001）", RouterPath.I07,
                bundle(InspectExtras.EXTRA_REPORT_ID, 6001L)));
        return pages;
    }

    @NonNull
    private static Bundle bundle(@NonNull String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }
}
