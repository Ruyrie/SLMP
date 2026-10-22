package com.zhimu.slmp.inspect.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.ai.AiChatClient;
import com.zhimu.slmp.common.ai.AiChatRequest;
import com.zhimu.slmp.common.ai.AiChatResult;
import com.zhimu.slmp.common.ai.AiImage;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;
import com.zhimu.slmp.media.process.VideoFrameExtractor;
import com.zhimu.slmp.media.util.MediaGlide;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * AI 诊断测试直连（2026-10-22 用户确认：服务端完成前 debug 包直连做测试）：I05 提交成功后 {@link #start} 用上报的照片、
 * 视频关键帧、症状、批次信息组装提示词，经 {@link AiChatClient}（主用阿里云百炼、失败用 DeepSeek）请求模型，
 * {@link AiDiagnosisParser} 整理为 DiagnosisResultVO；I06 用 {@link #observe} 取结果并标注“测试直连”。
 * 结果在进程内按 report_id 缓存（离开 I06 后诊断继续）；直连关闭（release 包没有 Key）或失败时 I06 走原有接口。
 */
@FeatureScope
public class DirectDiagnosisRepository {

    private static final String TAG = "ZmtAi";
    /** 照片最多发送几张、视频取几帧、图片长边 */
    static final int MAX_PHOTOS = 4;
    static final int VIDEO_FRAMES = 3;
    static final int IMAGE_MAX_SIDE = 768;
    static final int IMAGE_QUALITY = 75;
    static final int MAX_TOKENS = 3000;

    /** 一次直连诊断的结果 */
    public static final class Result {
        @NonNull
        public final DiagnosisResultVO diagnosis;
        @NonNull
        public final String sourceLabel;
        @NonNull
        public final DirectDiagnosisInput input;

        Result(@NonNull DiagnosisResultVO diagnosis, @NonNull String sourceLabel, @NonNull DirectDiagnosisInput input) {
            this.diagnosis = diagnosis;
            this.sourceLabel = sourceLabel;
            this.input = input;
        }
    }

    private static final class Session {
        final DirectDiagnosisInput input;
        final Single<Result> result;
        final Disposable keepAlive;

        Session(DirectDiagnosisInput input, Single<Result> result, Disposable keepAlive) {
            this.input = input;
            this.result = result;
            this.keepAlive = keepAlive;
        }
    }

    private final Context context;
    private final AiChatClient client;
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    @Inject
    public DirectDiagnosisRepository(@NonNull Context context) {
        this(context, AiChatClient.getDefault());
    }

    DirectDiagnosisRepository(@NonNull Context context, @NonNull AiChatClient client) {
        this.context = context.getApplicationContext();
        this.client = client;
    }

    /** 直连开启（debug 包且配置了 Key） */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /** I05 提交成功后开始诊断（立即在后台执行，结果缓存） */
    public void start(long reportId, @NonNull DirectDiagnosisInput input) {
        if (!isEnabled()) {
            return;
        }
        Session old = sessions.remove(reportId);
        if (old != null) {
            old.keepAlive.dispose();
        }
        Single<Result> single = Single.fromCallable(() -> buildRequest(input))
                .flatMap(client::chat)
                .map(r -> toResult(r, input))
                .timeout(AiChatClient.CALL_TIMEOUT_SEC * 2 + 30, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .cache();
        Disposable keep = single.subscribe(r -> Log.i(TAG, "直连诊断完成 report=" + reportId + " "
                + r.sourceLabel), e -> Log.w(TAG, "直连诊断失败 report=" + reportId + "：" + e.getMessage()));
        sessions.put(reportId, new Session(input, single, keep));
    }

    /** I06：有直连诊断时返回结果（成功或失败）；没有发起过时为空 */
    @NonNull
    public Maybe<Result> observe(long reportId) {
        Session s = sessions.get(reportId);
        return s == null ? Maybe.empty() : s.result.toMaybe();
    }

    public boolean has(long reportId) {
        return sessions.containsKey(reportId);
    }

    /** 直连失败后不再使用（改走服务端接口） */
    public void discard(long reportId) {
        Session s = sessions.remove(reportId);
        if (s != null) {
            s.keepAlive.dispose();
        }
    }

    @Nullable
    public DirectDiagnosisInput inputOf(long reportId) {
        Session s = sessions.get(reportId);
        return s == null ? null : s.input;
    }

    @NonNull
    private Result toResult(@NonNull AiChatResult r, @NonNull DirectDiagnosisInput input) throws Exception {
        String now = LocalDateTime.now(DateFormatter.ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        DiagnosisResultVO vo = AiDiagnosisParser.parse(r.getContent(), new AiDiagnosisParser.Context(
                input.sickCount, input.stockCount, input.sameDayIndex, now, r.sourceLabel()));
        if (vo == null) {
            throw new IllegalStateException("AI 回答格式不符合要求");
        }
        return new Result(vo, r.sourceLabel(), input);
    }

    // ===== 请求 =====

    @WorkerThread
    @NonNull
    AiChatRequest buildRequest(@NonNull DirectDiagnosisInput input) {
        List<AiImage> images = new ArrayList<>();
        int photos = 0;
        for (DirectDiagnosisInput.Media m : input.media) {
            if (m.video) {
                images.addAll(videoFrames(m));
            } else if (photos < MAX_PHOTOS) {
                AiImage img = photo(m, "照片 " + (photos + 1));
                if (img != null) {
                    images.add(img);
                    photos++;
                }
            }
        }
        return AiChatRequest.builder()
                .tag("diagnosis")
                .system(AiDiagnosisPrompt.system())
                .user(AiDiagnosisPrompt.user(input))
                .images(images)
                .maxTokens(MAX_TOKENS)
                .build();
    }

    @Nullable
    private AiImage photo(@NonNull DirectDiagnosisInput.Media m, @NonNull String label) {
        Bitmap b = null;
        try {
            if (m.localPath != null && new File(m.localPath).isFile()) {
                b = decodeSampled(m.localPath);
            } else if (m.url != null) {
                Object model = MediaGlide.model(m.url, m.fileId);
                b = Glide.with(context).asBitmap().load(model).submit(IMAGE_MAX_SIDE, IMAGE_MAX_SIDE).get(30,
                        TimeUnit.SECONDS);
            }
            if (b == null) {
                return null;
            }
            return AiImage.fromBitmap(b, IMAGE_MAX_SIDE, IMAGE_QUALITY, label);
        } catch (Exception e) {
            Log.w(TAG, "照片读取失败，跳过：" + e.getClass().getSimpleName());
            return null;
        }
    }

    @NonNull
    private List<AiImage> videoFrames(@NonNull DirectDiagnosisInput.Media m) {
        List<AiImage> out = new ArrayList<>();
        String src = m.localPath != null && new File(m.localPath).isFile() ? m.localPath
                : m.url != null && !MediaGlide.isRemoteUrl(m.url) ? m.url : null;
        if (src != null) {
            for (VideoFrameExtractor.Frame f : VideoFrameExtractor.extractKeyFrames(context, src, VIDEO_FRAMES,
                    IMAGE_MAX_SIDE)) {
                out.add(AiImage.fromBitmap(f.bitmap, IMAGE_MAX_SIDE, IMAGE_QUALITY,
                        String.format(Locale.ROOT, "视频关键帧 %d:%02d", f.timeMs / 60000, (f.timeMs / 1000) % 60)));
                f.bitmap.recycle();
            }
        } else if (m.thumbUrl != null) {
            // 服务端视频：只送封面
            AiImage cover = photo(new DirectDiagnosisInput.Media(false, null, m.thumbUrl, null, null, 0), "视频封面");
            if (cover != null) {
                out.add(cover);
            }
        }
        return out;
    }

    @Nullable
    private static Bitmap decodeSampled(@NonNull String path) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sample = 1;
        int longSide = Math.max(o.outWidth, o.outHeight);
        while (longSide / (sample * 2) >= IMAGE_MAX_SIDE) {
            sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, d);
    }
}
