package com.zhimu.slmp.media.work;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;

import java.util.Locale;

/**
 * 视频处理的前台通知（dataSync 类型，03 号规范 8.4 节）：“正在处理视频 62%”，带进度条，静默、不可划掉。
 * 未授予通知权限时系统不显示通知，任务照常运行。
 */
final class UploadNotifications {

    static final String CHANNEL_ID = "zmt_media_upload";
    private static final int ID_BASE = 7_000;

    private UploadNotifications() {
    }

    static void ensureChannel(@NonNull Context context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
                new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName("照片和视频上传")
                        .setDescription("处理和上传巡检、异常上报中的视频")
                        .setShowBadge(false)
                        .build());
    }

    @NonNull
    static String text(int percent) {
        return String.format(Locale.ROOT, "正在处理视频 %d%%", Math.max(0, Math.min(100, percent)));
    }

    @NonNull
    static ForegroundInfo foregroundInfo(@NonNull Context context, long taskId, int percent) {
        ensureChannel(context);
        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.zhimu.slmp.base.R.drawable.ic_videocam)
                .setContentTitle(text(percent))
                .setContentText("离开页面或锁屏也会继续处理")
                .setProgress(100, Math.max(0, Math.min(100, percent)), percent <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        return new ForegroundInfo(ID_BASE + (int) (taskId % 1000), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }
}
