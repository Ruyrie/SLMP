package com.zhimu.slmp.herd.ui.stock;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.herd.databinding.HerdItemBatchBinding;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;

import java.util.function.Consumer;

/**
 * L03 批次视图（API-L06 分页，只含在养批次）。
 */
public class BatchPagingAdapter extends PagingDataAdapter<BatchListItemVO, BatchPagingAdapter.Holder> {

    private final Consumer<BatchListItemVO> onClick;

    public BatchPagingAdapter(@NonNull Consumer<BatchListItemVO> onClick) {
        super(new DiffUtil.ItemCallback<BatchListItemVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull BatchListItemVO a, @NonNull BatchListItemVO b) {
                return a.getBatchId() == b.getBatchId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull BatchListItemVO a, @NonNull BatchListItemVO b) {
                return a.getCurrentCount() == b.getCurrentCount() && a.getDayAge() == b.getDayAge()
                        && a.isInWithdrawal() == b.isInWithdrawal();
            }
        });
        this.onClick = onClick;
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    notifyItemChanged(positionStart - 1);
                }
            }
        });
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(HerdItemBatchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BatchListItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        GroupPosition.apply(holder.binding.getRoot(), GroupPosition.of(position, getItemCount()));
        BatchRowBinder.bind(holder.binding, item);
        ClickThrottle.setOnClickListener(holder.binding.getRoot(), v -> onClick.accept(item));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HerdItemBatchBinding binding;

        Holder(@NonNull HerdItemBatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
