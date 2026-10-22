package com.zhimu.slmp.today.ui.calendar;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.today.databinding.TodayItemTimelineBinding;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;
import com.zhimu.slmp.today.ui.home.TodoItemBinder;

import java.util.Objects;

/**
 * 日视图时间轴（技术文档 2.6.3）：比较当前项与上一项的 time_label，相同则不显示时间列，同一时间共用一个时间标签；
 * 全天事项排在最上方，时间列写“全天”。日视图不显示养殖场标签。
 */
public class DayTimelineAdapter extends PagingDataAdapter<TodoItemVO, DayTimelineAdapter.VH> {

    @NonNull
    private final TodoPagingAdapter.Listener listener;

    public DayTimelineAdapter(@NonNull TodoPagingAdapter.Listener listener) {
        super(TodoPagingAdapter.DIFF);
        this.listener = listener;
        registerAdapterDataObserver(new TodoPagingAdapter.NeighborRebinder(this, new Handler(Looper.getMainLooper())));
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(TodayItemTimelineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TodoItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        TodoItemVO prev = position > 0 ? peek(position - 1) : null;
        boolean sameTime = prev != null && Objects.equals(prev.getTimeLabel(), item.getTimeLabel());
        holder.b.time.setText(sameTime ? null : item.getTimeLabel());
        holder.b.time.setImportantForAccessibility(sameTime ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        TodoItemBinder.bind(holder.b.item, item, false, position, getItemCount(), false);
        ClickThrottle.setOnClickListener(holder.b.item.card, v -> listener.onTodoClick(item));
        holder.b.item.card.setOnLongClickListener(v -> {
            listener.onTodoLongClick(item);
            return true;
        });
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TodayItemTimelineBinding b;

        VH(@NonNull TodayItemTimelineBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
