package com.zhimu.slmp.herd.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.herd.databinding.HerdItemListFooterBinding;

/**
 * 分页列表底部：加载下一页时 24 dp 圆形进度；全部加载完显示“没有更多了”；加载失败显示“加载失败，点按重试”。
 */
public class FooterLoadStateAdapter extends LoadStateAdapter<FooterLoadStateAdapter.Holder> {

    private final Runnable retry;
    private boolean hasItems;

    public FooterLoadStateAdapter(@NonNull Runnable retry) {
        this.retry = retry;
    }

    /** 列表为空时不显示“没有更多了”（由页面显示空状态） */
    public void setHasItems(boolean hasItems) {
        if (this.hasItems != hasItems) {
            this.hasItems = hasItems;
            LoadState state = getLoadState();
            setLoadState(state);
        }
    }

    @Override
    public boolean displayLoadStateAsItem(@NonNull LoadState loadState) {
        if (loadState instanceof LoadState.NotLoading) {
            return loadState.getEndOfPaginationReached() && hasItems;
        }
        return true;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        return new Holder(HerdItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        HerdItemListFooterBinding b = holder.binding;
        b.herdFooterProgress.setVisibility(loadState instanceof LoadState.Loading ? View.VISIBLE : View.GONE);
        boolean error = loadState instanceof LoadState.Error;
        boolean end = loadState instanceof LoadState.NotLoading;
        b.herdFooterText.setVisibility(error || end ? View.VISIBLE : View.GONE);
        b.herdFooterText.setText(error ? com.zhimu.slmp.herd.R.string.herd_list_load_failed
                : com.zhimu.slmp.herd.R.string.herd_list_no_more);
        b.getRoot().setOnClickListener(error ? v -> retry.run() : null);
        b.getRoot().setClickable(error);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HerdItemListFooterBinding binding;

        Holder(@NonNull HerdItemListFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
