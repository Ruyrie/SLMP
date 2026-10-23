package com.zhimu.slmp.stock.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.stock.databinding.StockItemListFooterBinding;

/** 分页列表底部：加载下一页时 24 dp 圆形进度；失败时“加载失败，点按重试”；没有更多时不显示。 */
public class FooterLoadStateAdapter extends LoadStateAdapter<FooterLoadStateAdapter.Holder> {

    private final Runnable retry;

    public FooterLoadStateAdapter(@NonNull Runnable retry) {
        this.retry = retry;
    }

    @Override
    public boolean displayLoadStateAsItem(@NonNull LoadState loadState) {
        return loadState instanceof LoadState.Loading || loadState instanceof LoadState.Error;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        return new Holder(StockItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent,
                false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        StockItemListFooterBinding b = holder.binding;
        boolean error = loadState instanceof LoadState.Error;
        b.stockFooterProgress.setVisibility(error ? View.GONE : View.VISIBLE);
        b.stockFooterText.setVisibility(error ? View.VISIBLE : View.GONE);
        b.getRoot().setOnClickListener(error ? v -> retry.run() : null);
        b.getRoot().setClickable(error);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final StockItemListFooterBinding binding;

        Holder(@NonNull StockItemListFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
