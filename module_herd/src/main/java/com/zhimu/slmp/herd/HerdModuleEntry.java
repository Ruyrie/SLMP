package com.zhimu.slmp.herd;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.herd.di.HerdInjector;

import java.util.Collections;
import java.util.List;

/**
 * 模块04 栏舍与存栏组件入口：创建 HerdComponent；之后在这里向 ServiceHub 注册服务、向 TabRegistry 注册底部导航标签。
 */
public class HerdModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        HerdInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Collections.emptyList();
    }
}
