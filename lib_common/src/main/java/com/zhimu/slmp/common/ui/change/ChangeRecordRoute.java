package com.zhimu.slmp.common.ui.change;

import com.zhimu.slmp.common.model.vo.ChangeRequestVO;
import com.zhimu.slmp.common.router.RouterPath;
import java.util.LinkedHashMap;
import java.util.Map;

/** 修改申请打开原单据，始终携带原记录 ID，避免跳到新增表单。 */
public final class ChangeRecordRoute {
    private ChangeRecordRoute() {}

    public static String action(ChangeRequestVO item) {
        String type = item.getBizType();
        if (type == null || item.getBizId() <= 0) return null;
        switch (type) {
            case "INBOUND": case "OUTBOUND": case "LOSS": case "STOCKTAKE": return RouterPath.STOCK_RECORD;
            case "SALE_ORDER": return RouterPath.E07;
            case "BATCH_ENTRY": return item.getBatchId() != null && item.getBatchId() > 0 ? RouterPath.L08 : null;
            case "BATCH_TRANSFER": return RouterPath.L10;
            case "BATCH_DEATH": return RouterPath.L11;
            case "FEED_RECORD": return RouterPath.G02;
            case "WEIGH_RECORD": return RouterPath.G05;
            case "INSPECTION": return RouterPath.I02;
            case "IMMUNE_RECORD": return RouterPath.D03;
            case "MEDICATION_RECORD": return RouterPath.D04;
            case "DISINFECT_RECORD": return RouterPath.D08;
            case "FARROWING": return RouterPath.B04;
            case "INCUBATION": return RouterPath.B07;
            default: return item.getBatchId() != null ? RouterPath.L06 : null;
        }
    }

    public static Map<String, Object> arguments(ChangeRequestVO item) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(RouterPath.EXTRA_BIZ_TYPE, item.getBizType());
        args.put(RouterPath.EXTRA_BIZ_ID, item.getBizId());
        args.put(RouterPath.EXTRA_RECORD_ID, item.getBizId());
        args.put(RouterPath.EXTRA_FARM_ID, item.getFarmId());
        if (item.getBatchId() != null) args.put(RouterPath.EXTRA_BATCH_ID, item.getBatchId());
        String type = item.getBizType();
        if ("SALE_ORDER".equals(type)) args.put(RouterPath.EXTRA_SALE_ORDER_ID, item.getBizId());
        if ("INBOUND".equals(type) || "OUTBOUND".equals(type) || "LOSS".equals(type) || "STOCKTAKE".equals(type))
            args.put(RouterPath.EXTRA_RECORD_KIND, type);
        if ("BATCH_ENTRY".equals(type)) args.put(RouterPath.EXTRA_ENTRY_EVENT_ID, item.getBizId());
        if ("BATCH_TRANSFER".equals(type) || "BATCH_DEATH".equals(type))
            args.put(RouterPath.EXTRA_EVENT_ID, item.getBizId());
        if ("WEIGH_RECORD".equals(type)) args.put(RouterPath.EXTRA_WEIGH_RECORD_ID, item.getBizId());
        if ("INSPECTION".equals(type)) args.put(RouterPath.EXTRA_INSPECTION_ID, item.getBizId());
        if ("INCUBATION".equals(type)) args.put(RouterPath.EXTRA_INCUBATION_ID, item.getBizId());
        return args;
    }
}
