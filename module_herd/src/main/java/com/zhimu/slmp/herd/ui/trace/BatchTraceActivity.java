package com.zhimu.slmp.herd.ui.trace;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.KeyValueRowView;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityBatchTraceBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.BatchTraceVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Native read-only public trace page. No internal archive, finance data or edit routes are rendered. */
public final class BatchTraceActivity extends BaseActivity {
    private HerdActivityBatchTraceBinding binding;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HerdActivityBatchTraceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        BatchTraceViewModel vm = obtainViewModel(HerdInjector.factory(), BatchTraceViewModel.class);
        observeBase(vm);
        binding.herdToolbar.setNavigationOnClickListener(v -> finish());
        binding.herdTraceState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.herdTraceState::render);
        vm.getTrace().observe(this, this::render);
    }

    private void render(@Nullable BatchTraceVO trace) {
        if (trace == null) return;
        binding.herdTraceBatchNo.setText(value(trace.getBatchNo()));
        binding.herdTraceFarm.setText(value(trace.getFarmName()));
        List<String> breed = new ArrayList<>();
        add(breed, trace.getCategoryName());
        add(breed, trace.getBreedName());
        binding.herdTraceBreed.setText(String.join(" · ", breed));
        binding.herdTraceInfo.removeAllViews();
        info("批次状态", "ACTIVE".equals(trace.getStatus()) ? "在养"
                : "ENDED".equals(trace.getStatus()) ? "已结束" : "—");
        info("入栏日期", value(trace.getEntryDate()));
        info("来源", "PURCHASED".equals(trace.getSource()) ? "外购"
                : "SELF_BRED".equals(trace.getSource()) ? "自繁" : "—");
        if (trace.getEntryAgeDays() != null) info("入栏日龄", trace.getEntryAgeDays() + " 天");
        if (trace.getEntryAvgWeight() != null) info("入栏均重", number(trace.getEntryAvgWeight()) + " 千克");
        if (trace.getEndDate() != null) info("结束日期", trace.getEndDate());

        binding.herdTraceEvents.removeAllViews();
        binding.herdTraceEmpty.setVisibility(trace.getEvents().isEmpty() ? View.VISIBLE : View.GONE);
        for (BatchTraceVO.Event event : trace.getEvents()) {
            ListRowView row = new ListRowView(this);
            row.setTitle(value(event.getDate()) + " · " + eventType(event.getType()));
            List<String> details = new ArrayList<>();
            add(details, event.getName());
            if (event.getQuantity() != null) details.add("数量 " + event.getQuantity());
            if (event.getAvgWeight() != null) details.add("平均体重 " + number(event.getAvgWeight()) + " 千克");
            if (event.getWithdrawalEndDate() != null) details.add("休药结束日期 " + event.getWithdrawalEndDate());
            row.setSubtitle(String.join("\n", details));
            row.setSubtitleMaxLines(Integer.MAX_VALUE);
            row.setClickable(false);
            binding.herdTraceEvents.addView(row);
        }
        binding.herdTraceUpdated.setText(getString(R.string.herd_trace_updated,
                value(trace.getUpdatedAt()).replace('T', ' ')));
    }

    private void info(String key, String text) {
        KeyValueRowView row = new KeyValueRowView(this);
        row.setKey(key);
        row.setValue(text);
        binding.herdTraceInfo.addView(row);
    }

    private static String eventType(@Nullable String type) {
        if ("IMMUNE".equals(type)) return "免疫";
        if ("MEDICATION".equals(type)) return "用药";
        if ("WEIGH".equals(type)) return "称重";
        if ("SALE".equals(type)) return "出栏";
        return "养殖记录";
    }

    private static String value(@Nullable String text) {
        return text == null || text.isEmpty() ? "—" : text;
    }

    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static void add(List<String> values, @Nullable String value) {
        if (value != null && !value.isEmpty()) values.add(value);
    }
}
