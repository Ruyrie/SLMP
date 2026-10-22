package com.zhimu.slmp.inspect.alone;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 模块07 日常巡检与异常上报 AI 诊断组件单独运行时的 Application：只加载本组件和它依赖的 lib 组件入口。
 */
public class InspectAloneApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                "com.zhimu.slmp.media.MediaModuleEntry",
                "com.zhimu.slmp.map.MapModuleEntry",
                "com.zhimu.slmp.inspect.InspectModuleEntry");
    }
}
