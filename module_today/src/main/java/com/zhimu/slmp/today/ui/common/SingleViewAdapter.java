package com.zhimu.slmp.today.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 只有一项、视图预先创建的适配器（列表头部：问候区、日历头部）。头部视图持有 DataBinding，随页面生命周期更新。
 */
public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    @NonNull
    private final View view;
    private boolean visible = true;

    public SingleViewAdapter(@NonNull View view) {
        this.view = view;
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    public void setVisible(boolean show) {
        if (visible == show) {
            return;
        }
        visible = show;
        if (show) {
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
