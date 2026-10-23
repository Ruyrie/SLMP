package com.zhimu.slmp.feed;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.feed.di.FeedInjector;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块06 饲喂与生长称重组件入口：创建 FeedComponent；本组件没有底部导航标签与对外服务。
 * debugPages 列出 G01–G06（带示例参数），单独运行与调试入口使用。
 */
public class FeedModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        FeedInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("G01 今日饲喂任务", RouterPath.G01));
        pages.add(new DebugPage("G02 登记投喂 · 3 号育肥舍午餐（task_id=70108）", RouterPath.G02,
                id(RouterPath.EXTRA_TASK_ID, 70108)));
        pages.add(new DebugPage("G02 登记投喂 · 1 号保育舍早餐已逾期（task_id=70101）", RouterPath.G02,
                id(RouterPath.EXTRA_TASK_ID, 70101)));
        pages.add(new DebugPage("G02 修改投喂 · 今天早餐 158 千克（record_id=7106）", RouterPath.G02,
                id(RouterPath.EXTRA_RECORD_ID, 7106)));
        pages.add(new DebugPage("G02 投喂记录 · 10月18日晚餐（record_id=7001）", RouterPath.G02,
                id(RouterPath.EXTRA_RECORD_ID, 7001)));
        pages.add(new DebugPage("G03 饲喂方案 · ZM2610-031（batch_id=3001）", RouterPath.G03,
                id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("G04 饲喂执行情况", RouterPath.G04));
        pages.add(new DebugPage("G05 称重登记 · ZM2610-031（batch_id=3001）", RouterPath.G05,
                id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("G05 称重登记 · 未带批次（先选批次）", RouterPath.G05));
        pages.add(new DebugPage("G05 修改称重 · 10月19日（weigh_record_id=3301）", RouterPath.G05,
                id(RouterPath.EXTRA_WEIGH_RECORD_ID, 3301)));
        pages.add(new DebugPage("G06 生长曲线 · ZM2610-031（batch_id=3001）", RouterPath.G06,
                id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("G06 生长曲线 · 已结束批次 ZM2609-012（batch_id=3009）", RouterPath.G06,
                id(RouterPath.EXTRA_BATCH_ID, 3009)));
        return pages;
    }

    @NonNull
    private static Bundle id(@NonNull String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }
}
