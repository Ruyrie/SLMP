package com.zhimu.slmp.today.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.zhimu.slmp.today.R;

import java.util.Arrays;

/**
 * 三个通知渠道（03 号 11.2 节、技术文档 2.7.2），ID 与服务端 channel_id 一致：
 * zm_todo 待办提醒（默认）、zm_urgent 紧急提醒（高，横幅弹出）、zm_system 审核与系统（默认）。
 * 应用启动时创建（重复创建只更新名称）。
 */
public final class NotificationChannels {

    public static final String TODO = "zm_todo";
    public static final String URGENT = "zm_urgent";
    public static final String SYSTEM = "zm_system";

    private NotificationChannels() {
    }

    public static void createAll(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel todo = new NotificationChannel(TODO,
                context.getString(R.string.today_channel_todo), NotificationManager.IMPORTANCE_DEFAULT);
        todo.setDescription(context.getString(R.string.today_channel_todo_desc));
        NotificationChannel urgent = new NotificationChannel(URGENT,
                context.getString(R.string.today_channel_urgent), NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription(context.getString(R.string.today_channel_urgent_desc));
        NotificationChannel system = new NotificationChannel(SYSTEM,
                context.getString(R.string.today_channel_system), NotificationManager.IMPORTANCE_DEFAULT);
        system.setDescription(context.getString(R.string.today_channel_system_desc));
        nm.createNotificationChannels(Arrays.asList(todo, urgent, system));
    }

    /** 系统通知总开关与 zm_todo 渠道是否都打开 */
    public static boolean isTodoChannelOn(@NonNull Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return false;
        }
        NotificationChannel c = nm.getNotificationChannel(TODO);
        return c == null || c.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
}
