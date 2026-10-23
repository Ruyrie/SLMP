package com.zhimu.slmp.finance.util;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.function.LongConsumer;

/**
 * 系统下载完成广播（ACTION_DOWNLOAD_COMPLETE）：E13 在 onStart 注册（RECEIVER_EXPORTED，广播来自系统下载进程）、onStop 注销。
 */
public class DownloadCompleteReceiver extends BroadcastReceiver {

    private final LongConsumer onComplete;
    private boolean registered;

    public DownloadCompleteReceiver(@NonNull LongConsumer onComplete) {
        this.onComplete = onComplete;
    }

    public void register(@NonNull Context context) {
        if (registered) {
            return;
        }
        ContextCompat.registerReceiver(context, this, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED);
        registered = true;
    }

    public void unregister(@NonNull Context context) {
        if (registered) {
            context.unregisterReceiver(this);
            registered = false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id != -1) {
            onComplete.accept(id);
        }
    }
}
