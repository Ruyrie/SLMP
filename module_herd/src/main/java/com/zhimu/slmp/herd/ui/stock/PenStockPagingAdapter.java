package com.zhimu.slmp.herd.ui.stock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.herd.databinding.HerdItemPenStockBinding;
import com.zhimu.slmp.herd.model.vo.PenStockItemVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * L03 栏舍视图（API-L04 分页）：占用进度条；空栏中性标签、休药 warning 标签；停用栏舍 error 标签且不显示进度条；
 * 种畜栏舍（母猪舍）显示“种猪按耳号建档，在繁育中查看”并带 chevron。
 */
public class PenStockPagingAdapter extends PagingDataAdapter<PenStockItemVO, PenStockPagingAdapter.Holder> {

    static final String BREEDING_HINT = "种猪按耳号建档，在繁育中查看";

    private final Consumer<PenStockItemVO> onClick;

    public PenStockPagingAdapter(@NonNull Consumer<PenStockItemVO> onClick) {
        super(new DiffUtil.ItemCallback<PenStockItemVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull PenStockItemVO a, @NonNull PenStockItemVO b) {
                return a.getPenId() == b.getPenId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull PenStockItemVO a, @NonNull PenStockItemVO b) {
                return a.getStockCount() == b.getStockCount() && a.getCapacity() == b.getCapacity()
                        && Objects.equals(a.getDisplayStatus(), b.getDisplayStatus())
                        && Objects.equals(a.getPenName(), b.getPenName());
            }
        });
        this.onClick = onClick;
        // 追加一页后，原来的末项改为中间项圆角
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
        return new Holder(HerdItemPenStockBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PenStockItemVO item = getItem(position);
        HerdItemPenStockBinding b = holder.binding;
        if (item == null) {
            return;
        }
        GroupPosition.apply(b.getRoot(), GroupPosition.of(position, getItemCount()));
        String unit = item.getUnitText();
        b.herdPenName.setText(item.getPenName());
        boolean disabled = item.isDisabled();
        boolean breeding = item.isBreedingPen()
                && (item.getBatchCount() == null || item.getBatchCount() == 0);
        b.herdPenDisabledTag.setVisibility(disabled ? View.VISIBLE : View.GONE);
        b.herdPenChevron.setVisibility(breeding ? View.VISIBLE : View.GONE);
        b.herdPenStock.setVisibility(breeding ? View.GONE : View.VISIBLE);
        b.herdPenStock.setText(HerdFormat.stockSlashCapacity(item.getStockCount(), item.getCapacity(), unit));
        b.herdPenStock.setTextColor(MaterialColors.getColor(b.herdPenStock, item.isEmpty()
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface));
        b.herdPenEmptyTag.setVisibility(item.isEmpty() && !breeding ? View.VISIBLE : View.GONE);
        b.herdPenWithdrawalTag.setVisibility(item.isInWithdrawal() ? View.VISIBLE : View.GONE);
        b.herdPenSub.setText(breeding ? BREEDING_HINT : subtitle(item));
        boolean showProgress = !disabled && !breeding;
        b.herdPenProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (showProgress) {
            // 空栏只显示轨道（原型中 5 号育肥舍进度条为绘制错误）
            b.herdPenProgress.setProgressCompat(item.isEmpty() ? 0 : HerdFormat.progress(item.getOccupancyRate()), false);
        }
        ClickThrottle.setOnClickListener(b.getRoot(), v -> onClick.accept(item));
    }

    /** 第二行：“ZM2610-045  35 日龄 · 保育期 · 死亡率 1.8%”；多批次“ZM2610-019 等 2 个批次 · 死亡率”；空栏“ZM2609-012 已于 10月13日全部出栏” */
    @NonNull
    static String subtitle(@NonNull PenStockItemVO item) {
        if (item.isEmpty()) {
            PenStockItemVO.EndedBatchItem ended = item.getLastEndedBatch();
            if (ended == null) {
                return "";
            }
            String how = PenStockItemVO.EndedBatchItem.END_ALL_DEAD.equals(ended.getEndType()) ? "全部死淘" : "全部出栏";
            return ended.getBatchNo() + " 已于 " + HerdFormat.date(ended.getEndDate()) + how;
        }
        PenStockItemVO.PenFirstBatchItem first = item.getFirstBatch();
        if (first == null) {
            return "";
        }
        String mortality = "死亡率 " + HerdFormat.percent(item.getMortalityRate());
        Integer count = item.getBatchCount();
        if (count != null && count > 1) {
            return first.getBatchNo() + " 等 " + count + " 个批次" + HerdFormat.DOT + mortality;
        }
        StringBuilder sb = new StringBuilder(first.getBatchNo()).append("  ").append(first.getDayAge()).append(" 日龄");
        if (first.getStageName() != null) {
            sb.append(HerdFormat.DOT).append(first.getStageName());
        }
        return sb.append(HerdFormat.DOT).append(mortality).toString();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HerdItemPenStockBinding binding;

        Holder(@NonNull HerdItemPenStockBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
