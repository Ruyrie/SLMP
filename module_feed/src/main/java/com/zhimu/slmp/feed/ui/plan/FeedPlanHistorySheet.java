package com.zhimu.slmp.feed.ui.plan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedSheetListBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.List;

/**
 * G03“修改记录”面板（API-U14，biz_type FEED_PLAN）：按时间倒序，主文字为修改内容（“育肥前期每头日投喂量 2.5 千克 → 2.4 千克”），
 * 次文字“修改人 · 日期 时间”；为空时居中“还没有修改记录”。面板最高占屏幕 2/3，内容多时面板内滚动。
 */
public class FeedPlanHistorySheet extends BaseBottomSheetDialogFragment {

    private FeedSheetListBinding binding;

    @NonNull
    public static FeedPlanHistorySheet newInstance(@NonNull CharSequence title) {
        FeedPlanHistorySheet s = new FeedPlanHistorySheet();
        s.setArguments(baseArgs(null, title, null, null));
        return s;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = FeedSheetListBinding.inflate(inflater, container, false);
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 2f / 3f);
        binding.feedSheetScroll.setMaxHeightPx(max);
        binding.feedSheetEmpty.setText(R.string.feed_plan_history_empty);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FeedPlanViewModel vm = new ViewModelProvider(requireActivity(), FeedInjector.factory())
                .get(FeedPlanViewModel.class);
        vm.getHistoryItems().observe(getViewLifecycleOwner(), this::render);
        if (savedInstanceState == null) {
            vm.loadHistory();
        }
    }

    private void render(@Nullable List<RecordLogVO> list) {
        binding.feedSheetProgress.setVisibility(list == null ? View.VISIBLE : View.GONE);
        binding.feedSheetItems.removeAllViews();
        if (list == null) {
            binding.feedSheetEmpty.setVisibility(View.GONE);
            return;
        }
        binding.feedSheetEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RecordLogVO log : list) {
            View row = inflater.inflate(R.layout.feed_item_history, binding.feedSheetItems, false);
            ((TextView) row.findViewById(R.id.feed_history_title)).setText(titleOf(log));
            String who = log.getOperator() == null ? null : log.getOperator().getName();
            ((TextView) row.findViewById(R.id.feed_history_subtitle)).setText(
                    FeedFormat.join(who, DateFormatter.formatDateTime(log.getCreatedAt())));
            binding.feedSheetItems.addView(row);
        }
    }

    @NonNull
    static String titleOf(@NonNull RecordLogVO log) {
        StringBuilder sb = new StringBuilder();
        for (RecordLogVO.Change c : log.getChanges()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(FeedFormat.orEmpty(c.getFieldName())).append(' ').append(FeedFormat.orEmpty(c.getBefore()))
                    .append(" → ").append(FeedFormat.orEmpty(c.getAfter()));
        }
        if (sb.length() == 0) {
            return FeedFormat.orEmpty(log.getOpActionText());
        }
        return sb.toString();
    }
}
