package com.zhimu.slmp.today.ui.calendar;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayItemExpandBinding;
import com.zhimu.slmp.today.databinding.TodayItemSectionBinding;
import com.zhimu.slmp.today.databinding.TodayItemTodoBinding;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;
import com.zhimu.slmp.today.ui.home.TodoItemBinder;
import com.zhimu.slmp.today.util.WeekGroup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 周视图：一周 7 天依次列出（没有待办的日期不显示），每天分组标题“10月19日 周一 · 今天 / 24 项”、先显示前 5 项，
 * 第 6 项起收起为“展开其余 N 项”，展开后为“收起”。行展平为标题、待办、展开按钮三种。
 */
public class WeekGroupAdapter extends ListAdapter<WeekGroupAdapter.Row, RecyclerView.ViewHolder> {

    /** 展开 / 收起 */
    public interface ExpandListener {
        void onExpand(@NonNull LocalDate date);
    }

    static final int TYPE_TITLE = 0;
    static final int TYPE_ITEM = 1;
    static final int TYPE_EXPAND = 2;

    /** 展平后的一行 */
    static final class Row {
        final int type;
        @NonNull
        final WeekGroup group;
        @Nullable
        final TodoItemVO item;
        final int index;
        final int size;

        Row(int type, @NonNull WeekGroup group, @Nullable TodoItemVO item, int index, int size) {
            this.type = type;
            this.group = group;
            this.item = item;
            this.index = index;
            this.size = size;
        }

        @NonNull
        String key() {
            return type + "_" + group.getDate() + "_" + (item == null ? "" : item.getTodoId());
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.key().equals(b.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            if (a.index != b.index || a.size != b.size) {
                return false;
            }
            if (a.type == TYPE_ITEM) {
                return a.item != null && b.item != null && TodoPagingAdapter.DIFF.areContentsTheSame(a.item, b.item);
            }
            return Objects.equals(a.group.getTitle(), b.group.getTitle()) && a.group.getTotal() == b.group.getTotal()
                    && a.group.isExpanded() == b.group.isExpanded() && a.group.isExpanding() == b.group.isExpanding()
                    && a.group.getRemaining() == b.group.getRemaining();
        }
    };

    @NonNull
    private final TodoPagingAdapter.Listener listener;
    @NonNull
    private final ExpandListener expandListener;
    private boolean showFarmTag = true;

    public WeekGroupAdapter(@NonNull TodoPagingAdapter.Listener listener, @NonNull ExpandListener expandListener) {
        super(DIFF);
        this.listener = listener;
        this.expandListener = expandListener;
    }

    public void setShowFarmTag(boolean show) {
        if (show != showFarmTag) {
            showFarmTag = show;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    public void submitGroups(@Nullable List<WeekGroup> groups) {
        List<Row> rows = new ArrayList<>();
        if (groups != null) {
            for (WeekGroup g : groups) {
                rows.add(new Row(TYPE_TITLE, g, null, 0, 0));
                List<TodoItemVO> items = g.getItems();
                for (int i = 0; i < items.size(); i++) {
                    rows.add(new Row(TYPE_ITEM, g, items.get(i), i, items.size()));
                }
                if (g.isExpanded() || g.getRemaining() > 0) {
                    rows.add(new Row(TYPE_EXPAND, g, null, 0, 0));
                }
            }
        }
        submitList(rows);
    }

    /** 某天分组标题在本适配器中的位置 */
    public int positionOf(@NonNull LocalDate date) {
        List<Row> rows = getCurrentList();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type == TYPE_TITLE && rows.get(i).group.getDate().equals(date)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TITLE) {
            return new TitleVH(TodayItemSectionBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_EXPAND) {
            return new ExpandVH(TodayItemExpandBinding.inflate(inflater, parent, false));
        }
        return new ItemVH(TodayItemTodoBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof TitleVH) {
            TodayItemSectionBinding b = ((TitleVH) holder).b;
            b.title.setText(row.group.getTitle());
            b.count.setText(b.getRoot().getContext().getString(R.string.today_calendar_count, row.group.getTotal()));
        } else if (holder instanceof ExpandVH) {
            TodayItemExpandBinding b = ((ExpandVH) holder).b;
            if (row.group.isExpanded()) {
                b.button.setText(R.string.today_calendar_collapse);
                b.button.setIconResource(com.zhimu.slmp.base.R.drawable.ic_expand_less);
            } else {
                b.button.setText(b.getRoot().getContext().getString(R.string.today_calendar_expand,
                        row.group.getRemaining()));
                b.button.setIconResource(com.zhimu.slmp.base.R.drawable.ic_expand_more);
            }
            b.button.setEnabled(!row.group.isExpanding());
            ClickThrottle.setOnClickListener(b.button, v -> expandListener.onExpand(row.group.getDate()));
        } else if (holder instanceof ItemVH && row.item != null) {
            TodayItemTodoBinding b = ((ItemVH) holder).b;
            TodoItemVO item = row.item;
            TodoItemBinder.bind(b, item, showFarmTag, row.index, row.size, true);
            ClickThrottle.setOnClickListener(b.card, v -> listener.onTodoClick(item));
            b.card.setOnLongClickListener(v -> {
                listener.onTodoLongClick(item);
                return true;
            });
        }
    }

    static final class TitleVH extends RecyclerView.ViewHolder {
        final TodayItemSectionBinding b;

        TitleVH(@NonNull TodayItemSectionBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class ExpandVH extends RecyclerView.ViewHolder {
        final TodayItemExpandBinding b;

        ExpandVH(@NonNull TodayItemExpandBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class ItemVH extends RecyclerView.ViewHolder {
        final TodayItemTodoBinding b;

        ItemVH(@NonNull TodayItemTodoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
