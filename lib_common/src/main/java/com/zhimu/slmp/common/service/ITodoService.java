package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import io.reactivex.rxjava3.core.Observable;

/**
 * 待办与消息未读服务（模块03 实现）。
 */
public interface ITodoService {

    /** 登记完成等导致待办变化时调用 */
    void notifyTodoChanged(@NonNull TodoChange change);

    @NonNull
    Observable<TodoChange> todoChanges();

    /** 消息未读数（一级页顶部栏角标） */
    @NonNull
    LiveData<Integer> unreadCount();

    void setUnread(int count);

    /** 重新获取未读数（应用回到前台、收到推送时） */
    void refreshUnread();

    /** 注销成功后停止旧会话刷新并清空进程内的消息角标。 */
    void clearSessionState();
}
