package com.zhimu.slmp.feed.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.feed.databinding.FeedItemListFooterBinding;

/**
 * 分页列表底部（UI 2.4 节）：加载下一页时 24 dp 圆形进度；没有更多时不显示任何提示；加载失败显示“加载失败，点按重试”。
 */
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
        return new Holder(FeedItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        FeedItemListFooterBinding b = holder.binding;
        boolean error = loadState instanceof LoadState.Error;
        b.feedFooterProgress.setVisibility(error ? View.GONE : View.VISIBLE);
        b.feedFooterText.setVisibility(error ? View.VISIBLE : View.GONE);
        b.getRoot().setOnClickListener(error ? v -> retry.run() : null);
        b.getRoot().setClickable(error);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FeedItemListFooterBinding binding;

        Holder(@NonNull FeedItemListFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
