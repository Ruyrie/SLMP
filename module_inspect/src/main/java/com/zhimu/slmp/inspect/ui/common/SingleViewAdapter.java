package com.zhimu.slmp.inspect.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 只放一个固定视图的适配器（列表头部、空状态），可显示或隐藏。
 */
public final class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final View view;
    private boolean visible;

    public SingleViewAdapter(@NonNull View view, boolean visible) {
        this.view = view;
        this.visible = visible;
    }

    public void setVisible(boolean show) {
        if (visible != show) {
            visible = show;
            if (show) {
                notifyItemInserted(0);
            } else {
                notifyItemRemoved(0);
            }
        }
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    }
}
