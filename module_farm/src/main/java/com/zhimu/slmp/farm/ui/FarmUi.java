package com.zhimu.slmp.farm.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.R;

import java.util.List;
import java.util.Map;

/**
 * 本组件页面共用的小工具：字段错误、滚动定位、无权查看标题、日期文字、列表行取值样式。
 */
public final class FarmUi {

    private FarmUi() {
    }

    /**
     * 二级页顶部栏：标题写入 include 的 DataBinding 变量（否则绑定执行时会把标题覆盖为空），返回图标执行返回。
     */
    public static void setupTopBar(@NonNull com.zhimu.slmp.base.databinding.BaseIncludeTopAppBarBinding bar,
                                   @NonNull CharSequence title) {
        bar.setTitle(title);
        com.zhimu.slmp.base.widget.TopAppBarHelper.setupBack(bar.baseToolbar, title);
    }

    /** 把字段错误写到输入框（没有错误时恢复辅助文字） */
    public static void setError(@NonNull TextInputLayout layout, @Nullable Map<String, String> errors,
                                @NonNull String... fields) {
        String msg = null;
        if (errors != null) {
            for (String f : fields) {
                if (errors.containsKey(f)) {
                    msg = errors.get(f);
                    break;
                }
            }
        }
        layout.setError(msg);
        layout.setErrorEnabled(msg != null);
    }

    /** 滚动到字段并让其获得焦点 */
    public static void scrollTo(@NonNull NestedScrollView scroll, @Nullable View target) {
        if (target == null) {
            return;
        }
        int top = 0;
        View v = target;
        while (v != null && v != scroll) {
            top += v.getTop();
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        int offset = Math.round(16 * scroll.getResources().getDisplayMetrics().density);
        scroll.smoothScrollTo(0, Math.max(0, top - offset));
        target.requestFocus();
    }

    /** “你没有东山猪场成员管理的查看权限”（UI 文档 2.5 节，F05–F10 员工经旧链接进入） */
    @NonNull
    public static String noPermissionTitle(@NonNull Context context, @Nullable String farmName) {
        return context.getString(R.string.farm_no_permission_title,
                TextUtils.isEmpty(farmName) ? context.getString(R.string.farm_this_farm) : farmName);
    }

    /** 无权查看的整页状态（StateLayout 标题用 noPermissionTitle） */
    @NonNull
    public static UiState noPermissionState() {
        return UiState.noPermission(null, com.zhimu.slmp.base.error.ErrorCodes.ROLE_FORBIDDEN);
    }

    /** 页面进入时按 FarmContext 的权限判断（只对当前养殖场判断；其他养殖场以服务端 40303 为准） */
    public static boolean deniedByContext(@NonNull FarmContext farmContext, long farmId, boolean allowed) {
        FarmContextVO ctx = farmContext.current();
        return ctx != null && ctx.getFarmId() == farmId && !allowed;
    }

    /** 配置 StateLayout 的无权查看标题 */
    public static void setupNoPermission(@NonNull StateLayout state, @Nullable String farmName) {
        state.setNoPermissionTitle(noPermissionTitle(state.getContext(), farmName));
    }

    /** “10月17日”（跨年带年份） */
    @NonNull
    public static String monthDay(@Nullable String date) {
        return DateFormatter.formatDate(date, false);
    }

    /** “10月18日 16:20” */
    @NonNull
    public static String monthDayTime(@Nullable String datetime) {
        if (datetime == null) {
            return "";
        }
        return monthDay(datetime) + " " + DateFormatter.formatTime(datetime);
    }

    /** ListRowView 右侧取值改为 body-m 14 sp（UI 文档 2.3 节“列表次要文字、右侧取值”） */
    public static void trailingBodyMedium(@NonNull ListRowView row) {
        TextView tv = row.findViewById(com.zhimu.slmp.base.R.id.base_row_trailing_text);
        if (tv != null) {
            tv.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
            tv.setTextColor(com.google.android.material.color.MaterialColors.getColor(tv,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setMaxWidth(Math.round(220 * row.getResources().getDisplayMetrics().density));
        }
    }

    /** “3 号育肥舍、4 号育肥舍” */
    @NonNull
    public static String joinNames(@NonNull List<String> names) {
        return TextUtils.join("、", names);
    }
}
