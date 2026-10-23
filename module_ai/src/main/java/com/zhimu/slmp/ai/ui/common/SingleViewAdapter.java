package com.zhimu.slmp.ai.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 只放一个固定视图的适配器（列表顶部的说明横幅、页头），配合 ConcatAdapter 使用；可隐藏（项数为 0）。
 */
public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final View view;
    private boolean visible = true;

    public SingleViewAdapter(@NonNull View view) {
        this.view = view;
    }

    public void setVisible(boolean v) {
        if (visible == v) {
            return;
        }
        visible = v;
        if (v) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    @NonNull
    public View getView() {
        return view;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    @Override
    public int getItemViewType(int position) {
        return System.identityHashCode(view);
    }
}
