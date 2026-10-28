package com.zhimu.slmp.herd.ui.archive;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.herd.databinding.HerdItemTimelineBinding;
import com.zhimu.slmp.herd.model.vo.KeyValue;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * L06 生产记录时间线（API-L16 分页）：点按展开或跳转，长按弹记录菜单。
 */
public class TimelinePagingAdapter extends PagingDataAdapter<TimelineItemVO, TimelinePagingAdapter.Holder> {

    /** 点击回调 */
    public interface Listener {
        void onRecordClick(@NonNull TimelineItemVO item);

        void onRecordLongClick(@NonNull View anchor, @NonNull TimelineItemVO item);
    }

    private final Listener listener;
    private Set<Long> expanded = Collections.emptySet();

    public TimelinePagingAdapter(@NonNull Listener listener) {
        super(new DiffUtil.ItemCallback<TimelineItemVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull TimelineItemVO a, @NonNull TimelineItemVO b) {
                return a.getId() == b.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull TimelineItemVO a, @NonNull TimelineItemVO b) {
                return Objects.equals(a.getTitle(), b.getTitle()) && Objects.equals(a.getSubtitle(), b.getSubtitle())
                        && Objects.equals(a.getAction(), b.getAction());
            }
        });
        this.listener = listener;
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    notifyItemChanged(positionStart - 1);
                }
            }
        });
    }

    /** 访问最后一项，让分页加载下一页（定位记录时用） */
    public void loadMore() {
        int count = getItemCount();
        if (count > 0) {
            getItem(count - 1);
        }
    }

    /** 展开的记录；只刷新变化的项 */
    public void setExpanded(@NonNull Set<Long> ids) {
        Set<Long> old = expanded;
        expanded = ids;
        for (int i = 0; i < getItemCount(); i++) {
            TimelineItemVO item = peek(i);
            if (item != null && old.contains(item.getId()) != ids.contains(item.getId())) {
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(HerdItemTimelineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TimelineItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        HerdItemTimelineBinding b = holder.binding;
        Context ctx = b.getRoot().getContext();
        // 日期列：当天“今天 / 08:40”，往日“10月12日 / 周一”
        LocalDate d = DateFormatter.parseDate(item.getBizDate());
        if (item.isToday()) {
            b.herdTlDate.setText("今天");
            b.herdTlTime.setText(DateFormatter.formatTime(item.getOccurredAt()));
        } else {
            b.herdTlDate.setText(d == null ? item.getBizDate() : DateFormatter.monthDay(d));
            b.herdTlTime.setText(d == null ? "" : DateFormatter.weekText(d));
        }
        b.herdTlTitle.setText(item.getTitle());
        b.herdTlSubtitle.setText(item.getSubtitle());
        b.herdTlSubtitle.setVisibility(item.getSubtitle() == null ? View.GONE : View.VISIBLE);
        b.herdTlLine.setVisibility(position == getItemCount() - 1 ? View.INVISIBLE : View.VISIBLE);

        String type = item.getRecordType();
        b.herdTlNode.setImageResource(iconOf(type));
        int bg;
        int fg;
        if (TimelineItemVO.TYPE_DRUG.equals(type)) {
            bg = color(b.herdTlNode, R.attr.colorWarningContainer);
            fg = color(b.herdTlNode, R.attr.colorOnWarningContainer);
        } else if (TimelineItemVO.TYPE_ENTRY.equals(type)) {
            bg = color(b.herdTlNode, com.google.android.material.R.attr.colorSurfaceContainerHighest);
            fg = color(b.herdTlNode, com.google.android.material.R.attr.colorOnSurfaceVariant);
        } else {
            bg = color(b.herdTlNode, com.google.android.material.R.attr.colorSecondaryContainer);
            fg = color(b.herdTlNode, com.google.android.material.R.attr.colorOnSecondaryContainer);
        }
        b.herdTlNode.setBackgroundTintList(ColorStateList.valueOf(bg));
        ImageViewCompat.setImageTintList(b.herdTlNode, ColorStateList.valueOf(fg));

        // 展开详情：登记人、登记时间、备注
        boolean open = item.isExpandable() && expanded.contains(item.getId());
        b.herdTlDetail.removeAllViews();
        b.herdTlDetail.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            for (KeyValue kv : item.getDetail()) {
                TextView tv = new TextView(ctx);
                tv.setTextAppearance(R.style.TextAppearance_Zmt_BodyMedium);
                tv.setTextColor(color(tv, com.google.android.material.R.attr.colorOnSurfaceVariant));
                tv.setText(kv.getKey() + "  " + kv.getValue());
                b.herdTlDetail.addView(tv);
            }
        }
        b.herdTlNote.setVisibility(open && item.getActionNote() != null ? View.VISIBLE : View.GONE);
        b.herdTlNote.setText(item.getActionNote());

        b.getRoot().setOnClickListener(v -> listener.onRecordClick(item));
        b.getRoot().setOnLongClickListener(v -> {
            listener.onRecordLongClick(v, item);
            return true;
        });
    }

    private static int color(@NonNull View v, @AttrRes int attr) {
        return MaterialColors.getColor(v, attr);
    }

    @DrawableRes
    static int iconOf(String type) {
        if (TimelineItemVO.TYPE_INSPECT.equals(type)) {
            return R.drawable.ic_fact_check;
        }
        if (TimelineItemVO.TYPE_FEED.equals(type)) {
            return R.drawable.ic_rice_bowl;
        }
        if (TimelineItemVO.TYPE_WEIGH.equals(type)) {
            return R.drawable.ic_scale;
        }
        if (TimelineItemVO.TYPE_IMMUNE.equals(type)) {
            return R.drawable.ic_vaccines;
        }
        if (TimelineItemVO.TYPE_DRUG.equals(type)) {
            return R.drawable.ic_medication;
        }
        if (TimelineItemVO.TYPE_TRANSFER.equals(type)) {
            return R.drawable.ic_swap_horiz;
        }
        if (TimelineItemVO.TYPE_DEATH.equals(type)) {
            return R.drawable.ic_heart_minus;
        }
        if (TimelineItemVO.TYPE_SALE.equals(type)) {
            return R.drawable.ic_local_shipping;
        }
        return R.drawable.ic_add_box;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HerdItemTimelineBinding binding;

        Holder(@NonNull HerdItemTimelineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
