package com.zhimu.server.biz.inspect.support;

import com.zhimu.server.biz.inspect.query.InspectTemplateHeadRow;
import com.zhimu.server.biz.inspect.query.InspectTemplateItemRow;
import com.zhimu.server.biz.inspect.request.InspectionSubmitRequest;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 已发布巡检模板与提交结果的校验/快照。 */
public final class InspectTemplateBundle {

    public final long templateId;
    public final int version;
    public final List<Item> items;

    private InspectTemplateBundle(long templateId, int version, List<Item> items) {
        this.templateId = templateId;
        this.version = version;
        this.items = List.copyOf(items);
    }

    public static InspectTemplateBundle build(InspectTemplateHeadRow head, List<InspectTemplateItemRow> rows) {
        if (head == null || rows == null || rows.isEmpty()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个品类还没有可用的巡检模板");
        }
        Map<Long, Item> byId = new LinkedHashMap<>();
        for (InspectTemplateItemRow row : rows) {
            Item item = byId.computeIfAbsent(row.getItemId(),
                    id -> new Item(id, row.getItemName(), row.getItemSort()));
            item.options.add(new Option(row.getOptionId(), row.getOptionName(),
                    Boolean.TRUE.equals(row.getOptionAbnormal()), row.getOptionSort()));
        }
        return new InspectTemplateBundle(head.getTemplateId(),
                head.getVersionNo() == null ? 0 : head.getVersionNo(), new ArrayList<>(byId.values()));
    }

    /**
     * 校验提交结果：必须覆盖全部启用项目，每项一个且选项属于该项目。
     *
     * @return 按模板顺序的结果快照
     */
    public List<ResultSnapshot> validate(List<InspectionSubmitRequest.ItemResultRequest> submitted) {
        Map<Long, Long> selected = new LinkedHashMap<>();
        if (submitted != null) {
            for (InspectionSubmitRequest.ItemResultRequest result : submitted) {
                if (result == null || result.getItemId() == null) {
                    continue;
                }
                if (selected.putIfAbsent(result.getItemId(), result.getOptionId()) != null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "巡检项目重复提交");
                }
            }
        }
        List<ResultSnapshot> snapshots = new ArrayList<>();
        for (Item item : items) {
            Long optionId = selected.get(item.itemId);
            if (optionId == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择" + item.itemName);
            }
            Option option = item.findOption(optionId);
            if (option == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择" + item.itemName + "的有效选项");
            }
            snapshots.add(new ResultSnapshot(item.itemId, item.itemName, option.optionId,
                    option.optionName, option.abnormal, item.sortNo));
        }
        Set<Long> templateIds = new LinkedHashSet<>();
        for (Item item : items) {
            templateIds.add(item.itemId);
        }
        for (Long id : selected.keySet()) {
            if (!templateIds.contains(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "巡检项目不属于当前模板");
            }
        }
        return snapshots;
    }

    public static List<String> abnormalSummaries(List<ResultSnapshot> snapshots) {
        List<String> result = new ArrayList<>();
        for (ResultSnapshot snapshot : snapshots) {
            if (snapshot.optionAbnormal()) {
                result.add(snapshot.itemName() + " " + snapshot.optionName());
            }
        }
        return result;
    }

    public static final class Item {
        private final long itemId;
        private final String itemName;
        private final int sortNo;
        private final List<Option> options = new ArrayList<>();

        private Item(long itemId, String itemName, Integer sortNo) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.sortNo = sortNo == null ? 0 : sortNo;
        }

        public long itemId() {
            return itemId;
        }

        public String itemName() {
            return itemName;
        }

        public int sortNo() {
            return sortNo;
        }

        public List<Option> options() {
            return options;
        }

        public Option findOption(long optionId) {
            for (Option option : options) {
                if (option.optionId == optionId) {
                    return option;
                }
            }
            return null;
        }
    }

    public record Option(long optionId, String optionName, boolean abnormal, int sortNo) {
    }

    public record ResultSnapshot(long itemId, String itemName, long optionId, String optionName,
                                 boolean optionAbnormal, int sortNo) {
    }
}
