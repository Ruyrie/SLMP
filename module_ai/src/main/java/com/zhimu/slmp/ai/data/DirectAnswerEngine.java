package com.zhimu.slmp.ai.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.AskInput;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.StreamDoneVO;
import com.zhimu.slmp.ai.model.vo.StreamMetaVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.ai.AiChatClient;
import com.zhimu.slmp.common.ai.AiChatRequest;
import com.zhimu.slmp.common.ai.AiException;
import com.zhimu.slmp.common.ai.AiImage;
import com.zhimu.slmp.common.ai.AiStreamChunk;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.rxjava3.core.Flowable;

/**
 * 测试直连千问的回答（debug 包配置了 ai.dashscopeKey 时使用，DeepSeek 备用；2026-10-22 用户确认）：
 * 用 {@link AiChatClient#stream} 流式请求，按服务端 SSE 的格式生成事件——服务商接受请求后发 meta（本机轮次 ID 为负数），
 * 推理中发 HEARTBEAT（界面保持“正在思考…”），正文增量发 delta（先去掉回答类型标记），结束发 done（本机组装 AiMessageVO，
 * 没有引用来源）。附图读取本地压缩文件，按 AiImage 缩放到长边 1024 后以 base64 发送。
 * <p>
 * 错误：服务商还没接受请求就网络失败时抛 ApiException.network（界面显示“发送失败 + 重试”）；其余失败转为 error 事件——
 * 已有正文为“回答中断，点按重新生成”，没有正文为“AI 助手暂时不可用，点按重新生成”。
 */
public class DirectAnswerEngine implements AnswerEngine {

    public static final int MAX_TOKENS = 6000;
    static final String MSG_UNAVAILABLE = "AI 助手暂时不可用，点按重新生成";
    static final String MSG_INTERRUPTED = "回答中断，点按重新生成";
    private static final AtomicLong LOCAL_IDS = new AtomicLong(0);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiChatClient client;
    private final FarmContext farm;

    public DirectAnswerEngine(@NonNull AiChatClient client, @NonNull FarmContext farm) {
        this.client = client;
        this.farm = farm;
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @NonNull
    @Override
    public Flowable<AiStreamEvent> ask(@NonNull AskInput input, @NonNull List<AiMessageVO> history) {
        return Flowable.defer(() -> run(LOCAL_IDS.decrementAndGet(), input, history));
    }

    @NonNull
    @Override
    public Flowable<AiStreamEvent> regenerate(long messageId, @NonNull AskInput input,
                                              @NonNull List<AiMessageVO> history) {
        long id = messageId < 0 ? messageId : LOCAL_IDS.decrementAndGet();
        return Flowable.defer(() -> run(id, input, history));
    }

    @NonNull
    private Flowable<AiStreamEvent> run(long localId, @NonNull AskInput input, @NonNull List<AiMessageVO> history) {
        FarmContextVO ctx = farm.current();
        List<AiImage> images = loadImages(input);
        AiChatRequest req = AiChatRequest.builder()
                .system(DirectPrompt.system(ctx == null ? null : ctx.getName(),
                        ctx == null ? null : ctx.getMainCategoryName(), input.getBatch(), input.getCourse(),
                        DateFormatter.today()))
                .history(DirectPrompt.history(history))
                .user(DirectPrompt.user(input.getQuestionText(), images.size()))
                .images(images)
                .maxTokens(MAX_TOKENS)
                .tag("k01_chat")
                .build();
        AnswerTypeTag tag = new AnswerTypeTag();
        boolean[] started = {false};
        StringBuilder shown = new StringBuilder();
        String askedAt = now();
        return client.stream(req)
                .concatMap(chunk -> {
                    switch (chunk.getType()) {
                        case STARTED:
                            if (started[0]) {
                                return Flowable.just(AiStreamEvent.heartbeat());
                            }
                            started[0] = true;
                            return Flowable.just(AiStreamEvent.meta(meta(localId, input)));
                        case CONTENT: {
                            String out = DirectPrompt.clean(tag.feed(chunk.getText()));
                            if (out.isEmpty()) {
                                return Flowable.just(AiStreamEvent.heartbeat());
                            }
                            shown.append(out);
                            return Flowable.just(AiStreamEvent.delta(out));
                        }
                        case DONE: {
                            List<AiStreamEvent> events = new ArrayList<>(2);
                            String rest = DirectPrompt.clean(tag.flush());
                            if (!rest.isEmpty()) {
                                shown.append(rest);
                                events.add(AiStreamEvent.delta(rest));
                            }
                            String[] split = AnswerTypeTag.split(chunk.getText());
                            events.add(AiStreamEvent.done(done(localId, input, split[0],
                                    DirectPrompt.clean(split[1]), askedAt)));
                            return Flowable.fromIterable(events);
                        }
                        case THINKING:
                        default:
                            return Flowable.just(AiStreamEvent.heartbeat());
                    }
                })
                .onErrorResumeNext(e -> {
                    if (!started[0]) {
                        if (e instanceof AiException && ((AiException) e).getKind() == AiException.Kind.NETWORK) {
                            return Flowable.error(ApiException.network(e));
                        }
                        return Flowable.just(AiStreamEvent.meta(meta(localId, input)),
                                AiStreamEvent.error(ErrorCodes.AI_UNAVAILABLE, MSG_UNAVAILABLE, AiCodes.STATUS_FAILED));
                    }
                    boolean hasText = shown.toString().trim().length() > 0;
                    return Flowable.just(AiStreamEvent.error(ErrorCodes.AI_UNAVAILABLE,
                            hasText ? MSG_INTERRUPTED : MSG_UNAVAILABLE,
                            hasText ? AiCodes.STATUS_INTERRUPTED : AiCodes.STATUS_FAILED));
                });
    }

    @NonNull
    private static StreamMetaVO meta(long localId, @NonNull AskInput input) {
        StreamMetaVO m = new StreamMetaVO();
        m.setMessageId(localId);
        m.setSessionId(input.getSessionId() == null ? 0 : input.getSessionId());
        m.setSessionCreated(false);
        m.setBatchLinkState(input.getBatch() == null ? AiCodes.LINK_NONE : AiCodes.LINK_VALID);
        return m;
    }

    @NonNull
    static StreamDoneVO done(long localId, @NonNull AskInput input, @NonNull String type, @NonNull String body,
                             @NonNull String askedAt) {
        AiMessageVO m = new AiMessageVO();
        m.setId(localId);
        m.setSessionId(input.getSessionId() == null ? 0 : input.getSessionId());
        m.setAskedAt(askedAt);
        m.setQuestionText(input.getQuestionText().isEmpty() ? null : input.getQuestionText());
        if (input.getBatch() != null) {
            m.setBatchId(input.getBatch().getBatchId());
            m.setBatchNo(input.getBatch().getBatchNo());
        }
        if (input.getCourse() != null) {
            m.setContextCourseTitle(input.getCourse().getTitle());
        }
        m.setStatus(AiCodes.STATUS_ANSWERED);
        m.setAnswerType(type);
        m.setAnswerText(body);
        m.setCitations(Collections.emptyList());
        boolean offTopic = AiCodes.ANSWER_OFF_TOPIC.equals(type);
        m.setCanRate(!offTopic);
        m.setCanCopy(!offTopic);
        m.setInContext(true);
        m.setAnsweredAt(now());
        StreamDoneVO d = new StreamDoneVO();
        d.setAnswerType(type);
        d.setFinishReason("STOP");
        d.setMessage(m);
        return d;
    }

    /** 读取本地压缩文件转为 AiImage（读不了的跳过） */
    @WorkerThread
    @NonNull
    private static List<AiImage> loadImages(@NonNull AskInput input) {
        List<AiImage> list = new ArrayList<>();
        int n = 0;
        for (AskInput.Attach a : input.getImages()) {
            String path = a.localPath;
            if (path == null) {
                continue;
            }
            Bitmap bmp = decode(path);
            if (bmp == null) {
                continue;
            }
            n++;
            list.add(AiImage.fromBitmap(bmp, AiImage.DEFAULT_MAX_SIDE, AiImage.DEFAULT_QUALITY, "图片 " + n));
            bmp.recycle();
        }
        return list;
    }

    @Nullable
    private static Bitmap decode(@NonNull String path) {
        String p = path.startsWith("file://") ? path.substring(7) : path;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(p, o);
        int longSide = Math.max(o.outWidth, o.outHeight);
        if (longSide <= 0) {
            return null;
        }
        int sample = 1;
        while (longSide / (sample * 2) >= AiImage.DEFAULT_MAX_SIDE) {
            sample *= 2;
        }
        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = sample;
        return BitmapFactory.decodeFile(p, real);
    }

    @NonNull
    private static String now() {
        return LocalDateTime.now(DateFormatter.ZONE).format(TIME);
    }
}
