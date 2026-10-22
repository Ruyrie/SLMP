package com.zhimu.slmp.feed.ui.growth;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.databinding.FeedItemRowBinding;
import com.zhimu.slmp.feed.model.vo.WeighRecordItemVO;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * G06 称重记录：“10月19日 · 96 日龄”/“60.0 千克 · 标准 68.6 · 日增重 0.81 千克”+ 偏离标签；
 * 超出允许范围的记录用 warning 图标底与标签，其余 secondary 图标底、中性标签。action 为 NONE 的行不可点、没有水波。
 */
public class WeighRecordPagingAdapter extends PagingDataAdapter<WeighRecordItemVO, WeighRecordPagingAdapter.Holder> {

    private static final DiffUtil.ItemCallback<WeighRecordItemVO> DIFF = new DiffUtil.ItemCallback<WeighRecordItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull WeighRecordItemVO a, @NonNull WeighRecordItemVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull WeighRecordItemVO a, @NonNull WeighRecordItemVO b) {
            return Objects.equals(a.getAvgWeight(), b.getAvgWeight())
                    && Objects.equals(a.getDeviationRatio(), b.getDeviationRatio())
                    && Objects.equals(a.getAction(), b.getAction());
        }
    };

    /** 点按回调：记录与被点的视图（弹出菜单的锚点） */
    private final BiConsumer<WeighRecordItemVO, ListRowView> onClick;

    public WeighRecordPagingAdapter(@NonNull BiConsumer<WeighRecordItemVO, ListRowView> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FeedItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        WeighRecordItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        ListRowView row = holder.binding.feedRow;
        boolean out = item.isOutOfRange();
        row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_scale, out ? ListRowView.TONE_WARNING
                : ListRowView.TONE_SECONDARY);
        row.setTitle(FeedFormat.join(FeedFormat.date(item.getWeighDate()),
                item.getDayAge() == null ? null : item.getDayAge() + " 日龄"));
        row.setSubtitle(FeedFormat.join(FeedFormat.kg1(item.getAvgWeight()),
                item.getStdWeight() == null ? null : "标准 " + FeedFormat.num1(item.getStdWeight()),
                item.getDailyGain() == null ? null : "日增重 " + FeedFormat.kg2(item.getDailyGain())));
        if (item.getDeviationRatio() == null) {
            row.setStatusTag(null, StatusTagView.NEUTRAL, 0);
        } else {
            row.setStatusTag(FeedFormat.signedPercent(item.getDeviationRatio()),
                    out ? StatusTagView.WARNING : StatusTagView.NEUTRAL, 0);
        }
        row.setGroupPosition(GroupPosition.of(position, getItemCount()));
        if (item.isClickable()) {
            ClickThrottle.setOnClickListener(row, v -> onClick.accept(item, row));
        } else {
            row.setOnClickListener(null);
            row.setClickable(false);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FeedItemRowBinding binding;

        Holder(@NonNull FeedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
