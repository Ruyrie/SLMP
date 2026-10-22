package com.zhimu.slmp.ai;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.ai.di.AiInjector;

import java.util.Collections;
import java.util.List;

/**
 * 模块11 AI 养殖助手与养殖课堂组件入口：创建 AiComponent；之后在这里向 ServiceHub 注册服务、向 TabRegistry 注册底部导航标签。
 */
public class AiModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        AiInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Collections.emptyList();
    }
}
