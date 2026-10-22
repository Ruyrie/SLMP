package com.zhimu.slmp.health.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthItemListFooterBinding;

/**
 * 分页列表底部：加载下一页时 24 dp 圆形进度；加载失败显示“加载失败，点按重试”；全部加载完不显示提示（UI 文档 2.4 通用）。
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
        return new Holder(HealthItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        HealthItemListFooterBinding b = holder.binding;
        boolean error = loadState instanceof LoadState.Error;
        b.healthFooterProgress.setVisibility(loadState instanceof LoadState.Loading ? View.VISIBLE : View.GONE);
        b.healthFooterText.setVisibility(error ? View.VISIBLE : View.GONE);
        b.healthFooterText.setText(R.string.health_list_load_failed);
        b.getRoot().setOnClickListener(error ? v -> retry.run() : null);
        b.getRoot().setClickable(error);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HealthItemListFooterBinding binding;

        Holder(@NonNull HealthItemListFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
