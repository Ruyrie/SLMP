package com.zhimu.slmp.feed.ui.register;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.feed.model.vo.FeedRecordDetailVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskFormVO;
import com.zhimu.slmp.feed.util.FeedFormat;

/**
 * G02 任务信息卡（只读）：主文字“3 号育肥舍 · 午餐 11:30”，次文字“ZM2610-031 · 杜长大育肥猪 · 96 日龄 · 存栏 200 头”；
 * 非当天的记录在次文字前加日期；逾期任务卡片尾部显示“已逾期”。
 */
public final class FeedTaskCard {

    @NonNull
    public final String title;
    @NonNull
    public final String subtitle;
    @Nullable
    public final String tag;

    public FeedTaskCard(@NonNull String title, @NonNull String subtitle, @Nullable String tag) {
        this.title = title;
        this.subtitle = subtitle;
        this.tag = tag;
    }

    @NonNull
    static FeedTaskCard of(@NonNull FeedTaskFormVO f) {
        return new FeedTaskCard(title(f.getPenName(), f.getMealName(), f.getFeedTime()),
                batchLine(f.getBatchNo(), f.getBreedName(), f.getDayAge(), f.getBatchStock()),
                f.isOverdue() && f.isCanRegister() ? "已逾期" : null);
    }

    @NonNull
    static FeedTaskCard of(@NonNull FeedRecordDetailVO r) {
        String batch = batchLine(r.getBatchNo(), r.getBreedName(), r.getDayAge(), r.getBatchStock());
        String date = r.getFeedDate();
        boolean today = date == null || date.equals(DateFormatter.todayString());
        String sub = today ? batch : FeedFormat.date(date) + FeedFormat.DOT + batch;
        String tag = FeedRecordDetailVO.STATUS_VOID.equals(r.getStatus()) ? "已作废" : null;
        return new FeedTaskCard(title(r.getPenName(), r.getMealName(), r.getFeedTime()), sub, tag);
    }

    @NonNull
    private static String title(@Nullable String pen, @Nullable String meal, @Nullable String time) {
        String mealTime = (FeedFormat.orEmpty(meal) + " " + FeedFormat.orEmpty(time)).trim();
        return FeedFormat.join(pen, mealTime);
    }

    @NonNull
    private static String batchLine(@Nullable String batchNo, @Nullable String breed, @Nullable Integer dayAge,
                                    @Nullable Integer stock) {
        return FeedFormat.join(batchNo, breed, dayAge == null ? null : dayAge + " 日龄",
                stock == null ? null : "存栏 " + FeedFormat.head(stock));
    }
}
