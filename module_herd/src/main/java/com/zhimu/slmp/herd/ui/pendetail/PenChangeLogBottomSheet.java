package com.zhimu.slmp.herd.ui.pendetail;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

import java.util.List;

/**
 * L04 修改记录底部面板（复用页面 PenDetailViewModel，API-U14 biz_type = PEN）：主文字为变更内容
 * “容量：200 头 → 220 头”，次文字“10月12日 14:20 · 刘芳”，按时间倒序；没有记录时“还没有修改记录”。
 */
public class PenChangeLogBottomSheet extends BaseBottomSheetDialogFragment {

    private LinearLayout list;

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.herd_pen_change_log);
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
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setMinimumHeight(0);
        scroll.post(() -> {
            if (scroll.getHeight() > maxHeight) {
                scroll.getLayoutParams().height = maxHeight;
                scroll.requestLayout();
            }
        });
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PenDetailViewModel vm = new ViewModelProvider(requireActivity(), HerdInjector.factory())
                .get(PenDetailViewModel.class);
        vm.getChangeLogs().observe(getViewLifecycleOwner(), this::render);
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
            empty.setText(R.string.herd_pen_change_log_empty);
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
            row.setTitle(changesText(log));
            row.setSubtitleMaxLines(2);
            String who = log.getOperator() == null ? "" : HerdFormat.DOT + log.getOperator().getName();
            row.setSubtitle(DateFormatter.formatDateTime(log.getCreatedAt()) + who);
            list.addView(row, lp);
        }
    }

    @NonNull
    static String changesText(@NonNull RecordLogVO log) {
        StringBuilder sb = new StringBuilder();
        if (log.getChanges() != null) {
            for (RecordLogVO.Change c : log.getChanges()) {
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append(c.getFieldName()).append("：").append(c.getBefore() == null ? "" : c.getBefore())
                        .append(" → ").append(c.getAfter() == null ? "" : c.getAfter());
            }
        }
        if (sb.length() == 0 && log.getOpActionText() != null) {
            sb.append(log.getOpActionText());
        }
        return sb.toString();
    }
}
