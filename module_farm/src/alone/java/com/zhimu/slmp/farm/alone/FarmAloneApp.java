package com.zhimu.slmp.farm.alone;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 模块02 养殖场与成员管理组件单独运行时的 Application：只加载本组件和它依赖的 lib 组件入口。
 */
public class FarmAloneApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                "com.zhimu.slmp.media.MediaModuleEntry",
                "com.zhimu.slmp.map.MapModuleEntry",
                "com.zhimu.slmp.farm.FarmModuleEntry");
    }
}
