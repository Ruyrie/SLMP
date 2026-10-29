package com.zhimu.slmp.media.picker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.media.R;
import com.zhimu.slmp.media.databinding.MediaItemGridAddBinding;
import com.zhimu.slmp.media.databinding.MediaItemGridMediaBinding;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.util.MediaGlide;

import java.util.List;
import java.util.Objects;

/**
 * 照片网格适配器：缩略图格（已上传 / 上传中 / 失败 / 标签）与最后的“添加”格；只更新进度时局部刷新。
 */
final class MediaGridAdapter extends ListAdapter<MediaGridAdapter.Cell, RecyclerView.ViewHolder> {

    private static final int TYPE_MEDIA = 0;
    private static final int TYPE_ADD = 1;
    private static final Object PAYLOAD_PROGRESS = new Object();

    interface Callback {
        void onAddClick();

        void onItemClick(@NonNull UploadItem item);

        void onRetryClick(@NonNull UploadItem item);

        void onDeleteClick(@NonNull UploadItem item);
    }

    /** 网格中的一格：照片或“添加” */
    static final class Cell {
        @Nullable
        final UploadItem item;
        @Nullable
        final String addCount;
        final boolean readOnly;

        private Cell(@Nullable UploadItem item, @Nullable String addCount, boolean readOnly) {
            this.item = item;
            this.addCount = addCount;
            this.readOnly = readOnly;
        }

        static Cell media(@NonNull UploadItem item, boolean readOnly) {
            return new Cell(item, null, readOnly);
        }

        static Cell add(@Nullable String count) {
            return new Cell(null, count, false);
        }
    }

    private final Callback callback;
    @DrawableRes
    private int addIcon = com.zhimu.slmp.base.R.drawable.ic_add_a_photo;

    MediaGridAdapter(@NonNull Callback callback) {
        super(DIFF);
        this.callback = callback;
    }

    void setAddIcon(@DrawableRes int icon) {
        this.addIcon = icon;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).item == null ? TYPE_ADD : TYPE_MEDIA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            return new AddHolder(MediaItemGridAddBinding.inflate(inflater, parent, false));
        }
        return new MediaHolder(MediaItemGridMediaBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Cell cell = getItem(position);
        if (holder instanceof AddHolder) {
            ((AddHolder) holder).bind(cell);
        } else {
            ((MediaHolder) holder).bind(cell, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (holder instanceof MediaHolder && payloads.contains(PAYLOAD_PROGRESS)) {
            Cell cell = getItem(position);
            if (cell.item != null) {
                ((MediaHolder) holder).bindProgress(cell.item);
                return;
            }
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof MediaHolder) {
            MediaHolder h = (MediaHolder) holder;
            MediaGlide.clear(h.b.mediaThumb);
        }
        super.onViewRecycled(holder);
    }

    // ===== ViewHolder =====

    final class AddHolder extends RecyclerView.ViewHolder {
        final MediaItemGridAddBinding b;

        AddHolder(@NonNull MediaItemGridAddBinding b) {
            super(b.getRoot());
            this.b = b;
            ClickThrottle.setOnClickListener(b.getRoot(), v -> callback.onAddClick());
        }

        void bind(@NonNull Cell cell) {
            b.mediaAddIcon.setImageResource(addIcon);
            b.mediaAddCount.setText(cell.addCount);
            b.mediaAddCount.setVisibility(cell.addCount == null ? View.GONE : View.VISIBLE);
            b.getRoot().setContentDescription(cell.addCount == null
                    ? b.mediaAddText.getText() : b.mediaAddText.getText() + " " + cell.addCount);
        }
    }

    final class MediaHolder extends RecyclerView.ViewHolder {
        final MediaItemGridMediaBinding b;
        @Nullable
        private UploadItem item;

        MediaHolder(@NonNull MediaItemGridMediaBinding b) {
            super(b.getRoot());
            this.b = b;
            ClickThrottle.setOnClickListener(b.getRoot(), v -> {
                UploadItem it = item;
                if (it == null) {
                    return;
                }
                if (it.isFailed()) {
                    callback.onRetryClick(it);
                } else {
                    callback.onItemClick(it);
                }
            });
            ClickThrottle.setOnClickListener(b.mediaDelete, v -> {
                if (item != null) {
                    callback.onDeleteClick(item);
                }
            });
        }

        void bind(@NonNull Cell cell, int position) {
            UploadItem it = Objects.requireNonNull(cell.item);
            item = it;
            Glide.with(b.mediaThumb)
                    .load(MediaGlide.gridModel(it))
                    .centerCrop()
                    .into(b.mediaThumb);
            b.mediaPlay.setVisibility(it.isVideo() && !it.isFailed() && !it.isInProgress() ? View.VISIBLE : View.GONE);
            Integer duration = it.getDurationSec();
            if (it.isVideo() && duration != null && !it.isFailed()) {
                b.mediaDuration.setText(com.zhimu.slmp.media.ui.viewer.ViewerItem.formatDuration(duration));
                b.mediaDuration.setVisibility(View.VISIBLE);
            } else {
                b.mediaDuration.setVisibility(View.GONE);
            }
            b.mediaDelete.setVisibility(cell.readOnly ? View.GONE : View.VISIBLE);
            if (it.getTagText() != null) {
                b.mediaTag.setStatus(it.getTagText(), it.getTagType());
                b.mediaTag.setVisibility(View.VISIBLE);
            } else {
                b.mediaTag.setVisibility(View.GONE);
            }
            b.getRoot().setContentDescription(it.isVideo() && duration != null
                    ? b.getRoot().getContext().getString(R.string.media_picker_video_thumb, position + 1,
                    com.zhimu.slmp.media.ui.viewer.ViewerItem.formatDuration(duration))
                    : b.getRoot().getContext().getString(R.string.media_picker_thumb, position + 1));
            bindProgress(it);
        }

        void bindProgress(@NonNull UploadItem it) {
            item = it;
            b.mediaFailed.setVisibility(it.isFailed() ? View.VISIBLE : View.GONE);
            boolean busy = it.isInProgress();
            b.mediaMask.setVisibility(busy ? View.VISIBLE : View.GONE);
            if (!busy) {
                return;
            }
            // 上传中、视频转码中（带进度）显示百分比；图片压缩无进度，显示不确定的环
            boolean determinate = (it.getStatus() == UploadItem.Status.UPLOADING
                    || it.getStatus() == UploadItem.Status.COMPRESSING) && it.getProgress() > 0;
            if (determinate) {
                if (b.mediaProgress.isIndeterminate()) {
                    b.mediaProgress.setVisibility(View.INVISIBLE);
                    b.mediaProgress.setIndeterminate(false);
                    b.mediaProgress.setVisibility(View.VISIBLE);
                }
                b.mediaProgress.setProgressCompat(it.getProgress(), true);
                b.mediaPercent.setText(b.mediaPercent.getContext().getString(R.string.media_picker_percent, it.getProgress()));
                b.mediaPercent.setVisibility(View.VISIBLE);
            } else {
                if (!b.mediaProgress.isIndeterminate()) {
                    b.mediaProgress.setVisibility(View.INVISIBLE);
                    b.mediaProgress.setIndeterminate(true);
                    b.mediaProgress.setVisibility(View.VISIBLE);
                }
                b.mediaPercent.setVisibility(View.GONE);
            }
        }
    }

    private static final DiffUtil.ItemCallback<Cell> DIFF = new DiffUtil.ItemCallback<Cell>() {
        @Override
        public boolean areItemsTheSame(@NonNull Cell a, @NonNull Cell b) {
            if (a.item == null || b.item == null) {
                return a.item == null && b.item == null;
            }
            return a.item.getId().equals(b.item.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Cell a, @NonNull Cell b) {
            return Objects.equals(a.item, b.item) && Objects.equals(a.addCount, b.addCount)
                    && a.readOnly == b.readOnly;
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Cell a, @NonNull Cell b) {
            if (a.item != null && b.item != null && a.readOnly == b.readOnly
                    && a.item.getStatus() == b.item.getStatus()
                    && Objects.equals(a.item.getLocalPath(), b.item.getLocalPath())
                    && Objects.equals(a.item.getThumbUrl(), b.item.getThumbUrl())
                    && Objects.equals(a.item.getDurationSec(), b.item.getDurationSec())
                    && Objects.equals(a.item.getTagText(), b.item.getTagText())
                    && a.item.getFileId() == b.item.getFileId()) {
                return PAYLOAD_PROGRESS;
            }
            return null;
        }
    };
}
