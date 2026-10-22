package com.zhimu.slmp.herd.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 只包含一个已创建视图的适配器（列表头部信息卡随列表滚动，数据由页面直接绑定到该视图）。
 */
public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final View view;

    public SingleViewAdapter(@NonNull View view) {
        this.view = view;
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    public int getItemCount() {
        return 1;
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
}
