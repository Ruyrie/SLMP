package com.zhimu.slmp.farm.ui.member;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.farm.databinding.FarmItemSectionBinding;

import java.util.Objects;

/**
 * ConcatAdapter 中的分组标题段（0 或 1 项）：标题 + 可选说明（空状态、只有所有者一人的提示）。
 */
public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.Holder> {

    private boolean visible = true;
    @Nullable
    private CharSequence title;
    @Nullable
    private CharSequence message;

    public void setVisible(boolean v) {
        if (visible == v) {
            return;
        }
        visible = v;
        if (v) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    public void setTitle(@Nullable CharSequence t) {
        if (!Objects.equals(title, t)) {
            title = t;
            changed();
        }
    }

    public void setMessage(@Nullable CharSequence m) {
        if (!Objects.equals(message, m)) {
            message = m;
            changed();
        }
    }

    private void changed() {
        if (visible) {
            notifyItemChanged(0);
        }
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FarmItemSectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.binding.sectionTitle.setText(title);
        holder.binding.sectionMessage.setText(message);
        holder.binding.sectionMessage.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FarmItemSectionBinding binding;

        Holder(@NonNull FarmItemSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
