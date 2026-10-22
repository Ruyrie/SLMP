package com.zhimu.slmp.feed.ui.growth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedSheetListBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.request.GrowthCurveQuery;
import com.zhimu.slmp.feed.model.vo.GrowthCompareOptionVO;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * G06“选择对比批次”面板（API-G26）：多选，每行“批次号 / 栏舍 · 在养或已结束 · 最近平均 x 千克（x 日龄）”；
 * 满 3 个时未勾选的行不可选，说明改为“已选 3 个，最多选 3 个”；没有可对比的批次时居中提示；底部“确定”。
 */
public class GrowthCompareSheet extends BaseBottomSheetDialogFragment {

    private static final String STATE_SELECTED = "selected";

    private FeedSheetListBinding binding;
    private GrowthCurveViewModel vm;
    private final Set<Long> selected = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();

    private static final class Row {
        final long id;
        final View view;
        final MaterialCheckBox check;

        Row(long id, @NonNull View view, @NonNull MaterialCheckBox check) {
            this.id = id;
            this.view = view;
            this.check = check;
        }
    }

    @NonNull
    public static GrowthCompareSheet newInstance(@NonNull CharSequence title) {
        GrowthCompareSheet s = new GrowthCompareSheet();
        s.setArguments(baseArgs(null, title, null, null));
        return s;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity(), FeedInjector.factory()).get(GrowthCurveViewModel.class);
        binding = FeedSheetListBinding.inflate(inflater, container, false);
        binding.feedSheetScroll.setMaxHeightPx((int) (getResources().getDisplayMetrics().heightPixels * 0.55f));
        String breed = vm.breedName();
        binding.feedSheetEmpty.setText(breed.isEmpty() ? getString(R.string.feed_growth_compare_empty)
                : "当前养殖场还没有其他" + breed + "批次");
        selected.clear();
        long[] saved = savedInstanceState == null ? null : savedInstanceState.getLongArray(STATE_SELECTED);
        if (saved != null) {
            for (long id : saved) {
                selected.add(id);
            }
        } else if (vm.getCompareIds().getValue() != null) {
            selected.addAll(vm.getCompareIds().getValue());
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.getCompareOptions().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] arr = new long[selected.size()];
        int i = 0;
        for (Long id : selected) {
            arr[i++] = id;
        }
        outState.putLongArray(STATE_SELECTED, arr);
    }

    private void render(@Nullable List<GrowthCompareOptionVO> list) {
        binding.feedSheetProgress.setVisibility(list == null ? View.VISIBLE : View.GONE);
        binding.feedSheetItems.removeAllViews();
        rows.clear();
        if (list == null) {
            binding.feedSheetEmpty.setVisibility(View.GONE);
            return;
        }
        binding.feedSheetEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GrowthCompareOptionVO o : list) {
            View row = inflater.inflate(R.layout.feed_item_compare_option, binding.feedSheetItems, false);
            MaterialCheckBox check = row.findViewById(R.id.feed_compare_check);
            ((TextView) row.findViewById(R.id.feed_compare_title)).setText(FeedFormat.orEmpty(o.getBatchNo()));
            String latest = o.getLatestAvgWeight() == null ? null : "最近平均 " + FeedFormat.kg1(o.getLatestAvgWeight())
                    + (o.getLatestDayAge() == null ? "" : "（" + o.getLatestDayAge() + " 日龄）");
            ((TextView) row.findViewById(R.id.feed_compare_subtitle)).setText(
                    FeedFormat.join(o.getPenNames(), o.isActive() ? "在养" : "已结束", latest));
            Row r = new Row(o.getBatchId(), row, check);
            check.setChecked(selected.contains(o.getBatchId()));
            row.setOnClickListener(v -> toggle(r));
            check.setOnClickListener(v -> toggle(r));
            rows.add(r);
            binding.feedSheetItems.addView(row);
        }
        refreshLimit();
    }

    private void toggle(@NonNull Row r) {
        if (selected.contains(r.id)) {
            selected.remove(r.id);
        } else if (selected.size() < GrowthCurveQuery.MAX_COMPARE) {
            selected.add(r.id);
        }
        r.check.setChecked(selected.contains(r.id));
        refreshLimit();
    }

    /** 已达上限时未勾选的行不可选 */
    private void refreshLimit() {
        boolean full = selected.size() >= GrowthCurveQuery.MAX_COMPARE;
        for (Row r : rows) {
            boolean enabled = !full || selected.contains(r.id);
            r.view.setEnabled(enabled);
            r.check.setEnabled(enabled);
            r.view.setAlpha(enabled ? 1f : 0.38f);
        }
        String breed = vm.breedName();
        setDescription(full ? "已选 " + GrowthCurveQuery.MAX_COMPARE + " 个，最多选 " + GrowthCurveQuery.MAX_COMPARE + " 个"
                : "只列出" + breed + "的批次，最多选 " + GrowthCurveQuery.MAX_COMPARE + " 个");
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.feed_action_confirm);
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.confirmCompare(new ArrayList<>(selected));
        dismiss();
    }
}
