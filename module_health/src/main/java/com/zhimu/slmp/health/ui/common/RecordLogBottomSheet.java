package com.zhimu.slmp.health.ui.common;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.util.HealthFormat;

import java.util.List;

/**
 * “修改记录”底部面板（API-U14，按时间倒序：时间、操作人、内容、原因）。观察所在页 ViewModel（实现 RecordLogHost）的 logs。
 */
public class RecordLogBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_HOST = "health_log_host";

    private LinearLayout list;

    @NonNull
    public static RecordLogBottomSheet newInstance(@NonNull Class<? extends ViewModel> hostClass) {
        RecordLogBottomSheet f = new RecordLogBottomSheet();
        Bundle args = baseArgs(null, null, null, null);
        args.putString(ARG_HOST, hostClass.getName());
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.health_log_title);
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        NestedScrollView scroll = new NestedScrollView(requireContext());
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_page_margin);
        list.setPadding(pad, 0, pad, pad);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.7f);
        scroll.post(() -> {
            if (scroll.getHeight() > maxHeight) {
                scroll.getLayoutParams().height = maxHeight;
                scroll.requestLayout();
            }
        });
        return scroll;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String host = requireArguments().getString(ARG_HOST);
        try {
            Class<? extends ViewModel> cls = (Class<? extends ViewModel>) Class.forName(host);
            ViewModel vm = new ViewModelProvider(requireActivity(), HealthInjector.factory()).get(cls);
            if (vm instanceof RecordLogHost) {
                ((RecordLogHost) vm).getLogs().observe(getViewLifecycleOwner(), this::render);
            }
        } catch (ClassNotFoundException | ClassCastException e) {
            dismissAllowingStateLoss();
        }
    }

    private void render(@Nullable List<RecordLogVO> logs) {
        list.removeAllViews();
        if (logs == null) {
            CircularProgressIndicator progress = new CircularProgressIndicator(requireContext());
            progress.setIndeterminate(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = lp.bottomMargin = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_6);
            list.addView(progress, lp);
            return;
        }
        if (logs.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
            empty.setTextColor(MaterialColors.getColor(empty, com.google.android.material.R.attr.colorOnSurfaceVariant));
            empty.setText(R.string.health_log_empty);
            empty.setGravity(Gravity.CENTER);
            int p = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_8);
            empty.setPadding(0, p, 0, p);
            list.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        int gap = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_list_gap);
        for (int i = 0; i < logs.size(); i++) {
            RecordLogVO log = logs.get(i);
            ListRowView row = new ListRowView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == 0 ? 0 : gap;
            row.setGroupPosition(GroupPosition.of(i, logs.size()));
            row.setTitle(titleOf(log));
            row.setSubtitleMaxLines(3);
            row.setSubtitle(subtitleOf(log));
            list.addView(row, lp);
        }
    }

    /** 主文字：操作 + 变化（“调整日期：10月26日 → 11月2日”） */
    @NonNull
    static String titleOf(@NonNull RecordLogVO log) {
        StringBuilder sb = new StringBuilder();
        for (RecordLogVO.Change c : log.getChanges()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(HealthFormat.nonNull(c.getFieldName())).append("：").append(HealthFormat.nonNull(c.getBefore()))
                    .append(HealthFormat.ARROW).append(HealthFormat.nonNull(c.getAfter()));
        }
        String action = log.getOpActionText();
        if (sb.length() == 0) {
            return HealthFormat.nonNull(action);
        }
        return TextUtils.isEmpty(action) ? sb.toString() : action + " · " + sb;
    }

    /** 辅助文字：“10月16日 14:20 · 刘芳”，有原因时第二行“原因：……” */
    @NonNull
    static String subtitleOf(@NonNull RecordLogVO log) {
        StringBuilder sb = new StringBuilder(DateFormatter.formatDateTime(log.getCreatedAt()));
        if (log.getOperator() != null && log.getOperator().getName() != null) {
            sb.append(HealthFormat.DOT).append(log.getOperator().getName());
        }
        if (!TextUtils.isEmpty(log.getChangeRequestNo())) {
            sb.append(HealthFormat.DOT).append("申请 ").append(log.getChangeRequestNo());
        }
        if (!TextUtils.isEmpty(log.getReason())) {
            sb.append("\n原因：").append(log.getReason());
        }
        return sb.toString();
    }
}
