package com.zhimu.slmp.ai.ui.knowledge;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.data.AiRepository;
import com.zhimu.slmp.ai.data.CourseRepository;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.PlayCommand;
import com.zhimu.slmp.ai.model.PlayerState;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.KnowledgeBlockVO;
import com.zhimu.slmp.ai.model.vo.KnowledgeDetailVO;
import com.zhimu.slmp.ai.model.vo.RelatedKnowledgeVO;
import com.zhimu.slmp.ai.model.vo.VideoSourceVO;
import com.zhimu.slmp.ai.ui.play.PlaybackReporter;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * K03 知识原文（技术文档 2.4.3）：API-K12（从 K01 进入带 citation_id，定位并标注被引用段落）；回答后条目已更新时显示 Notice；
 * 内嵌教学视频点封面后取 API-K19 原位播放，进度按 KNOWLEDGE 入口上报；41001 显示下架。
 */
public class KnowledgeDetailViewModel extends BaseViewModel {

    public static final String MSG_OFFLINE = "这条知识已下架，不能查看原文";

    private final AiRepository repository;
    private final CourseRepository courses;
    private final PlaybackReporter reporter;

    private final MutableLiveData<KnowledgeDetailVO> detail = new MutableLiveData<>(null);
    private final MutableLiveData<List<KnowledgeBlockVO>> blocks = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> highlightIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<String> updateNotice = new MutableLiveData<>(null);
    private final MutableLiveData<List<RelatedKnowledgeVO>> relatedItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<CoursePlayVO> embeddedPlay = new MutableLiveData<>(null);
    private final MutableLiveData<PlayerState> embeddedState = new MutableLiveData<>(PlayerState.IDLE);
    private final SingleLiveEvent<Integer> scrollToBlock = new SingleLiveEvent<>();
    private final SingleLiveEvent<PlayCommand> playCommand = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> shareText = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openKnowledge = new SingleLiveEvent<>();

    private long knowledgeId;
    @Nullable
    private Long citationId;
    private long lastPositionMs;

    @Inject
    public KnowledgeDetailViewModel(@NonNull AiRepository repository, @NonNull CourseRepository courses,
                                    @NonNull NetworkStatus network) {
        this.repository = repository;
        this.courses = courses;
        this.reporter = new PlaybackReporter(courses, network, disposables);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(AiExtras.EXTRA_KNOWLEDGE_ID);
        Long cite = handle.get(AiExtras.EXTRA_CITATION_ID);
        if (id != null && id > 0) {
            load(id, cite != null && cite > 0 ? cite : null);
        }
    }

    public void load(long id, @Nullable Long citation) {
        knowledgeId = id;
        citationId = citation;
        load(repository.getKnowledge(id, citation), this::apply);
    }

    @Override
    public void retry() {
        if (knowledgeId > 0) {
            resetContentLoaded();
            load(knowledgeId, citationId);
        }
    }

    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.RECORD_DELETED) {
            setUiState(UiState.empty(MSG_OFFLINE, null));
            return;
        }
        super.applyErrorState(e);
    }

    private void apply(@NonNull KnowledgeDetailVO vo) {
        detail.setValue(vo);
        List<KnowledgeBlockVO> list = vo.getBlocks();
        blocks.setValue(list);
        relatedItems.setValue(vo.getRelatedItems());
        int idx = -1;
        if (citationId != null && vo.getHighlightParaKey() != null) {
            for (int i = 0; i < list.size(); i++) {
                if (vo.getHighlightParaKey().equals(list.get(i).getParaKey())) {
                    idx = i;
                    break;
                }
            }
        }
        highlightIndex.setValue(idx);
        updateNotice.setValue(vo.isUpdatedAfterAnswer() ? "这条知识在回答后已更新，当前显示版本 " + vo.getVersionNo()
                + "，被引用的内容可能有变化。" : null);
        if (idx >= 0) {
            scrollToBlock.setValue(idx);
        }
    }

    // ===== getter =====

    public LiveData<KnowledgeDetailVO> getDetail() {
        return detail;
    }

    public LiveData<List<KnowledgeBlockVO>> getBlocks() {
        return blocks;
    }

    public LiveData<Integer> getHighlightIndex() {
        return highlightIndex;
    }

    public LiveData<String> getUpdateNotice() {
        return updateNotice;
    }

    public LiveData<List<RelatedKnowledgeVO>> getRelatedItems() {
        return relatedItems;
    }

    public LiveData<CoursePlayVO> getEmbeddedPlay() {
        return embeddedPlay;
    }

    public LiveData<PlayerState> getEmbeddedState() {
        return embeddedState;
    }

    public SingleLiveEvent<Integer> getScrollToBlock() {
        return scrollToBlock;
    }

    public SingleLiveEvent<PlayCommand> getPlayCommand() {
        return playCommand;
    }

    public SingleLiveEvent<String> getShareText() {
        return shareText;
    }

    public SingleLiveEvent<Long> getOpenKnowledge() {
        return openKnowledge;
    }

    // ===== 内嵌视频 =====

    /** 点内嵌视频封面：取播放信息后原位开播（从记录位置续播） */
    public void playEmbedded() {
        KnowledgeDetailVO vo = detail.getValue();
        if (vo == null || vo.getEmbeddedVideo() == null) {
            return;
        }
        embeddedState.setValue(PlayerState.BUFFERING);
        launch(courses.getCoursePlay(vo.getEmbeddedVideo().getCourseId()), p -> {
            embeddedPlay.setValue(p);
            VideoSourceVO src = p.sourceOf(AiCodes.QUALITY_720);
            if (src == null) {
                embeddedState.setValue(PlayerState.ERROR);
                return;
            }
            reporter.begin(p.getId(), AiCodes.ENTRY_KNOWLEDGE);
            reporter.setQuality(src.getQuality());
            int resume = p.getProgress() == null ? 0 : p.getProgress().getResumePositionSec();
            playCommand.setValue(new PlayCommand(src.getHlsUrl(), src.getQuality(), resume * 1000L, true));
        }, e -> {
            embeddedState.setValue(PlayerState.ERROR);
            handleError(e);
        });
    }

    public void onPlaybackTick(long positionMs, long bufferedMs, boolean playing, float speed) {
        lastPositionMs = positionMs;
        reporter.onTick(positionMs, SystemClock.elapsedRealtime(), playing, speed);
    }

    public void onPlayerStateChanged(@NonNull PlayerState state) {
        embeddedState.setValue(state);
        if (state == PlayerState.PLAYING) {
            reporter.onPlaying(lastPositionMs);
        } else if (state == PlayerState.ENDED) {
            CoursePlayVO p = embeddedPlay.getValue();
            reporter.onEnded(p == null ? lastPositionMs : Math.max(lastPositionMs, p.getDurationSec() * 1000L));
        }
    }

    public void onUserPause() {
        reporter.onPause(lastPositionMs);
    }

    public void onSeekFinished(long positionMs) {
        lastPositionMs = positionMs;
        reporter.onSeek(positionMs, SystemClock.elapsedRealtime());
    }

    /** 离开页面：暂停并保存进度（LEAVE） */
    public void onLeave() {
        reporter.onLeave(lastPositionMs);
    }

    public long getLastPositionMs() {
        return lastPositionMs;
    }

    // ===== 分享与相关条目 =====

    public void share() {
        KnowledgeDetailVO vo = detail.getValue();
        if (vo != null) {
            shareText.setValue(vo.getTitle() + (vo.getShareUrl() == null ? "" : "\n" + vo.getShareUrl()));
        }
    }

    public void onRelatedClick(@NonNull RelatedKnowledgeVO item) {
        openKnowledge.setValue(item.getId());
    }
}
