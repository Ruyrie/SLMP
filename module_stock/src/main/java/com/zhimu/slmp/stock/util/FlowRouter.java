package com.zhimu.slmp.stock.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 点流水跳转（技术准备文档 2.6.6，biz_type 取值按服务端 t_stock_flow）：
 * <ul>
 *     <li>FEED_RECORD → L06 批次档案（batch_id；record_id 供 L06 定位并展开该条饲喂记录）</li>
 *     <li>IMMUNE_RECORD / MEDICATION_RECORD / DISINFECT_RECORD → D03 / D04 / D08 记录（record_id，待定事项 4）</li>
 *     <li>INBOUND → 饲料 M03、药品 M08 的只读状态；OUTBOUND → 其他出库 M10、领用与退货 M11 的只读状态</li>
 *     <li>LOSS、STOCKTAKE → 本组件的单据只读页（{@link #PAGE_RECORD}）</li>
 *     <li>CHANGE_REQUEST 等其他取值不跳转</li>
 * </ul>
 */
public final class FlowRouter {

    /** 本组件单据只读页（显式 Intent，没有页面编号） */
    public static final String PAGE_RECORD = "STOCK_RECORD";

    private FlowRouter() {
    }

    /** 跳转目标：页面编号（RouterTable 可识别）或 {@link #PAGE_RECORD}，加参数 */
    public static final class Target {
        @NonNull
        public final String page;
        @NonNull
        public final Map<String, Object> args;

        Target(@NonNull String page, @NonNull Map<String, Object> args) {
            this.page = page;
            this.args = Collections.unmodifiableMap(args);
        }
    }

    @Nullable
    public static Target route(@Nullable StockFlowItemVO f) {
        if (f == null || f.bizType == null || f.bizId == null) {
            return null;
        }
        Map<String, Object> args = new LinkedHashMap<>();
        long id = f.bizId;
        switch (f.bizType) {
            case StockEnums.BIZ_FEED_RECORD:
                if (f.batchId == null) {
                    return null;
                }
                args.put(RouterPath.EXTRA_BATCH_ID, f.batchId);
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target("L06", args);
            case StockEnums.BIZ_IMMUNE_RECORD:
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target("D03", args);
            case StockEnums.BIZ_MEDICATION_RECORD:
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target("D04", args);
            case StockEnums.BIZ_DISINFECT_RECORD:
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target("D08", args);
            case StockEnums.BIZ_INBOUND:
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target(StockEnums.MATERIAL_DRUG.equals(f.materialType) ? "M08" : "M03", args);
            case StockEnums.BIZ_OUTBOUND:
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target(StockEnums.FLOW_OTHER_OUT.equals(f.flowType) ? "M10" : "M11", args);
            case StockEnums.BIZ_LOSS:
                args.put(RouterPath.EXTRA_RECORD_KIND, StockExtras.KIND_LOSS);
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target(PAGE_RECORD, args);
            case StockEnums.BIZ_STOCKTAKE:
                args.put(RouterPath.EXTRA_RECORD_KIND, StockExtras.KIND_STOCKTAKE);
                args.put(RouterPath.EXTRA_RECORD_ID, id);
                return new Target(PAGE_RECORD, args);
            default:
                return null;
        }
    }
}
