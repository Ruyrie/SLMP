package com.zhimu.slmp.ai.ui.play;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiActivityCoursePlayBinding;
import com.zhimu.slmp.ai.databinding.AiViewPlayInfoBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayerState;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.model.vo.VideoSourceVO;
import com.zhimu.slmp.ai.ui.common.SingleViewAdapter;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * K05 视频播放 · 竖屏 / K06 全屏播放 · 横屏（同一个 Activity 两套控制层布局，旋转不重建、播放不中断）：
 * 续播 Snackbar“回到开头”、移动网络 Snackbar“仅 Wi-Fi”、倍速与清晰度（竖屏底部面板 / 横屏右侧面板）、选集、锁屏、
 * 画中画（播放中自动进入）、本专题课程列表、问 AI / 图文原文 / 本专题。onStop 时不在画中画则报 LEAVE 并释放播放器。
 */
public class CoursePlayActivity extends BaseActivity {

    private static final String ACTION_WIFI_ONLY = "ai_wifi_only";
    private static final String ACTION_BACK_TO_START = "ai_back_to_start";
    private static final int PANEL_NONE = 0;
    private static final int PANEL_SETTINGS = 1;
    private static final int PANEL_EPISODES = 2;

    private AiActivityCoursePlayBinding binding;
    private AiViewPlayInfoBinding info;
    private CoursePlayViewModel vm;
    private PlayerController player;
    private TopicCourseAdapter courseAdapter;
    private SingleViewAdapter infoAdapter;
    private OrientationEventListener orientation;
    private int statusBarHeight;
    private int panel = PANEL_NONE;
    @Nullable
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AiActivityCoursePlayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(AiInjector.factory(), CoursePlayViewModel.class);
        observeBase(vm);
        setupInsets();
        setupInfo();
        player = new PlayerController(binding.aiPlayPlayer, binding.aiPlayControls, new PlayerController.Callback() {
            @Override
            public void onTick(long positionMs, long bufferedMs, boolean playing, float speed) {
                vm.onPlaybackTick(positionMs, bufferedMs, playing, speed);
            }

            @Override
            public void onState(@NonNull PlayerState state) {
                vm.onPlayerStateChanged(state);
            }

            @Override
            public void onUserPause() {
                vm.onUserPause();
            }

            @Override
            public void onSeekFinished(long positionMs) {
                vm.onSeekFinished(positionMs);
            }

            @Override
            public void onControl(int viewId) {
                onControlClick(viewId);
            }
        });
        observe();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Boolean.TRUE.equals(vm.getLocked().getValue())) {
                    return;
                }
                if (panel != PANEL_NONE) {
                    hidePanel();
                    return;
                }
                if (Boolean.TRUE.equals(vm.getFullscreen().getValue())) {
                    exitFullscreen();
                    return;
                }
                finish();
            }
        });
        orientation = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int deg) {
                if (deg == ORIENTATION_UNKNOWN) {
                    return;
                }
                boolean portrait = deg < 30 || deg > 330;
                int req = getRequestedOrientation();
                if (portrait && req == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    // 点了退出全屏且手机已竖过来：恢复跟随系统
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                } else if (portrait && req == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        && Boolean.TRUE.equals(vm.getFullscreen().getValue())
                        && !Boolean.TRUE.equals(vm.getLocked().getValue())) {
                    exitFullscreen();
                }
            }
        };
        if (Boolean.TRUE.equals(vm.getFullscreen().getValue())) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        applyMode();
    }

    @Override
    protected boolean applyWindowInsetsToRoot() {
        return false;
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            statusBarHeight = bars.top;
            boolean full = Boolean.TRUE.equals(vm.getFullscreen().getValue());
            ViewGroup.LayoutParams lp = binding.aiPlayStatusBg.getLayoutParams();
            lp.height = full ? 0 : bars.top;
            binding.aiPlayStatusBg.setLayoutParams(lp);
            binding.aiPlayList.setPadding(0, 0, 0, bars.bottom + Math.round(24 * getResources().getDisplayMetrics().density));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupInfo() {
        binding.aiPlayList.setLayoutManager(new LinearLayoutManager(this));
        info = AiViewPlayInfoBinding.inflate(LayoutInflater.from(this), binding.aiPlayList, false);
        infoAdapter = new SingleViewAdapter(info.getRoot());
        courseAdapter = new TopicCourseAdapter(vm::switchCourse);
        binding.aiPlayList.setAdapter(new ConcatAdapter(infoAdapter, courseAdapter));
        info.aiInfoAsk.setOnClickListener(v -> vm.askAi());
        info.aiInfoKnowledge.setOnClickListener(v -> vm.openKnowledgePage());
        info.aiInfoSpeed.setOnClickListener(v -> PlaySettingSheet.show(getSupportFragmentManager()));
        info.aiInfoTopic.setOnClickListener(v -> vm.openTopicPage());
        binding.aiPlayState.setOnRetryListener(v -> vm.retry());
    }

    private void observe() {
        vm.getUiState().observe(this, s -> binding.aiPlayState.render(s));
        vm.getPlay().observe(this, this::renderPlay);
        vm.getTopicDetail().observe(this, this::renderTopic);
        vm.getSpeedText().observe(this, t -> {
            info.aiInfoSpeed.setText(t);
            renderPills();
        });
        vm.getSpeed().observe(this, s -> player.setSpeed(s == null ? 1f : s));
        vm.getCurrentSource().observe(this, s -> renderPills());
        vm.getPlayCommand().observe(this, cmd -> player.play(cmd));
        vm.getSeekTo().observe(this, ms -> player.seekTo(ms));
        vm.getPauseRequest().observe(this, v -> player.pause());
        vm.getPlayerState().observe(this, s -> {
            renderMessage();
            updatePipParams();
        });
        vm.getEndOverlay().observe(this, v -> renderMessage());
        vm.getHasNext().observe(this, v -> renderPills());
        vm.getFullscreen().observe(this, v -> applyMode());
        vm.getLocked().observe(this, l -> player.setLocked(Boolean.TRUE.equals(l)));
        vm.getLearnStatus().observe(this, s -> renderPlay(vm.getPlay().getValue()));
        vm.getResumeSnack().observe(this, sec -> showMessage(UiMessage.withAction(AiText.resumeTip(sec),
                getString(R.string.ai_play_back_to_start), ACTION_BACK_TO_START)));
        vm.getMobileSnack().observe(this, text -> showMessage(UiMessage.withAction(text,
                getString(R.string.ai_play_wifi_only_action), ACTION_WIFI_ONLY)));
        vm.getOpenAiChat().observe(this, ctx -> {
            Bundle b = new Bundle();
            b.putLong(RouterPath.EXTRA_COURSE_ID, ctx.getCourseId());
            b.putString(RouterPath.EXTRA_COURSE_TITLE, ctx.getTitle());
            b.putBoolean(RouterPath.EXTRA_NEW_SESSION, true);
            Router.open(this, RouterPath.K01, b);
        });
        vm.getOpenKnowledge().observe(this, id -> {
            Bundle b = new Bundle();
            b.putLong(RouterPath.EXTRA_KNOWLEDGE_ID, id);
            Router.open(this, RouterPath.K03, b);
        });
        vm.getOpenTopic().observe(this, id -> {
            Bundle b = new Bundle();
            b.putLong(RouterPath.EXTRA_TOPIC_ID, id);
            Router.open(this, RouterPath.K07, b);
        });
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (ACTION_WIFI_ONLY.equals(actionId)) {
            vm.setWifiOnly(true);
            player.pause();
        } else if (ACTION_BACK_TO_START.equals(actionId)) {
            vm.backToStart();
        }
    }

    // ===== 渲染 =====

    private void renderPlay(@Nullable CoursePlayVO p) {
        if (p == null) {
            return;
        }
        info.aiInfoTitle.setText(p.getTitle());
        player.setTitle(p.getTitle());
        info.aiInfoCategory.setText(p.getCategoryName());
        String updated = p.getUpdatedAt() == null ? "" : " · " + DateFormatter.formatDate(p.getUpdatedAt(), false) + "更新";
        info.aiInfoMeta.setText(AiText.duration(p.getDurationSec()) + " · " + AiText.nz(p.getSourceLabel()) + updated);
        info.aiInfoKnowledge.setVisibility(p.getKnowledgeId() == null ? View.GONE : View.VISIBLE);
        boolean hasTopic = p.getTopic() != null;
        info.aiInfoTopicRow.setVisibility(hasTopic ? View.VISIBLE : View.GONE);
        if (hasTopic) {
            info.aiInfoTopic.setText(getString(R.string.ai_play_topic, AiText.nz(p.getTopic().getName())));
        }
        courseAdapter.setPlayingId(p.getId());
        renderPills();
    }

    private void renderTopic(@Nullable TopicDetailVO t) {
        if (t == null) {
            courseAdapter.submitList(null);
            return;
        }
        info.aiInfoLearned.setText(AiText.learned(t.getLearnedCount(), t.getCourseCount()));
        courseAdapter.submitList(t.getCourses());
        renderPills();
        if (panel == PANEL_EPISODES) {
            showPanel(PANEL_EPISODES);
        }
    }

    private void renderPills() {
        VideoSourceVO src = vm.getCurrentSource().getValue();
        TopicDetailVO t = vm.getTopicDetail().getValue();
        player.setPills(vm.getSpeedText().getValue() == null ? "1.0x" : vm.getSpeedText().getValue(),
                src == null ? AiCodes.QUALITY_720 : AiText.nz(src.getQuality()),
                t != null && !t.getCourses().isEmpty(), Boolean.TRUE.equals(vm.getHasNext().getValue()));
    }

    private void renderMessage() {
        PlayerState s = vm.getPlayerState().getValue();
        if (Boolean.TRUE.equals(vm.getEndOverlay().getValue())) {
            player.showMessage(null, 0, getString(R.string.ai_play_replay),
                    Boolean.TRUE.equals(vm.getHasNext().getValue()) ? getString(R.string.ai_play_next) : null);
            return;
        }
        if (s == PlayerState.ERROR) {
            player.showMessage(getString(R.string.ai_play_failed), com.zhimu.slmp.base.R.drawable.ic_refresh, null, null);
        } else if (s == PlayerState.NETWORK_LOST) {
            player.showMessage(getString(R.string.ai_play_network_lost), com.zhimu.slmp.base.R.drawable.ic_cloud_off,
                    null, null);
        } else if (s == PlayerState.WIFI_BLOCKED) {
            player.showMessage(getString(R.string.ai_play_wifi_blocked), 0, getString(R.string.ai_play_continue), null);
        } else {
            player.hideMessage();
        }
    }

    private void onControlClick(int id) {
        if (id == R.id.ai_ctrl_back) {
            getOnBackPressedDispatcher().onBackPressed();
        } else if (id == R.id.ai_ctrl_fullscreen) {
            enterFullscreen();
        } else if (id == R.id.ai_ctrl_exit_fullscreen) {
            exitFullscreen();
        } else if (id == R.id.ai_ctrl_pip) {
            enterPip();
        } else if (id == R.id.ai_ctrl_more) {
            PlaySettingSheet.show(getSupportFragmentManager());
        } else if (id == R.id.ai_ctrl_lock) {
            vm.toggleLock();
            setRequestedOrientation(Boolean.TRUE.equals(vm.getLocked().getValue())
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : getRequestedOrientation());
        } else if (id == R.id.ai_ctrl_next) {
            vm.playNext();
        } else if (id == R.id.ai_ctrl_speed_pill || id == R.id.ai_ctrl_quality_pill) {
            showPanel(PANEL_SETTINGS);
        } else if (id == R.id.ai_ctrl_episodes_pill) {
            showPanel(PANEL_EPISODES);
        } else if (id == R.id.ai_ctrl_message) {
            if (vm.getPlayerState().getValue() == PlayerState.ERROR) {
                player.retry();
            }
        } else if (id == R.id.ai_ctrl_message_action1) {
            if (Boolean.TRUE.equals(vm.getEndOverlay().getValue())) {
                vm.replay();
            } else if (vm.getPlayerState().getValue() == PlayerState.WIFI_BLOCKED) {
                vm.continueOnMobile();
            }
        } else if (id == R.id.ai_ctrl_message_action2) {
            vm.playNext();
        }
    }

    // ===== 横屏全屏 =====

    private void enterFullscreen() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        vm.setFullscreen(true);
    }

    private void exitFullscreen() {
        hidePanel();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        vm.setFullscreen(false);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (land && !Boolean.TRUE.equals(vm.getFullscreen().getValue())) {
            vm.setFullscreen(true);
        } else if (!land && Boolean.TRUE.equals(vm.getFullscreen().getValue())
                && getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            vm.setFullscreen(false);
        }
        applyMode();
    }

    private void applyMode() {
        boolean full = Boolean.TRUE.equals(vm.getFullscreen().getValue());
        boolean pip = isInPictureInPictureMode();
        WindowInsetsControllerCompat wic = WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        wic.setAppearanceLightStatusBars(false);
        if (full || pip) {
            wic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            wic.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            wic.show(WindowInsetsCompat.Type.systemBars());
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        ViewGroup.LayoutParams lp = binding.aiPlayVideo.getLayoutParams();
        lp.height = full || pip ? ViewGroup.LayoutParams.MATCH_PARENT : Math.round(width * 9f / 16f);
        binding.aiPlayVideo.setLayoutParams(lp);
        binding.aiPlayState.setVisibility(full || pip ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams sl = binding.aiPlayStatusBg.getLayoutParams();
        sl.height = full || pip ? 0 : statusBarHeight;
        binding.aiPlayStatusBg.setLayoutParams(sl);
        player.setFullscreen(full && !pip);
        if (!full) {
            hidePanel();
        }
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    // ===== 右侧面板（K06） =====

    private void showPanel(int which) {
        panel = which;
        LinearLayout content = binding.aiPlayControls.aiCtrlPanelContent;
        content.removeAllViews();
        if (which == PANEL_SETTINGS) {
            addPanelTitle(content, getString(R.string.ai_play_speed));
            Float cur = vm.getSpeed().getValue();
            for (float s : CoursePlayViewModel.SPEEDS) {
                boolean on = cur != null && Math.abs(cur - s) < 0.01f;
                addPanelOption(content, AiText.speed(s), null, on, v -> {
                    vm.setSpeed(s);
                    showPanel(PANEL_SETTINGS);
                });
            }
            View divider = new View(this);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            int m = Math.round(23 * getResources().getDisplayMetrics().density);
            dl.setMargins(m, m / 2, m, m / 2);
            divider.setLayoutParams(dl);
            divider.setBackgroundColor(0x1FFFFFFF);
            content.addView(divider);
            addPanelTitle(content, getString(R.string.ai_play_quality));
            CoursePlayVO p = vm.getPlay().getValue();
            VideoSourceVO src = vm.getCurrentSource().getValue();
            if (p != null) {
                for (VideoSourceVO s : p.getSources()) {
                    boolean on = src != null && AiText.nz(s.getQuality()).equals(src.getQuality());
                    addPanelOption(content, AiCodes.QUALITY_480.equals(s.getQuality()) ? "480P" : "720P", null, on,
                            v -> {
                                vm.setQuality(AiText.nz(s.getQuality()));
                                showPanel(PANEL_SETTINGS);
                            });
                }
            }
        } else if (which == PANEL_EPISODES) {
            TopicDetailVO t = vm.getTopicDetail().getValue();
            addPanelTitle(content, getString(R.string.ai_play_episodes_title, t == null ? "" : AiText.nz(t.getName())));
            if (t != null) {
                CoursePlayVO p = vm.getPlay().getValue();
                for (CourseCardVO c : t.getCourses()) {
                    boolean on = p != null && p.getId() == c.getId();
                    String no = c.getSectionNo() == null ? "" : c.getSectionNo() + ". ";
                    addPanelOption(content, no + c.getTitle(), on ? getString(R.string.ai_play_now) : AiText.topicCourseSub(c),
                            on, v -> {
                                hidePanel();
                                vm.switchCourse(c.getId());
                            });
                }
            }
        }
        binding.aiPlayControls.aiCtrlPanel.setVisibility(View.VISIBLE);
    }

    private void hidePanel() {
        panel = PANEL_NONE;
        binding.aiPlayControls.aiCtrlPanel.setVisibility(View.GONE);
    }

    private void addPanelTitle(@NonNull LinearLayout parent, @NonNull String text) {
        TextView tv = new TextView(this);
        int pad = Math.round(23 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, Math.round(8 * getResources().getDisplayMetrics().density), pad, 0);
        tv.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodySmall);
        tv.setTextColor(getColor(com.zhimu.slmp.base.R.color.zm_on_media));
        tv.setText(text);
        parent.addView(tv);
    }

    private void addPanelOption(@NonNull LinearLayout parent, @NonNull String text, @Nullable String sub, boolean on,
                                @NonNull View.OnClickListener l) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(Math.round((sub == null ? 48 : 56) * d));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(23 * d), 0, Math.round(16 * d), 0);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        int color = on ? MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorPrimaryInverse,
                getColor(com.zhimu.slmp.base.R.color.zm_inverse_primary)) : getColor(com.zhimu.slmp.base.R.color.zm_on_media);
        TextView tv = new TextView(this);
        tv.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
        tv.setTextColor(color);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setText(on && sub == null ? text + "  ✓" : text);
        row.addView(tv);
        if (sub != null) {
            TextView s = new TextView(this);
            s.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodySmall);
            s.setTextColor(on ? color : 0xB3FFFFFF);
            s.setText(sub);
            row.addView(s);
        }
        row.setOnClickListener(l);
        parent.addView(row);
    }

    // ===== 画中画 =====

    private void enterPip() {
        try {
            boolean ok = enterPictureInPictureMode(pipParams(true));
            if (!ok) {
                showMessage(getString(R.string.ai_play_pip_disabled));
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            showMessage(getString(R.string.ai_play_pip_disabled));
        }
    }

    @NonNull
    private PictureInPictureParams pipParams(boolean autoEnter) {
        return new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .setAutoEnterEnabled(autoEnter)
                .build();
    }

    private void updatePipParams() {
        try {
            setPictureInPictureParams(pipParams(vm.getPlayerState().getValue() == PlayerState.PLAYING));
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 系统不支持画中画
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        player.setControlsShown(!isInPictureInPictureMode);
        applyMode();
    }

    // ===== 生命周期 =====

    @Override
    protected void onStart() {
        super.onStart();
        player.create();
        orientation.enable();
        registerNetwork();
    }

    @Override
    protected void onStop() {
        super.onStop();
        orientation.disable();
        unregisterNetwork();
        if (!isInPictureInPictureMode()) {
            vm.onLeave();
            player.release();
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        long id = intent.getLongExtra(RouterPath.EXTRA_COURSE_ID, 0);
        if (id > 0) {
            vm.switchCourse(id);
        }
    }

    @Override
    protected void onDestroy() {
        player.release();
        super.onDestroy();
    }

    private void registerNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null || networkCallback != null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (vm.getPlayerState().getValue() == PlayerState.NETWORK_LOST) {
                        player.retry();
                    }
                });
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    private void unregisterNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null && networkCallback != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // 已注销
            }
        }
        networkCallback = null;
    }
}
