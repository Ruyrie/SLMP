package com.zhimu.slmp.inspect.ui.today;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.model.vo.RecentIssueVO;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;

/**
 * I01 列表文字（UI 文档 2.5 节 I01）。
 */
final class TodayTexts {

    private TodayTexts() {
    }

    @NonNull
    static String location(@Nullable String status) {
        if (InspectionListItemVO.LOC_ON_SITE.equals(status)) {
            return "现场";
        }
        if (InspectionListItemVO.LOC_OFF_SITE.equals(status)) {
            return "非现场";
        }
        return "无位置";
    }

    /** 养殖户版副文字 */
    @NonNull
    static String ownerSubtitle(@NonNull InspectionListItemVO item, @Nullable String deadline) {
        String who = (item.getRegistrantName() == null ? "" : item.getRegistrantName() + " ")
                + InspectFormat.time(item.getRegisteredAt());
        String reported = item.isReported() ? "已上报" : "未上报";
        if (item.isManualAbnormal()) {
            return "手动标记 · " + nz(item.getManualReason()) + "\n" + who + InspectFormat.DOT + reported;
        }
        if (item.isAbnormal()) {
            return nz(item.getAbnormalSummary()) + InspectFormat.DOT + who + InspectFormat.DOT + reported;
        }
        if (item.isPending()) {
            if (item.isDeadlinePassed()) {
                return "已过截止时间 " + nz(deadline) + "，登记后标为超时";
            }
            if (item.getResponsibleNames().isEmpty()) {
                return "无负责员工 · 提醒所有者和场长";
            }
            return InspectFormat.join(item.getBatchNos(), "") + InspectFormat.DOT + "负责人 "
                    + TextUtils.join("、", item.getResponsibleNames());
        }
        if (item.isDone()) {
            return who + InspectFormat.DOT + location(item.getLocationStatus());
        }
        return "漏巡";
    }

    /** 员工版副文字（批次号一行 + 状态一行） */
    @NonNull
    static String staffSubtitle(@NonNull InspectionListItemVO item, @Nullable String deadline) {
        String batch = InspectFormat.join(item.getBatchNos(), "");
        if (item.isManualAbnormal()) {
            return batch + "\n· 手动标记 · " + nz(item.getManualReason()) + (item.isReported() ? " · 已上报" : "");
        }
        if (item.isAbnormal()) {
            return batch + "\n· " + nz(item.getAbnormalSummary()) + InspectFormat.DOT
                    + InspectFormat.time(item.getRegisteredAt()) + " 登记" + (item.isReported() ? " · 已上报" : "");
        }
        if (item.isPending()) {
            return item.isDeadlinePassed() ? "已过截止时间 " + nz(deadline) + "，登记后标为超时"
                    : nz(deadline) + " 前完成";
        }
        if (item.isDone()) {
            return InspectFormat.time(item.getRegisteredAt()) + " 登记" + InspectFormat.DOT
                    + location(item.getLocationStatus());
        }
        return "漏巡";
    }

    /** 前 3 天分组的标题与副文字 */
    @NonNull
    static String issueTitle(@NonNull RecentIssueVO issue) {
        return issue.getPenName() + InspectFormat.DOT + InspectFormat.monthDay(issue.getInspectDate());
    }

    @NonNull
    static String issueSubtitle(@NonNull RecentIssueVO issue) {
        switch (issue.getIssueType()) {
            case RecentIssueVO.TYPE_OVERDUE:
                return nz(issue.getRegistrantName()) + " " + InspectFormat.time(issue.getRegisteredAt())
                        + " 登记，晚于截止时间";
            case RecentIssueVO.TYPE_BACKFILL:
                return InspectFormat.monthDay(issue.getRegisteredAt()) + " " + nz(issue.getRegistrantName()) + "补录";
            default:
                return issue.isCanBackfill()
                        ? "漏巡 · 可补录至 " + InspectFormat.monthDay(issue.getBackfillUntil())
                        : "漏巡 · 已超过补录期限";
        }
    }

    @NonNull
    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}
