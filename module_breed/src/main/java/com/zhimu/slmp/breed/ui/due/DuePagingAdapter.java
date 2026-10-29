package com.zhimu.slmp.breed.ui.due;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.breed.R;
import com.zhimu.slmp.breed.databinding.BreedItemGroupTitleBinding;
import com.zhimu.slmp.breed.databinding.BreedItemRowBinding;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.breed.ui.common.RowBinder;
import com.zhimu.slmp.breed.util.BreedTextFormatter;

import java.util.Collections;
import java.util.Map;

/**
 * 待产 / 待出雏分组列表（UI 2.2 B02）：分组标题“今天 · 1 头”；待产项为天数徽标 + “0131 · 产房 · 第 3 胎” +
 * “6月27日配种 · 预产期 10月19日” + 提醒状态，“今天”组且可登记时尾部“登记产仔”；待出雏项主文字为“栏舍名 · 批次号”。
 * 员工只读版没有按钮，列表项不可点（无涟漪）。
 */
public class DuePagingAdapter extends PagingDataAdapter<DueRow, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 1;
    private static final int TYPE_ITEM = 2;

    /** 列表项操作 */
    public interface Listener {
        void onItemClick(@NonNull DueRow row);

        void onRegisterFarrow(@NonNull FarrowDueItemVO item);

        void onRegisterHatch(@NonNull HatchDueItemVO item);
    }

    private final Listener listener;
    private final boolean hatch;
    private boolean readOnly;
    @NonNull
    private Map<String, Integer> counts = Collections.emptyMap();

    public DuePagingAdapter(boolean hatch, @NonNull Listener listener) {
        super(new DiffUtil.ItemCallback<DueRow>() {
            @Override
            public boolean areItemsTheSame(@NonNull DueRow a, @NonNull DueRow b) {
                return a.key().equals(b.key());
            }

            @Override
            public boolean areContentsTheSame(@NonNull DueRow a, @NonNull DueRow b) {
                return a.equals(b) && contentOf(a).equals(contentOf(b));
            }
        });
        this.hatch = hatch;
        this.listener = listener;
        // 下一页接在分组末项之后时，原末项的圆角改为中间项
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
    private static String contentOf(@NonNull DueRow r) {
        if (r.getFarrow() != null) {
            return r.getFarrow().getDaysToDue() + BreedTextFormatter.nz(r.getFarrow().getReminderText())
                    + r.getFarrow().isCanRegister();
        }
        if (r.getHatch() != null) {
            return r.getHatch().getDaysToHatch() + BreedTextFormatter.nz(r.getHatch().getReminderText())
                    + r.getHatch().getFertileCount() + r.getHatch().isCanRegister();
        }
        return BreedTextFormatter.nz(r.getGroupText());
    }

    /** 员工只读版 */
    public void setReadOnly(boolean readOnly) {
        if (this.readOnly != readOnly) {
            this.readOnly = readOnly;
            notifyDataSetChanged();
        }
    }

    /** 第 1 页 tab_counts：刷新已显示的分组标题数量 */
    public void setCounts(@Nullable Map<String, Integer> counts) {
        this.counts = counts == null ? Collections.emptyMap() : counts;
        for (int i = 0; i < getItemCount(); i++) {
            DueRow r = peek(i);
            if (r != null && r.isHeader()) {
                notifyItemChanged(i);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        DueRow r = peek(position);
        return r != null && r.isHeader() ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(BreedItemGroupTitleBinding.inflate(inflater, parent, false));
        }
        return new ItemHolder(BreedItemRowBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DueRow r = getItem(position);
        if (r == null) {
            return;
        }
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).binding.breedGroupTitle.setText(BreedDueViewModel.groupTitle(r, counts, hatch));
            return;
        }
        BreedItemRowBinding b = ((ItemHolder) holder).binding;
        int[] group = groupRange(position);
        RowBinder.reset(b, position - group[0], group[1]);
        if (r.getFarrow() != null) {
            bindFarrow(b, r, r.getFarrow());
        } else if (r.getHatch() != null) {
            bindHatch(b, r, r.getHatch());
        }
    }

    private void bindFarrow(@NonNull BreedItemRowBinding b, @NonNull DueRow r, @NonNull FarrowDueItemVO i) {
        RowBinder.badge(b, i.getDaysToDue());
        // 第三行：负责员工 + 提醒状态（没有负责员工时只显示提醒状态）
        RowBinder.text(b, BreedTextFormatter.farrowDueTitle(i), BreedTextFormatter.farrowDueLine2(i),
                BreedTextFormatter.dueLine3(i.getStaffNames(), i.getReminderText()));
        if (!readOnly && i.isCanRegister()) {
            RowBinder.action(b, b.getRoot().getContext().getString(R.string.breed_action_register_farrow),
                    v -> listener.onRegisterFarrow(i));
        }
        RowBinder.click(b, readOnly || !i.isCanOpenArchive() ? null : v -> listener.onItemClick(r));
    }

    private void bindHatch(@NonNull BreedItemRowBinding b, @NonNull DueRow r, @NonNull HatchDueItemVO i) {
        RowBinder.badge(b, i.getDaysToHatch());
        RowBinder.text(b, BreedTextFormatter.nz(i.getFlockText()), BreedTextFormatter.hatchDueLine2(i),
                BreedTextFormatter.dueLine3(i.getStaffNames(), i.getReminderText()));
        if (!readOnly && i.isCanRegister()) {
            RowBinder.action(b, b.getRoot().getContext().getString(R.string.breed_action_register_hatch),
                    v -> listener.onRegisterHatch(i));
        }
        RowBinder.click(b, readOnly || !i.isCanOpen() ? null : v -> listener.onItemClick(r));
    }

    /** 当前项所在分组的起始位置与项数（分组标题之间） */
    @NonNull
    private int[] groupRange(int position) {
        int start = position;
        while (start > 0) {
            DueRow prev = peek(start - 1);
            if (prev == null || prev.isHeader()) {
                break;
            }
            start--;
        }
        int end = position;
        int count = getItemCount();
        while (end + 1 < count) {
            DueRow next = peek(end + 1);
            if (next == null || next.isHeader()) {
                break;
            }
            end++;
        }
        return new int[]{start, end - start + 1};
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final BreedItemGroupTitleBinding binding;

        HeaderHolder(@NonNull BreedItemGroupTitleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class ItemHolder extends RecyclerView.ViewHolder {
        final BreedItemRowBinding binding;

        ItemHolder(@NonNull BreedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
