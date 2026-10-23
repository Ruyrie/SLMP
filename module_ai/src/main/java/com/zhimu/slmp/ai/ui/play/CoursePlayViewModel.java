package com.zhimu.slmp.ai.ui.play;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.CourseContext;
import com.zhimu.slmp.ai.model.PlayCommand;
import com.zhimu.slmp.ai.model.PlayerState;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.model.vo.VideoSourceVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.session.AppSettings;

import java.math.BigDecimal;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * K05 / K06 视频播放（技术文档 2.4.6、2.6.7、2.6.8）：ExoPlayer 由 Activity 持有，本类只保存播放信息、清晰度与倍速、
 * 播放状态与进度，经事件下发播放指令（换媒体项并从 startMs 开始）。进度上报见 {@link PlaybackReporter}。
 * 移动网络开播提示“正在使用移动网络播放，本视频约 28 MB”；设为仅 Wi-Fi 后移动网络下不自动播放。
 */
public class CoursePlayViewModel extends BaseViewModel {

    public static final float[] SPEEDS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private final CourseRepository repository;
    private final NetworkStatus network;
    private final AppSettings settings;
    private final PlaybackReporter reporter;

    private final MutableLiveData<CoursePlayVO> play = new MutableLiveData<>(null);
    private final MutableLiveData<TopicDetailVO> topicDetail = new MutableLiveData<>(null);
    private final MutableLiveData<VideoSourceVO> currentSource = new MutableLiveData<>(null);
    private final MutableLiveData<Float> speed = new MutableLiveData<>(1.0f);
    private final MutableLiveData<String> speedText = new MutableLiveData<>("1.0x");
    private final MutableLiveData<PlayerState> playerState = new MutableLiveData<>(PlayerState.IDLE);
    private final MutableLiveData<Long> positionMs = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> bufferedMs = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> fullscreen = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> locked = new MutableLiveData<>(false);
    private final MutableLiveData<String> learnStatus = new MutableLiveData<>(null);
    private final MutableLiveData<BigDecimal> progressRatio = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> hasNext = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> endOverlay = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> wifiOnly = new MutableLiveData<>(false);
    private final MutableLiveData<String> networkType = new MutableLiveData<>(AiCodes.NET_UNKNOWN);

    private final SingleLiveEvent<PlayCommand> playCommand = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> seekTo = new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer> resumeSnack = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> mobileSnack = new SingleLiveEvent<>();
    private final SingleLiveEvent<CourseContext> openAiChat = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openKnowledge = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openTopic = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> pauseRequest = new SingleLiveEvent<>();

    private long courseId;
    @NonNull
    private String entryType = AiCodes.ENTRY_COURSE;
    private long initialStartMs = -1;
    private boolean loadedOnce;

    @Inject
    public CoursePlayViewModel(@NonNull CourseRepository repository, @NonNull NetworkStatus network,
                               @NonNull AppSettings settings) {
        this.repository = repository;
        this.network = network;
        this.settings = settings;
        this.reporter = new PlaybackReporter(repository, network, disposables);
        reporter.setOnResult(this::applyProgress);
        wifiOnly.setValue(settings.isVideoWifiOnly());
        disposables.add(repository.progressChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(id -> {
                    if (id == courseId) {
                        ProgressResultVO r = repository.lastResult(id);
                        if (r != null) {
                            applyProgress(r);
                        }
                    }
                    TopicDetailVO t = topicDetail.getValue();
                    if (t != null) {
                        refreshTopic(t.getId());
                    }
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(AiExtras.EXTRA_COURSE_ID);
        String entry = handle.get(AiExtras.EXTRA_ENTRY_TYPE);
        Boolean full = handle.get(AiExtras.EXTRA_FULLSCREEN);
        Long start = handle.get(AiExtras.EXTRA_START_MS);
        if (start != null) {
            initialStartMs = start;
        }
        fullscreen.setValue(Boolean.TRUE.equals(full));
        if (id != null && id > 0) {
            load(id, entry == null ? AiCodes.ENTRY_COURSE : entry);
        }
    }

    // ===== getter =====

    public LiveData<CoursePlayVO> getPlay() {
        return play;
    }

    public LiveData<TopicDetailVO> getTopicDetail() {
        return topicDetail;
    }

    public LiveData<VideoSourceVO> getCurrentSource() {
        return currentSource;
    }

    public LiveData<Float> getSpeed() {
        return speed;
    }

    public LiveData<String> getSpeedText() {
        return speedText;
    }

    public LiveData<PlayerState> getPlayerState() {
        return playerState;
    }

    public LiveData<Long> getPositionMs() {
        return positionMs;
    }

    public LiveData<Long> getBufferedMs() {
        return bufferedMs;
    }

    public LiveData<Boolean> getFullscreen() {
        return fullscreen;
    }

    public LiveData<Boolean> getLocked() {
        return locked;
    }

    public LiveData<String> getLearnStatus() {
        return learnStatus;
    }

    public LiveData<BigDecimal> getProgressRatio() {
        return progressRatio;
    }

    public LiveData<Boolean> getHasNext() {
        return hasNext;
    }

    public LiveData<Boolean> getEndOverlay() {
        return endOverlay;
    }

    public LiveData<Boolean> getWifiOnly() {
        return wifiOnly;
    }

    public LiveData<String> getNetworkType() {
        return networkType;
    }

    public SingleLiveEvent<PlayCommand> getPlayCommand() {
        return playCommand;
    }

    public SingleLiveEvent<Long> getSeekTo() {
        return seekTo;
    }

    public SingleLiveEvent<Integer> getResumeSnack() {
        return resumeSnack;
    }

    public SingleLiveEvent<String> getMobileSnack() {
        return mobileSnack;
    }

    public SingleLiveEvent<CourseContext> getOpenAiChat() {
        return openAiChat;
    }

    public SingleLiveEvent<Long> getOpenKnowledge() {
        return openKnowledge;
    }

    public SingleLiveEvent<Long> getOpenTopic() {
        return openTopic;
    }

    /** 页面暂停播放器（“仅 Wi-Fi”、问 AI、图文原文、本专题） */
    public SingleLiveEvent<Boolean> getPauseRequest() {
        return pauseRequest;
    }

    public long getCourseId() {
        return courseId;
    }

    // ===== 加载 =====

    public void load(long id, @NonNull String entry) {
        courseId = id;
        entryType = entry;
        endOverlay.setValue(false);
        resetContentLoaded();
        load(repository.getCoursePlay(id), this::onLoaded);
    }

    @Override
    public void retry() {
        if (courseId > 0) {
            load(courseId, entryType);
        }
    }

    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.RECORD_DELETED) {
            setUiState(UiState.empty(e.getMessage() == null ? "这个视频已下架" : e.getMessage(), null));
            return;
        }
        super.applyErrorState(e);
    }

    private void onLoaded(@NonNull CoursePlayVO vo) {
        play.setValue(vo);
        hasNext.setValue(vo.getNextCourseId() != null);
        String quality = currentSource.getValue() == null ? AiCodes.QUALITY_720 : currentSource.getValue().getQuality();
        VideoSourceVO src = vo.sourceOf(quality);
        currentSource.setValue(src);
        CoursePlayVO.Progress p = vo.getProgress();
        learnStatus.setValue(p != null && p.isCompleted() ? AiCodes.LEARN_COMPLETED
                : p != null && p.getPositionSec() > 0 ? AiCodes.LEARN_LEARNING : AiCodes.LEARN_NOT_STARTED);
        if (vo.getTopic() != null) {
            refreshTopic(vo.getTopic().getId());
        } else {
            topicDetail.setValue(null);
        }
        reporter.begin(vo.getId(), entryType);
        reporter.setQuality(src == null ? AiCodes.QUALITY_720 : src.getQuality());
        int resume = p == null ? 0 : p.getResumePositionSec();
        long startMs = initialStartMs >= 0 ? initialStartMs : resume * 1000L;
        initialStartMs = -1;
        if (src == null) {
            playerState.setValue(PlayerState.ERROR);
            return;
        }
        String net = network.type();
        networkType.setValue(net);
        boolean mobile = AiCodes.NET_MOBILE.equals(net);
        if (mobile && Boolean.TRUE.equals(wifiOnly.getValue())) {
            playerState.setValue(PlayerState.WIFI_BLOCKED);
            playCommand.setValue(new PlayCommand(src.getHlsUrl(), src.getQuality(), startMs, false));
            return;
        }
        playCommand.setValue(new PlayCommand(src.getHlsUrl(), src.getQuality(), startMs, true));
        if (mobile) {
            mobileSnack.setValue(AiText.mobileTip(src.getSizeBytes()));
        } else if (resume > 0 && startMs > 0) {
            resumeSnack.setValue(resume);
        }
        loadedOnce = true;
    }

    private void refreshTopic(long topicId) {
        launch(repository.getTopic(topicId), topicDetail::setValue, e -> {
        });
    }

    // ===== 播放器回调 =====

    public void onPlaybackTick(long position, long buffered, boolean playing, float currentSpeed) {
        if (!Objects.equals(positionMs.getValue(), position)) {
            positionMs.setValue(position);
        }
        if (!Objects.equals(bufferedMs.getValue(), buffered)) {
            bufferedMs.setValue(buffered);
        }
        reporter.onTick(position, SystemClock.elapsedRealtime(), playing, currentSpeed);
    }

    public void onPlayerStateChanged(@NonNull PlayerState state) {
        PlayerState old = playerState.getValue();
        if (old == PlayerState.WIFI_BLOCKED && state != PlayerState.PLAYING) {
            return;
        }
        playerState.setValue(state);
        long pos = positionMs.getValue() == null ? 0 : positionMs.getValue();
        if (state == PlayerState.PLAYING) {
            endOverlay.setValue(false);
            reporter.onPlaying(pos);
        } else if (state == PlayerState.ENDED) {
            CoursePlayVO vo = play.getValue();
            long end = vo == null ? pos : Math.max(pos, vo.getDurationSec() * 1000L);
            reporter.onEnded(end);
            endOverlay.setValue(true);
        }
    }

    public void onUserPause() {
        reporter.onPause(currentPos());
    }

    public void onSeekFinished(long target) {
        positionMs.setValue(target);
        reporter.onSeek(target, SystemClock.elapsedRealtime());
    }

    /** 续播 Snackbar“回到开头” */
    public void backToStart() {
        seekTo.setValue(0L);
        onSeekFinished(0);
    }

    // ===== 清晰度、倍速、切换 =====

    public void setQuality(@NonNull String quality) {
        CoursePlayVO vo = play.getValue();
        VideoSourceVO cur = currentSource.getValue();
        if (vo == null || (cur != null && quality.equals(cur.getQuality()))) {
            return;
        }
        VideoSourceVO src = vo.sourceOf(quality);
        if (src == null) {
            return;
        }
        currentSource.setValue(src);
        reporter.setQuality(src.getQuality());
        boolean playing = playerState.getValue() == PlayerState.PLAYING || playerState.getValue() == PlayerState.BUFFERING;
        playCommand.setValue(new PlayCommand(src.getHlsUrl(), src.getQuality(), currentPos(), playing));
    }

    public void setSpeed(float s) {
        speed.setValue(s);
        speedText.setValue(AiText.speed(s));
    }

    /** 本专题中的其他课程 / 选集：先报 SWITCH，再加载 */
    public void switchCourse(long id) {
        if (id == courseId) {
            return;
        }
        reporter.onSwitch(currentPos());
        load(id, entryType);
    }

    public void playNext() {
        CoursePlayVO vo = play.getValue();
        if (vo != null && vo.getNextCourseId() != null) {
            switchCourse(vo.getNextCourseId());
        }
    }

    public void replay() {
        CoursePlayVO vo = play.getValue();
        VideoSourceVO src = currentSource.getValue();
        if (vo == null || src == null) {
            return;
        }
        endOverlay.setValue(false);
        reporter.begin(vo.getId(), entryType);
        reporter.setQuality(src.getQuality());
        playCommand.setValue(new PlayCommand(src.getHlsUrl(), src.getQuality(), 0, true));
    }

    /** “当前为移动网络，已设为仅在 Wi-Fi 下播放”下点“继续播放” */
    public void continueOnMobile() {
        VideoSourceVO src = currentSource.getValue();
        playerState.setValue(PlayerState.BUFFERING);
        if (src != null) {
            playCommand.setValue(new PlayCommand(src.getHlsUrl(), src.getQuality(), currentPos(), true));
        }
    }

    public void toggleFullscreen() {
        fullscreen.setValue(!Boolean.TRUE.equals(fullscreen.getValue()));
        if (!Boolean.TRUE.equals(fullscreen.getValue())) {
            locked.setValue(false);
        }
    }

    public void setFullscreen(boolean on) {
        if (!Objects.equals(fullscreen.getValue(), on)) {
            fullscreen.setValue(on);
            if (!on) {
                locked.setValue(false);
            }
        }
    }

    public void toggleLock() {
        locked.setValue(!Boolean.TRUE.equals(locked.getValue()));
    }

    public void askAi() {
        CoursePlayVO vo = play.getValue();
        if (vo == null) {
            return;
        }
        reporter.markAskAi();
        pauseRequest.setValue(true);
        openAiChat.setValue(new CourseContext(vo.getId(), vo.getTitle() == null ? "" : vo.getTitle()));
    }

    public void openKnowledgePage() {
        CoursePlayVO vo = play.getValue();
        if (vo == null || vo.getKnowledgeId() == null) {
            return;
        }
        reporter.markKnowledge();
        pauseRequest.setValue(true);
        openKnowledge.setValue(vo.getKnowledgeId());
    }

    public void openTopicPage() {
        CoursePlayVO vo = play.getValue();
        if (vo == null || vo.getTopic() == null) {
            return;
        }
        pauseRequest.setValue(true);
        openTopic.setValue(vo.getTopic().getId());
    }

    /** 播放设置“仅在 Wi-Fi 下播放”开关；移动网络 Snackbar 点“仅 Wi-Fi”时暂停 */
    public void setWifiOnly(boolean on) {
        wifiOnly.setValue(on);
        settings.setVideoWifiOnly(on);
        if (on && AiCodes.NET_MOBILE.equals(network.type())) {
            pauseRequest.setValue(true);
        }
    }

    /** 返回、非画中画 onStop、小窗关闭：LEAVE */
    public void onLeave() {
        reporter.onLeave(currentPos());
    }

    /** 本专题列表中对应课程的进度（课程行状态） */
    @Nullable
    public CourseCardVO topicCourse(long id) {
        TopicDetailVO t = topicDetail.getValue();
        if (t == null) {
            return null;
        }
        for (CourseCardVO c : t.getCourses()) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private void applyProgress(@NonNull ProgressResultVO r) {
        learnStatus.setValue(r.getLearnStatus());
        progressRatio.setValue(r.getProgressRatio());
    }

    private long currentPos() {
        Long p = positionMs.getValue();
        return p == null ? 0 : p;
    }

    boolean isLoadedOnce() {
        return loadedOnce;
    }
}
