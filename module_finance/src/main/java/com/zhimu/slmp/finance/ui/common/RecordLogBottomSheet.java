package com.zhimu.slmp.finance.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListLayout;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.network.GsonFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 修改记录只读底部面板（E04 手工记录、E07 出栏单，数据来自 API-U14）：每条“10月19日 14:20 · 刘芳”+ 变化“金额 −500 元 → −600 元”；
 * 没有修改过时“这条记录还没有修改过”。
 */
public class RecordLogBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_LOGS = "logs";

    public static void show(@NonNull FragmentManager fm, @NonNull List<RecordLogVO> logs, @NonNull String title) {
        RecordLogBottomSheet s = new RecordLogBottomSheet();
        Bundle args = baseArgs(null, title, null, null);
        args.putString(ARG_LOGS, GsonFactory.get().toJson(logs));
        s.setArguments(args);
        s.showSafely(fm, "finance_record_log");
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        List<RecordLogVO> logs = parse();
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_sheet_padding_horizontal);
        if (logs.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText(R.string.finance_log_empty);
            tv.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
            tv.setTextColor(FinanceViews.color(container, com.google.android.material.R.attr.colorOnSurfaceVariant));
            tv.setPadding(pad, pad / 2, pad, pad * 2);
            return tv;
        }
        GroupedListLayout list = new GroupedListLayout(requireContext());
        list.setPadding(pad, 0, pad, pad);
        for (RecordLogVO l : logs) {
            ListRowView row = new ListRowView(requireContext());
            String who = l.getOperator() == null ? "" : " · " + l.getOperator().getName();
            row.setTitle(DateFormatter.formatDateTime(l.getCreatedAt()) + who);
            row.setSubtitle(changeText(l));
            row.setSubtitleMaxLines(6);
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_history, ListRowView.TONE_NEUTRAL);
            list.addView(row);
        }
        list.updatePositions();
        return list;
    }

    /** “金额 −500 元 → −600 元”；作废等没有字段变化的写操作名称 */
    @NonNull
    static String changeText(@NonNull RecordLogVO l) {
        List<String> parts = new ArrayList<>();
        if (l.getChanges() != null) {
            for (RecordLogVO.Change c : l.getChanges()) {
                parts.add(c.getFieldName() + " " + (c.getBefore() == null ? "—" : c.getBefore()) + " → "
                        + (c.getAfter() == null ? "—" : c.getAfter()));
            }
        }
        String head = parts.isEmpty() ? (l.getOpActionText() == null ? "" : l.getOpActionText()) : String.join("\n",
                parts);
        if (l.getReason() != null && !l.getReason().isEmpty()) {
            head += "\n原因：" + l.getReason();
        }
        if (l.getChangeRequestNo() != null) {
            head += "\n修改申请 " + l.getChangeRequestNo();
        }
        return head;
    }

    @NonNull
    private List<RecordLogVO> parse() {
        String json = getArguments() == null ? null : getArguments().getString(ARG_LOGS);
        if (json == null) {
            return Collections.emptyList();
        }
        List<RecordLogVO> list = GsonFactory.get().fromJson(json, new TypeToken<List<RecordLogVO>>() {
        }.getType());
        return list == null ? Collections.emptyList() : list;
    }
}
