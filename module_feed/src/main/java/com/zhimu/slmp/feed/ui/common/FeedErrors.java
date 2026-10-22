package com.zhimu.slmp.feed.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.error.ApiException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

/** 提交类错误的共用判断与文案。 */
public final class FeedErrors {

    public static final String MSG_NETWORK_KEEP = "网络连接失败，已保留填写的内容，请联网后重新提交";
    public static final String MSG_VERSION = "内容已更新，请刷新后再修改";
    public static final String MSG_UNCONFIRMED = "网络不稳定，没能确认这次提交是否成功，请稍后重试";
    public static final String ACTION_REFRESH = "feed_refresh";
    public static final String ACTION_RETRY = "feed_retry";
    public static final String ACTION_RESTORE_DRAFT = "feed_restore_draft";
    public static final String ACTION_STOCK_IN = "feed_stock_in";

    private FeedErrors() {
    }

    /** 读超时（请求可能已经到达服务端，需要先查询再决定是否重发） */
    public static boolean isTimeout(@NonNull ApiException e) {
        Throwable c = e.getCause();
        while (c != null) {
            if (c instanceof SocketTimeoutException || c instanceof InterruptedIOException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    /** data 中的字符串字段（不存在时为 null） */
    @Nullable
    public static String data(@NonNull ApiException e, @NonNull String key) {
        return e.dataString(key);
    }
}
