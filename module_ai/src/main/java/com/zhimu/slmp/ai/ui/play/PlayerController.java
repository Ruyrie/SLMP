package com.zhimu.slmp.ai.ui.play;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiViewPlayerControlsBinding;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayCommand;
import com.zhimu.slmp.ai.model.PlayerState;
import com.zhimu.slmp.ai.util.AiText;

/**
 * ExoPlayer 与自定义控制层（K05/K06、K03 内嵌视频共用，技术文档 2.6.7）：只在主线程使用，由页面在 onStart 创建、onStop 释放。
 * <ul>
 *     <li>HLS（media3-exoplayer-hls）；两档清晰度按播放列表切换，同一个多码率列表时用最大视频尺寸限制；倍速用播放参数；</li>
 *     <li>每秒读取位置回调 {@link Callback#onTick}（ExoPlayer 只能在主线程访问）；</li>
 *     <li>控制层 3 秒无操作隐藏（暂停时不隐藏）；±10 秒连点累加，600 ms 后按目标位置报 SEEK；拖动只更新时间，松手 seekTo；</li>
 *     <li>网络类错误为 NETWORK_LOST，其余为 ERROR。</li>
 * </ul>
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerController {

    public interface Callback {
        void onTick(long positionMs, long bufferedMs, boolean playing, float speed);

        void onState(@NonNull PlayerState state);

        void onUserPause();

        void onSeekFinished(long positionMs);

        /** 控制层按钮（返回、全屏、画中画、更多、锁屏、下一节、胶囊、提示按钮） */
        void onControl(int viewId);
    }

    private static final long TICK_MS = 1000;
    private static final long HIDE_MS = 3000;
    private static final long SEEK_SETTLE_MS = 600;

    private final Context context;
    private final PlayerView playerView;
    private final AiViewPlayerControlsBinding c;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private ExoPlayer player;
    @Nullable
    private PlayCommand lastCommand;
    private float speed = 1f;
    private boolean dragging;
    private boolean controlsShown = true;
    private boolean locked;
    private boolean fullscreen;
    private boolean embedded;
    private long pendingSeekMs;
    private int seekSteps;
    private boolean ended;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            ExoPlayer p = player;
            if (p == null) {
                return;
            }
            long pos = p.getCurrentPosition();
            long buf = p.getBufferedPosition();
            callback.onTick(pos, buf, p.isPlaying(), speed);
            updateProgress(pos, buf, p.getDuration());
            handler.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable hide = () -> setControlsShown(false);
    private final Runnable settleSeek = this::finishStepSeek;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) {
                ended = false;
                callback.onState(PlayerState.BUFFERING);
                c.aiCtrlBuffering.setVisibility(View.VISIBLE);
                c.aiCtrlPlay.setVisibility(View.INVISIBLE);
            } else {
                c.aiCtrlBuffering.setVisibility(View.GONE);
                c.aiCtrlPlay.setVisibility(View.VISIBLE);
            }
            if (state == Player.STATE_ENDED) {
                ended = true;
                if (player != null) {
                    callback.onTick(Math.max(0, player.getCurrentPosition()),
                            player.getBufferedPosition(), false, player.getPlaybackParameters().speed);
                }
                callback.onState(PlayerState.ENDED);
                setControlsShown(true);
            } else if (state == Player.STATE_READY && player != null && !player.getPlayWhenReady()) {
                callback.onState(PlayerState.PAUSED);
            }
            renderPlayButton();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                ended = false;
                callback.onState(PlayerState.PLAYING);
                scheduleHide();
            } else if (player != null && player.getPlaybackState() == Player.STATE_READY) {
                callback.onState(PlayerState.PAUSED);
                handler.removeCallbacks(hide);
            }
            renderPlayButton();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            int code = error.errorCode;
            boolean network = code >= PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    && code <= PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
            callback.onState(network ? PlayerState.NETWORK_LOST : PlayerState.ERROR);
            setControlsShown(true);
        }
    };

    public PlayerController(@NonNull PlayerView playerView, @NonNull AiViewPlayerControlsBinding controls,
                            @NonNull Callback callback) {
        this.context = playerView.getContext();
        this.playerView = playerView;
        this.c = controls;
        this.callback = callback;
        setup();
    }

    private void setup() {
        View.OnClickListener control = v -> {
            touch();
            callback.onControl(v.getId());
        };
        c.aiCtrlBack.setOnClickListener(control);
        c.aiCtrlPip.setOnClickListener(control);
        c.aiCtrlMore.setOnClickListener(control);
        c.aiCtrlFullscreen.setOnClickListener(control);
        c.aiCtrlExitFullscreen.setOnClickListener(control);
        c.aiCtrlLock.setOnClickListener(control);
        c.aiCtrlNext.setOnClickListener(control);
        c.aiCtrlSpeedPill.setOnClickListener(control);
        c.aiCtrlQualityPill.setOnClickListener(control);
        c.aiCtrlEpisodesPill.setOnClickListener(control);
        c.aiCtrlMessageAction1.setOnClickListener(control);
        c.aiCtrlMessageAction2.setOnClickListener(control);
        c.aiCtrlMessage.setOnClickListener(control);
        c.aiCtrlPlay.setOnClickListener(v -> togglePlay());
        c.aiCtrlPlaySmall.setOnClickListener(v -> togglePlay());
        c.aiCtrlRewind.setOnClickListener(v -> stepSeek(-10_000));
        c.aiCtrlForward.setOnClickListener(v -> stepSeek(10_000));
        c.aiCtrlRoot.setOnClickListener(v -> onSurfaceTap());
        c.aiCtrlLayer.setOnClickListener(v -> onSurfaceTap());
        c.aiCtrlSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    updateTime(progress * 1000L, player.getDuration());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (locked) {
                    return;
                }
                dragging = true;
                handler.removeCallbacks(hide);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                dragging = false;
                if (player != null && !locked) {
                    long target = seekBar.getProgress() * 1000L;
                    player.seekTo(target);
                    callback.onSeekFinished(target);
                }
                scheduleHide();
            }
        });
        renderMode();
    }

    // ===== 播放器 =====

    public void create() {
        if (player != null) {
            return;
        }
        ExoPlayer p = new ExoPlayer.Builder(context).build();
        p.addListener(listener);
        playerView.setPlayer(p);
        player = p;
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (lastCommand != null) {
            PlayCommand cmd = lastCommand;
            lastCommand = new PlayCommand(cmd.getHlsUrl(), cmd.getQuality(), position(cmd.getStartMs()),
                    cmd.isPlayWhenReady());
            apply(lastCommand);
        }
    }

    /** 释放（记下当前位置，重新创建时从这里继续） */
    public void release() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(hide);
        handler.removeCallbacks(settleSeek);
        ExoPlayer p = player;
        if (p == null) {
            return;
        }
        if (lastCommand != null) {
            lastCommand = new PlayCommand(lastCommand.getHlsUrl(), lastCommand.getQuality(), p.getCurrentPosition(),
                    p.getPlayWhenReady());
        }
        p.removeListener(listener);
        playerView.setPlayer(null);
        p.release();
        player = null;
    }

    /** 换媒体项并从 startMs 开始 */
    public void play(@NonNull PlayCommand cmd) {
        lastCommand = cmd;
        if (player != null) {
            apply(cmd);
        }
    }

    /** Forget an old/withdrawn source before a fresh permission/source lookup. */
    public void clearPlayback() {
        lastCommand = null;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
    }

    public boolean playWhenReady() {
        return player != null ? player.getPlayWhenReady() : lastCommand != null && lastCommand.isPlayWhenReady();
    }

    private void apply(@NonNull PlayCommand cmd) {
        ExoPlayer p = player;
        if (p == null) {
            return;
        }
        ended = false;
        boolean p480 = AiCodes.QUALITY_480.equals(cmd.getQuality());
        p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                .setMaxVideoSize(p480 ? 854 : 1280, p480 ? 480 : 720).build());
        MediaItem item = new MediaItem.Builder().setUri(cmd.getHlsUrl()).setMimeType(MimeTypes.APPLICATION_M3U8).build();
        p.setMediaItem(item, Math.max(0, cmd.getStartMs()));
        p.setPlaybackParameters(new PlaybackParameters(speed));
        p.setPlayWhenReady(cmd.isPlayWhenReady());
        p.prepare();
        hideMessage();
    }

    /** 网络恢复、点按重试 */
    public void retry() {
        ExoPlayer p = player;
        if (p != null) {
            hideMessage();
            p.prepare();
            p.setPlayWhenReady(true);
        }
    }

    public void setSpeed(float s) {
        speed = s;
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(s));
        }
    }

    public void seekTo(long ms) {
        if (player != null) {
            player.seekTo(ms);
        }
    }

    public void pause() {
        if (player != null && player.getPlayWhenReady()) {
            player.pause();
            callback.onUserPause();
        }
    }

    /** 继续播放（K03 从全屏返回后原位接着播） */
    public void resume() {
        if (player != null && !locked) {
            player.play();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long position() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    private long position(long fallback) {
        return player == null ? fallback : Math.max(fallback, 0);
    }

    private void togglePlay() {
        ExoPlayer p = player;
        if (p == null || locked) {
            return;
        }
        touch();
        if (ended) {
            p.seekTo(0);
            p.play();
            return;
        }
        if (p.getPlayWhenReady()) {
            p.pause();
            callback.onUserPause();
        } else {
            p.play();
        }
    }

    private void stepSeek(long delta) {
        ExoPlayer p = player;
        if (p == null || locked) {
            return;
        }
        touch();
        long duration = Math.max(0, p.getDuration());
        long target = Math.max(0, Math.min(duration > 0 ? duration : Long.MAX_VALUE, p.getCurrentPosition() + delta));
        p.seekTo(target);
        pendingSeekMs = target;
        seekSteps += delta > 0 ? 1 : -1;
        int seconds = seekSteps * 10;
        c.aiCtrlSeekHint.setText(seconds > 0 ? "+" + seconds + " 秒" : seconds + " 秒");
        c.aiCtrlSeekHint.setVisibility(View.VISIBLE);
        handler.removeCallbacks(settleSeek);
        handler.postDelayed(settleSeek, SEEK_SETTLE_MS);
    }

    private void finishStepSeek() {
        seekSteps = 0;
        c.aiCtrlSeekHint.setVisibility(View.GONE);
        callback.onSeekFinished(pendingSeekMs);
    }

    // ===== 控制层 =====

    public void setFullscreen(boolean on) {
        fullscreen = on;
        renderMode();
    }

    public void setLocked(boolean on) {
        locked = on;
        c.aiCtrlLock.setImageResource(on ? com.zhimu.slmp.base.R.drawable.ic_lock_filled
                : com.zhimu.slmp.base.R.drawable.ic_lock_open);
        c.aiCtrlLock.setContentDescription(context.getString(on ? R.string.ai_play_unlock : R.string.ai_play_lock));
        renderMode();
        touch();
    }

    /** 内嵌（K03）：没有返回、画中画与更多 */
    public void setEmbedded() {
        embedded = true;
        c.aiCtrlBack.setVisibility(View.GONE);
        renderMode();
    }

    public void setTitle(@Nullable CharSequence title) {
        c.aiCtrlTitle.setText(title);
    }

    public void setPills(@NonNull String speedText, @NonNull String quality, boolean hasEpisodes, boolean hasNext) {
        c.aiCtrlSpeedPill.setText(speedText);
        c.aiCtrlQualityPill.setText(AiCodes.QUALITY_480.equals(quality) ? "480P" : "720P");
        c.aiCtrlEpisodesPill.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        c.aiCtrlNext.setVisibility(hasNext ? View.VISIBLE : View.GONE);
    }

    private void renderMode() {
        c.aiCtrlPortraitRow.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        c.aiCtrlLandRow.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        c.aiCtrlTitle.setVisibility(fullscreen ? View.VISIBLE : View.INVISIBLE);
        c.aiCtrlLock.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        c.aiCtrlPip.setVisibility(embedded || fullscreen ? View.GONE : View.VISIBLE);
        c.aiCtrlMore.setVisibility(embedded || fullscreen ? View.GONE : View.VISIBLE);
        float d = context.getResources().getDisplayMetrics().density;
        int side = Math.round((fullscreen ? 40 : 16) * d);
        c.aiCtrlBottom.setPadding(side, 0, side, Math.round((fullscreen ? 12 : 4) * d));
        // K06：arrow_back 图标 x 44、y 24，标题 x 84
        c.aiCtrlTop.setPadding(Math.round((fullscreen ? 32 : 4) * d), 0, Math.round((fullscreen ? 32 : 4) * d), 0);
        ViewGroup.MarginLayoutParams top = (ViewGroup.MarginLayoutParams) c.aiCtrlTop.getLayoutParams();
        top.topMargin = Math.round((fullscreen ? 12 : 4) * d);
        c.aiCtrlTop.setLayoutParams(top);
        // 锁定：只留锁按钮
        int others = locked ? View.INVISIBLE : View.VISIBLE;
        c.aiCtrlTop.setVisibility(others);
        c.aiCtrlCenter.setVisibility(others);
        c.aiCtrlBottom.setVisibility(others);
        c.aiCtrlLayer.setBackgroundResource(locked ? 0 : R.drawable.ai_bg_scrim_32);
    }

    private void onSurfaceTap() {
        if (c.aiCtrlPanel.getVisibility() == View.VISIBLE) {
            c.aiCtrlPanel.setVisibility(View.GONE);
            return;
        }
        setControlsShown(!controlsShown);
    }

    private void touch() {
        setControlsShown(true);
    }

    public void setControlsShown(boolean shown) {
        controlsShown = shown;
        View layer = c.aiCtrlLayer;
        layer.animate().cancel();
        if (shown) {
            layer.setVisibility(View.VISIBLE);
            layer.animate().alpha(1f).setDuration(200).start();
            scheduleHide();
        } else {
            layer.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                if (!controlsShown) {
                    layer.setVisibility(View.INVISIBLE);
                }
            }).start();
            handler.removeCallbacks(hide);
        }
    }

    private void scheduleHide() {
        handler.removeCallbacks(hide);
        if (controlsShown && (isPlaying() || locked) && !dragging) {
            handler.postDelayed(hide, HIDE_MS);
        }
    }

    private void renderPlayButton() {
        boolean playing = player != null && player.getPlayWhenReady() && !ended;
        c.aiCtrlPlay.setImageResource(playing ? com.zhimu.slmp.base.R.drawable.ic_pause_filled
                : com.zhimu.slmp.base.R.drawable.ic_play_arrow_filled);
        c.aiCtrlPlaySmall.setImageResource(playing ? com.zhimu.slmp.base.R.drawable.ic_pause
                : com.zhimu.slmp.base.R.drawable.ic_play_arrow);
    }

    private void updateProgress(long pos, long buffered, long duration) {
        if (duration <= 0) {
            return;
        }
        int max = (int) (duration / 1000);
        if (c.aiCtrlSeek.getMax() != max) {
            c.aiCtrlSeek.setMax(max);
        }
        if (!dragging) {
            c.aiCtrlSeek.setProgress((int) (pos / 1000));
            updateTime(pos, duration);
        }
        c.aiCtrlSeek.setSecondaryProgress((int) (buffered / 1000));
    }

    private void updateTime(long pos, long duration) {
        String text = AiText.duration((int) (pos / 1000)) + " / " + AiText.duration((int) (Math.max(0, duration) / 1000));
        c.aiCtrlTime.setText(text);
        c.aiCtrlTimeLand.setText(text);
    }

    // ===== 中央提示 =====

    /** 显示提示：text 为空时隐藏；action 文案为空的按钮不显示 */
    public void showMessage(@Nullable CharSequence text, int iconRes, @Nullable CharSequence action1,
                            @Nullable CharSequence action2) {
        if (text == null && action1 == null) {
            hideMessage();
            return;
        }
        c.aiCtrlMessage.setVisibility(View.VISIBLE);
        c.aiCtrlMessageText.setText(text);
        c.aiCtrlMessageText.setVisibility(text == null ? View.GONE : View.VISIBLE);
        c.aiCtrlMessageIcon.setVisibility(iconRes == 0 ? View.GONE : View.VISIBLE);
        if (iconRes != 0) {
            c.aiCtrlMessageIcon.setImageResource(iconRes);
        }
        c.aiCtrlMessageAction1.setText(action1);
        c.aiCtrlMessageAction1.setVisibility(action1 == null ? View.GONE : View.VISIBLE);
        c.aiCtrlMessageAction2.setText(action2);
        c.aiCtrlMessageAction2.setVisibility(action2 == null ? View.GONE : View.VISIBLE);
    }

    public void hideMessage() {
        c.aiCtrlMessage.setVisibility(View.GONE);
    }

    @NonNull
    public AiViewPlayerControlsBinding getBinding() {
        return c;
    }
}
