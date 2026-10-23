package com.zhimu.slmp.ai.ui.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.ai.AiTestRules;
import com.zhimu.slmp.ai.data.AiRepository;
import com.zhimu.slmp.ai.data.AnswerEngine;
import com.zhimu.slmp.ai.data.SpeechEngine;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.AskInput;
import com.zhimu.slmp.ai.model.InputMode;
import com.zhimu.slmp.ai.model.StreamPhase;
import com.zhimu.slmp.ai.model.vo.AiMessageSliceVO;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.AiSessionDetailVO;
import com.zhimu.slmp.ai.model.vo.SpeechResultVO;
import com.zhimu.slmp.ai.model.vo.StreamDoneVO;
import com.zhimu.slmp.ai.model.vo.StreamMetaVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.ai.util.InputModeStore;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.TestScheduler;

/**
 * K01 流式问答状态：发送 → 正在思考 → 首个 delta 进入生成中（50 ms 合并刷新、[^n] 隐藏）→ done 完成；停止保留文字、
 * 重新生成用原轮次；开流前 42901 撤回并放回输入栏；网络失败显示“重试”并沿用幂等键；45 秒看门狗中断；125 秒总超时；
 * 疫病回答显示横幅；离开 K01 中断；载入会话（超时轮次、10 轮分隔）；语音识别结果追加并临时切回文字。
 */
public class AiChatViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public AiTestRules rules = new AiTestRules();

    private AiRepository repo;
    private AnswerEngine engine;
    private SpeechEngine speech;
    private NetworkStatus network;
    private InputModeStore modeStore;
    private AiChatViewModel vm;
    private TestScheduler timer;
    private PublishProcessor<AiStreamEvent> stream;
    private final List<String> messages = new ArrayList<>();

    @Before
    public void setUp() {
        repo = mock(AiRepository.class);
        engine = mock(AnswerEngine.class);
        speech = mock(SpeechEngine.class);
        network = mock(NetworkStatus.class);
        modeStore = mock(InputModeStore.class);
        when(modeStore.get()).thenReturn(InputMode.TEXT);
        when(network.isConnected()).thenReturn(true);
        when(repo.getMessage(anyLong())).thenReturn(Single.error(ApiException.network(null)));
        FarmContext farm = mock(FarmContext.class);
        when(farm.getContext()).thenReturn(new MutableLiveData<FarmContextVO>(null));
        UploadManager.Factory uploads = mock(UploadManager.Factory.class);
        when(uploads.create(any(), anyString(), anyString(), anyInt())).thenAnswer(inv -> emptyUploads());
        stream = PublishProcessor.create();
        when(engine.ask(any(), any())).thenAnswer(inv -> stream);
        when(engine.regenerate(anyLong(), any(), any())).thenAnswer(inv -> stream);
        timer = new TestScheduler();
        vm = new AiChatViewModel(repo, engine, speech, uploads, farm, network, modeStore);
        vm.setTimeScheduler(timer);
        vm.attachSavedState(new SavedStateHandle());
        vm.getMessage().observeForever(m -> messages.add(m.getText()));
        // 让 MediatorLiveData 生效
        vm.getCanSend().observeForever(v -> {
        });
        vm.getVoiceEnabled().observeForever(v -> {
        });
    }

    private static UploadManager emptyUploads() {
        UploadManager m = mock(UploadManager.class);
        MutableLiveData<List<UploadItem>> items = new MutableLiveData<>(Collections.emptyList());
        when(m.getItems()).thenReturn(items);
        when(m.getMessage()).thenReturn(new MutableLiveData<UiMessage>());
        when(m.current()).thenReturn(Collections.emptyList());
        when(m.getRemaining()).thenReturn(AiText.MAX_IMAGES);
        return m;
    }

    private static StreamMetaVO meta(long id) {
        StreamMetaVO m = new StreamMetaVO();
        m.setMessageId(id);
        m.setBatchLinkState(AiCodes.LINK_NONE);
        return m;
    }

    private static AiStreamEvent done(long id, String type, String text) {
        AiMessageVO m = new AiMessageVO();
        m.setId(id);
        m.setStatus(AiCodes.STATUS_ANSWERED);
        m.setAnswerType(type);
        m.setAnswerText(text);
        m.setCanRate(true);
        m.setCanCopy(true);
        m.setInContext(true);
        StreamDoneVO d = new StreamDoneVO();
        d.setAnswerType(type);
        d.setMessage(m);
        return AiStreamEvent.done(d);
    }

    private ChatItem lastAnswer() {
        List<ChatItem> items = vm.getChatItems().getValue();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).type == ChatItem.Type.ANSWER) {
                return items.get(i);
            }
        }
        throw new AssertionError("no answer");
    }

    private void ask(String text) {
        vm.getInputText().setValue(text);
        assertTrue(vm.getCanSend().getValue());
        vm.send();
    }

    @Test
    public void send_streams_thenDone() {
        ask("氟苯尼考粉用于猪，多久可以出栏？");
        assertEquals("", vm.getInputText().getValue());
        assertEquals(StreamPhase.WAITING, vm.getStreamPhase().getValue());
        assertEquals(ChatItem.AnswerState.THINKING, lastAnswer().state);
        assertFalse(vm.getCanSend().getValue());
        assertFalse(vm.getVoiceEnabled().getValue());

        stream.onNext(AiStreamEvent.meta(meta(5501)));
        stream.onNext(AiStreamEvent.heartbeat());
        assertEquals(StreamPhase.WAITING, vm.getStreamPhase().getValue());
        stream.onNext(AiStreamEvent.delta("休药期为 20 天["));
        assertEquals(StreamPhase.STREAMING, vm.getStreamPhase().getValue());
        assertEquals(ChatItem.AnswerState.STREAMING, lastAnswer().state);
        assertEquals("休药期为 20 天", vm.getStreamingText().getValue().toString());
        stream.onNext(AiStreamEvent.delta("^1]，从末次用药日起算。"));
        // 50 ms 合并：到时才刷新
        assertEquals("休药期为 20 天", vm.getStreamingText().getValue().toString());
        timer.advanceTimeBy(50, TimeUnit.MILLISECONDS);
        assertEquals("休药期为 20 天，从末次用药日起算。", vm.getStreamingText().getValue().toString());

        stream.onNext(done(5501, AiCodes.ANSWER_NORMAL, "休药期为 20 天[^1]，从末次用药日起算。"));
        assertEquals(StreamPhase.IDLE, vm.getStreamPhase().getValue());
        ChatItem a = lastAnswer();
        assertEquals(ChatItem.AnswerState.DONE, a.state);
        assertEquals(5501, a.message.getId());
        assertFalse(vm.getShowEpidemicBanner().getValue());
        assertTrue(vm.getVoiceEnabled().getValue());
        // 提问气泡显示用户原文
        ChatItem q = vm.getChatItems().getValue().get(vm.getChatItems().getValue().size() - 2);
        assertEquals("氟苯尼考粉用于猪，多久可以出栏？", q.text);
    }

    @Test
    public void stop_keepsText_thenRegenerate() {
        ask("保育猪换料后拉稀，要停料吗？");
        stream.onNext(AiStreamEvent.meta(meta(5600)));
        stream.onNext(AiStreamEvent.delta("不需要全停，"));
        vm.stop();
        ChatItem a = lastAnswer();
        assertEquals(ChatItem.AnswerState.STOPPED, a.state);
        assertEquals("不需要全停，", a.partialText);
        assertEquals(AiText.MSG_STOPPED, a.statusText);
        assertEquals(StreamPhase.IDLE, vm.getStreamPhase().getValue());
        assertFalse(stream.hasSubscribers());

        stream = PublishProcessor.create();
        vm.regenerate(a.roundId);
        verify(engine).regenerate(eq(5600L), any(AskInput.class), any());
        assertEquals(ChatItem.AnswerState.THINKING, lastAnswer().state);
        stream.onNext(AiStreamEvent.meta(meta(5600)));
        stream.onNext(done(5600, AiCodes.ANSWER_NORMAL, "逐步换料。"));
        assertEquals(ChatItem.AnswerState.DONE, lastAnswer().state);
    }

    @Test
    public void busy42901_withdrawsAndRestoresInput() {
        ask("另一台设备上还在回答");
        stream.onError(new ApiException(42901, "上一个问题还在回答中，请稍后再问"));
        assertTrue(vm.getChatItems().getValue().isEmpty());
        assertEquals("另一台设备上还在回答", vm.getInputText().getValue());
        assertEquals(StreamPhase.IDLE, vm.getStreamPhase().getValue());
        assertTrue(messages.contains("上一个问题还在回答中，请稍后再问"));
    }

    @Test
    public void networkFailure_retryKeepsIdemKey() {
        ArgumentCaptor<AskInput> captor = ArgumentCaptor.forClass(AskInput.class);
        when(network.isConnected()).thenReturn(false);
        ask("没有网络时提问");
        ChatItem a = lastAnswer();
        assertEquals(ChatItem.AnswerState.SEND_FAILED, a.state);
        assertEquals(AiText.MSG_NO_NETWORK, a.statusText);

        when(network.isConnected()).thenReturn(true);
        vm.retrySend(a.roundId);
        stream.onError(ApiException.network(new IOException("reset")));
        assertEquals(AiText.MSG_NETWORK_FAILED, lastAnswer().statusText);
        stream = PublishProcessor.create();
        vm.retrySend(a.roundId);
        verify(engine, times(2)).ask(captor.capture(), any());
        assertEquals(captor.getAllValues().get(0).getIdemKey(), captor.getAllValues().get(1).getIdemKey());
    }

    @Test
    public void watchdog45s_interrupts_andTotalTimeout() {
        ask("采食量下降怎么办");
        stream.onNext(AiStreamEvent.meta(meta(5700)));
        stream.onNext(AiStreamEvent.delta("先看舍温"));
        timer.advanceTimeBy(46, TimeUnit.SECONDS);
        ChatItem a = lastAnswer();
        assertEquals(ChatItem.AnswerState.ERROR, a.state);
        assertEquals(AiText.MSG_INTERRUPTED, a.statusText);
        assertEquals("先看舍温", a.partialText);

        when(engine.isDirect()).thenReturn(true);
        stream = PublishProcessor.create();
        vm.regenerate(a.roundId);
        stream.onNext(AiStreamEvent.meta(meta(5700)));
        for (int i = 0; i < 6; i++) {
            timer.advanceTimeBy(20, TimeUnit.SECONDS);
            stream.onNext(AiStreamEvent.heartbeat());
        }
        timer.advanceTimeBy(6, TimeUnit.SECONDS);
        assertEquals(AiText.MSG_TIMEOUT, lastAnswer().statusText);
        assertEquals(StreamPhase.IDLE, vm.getStreamPhase().getValue());
    }

    @Test
    public void errorEvent_epidemicBanner_andLeave() {
        ask("3 头猪高烧不退");
        stream.onNext(AiStreamEvent.meta(meta(5800)));
        stream.onNext(AiStreamEvent.error(50401, "回答超时，点按重新生成", AiCodes.STATUS_TIMEOUT));
        assertEquals("回答超时，点按重新生成", lastAnswer().statusText);

        stream = PublishProcessor.create();
        vm.regenerate(lastAnswer().roundId);
        stream.onNext(AiStreamEvent.meta(meta(5800)));
        stream.onNext(done(5800, AiCodes.ANSWER_EPIDEMIC, "可能与非洲猪瘟有关"));
        assertTrue(vm.getShowEpidemicBanner().getValue());

        stream = PublishProcessor.create();
        ask("还要注意什么");
        stream.onNext(AiStreamEvent.meta(meta(5801)));
        stream.onNext(AiStreamEvent.delta("隔离发病猪"));
        vm.interruptForLeave();
        assertEquals(AiText.MSG_INTERRUPTED, lastAnswer().statusText);
        assertTrue(vm.getShowEpidemicBanner().getValue());
    }

    @Test
    public void loadSession_timeoutRoundAndDivider() throws IOException {
        when(repo.getSession(1018)).thenReturn(Single.just(AiTestRules.mock("session_1018.json", AiSessionDetailVO.class)));
        when(repo.getMessages(eq(1018L), any(), anyInt())).thenReturn(Single.just(
                AiTestRules.mock("messages_1018.json", AiMessageSliceVO.class)));
        vm.loadSession(1018);
        List<ChatItem> items = vm.getChatItems().getValue();
        long dividers = items.stream().filter(i -> i.type == ChatItem.Type.DIVIDER).count();
        assertEquals(1, dividers);
        assertEquals("ZM2610-031 · 育肥猪 · 96 日龄 · 存栏 200 头", vm.getBatchChipText().getValue());
        assertEquals(ChatItem.Type.TIME, items.get(0).type);

        when(repo.getSession(1015)).thenReturn(Single.just(AiTestRules.mock("session_1015.json", AiSessionDetailVO.class)));
        when(repo.getMessages(eq(1015L), any(), anyInt())).thenReturn(Single.just(
                AiTestRules.mock("messages_1015.json", AiMessageSliceVO.class)));
        vm.loadSession(1015);
        ChatItem a = lastAnswer();
        assertEquals(ChatItem.AnswerState.ERROR, a.state);
        assertEquals("回答超时，点按重新生成", a.statusText);
        assertNotNull(vm.getSessionId().getValue());
    }

    @Test
    public void speech_appendsText_andRevertsToVoiceAfterSend() {
        SpeechResultVO r = new SpeechResultVO();
        r.setRecognized(true);
        r.setText("要停料吗");
        when(speech.recognize(any(), anyInt())).thenReturn(Single.just(r));
        vm.setInputMode(InputMode.VOICE);
        vm.getInputText().setValue("保育猪拉稀，");
        vm.recognizeSpeech(new File("not-exist.wav"), 2000);
        assertEquals("保育猪拉稀，要停料吗", vm.getInputText().getValue());
        assertEquals(InputMode.TEXT, vm.getInputMode().getValue());
        assertEquals("保育猪拉稀，要停料吗", vm.getFillRecognized().getValue());
        vm.send();
        assertEquals(InputMode.VOICE, vm.getInputMode().getValue());

        SpeechResultVO empty = new SpeechResultVO();
        empty.setRecognized(false);
        when(speech.recognize(any(), anyInt())).thenReturn(Single.just(empty));
        vm.recognizeSpeech(new File("not-exist.wav"), 2000);
        assertTrue(messages.contains(AiText.MSG_NOT_HEARD));
        when(speech.recognize(any(), anyInt())).thenReturn(Single.error(new ApiException(50201, "x")));
        vm.recognizeSpeech(new File("not-exist.wav"), 2000);
        assertTrue(messages.contains(AiText.MSG_ASR_UNAVAILABLE));
    }

    @Test
    public void newChat_keepsBatch_andRateLocal() {
        when(engine.isDirect()).thenReturn(true);
        ask("问一个问题");
        stream.onNext(AiStreamEvent.meta(meta(-3)));
        stream.onNext(done(-3, AiCodes.ANSWER_NORMAL, "回答"));
        vm.rate(lastAnswer().roundId, AiCodes.RATING_USEFUL);
        assertEquals(AiCodes.RATING_USEFUL, lastAnswer().message.getRating());
        vm.rate(lastAnswer().roundId, AiCodes.RATING_USEFUL);
        assertNull(lastAnswer().message.getRating());
        vm.copyAnswer(lastAnswer().roundId);
        assertEquals("回答\n\n由 AI 生成，仅供参考", vm.getCopyText().getValue());
        vm.startNewChat();
        assertTrue(vm.getChatItems().getValue().isEmpty());
        assertTrue(messages.contains(AiText.MSG_NEW_CHAT));
        verify(repo, times(0)).rate(anyLong(), any());
    }
}
