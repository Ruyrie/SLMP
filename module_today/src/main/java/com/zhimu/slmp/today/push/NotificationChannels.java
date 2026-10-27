package com.zhimu.slmp.today.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.zhimu.slmp.today.R;

import java.util.Arrays;

/**
 * 三个通知渠道（03 号 11.2 节、技术文档 2.7.2），ID 与服务端 channel_id 一致：
 * 待办、紧急、系统通知均默认显示横幅。普通渠道使用新版 ID，避免旧渠道的默认重要级别无法升级。
 * 迁移保留用户主动关闭或降低的重要级别，以及声音、震动等设置；重复创建保留新版渠道的用户设置。
 */
public final class NotificationChannels {

    public static final String TODO = "zm_todo_v2";
    public static final String URGENT = "zm_urgent";
    public static final String SYSTEM = "zm_system_v2";

    private NotificationChannels() {
    }

    public static void createAll(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel todo = createChannel(nm, TODO, "zm_todo",
                context.getString(R.string.today_channel_todo), context.getString(R.string.today_channel_todo_desc));
        NotificationChannel urgent = createChannel(nm, URGENT, null,
                context.getString(R.string.today_channel_urgent), context.getString(R.string.today_channel_urgent_desc));
        NotificationChannel system = createChannel(nm, SYSTEM, "zm_system",
                context.getString(R.string.today_channel_system), context.getString(R.string.today_channel_system_desc));
        nm.createNotificationChannels(Arrays.asList(todo, urgent, system));
    }

    private static NotificationChannel createChannel(NotificationManager nm, String id, String legacyId,
                                                      String name, String description) {
        NotificationChannel existing = nm.getNotificationChannel(id);
        if (existing != null) {
            existing.setName(name);
            existing.setDescription(description);
            return existing;
        }
        NotificationChannel previous = legacyId == null ? null : nm.getNotificationChannel(legacyId);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        if (previous != null && (previous.hasUserSetImportance()
                || previous.getImportance() == NotificationManager.IMPORTANCE_NONE)) {
            importance = previous.getImportance();
        }
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        if (previous != null) {
            channel.setSound(previous.getSound(), previous.getAudioAttributes());
            if (previous.getVibrationPattern() != null) {
                channel.setVibrationPattern(previous.getVibrationPattern());
            }
            channel.enableVibration(previous.shouldVibrate());
            channel.enableLights(previous.shouldShowLights());
            channel.setLightColor(previous.getLightColor());
            channel.setShowBadge(previous.canShowBadge());
            channel.setLockscreenVisibility(previous.getLockscreenVisibility());
            channel.setGroup(previous.getGroup());
        }
        return channel;
    }

    /** 待办渠道是否打开（通知总开关由调用方另行检测） */
    public static boolean isTodoChannelOn(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return false;
        }
        NotificationChannel c = nm.getNotificationChannel(TODO);
        return c == null || c.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
}
