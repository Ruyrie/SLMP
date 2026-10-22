package com.zhimu.slmp.stock;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.stock.di.StockInjector;

import java.util.Collections;
import java.util.List;

/**
 * 模块09 饲料与兽药疫苗库存组件入口：创建 StockComponent；之后在这里向 ServiceHub 注册服务、向 TabRegistry 注册底部导航标签。
 */
public class StockModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        StockInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Collections.emptyList();
    }
}
