package com.zhimu.slmp.media.ui.viewer;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.zhimu.slmp.media.databinding.MediaItemViewerPageBinding;
import com.zhimu.slmp.media.util.MediaGlide;

import java.util.Objects;

/**
 * I09 的 ViewPager2 页面：图片用 ZoomableImageView（加载最长 2048 像素，放大后仍清晰）；
 * 视频项显示封面与中央播放按钮，播放时由页面把共用的 ExoPlayer 挂到本页的 PlayerView。
 */
final class ViewerPageAdapter extends ListAdapter<ViewerItem, ViewerPageAdapter.Holder> {

    /** 加载的最大边长 */
    private static final int MAX_SIZE = 2048;

    interface Callback {
        /** 单击画面：显示或隐藏顶栏与底部说明 */
        void onPageTap();

        /** 图片加载失败（可调 API-U06 换地址） */
        void onLoadFailed(int position);

        /** 点视频中央的播放按钮 */
        void onVideoPlay(int position);

        /** 视频加载失败后点“重新加载” */
        void onVideoReload(int position);
    }

    private final Callback callback;

    ViewerPageAdapter(@NonNull Callback callback) {
        super(DIFF);
        this.callback = callback;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MediaItemViewerPageBinding b = MediaItemViewerPageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.b.mediaImage).clear(holder.b.mediaImage);
        holder.b.mediaPlayer.setPlayer(null);
        super.onViewRecycled(holder);
    }

    final class Holder extends RecyclerView.ViewHolder {
        final MediaItemViewerPageBinding b;
        boolean video;

        Holder(@NonNull MediaItemViewerPageBinding b) {
            super(b.getRoot());
            this.b = b;
            b.mediaImage.setOnClickListener(v -> callback.onPageTap());
            b.mediaPlayer.setOnClickListener(v -> callback.onPageTap());
            b.mediaVideoPlay.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    callback.onVideoPlay(pos);
                }
            });
            b.mediaVideoReload.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    callback.onVideoReload(pos);
                }
            });
        }

        void bind(@NonNull ViewerItem item) {
            video = item.isVideo();
            b.mediaError.setVisibility(View.GONE);
            b.mediaVideoError.setVisibility(View.GONE);
            b.mediaPlayer.setVisibility(View.GONE);
            b.mediaPlayer.setPlayer(null);
            b.mediaVideoPlay.setVisibility(video ? View.VISIBLE : View.GONE);
            b.mediaImage.setVisibility(View.VISIBLE);
            b.mediaLoading.setVisibility(video ? View.GONE : View.VISIBLE);
            Object model = video ? videoCoverModel(item) : MediaGlide.model(item.getUrl(), item.getFileId());
            if (model == null) {
                Glide.with(b.mediaImage).clear(b.mediaImage);
                b.mediaLoading.setVisibility(View.GONE);
                b.mediaError.setVisibility(video ? View.GONE : View.VISIBLE);
                return;
            }
            Glide.with(b.mediaImage)
                    .load(model)
                    .override(MAX_SIZE, MAX_SIZE)
                    .downsample(DownsampleStrategy.CENTER_INSIDE)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object m,
                                                    @NonNull Target<Drawable> target, boolean isFirstResource) {
                            b.mediaLoading.setVisibility(View.GONE);
                            if (!video) {
                                b.mediaError.setVisibility(View.VISIBLE);
                                int pos = getBindingAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    callback.onLoadFailed(pos);
                                }
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object m,
                                                       Target<Drawable> target, @NonNull DataSource dataSource,
                                                       boolean isFirstResource) {
                            b.mediaLoading.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(b.mediaImage);
        }

        /** 视频开始播放：隐藏封面与播放按钮，显示 PlayerView */
        void showPlaying() {
            b.mediaVideoError.setVisibility(View.GONE);
            b.mediaVideoPlay.setVisibility(View.GONE);
            b.mediaPlayer.setVisibility(View.VISIBLE);
            b.mediaImage.setVisibility(View.INVISIBLE);
        }

        /** 切走或停止：恢复封面与播放按钮 */
        void showIdle() {
            b.mediaPlayer.setPlayer(null);
            b.mediaPlayer.setVisibility(View.GONE);
            b.mediaImage.setVisibility(View.VISIBLE);
            b.mediaLoading.setVisibility(View.GONE);
            b.mediaVideoPlay.setVisibility(video ? View.VISIBLE : View.GONE);
        }

        void showBuffering(boolean buffering) {
            b.mediaLoading.setVisibility(buffering ? View.VISIBLE : View.GONE);
        }

        /** 视频加载失败：居中“视频加载失败”+“重新加载” */
        void showVideoError() {
            showIdle();
            b.mediaVideoPlay.setVisibility(View.GONE);
            b.mediaVideoError.setVisibility(View.VISIBLE);
        }
    }

    /** 视频封面：优先缩略图（服务端封面或本地封面 JPEG），其次让 Glide 从本地视频取帧 */
    @Nullable
    static Object videoCoverModel(@NonNull ViewerItem item) {
        if (item.getThumbUrl() != null) {
            return MediaGlide.thumbModel(item.getThumbUrl(), item.getFileId());
        }
        String url = item.getUrl();
        if (url != null && !MediaGlide.isRemoteUrl(url)) {
            return MediaGlide.model(url, 0);
        }
        return null;
    }

    private static final DiffUtil.ItemCallback<ViewerItem> DIFF = new DiffUtil.ItemCallback<ViewerItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ViewerItem a, @NonNull ViewerItem b) {
            if (a.getKey() != null || b.getKey() != null) {
                return Objects.equals(a.getKey(), b.getKey());
            }
            if (a.getFileId() > 0 || b.getFileId() > 0) {
                return a.getFileId() == b.getFileId();
            }
            return Objects.equals(a.getUrl(), b.getUrl());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ViewerItem a, @NonNull ViewerItem b) {
            return Objects.equals(a.getUrl(), b.getUrl()) && Objects.equals(a.getThumbUrl(), b.getThumbUrl());
        }
    };
}
