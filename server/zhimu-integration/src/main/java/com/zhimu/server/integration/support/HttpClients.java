package com.zhimu.server.integration.support;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 外部服务用的同步 HTTP 客户端：JDK HttpClient + RestClient。
 * 读超时覆盖到响应体读完（Spring 的 JdkClientHttpRequest 对响应体也计时）；需要逐次指定超时时用 {@link #withTimeout}。
 */
public final class HttpClients {

    private static final ThreadLocal<Duration> CALL_TIMEOUT = new ThreadLocal<>();

    private HttpClients() {
    }

    public static HttpClient jdk(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * RestClient：默认读超时 defaultReadTimeout，{@link #withTimeout} 内发起的请求改用指定超时。
     * 只用字符串收发，报文由调用方用 {@link Jsons} 组装与解析。
     */
    public static RestClient restClient(Duration connectTimeout, Duration defaultReadTimeout) {
        return RestClient.builder()
                .requestFactory(new PerCallTimeoutFactory(jdk(connectTimeout), defaultReadTimeout))
                .build();
    }

    /** 在当前线程以指定读超时执行 RestClient 调用 */
    public static <T> T withTimeout(Duration timeout, Supplier<T> call) {
        Duration old = CALL_TIMEOUT.get();
        CALL_TIMEOUT.set(timeout);
        try {
            return call.get();
        } finally {
            if (old == null) {
                CALL_TIMEOUT.remove();
            } else {
                CALL_TIMEOUT.set(old);
            }
        }
    }

    /** 异常链中是否有读超时（连接超时不算，按网络错误处理） */
    public static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException
                    || t instanceof TimeoutException) {
                return true;
            }
            String msg = t.getMessage();
            if (t instanceof IOException && msg != null && msg.toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
    }

    private static final class PerCallTimeoutFactory implements ClientHttpRequestFactory {

        private final HttpClient client;
        private final Duration defaultTimeout;
        private final Executor executor;

        private PerCallTimeoutFactory(HttpClient client, Duration defaultTimeout) {
            this.client = client;
            this.defaultTimeout = defaultTimeout;
            SimpleAsyncTaskExecutor ex = new SimpleAsyncTaskExecutor("http-body-");
            ex.setVirtualThreads(true);
            this.executor = ex;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client, executor);
            Duration timeout = CALL_TIMEOUT.get();
            factory.setReadTimeout(timeout != null ? timeout : defaultTimeout);
            return factory.createRequest(uri, httpMethod);
        }
    }
}
