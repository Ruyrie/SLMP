package com.zhimu.server.integration.ai;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.integration.ai.UpstreamException.Target;
import com.zhimu.server.integration.support.HttpClients;
import com.zhimu.server.integration.support.Jsons;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 网关实现：非流式用 RestClient（JDK HttpClient），流式用 WebClient（Reactor Netty）读 text/event-stream。
 * 超时按整次调用计算（含切换备用），超时后不再切换。
 */
@Slf4j
public class AiGatewayImpl implements AiGateway {

    static final Duration DEFAULT_ACQUIRE_WAIT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 官方单个音频上限 10MB（base64 前） */
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AiProperties props;
    private final Semaphore permits;
    private final Duration acquireWait;
    private final RestClient restClient;
    private final WebClient webClient;

    public AiGatewayImpl(AiProperties props) {
        this(props, DEFAULT_ACQUIRE_WAIT);
    }

    AiGatewayImpl(AiProperties props, Duration acquireWait) {
        this.props = props;
        this.permits = new Semaphore(Math.max(1, props.getMaxConcurrency()), true);
        this.acquireWait = acquireWait;
        this.restClient = HttpClients.restClient(CONNECT_TIMEOUT, props.getChatTimeout());
        HttpClient netty = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis());
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(netty))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        log.info("AI 网关：enabled={} 主={} 备={} 并发上限={}", props.isEnabled(), describe(props.getPrimary()),
                describe(props.getFallback()), props.getMaxConcurrency());
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && (props.getPrimary().available() || props.getFallback().available());
    }

    // ===== 非流式 =====

    @Override
    public AiChatResult chat(AiChatRequest req) {
        checkRequest(req);
        List<Target> targets = targets(req.hasImages());
        if (!props.isEnabled() || targets.isEmpty()) {
            log.warn("AI 调用跳过 scene={}：未配置任何服务商 Key", req.getScene());
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }
        Duration timeout = timeoutOf(req);
        acquire(req.getScene());
        long start = System.nanoTime();
        long deadline = start + timeout.toNanos();
        try {
            for (int i = 0; i < targets.size(); i++) {
                Target t = targets.get(i);
                long remain = deadline - System.nanoTime();
                if (remain <= 0) {
                    throw timeoutError(req.getScene(), t, start);
                }
                try {
                    OpenAiProtocol.Completion c = callChat(t, req, Duration.ofNanos(remain));
                    AiChatResult result = AiChatResult.builder()
                            .content(c.content().trim())
                            .provider(t.name())
                            .model(c.model() != null ? c.model() : t.model())
                            .fallback(t.fallback())
                            .finishReason(c.finishReason())
                            .usage(c.usage())
                            .costMs(costMs(start))
                            .build();
                    logSuccess(req, result, false);
                    return result;
                } catch (UpstreamException e) {
                    if (e.timeout || System.nanoTime() >= deadline) {
                        throw timeoutError(req.getScene(), t, start);
                    }
                    boolean next = e.retryable && i + 1 < targets.size();
                    logFailure(req.getScene(), e, start, next);
                    if (!next) {
                        break;
                    }
                }
            }
            log.warn("AI 调用失败 scene={} costMs={}：没有可用的服务商", req.getScene(), costMs(start));
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        } finally {
            permits.release();
        }
    }

    private OpenAiProtocol.Completion callChat(Target t, AiChatRequest req, Duration timeout) {
        String body = Jsons.write(OpenAiProtocol.chatBody(t.model(), req, false));
        ResponseEntity<String> resp = post(t, body, MediaType.APPLICATION_JSON, timeout);
        int status = resp.getStatusCode().value();
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw UpstreamException.http(t, status, OpenAiProtocol.errorSummary(resp.getBody()));
        }
        OpenAiProtocol.Completion c = OpenAiProtocol.parseCompletion(resp.getBody());
        if (c == null) {
            throw UpstreamException.badResponse(t, status);
        }
        if (c.content() == null || c.content().isBlank()) {
            // 推理用尽输出长度或被截断：换备用再试
            throw UpstreamException.emptyAnswer(t, c.finishReason());
        }
        return c;
    }

    private ResponseEntity<String> post(Target t, String body, MediaType accept, Duration timeout) {
        try {
            return HttpClients.withTimeout(timeout, () -> restClient.post()
                    .uri(URI.create(t.url()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + t.provider().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(accept)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                    })
                    .toEntity(String.class));
        } catch (RestClientException e) {
            throw UpstreamException.network(t, e);
        }
    }

    // ===== 流式 =====

    @Override
    public AiStreamHandle stream(AiChatRequest req, AiStreamHandler handler) {
        checkRequest(req);
        List<Target> targets = targets(req.hasImages());
        if (!props.isEnabled() || targets.isEmpty()) {
            log.warn("AI 流式调用跳过 scene={}：未配置任何服务商 Key", req.getScene());
            handler.onError(new BizException(ErrorCode.AI_UNAVAILABLE));
            return DoneHandle.INSTANCE;
        }
        try {
            acquire(req.getScene());
        } catch (BizException e) {
            handler.onError(e);
            return DoneHandle.INSTANCE;
        }
        StreamState st = new StreamState(req, handler);
        Duration timeout = timeoutOf(req);
        AtomicBoolean released = new AtomicBoolean();
        Flux<OpenAiProtocol.Delta> flux;
        try {
            flux = attemptChain(targets, 0, st)
                    .takeUntilOther(Mono.delay(timeout).doOnNext(x -> st.cancelled = true)
                            .then(Mono.error(new DeadlineException())))
                    .publishOn(Schedulers.boundedElastic())
                    .doFinally(signal -> {
                        if (released.compareAndSet(false, true)) {
                            permits.release();
                        }
                    });
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
        StreamHandle handle = new StreamHandle(st);
        Disposable disposable = flux.subscribe(st::deliver, e -> handle.finishError(e), handle::finishComplete);
        handle.attach(disposable);
        return handle;
    }

    /** 依次尝试各服务商；未输出正文前的可重试失败换下一个 */
    private Flux<OpenAiProtocol.Delta> attemptChain(List<Target> targets, int index, StreamState st) {
        Target t = targets.get(index);
        return attempt(t, st).onErrorResume(e -> {
            if (e instanceof DeadlineException || st.cancelled) {
                // 已超时或已取消：上游关闭连接引起的错误不再记录、不再切换
                return Flux.error(e);
            }
            UpstreamException ue = e instanceof UpstreamException u ? u : UpstreamException.network(t, e);
            boolean next = ue.retryable && !st.contentStarted && index + 1 < targets.size();
            logFailure(st.req.getScene(), ue, st.startNanos, next);
            return next ? attemptChain(targets, index + 1, st) : Flux.error(ue);
        });
    }

    private Flux<OpenAiProtocol.Delta> attempt(Target t, StreamState st) {
        return Flux.defer(() -> {
            st.begin(t);
            String body = Jsons.write(OpenAiProtocol.chatBody(t.model(), st.req, true));
            return webClient.post()
                    .uri(URI.create(t.url()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + t.provider().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .exchangeToFlux(resp -> {
                        if (resp.statusCode().is2xxSuccessful()) {
                            return resp.bodyToFlux(SSE_TYPE);
                        }
                        int status = resp.statusCode().value();
                        return resp.bodyToMono(String.class).defaultIfEmpty("").flatMapMany(raw ->
                                Flux.error(UpstreamException.http(t, status, OpenAiProtocol.errorSummary(raw))));
                    })
                    .filter(ev -> ev.data() != null && !ev.data().isBlank())
                    .takeWhile(ev -> !"[DONE]".equals(ev.data().trim()))
                    .<OpenAiProtocol.Delta>handle((ev, sink) -> {
                        OpenAiProtocol.Delta d = OpenAiProtocol.parseDelta(ev.data());
                        if (d == null) {
                            sink.error(UpstreamException.badResponse(t, 200));
                        } else if (d.error() != null) {
                            sink.error(UpstreamException.streamError(t, d.error()));
                        } else {
                            st.record(d);
                            sink.next(d);
                        }
                    })
                    .concatWith(Mono.defer(() -> st.contentStarted ? Mono.empty()
                            : Mono.error(UpstreamException.emptyAnswer(t, st.finishReason))));
        });
    }

    /** 一次流式调用的状态。正文标记在网络线程上记录，回调在 publishOn 线程上执行 */
    private final class StreamState {
        final AiChatRequest req;
        final AiStreamHandler handler;
        final long startNanos = System.nanoTime();
        volatile boolean contentStarted;
        volatile boolean cancelled;
        volatile Target target;
        volatile String model;
        volatile String finishReason;
        volatile AiUsage usage;
        private final StringBuilder full = new StringBuilder();
        private boolean started;

        StreamState(AiChatRequest req, AiStreamHandler handler) {
            this.req = req;
            this.handler = handler;
        }

        void begin(Target t) {
            target = t;
            model = t.model();
            finishReason = null;
            usage = null;
        }

        void record(OpenAiProtocol.Delta d) {
            if (d.content() != null && !d.content().isEmpty()) {
                contentStarted = true;
            }
            if (d.finishReason() != null) {
                finishReason = d.finishReason();
            }
            if (d.usage() != null) {
                usage = d.usage();
            }
            if (d.model() != null) {
                model = d.model();
            }
        }

        void deliver(OpenAiProtocol.Delta d) {
            String text = d.content();
            if (text == null || text.isEmpty() || cancelled) {
                return;
            }
            try {
                if (!started) {
                    started = true;
                    handler.onStart(target.name(), model, target.fallback());
                }
                full.append(text);
                handler.onDelta(text);
            } catch (RuntimeException e) {
                throw new HandlerFailure(e);
            }
        }

        AiChatResult result() {
            return AiChatResult.builder()
                    .content(full.toString())
                    .provider(target.name())
                    .model(model)
                    .fallback(target.fallback())
                    .finishReason(finishReason)
                    .usage(usage)
                    .costMs(costMs(startNanos))
                    .build();
        }
    }

    private final class StreamHandle implements AiStreamHandle {
        private final StreamState st;
        private final AtomicBoolean done = new AtomicBoolean();
        private final AtomicReference<Disposable> disposable = new AtomicReference<>();

        StreamHandle(StreamState st) {
            this.st = st;
        }

        void attach(Disposable d) {
            disposable.set(d);
            if (done.get() && !d.isDisposed()) {
                d.dispose();
            }
        }

        @Override
        public void cancel() {
            if (done.compareAndSet(false, true)) {
                st.cancelled = true;
                Disposable d = disposable.get();
                if (d != null) {
                    d.dispose();
                }
                log.info("AI 流式调用已取消 scene={} provider={} costMs={} chars={}", st.req.getScene(),
                        st.target == null ? null : st.target.name(), costMs(st.startNanos), st.full.length());
            }
        }

        @Override
        public boolean isDone() {
            return done.get();
        }

        void finishComplete() {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            AiChatResult result = st.result();
            logSuccess(st.req, result, true);
            try {
                st.handler.onResult(result);
                st.handler.onComplete(result.getFinishReason(), result.getContent());
            } catch (RuntimeException e) {
                log.warn("AI 流式完成回调异常 scene={}：{}", st.req.getScene(), e.toString());
            }
        }

        void finishError(Throwable e) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            BizException error;
            if (e instanceof DeadlineException) {
                error = timeoutError(st.req.getScene(), st.target, st.startNanos);
            } else if (e instanceof UpstreamException ue && ue.timeout) {
                error = timeoutError(st.req.getScene(), st.target, st.startNanos);
            } else if (e instanceof HandlerFailure hf) {
                log.info("AI 流式回调异常，已取消上游 scene={}：{}", st.req.getScene(), hf.getCause().toString());
                error = new BizException(ErrorCode.AI_UNAVAILABLE);
            } else {
                log.warn("AI 流式调用失败 scene={} contentStarted={} chars={} costMs={}：{}", st.req.getScene(),
                        st.contentStarted, st.full.length(), costMs(st.startNanos), e.getMessage());
                error = new BizException(ErrorCode.AI_UNAVAILABLE);
            }
            try {
                st.handler.onError(error);
            } catch (RuntimeException ex) {
                log.warn("AI 流式失败回调异常 scene={}：{}", st.req.getScene(), ex.toString());
            }
        }
    }

    private enum DoneHandle implements AiStreamHandle {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }

    /** 整次调用超时 */
    private static final class DeadlineException extends RuntimeException {
        DeadlineException() {
            super("deadline", null, false, false);
        }
    }

    /** handler 回调抛出的异常 */
    private static final class HandlerFailure extends RuntimeException {
        HandlerFailure(Throwable cause) {
            super(cause.toString(), cause, false, false);
        }
    }

    // ===== 语音识别 =====

    @Override
    public String recognizeSpeech(byte[] audio, String mimeType) {
        AiProperties.Provider p = props.getPrimary();
        if (!props.isEnabled() || !p.available() || p.getAsrModel() == null || p.getAsrModel().isBlank()) {
            log.warn("语音识别跳过：未配置主服务商 Key 或 asr-model");
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }
        if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_BYTES) {
            String msg = audio == null || audio.length == 0 ? "录音为空，请重新录制" : "录音不能超过 10MB";
            throw new BizException(ErrorCode.PARAM_INVALID, msg)
                    .with("field_errors", List.of(new FieldErrorVO("file", msg)));
        }
        String mime = mimeType == null || mimeType.isBlank() ? "audio/wav" : mimeType;
        acquire(AiScene.ASR);
        long start = System.nanoTime();
        Target t = new Target(p, p.getAsrModel(), false);
        try {
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audio);
            String body = Jsons.write(OpenAiProtocol.asrBody(t.model(), dataUrl));
            ResponseEntity<String> resp = post(t, body, MediaType.APPLICATION_JSON, props.getAsrTimeout());
            int status = resp.getStatusCode().value();
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw UpstreamException.http(t, status, OpenAiProtocol.errorSummary(resp.getBody()));
            }
            OpenAiProtocol.Completion c = OpenAiProtocol.parseCompletion(resp.getBody());
            if (c == null) {
                throw UpstreamException.badResponse(t, status);
            }
            String text = c.content() == null ? "" : c.content().trim();
            log.info("语音识别成功 provider={} model={} mime={} bytes={} chars={} costMs={}", t.name(),
                    c.model() != null ? c.model() : t.model(), mime, audio.length, text.length(), costMs(start));
            return text;
        } catch (UpstreamException e) {
            if (e.timeout) {
                throw timeoutError(AiScene.ASR, t, start);
            }
            logFailure(AiScene.ASR, e, start, false);
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        } finally {
            permits.release();
        }
    }

    // ===== 公共 =====

    private List<Target> targets(boolean vision) {
        List<Target> list = new ArrayList<>(2);
        AiProperties.Provider primary = props.getPrimary();
        AiProperties.Provider fallback = props.getFallback();
        if (primary.available()) {
            list.add(new Target(primary, primary.modelFor(vision), false));
        }
        if (fallback.available()) {
            list.add(new Target(fallback, fallback.modelFor(vision), !list.isEmpty()));
        }
        return list;
    }

    private Duration timeoutOf(AiChatRequest req) {
        return req.getTimeout() != null ? req.getTimeout() : props.timeoutOf(req.getScene());
    }

    private void acquire(AiScene scene) {
        boolean ok;
        try {
            ok = permits.tryAcquire(acquireWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }
        if (!ok) {
            log.warn("AI 并发已满，排队超时 scene={} maxConcurrency={}", scene, props.getMaxConcurrency());
            throw new BizException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private static void checkRequest(AiChatRequest req) {
        if (req == null || req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new IllegalArgumentException("AI 请求没有消息");
        }
    }

    private BizException timeoutError(AiScene scene, Target t, long startNanos) {
        log.warn("AI 调用超时 scene={} provider={} model={} costMs={}", scene, t == null ? null : t.name(),
                t == null ? null : t.model(), costMs(startNanos));
        return new BizException(ErrorCode.AI_TIMEOUT);
    }

    private static void logSuccess(AiChatRequest req, AiChatResult r, boolean stream) {
        AiUsage u = r.getUsage();
        log.info("AI 调用成功 scene={} stream={} provider={} model={} fallback={} images={} costMs={} "
                        + "promptTokens={} completionTokens={} finish={} chars={}",
                req.getScene(), stream, r.getProvider(), r.getModel(), r.isFallback(), req.imageCount(),
                r.getCostMs(), u == null ? null : u.promptTokens(), u == null ? null : u.completionTokens(),
                r.getFinishReason(), r.getContent() == null ? 0 : r.getContent().length());
    }

    private static void logFailure(AiScene scene, UpstreamException e, long startNanos, boolean next) {
        log.warn("AI 调用失败 scene={} provider={} model={} http={} costMs={} 原因={}{}", scene, e.provider, e.model,
                e.httpStatus, costMs(startNanos), e.getMessage(), next ? "，改用备用服务商" : "");
    }

    private static long costMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String describe(AiProperties.Provider p) {
        if (!p.available()) {
            return p.getName() + "(未配置)";
        }
        return p.getName() + "(" + p.getChatModel() + "/" + p.getVisionModel() + ", key=" + Masks.secret(p.getApiKey())
                + ")";
    }
}
