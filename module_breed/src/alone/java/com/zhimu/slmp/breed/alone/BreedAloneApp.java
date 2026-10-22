package com.zhimu.slmp.breed.alone;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.app.BaseApplication;

import java.util.Arrays;
import java.util.List;

/**
 * 模块05 繁育管理组件单独运行时的 Application：只加载本组件和它依赖的 lib 组件入口。
 */
public class BreedAloneApp extends BaseApplication {

    @NonNull
    @Override
    protected List<String> moduleEntryClassNames() {
        return Arrays.asList(
                "com.zhimu.slmp.breed.BreedModuleEntry");
    }
}
