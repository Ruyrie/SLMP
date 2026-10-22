package com.zhimu.slmp.today.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.today.databinding.TodayItemListStateBinding;

import java.util.Objects;

/**
 * 列表区状态（作为 ConcatAdapter 的一项）：加载更多的 24 dp 加载圈、列表区骨架、空状态（图标 + 标题 + 说明 + 按钮）、
 * 加载失败（标题 + 说明 + “重新加载”）、列表末尾说明（“只保留最近 180 天的消息”）。NONE 时不占位。
 */
public class ListStateAdapter extends RecyclerView.Adapter<ListStateAdapter.VH> {

    /** 状态 */
    public static final class State {
        public static final int NONE = 0;
        public static final int LOADING = 1;
        public static final int SKELETON = 2;
        public static final int EMPTY = 3;
        public static final int ERROR = 4;
        public static final int NOTE = 5;

        final int type;
        @DrawableRes
        final int icon;
        @Nullable
        final CharSequence title;
        @Nullable
        final CharSequence desc;
        @Nullable
        final CharSequence action;

        private State(int type, int icon, @Nullable CharSequence title, @Nullable CharSequence desc,
                      @Nullable CharSequence action) {
            this.type = type;
            this.icon = icon;
            this.title = title;
            this.desc = desc;
            this.action = action;
        }

        public static final State NONE_STATE = new State(NONE, 0, null, null, null);

        @NonNull
        public static State none() {
            return NONE_STATE;
        }

        @NonNull
        public static State loading() {
            return new State(LOADING, 0, null, null, null);
        }

        @NonNull
        public static State skeleton() {
            return new State(SKELETON, 0, null, null, null);
        }

        @NonNull
        public static State empty(@DrawableRes int icon, @Nullable CharSequence title, @Nullable CharSequence desc,
                                  @Nullable CharSequence action) {
            return new State(EMPTY, icon, title, desc, action);
        }

        @NonNull
        public static State error(@Nullable CharSequence title, @Nullable CharSequence desc, @NonNull CharSequence retry) {
            return new State(ERROR, com.zhimu.slmp.base.R.drawable.ic_cloud_off, title, desc, retry);
        }

        @NonNull
        public static State note(@NonNull CharSequence text) {
            return new State(NOTE, 0, null, text, null);
        }

        public int getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof State)) {
                return false;
            }
            State s = (State) o;
            return type == s.type && icon == s.icon && Objects.equals(str(title), str(s.title))
                    && Objects.equals(str(desc), str(s.desc)) && Objects.equals(str(action), str(s.action));
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, icon, str(title), str(desc));
        }

        @Nullable
        private static String str(@Nullable CharSequence c) {
            return c == null ? null : c.toString();
        }
    }

    @NonNull
    private State state = State.none();
    @Nullable
    private View.OnClickListener onAction;

    public void setOnActionClickListener(@Nullable View.OnClickListener l) {
        onAction = l;
    }

    @NonNull
    public State getState() {
        return state;
    }

    public void setState(@NonNull State newState) {
        if (state.equals(newState)) {
            return;
        }
        boolean had = state.type != State.NONE;
        boolean has = newState.type != State.NONE;
        state = newState;
        if (had && has) {
            notifyItemChanged(0);
        } else if (had) {
            notifyItemRemoved(0);
        } else if (has) {
            notifyItemInserted(0);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(TodayItemListStateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TodayItemListStateBinding b = holder.b;
        int t = state.type;
        b.progress.setVisibility(t == State.LOADING ? View.VISIBLE : View.GONE);
        b.skeleton.setVisibility(t == State.SKELETON ? View.VISIBLE : View.GONE);
        boolean block = t == State.EMPTY || t == State.ERROR;
        b.icon.setVisibility(block && state.icon != 0 ? View.VISIBLE : View.GONE);
        if (state.icon != 0) {
            b.icon.setImageResource(state.icon);
        }
        b.title.setVisibility(block && state.title != null ? View.VISIBLE : View.GONE);
        b.title.setText(state.title);
        b.desc.setVisibility(block && state.desc != null ? View.VISIBLE : View.GONE);
        b.desc.setText(state.desc);
        b.action.setVisibility(block && state.action != null ? View.VISIBLE : View.GONE);
        b.action.setText(state.action);
        if (t == State.ERROR) {
            b.action.setIconResource(com.zhimu.slmp.base.R.drawable.ic_refresh);
        } else {
            b.action.setIcon(null);
        }
        ClickThrottle.setOnClickListener(b.action, v -> {
            if (onAction != null) {
                onAction.onClick(v);
            }
        });
        b.note.setVisibility(t == State.NOTE ? View.VISIBLE : View.GONE);
        b.note.setText(t == State.NOTE ? state.desc : null);
    }

    @Override
    public int getItemCount() {
        return state.type == State.NONE ? 0 : 1;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TodayItemListStateBinding b;

        VH(@NonNull TodayItemListStateBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
