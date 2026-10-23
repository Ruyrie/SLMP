package com.zhimu.slmp.finance.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.finance.databinding.FinanceItemListFooterBinding;

/**
 * 分页列表底部（UI 2.4 节）：加载下一页时 24 dp 圆形进度；全部加载完“没有更多记录了”；加载失败“加载失败，点按重试”。
 */
public class FooterLoadStateAdapter extends LoadStateAdapter<FooterLoadStateAdapter.Holder> {

    private final Runnable retry;
    private final boolean showEnd;

    public FooterLoadStateAdapter(@NonNull Runnable retry, boolean showEnd) {
        this.retry = retry;
        this.showEnd = showEnd;
    }

    @Override
    public boolean displayLoadStateAsItem(@NonNull LoadState loadState) {
        return loadState instanceof LoadState.Loading || loadState instanceof LoadState.Error
                || (showEnd && loadState instanceof LoadState.NotLoading && loadState.getEndOfPaginationReached());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        return new Holder(FinanceItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        FinanceItemListFooterBinding b = holder.binding;
        boolean error = loadState instanceof LoadState.Error;
        boolean end = loadState instanceof LoadState.NotLoading;
        b.financeFooterProgress.setVisibility(error || end ? View.GONE : View.VISIBLE);
        b.financeFooterText.setVisibility(error || end ? View.VISIBLE : View.GONE);
        b.financeFooterText.setText(error ? com.zhimu.slmp.finance.R.string.finance_list_load_failed
                : com.zhimu.slmp.finance.R.string.finance_list_end);
        b.getRoot().setOnClickListener(error ? v -> retry.run() : null);
        b.getRoot().setClickable(error);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FinanceItemListFooterBinding binding;

        Holder(@NonNull FinanceItemListFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
