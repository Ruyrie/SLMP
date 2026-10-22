package com.zhimu.slmp.inspect.ui.form;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登记 / 补录巡检的表单规则（模块07 文档 2.4.2，UI 文档 2.5 节文案），纯函数，便于单元测试：
 * 每项必选、检查结果异常与手动标记、异常原因与说明长度、代登记执行人、补录日期；异常横幅文字。
 */
public final class InspectFormRules {

    public static final int MAX_REASON = 100;
    public static final int MAX_REMARK = 200;

    public static final String F_MANUAL_REASON = "manual_reason";
    public static final String F_REMARK = "remark";
    public static final String F_EXECUTOR = "executor_id";
    public static final String F_DATE = "inspect_date";
    public static final String F_MEDIA = "media_file_ids";

    public static final String MSG_REASON_REQUIRED = "请填写异常原因";
    public static final String MSG_REASON_TOO_LONG = "异常原因不能超过 100 字";
    public static final String MSG_REMARK_TOO_LONG = "说明不能超过 200 字";
    public static final String MSG_EXECUTOR_REQUIRED = "请选择实际执行人";
    public static final String MSG_DATE_REQUIRED = "请选择补录日期";
    public static final String MSG_LOCKED_CHECK = "检查结果有异常项，不能取消异常标记";
    public static final String MSG_LOCKED_REPORTED = "已转上报，不能取消异常标记";

    private static final String REPORT_SUFFIX = "点“转上报”会先保存本次巡检，再带入栏舍、批次和照片。";
    private static final String REPORT_SUFFIX_MANUAL = "点“转上报”会先保存本次巡检，再带入栏舍、批次、异常原因和照片。";
    private static final String BACKFILL_SUFFIX = "补录记录不能转上报，如需上报请从快捷登记进入异常上报。";

    private InspectFormRules() {
    }

    /** 项目的错误键：item_&lt;item_id&gt; */
    @NonNull
    public static String itemKey(long itemId) {
        return "item_" + itemId;
    }

    /** 校验输入 */
    public static final class Input {
        @NonNull
        public List<InspectionFormVO.TemplateItem> items = new ArrayList<>();
        @NonNull
        public Map<Long, Long> selections = new LinkedHashMap<>();
        /** 用户手动打开了“标记为异常” */
        public boolean manualOn;
        @Nullable
        public String manualReason;
        @Nullable
        public String remark;
        /** 补录：执行人必选 */
        public boolean requireExecutor;
        @Nullable
        public Long executorId;
        /** 补录：日期必选 */
        public boolean requireDate;
        @Nullable
        public String inspectDate;
    }

    /** 检查结果是否异常：任一项选了 is_abnormal 的选项 */
    public static boolean checkAbnormal(@NonNull List<InspectionFormVO.TemplateItem> items,
                                        @NonNull Map<Long, Long> selections) {
        for (InspectionFormVO.TemplateItem item : items) {
            Long optionId = selections.get(item.getItemId());
            if (optionId == null) {
                continue;
            }
            InspectionFormVO.TemplateOption o = item.findOption(optionId);
            if (o != null && o.isAbnormal()) {
                return true;
            }
        }
        return false;
    }

    /** 异常项摘要：“粪便为“水样””“采食为“减少”、粪便为“水样””；没有异常项返回 null */
    @Nullable
    public static String abnormalSummary(@NonNull List<InspectionFormVO.TemplateItem> items,
                                         @NonNull Map<Long, Long> selections) {
        List<String> parts = new ArrayList<>();
        for (InspectionFormVO.TemplateItem item : items) {
            Long optionId = selections.get(item.getItemId());
            InspectionFormVO.TemplateOption o = optionId == null ? null : item.findOption(optionId);
            if (o != null && o.isAbnormal()) {
                parts.add(item.getItemName() + "为“" + o.getOptionName() + "”");
            }
        }
        return parts.isEmpty() ? null : String.join("、", parts);
    }

    /**
     * 校验；返回字段 → 文案（按页面从上到下的顺序，第一个用于滚动定位）。
     */
    @NonNull
    public static Map<String, String> validate(@NonNull Input in) {
        Map<String, String> e = new LinkedHashMap<>();
        if (in.requireDate && (in.inspectDate == null || in.inspectDate.trim().isEmpty())) {
            e.put(F_DATE, MSG_DATE_REQUIRED);
        }
        if (in.requireExecutor && in.executorId == null) {
            e.put(F_EXECUTOR, MSG_EXECUTOR_REQUIRED);
        }
        for (InspectionFormVO.TemplateItem item : in.items) {
            Long optionId = in.selections.get(item.getItemId());
            if (optionId == null || item.findOption(optionId) == null) {
                e.put(itemKey(item.getItemId()), "请选择" + item.getItemName());
            }
        }
        boolean check = checkAbnormal(in.items, in.selections);
        String reason = in.manualReason == null ? "" : in.manualReason.trim();
        if (in.manualOn) {
            if (reason.isEmpty() && !check) {
                e.put(F_MANUAL_REASON, MSG_REASON_REQUIRED);
            } else if (reason.codePointCount(0, reason.length()) > MAX_REASON) {
                e.put(F_MANUAL_REASON, MSG_REASON_TOO_LONG);
            }
        }
        String remark = in.remark == null ? "" : in.remark.trim();
        if (remark.codePointCount(0, remark.length()) > MAX_REMARK) {
            e.put(F_REMARK, MSG_REMARK_TOO_LONG);
        }
        return e;
    }

    /**
     * 异常横幅（null 为不显示）。
     *
     * @param backfill       补录（不能转上报）
     * @param othersMarkedBy 员工修改被他人手动标记的记录时的标记人（如“刘芳”），否则 null
     */
    @Nullable
    public static String banner(@Nullable String checkSummary, boolean manualOn, boolean backfill,
                                @Nullable String othersMarkedBy, @Nullable String othersReason) {
        if (checkSummary != null) {
            return checkSummary + "，已自动标为异常。" + (backfill ? BACKFILL_SUFFIX : REPORT_SUFFIX);
        }
        if (othersMarkedBy != null) {
            return othersMarkedBy + "已手动标为异常：" + (othersReason == null ? "" : othersReason) + "。"
                    + REPORT_SUFFIX_MANUAL;
        }
        if (manualOn) {
            return "已手动标为异常。" + (backfill ? BACKFILL_SUFFIX : REPORT_SUFFIX_MANUAL);
        }
        return null;
    }
}
