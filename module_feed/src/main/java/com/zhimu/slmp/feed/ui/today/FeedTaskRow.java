package com.zhimu.slmp.feed.ui.today;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.model.vo.FeedTaskItemVO;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.Objects;

/**
 * G01 列表行（PagingData.map 与 insertSeparators 生成）：餐次标题“早餐 · 07:00”或任务行（次文字、标签已按状态拼好）。
 * <ul>
 *     <li>已逾期：“保育料 · 计划 80 千克 · 张伟” / “超过投喂时间 2 小时 30 分”，error 图标底，标签“已逾期”</li>
 *     <li>已投喂：“实投 158 千克 · 李明 07:12”（逾期后登记末尾加“逾期完成”），删除线，标签“已投喂”或“投喂异常”</li>
 *     <li>待投喂：“育肥全价料”（库存不足时后接标签）/ “计划 160 千克 · 李明”，标签“待投喂”</li>
 * </ul>
 * 栏舍没有负责员工时执行人位置为“无负责员工”。
 */
public final class FeedTaskRow {

    public static final String NO_ASSIGNEE = "无负责员工";

    public final boolean header;
    @NonNull
    public final String key;
    /** 餐次标题（header 为 true） */
    @Nullable
    public final String headerText;
    @Nullable
    public final FeedTaskItemVO item;
    @NonNull
    public final String title;
    @NonNull
    public final String line1;
    public final boolean lowStockTag;
    @Nullable
    public final String line2;
    @NonNull
    public final String tagText;
    public final int tagType;
    public final int tagIcon;
    public final int tone;
    public final boolean strike;

    private FeedTaskRow(boolean header, @NonNull String key, @Nullable String headerText, @Nullable FeedTaskItemVO item,
                        @NonNull String title, @NonNull String line1, boolean lowStockTag, @Nullable String line2,
                        @NonNull String tagText, int tagType, int tagIcon, int tone, boolean strike) {
        this.header = header;
        this.key = key;
        this.headerText = headerText;
        this.item = item;
        this.title = title;
        this.line1 = line1;
        this.lowStockTag = lowStockTag;
        this.line2 = line2;
        this.tagText = tagText;
        this.tagType = tagType;
        this.tagIcon = tagIcon;
        this.tone = tone;
        this.strike = strike;
    }

    /** 餐次分组标题 */
    @NonNull
    public static FeedTaskRow header(@NonNull FeedTaskItemVO first) {
        String text = FeedFormat.orEmpty(first.getMealName()) + FeedFormat.DOT + FeedFormat.orEmpty(first.getFeedTime());
        return new FeedTaskRow(true, "h" + first.getMealNo() + "_" + first.getFeedTime(), text, null, "", "", false,
                null, "", StatusTagView.NEUTRAL, 0, ListRowView.TONE_SECONDARY, false);
    }

    /** 两条任务是否属于不同餐次（需要插入标题） */
    public static boolean newMeal(@Nullable FeedTaskItemVO before, @NonNull FeedTaskItemVO after) {
        return before == null || before.getMealNo() != after.getMealNo()
                || !Objects.equals(before.getFeedTime(), after.getFeedTime());
    }

    /** 任务行 */
    @NonNull
    public static FeedTaskRow of(@NonNull FeedTaskItemVO t) {
        String person = t.getAssigneeName() == null || t.getAssigneeName().isEmpty() ? NO_ASSIGNEE : t.getAssigneeName();
        String plan = "计划 " + FeedFormat.kg(t.getPlanQty());
        String key = "t" + t.getTaskId();
        String pen = FeedFormat.orEmpty(t.getPenName());
        if (t.isDone()) {
            String line1;
            if (t.getActualQty() != null) {
                String who = FeedFormat.orEmpty(t.getExecutorName());
                String time = DateFormatter.formatTime(t.getRegisteredAt());
                line1 = "实投 " + FeedFormat.kg(t.getActualQty()) + FeedFormat.DOT + (who + " " + time).trim();
            } else {
                line1 = FeedFormat.join(plan, t.getStatusText());
            }
            if (t.isBackfill()) {
                line1 = line1 + FeedFormat.DOT + "逾期完成";
            }
            boolean abnormal = t.isAbnormal();
            return new FeedTaskRow(false, key, null, t, pen, line1, false, null,
                    abnormal ? "投喂异常" : "已投喂", abnormal ? StatusTagView.WARNING : StatusTagView.NEUTRAL,
                    abnormal ? 0 : com.zhimu.slmp.base.R.drawable.ic_check, ListRowView.TONE_NEUTRAL, true);
        }
        if (t.isOverdue()) {
            String line1 = FeedFormat.join(t.getFeedName(), plan, person);
            return new FeedTaskRow(false, key, null, t, pen, line1, false, FeedFormat.overdue(t.getOverdueMinutes()),
                    "已逾期", StatusTagView.ERROR, com.zhimu.slmp.base.R.drawable.ic_schedule, ListRowView.TONE_ERROR,
                    false);
        }
        if (FeedTaskItemVO.STATUS_PENDING.equals(t.getStatus())) {
            return new FeedTaskRow(false, key, null, t, pen, FeedFormat.orEmpty(t.getFeedName()), t.isLowStock(),
                    FeedFormat.join(plan, person), "待投喂", StatusTagView.PRIMARY, 0, ListRowView.TONE_SECONDARY,
                    false);
        }
        // 未执行等：只读展示
        String text = t.getStatusText() == null ? "未执行" : t.getStatusText();
        return new FeedTaskRow(false, key, null, t, pen, FeedFormat.join(t.getFeedName(), plan, person), false, null,
                text, StatusTagView.NEUTRAL, 0, ListRowView.TONE_NEUTRAL, false);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FeedTaskRow)) {
            return false;
        }
        FeedTaskRow r = (FeedTaskRow) o;
        return header == r.header && key.equals(r.key) && Objects.equals(headerText, r.headerText)
                && title.equals(r.title) && line1.equals(r.line1) && lowStockTag == r.lowStockTag
                && Objects.equals(line2, r.line2) && tagText.equals(r.tagText) && tagType == r.tagType
                && tone == r.tone && strike == r.strike;
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, key, line1, line2, tagText);
    }
}
