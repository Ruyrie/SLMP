package com.zhimu.slmp.ai.ui.play;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.request.CourseProgressRequest;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.ai.util.ProgressTracker;
import com.zhimu.slmp.base.util.DateFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 学习进度上报（技术文档 2.6.8，K05/K06 与 K03 内嵌视频共用）：
 * START 本次播放首次进入 PLAYING（生成 play_token，带 entry_type）；播放中每累计 10 秒 TICK；暂停 PAUSE；拖动或 ±10 秒松手 SEEK；
 * 切课程前 SWITCH、播放到结尾 END、离开 LEAVE（后三者用应用级订阅，页面销毁不取消）。watched_sec 由 {@link ProgressTracker} 给出，成功后扣除。
 * 上报失败只记日志，不提示（40001、40401 同）。
 */
public class PlaybackReporter {

    private static final String TAG = "ZmtCourse";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CourseRepository repository;
    private final NetworkStatus network;
    private final CompositeDisposable disposables;
    private final ProgressTracker tracker = new ProgressTracker();
    @Nullable
    private Consumer<ProgressResultVO> onResult;

    private long courseId;
    @NonNull
    private String entryType = AiCodes.ENTRY_COURSE;
    @NonNull
    private String quality = AiCodes.QUALITY_720;
    @Nullable
    private String playToken;
    private boolean started;
    private boolean askAiClicked;
    private boolean knowledgeClicked;

    public PlaybackReporter(@NonNull CourseRepository repository, @NonNull NetworkStatus network,
                            @NonNull CompositeDisposable disposables) {
        this.repository = repository;
        this.network = network;
        this.disposables = disposables;
    }

    public void setOnResult(@Nullable Consumer<ProgressResultVO> onResult) {
        this.onResult = onResult;
    }

    /** 新的一次播放（加载课程、切课程、重播） */
    public void begin(long courseId, @NonNull String entryType) {
        this.courseId = courseId;
        this.entryType = entryType;
        this.playToken = UUID.randomUUID().toString().replace("-", "");
        this.started = false;
        this.askAiClicked = false;
        this.knowledgeClicked = false;
        tracker.reset();
    }

    public void setQuality(@NonNull String quality) {
        this.quality = quality;
    }

    public void markAskAi() {
        askAiClicked = true;
    }

    public void markKnowledge() {
        knowledgeClicked = true;
    }

    public boolean isStarted() {
        return started;
    }

    /** 每秒一次 */
    public void onTick(long positionMs, long clockMs, boolean playing, float speed) {
        if (playToken == null) {
            return;
        }
        if (tracker.onTick(positionMs, clockMs, playing, speed) && started) {
            report(AiCodes.EVENT_TICK, positionMs, false);
        }
    }

    /** 进入 PLAYING */
    public void onPlaying(long positionMs) {
        if (playToken == null || started) {
            return;
        }
        started = true;
        report(AiCodes.EVENT_START, positionMs, false);
    }

    public void onPause(long positionMs) {
        if (started) {
            report(AiCodes.EVENT_PAUSE, positionMs, false);
        }
    }

    public void onSeek(long targetMs, long clockMs) {
        tracker.reanchor(targetMs, clockMs);
        if (started) {
            report(AiCodes.EVENT_SEEK, targetMs, false);
        }
    }

    public void onEnded(long positionMs) {
        if (started) {
            report(AiCodes.EVENT_END, positionMs, true);
        }
    }

    public void onSwitch(long positionMs) {
        if (started) {
            report(AiCodes.EVENT_SWITCH, positionMs, true);
        }
    }

    public void onLeave(long positionMs) {
        if (started) {
            report(AiCodes.EVENT_LEAVE, positionMs, true);
        }
    }

    @NonNull
    CourseProgressRequest build(@NonNull String event, long positionMs, int watched) {
        CourseProgressRequest r = new CourseProgressRequest();
        r.setPlayToken(playToken);
        r.setEvent(event);
        r.setPositionSec((int) Math.max(0, positionMs / 1000));
        r.setWatchedSec(watched);
        r.setQuality(quality);
        r.setNetworkType(network.type());
        r.setEntryType(AiCodes.EVENT_START.equals(event) ? entryType : null);
        r.setClientTime(LocalDateTime.now(DateFormatter.ZONE).format(TIME));
        if (askAiClicked) {
            r.setAskAiClicked(true);
        }
        if (knowledgeClicked) {
            r.setKnowledgeClicked(true);
        }
        return r;
    }

    private void report(@NonNull String event, long positionMs, boolean detached) {
        int watched = tracker.peekWatched();
        CourseProgressRequest req = build(event, positionMs, watched);
        long id = courseId;
        if (detached) {
            tracker.commit(watched);
            repository.reportProgressDetached(id, req);
            return;
        }
        disposables.add(repository.reportProgress(id, req)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(r -> {
                    tracker.commit(watched);
                    if (onResult != null && id == courseId) {
                        onResult.accept(r);
                    }
                }, e -> Log.w(TAG, "进度上报失败 event=" + event)));
    }
}
