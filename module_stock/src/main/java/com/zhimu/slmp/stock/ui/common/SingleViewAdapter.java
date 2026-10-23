package com.zhimu.slmp.stock.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 把一个已创建好的视图（页面头部、分组标题、空状态行）作为 ConcatAdapter 的一项；页面直接更新该视图。
 * visible 为 false 时项数为 0。
 */
public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final View view;
    private boolean visible;

    public SingleViewAdapter(@NonNull View view) {
        this(view, true);
    }

    public SingleViewAdapter(@NonNull View view, boolean visible) {
        this.view = view;
        this.visible = visible;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        if (visible) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
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
