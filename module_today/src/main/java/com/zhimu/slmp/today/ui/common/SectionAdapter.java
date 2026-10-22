package com.zhimu.slmp.today.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.today.databinding.TodayItemSectionBinding;

import java.util.Objects;

/**
 * 列表内单个分组标题（“10月19日 周一 · 今天 / 17 项”“已逾期 · 1 项”），隐藏时不占位。
 */
public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.VH> {

    @Nullable
    private CharSequence title;
    @Nullable
    private CharSequence count;
    private boolean visible = true;

    public void set(@Nullable CharSequence newTitle, @Nullable CharSequence newCount) {
        boolean changed = !Objects.equals(str(title), str(newTitle)) || !Objects.equals(str(count), str(newCount));
        title = newTitle;
        count = newCount;
        if (changed && visible) {
            notifyItemChanged(0);
        }
    }

    public void setVisible(boolean show) {
        if (visible == show) {
            return;
        }
        visible = show;
        if (show) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    @Nullable
    private static String str(@Nullable CharSequence c) {
        return c == null ? null : c.toString();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(TodayItemSectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.b.title.setText(title);
        holder.b.count.setText(count);
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TodayItemSectionBinding b;

        VH(@NonNull TodayItemSectionBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
