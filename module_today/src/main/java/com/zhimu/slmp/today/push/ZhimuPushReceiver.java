package com.zhimu.slmp.today.push;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.today.model.vo.PushExtras;
import com.zhimu.slmp.today.ui.push.PushRouterActivity;

import cn.jpush.android.api.JPushInterface;
import cn.jpush.android.api.NotificationMessage;
import cn.jpush.android.service.JPushMessageReceiver;

/**
 * 极光消息接收器（不导出）：通知到达时刷新未读数（API-T13，不在本地加 1）；点击时启动 PushRouterActivity
 * （SDK 自带中转页回调，满足 targetSdk 31 起的通知跳板限制）；注册完成与通知开关变化时重新上报设备（API-T20）。
 */
public class ZhimuPushReceiver extends JPushMessageReceiver {

    private static final String TAG = "ZmtPush";

    @Override
    public void onNotifyMessageArrived(Context context, NotificationMessage message) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.refreshUnread();
        }
    }

    @Override
    public void onNotifyMessageOpened(Context context, NotificationMessage message) {
        PushExtras extras = PushExtras.fromJson(message == null ? null : message.notificationExtras);
        if (message != null && message.msgId != null) {
            JPushInterface.reportNotificationOpened(context, message.msgId);
        }
        Intent intent = new Intent(context, PushRouterActivity.class)
                .setAction(PushRouterActivity.ACTION_OPEN)
                .putExtras(extras.toBundle())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "打开推送中转页失败", e);
        }
    }

    @Override
    public void onRegister(Context context, String registrationId) {
        syncDevice();
    }

    @Override
    public void onNotificationSettingsCheck(Context context, boolean isOn, int source) {
        syncDevice();
    }

    private static void syncDevice() {
        IPushService push = ServiceHub.get(IPushService.class);
        if (push != null) {
            push.syncDevice();
        }
    }
}
