package com.zhimu.slmp.feed.alone;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 模块06 饲喂与生长称重组件单独运行时的 Application：只加载本组件和它依赖的 lib 组件入口。
 */
public class FeedAloneApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                "com.zhimu.slmp.feed.FeedModuleEntry");
    }
}
