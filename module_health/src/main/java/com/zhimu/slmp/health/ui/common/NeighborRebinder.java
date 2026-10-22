package com.zhimu.slmp.health.ui.common;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 分页加载插入或移除项后重绑相邻项，使分组列表的首、中、末圆角正确。
 */
public final class NeighborRebinder extends RecyclerView.AdapterDataObserver {

    public static final Object PAYLOAD_POSITION = "position";

    private final RecyclerView.Adapter<?> adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public NeighborRebinder(@NonNull RecyclerView.Adapter<?> adapter) {
        this.adapter = adapter;
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
        rebind(positionStart - 1, positionStart + itemCount);
    }

    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
        rebind(positionStart - 1, positionStart);
    }

    private void rebind(int before, int after) {
        handler.post(() -> {
            int count = adapter.getItemCount();
            if (before >= 0 && before < count) {
                adapter.notifyItemChanged(before, PAYLOAD_POSITION);
            }
            if (after >= 0 && after < count) {
                adapter.notifyItemChanged(after, PAYLOAD_POSITION);
            }
        });
    }
}
