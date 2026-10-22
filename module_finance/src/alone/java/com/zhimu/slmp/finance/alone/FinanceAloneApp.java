package com.zhimu.slmp.finance.alone;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 模块10 收支、出栏、行情与报表组件单独运行时的 Application：只加载本组件和它依赖的 lib 组件入口。
 */
public class FinanceAloneApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                "com.zhimu.slmp.media.MediaModuleEntry",
                "com.zhimu.slmp.finance.FinanceModuleEntry");
    }
}
