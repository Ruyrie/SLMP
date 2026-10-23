package com.zhimu.slmp.ai.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.model.AiCodes;

/**
 * 当前网络（公共组件尚无 NetworkStateMonitor，本模块先用 ConnectivityManager 即时查询）：K01 发送前判断是否联网，
 * K05 开播时判断 Wi-Fi / 移动网络。ViewModel 依赖本接口，单元测试用假实现。
 */
public interface NetworkStatus {

    boolean isConnected();

    /** WIFI / MOBILE / UNKNOWN（AiCodes.NET_*） */
    @NonNull
    String type();

    /** 系统实现 */
    final class Android implements NetworkStatus {

        private final ConnectivityManager cm;

        public Android(@NonNull Context context) {
            this.cm = context.getApplicationContext().getSystemService(ConnectivityManager.class);
        }

        @Override
        public boolean isConnected() {
            NetworkCapabilities c = caps();
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        @NonNull
        @Override
        public String type() {
            NetworkCapabilities c = caps();
            if (c == null) {
                return AiCodes.NET_UNKNOWN;
            }
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return AiCodes.NET_WIFI;
            }
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return AiCodes.NET_MOBILE;
            }
            return AiCodes.NET_UNKNOWN;
        }

        private NetworkCapabilities caps() {
            if (cm == null) {
                return null;
            }
            Network n = cm.getActiveNetwork();
            return n == null ? null : cm.getNetworkCapabilities(n);
        }
    }
}
