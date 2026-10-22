package com.zhimu.slmp.media.ui.viewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.media.databinding.MediaItemViewerThumbBinding;
import com.zhimu.slmp.media.util.MediaGlide;

import java.util.ArrayList;
import java.util.List;

/**
 * I09 底部缩略图条：点按切换到对应项，当前项白色描边。
 */
final class ViewerThumbAdapter extends RecyclerView.Adapter<ViewerThumbAdapter.Holder> {

    interface OnThumbClick {
        void onThumbClick(int position);
    }

    private final OnThumbClick listener;
    private final List<ViewerItem> items = new ArrayList<>();
    private int selected;

    ViewerThumbAdapter(@NonNull OnThumbClick listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<ViewerItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    void setSelected(int position) {
        if (position == selected) {
            return;
        }
        int old = selected;
        selected = position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(MediaItemViewerThumbBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ViewerItem item = items.get(position);
        holder.b.getRoot().setSelected(position == selected);
        holder.b.mediaThumbVideo.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        Object model = item.getThumbUrl() != null
                ? MediaGlide.thumbModel(item.getThumbUrl(), item.getFileId())
                : MediaGlide.model(item.getUrl(), item.getFileId());
        Glide.with(holder.b.mediaThumb).load(model).centerCrop().into(holder.b.mediaThumb);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        final MediaItemViewerThumbBinding b;

        Holder(@NonNull MediaItemViewerThumbBinding b) {
            super(b.getRoot());
            this.b = b;
            b.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onThumbClick(pos);
                }
            });
        }
    }
}
