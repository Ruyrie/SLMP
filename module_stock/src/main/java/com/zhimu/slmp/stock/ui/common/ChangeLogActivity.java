package com.zhimu.slmp.stock.ui.common;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockActivityListBinding;
import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.util.StockTexts;

import java.util.ArrayList;
import java.util.List;

/**
 * 修改记录页（UI 文档 M02“修改记录”，原型未绘制）：整页列表，按时间倒序；主文字为修改内容“预警天数 5 天 → 7 天”，
 * 一次保存改了多个字段时每个字段一行，副文字“10月10日 09:15 · 王建国”。本组件内页面，显式 Intent 打开。
 */
public class ChangeLogActivity extends BaseActivity {

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String bizType, long bizId) {
        return new Intent(context, ChangeLogActivity.class)
                .putExtra(RouterPath.EXTRA_BIZ_TYPE, bizType)
                .putExtra(RouterPath.EXTRA_BIZ_ID, bizId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StockActivityListBinding binding = StockActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ChangeLogViewModel vm = obtainViewModel(StockInjector.factory(), ChangeLogViewModel.class);
        observeBase(vm);
        com.zhimu.slmp.base.widget.TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar,
                getString(R.string.stock_change_log_title));
        binding.stockRefresh.setEnabled(false);
        binding.stockState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_history,
                getString(R.string.stock_change_log_empty), null, null, 0,
                com.zhimu.slmp.base.widget.StateLayout.EMPTY_ACTION_TONAL, null);
        binding.stockList.setLayoutManager(new LinearLayoutManager(this));
        binding.stockList.addItemDecoration(new GroupedListDecoration(this));
        LogAdapter adapter = new LogAdapter();
        binding.stockList.setAdapter(adapter);
        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.stockState::render);
        vm.getLogs().observe(this, adapter::submit);
    }

    /** 主文字：字段“前 → 后”逐行；没有字段时写操作名称 */
    @NonNull
    static String changesText(@NonNull RecordLogVO log) {
        List<String> lines = new ArrayList<>();
        if (log.getChanges() != null) {
            for (RecordLogVO.Change c : log.getChanges()) {
                lines.add(StockTexts.nz(c.getFieldName()) + " " + StockTexts.nz(c.getBefore()) + " → "
                        + StockTexts.nz(c.getAfter()));
            }
        }
        return lines.isEmpty() ? StockTexts.nz(log.getOpActionText()) : String.join("\n", lines);
    }

    private static final class LogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<RecordLogVO> items = new ArrayList<>();

        void submit(@Nullable List<RecordLogVO> list) {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            StockItemRowBinding b = StockUi.inflateRow(parent);
            return new RecyclerView.ViewHolder(b.getRoot()) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            StockItemRowBinding b = StockItemRowBinding.bind(holder.itemView);
            RecordLogVO log = items.get(position);
            String who = log.getOperator() == null ? null : log.getOperator().getName();
            String sub = StockTexts.join(StockTexts.DOT, DateFormatter.formatDateTime(log.getCreatedAt()), who);
            String extra = log.getChangeRequestNo() != null ? "修改申请 " + log.getChangeRequestNo() : log.getReason();
            StockUi.bindRow(b, 0, ListRowView.TONE_NEUTRAL, changesText(log),
                    extra == null ? sub : sub + "\n" + extra, null, null, StatusTagView.NEUTRAL, 0);
            b.stockRowTitle.setSingleLine(false);
            b.stockRowTitle.setMaxLines(8);
            b.stockRowCard.setClickable(false);
            b.stockRowLeading.setVisibility(View.GONE);
            StockUi.position(b.stockRowCard, position, items.size());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
