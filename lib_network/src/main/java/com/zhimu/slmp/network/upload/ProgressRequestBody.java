package com.zhimu.slmp.network.upload;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;

/**
 * 带进度的请求体（multipart 上传 API-U05 时包装文件部分）；回调在 OkHttp 线程，按百分比变化节流。
 */
public class ProgressRequestBody extends RequestBody {

    /** 上传进度回调 */
    public interface Listener {
        /** @param percent 0–100 */
        void onProgress(long written, long total, int percent);
    }

    private final RequestBody delegate;
    private final Listener listener;

    public ProgressRequestBody(@NonNull RequestBody delegate, @NonNull Listener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return delegate.contentLength();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        final long total = contentLength();
        ForwardingSink counting = new ForwardingSink(sink) {
            private long written;
            private int lastPercent = -1;

            @Override
            public void write(@NonNull Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                written += byteCount;
                int percent = total > 0 ? (int) (written * 100 / total) : 0;
                if (percent != lastPercent) {
                    lastPercent = percent;
                    listener.onProgress(written, total, percent);
                }
            }
        };
        BufferedSink buffered = Okio.buffer(counting);
        delegate.writeTo(buffered);
        buffered.flush();
    }
}
