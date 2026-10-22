package com.zhimu.slmp.today.ui.common;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.today.databinding.TodayItemTodoBinding;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.home.TodoItemBinder;

import java.util.List;
import java.util.Objects;

/**
 * 待办分页列表（T01“今天和已逾期”、T03 月视图当日列表）：分组圆角随首末项变化，新增或移除时重绑相邻项；
 * 点击、长按回调页面；登记回流时对应项淡出 200 毫秒。
 */
public class TodoPagingAdapter extends PagingDataAdapter<TodoItemVO, TodoPagingAdapter.VH> {

    /** 点击回调 */
    public interface Listener {
        void onTodoClick(@NonNull TodoItemVO item);

        void onTodoLongClick(@NonNull TodoItemVO item);
    }

    public static final DiffUtil.ItemCallback<TodoItemVO> DIFF = new DiffUtil.ItemCallback<TodoItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull TodoItemVO a, @NonNull TodoItemVO b) {
            return a.getTodoId() == b.getTodoId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull TodoItemVO a, @NonNull TodoItemVO b) {
            return a.getVersion() == b.getVersion() && Objects.equals(a.getDisplayStatus(), b.getDisplayStatus())
                    && Objects.equals(a.getTitle(), b.getTitle()) && Objects.equals(a.getSubText(), b.getSubText())
                    && Objects.equals(a.getTailText(), b.getTailText());
        }
    };

    @NonNull
    private final Listener listener;
    private boolean showFarmTag;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public TodoPagingAdapter(@NonNull Listener listener, boolean showFarmTag) {
        super(DIFF);
        this.listener = listener;
        this.showFarmTag = showFarmTag;
        registerAdapterDataObserver(new NeighborRebinder(this, handler));
    }

    public void setShowFarmTag(boolean show) {
        if (showFarmTag != show) {
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
        if (item == null) {
            return;
        }
        holder.itemView.setAlpha(1f);
        TodoItemBinder.bind(holder.b, item, showFarmTag, position, getItemCount(), true);
        ClickThrottle.setOnClickListener(holder.b.card, v -> listener.onTodoClick(item));
        holder.b.card.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            listener.onTodoLongClick(item);
            return true;
        });
    }

    /** 已加载项中某个待办的位置，没有时返回 -1 */
    public int positionOf(long todoId) {
        List<TodoItemVO> items = snapshot().getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getTodoId() == todoId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 对应项淡出（200 毫秒）后执行 onEnd；不在屏幕上时直接执行。
     *
     * @param offset 本适配器在 ConcatAdapter 中的起始位置
     */
    public void fadeOut(@NonNull RecyclerView rv, long todoId, int offset, @NonNull Runnable onEnd) {
        int pos = positionOf(todoId);
        RecyclerView.ViewHolder vh = pos < 0 ? null : rv.findViewHolderForAdapterPosition(pos + offset);
        if (vh == null) {
            onEnd.run();
            return;
        }
        vh.itemView.animate().alpha(0f).setDuration(200).withEndAction(onEnd).start();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TodayItemTodoBinding b;

        VH(@NonNull TodayItemTodoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    /** 首末项变化时重绑相邻项，使分组圆角正确 */
    public static final class NeighborRebinder extends RecyclerView.AdapterDataObserver {
        private final RecyclerView.Adapter<?> adapter;
        private final Handler handler;

        public NeighborRebinder(@NonNull RecyclerView.Adapter<?> adapter, @NonNull Handler handler) {
            this.adapter = adapter;
            this.handler = handler;
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            rebind(positionStart - 1, positionStart + itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            rebind(positionStart - 1, positionStart);
        }

        private void rebind(int before, int after) {
            handler.post(() -> {
                int count = adapter.getItemCount();
                if (before >= 0 && before < count) {
                    adapter.notifyItemChanged(before, PAYLOAD_POSITION);
                }
                if (after >= 0 && after < count) {
                    adapter.notifyItemChanged(after, PAYLOAD_POSITION);
                }
            });
        }

        @Nullable
        public static final Object PAYLOAD_POSITION = "position";
    }
}
