package com.zhimu.slmp.ai.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.databinding.AiItemAttachHintBinding;
import com.zhimu.slmp.ai.databinding.AiItemAttachThumbBinding;
import com.zhimu.slmp.media.upload.UploadItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * K01 输入栏附图缩略图条：64 dp 缩略图（上传进度环、失败覆盖层点按重试、右上角删除）+ 末尾计数文字（随缩略图滚动）。
 */
public class AttachThumbAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onRemove(@NonNull UploadItem item);

        void onRetry(@NonNull UploadItem item);

        void onPreview(@NonNull UploadItem item, int index);
    }

    private static final int TYPE_THUMB = 0;
    private static final int TYPE_HINT = 1;

    private final Listener listener;
    private final List<UploadItem> items = new ArrayList<>();
    @Nullable
    private String hint;

    public AttachThumbAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    /** @return 是否新增了图片（滚动到末尾） */
    public boolean submit(@Nullable List<UploadItem> list, @Nullable String hintText) {
        int before = items.size();
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        boolean changedHint = !Objects.equals(hint, hintText);
        hint = hintText;
        notifyDataSetChanged();
        return items.size() > before || changedHint && items.size() > 0 && before == 0;
    }

    @Override
    public int getItemCount() {
        return items.isEmpty() ? 0 : items.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_THUMB : TYPE_HINT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HINT) {
            return new HintHolder(AiItemAttachHintBinding.inflate(inf, parent, false));
        }
        return new ThumbHolder(AiItemAttachThumbBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HintHolder) {
            ((HintHolder) holder).b.aiAttachHint.setText(hint);
            boolean failed = false;
            for (UploadItem item : items) {
                if (item.isFailed()) {
                    failed = true;
                    break;
                }
            }
            int color = com.google.android.material.color.MaterialColors.getColor(holder.itemView, failed
                    ? androidx.appcompat.R.attr.colorError
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            ((HintHolder) holder).b.aiAttachHint.setTextColor(color);
            return;
        }
        ThumbHolder h = (ThumbHolder) holder;
        UploadItem item = items.get(position);
        String src = item.getLocalPath() != null ? item.getLocalPath()
                : item.getSourceUri() != null ? item.getSourceUri() : item.getThumbUrl();
        Glide.with(h.b.aiThumbImage).load(src).centerCrop().into(h.b.aiThumbImage);
        boolean failed = item.isFailed();
        boolean progress = item.isInProgress();
        h.b.aiThumbFailed.setVisibility(failed ? View.VISIBLE : View.GONE);
        h.b.aiThumbMask.setVisibility(progress ? View.VISIBLE : View.GONE);
        if (progress) {
            int p = item.getProgress();
            h.b.aiThumbProgress.setIndeterminate(p <= 0);
            if (p > 0) {
                h.b.aiThumbProgress.setProgressCompat(p, true);
            }
        }
        h.b.aiThumbRemove.setOnClickListener(v -> listener.onRemove(item));
        h.b.getRoot().setOnClickListener(v -> {
            if (item.isFailed()) {
                listener.onRetry(item);
            } else {
                listener.onPreview(item, h.getBindingAdapterPosition());
            }
        });
    }

    static final class ThumbHolder extends RecyclerView.ViewHolder {
        final AiItemAttachThumbBinding b;

        ThumbHolder(@NonNull AiItemAttachThumbBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class HintHolder extends RecyclerView.ViewHolder {
        final AiItemAttachHintBinding b;

        HintHolder(@NonNull AiItemAttachHintBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
