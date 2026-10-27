package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.SourceModule;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 修改申请相关的固定映射（05 号 5.4 / 6.3 / 6.5，任务书第七节）：
 * 可申请修改的 17 种记录类型、UI 长名、来源模块、申请状态与动作中文名。
 * <p>
 * biz_type_text 用 UI 长名（投喂记录、入库单、死淘记录…），不能直接用 {@link BizType#getText()} 的短名。
 */
public final class ChangeBizTypes {

    /** 可提交修改申请的 17 种记录类型（含 BREEDING_EXIT） */
    public static final Set<BizType> APPLICATION_TYPES = Set.of(
            BizType.BATCH_ENTRY, BizType.BATCH_TRANSFER, BizType.BATCH_DEATH,
            BizType.FARROWING, BizType.INCUBATION, BizType.BREEDING_EXIT,
            BizType.FEED_RECORD, BizType.WEIGH_RECORD,
            BizType.INSPECTION,
            BizType.IMMUNE_RECORD, BizType.MEDICATION_RECORD, BizType.DISINFECT_RECORD,
            BizType.INBOUND, BizType.STOCKTAKE, BizType.LOSS, BizType.OUTBOUND,
            BizType.SALE_ORDER);

    private static final Map<BizType, String> LONG_NAMES = new EnumMap<>(BizType.class);
    private static final Map<BizType, SourceModule> SOURCE_MODULES = new EnumMap<>(BizType.class);

    static {
        LONG_NAMES.put(BizType.BATCH_ENTRY, "入栏记录");
        LONG_NAMES.put(BizType.BATCH_TRANSFER, "转栏记录");
        LONG_NAMES.put(BizType.BATCH_DEATH, "死淘记录");
        LONG_NAMES.put(BizType.FARROWING, "产仔记录");
        LONG_NAMES.put(BizType.INCUBATION, "孵化记录");
        LONG_NAMES.put(BizType.BREEDING_EXIT, "种畜死亡淘汰");
        LONG_NAMES.put(BizType.FEED_RECORD, "投喂记录");
        LONG_NAMES.put(BizType.WEIGH_RECORD, "称重记录");
        LONG_NAMES.put(BizType.INSPECTION, "巡检记录");
        LONG_NAMES.put(BizType.IMMUNE_RECORD, "接种记录");
        LONG_NAMES.put(BizType.MEDICATION_RECORD, "用药记录");
        LONG_NAMES.put(BizType.DISINFECT_RECORD, "消毒记录");
        LONG_NAMES.put(BizType.INBOUND, "入库单");
        LONG_NAMES.put(BizType.STOCKTAKE, "盘点单");
        LONG_NAMES.put(BizType.LOSS, "报损单");
        LONG_NAMES.put(BizType.OUTBOUND, "出库单");
        LONG_NAMES.put(BizType.SALE_ORDER, "出栏单");

        SOURCE_MODULES.put(BizType.BATCH_ENTRY, SourceModule.PEN_STOCK);
        SOURCE_MODULES.put(BizType.BATCH_TRANSFER, SourceModule.PEN_STOCK);
        SOURCE_MODULES.put(BizType.BATCH_DEATH, SourceModule.PEN_STOCK);
        SOURCE_MODULES.put(BizType.FARROWING, SourceModule.BREEDING);
        SOURCE_MODULES.put(BizType.INCUBATION, SourceModule.BREEDING);
        SOURCE_MODULES.put(BizType.BREEDING_EXIT, SourceModule.BREEDING);
        SOURCE_MODULES.put(BizType.FEED_RECORD, SourceModule.FEEDING);
        SOURCE_MODULES.put(BizType.WEIGH_RECORD, SourceModule.FEEDING);
        SOURCE_MODULES.put(BizType.INSPECTION, SourceModule.INSPECTION);
        SOURCE_MODULES.put(BizType.IMMUNE_RECORD, SourceModule.HEALTH);
        SOURCE_MODULES.put(BizType.MEDICATION_RECORD, SourceModule.HEALTH);
        SOURCE_MODULES.put(BizType.DISINFECT_RECORD, SourceModule.HEALTH);
        SOURCE_MODULES.put(BizType.INBOUND, SourceModule.INVENTORY);
        SOURCE_MODULES.put(BizType.STOCKTAKE, SourceModule.INVENTORY);
        SOURCE_MODULES.put(BizType.LOSS, SourceModule.INVENTORY);
        SOURCE_MODULES.put(BizType.OUTBOUND, SourceModule.INVENTORY);
        SOURCE_MODULES.put(BizType.SALE_ORDER, SourceModule.SALE);
    }

    private ChangeBizTypes() {
    }

    /** UI 长名；未在 17 种之内的类型退回 05 号 6.3 的短名 */
    public static String textOf(BizType bizType) {
        if (bizType == null) {
            return null;
        }
        String name = LONG_NAMES.get(bizType);
        return name != null ? name : bizType.getText();
    }

    public static String textOf(String code) {
        return textOf(code == null ? null : BizType.valueOf(code));
    }

    /** 来源模块映射；未在 17 种之内时按记录类型猜测，兜底 PEN_STOCK */
    public static SourceModule sourceModuleOf(BizType bizType) {
        if (bizType == null) {
            return SourceModule.PEN_STOCK;
        }
        SourceModule module = SOURCE_MODULES.get(bizType);
        return module != null ? module : SourceModule.PEN_STOCK;
    }

    public static boolean applicationType(BizType bizType) {
        return bizType != null && APPLICATION_TYPES.contains(bizType);
    }

    /** op_action 中文名：修改 / 作废 / 删除 */
    public static String actionText(String opAction) {
        if (opAction == null) {
            return null;
        }
        return switch (opAction) {
            case "UPDATE" -> "修改";
            case "VOID" -> "作废";
            case "DELETE" -> "删除";
            case "RETURN_REFUND_REGISTER" -> "登记退款";
            case "RETURN_REFUND_CORRECT" -> "更正退款";
            default -> opAction;
        };
    }
}
