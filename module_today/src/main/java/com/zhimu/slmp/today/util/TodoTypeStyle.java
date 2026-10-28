package com.zhimu.slmp.today.util;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;

/**
 * 待办与消息的图标、图标底色调、尾部标签样式（UI 设计文档 2.2 节“待办类型与列表展示”“消息类型、样式与点击去向”）。
 * 显示文字由后端生成，这里只决定样式。
 */
public final class TodoTypeStyle {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_IGNORED = "IGNORED";

    public static final String CATEGORY_WORK = "WORK";
    public static final String CATEGORY_WARNING = "WARNING";
    public static final String CATEGORY_BUSINESS = "BUSINESS";

    private TodoTypeStyle() {
    }

    /** 14 种 TODO_TYPE 的图标 */
    @DrawableRes
    public static int iconOf(@Nullable String todoType) {
        if (todoType == null) {
            return R.drawable.ic_task_alt;
        }
        switch (todoType) {
            case "FEEDING":
                return R.drawable.ic_rice_bowl;
            case "INSPECTION":
                return R.drawable.ic_fact_check;
            case "IMMUNIZATION":
                return R.drawable.ic_vaccines;
            case "DISINFECTION":
                return R.drawable.ic_sanitizer;
            case "WEIGHING":
                return R.drawable.ic_scale;
            case "FARROWING":
                return R.drawable.ic_pregnant_woman;
            case "HATCHING":
                return R.drawable.ic_egg;
            case "FEED_LOW_STOCK":
            case "DRUG_LOW_STOCK":
            case "DRUG_NEAR_EXPIRY":
            case "DRUG_EXPIRED":
                return R.drawable.ic_inventory_2;
            case "WITHDRAWAL_END":
                return R.drawable.ic_medication;
            case "RECEIVABLE":
                return R.drawable.ic_payments;
            case "ABNORMAL_REPORT":
                return R.drawable.ic_report;
            default:
                return R.drawable.ic_task_alt;
        }
    }

    /** 待办来源 biz_type 的图标（消息只带 biz_type） */
    @DrawableRes
    public static int iconOfBizType(@Nullable String bizType) {
        if (bizType == null) {
            return R.drawable.ic_task_alt;
        }
        switch (bizType) {
            case "FEED_TASK":
                return R.drawable.ic_rice_bowl;
            case "INSPECTION":
                return R.drawable.ic_fact_check;
            case "IMMUNE_PLAN":
                return R.drawable.ic_vaccines;
            case "DISINFECT_TASK":
                return R.drawable.ic_sanitizer;
            case "WEIGH_CYCLE":
                return R.drawable.ic_scale;
            case "MATING":
                return R.drawable.ic_pregnant_woman;
            case "INCUBATION":
                return R.drawable.ic_egg;
            case "FEED":
            case "DRUG":
            case "DRUG_LOT":
                return R.drawable.ic_inventory_2;
            case "BATCH":
                return R.drawable.ic_medication;
            case "SALE_ORDER":
                return R.drawable.ic_payments;
            default:
                return R.drawable.ic_task_alt;
        }
    }

    /** 图标底：已逾期 error，已完成 / 已忽略中性，预警 warning，经营中性，作业 secondary */
    public static int toneOf(@NonNull TodoItemVO item) {
        String s = item.getDisplayStatus();
        if (STATUS_OVERDUE.equals(s)) {
            return ListRowView.TONE_ERROR;
        }
        if (STATUS_DONE.equals(s) || STATUS_IGNORED.equals(s)) {
            return ListRowView.TONE_NEUTRAL;
        }
        if (CATEGORY_WARNING.equals(item.getCategory())) {
            return ListRowView.TONE_WARNING;
        }
        if (CATEGORY_BUSINESS.equals(item.getCategory())) {
            return ListRowView.TONE_NEUTRAL;
        }
        return ListRowView.TONE_SECONDARY;
    }

    /** 尾部标签语义 */
    public static int tagTypeOf(@NonNull TodoItemVO item) {
        String s = item.getDisplayStatus();
        if (STATUS_OVERDUE.equals(s)) {
            return StatusTagView.ERROR;
        }
        if (STATUS_DONE.equals(s) || STATUS_IGNORED.equals(s)) {
            return StatusTagView.NEUTRAL;
        }
        if ("预警".equals(item.getTailText())) {
            return StatusTagView.WARNING;
        }
        return StatusTagView.PRIMARY;
    }

    /** 尾部标签图标：已逾期 schedule、已完成 check */
    @DrawableRes
    public static int tagIconOf(@NonNull TodoItemVO item) {
        String s = item.getDisplayStatus();
        if (STATUS_OVERDUE.equals(s)) {
            return R.drawable.ic_schedule;
        }
        if (STATUS_DONE.equals(s)) {
            return R.drawable.ic_check;
        }
        return 0;
    }

    public static boolean isClosed(@NonNull TodoItemVO item) {
        String s = item.getDisplayStatus();
        return STATUS_DONE.equals(s) || STATUS_IGNORED.equals(s);
    }

    /** 列表副文字：后端 sub_text，没有时取完成或忽略说明 */
    @Nullable
    public static String subTextOf(@NonNull TodoItemVO item) {
        if (item.getSubText() != null) {
            return item.getSubText();
        }
        if (STATUS_DONE.equals(item.getDisplayStatus())) {
            return item.getCompletedText();
        }
        if (STATUS_IGNORED.equals(item.getDisplayStatus())) {
            return item.getIgnoreText() != null ? item.getIgnoreText() : item.getIgnoreReason();
        }
        return null;
    }

    // ===================== 消息 =====================

    @DrawableRes
    public static int messageIconOf(@NonNull MessageBriefVO m) {
        String category = m.getCategory();
        String type = m.getMsgType();
        if (MessageBriefVO.CATEGORY_TODO.equals(category)) {
            return iconOfBizType(m.getBizType());
        }
        if (MessageBriefVO.CATEGORY_AI.equals(category)) {
            return R.drawable.ic_auto_awesome;
        }
        if (MessageBriefVO.CATEGORY_LOT.equals(category)) {
            return R.drawable.ic_block;
        }
        if (MessageBriefVO.CATEGORY_NOTICE.equals(category)) {
            return R.drawable.ic_campaign;
        }
        if (type == null) {
            return R.drawable.ic_description;
        }
        switch (type) {
            case MessageBriefVO.TYPE_FARM_INVITE:
            case "INVITE_ACCEPTED":
                return R.drawable.ic_group_add;
            case MessageBriefVO.TYPE_OWNER_TRANSFER:
            case "TRANSFER_RESULT":
                return R.drawable.ic_swap_horiz;
            case "MEMBER_REMOVED":
            case "FARM_DELETED":
            case "FARM_DISABLED":
            case MessageBriefVO.TYPE_FARM_FROZEN:
                return R.drawable.ic_domain_disabled;
            case "MEMBER_ROLE_CHANGED":
                return R.drawable.ic_badge;
            case "CHANGE_REQ_RESULT":
                return R.drawable.ic_edit_note;
            case "FARM_AUDIT_RESULT":
            case "FARM_CHANGE_RESULT":
            case "FARM_ENABLED":
            case "FARM_UNFROZEN":
                return R.drawable.ic_domain;
            case "RECOVERY_RESULT":
                return R.drawable.ic_manage_accounts;
            case "REPORT_READY":
                return R.drawable.ic_bar_chart;
            default:
                return R.drawable.ic_description;
        }
    }

    /** 消息图标底；actionStatus 为邀请当前状态 */
    public static int messageToneOf(@NonNull MessageBriefVO m, @Nullable String actionStatus) {
        String category = m.getCategory();
        if (m.isRevoked()) {
            return ListRowView.TONE_NEUTRAL;
        }
        if (MessageBriefVO.CATEGORY_TODO.equals(category)) {
            String state = m.getIconState();
            if (STATUS_OVERDUE.equals(state)) {
                return ListRowView.TONE_ERROR;
            }
            if (STATUS_DONE.equals(state) || STATUS_IGNORED.equals(state) || "HANDLED".equals(state)) {
                return ListRowView.TONE_NEUTRAL;
            }
            return ListRowView.TONE_SECONDARY;
        }
        if (MessageBriefVO.CATEGORY_AI.equals(category)) {
            return ListRowView.TONE_TERTIARY;
        }
        if (MessageBriefVO.CATEGORY_LOT.equals(category)) {
            return ListRowView.TONE_ERROR;
        }
        if (MessageBriefVO.CATEGORY_NOTICE.equals(category)) {
            return ListRowView.TONE_SECONDARY;
        }
        if (m.getAction() != null) {
            return MessageRow.STATUS_PENDING.equals(actionStatus) ? ListRowView.TONE_SECONDARY : ListRowView.TONE_NEUTRAL;
        }
        return ListRowView.TONE_NEUTRAL;
    }

    /** 消息尾部标签语义（tag_code） */
    public static int messageTagTypeOf(@Nullable String tagCode) {
        if (tagCode == null) {
            return StatusTagView.NEUTRAL;
        }
        switch (tagCode) {
            case "ATTENTION":
                return StatusTagView.WARNING;
            case "URGENT":
            case "RECALLED":
            case "REJECTED":
                return StatusTagView.ERROR;
            case "IMPORTANT":
                return StatusTagView.PRIMARY;
            default:
                return StatusTagView.NEUTRAL;
        }
    }

    @DrawableRes
    public static int messageTagIconOf(@Nullable String tagCode) {
        if ("ATTENTION".equals(tagCode) || "URGENT".equals(tagCode)) {
            return R.drawable.ic_priority_high;
        }
        if ("PASSED".equals(tagCode) || "DONE".equals(tagCode)) {
            return R.drawable.ic_check;
        }
        return 0;
    }
}
