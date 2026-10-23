package com.zhimu.slmp.stock.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.databinding.StockItemSectionBinding;

import java.util.function.Consumer;

/**
 * 分页分组列表（ListRow）：标题行用 stock_item_section，数据行用 stock_item_row；
 * 组内首末大圆角按相邻行是否为标题判断，追加下一页后重绑上一页最后一项（圆角随之更新）。
 */
public abstract class RowPagingAdapter<T> extends PagingDataAdapter<ListRow<T>, RecyclerView.ViewHolder> {

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_ITEM = 0;

    @Nullable
    private final Consumer<T> onClick;

    protected RowPagingAdapter(@Nullable Consumer<T> onClick) {
        super(new DiffUtil.ItemCallback<ListRow<T>>() {
            @Override
            public boolean areItemsTheSame(@NonNull ListRow<T> a, @NonNull ListRow<T> b) {
                return a.key.equals(b.key);
            }

            @Override
            public boolean areContentsTheSame(@NonNull ListRow<T> a, @NonNull ListRow<T> b) {
                return a.equals(b);
            }
        });
        this.onClick = onClick;
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    int p = positionStart - 1;
                    MAIN.post(() -> {
                        if (p < getItemCount()) {
                            notifyItemChanged(p);
                        }
                    });
                }
            }
        });
    }

    /** 绑定数据行（图标、文字、数量、标签） */
    protected abstract void bindItem(@NonNull StockItemRowBinding b, @NonNull T item);

    @Override
    public int getItemViewType(int position) {
        ListRow<T> r = peek(position);
        return r != null && r.isHeader() ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            StockItemSectionBinding b = StockItemSectionBinding.inflate(inf, parent, false);
            return new SectionHolder(b);
        }
        return new RowHolder(StockItemRowBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListRow<T> row = getItem(position);
        if (row == null) {
            return;
        }
        if (holder instanceof SectionHolder) {
            StockUi.bindSection(((SectionHolder) holder).binding, row.header, null, null, null);
            return;
        }
        StockItemRowBinding b = ((RowHolder) holder).binding;
        T item = row.item;
        if (item == null) {
            return;
        }
        bindItem(b, item);
        boolean first = position == 0 || isHeader(position - 1);
        boolean last = position == getItemCount() - 1 || isHeader(position + 1);
        int size = first && last ? 1 : 3;
        int index = first ? 0 : last ? 2 : 1;
        StockUi.position(b.stockRowCard, index, size);
        if (onClick != null) {
            b.stockRowCard.setOnClickListener(v -> onClick.accept(item));
        } else {
            b.stockRowCard.setOnClickListener(null);
            b.stockRowCard.setClickable(false);
        }
    }

    /** 是否为标题行（不触发加载） */
    public boolean isHeader(int position) {
        if (position < 0 || position >= getItemCount()) {
            return true;
        }
        ListRow<T> r = peek(position);
        return r == null || r.isHeader();
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final StockItemRowBinding binding;

        RowHolder(@NonNull StockItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        final StockItemSectionBinding binding;

        SectionHolder(@NonNull StockItemSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /** 分组列表项之间留 2 dp（标题行不留） */
    public static final class Decoration extends com.zhimu.slmp.base.widget.GroupedListDecoration {
        private final RowPagingAdapter<?> adapter;
        private final int offset;

        /**
         * @param offset 本适配器在 ConcatAdapter 中的起始位置（前面有页面头部时传头部项数）
         */
        public Decoration(@NonNull android.content.Context context, @NonNull RowPagingAdapter<?> adapter,
                          int offset) {
            super(context);
            this.adapter = adapter;
            this.offset = offset;
        }

        @Override
        protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
            int p = adapterPosition - offset;
            return p >= 0 && p < adapter.getItemCount() && !adapter.isHeader(p);
        }
    }
}
