package com.zhimu.slmp.feed.ui.today;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.databinding.FeedItemMealHeaderBinding;
import com.zhimu.slmp.feed.databinding.FeedItemTaskBinding;
import com.zhimu.slmp.feed.model.vo.FeedTaskItemVO;

import java.util.function.Consumer;

/**
 * G01 任务列表：餐次标题 + 分组列表项（组首末项圆角 20、中间 4、项间 2 dp）。组内位置按相邻项是否为标题计算，
 * 新一页追加时由页面通知上一项重新绑定。
 */
public class FeedTaskPagingAdapter extends PagingDataAdapter<FeedTaskRow, RecyclerView.ViewHolder> {

    static final int TYPE_HEADER = 1;
    static final int TYPE_TASK = 2;

    private static final DiffUtil.ItemCallback<FeedTaskRow> DIFF = new DiffUtil.ItemCallback<FeedTaskRow>() {
        @Override
        public boolean areItemsTheSame(@NonNull FeedTaskRow a, @NonNull FeedTaskRow b) {
            return a.key.equals(b.key);
        }

        @Override
        public boolean areContentsTheSame(@NonNull FeedTaskRow a, @NonNull FeedTaskRow b) {
            return a.equals(b);
        }
    };

    private final Consumer<FeedTaskItemVO> onClick;

    public FeedTaskPagingAdapter(@NonNull Consumer<FeedTaskItemVO> onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    @Override
    public int getItemViewType(int position) {
        FeedTaskRow row = peek(position);
        return row != null && row.header ? TYPE_HEADER : TYPE_TASK;
    }

    /** 该位置是否为任务行（GroupedListDecoration 只在任务行之间留缝隙） */
    public boolean isTask(int position) {
        return position >= 0 && position < getItemCount() && getItemViewType(position) == TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(FeedItemMealHeaderBinding.inflate(inflater, parent, false));
        }
        return new TaskHolder(FeedItemTaskBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FeedTaskRow row = getItem(position);
        if (row == null) {
            return;
        }
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).binding.feedMealHeader.setText(row.headerText);
            return;
        }
        TaskHolder h = (TaskHolder) holder;
        FeedItemTaskBinding b = h.binding;
        b.feedTaskRow.setTitle(row.title);
        b.feedTaskRow.setTitleStrikeThrough(row.strike);
        b.feedTaskRow.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, row.tone);
        b.feedTaskRow.setStatusTag(row.tagText, row.tagType, row.tagIcon);
        b.feedTaskLine1.setText(row.line1);
        b.feedTaskLowStock.setVisibility(row.lowStockTag ? View.VISIBLE : View.GONE);
        if (row.lowStockTag) {
            b.feedTaskLowStock.setStatus("库存不足", StatusTagView.WARNING);
        }
        b.feedTaskLine2.setText(row.line2);
        b.feedTaskLine2.setVisibility(row.line2 == null || row.line2.isEmpty() ? View.GONE : View.VISIBLE);
        b.feedTaskRow.setGroupPosition(groupPosition(position));
        FeedTaskItemVO item = row.item;
        ClickThrottle.setOnClickListener(b.feedTaskRow, item == null ? null : v -> onClick.accept(item));
        b.feedTaskRow.setContentDescription(row.title + "，" + row.line1
                + (row.line2 == null ? "" : "，" + row.line2) + "，" + row.tagText);
    }

    /** 组内位置：上一项为标题（或没有）为组首，下一项为标题（或没有）为组末 */
    private int groupPosition(int position) {
        boolean top = position == 0 || !isTaskPeek(position - 1);
        boolean bottom = position >= getItemCount() - 1 || !isTaskPeek(position + 1);
        if (top && bottom) {
            return GroupPosition.SINGLE;
        }
        if (top) {
            return GroupPosition.TOP;
        }
        return bottom ? GroupPosition.BOTTOM : GroupPosition.MIDDLE;
    }

    private boolean isTaskPeek(int position) {
        @Nullable FeedTaskRow row = position >= 0 && position < getItemCount() ? peek(position) : null;
        return row != null && !row.header;
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final FeedItemMealHeaderBinding binding;

        HeaderHolder(@NonNull FeedItemMealHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static final class TaskHolder extends RecyclerView.ViewHolder {
        final FeedItemTaskBinding binding;

        TaskHolder(@NonNull FeedItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
