package com.zhimu.slmp.inspect.ui.history;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.ui.common.RecordLogHost;

import java.util.List;

/**
 * 修改记录底部面板（I04“查看修改记录”、I06 更多菜单）：按时间倒序列出修改人、修改时间和修改前后的内容；
 * 没有记录时“还没有修改记录”。数据来自宿主 Activity 的 ViewModel（实现 {@link RecordLogHost}）。
 */
public class RecordLogBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_HOST = "inspect_record_log_host";

    private LinearLayout list;
    private TextView empty;

    @NonNull
    public static RecordLogBottomSheet newInstance(@NonNull Class<? extends ViewModel> hostClass) {
        RecordLogBottomSheet f = new RecordLogBottomSheet();
        Bundle args = baseArgs(null, "修改记录", null, null);
        args.putString(ARG_HOST, hostClass.getName());
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.inspect_sheet_record_logs, container, false);
        list = root.findViewById(R.id.inspect_logs_list);
        empty = root.findViewById(R.id.inspect_logs_empty);
        return root;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String host = requireArguments().getString(ARG_HOST);
        try {
            Class<? extends ViewModel> cls = (Class<? extends ViewModel>) Class.forName(host);
            ViewModel vm = new ViewModelProvider(requireActivity(), InspectInjector.factory()).get(cls);
            if (vm instanceof RecordLogHost) {
                ((RecordLogHost) vm).getRecordLogs().observe(getViewLifecycleOwner(), this::render);
            }
        } catch (ClassNotFoundException | RuntimeException e) {
            dismissAllowingStateLoss();
        }
    }

    private void render(@Nullable List<RecordLogVO> logs) {
        list.removeAllViews();
        boolean none = logs == null || logs.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) {
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        for (RecordLogVO log : logs) {
            View row = inflater.inflate(R.layout.inspect_item_record_log, list, false);
            String who = log.getOperator() == null || log.getOperator().getName() == null ? ""
                    : log.getOperator().getName();
            String action = log.getOpActionText() == null ? "修改" : log.getOpActionText();
            ((TextView) row.findViewById(R.id.inspect_log_title)).setText(
                    who + " · " + DateFormatter.formatDateTime(log.getCreatedAt()) + " " + action);
            StringBuilder sb = new StringBuilder();
            for (RecordLogVO.Change c : log.getChanges()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(c.getFieldName()).append("：").append(TextUtils.isEmpty(c.getBefore()) ? "无" : c.getBefore())
                        .append(" → ").append(TextUtils.isEmpty(c.getAfter()) ? "无" : c.getAfter());
            }
            if (log.getChangeRequestNo() != null) {
                sb.append(sb.length() > 0 ? "\n" : "").append("修改申请 ").append(log.getChangeRequestNo());
            }
            ((TextView) row.findViewById(R.id.inspect_log_changes)).setText(sb.toString());
            list.addView(row);
        }
    }
}
