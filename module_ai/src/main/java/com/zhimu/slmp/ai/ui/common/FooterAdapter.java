package com.zhimu.slmp.ai.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.ai.databinding.AiItemListFooterBinding;

/**
 * 分页列表底部：加载下一页时 24 dp 圆形进度，失败时“重新加载”。
 */
public class FooterAdapter extends LoadStateAdapter<FooterAdapter.Holder> {

    private final Runnable retry;

    public FooterAdapter(@NonNull Runnable retry) {
        this.retry = retry;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        return new Holder(AiItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        boolean error = loadState instanceof LoadState.Error;
        holder.b.aiFooterProgress.setVisibility(error ? View.GONE : View.VISIBLE);
        holder.b.aiFooterRetry.setVisibility(error ? View.VISIBLE : View.GONE);
        holder.b.aiFooterRetry.setOnClickListener(v -> retry.run());
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final AiItemListFooterBinding b;

        Holder(@NonNull AiItemListFooterBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
