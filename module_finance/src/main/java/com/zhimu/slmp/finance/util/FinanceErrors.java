package com.zhimu.slmp.finance.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

/**
 * 模块10 通用错误与提示文案（技术准备文档 2.5、UI 设计文档 2.5）。
 */
public final class FinanceErrors {

    public static final String MSG_NO_PERMISSION = "你没有查看经营数据的权限。经营功能只对所有者和场长开放。";
    public static final String MSG_ROLE_CHANGED = "你在%s的角色已变为员工，不能查看经营数据。";
    public static final String MSG_TIMEOUT = "提交超时，请重试";
    public static final String MSG_NETWORK_KEPT = "网络连接已断开，已填写的内容已保留";
    public static final String MSG_REFRESH_FAILED = "刷新失败，请检查网络";

    private FinanceErrors() {
    }

    public static boolean isNetworkError(@NonNull Throwable t) {
        return ApiException.from(t).isNetwork();
    }

    /** 读超时（提交后需经 API-E43 确认是否已保存） */
    public static boolean isTimeout(@NonNull Throwable e) {
        Throwable c = e;
        int guard = 0;
        while (c != null && guard++ < 8) {
            if (c instanceof SocketTimeoutException || c instanceof InterruptedIOException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    /**
     * 40303 的整页文案：FarmContext 仍为所有者或场长时说明角色已变为员工，否则为员工无权查看。
     */
    @NonNull
    public static String noPermissionText(@Nullable FarmContext farm, @Nullable ApiException e) {
        if (e != null && e.getCode() == ErrorCodes.ROLE_FORBIDDEN && farm != null && farm.isOwnerOrManager()) {
            String name = farm.current() == null || farm.current().getName() == null ? "当前养殖场"
                    : farm.current().getName();
            return String.format(MSG_ROLE_CHANGED, name);
        }
        return MSG_NO_PERMISSION;
    }
}
