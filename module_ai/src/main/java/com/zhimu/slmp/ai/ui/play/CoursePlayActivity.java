package com.zhimu.slmp.ai.ui.play;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.provider.Settings;
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
 * 进入全屏：点 fullscreen（请求横屏传感器方向）或打开自动旋转时把手机横过来；退出全屏：exit 按钮、返回键、
 * 手机竖回去。从 K03 内嵌视频点全屏进入（EXTRA_FULLSCREEN）时，退出全屏即返回 K03 并带回当前位置。
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
    /** 从 K03 内嵌视频点全屏进入：退出全屏即返回 K03，并带回当前位置 */
    private boolean returnOnExit;
    /** 请求横屏后手机是否真的横过来过：横过来再竖回去才自动退出全屏（刚点全屏时手机还竖着，不能马上退回） */
    private boolean landscapeSeen;
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
                finishToCaller();
            }
        });
        orientation = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int deg) {
                if (deg == ORIENTATION_UNKNOWN || isFinishing() || isInPictureInPictureMode()) {
                    return;
                }
                boolean portrait = deg < 30 || deg > 330;
                boolean landscape = (deg > 60 && deg < 120) || (deg > 240 && deg < 300);
                int req = getRequestedOrientation();
                if (req == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    if (landscape) {
                        landscapeSeen = true;
                    } else if (portrait && landscapeSeen && autoRotateOn()
                            && Boolean.TRUE.equals(vm.getFullscreen().getValue())
                            && !Boolean.TRUE.equals(vm.getLocked().getValue())) {
                        // 点全屏后手机横过来又竖回去（系统已打开自动旋转）：退出全屏
                        exitFullscreen();
                    }
                } else if (req == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && portrait) {
                    // 退出全屏且手机已竖过来：恢复跟随系统（打开自动旋转时再横过来即进入全屏）
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        };
        returnOnExit = getIntent().getBooleanExtra(RouterPath.EXTRA_FULLSCREEN, false);
        if (Boolean.TRUE.equals(vm.getFullscreen().getValue())) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                && !isInMultiWindowMode()) {
            // 横着拿手机打开（自动旋转）：直接全屏，竖回去时跟随系统退出
            vm.setFullscreen(true);
        }
        applyMode();
    }

    /** 系统设置是否打开了自动旋转（关闭时旋转手机不自动进出全屏） */
    private boolean autoRotateOn() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
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
            lp.height = full || isInPictureInPictureMode() ? 0 : bars.top;
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
            // 锁定：固定当前横屏方向；解锁：恢复横屏传感器方向，手机横过来再竖回去时退出全屏
            landscapeSeen = false;
            setRequestedOrientation(Boolean.TRUE.equals(vm.getLocked().getValue())
                    ? ActivityInfo.SCREEN_ORIENTATION_LOCKED : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
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

    /**
     * 点 fullscreen：请求横屏传感器方向并立即切到全屏布局（不等旋转完成）。之后手机要先横过来，
     * 再竖回去才自动退出（landscapeSeen），否则竖着拿手机点全屏会被方向监听马上退回竖屏。
     */
    private void enterFullscreen() {
        landscapeSeen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        vm.setFullscreen(true);
    }

    /** 退出全屏：请求竖屏，手机竖过来后由方向监听恢复跟随系统；从 K03 进入时直接返回 K03 */
    private void exitFullscreen() {
        if (returnOnExit) {
            finishToCaller();
            return;
        }
        hidePanel();
        landscapeSeen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        vm.setFullscreen(false);
    }

    /** 返回上一页；从 K03 全屏进入时带回当前位置（仍是同一课程时），K03 原位接着播 */
    private void finishToCaller() {
        CoursePlayVO p = vm.getPlay().getValue();
        long pos = player.position();
        if (returnOnExit && pos > 0 && p != null && p.getId() == getIntent().getLongExtra(RouterPath.EXTRA_COURSE_ID, 0)) {
            setResult(RESULT_OK, new Intent().putExtra(RouterPath.EXTRA_START_MS, pos));
        }
        finish();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isInPictureInPictureMode() && !isInMultiWindowMode()) {
            boolean land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
            boolean full = Boolean.TRUE.equals(vm.getFullscreen().getValue());
            int req = getRequestedOrientation();
            if (land && !full && req != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                // 打开自动旋转时把手机横过来：进入全屏（刚点了退出全屏、还在转回竖屏途中的横屏不算）
                vm.setFullscreen(true);
            } else if (!land && full && req == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                // 跟随系统转回竖屏：退出全屏（请求横屏后、还没转过去的竖屏不算）
                if (returnOnExit) {
                    finishToCaller();
                    return;
                }
                vm.setFullscreen(false);
            }
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
        // 竖屏 16:9 由 VideoFrameLayout 测量时按实际宽度计算（旋转途中屏幕尺寸还是旧值，不能在这里按屏宽算）；全屏铺满
        binding.aiPlayVideo.setKeepRatio(!full && !pip);
        ViewGroup.LayoutParams lp = binding.aiPlayVideo.getLayoutParams();
        lp.height = full || pip ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
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
        // singleTask 复用本页时上面的页面（含 K03）已出栈，退出全屏不再返回
        returnOnExit = false;
        long id = intent.getLongExtra(RouterPath.EXTRA_COURSE_ID, 0);
        if (id > 0) {
            vm.switchCourse(id);
        }
        if (intent.getBooleanExtra(RouterPath.EXTRA_FULLSCREEN, false)
                && !Boolean.TRUE.equals(vm.getFullscreen().getValue())) {
            enterFullscreen();
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
