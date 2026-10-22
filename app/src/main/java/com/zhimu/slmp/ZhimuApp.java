package com.zhimu.slmp;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 壳工程 Application：列出全部组件入口（后续 7 个业务组件尚未加入工程时被跳过）。
 */
public class ZhimuApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                // 基础组件
                "com.zhimu.slmp.media.MediaModuleEntry",
                "com.zhimu.slmp.map.MapModuleEntry",
                // 业务组件（本期）
                "com.zhimu.slmp.account.AccountModuleEntry",
                "com.zhimu.slmp.farm.FarmModuleEntry",
                "com.zhimu.slmp.today.TodayModuleEntry",
                "com.zhimu.slmp.herd.HerdModuleEntry",
                // 业务组件（后续迭代）
                "com.zhimu.slmp.breed.BreedModuleEntry",
                "com.zhimu.slmp.feed.FeedModuleEntry",
                "com.zhimu.slmp.inspect.InspectModuleEntry",
                "com.zhimu.slmp.health.HealthModuleEntry",
                "com.zhimu.slmp.stock.StockModuleEntry",
                "com.zhimu.slmp.finance.FinanceModuleEntry",
                "com.zhimu.slmp.ai.AiModuleEntry");
    }
}
