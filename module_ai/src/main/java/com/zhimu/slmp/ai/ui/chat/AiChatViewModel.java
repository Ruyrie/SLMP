package com.zhimu.slmp.ai.ui.chat;

import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.ai.data.AiRepository;
import com.zhimu.slmp.ai.data.AnswerEngine;
import com.zhimu.slmp.ai.data.SpeechEngine;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.AskInput;
import com.zhimu.slmp.ai.model.CourseContext;
import com.zhimu.slmp.ai.model.InputMode;
import com.zhimu.slmp.ai.model.StreamPhase;
import com.zhimu.slmp.ai.model.vo.AiMessageSliceVO;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.AiSessionDetailVO;
import com.zhimu.slmp.ai.model.vo.CitationBriefVO;
import com.zhimu.slmp.ai.model.vo.LinkableBatchesVO;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;
import com.zhimu.slmp.ai.model.vo.SpeechResultVO;
import com.zhimu.slmp.ai.model.vo.StreamDoneVO;
import com.zhimu.slmp.ai.model.vo.StreamErrorVO;
import com.zhimu.slmp.ai.model.vo.StreamMetaVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.ai.util.InputModeStore;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * K01 AI 问答（技术文档 2.4.1、2.6.1–2.6.5）：会话载入与加载更早、关联批次、附图（最多 9 张，lib_media 上传 API-U05）、
 * 流式回答（正在思考 → 逐段追加 → 完成替换；50 ms 合并刷新；相邻事件 45 秒看门狗、发送起 125 秒总超时）、
 * 停止 / 离开中断 / 重新生成、评价复制、语音转文字结果填入、新对话与切换养殖场。
 * <p>
 * 回答来源由 {@link AnswerEngine} 决定（测试直连千问或服务端 K06/K07），本类不区分。只在主线程调用。
 */
public class AiChatViewModel extends BaseViewModel {

    static final long EVENT_TIMEOUT_SEC = 45;
    static final long TOTAL_TIMEOUT_SEC = 125;
    static final long FLUSH_MS = 50;
    static final long TIME_GAP_MINUTES = 30;
    static final int PAGE_SIZE = 20;
    static final long POLL_SEC = 5;
    static final int MAX_DUPLICATE_RETRIES = 3;
    static final long DUPLICATE_RETRY_SEC = 2;
    static final String KEY_INPUT = "ai_input_text";
    static final String KEY_ATTACH = "ai_attach_key";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiRepository repository;
    private final AnswerEngine engine;
    private final SpeechEngine speech;
    private final UploadManager.Factory uploads;
    private final FarmContext farm;
    private final NetworkStatus network;
    private final InputModeStore modeStore;
    @NonNull
    private Scheduler timer = Schedulers.computation();

    private final MutableLiveData<Long> sessionId = new MutableLiveData<>(null);
    private final MutableLiveData<AiSessionDetailVO> session = new MutableLiveData<>(null);
    private final MutableLiveData<List<ChatItem>> chatItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> hasMoreBefore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingEarlier = new MutableLiveData<>(false);
    private final MutableLiveData<LinkedBatchVO> pendingBatch = new MutableLiveData<>(null);
    private final MutableLiveData<String> batchChipText = new MutableLiveData<>(AiText.UNLINKED);
    private final MutableLiveData<Boolean> batchLinked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showEpidemicBanner = new MutableLiveData<>(false);
    private final MutableLiveData<LinkableBatchesVO> linkableBatches = new MutableLiveData<>(null);
    private final MutableLiveData<UiState> linkableState = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<StreamPhase> streamPhase = new MutableLiveData<>(StreamPhase.IDLE);
    private final MutableLiveData<Long> streamingMessageId = new MutableLiveData<>(null);
    private final MutableLiveData<CharSequence> streamingText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> showBackToBottom = new MutableLiveData<>(false);
    private MutableLiveData<String> inputText = new MutableLiveData<>("");
    private final MediatorLiveData<String> inputCountText = new MediatorLiveData<>();
    private final MediatorLiveData<List<UploadItem>> attachments = new MediatorLiveData<>();
    private final MediatorLiveData<String> attachHint = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> canAttach = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> canSend = new MediatorLiveData<>();
    private final MutableLiveData<InputMode> inputMode = new MutableLiveData<>(InputMode.TEXT);
    private final MediatorLiveData<Boolean> voiceEnabled = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> voiceRecognizing = new MutableLiveData<>(false);
    private final MutableLiveData<CourseContext> contextCourse = new MutableLiveData<>(null);
    private final MediatorLiveData<UiMessage> uploadMessage = new MediatorLiveData<>();

    private final SingleLiveEvent<Boolean> restoreInput = new SingleLiveEvent<>();
    private final SingleLiveEvent<CitationBriefVO> openKnowledge = new SingleLiveEvent<>();
    private final SingleLiveEvent<LinkedBatchVO[]> openInspectReport = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> openHistory = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> copyText = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> fillRecognized = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> scrollToBottom = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> earlierLoaded = new SingleLiveEvent<>();

    private final List<ChatRound> rounds = new ArrayList<>();
    /** 本次使用中插入的系统提示（换批次等），afterCount 为插入时的轮数 */
    private final List<Tip> tips = new ArrayList<>();
    private long nextLocalId = 1;
    private long nextTipId = -10;

    @Nullable
    private ChatRound streamRound;
    private boolean metaReceived;
    private boolean regenerating;
    @Nullable
    private Saved regenerateSaved;
    @Nullable
    private Disposable streamDisposable;
    @Nullable
    private Disposable totalTimer;
    @Nullable
    private Disposable flushTimer;
    @Nullable
    private Disposable pollDisposable;
    @Nullable
    private Disposable loadDisposable;
    @Nullable
    private Disposable speechDisposable;

    @Nullable
    private SavedStateHandle handle;
    @Nullable
    private UploadManager attach;
    @Nullable
    private String attachKey;
    private int attachSeq;
    private boolean revertToVoiceAfterSend;
    private boolean contextTruncated;
    @Nullable
    private String invalidBatchTip;

    @Nullable
    private Long lastFarmId;
    private final Observer<FarmContextVO> farmObserver = this::onFarmContext;

    private static final class Tip {
        final long id;
        final int afterCount;
        final String text;

        Tip(long id, int afterCount, String text) {
            this.id = id;
            this.afterCount = afterCount;
            this.text = text;
        }
    }

    /** 重新生成前的状态（开流前失败时恢复） */
    private static final class Saved {
        final ChatRound.Phase phase;
        final String raw;
        final String statusText;

        Saved(ChatRound.Phase phase, String raw, String statusText) {
            this.phase = phase;
            this.raw = raw;
            this.statusText = statusText;
        }
    }

    @Inject
    public AiChatViewModel(@NonNull AiRepository repository, @NonNull AnswerEngine engine,
                           @NonNull SpeechEngine speech, @NonNull UploadManager.Factory uploads,
                           @NonNull FarmContext farm, @NonNull NetworkStatus network,
                           @NonNull InputModeStore modeStore) {
        this.repository = repository;
        this.engine = engine;
        this.speech = speech;
        this.uploads = uploads;
        this.farm = farm;
        this.network = network;
        this.modeStore = modeStore;
        inputMode.setValue(modeStore.get());
        voiceEnabled.addSource(streamPhase, v -> updateVoiceEnabled());
        voiceEnabled.addSource(voiceRecognizing, v -> updateVoiceEnabled());
        farm.getContext().observeForever(farmObserver);
    }

    @VisibleForTesting
    void setTimeScheduler(@NonNull Scheduler scheduler) {
        this.timer = scheduler;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle h) {
        handle = h;
        inputText = h.getLiveData(KEY_INPUT, "");
        inputCountText.addSource(inputText, t -> inputCountText.setValue(AiText.inputCount(t == null ? 0 : t.length())));
        canSend.addSource(inputText, t -> updateCanSend());
        canSend.addSource(streamPhase, p -> updateCanSend());
        canSend.addSource(attachments, a -> updateCanSend());
        attachHint.addSource(attachments, list -> attachHint.setValue(hintOf(list)));
        canAttach.addSource(attachments, list -> canAttach.setValue(list == null || list.size() < AiText.MAX_IMAGES));
        String key = h.get(KEY_ATTACH);
        if (key == null) {
            key = "ai_attach_0";
            h.set(KEY_ATTACH, key);
        }
        useAttach(uploads.create(h, key, MediaScene.AI_IMAGE, AiText.MAX_IMAGES), key);
    }

    // ===== getter =====

    public LiveData<Long> getSessionId() {
        return sessionId;
    }

    public LiveData<AiSessionDetailVO> getSession() {
        return session;
    }

    public LiveData<List<ChatItem>> getChatItems() {
        return chatItems;
    }

    public LiveData<Boolean> getHasMoreBefore() {
        return hasMoreBefore;
    }

    public LiveData<Boolean> getLoadingEarlier() {
        return loadingEarlier;
    }

    public LiveData<LinkedBatchVO> getPendingBatch() {
        return pendingBatch;
    }

    public LiveData<String> getBatchChipText() {
        return batchChipText;
    }

    public LiveData<Boolean> getBatchLinked() {
        return batchLinked;
    }

    public LiveData<Boolean> getShowEpidemicBanner() {
        return showEpidemicBanner;
    }

    public LiveData<LinkableBatchesVO> getLinkableBatches() {
        return linkableBatches;
    }

    public LiveData<UiState> getLinkableState() {
        return linkableState;
    }

    public LiveData<StreamPhase> getStreamPhase() {
        return streamPhase;
    }

    /** 正在生成的轮次（本地 ID，与 ChatItem.roundId 对应） */
    public LiveData<Long> getStreamingMessageId() {
        return streamingMessageId;
    }

    public LiveData<CharSequence> getStreamingText() {
        return streamingText;
    }

    public LiveData<Boolean> getShowBackToBottom() {
        return showBackToBottom;
    }

    public MutableLiveData<String> getInputText() {
        return inputText;
    }

    public LiveData<String> getInputCountText() {
        return inputCountText;
    }

    public LiveData<List<UploadItem>> getAttachments() {
        return attachments;
    }

    public LiveData<String> getAttachHint() {
        return attachHint;
    }

    public LiveData<Boolean> getCanAttach() {
        return canAttach;
    }

    public LiveData<Boolean> getCanSend() {
        return canSend;
    }

    public LiveData<InputMode> getInputMode() {
        return inputMode;
    }

    public LiveData<Boolean> getVoiceEnabled() {
        return voiceEnabled;
    }

    public LiveData<Boolean> getVoiceRecognizing() {
        return voiceRecognizing;
    }

    public LiveData<CourseContext> getContextCourse() {
        return contextCourse;
    }

    public LiveData<UiMessage> getUploadMessage() {
        return uploadMessage;
    }

    public SingleLiveEvent<Boolean> getRestoreInput() {
        return restoreInput;
    }

    public SingleLiveEvent<CitationBriefVO> getOpenKnowledge() {
        return openKnowledge;
    }

    /** 去异常上报：数组第 0 项为当前关联批次（未关联为 null） */
    public SingleLiveEvent<LinkedBatchVO[]> getOpenInspectReport() {
        return openInspectReport;
    }

    public SingleLiveEvent<Boolean> getOpenHistory() {
        return openHistory;
    }

    public SingleLiveEvent<String> getCopyText() {
        return copyText;
    }

    public SingleLiveEvent<String> getFillRecognized() {
        return fillRecognized;
    }

    public SingleLiveEvent<Boolean> getScrollToBottom() {
        return scrollToBottom;
    }

    /** 加载更早完成（页面恢复阅读位置） */
    public SingleLiveEvent<Boolean> getEarlierLoaded() {
        return earlierLoaded;
    }

    /** 测试直连模式（评价、重新生成只在本机） */
    public boolean isDirect() {
        return engine.isDirect();
    }

    // ===== 会话 =====

    /** K02 选会话后载入：并行请求 K02、K03 后滚到最后一轮 */
    public void loadSession(long id) {
        interruptForLeave();
        resetConversation();
        sessionId.setValue(id);
        setUiState(UiState.loading());
        if (loadDisposable != null) {
            loadDisposable.dispose();
        }
        loadDisposable = launch(Single.zip(repository.getSession(id), repository.getMessages(id, null, PAGE_SIZE),
                Pair::new), p -> {
            applySession(p.first);
            hasMoreBefore.setValue(p.second.isHasMore());
            for (AiMessageVO m : p.second.getList()) {
                rounds.add(loadedRound(m));
            }
            setUiState(UiState.content());
            rebuild();
            startPollingRemote();
            scrollToBottom.setValue(true);
        }, e -> {
            ApiException ae = ApiException.from(e);
            if (ae.getCode() == ErrorCodes.NOT_FOUND) {
                setUiState(UiState.noPermission(ae.getMessage(), ae.getCode()));
            } else {
                setUiState(UiState.error(ae.getMessage(), ae.getTraceId(), ae.getCode()));
            }
        });
    }

    @Override
    public void retry() {
        Long id = sessionId.getValue();
        if (id != null) {
            loadSession(id);
        } else {
            setUiState(UiState.content());
        }
    }

    /** 滚到顶部：加载更早的 20 轮 */
    public void loadEarlier() {
        Long id = sessionId.getValue();
        if (id == null || !Boolean.TRUE.equals(hasMoreBefore.getValue()) || Boolean.TRUE.equals(loadingEarlier.getValue())
                || rounds.isEmpty() || rounds.get(0).message == null) {
            return;
        }
        int before = rounds.get(0).message.getRoundNo();
        loadingEarlier.setValue(true);
        launch(repository.getMessages(id, before, PAGE_SIZE), (AiMessageSliceVO slice) -> {
            loadingEarlier.setValue(false);
            hasMoreBefore.setValue(slice.isHasMore());
            List<ChatRound> older = new ArrayList<>();
            for (AiMessageVO m : slice.getList()) {
                older.add(loadedRound(m));
            }
            rounds.addAll(0, older);
            for (int i = 0; i < tips.size(); i++) {
                Tip t = tips.get(i);
                tips.set(i, new Tip(t.id, t.afterCount + older.size(), t.text));
            }
            rebuild();
            earlierLoaded.setValue(true);
        }, e -> {
            loadingEarlier.setValue(false);
            handleError(e);
        });
    }

    /** 点 add_comment：已有问答才清空，批次转为 pendingBatch */
    public void startNewChat() {
        if (rounds.isEmpty() && sessionId.getValue() == null) {
            return;
        }
        interruptForLeave();
        LinkedBatchVO keep = currentBatch();
        resetConversation();
        pendingBatch.setValue(keep);
        refreshChip();
        rebuild();
        showMessage(AiText.MSG_NEW_CHAT);
    }

    /**
     * 外部进入（T01 推荐问题、K05“问 AI”、K02“新对话”）：回到新会话；课程背景与推荐问题按需带入（不自动发送）。
     */
    public void newSession(@Nullable CourseContext course, @Nullable String question) {
        interruptForLeave();
        LinkedBatchVO keep = currentBatch();
        boolean hadContent = !rounds.isEmpty() || sessionId.getValue() != null;
        resetConversation();
        pendingBatch.setValue(keep);
        contextCourse.setValue(course);
        refreshChip();
        rebuild();
        if (question != null && !question.trim().isEmpty()) {
            inputText.setValue(question.trim());
            restoreInput.setValue(true);
        }
        if (hadContent && course == null && question == null) {
            showMessage(AiText.MSG_NEW_CHAT);
        }
    }

    /** K02 删除了会话：当前正在看的会话被删时回到新对话 */
    public void onSessionsDeleted(@NonNull long[] ids) {
        Long current = sessionId.getValue();
        if (current == null) {
            return;
        }
        for (long id : ids) {
            if (id == current) {
                newSession(null, null);
                return;
            }
        }
    }

    public void clearCourseContext() {
        contextCourse.setValue(null);
    }

    public void requestHistory() {
        interruptForLeave();
        openHistory.setValue(true);
    }

    // ===== 关联批次 =====

    public void fetchLinkable() {
        linkableState.setValue(UiState.loading());
        launch(repository.getLinkableBatches(), v -> {
            linkableBatches.setValue(v);
            linkableState.setValue(UiState.content());
        }, e -> linkableState.setValue(UiState.error(null, null, ApiException.from(e).getCode())));
    }

    /** 面板单选（null 为不关联）：新会话只写 pendingBatch；已有会话先更新 Chip 再调 API-K04，失败回滚 */
    public void selectBatch(@Nullable LinkedBatchVO batch) {
        LinkedBatchVO before = currentBatch();
        if (sameBatch(before, batch)) {
            return;
        }
        Long id = sessionId.getValue();
        if (id == null) {
            pendingBatch.setValue(batch);
            refreshChip();
            if (!rounds.isEmpty()) {
                addTip(AiText.batchChangedTip(batch));
            }
            return;
        }
        AiSessionDetailVO prev = session.getValue();
        AiSessionDetailVO optimistic = copySession(prev, batch);
        session.setValue(optimistic);
        refreshChip();
        launch(repository.changeBatch(id, batch == null ? null : batch.getBatchId()), result -> {
            session.setValue(result);
            invalidBatchTip = null;
            refreshChip();
            if (!rounds.isEmpty()) {
                addTip(AiText.batchChangedTip(batch));
            } else {
                rebuild();
            }
        }, e -> {
            ApiException ae = ApiException.from(e);
            if (ae.getCode() == ErrorCodes.PEN_FORBIDDEN) {
                session.setValue(copySession(prev, null));
                refreshChip();
                addTip(AiText.MSG_PEN_FORBIDDEN);
                return;
            }
            session.setValue(prev);
            refreshChip();
            if (ae.getCode() == ErrorCodes.STATE_CHANGED) {
                showMessage(AiText.MSG_NOT_IN_STOCK_RETRY);
            } else {
                showMessage(ae.getMessage());
            }
        });
    }

    /** 当前关联批次：已有会话取会话的批次（VALID 时），新会话取 pendingBatch */
    @Nullable
    public LinkedBatchVO currentBatch() {
        if (sessionId.getValue() != null && session.getValue() != null) {
            AiSessionDetailVO s = session.getValue();
            return AiCodes.LINK_VALID.equals(s.getBatchLinkState()) ? s.getBatch() : null;
        }
        return pendingBatch.getValue();
    }

    public void onInspectReportClick() {
        interruptForLeave();
        openInspectReport.setValue(new LinkedBatchVO[]{currentBatch()});
    }

    // ===== 输入与附图 =====

    public void addImages(@NonNull List<Uri> uris) {
        if (attach != null) {
            attach.addUris(uris);
        }
    }

    public void addCapturedFiles(@NonNull List<String> paths) {
        if (attach != null) {
            attach.addCapturedFiles(paths);
        }
    }

    public void removeImage(@NonNull String itemId) {
        if (attach != null) {
            attach.remove(itemId);
        }
    }

    public void retryUpload(@NonNull String itemId) {
        if (attach != null) {
            attach.retry(itemId);
        }
    }

    /** 还能附几张（照片选择器上限） */
    public int remainingImages() {
        return attach == null ? AiText.MAX_IMAGES : attach.getRemaining();
    }

    /** 已满 9 张时提示，返回能否继续选图 */
    public boolean checkCanAttach() {
        if (remainingImages() <= 0) {
            showMessage(AiText.MSG_IMAGES_FULL);
            return false;
        }
        return true;
    }

    /** 用户主动切换输入方式（记住） */
    public void setInputMode(@NonNull InputMode mode) {
        revertToVoiceAfterSend = false;
        inputMode.setValue(mode);
        modeStore.set(mode);
    }

    public void onScrolledAway(boolean away) {
        if (!Objects.equals(showBackToBottom.getValue(), away)) {
            showBackToBottom.setValue(away);
        }
    }

    // ===== 发送 =====

    /** 点发送（View 层已防连点）：插入提问与“正在思考…”，清空输入栏，等附图上传完成后请求 */
    @MainThread
    public void send() {
        if (!Boolean.TRUE.equals(canSend.getValue()) || attach == null) {
            return;
        }
        String text = trimmed(inputText.getValue());
        UploadManager used = attach;
        List<UploadItem> items = new ArrayList<>(used.current());
        AskInput input = new AskInput(sessionId.getValue(), text, attachesOf(items), currentBatch(),
                contextCourse.getValue(), IdemKey.newKey());
        ChatRound r = new ChatRound(nextLocalId++, input, ChatRound.Phase.WAITING);
        r.askedAt = now();
        rounds.add(r);
        inputText.setValue("");
        r.uploads = used;
        r.uploadsKey = attachKey;
        newAttach();
        if (revertToVoiceAfterSend) {
            revertToVoiceAfterSend = false;
            inputMode.setValue(InputMode.VOICE);
        }
        beginRound(r);
        scrollToBottom.setValue(true);
        if (used.hasUploading()) {
            waitUploads(r, used);
        } else {
            askAfterUploads(r, used);
        }
    }

    private void waitUploads(@NonNull ChatRound r, @NonNull UploadManager used) {
        Observer<List<UploadItem>> obs = new Observer<List<UploadItem>>() {
            @Override
            public void onChanged(List<UploadItem> list) {
                if (used.hasUploading()) {
                    return;
                }
                used.getItems().removeObserver(this);
                r.uploadObserver = null;
                if (streamRound == r) {
                    askAfterUploads(r, used);
                }
            }
        };
        r.uploadObserver = obs;
        used.getItems().observeForever(obs);
    }

    private void askAfterUploads(@NonNull ChatRound r, @NonNull UploadManager used) {
        if (used.hasFailed()) {
            withdraw(r, AiText.MSG_UPLOAD_FAILED);
            return;
        }
        r.input = r.input.withImages(attachesOf(used.current()));
        ask(r);
    }

    private void ask(@NonNull ChatRound r) {
        if (!network.isConnected()) {
            r.phase = ChatRound.Phase.SEND_FAILED;
            r.noNetwork = true;
            r.touch();
            endStream();
            rebuild();
            return;
        }
        subscribeStream(r, engine.ask(r.input, historyBefore(r)), false);
    }

    /** 发送失败后的“重试”：沿用原幂等键 */
    public void retrySend(long roundId) {
        ChatRound r = find(roundId);
        if (r == null || r.phase != ChatRound.Phase.SEND_FAILED || streamRound != null) {
            return;
        }
        r.noNetwork = false;
        if (r.input.getSessionId() == null && sessionId.getValue() != null) {
            r.input = r.input.withSession(sessionId.getValue());
        }
        beginRound(r);
        ask(r);
    }

    /** 点“停止”：保留已生成的文字，标“已停止生成” */
    public void stop() {
        ChatRound r = streamRound;
        if (r == null) {
            return;
        }
        if (!metaReceived) {
            abortBeforeMeta(r);
            return;
        }
        disposeStream();
        r.phase = ChatRound.Phase.STOPPED;
        r.statusText = AiText.MSG_STOPPED;
        r.touch();
        endStream();
        rebuild();
    }

    /** 离开 K01（切到课堂、切底部导航、进入 K02/K03/I05、新对话、切换养殖场、返回退出）：中断生成 */
    public void interruptForLeave() {
        ChatRound r = streamRound;
        if (r == null) {
            return;
        }
        if (!metaReceived) {
            abortBeforeMeta(r);
            return;
        }
        disposeStream();
        r.phase = ChatRound.Phase.INTERRUPTED;
        r.statusText = AiText.MSG_INTERRUPTED;
        r.touch();
        endStream();
        rebuild();
    }

    /** 还没开流（等附图、等服务端受理）就停止或离开：新提问撤回并放回输入栏；重新生成恢复原状态 */
    private void abortBeforeMeta(@NonNull ChatRound r) {
        disposeStream();
        if (regenerating) {
            restoreRegenerate(r);
            endStream();
            rebuild();
            return;
        }
        withdraw(r, null);
    }

    /** 点“重新生成”或中断、超时、不可用的状态行；有回答在生成时不响应 */
    public void regenerate(long roundId) {
        if (streamRound != null) {
            return;
        }
        ChatRound r = find(roundId);
        if (r == null || !r.canRegenerate()) {
            return;
        }
        regenerateSaved = new Saved(r.phase, r.buffer.raw(), r.statusText);
        subscribeStream(r, engine.regenerate(r.messageId, r.input, historyBefore(r)), true);
    }

    // ===== 流式处理 =====

    public void toggleProcess(long roundId) {
        ChatRound r = find(roundId);
        if (r == null || r.processText().isEmpty()) return;
        r.processExpanded = !r.processExpanded;
        r.touch();
        rebuild();
    }

    private void beginRound(@NonNull ChatRound r) {
        streamRound = r;
        metaReceived = false;
        r.phase = ChatRound.Phase.WAITING;
        r.buffer.clear();
        r.statusText = null;
        r.pendingCitations.clear();
        r.progressMessages.clear();
        r.analysisSummary = "";
        r.processExpanded = true;
        r.touch();
        streamPhase.setValue(StreamPhase.WAITING);
        streamingMessageId.setValue(r.localId);
        streamingText.setValue("");
        rebuild();
    }

    private void subscribeStream(@NonNull ChatRound r, @NonNull Flowable<AiStreamEvent> source, boolean regenerate) {
        disposeStream();
        beginRound(r);
        regenerating = regenerate;
        streamDisposable = source
                .timeout(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS, timer)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> onEvent(r, e), t -> onStreamError(r, t), () -> onStreamComplete(r));
        totalTimer = Completable.timer(TOTAL_TIMEOUT_SEC, TimeUnit.SECONDS, timer)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> onTotalTimeout(r));
    }

    private void onEvent(@NonNull ChatRound r, @NonNull AiStreamEvent e) {
        if (r != streamRound) {
            return;
        }
        switch (e.getType()) {
            case PROGRESS:
                if (e.getProgress() != null && !e.getProgress().getMessage().trim().isEmpty()) {
                    r.progressMessages.put(e.getProgress().getStage(), e.getProgress().getMessage());
                    r.touch();
                    rebuild();
                }
                break;
            case ANALYSIS_SUMMARY:
                r.analysisSummary = e.getText();
                r.touch();
                rebuild();
                break;
            case META:
                onMeta(r, e.getMeta());
                break;
            case DELTA:
                r.buffer.append(e.getText());
                if (r.phase == ChatRound.Phase.WAITING) {
                    if (!r.buffer.hasText()) {
                        return;
                    }
                    r.phase = ChatRound.Phase.STREAMING;
                    r.touch();
                    streamPhase.setValue(StreamPhase.STREAMING);
                    flush();
                    rebuild();
                } else {
                    scheduleFlush();
                }
                break;
            case CITATION:
                r.pendingCitations.addAll(e.getCitations());
                break;
            case DONE:
                onDone(r, e.getDone());
                break;
            case ERROR:
                onErrorEvent(r, e.getError());
                break;
            case HEARTBEAT:
            default:
                break;
        }
    }

    private void onMeta(@NonNull ChatRound r, @Nullable StreamMetaVO meta) {
        metaReceived = true;
        regenerateSaved = null;
        releaseUploads(r);
        if (meta == null) {
            return;
        }
        if (meta.getMessageId() != 0) {
            r.messageId = meta.getMessageId();
        }
        if (meta.isSessionCreated() && meta.getSessionId() > 0) {
            long id = meta.getSessionId();
            sessionId.setValue(id);
            for (ChatRound each : rounds) {
                if (each.input.getSessionId() == null) {
                    each.input = each.input.withSession(id);
                }
            }
            refreshSessionQuietly(id);
        }
        String state = meta.getBatchLinkState();
        if (AiCodes.LINK_NOT_IN_STOCK.equals(state) || AiCodes.LINK_NO_PERMISSION.equals(state)) {
            LinkedBatchVO b = r.input.getBatch();
            pendingBatch.setValue(null);
            AiSessionDetailVO s = session.getValue();
            if (s != null) {
                session.setValue(copySession(s, null));
            }
            refreshChip();
            addTip(AiText.invalidBatchTip(state, b == null ? null : b.getBatchNo()));
        }
    }

    private void onDone(@NonNull ChatRound r, @Nullable StreamDoneVO done) {
        AiMessageVO m = done == null ? null : done.getMessage();
        if (m == null) {
            onStreamComplete(r);
            return;
        }
        if (m.getCitations().isEmpty() && !r.pendingCitations.isEmpty()) {
            m.setCitations(new ArrayList<>(r.pendingCitations));
        }
        r.apply(m);
        r.phase = ChatRound.Phase.ANSWERED;
        r.processExpanded = false;
        String type = done.getAnswerType() != null ? done.getAnswerType() : m.getAnswerType();
        if (AiCodes.ANSWER_EPIDEMIC.equals(type)) {
            showEpidemicBanner.setValue(true);
        }
        endStream();
        rebuild();
    }

    private void onErrorEvent(@NonNull ChatRound r, @Nullable StreamErrorVO err) {
        String status = err == null ? null : err.getStatus();
        if (err != null && err.getCode() == ErrorCodes.AI_TIMEOUT && status == null) {
            status = AiCodes.STATUS_TIMEOUT;
        }
        if (AiCodes.STATUS_TIMEOUT.equals(status)) {
            r.phase = ChatRound.Phase.TIMEOUT;
        } else if (AiCodes.STATUS_FAILED.equals(status)) {
            r.phase = ChatRound.Phase.FAILED;
        } else {
            r.phase = ChatRound.Phase.INTERRUPTED;
        }
        r.statusText = AiText.statusLine(status, err == null ? null : err.getMessage(), r.buffer.hasText());
        r.touch();
        endStream();
        rebuild();
    }

    private void onStreamError(@NonNull ChatRound r, @NonNull Throwable t) {
        if (r != streamRound) {
            return;
        }
        boolean watchdog = t instanceof TimeoutException;
        ApiException e = watchdog ? ApiException.network(t) : ApiException.from(t);
        if (!metaReceived) {
            int code = e.getCode();
            if (regenerating) {
                restoreRegenerate(r);
                endStream();
                rebuild();
                if (code == ErrorCodes.DUPLICATE_SUBMIT) {
                    fixFromServer(r);
                } else {
                    showMessage(e.getMessage());
                }
                return;
            }
            if (code == ErrorCodes.DUPLICATE_SUBMIT) {
                disposeStream();
                if (r.duplicateRetries++ < MAX_DUPLICATE_RETRIES) {
                    streamDisposable = Completable.timer(DUPLICATE_RETRY_SEC, TimeUnit.SECONDS, timer)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> {
                                if (streamRound == r) {
                                    ask(r);
                                }
                            });
                    return;
                }
                markSendFailed(r);
                return;
            }
            if (e.isNetwork()) {
                markSendFailed(r);
                return;
            }
            if (code == ErrorCodes.NOT_FOUND) {
                withdraw(r, null);
                setUiState(UiState.noPermission(e.getMessage(), code));
                return;
            }
            if (ErrorCodes.isAuthHandled(code)) {
                withdraw(r, null);
                return;
            }
            withdraw(r, e.getMessage());
            return;
        }
        r.phase = ChatRound.Phase.INTERRUPTED;
        r.statusText = AiText.MSG_INTERRUPTED;
        r.touch();
        endStream();
        rebuild();
        fixFromServer(r);
    }

    private void onStreamComplete(@NonNull ChatRound r) {
        if (r != streamRound) {
            return;
        }
        if (!metaReceived) {
            markSendFailed(r);
            return;
        }
        r.phase = ChatRound.Phase.INTERRUPTED;
        r.statusText = AiText.MSG_INTERRUPTED;
        r.touch();
        endStream();
        rebuild();
        fixFromServer(r);
    }

    /** 发送起 125 秒仍无 done、error：取消，服务端模式再调 API-K08 */
    private void onTotalTimeout(@NonNull ChatRound r) {
        if (r != streamRound) {
            return;
        }
        if (!metaReceived) {
            disposeStream();
            if (regenerating) {
                restoreRegenerate(r);
                endStream();
                rebuild();
                return;
            }
            markSendFailed(r);
            return;
        }
        disposeStream();
        r.phase = ChatRound.Phase.TIMEOUT;
        r.statusText = AiText.MSG_TIMEOUT;
        r.touch();
        endStream();
        rebuild();
        if (!engine.isDirect() && r.messageId > 0) {
            launch(repository.getMessage(r.messageId), m -> {
                if (AiCodes.STATUS_ANSWERED.equals(m.getStatus())) {
                    r.apply(m);
                    rebuild();
                }
            }, err -> {
            });
        }
    }

    /** 服务端保存的轮次校正（中断后补拉 API-K08） */
    private void fixFromServer(@NonNull ChatRound r) {
        if (engine.isDirect() || r.messageId <= 0) {
            return;
        }
        launch(repository.getMessage(r.messageId), m -> {
            if (streamRound == r || AiCodes.STATUS_ANSWERING.equals(m.getStatus())) {
                return;
            }
            r.apply(m);
            if (AiCodes.ANSWER_EPIDEMIC.equals(m.getAnswerType()) && AiCodes.STATUS_ANSWERED.equals(m.getStatus())) {
                showEpidemicBanner.setValue(true);
            }
            rebuild();
        }, err -> {
        });
    }

    private void markSendFailed(@NonNull ChatRound r) {
        disposeStream();
        r.phase = ChatRound.Phase.SEND_FAILED;
        r.noNetwork = !network.isConnected();
        r.touch();
        endStream();
        rebuild();
    }

    private void restoreRegenerate(@NonNull ChatRound r) {
        Saved s = regenerateSaved;
        regenerateSaved = null;
        if (s == null) {
            return;
        }
        r.phase = s.phase;
        r.buffer.clear();
        r.buffer.append(s.raw);
        r.statusText = s.statusText;
        r.touch();
    }

    /** 撤回提问与回答项，内容放回输入栏（42901、40001、40305 等开流前错误，附图上传失败） */
    private void withdraw(@NonNull ChatRound r, @Nullable String message) {
        disposeStream();
        rounds.remove(r);
        endStream();
        String current = inputText.getValue();
        if (current == null || current.trim().isEmpty()) {
            inputText.setValue(r.input.getQuestionText());
        }
        UploadManager used = r.uploads;
        r.uploads = null;
        if (r.uploadObserver != null && used != null) {
            used.getItems().removeObserver(r.uploadObserver);
            r.uploadObserver = null;
        }
        if (used != null && attach != null && attach.current().isEmpty() && r.uploadsKey != null) {
            UploadManager empty = attach;
            String emptyKey = attachKey;
            useAttach(used, r.uploadsKey);
            discardAttach(empty, emptyKey);
        } else if (used != null) {
            discardAttach(used, r.uploadsKey);
        }
        rebuild();
        restoreInput.setValue(true);
        if (message != null && !message.isEmpty()) {
            showMessage(message);
        }
    }

    private void scheduleFlush() {
        if (flushTimer != null && !flushTimer.isDisposed()) {
            return;
        }
        flushTimer = Completable.timer(FLUSH_MS, TimeUnit.MILLISECONDS, timer)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::flush);
    }

    private void flush() {
        flushTimer = null;
        ChatRound r = streamRound;
        if (r != null) {
            streamingText.setValue(r.buffer.visible());
        }
    }

    private void disposeStream() {
        if (streamDisposable != null) {
            streamDisposable.dispose();
            streamDisposable = null;
        }
        if (totalTimer != null) {
            totalTimer.dispose();
            totalTimer = null;
        }
        if (flushTimer != null) {
            flushTimer.dispose();
            flushTimer = null;
        }
    }

    private void endStream() {
        disposeStream();
        streamRound = null;
        metaReceived = false;
        regenerating = false;
        streamPhase.setValue(StreamPhase.IDLE);
        streamingMessageId.setValue(null);
    }

    // ===== 评价与复制 =====

    /** 点 thumb_up / thumb_down：再点已选项取消（发 null） */
    public void rate(long roundId, @NonNull String rating) {
        ChatRound r = find(roundId);
        if (r == null || r.message == null || !r.message.isCanRate()) {
            return;
        }
        AiMessageVO m = r.message;
        String next = rating.equals(m.getRating()) ? null : rating;
        // 测试直连生成的轮次 ID 为负数，只在本机记录；服务端轮次调 API-K09
        if (m.getId() < 0) {
            m.setRating(next);
            r.touch();
            rebuild();
            showMessage(AiText.MSG_RATED);
            return;
        }
        launch(repository.rate(m.getId(), next), result -> {
            m.setRating(result.getRating());
            m.setRatedAt(result.getRatedAt());
            r.touch();
            rebuild();
            showMessage(AiText.MSG_RATED);
        }, e -> {
            ApiException ae = ApiException.from(e);
            showMessage(ae.getMessage());
        });
    }

    public void copyAnswer(long roundId) {
        ChatRound r = find(roundId);
        if (r == null || r.message == null || !r.message.isCanCopy()) {
            return;
        }
        copyText.setValue(AiText.copyText(r.message.getAnswerText()));
        showMessage(AiText.MSG_COPIED);
    }

    /** 点角标或来源 Chip：先中断，再进 K03 */
    public void onCitationClick(@NonNull CitationBriefVO c) {
        interruptForLeave();
        openKnowledge.setValue(c);
    }

    // ===== 语音 =====

    /** 录音完成，交给识别（服务端 API-K10 或直连百炼）；结果追加到输入框之后，临时切回文字 */
    public void recognizeSpeech(@NonNull File wav, long durationMs) {
        if (Boolean.TRUE.equals(voiceRecognizing.getValue())) {
            //noinspection ResultOfMethodCallIgnored 临时文件
            wav.delete();
            return;
        }
        voiceRecognizing.setValue(true);
        speechDisposable = speech.recognize(wav, (int) durationMs)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> {
                    //noinspection ResultOfMethodCallIgnored 临时文件
                    wav.delete();
                })
                .subscribe(this::onRecognized, this::onRecognizeFailed);
    }

    private void onRecognized(@NonNull SpeechResultVO r) {
        voiceRecognizing.setValue(false);
        String text = r.getText() == null ? "" : r.getText().trim();
        if (!r.isRecognized() || text.isEmpty()) {
            showMessage(AiText.MSG_NOT_HEARD);
            return;
        }
        String current = inputText.getValue() == null ? "" : inputText.getValue();
        String merged = current + text;
        if (merged.length() > AiText.MAX_QUESTION) {
            merged = merged.substring(0, AiText.MAX_QUESTION);
        }
        inputText.setValue(merged);
        if (inputMode.getValue() == InputMode.VOICE) {
            revertToVoiceAfterSend = true;
            inputMode.setValue(InputMode.TEXT);
        }
        fillRecognized.setValue(merged);
    }

    private void onRecognizeFailed(@NonNull Throwable t) {
        voiceRecognizing.setValue(false);
        showMessage(speechError(ApiException.from(t)));
    }

    @NonNull
    static String speechError(@NonNull ApiException e) {
        if (e.isNetwork()) {
            return e.isTimeout() ? ApiException.MSG_TIMEOUT : AiText.MSG_NETWORK_FAILED;
        }
        switch (e.getCode()) {
            case ErrorCodes.PARAM_INVALID:
                return AiText.MSG_TOO_SHORT;
            case ErrorCodes.TOO_FREQUENT:
                return AiText.MSG_TOO_FREQUENT;
            case ErrorCodes.AI_TIMEOUT:
                return AiText.MSG_NOT_HEARD;
            case ErrorCodes.AI_UNAVAILABLE:
                return AiText.MSG_ASR_UNAVAILABLE;
            default:
                return e.getMessage() == null ? AiText.MSG_ASR_UNAVAILABLE : e.getMessage();
        }
    }

    public void onVoiceTooShort() {
        showMessage(AiText.MSG_TOO_SHORT);
    }

    public void onVoiceUnavailable() {
        showMessage(AiText.MSG_MIC_UNAVAILABLE);
    }

    // ===== 切换养殖场 =====

    private void onFarmContext(@Nullable FarmContextVO ctx) {
        Long id = ctx == null ? null : ctx.getFarmId();
        if (lastFarmId != null && !lastFarmId.equals(id)) {
            onFarmChanged();
        }
        lastFarmId = id;
    }

    /** 切换养殖场：取消生成，回到新会话，批次清空 */
    public void onFarmChanged() {
        interruptForLeave();
        resetConversation();
        pendingBatch.setValue(null);
        contextCourse.setValue(null);
        refreshChip();
        rebuild();
    }

    // ===== 列表组装 =====

    private void rebuild() {
        List<ChatItem> list = new ArrayList<>();
        ChatRound firstInContext = null;
        if (contextTruncated) {
            for (ChatRound r : rounds) {
                if (r.message != null && r.message.isInContext()) {
                    firstInContext = r;
                    break;
                }
            }
        }
        String prevAsked = null;
        for (int i = 0; i < rounds.size(); i++) {
            addTips(list, i);
            ChatRound r = rounds.get(i);
            if (r == firstInContext && i > 0) {
                list.add(ChatItem.divider(r.localId * 8 + 4));
            }
            if (i > 0 && r.loaded && rounds.get(i - 1).loaded) {
                String a = batchNoOf(rounds.get(i - 1));
                String b = batchNoOf(r);
                if (!Objects.equals(a, b)) {
                    list.add(ChatItem.tip(r.localId * 8 + 5, b == null ? AiText.batchChangedTip(null)
                            : "已改为关联 " + b + "，之后的提问按这个批次回答"));
                }
            }
            if (r.askedAt != null && (prevAsked == null || minutesBetween(prevAsked, r.askedAt) >= TIME_GAP_MINUTES)) {
                list.add(ChatItem.time(r.localId * 8 + 3, DateFormatter.formatDateTime(r.askedAt)));
            }
            if (r.askedAt != null) {
                prevAsked = r.askedAt;
            }
            list.add(questionItem(r));
            list.add(answerItem(r).withProcess(r.processText(), r.processExpanded));
        }
        addTips(list, rounds.size());
        if (invalidBatchTip != null) {
            list.add(ChatItem.tip(-1, invalidBatchTip));
        }
        chatItems.setValue(Collections.unmodifiableList(list));
    }

    private void addTips(@NonNull List<ChatItem> list, int count) {
        for (Tip t : tips) {
            if (t.afterCount == count) {
                list.add(ChatItem.tip(t.id, t.text));
            }
        }
    }

    @NonNull
    private ChatItem questionItem(@NonNull ChatRound r) {
        List<String> local = new ArrayList<>();
        for (AskInput.Attach a : r.input.getImages()) {
            local.add(a.displayUri);
        }
        List<com.zhimu.slmp.common.model.vo.FileVO> remote = local.isEmpty() && r.message != null
                && !r.remoteImages.isEmpty() ? r.message.getImages() : Collections.emptyList();
        // 本机提问显示用户输入的原文；载入的历史轮次用服务端保存的问题
        String text = r.loaded && r.message != null && r.message.getQuestionText() != null
                ? r.message.getQuestionText() : r.input.getQuestionText();
        return ChatItem.question(r.localId * 8 + 1, r.localId, r.version, text, local, remote);
    }

    @NonNull
    private ChatItem answerItem(@NonNull ChatRound r) {
        boolean enabled = streamRound == null || streamRound == r;
        long id = r.localId * 8 + 2;
        String partial = r.buffer.visible();
        switch (r.phase) {
            case ANSWERED:
                return ChatItem.answer(id, r.localId, r.version, ChatItem.AnswerState.DONE, r.message, null, null,
                        enabled);
            case WAITING:
            case REMOTE_ANSWERING:
                return ChatItem.answer(id, r.localId, r.version, ChatItem.AnswerState.THINKING, null, null, null,
                        enabled);
            case STREAMING:
                return ChatItem.answer(id, r.localId, r.version, ChatItem.AnswerState.STREAMING, null, null, null,
                        enabled);
            case STOPPED:
                return ChatItem.answer(id, r.localId, r.version, ChatItem.AnswerState.STOPPED, r.message, partial,
                        AiText.MSG_STOPPED, enabled);
            case SEND_FAILED:
                return ChatItem.answer(id, r.localId, r.version, ChatItem.AnswerState.SEND_FAILED, null, null,
                        r.noNetwork ? AiText.MSG_NO_NETWORK : AiText.MSG_NETWORK_FAILED, enabled);
            case INTERRUPTED:
            case TIMEOUT:
            case FAILED:
            default: {
                String status = r.phase == ChatRound.Phase.TIMEOUT ? AiCodes.STATUS_TIMEOUT
                        : r.phase == ChatRound.Phase.FAILED ? AiCodes.STATUS_FAILED : AiCodes.STATUS_INTERRUPTED;
                String line = r.statusText != null ? r.statusText
                        : AiText.statusLine(status, null, !partial.trim().isEmpty());
                return ChatItem.answer(id, r.localId, r.version, ChatItem.AnswerState.ERROR, r.message, partial, line,
                        enabled);
            }
        }
    }

    // ===== 内部工具 =====

    private void addTip(@Nullable String text) {
        if (text == null) {
            return;
        }
        tips.add(new Tip(nextTipId--, rounds.size(), text));
        rebuild();
        scrollToBottom.setValue(true);
    }

    private void applySession(@NonNull AiSessionDetailVO s) {
        session.setValue(s);
        contextTruncated = s.isContextTruncated();
        invalidBatchTip = AiText.invalidBatchTip(s.getBatchLinkState(), s.getInvalidBatchNo());
        if (s.isHasEpidemicAlert()) {
            showEpidemicBanner.setValue(true);
        }
        refreshChip();
    }

    private void refreshSessionQuietly(long id) {
        launch(repository.getSession(id), s -> {
            if (Objects.equals(sessionId.getValue(), id)) {
                session.setValue(s);
                contextTruncated = s.isContextTruncated();
                refreshChip();
            }
        }, e -> {
        });
    }

    private void refreshChip() {
        LinkedBatchVO b = currentBatch();
        batchChipText.setValue(AiText.batchChip(b));
        batchLinked.setValue(b != null);
    }

    /** 打开会话时的 ANSWERING 轮次：每 5 秒调 API-K08，最多 125 秒（待定事项 1） */
    private void startPollingRemote() {
        if (pollDisposable != null) {
            pollDisposable.dispose();
        }
        boolean any = false;
        for (ChatRound r : rounds) {
            if (r.phase == ChatRound.Phase.REMOTE_ANSWERING) {
                any = true;
                break;
            }
        }
        if (!any || engine.isDirect()) {
            return;
        }
        long maxTicks = TOTAL_TIMEOUT_SEC / POLL_SEC;
        pollDisposable = io.reactivex.rxjava3.core.Observable.interval(POLL_SEC, TimeUnit.SECONDS, timer)
                .take(maxTicks)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tick -> {
                    boolean pending = false;
                    for (ChatRound r : new ArrayList<>(rounds)) {
                        if (r.phase != ChatRound.Phase.REMOTE_ANSWERING || r.messageId <= 0) {
                            continue;
                        }
                        pending = true;
                        launch(repository.getMessage(r.messageId), m -> {
                            if (!AiCodes.STATUS_ANSWERING.equals(m.getStatus())) {
                                r.apply(m);
                                rebuild();
                            }
                        }, e -> {
                        });
                    }
                    if (!pending && pollDisposable != null) {
                        pollDisposable.dispose();
                    }
                });
        disposables.add(pollDisposable);
    }

    private void resetConversation() {
        if (loadDisposable != null) {
            loadDisposable.dispose();
        }
        if (pollDisposable != null) {
            pollDisposable.dispose();
        }
        for (ChatRound r : rounds) {
            releaseUploads(r);
        }
        rounds.clear();
        tips.clear();
        sessionId.setValue(null);
        session.setValue(null);
        hasMoreBefore.setValue(false);
        loadingEarlier.setValue(false);
        showEpidemicBanner.setValue(false);
        contextTruncated = false;
        invalidBatchTip = null;
        setUiState(UiState.content());
    }

    @NonNull
    private ChatRound loadedRound(@NonNull AiMessageVO m) {
        List<AskInput.Attach> images = new ArrayList<>();
        for (com.zhimu.slmp.common.model.vo.FileVO f : m.getImages()) {
            images.add(new AskInput.Attach(f.getFileId(), f.getUrl() == null ? "" : f.getUrl(), null));
        }
        LinkedBatchVO batch = null;
        if (m.getBatchId() != null) {
            batch = new LinkedBatchVO();
            batch.setBatchId(m.getBatchId());
            batch.setBatchNo(m.getBatchNo());
        }
        AskInput input = new AskInput(m.getSessionId() == 0 ? sessionId.getValue() : m.getSessionId(),
                m.getQuestionText() == null ? "" : m.getQuestionText(), Collections.emptyList(), batch, null,
                IdemKey.newKey());
        ChatRound r = ChatRound.fromMessage(nextLocalId++, m, input);
        r.loaded = true;
        r.remoteImages = images;
        return r;
    }

    /** 本轮之前、参与上下文的已完成轮次（直连组装提示词用） */
    @NonNull
    private List<AiMessageVO> historyBefore(@NonNull ChatRound target) {
        List<AiMessageVO> list = new ArrayList<>();
        for (ChatRound r : rounds) {
            if (r == target) {
                break;
            }
            if (r.phase == ChatRound.Phase.ANSWERED && r.message != null) {
                list.add(r.message);
            }
        }
        return list;
    }

    @Nullable
    private ChatRound find(long roundId) {
        for (ChatRound r : rounds) {
            if (r.localId == roundId) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    private static String batchNoOf(@NonNull ChatRound r) {
        return r.message == null ? null : r.message.getBatchNo();
    }

    private void useAttach(@NonNull UploadManager m, @NonNull String key) {
        if (attach != null) {
            attachments.removeSource(attach.getItems());
            uploadMessage.removeSource(attach.getMessage());
        }
        attach = m;
        attachKey = key;
        if (handle != null) {
            handle.set(KEY_ATTACH, key);
        }
        attachments.addSource(m.getItems(), attachments::setValue);
        uploadMessage.addSource(m.getMessage(), uploadMessage::setValue);
    }

    /** 发送后换一个空的附图管理（原来的随轮次保留，开流后释放） */
    private void newAttach() {
        if (handle == null) {
            return;
        }
        attachSeq++;
        String key = "ai_attach_" + System.nanoTime() + "_" + attachSeq;
        useAttach(uploads.create(handle, key, MediaScene.AI_IMAGE, AiText.MAX_IMAGES), key);
    }

    private void releaseUploads(@NonNull ChatRound r) {
        UploadManager used = r.uploads;
        if (used == null) {
            return;
        }
        if (r.uploadObserver != null) {
            used.getItems().removeObserver(r.uploadObserver);
            r.uploadObserver = null;
        }
        r.uploads = null;
        discardAttach(used, r.uploadsKey);
    }

    private void discardAttach(@NonNull UploadManager m, @Nullable String key) {
        m.release();
        if (handle != null && key != null) {
            handle.clearSavedStateProvider(key);
            handle.remove(key);
        }
    }

    @NonNull
    private static List<AskInput.Attach> attachesOf(@NonNull List<UploadItem> items) {
        List<AskInput.Attach> list = new ArrayList<>();
        for (UploadItem item : items) {
            String display = item.getLocalPath() != null ? item.getLocalPath()
                    : item.getSourceUri() != null ? item.getSourceUri() : item.getUrl();
            list.add(new AskInput.Attach(item.getFileId(), display == null ? "" : display, item.getLocalPath()));
        }
        return list;
    }

    private void updateCanSend() {
        String t = trimmed(inputText.getValue());
        List<UploadItem> list = attachments.getValue();
        boolean hasAttach = list != null && !list.isEmpty();
        boolean failed = false;
        if (list != null) {
            for (UploadItem item : list) {
                if (item.isFailed()) {
                    failed = true;
                    break;
                }
            }
        }
        boolean idle = streamPhase.getValue() == StreamPhase.IDLE;
        boolean ok = idle && (!t.isEmpty() || hasAttach) && t.length() <= AiText.MAX_QUESTION && !failed;
        if (!Objects.equals(canSend.getValue(), ok)) {
            canSend.setValue(ok);
        }
    }

    private void updateVoiceEnabled() {
        boolean ok = streamPhase.getValue() == StreamPhase.IDLE && !Boolean.TRUE.equals(voiceRecognizing.getValue());
        if (!Objects.equals(voiceEnabled.getValue(), ok)) {
            voiceEnabled.setValue(ok);
        }
    }

    @Nullable
    private static String hintOf(@Nullable List<UploadItem> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        int failed = 0;
        for (UploadItem item : list) {
            if (item.isFailed()) {
                failed++;
            }
        }
        return AiText.attachHint(list.size(), failed);
    }

    @NonNull
    private static String trimmed(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean sameBatch(@Nullable LinkedBatchVO a, @Nullable LinkedBatchVO b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getBatchId() == b.getBatchId();
    }

    @Nullable
    private static AiSessionDetailVO copySession(@Nullable AiSessionDetailVO s, @Nullable LinkedBatchVO batch) {
        AiSessionDetailVO c = new AiSessionDetailVO();
        if (s != null) {
            c.setId(s.getId());
            c.setTitle(s.getTitle());
            c.setRoundCount(s.getRoundCount());
            c.setHasCorrection(s.isHasCorrection());
            c.setContextTruncated(s.isContextTruncated());
            c.setHasEpidemicAlert(s.isHasEpidemicAlert());
            c.setLastRoundNo(s.getLastRoundNo());
        }
        c.setBatch(batch);
        c.setBatchLinkState(batch == null ? AiCodes.LINK_NONE : AiCodes.LINK_VALID);
        return c;
    }

    private static long minutesBetween(@NonNull String a, @NonNull String b) {
        LocalDateTime x = DateFormatter.parseDateTime(a);
        LocalDateTime y = DateFormatter.parseDateTime(b);
        if (x == null || y == null) {
            return 0;
        }
        return Math.abs(Duration.between(x, y).toMinutes());
    }

    @NonNull
    private static String now() {
        return LocalDateTime.now(DateFormatter.ZONE).format(TIME);
    }

    private static final class Pair {
        final AiSessionDetailVO first;
        final AiMessageSliceVO second;

        Pair(AiSessionDetailVO first, AiMessageSliceVO second) {
            this.first = first;
            this.second = second;
        }
    }

    @Override
    protected void onCleared() {
        disposeStream();
        if (speechDisposable != null) {
            speechDisposable.dispose();
        }
        farm.getContext().removeObserver(farmObserver);
        for (ChatRound r : rounds) {
            releaseUploads(r);
        }
        if (attach != null) {
            attach.release();
        }
        super.onCleared();
    }
}
