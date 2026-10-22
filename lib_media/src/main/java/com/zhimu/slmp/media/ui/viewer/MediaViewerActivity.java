package com.zhimu.slmp.media.ui.viewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.databinding.DataBindingUtil;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.ui.InsetsHelper;
import com.zhimu.slmp.media.databinding.MediaActivityViewerBinding;
import com.zhimu.slmp.media.di.MediaInjector;
import com.zhimu.slmp.media.util.MediaFiles;

import java.util.List;

/**
 * I09 照片和视频全屏预览（02 号规范 7.16、10.2 节，模块07 文档 2.7.4）：ViewPager2 左右滑动、图片双指缩放与双击放大、
 * 序号“3 / 6”、两行来源说明、缩略图条、保存到相册（MediaStore，不申请存储权限）。单击画面显示或隐藏顶栏与底部说明。
 * <p>
 * 视频：共用一个 ExoPlayer 挂到当前页的 PlayerView；进入时不自动播放，点中央播放按钮开始；切页暂停；进度条显示已播放与缓冲，
 * 可拖动；静音、倍速（0.75x–2.0x，PlaybackParameters）；出错时服务端文件先调 API-U06 换地址重试一次，仍失败显示
 * “视频加载失败”与“重新加载”。从表单进入时更多菜单有“删除”，关闭时把删除的项回传给网格。打开方式见 {@link MediaViewer}。
 */
@OptIn(markerClass = UnstableApi.class)
public class MediaViewerActivity extends BaseActivity {

    private static final int MENU_DELETE = 1;
    private static final long TICK_MS = 250;

    private MediaActivityViewerBinding binding;
    private MediaViewerViewModel vm;
    private ViewerPageAdapter pageAdapter;
    private ViewerThumbAdapter thumbAdapter;

    @Nullable
    private ExoPlayer player;
    /** 播放器当前加载的页（-1 为未加载） */
    private int playerPosition = -1;
    @Nullable
    private String playerUrl;
    /** 换地址后需要重新播放的页 */
    private int retryPosition = -1;
    private boolean userSeeking;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            ExoPlayer p = player;
            if (p != null && p.isPlaying()) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, com.zhimu.slmp.media.R.layout.media_activity_viewer);
        vm = obtainViewModel(MediaInjector.factory(), MediaViewerViewModel.class);
        binding.setVm(vm);
        binding.setLifecycleOwner(this);
        observeBase(vm);

        InsetsHelper.applyTopPadding(binding.mediaTopBar);
        InsetsHelper.applyBottomPadding(binding.mediaBottomPanel);

        pageAdapter = new ViewerPageAdapter(new ViewerPageAdapter.Callback() {
            @Override
            public void onPageTap() {
                vm.toggleChrome();
            }

            @Override
            public void onLoadFailed(int position) {
                vm.refreshUrl(position);
            }

            @Override
            public void onVideoPlay(int position) {
                playAt(position, true);
            }

            @Override
            public void onVideoReload(int position) {
                releasePlayerSource();
                playAt(position, true);
            }
        });
        binding.mediaPager.setAdapter(pageAdapter);
        binding.mediaPager.setOffscreenPageLimit(1);
        binding.mediaPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                onPageChanged(position);
                vm.selectPage(position);
            }
        });

        thumbAdapter = new ViewerThumbAdapter(position -> binding.mediaPager.setCurrentItem(position, true));
        binding.mediaThumbs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.mediaThumbs.setAdapter(thumbAdapter);

        vm.getItems().observe(this, this::renderItems);
        vm.getCurrentIndex().observe(this, index -> {
            if (index == null) {
                return;
            }
            if (binding.mediaPager.getCurrentItem() != index) {
                binding.mediaPager.setCurrentItem(index, false);
            }
            thumbAdapter.setSelected(index);
            binding.mediaThumbs.scrollToPosition(index);
        });
        vm.getCurrentItem().observe(this, item -> resetControls(item));
        vm.getChromeVisible().observe(this, visible -> {
            boolean show = Boolean.TRUE.equals(visible);
            fade(binding.mediaTopBar, show);
            fade(binding.mediaBottomPanel, show);
        });
        vm.getMuted().observe(this, m -> {
            if (player != null) {
                player.setVolume(Boolean.TRUE.equals(m) ? 0f : 1f);
            }
            binding.mediaMute.setContentDescription(getString(Boolean.TRUE.equals(m)
                    ? com.zhimu.slmp.media.R.string.media_viewer_unmute : com.zhimu.slmp.media.R.string.media_viewer_mute));
        });
        vm.getSpeed().observe(this, s -> {
            if (player != null && s != null) {
                player.setPlaybackParameters(new PlaybackParameters(s));
            }
        });
        vm.getClose().observe(this, v -> finish());

        binding.mediaPlayPause.setOnClickListener(v -> togglePlay());
        binding.mediaSpeed.setOnClickListener(this::showSpeedMenu);
        binding.mediaMore.setOnClickListener(v -> MoreMenuHelper.with(v)
                .addDanger(MENU_DELETE, getString(com.zhimu.slmp.media.R.string.media_viewer_delete), R.drawable.ic_delete)
                .setOnItemClickListener(id -> {
                    if (id == MENU_DELETE) {
                        stopPlayback();
                        vm.deleteCurrent();
                    }
                })
                .show());
        binding.mediaSeek.setMax(1000);
        binding.mediaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = durationMs();
                    binding.mediaTimePosition.setText(ViewerItem.formatDuration((int) (dur * progress / 1000 / 1000)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                int current = binding.mediaPager.getCurrentItem();
                if (player == null || playerPosition != current) {
                    playAt(current, false);
                }
                ExoPlayer p = player;
                long dur = durationMs();
                if (p != null && dur > 0) {
                    p.seekTo(dur * seekBar.getProgress() / 1000);
                }
            }
        });
    }

    @Override
    protected boolean applyWindowInsetsToRoot() {
        // 图片铺满到状态栏和导航栏下方，顶栏与底部说明自行加 insets
        return false;
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (MediaViewerViewModel.ACTION_UNDO_DELETE.equals(actionId)) {
            vm.undoDelete();
        }
    }

    @Override
    protected void onPause() {
        if (player != null) {
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        List<String> deleted = vm == null ? null : vm.getDeletedKeys();
        if (deleted != null && !deleted.isEmpty()) {
            setResult(Activity.RESULT_OK, new Intent().putStringArrayListExtra(MediaViewer.RESULT_DELETED_KEYS,
                    vm.getDeletedKeys()));
        }
        super.finish();
    }

    private void renderItems(@Nullable List<ViewerItem> items) {
        if (items == null) {
            return;
        }
        Integer index = vm.getCurrentIndex().getValue();
        pageAdapter.submitList(items, () -> {
            if (index != null && binding.mediaPager.getCurrentItem() != index) {
                binding.mediaPager.setCurrentItem(index, false);
            }
            if (retryPosition >= 0) {
                int pos = retryPosition;
                retryPosition = -1;
                releasePlayerSource();
                playAt(pos, true);
            }
        });
        thumbAdapter.submit(items);
        if (index != null) {
            thumbAdapter.setSelected(index);
        }
        binding.mediaThumbs.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    }

    // ===== 视频 =====

    @NonNull
    private ExoPlayer ensurePlayer() {
        ExoPlayer p = player;
        if (p == null) {
            p = new ExoPlayer.Builder(this).build();
            p.setVolume(Boolean.TRUE.equals(vm.getMuted().getValue()) ? 0f : 1f);
            Float s = vm.getSpeed().getValue();
            p.setPlaybackParameters(new PlaybackParameters(s == null ? 1f : s));
            p.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    ViewerPageAdapter.Holder h = holderAt(playerPosition);
                    if (h != null) {
                        h.showBuffering(state == Player.STATE_BUFFERING);
                    }
                    if (state == Player.STATE_ENDED) {
                        ExoPlayer pl = player;
                        if (pl != null) {
                            pl.pause();
                            pl.seekTo(0);
                        }
                    }
                    updateProgress();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    binding.mediaPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                    binding.mediaPlayPause.setContentDescription(getString(isPlaying
                            ? com.zhimu.slmp.media.R.string.media_viewer_pause
                            : com.zhimu.slmp.media.R.string.media_viewer_play));
                    handler.removeCallbacks(ticker);
                    if (isPlaying) {
                        handler.post(ticker);
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    int pos = playerPosition;
                    releasePlayerSource();
                    if (vm.refreshUrl(pos)) {
                        retryPosition = pos;
                        return;
                    }
                    ViewerPageAdapter.Holder h = holderAt(pos);
                    if (h != null) {
                        h.showVideoError();
                    }
                }
            });
            player = p;
        }
        return p;
    }

    /** 在 position 页播放（或只准备好以便拖动） */
    private void playAt(int position, boolean play) {
        ViewerItem item = vm.itemAt(position);
        ViewerPageAdapter.Holder h = holderAt(position);
        if (item == null || !item.isVideo() || item.getUrl() == null || h == null) {
            return;
        }
        ExoPlayer p = ensurePlayer();
        if (playerPosition != position || !item.getUrl().equals(playerUrl)) {
            ViewerPageAdapter.Holder old = holderAt(playerPosition);
            if (old != null && old != h) {
                old.showIdle();
            }
            p.setMediaItem(MediaItem.fromUri(MediaFiles.toUri(item.getUrl())));
            p.prepare();
            playerPosition = position;
            playerUrl = item.getUrl();
        }
        h.b.mediaPlayer.setPlayer(p);
        h.showPlaying();
        if (play) {
            p.play();
        }
    }

    private void togglePlay() {
        int current = binding.mediaPager.getCurrentItem();
        ExoPlayer p = player;
        if (p == null || playerPosition != current) {
            playAt(current, true);
        } else if (p.isPlaying()) {
            p.pause();
        } else {
            playAt(current, true);
        }
    }

    /** 切页：暂停视频，旧页恢复封面 */
    private void onPageChanged(int position) {
        if (playerPosition >= 0 && playerPosition != position) {
            stopPlayback();
        }
    }

    private void stopPlayback() {
        ExoPlayer p = player;
        if (p != null) {
            p.pause();
        }
        ViewerPageAdapter.Holder old = holderAt(playerPosition);
        if (old != null) {
            old.showIdle();
        }
        releasePlayerSource();
    }

    private void releasePlayerSource() {
        ExoPlayer p = player;
        if (p != null) {
            p.stop();
            p.clearMediaItems();
        }
        playerPosition = -1;
        playerUrl = null;
    }

    private long durationMs() {
        ExoPlayer p = player;
        if (p != null && playerPosition == binding.mediaPager.getCurrentItem() && p.getDuration() != C.TIME_UNSET) {
            return p.getDuration();
        }
        ViewerItem item = vm.getCurrentItem().getValue();
        return item != null && item.getDurationSec() != null ? item.getDurationSec() * 1000L : 0;
    }

    private void updateProgress() {
        ExoPlayer p = player;
        if (p == null || userSeeking || playerPosition != binding.mediaPager.getCurrentItem()) {
            return;
        }
        long dur = durationMs();
        if (dur <= 0) {
            return;
        }
        binding.mediaSeek.setProgress((int) (p.getCurrentPosition() * 1000 / dur));
        binding.mediaSeek.setSecondaryProgress((int) (p.getBufferedPosition() * 1000 / dur));
        binding.mediaTimePosition.setText(ViewerItem.formatDuration((int) (p.getCurrentPosition() / 1000)));
        binding.mediaTimeDuration.setText(ViewerItem.formatDuration((int) (dur / 1000)));
    }

    /** 当前项变化：重置进度条与时间 */
    private void resetControls(@Nullable ViewerItem item) {
        binding.mediaSeek.setProgress(0);
        binding.mediaSeek.setSecondaryProgress(0);
        binding.mediaTimePosition.setText(ViewerItem.formatDuration(0));
        Integer d = item == null ? null : item.getDurationSec();
        binding.mediaTimeDuration.setText(d == null ? "" : ViewerItem.formatDuration(d));
        binding.mediaPlayPause.setImageResource(R.drawable.ic_play_arrow);
    }

    private void showSpeedMenu(@NonNull View anchor) {
        Float cur = vm.getSpeed().getValue();
        MoreMenuHelper.Builder menu = MoreMenuHelper.with(anchor);
        for (int i = 0; i < MediaViewerViewModel.SPEEDS.length; i++) {
            float s = MediaViewerViewModel.SPEEDS[i];
            boolean selected = cur != null && Math.abs(cur - s) < 0.01f;
            menu.add(i, MediaViewerViewModel.speedLabel(s), selected ? R.drawable.ic_check : 0);
        }
        menu.setOnItemClickListener(id -> {
            if (id >= 0 && id < MediaViewerViewModel.SPEEDS.length) {
                vm.setSpeed(MediaViewerViewModel.SPEEDS[id]);
            }
        }).show();
    }

    @Nullable
    private ViewerPageAdapter.Holder holderAt(int position) {
        if (position < 0) {
            return null;
        }
        View child = binding.mediaPager.getChildAt(0);
        if (!(child instanceof RecyclerView)) {
            return null;
        }
        RecyclerView.ViewHolder vh = ((RecyclerView) child).findViewHolderForAdapterPosition(position);
        return vh instanceof ViewerPageAdapter.Holder ? (ViewerPageAdapter.Holder) vh : null;
    }

    private static void fade(View view, boolean show) {
        view.animate().cancel();
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(150).start();
        } else {
            view.animate().alpha(0f).setDuration(150).withEndAction(() -> view.setVisibility(View.INVISIBLE)).start();
        }
    }
}
