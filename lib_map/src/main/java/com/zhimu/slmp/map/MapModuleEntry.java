package com.zhimu.slmp.map;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.service.IModuleEntry;

/**
 * 地图与定位（高德 SDK、F04 地图选点）组件入口（空骨架，之后由负责的代理填充）。
 */
public class MapModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
    }
}
