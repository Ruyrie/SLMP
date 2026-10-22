package com.zhimu.slmp.herd.ui.stock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.herd.databinding.HerdItemStockHeaderBinding;
import com.zhimu.slmp.herd.model.vo.StockSummaryVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

/**
 * L03 列表头（随列表滚动）：总存栏汇总卡、列表标题与排序按钮、列表区空状态。
 */
class StockHeaderAdapter extends RecyclerView.Adapter<StockHeaderAdapter.Holder> {

    /** 点击回调 */
    interface Listener {
        void onSortClick(@NonNull View anchor);

        void onEmptyActionClick();
    }

    private final Listener listener;
    @Nullable
    private StockSummaryVO summary;
    @Nullable
    private String title;
    @Nullable
    private String sortText;
    @Nullable
    private String emptyTitle;
    @Nullable
    private String emptyMessage;
    private boolean emptyAction;

    StockHeaderAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void setSummary(@Nullable StockSummaryVO summary) {
        this.summary = summary;
        notifyItemChanged(0);
    }

    void setTitle(@Nullable String title, @Nullable String sortText) {
        this.title = title;
        this.sortText = sortText;
        notifyItemChanged(0);
    }

    /** 列表区空状态；title 为 null 时隐藏 */
    void setEmpty(@Nullable String title, @Nullable String message, boolean showAction) {
        this.emptyTitle = title;
        this.emptyMessage = message;
        this.emptyAction = showAction;
        notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(HerdItemStockHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HerdItemStockHeaderBinding b = holder.binding;
        StockSummaryVO s = summary;
        if (s != null) {
            b.herdSummaryCard.setValue(HerdFormat.number(s.getTotalStock()), HerdFormat.unit(s.getUnitText()));
            b.herdSummaryCard.clearSubItems();
            b.herdSummaryCard.addSubItem("在养批次", s.getActiveBatchCount() + " 个");
            b.herdSummaryCard.addSubItem("死亡率", HerdFormat.percent(s.getMortalityRate()));
            b.herdSummaryCard.addSubItem("栏舍占用", HerdFormat.percent(s.getOccupancyRate()));
        }
        b.herdListTitle.setText(title);
        b.herdSort.setVisibility(sortText == null ? View.GONE : View.VISIBLE);
        b.herdSort.setText(sortText);
        ClickThrottle.setOnClickListener(b.herdSort, listener::onSortClick);
        b.herdEmpty.setVisibility(emptyTitle == null ? View.GONE : View.VISIBLE);
        b.herdEmptyTitle.setText(emptyTitle);
        b.herdEmptyMessage.setText(emptyMessage);
        b.herdEmptyMessage.setVisibility(emptyMessage == null ? View.GONE : View.VISIBLE);
        b.herdEmptyAction.setVisibility(emptyAction ? View.VISIBLE : View.GONE);
        ClickThrottle.setOnClickListener(b.herdEmptyAction, v -> listener.onEmptyActionClick());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HerdItemStockHeaderBinding binding;

        Holder(@NonNull HerdItemStockHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
