package com.zhimu.slmp.today.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.today.databinding.TodayItemTodoBinding;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.home.TodoItemBinder;

/**
 * 不分页的待办分组列表（日视图“已逾期”组）。
 */
public class TodoListAdapter extends ListAdapter<TodoItemVO, TodoListAdapter.VH> {

    @NonNull
    private final TodoPagingAdapter.Listener listener;
    private boolean showFarmTag;

    public TodoListAdapter(@NonNull TodoPagingAdapter.Listener listener, boolean showFarmTag) {
        super(TodoPagingAdapter.DIFF);
        this.listener = listener;
        this.showFarmTag = showFarmTag;
    }

    public void setShowFarmTag(boolean show) {
        if (show != showFarmTag) {
            showFarmTag = show;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(TodayItemTodoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TodoItemVO item = getItem(position);
        TodoItemBinder.bind(holder.b, item, showFarmTag, position, getItemCount(), true);
        ClickThrottle.setOnClickListener(holder.b.card, v -> listener.onTodoClick(item));
        holder.b.card.setOnLongClickListener(v -> {
            listener.onTodoLongClick(item);
            return true;
        });
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TodayItemTodoBinding b;

        VH(@NonNull TodayItemTodoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
