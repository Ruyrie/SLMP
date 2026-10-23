package com.zhimu.slmp.stock;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.stock.di.StockInjector;

import java.util.Arrays;
import java.util.List;

/**
 * 模块09 饲料与兽药疫苗库存组件入口：创建 StockComponent。本组件没有底部导航标签，也不对外提供服务；
 * 入口由 L01（M01、M05）、T02（M03、M08）、G02 / G01（M03）、D03（M08）、T03 / T06（M01、M03、M05、M06）以 RouterPath 打开。
 */
public class StockModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        StockInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        return Arrays.asList(
                new DebugPage("M01 饲料库存", RouterPath.M01),
                new DebugPage("M02 饲料档案（育肥全价料）", RouterPath.M02, longArg(StockExtras.EXTRA_FEED_ID, 5101)),
                new DebugPage("M03 饲料入库（预选育肥全价料）", RouterPath.M03, longArg(StockExtras.EXTRA_FEED_ID, 5101)),
                new DebugPage("M03 入库单（已发生出库）", RouterPath.M03, longArg(StockExtras.EXTRA_RECORD_ID, 6201)),
                new DebugPage("M04 盘点与报损", RouterPath.M04),
                new DebugPage("M05 兽药与疫苗库存", RouterPath.M05),
                new DebugPage("M06 批号详情（已停用）", RouterPath.M06, longArg(StockExtras.EXTRA_LOT_ID, 5201)),
                new DebugPage("M06 批号详情（临期）", RouterPath.M06, longArg(StockExtras.EXTRA_LOT_ID, 6301)),
                new DebugPage("M07 登记报废（已停用批号）", RouterPath.M07, longArg(StockExtras.EXTRA_LOT_ID, 5201)),
                new DebugPage("M08 兽药与疫苗入库", RouterPath.M08),
                new DebugPage("M08 入库单（未出库，可修改）", RouterPath.M08, longArg(StockExtras.EXTRA_RECORD_ID, 6211)),
                new DebugPage("M09 药品档案（氟苯尼考粉）", RouterPath.M09, longArg(StockExtras.EXTRA_DRUG_ID, 5202)),
                new DebugPage("M09 新增药品档案", RouterPath.M09),
                new DebugPage("M10 饲料其他出库", RouterPath.M10),
                new DebugPage("M10 其他出库记录（今天）", RouterPath.M10, longArg(StockExtras.EXTRA_RECORD_ID, 6502)),
                new DebugPage("M11 领用（聚维酮碘 2025033）", RouterPath.M11, lotArgs(5301, 6301)),
                new DebugPage("M11 退货记录", RouterPath.M11, longArg(StockExtras.EXTRA_RECORD_ID, 6512)),
                new DebugPage("M11 领用记录（今天，关联栏舍）", RouterPath.M11, longArg(StockExtras.EXTRA_RECORD_ID, 6511)));
    }

    @NonNull
    private static Bundle longArg(@NonNull String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }

    @NonNull
    private static Bundle lotArgs(long drugId, long lotId) {
        Bundle b = longArg(StockExtras.EXTRA_DRUG_ID, drugId);
        b.putLong(StockExtras.EXTRA_LOT_ID, lotId);
        return b;
    }
}
