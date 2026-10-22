package com.zhimu.slmp.today;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.today.di.TodayInjector;

import java.util.Collections;
import java.util.List;

/**
 * 模块03 今日首页、日历待办与消息中心组件入口：创建 TodayComponent；之后在这里向 ServiceHub 注册服务、向 TabRegistry 注册底部导航标签。
 */
public class TodayModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        TodayInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Collections.emptyList();
    }
}
