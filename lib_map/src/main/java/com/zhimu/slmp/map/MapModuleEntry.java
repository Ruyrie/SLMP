package com.zhimu.slmp.map;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.map.contract.PickLocationContract;
import com.zhimu.slmp.map.di.MapInjector;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.PickLocationInput;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 地图与定位组件入口（高德 SDK、F04 地图选点）：创建 MapComponent；用户同意隐私政策后调用三个 SDK 的隐私合规接口。
 * 本组件被 module_farm 直接依赖（PickLocationContract），不向 ServiceHub 注册服务。
 */
public class MapModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        MapInjector.init(core);
    }

    @Override
    public void onPrivacyAgreed(@NonNull Application app) {
        AmapInitializer.onPrivacyAgreed(app);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        Bundle create = new Bundle();
        create.putParcelable(PickLocationContract.EXTRA_INPUT,
                new PickLocationInput(PickLocationInput.MODE_NEW, "北湾牛场", null));
        LocationResult origin = new LocationResult(new BigDecimal("118.8516000"), new BigDecimal("42.3025000"),
                "内蒙古自治区", "赤峰市", "松山区", "太平地镇", "150404", "赤峰市松山区太平地镇", null, null);
        Bundle change = new Bundle();
        change.putParcelable(PickLocationContract.EXTRA_INPUT,
                new PickLocationInput(PickLocationInput.MODE_CHANGE, "东山猪场", origin));
        return Arrays.asList(
                new DebugPage("F04 养殖场位置选点 · 新建（北湾牛场）", RouterPath.F04, create),
                new DebugPage("F04 养殖场位置选点 · 资料变更（东山猪场原位置）", RouterPath.F04, change));
    }
}
