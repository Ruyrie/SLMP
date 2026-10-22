package com.zhimu.slmp.breed.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedItemListFooterBinding;

/**
 * 分页列表底部：加载下一页时 4 dp 线性进度条；全部加载完显示“已显示全部”；加载失败显示“加载失败，点按重试”。
 */
public class FooterLoadStateAdapter extends LoadStateAdapter<FooterLoadStateAdapter.Holder> {

    private final Runnable retry;
    private boolean hasItems;

    public FooterLoadStateAdapter(@NonNull Runnable retry) {
        this.retry = retry;
    }

    /** 列表为空时不显示“已显示全部”（由页面显示空状态） */
    public void setHasItems(boolean hasItems) {
        if (this.hasItems != hasItems) {
            this.hasItems = hasItems;
            setLoadState(getLoadState());
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
        return new Holder(BreedItemListFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
        BreedItemListFooterBinding b = holder.binding;
        b.breedFooterProgress.setVisibility(loadState instanceof LoadState.Loading ? View.VISIBLE : View.GONE);
        boolean error = loadState instanceof LoadState.Error;
        boolean end = loadState instanceof LoadState.NotLoading;
        b.breedFooterText.setVisibility(error || end ? View.VISIBLE : View.GONE);
        b.breedFooterText.setText(error ? R.string.breed_list_load_failed : R.string.breed_list_all_shown);
        b.getRoot().setOnClickListener(error ? v -> retry.run() : null);
        b.getRoot().setClickable(error);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final BreedItemListFooterBinding binding;

        Holder(@NonNull BreedItemListFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
