package com.zhimu.slmp.herd.ui.stock;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.herd.databinding.HerdItemBatchBinding;
import com.zhimu.slmp.herd.databinding.HerdItemCategoryStockBinding;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.CategoryStockVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * L03 品类视图（API-L05）：每项一个品类，点按展开为该品类的在养批次（同批次视图的列表项）。
 */
public class CategoryStockAdapter extends ListAdapter<CategoryStockAdapter.Row, RecyclerView.ViewHolder> {

    /** 列表行：品类行或展开的批次行 */
    static final class Row {
        final CategoryStockVO category;
        @Nullable
        final BatchListItemVO batch;

        Row(@NonNull CategoryStockVO category, @Nullable BatchListItemVO batch) {
            this.category = category;
            this.batch = batch;
        }

        long key() {
            return batch == null ? -category.getCategoryId() : batch.getBatchId();
        }
    }

    private static final int TYPE_CATEGORY = 0;
    private static final int TYPE_BATCH = 1;

    private final Consumer<BatchListItemVO> onBatchClick;
    private final Set<Long> expanded = new HashSet<>();
    private List<CategoryStockVO> source = new ArrayList<>();

    public CategoryStockAdapter(@NonNull Consumer<BatchListItemVO> onBatchClick) {
        super(new DiffUtil.ItemCallback<Row>() {
            @Override
            public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
                return a.key() == b.key();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
                return Objects.equals(a.category, b.category) && Objects.equals(a.batch, b.batch);
            }
        });
        this.onBatchClick = onBatchClick;
    }

    public void setCategories(@Nullable List<CategoryStockVO> list) {
        source = list == null ? new ArrayList<>() : new ArrayList<>(list);
        submitList(flatten());
    }

    private List<Row> flatten() {
        List<Row> rows = new ArrayList<>();
        for (CategoryStockVO c : source) {
            rows.add(new Row(c, null));
            if (expanded.contains(c.getCategoryId())) {
                for (BatchListItemVO b : c.getBatches()) {
                    rows.add(new Row(c, b));
                }
            }
        }
        return rows;
    }

    private void toggle(@NonNull CategoryStockVO c) {
        if (!expanded.remove(c.getCategoryId())) {
            expanded.add(c.getCategoryId());
        }
        submitList(flatten(), this::notifyDataSetChanged);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).batch == null ? TYPE_CATEGORY : TYPE_BATCH;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_BATCH) {
            return new BatchHolder(HerdItemBatchBinding.inflate(inflater, parent, false));
        }
        return new CategoryHolder(HerdItemCategoryStockBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        int groupPosition = GroupPosition.of(position, getItemCount());
        if (holder instanceof BatchHolder) {
            HerdItemBatchBinding b = ((BatchHolder) holder).binding;
            GroupPosition.apply(b.getRoot(), groupPosition);
            BatchListItemVO batch = row.batch;
            if (batch != null) {
                BatchRowBinder.bind(b, batch);
                ClickThrottle.setOnClickListener(b.getRoot(), v -> onBatchClick.accept(batch));
            }
            return;
        }
        HerdItemCategoryStockBinding b = ((CategoryHolder) holder).binding;
        GroupPosition.apply(b.getRoot(), groupPosition);
        CategoryStockVO c = row.category;
        b.herdCategoryName.setText(c.getCategoryName());
        b.herdCategoryStock.setText("存栏 " + HerdFormat.qty(c.getStock(), c.getUnitText()));
        b.herdCategorySub.setText(c.getBatchCount() + " 个批次" + HerdFormat.DOT + "死亡率 "
                + HerdFormat.percent(c.getMortalityRate()));
        b.herdCategoryExpand.setImageResource(expanded.contains(c.getCategoryId())
                ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        ClickThrottle.setOnClickListener(b.getRoot(), v -> toggle(c));
    }

    static final class CategoryHolder extends RecyclerView.ViewHolder {
        final HerdItemCategoryStockBinding binding;

        CategoryHolder(@NonNull HerdItemCategoryStockBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class BatchHolder extends RecyclerView.ViewHolder {
        final HerdItemBatchBinding binding;

        BatchHolder(@NonNull HerdItemBatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
