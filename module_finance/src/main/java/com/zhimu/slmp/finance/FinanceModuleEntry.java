package com.zhimu.slmp.finance;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.finance.di.FinanceInjector;

import java.util.Collections;
import java.util.List;

/**
 * 模块10 收支、出栏、行情与报表组件入口：创建 FinanceComponent；之后在这里向 ServiceHub 注册服务、向 TabRegistry 注册底部导航标签。
 */
public class FinanceModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        FinanceInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Collections.emptyList();
    }
}
