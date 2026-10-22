package com.zhimu.slmp.herd.alone;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 模块04 栏舍与存栏组件单独运行时的 Application：只加载本组件和它依赖的 lib 组件入口。
 */
public class HerdAloneApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                "com.zhimu.slmp.media.MediaModuleEntry",
                "com.zhimu.slmp.herd.HerdModuleEntry");
    }
}
