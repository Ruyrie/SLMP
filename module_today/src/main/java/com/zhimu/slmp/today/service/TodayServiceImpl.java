package com.zhimu.slmp.today.service;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.today.data.MessageRepository;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * ITodoService 实现：登记回流（todoChanges，T01 淡出该项、T03 刷新、T04 重新加载）与全应用唯一的消息未读数 LiveData
 * （各一级页顶部栏观察）。未读数来源：API-T01 unread_count、API-T13（回到前台、推送到达、筛选变化）、API-T14 与 API-T15 的
 * total_unread；推送到达只触发 API-T13，不在本地加 1。
 */
@FeatureScope
public class TodayServiceImpl implements ITodoService {

    private static final String TAG = "TodayService";

    private final MessageRepository messageRepository;
    private final Subject<TodoChange> changes = PublishSubject.<TodoChange>create().toSerialized();
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);
    private Disposable refreshing = Disposable.disposed();
    private long sessionGeneration;

    @Inject
    public TodayServiceImpl(@NonNull MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public void notifyTodoChanged(@NonNull TodoChange change) {
        changes.onNext(change);
    }

    @NonNull
    @Override
    public Observable<TodoChange> todoChanges() {
        return changes.hide();
    }

    @NonNull
    @Override
    public LiveData<Integer> unreadCount() {
        return unread;
    }

    @Override
    public void setUnread(int count) {
        int value = Math.max(0, count);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            unread.setValue(value);
        } else {
            unread.postValue(value);
        }
    }

    /** API-T13；失败静默 */
    @Override
    public synchronized void refreshUnread() {
        if (!refreshing.isDisposed()) {
            return;
        }
        long generation = sessionGeneration;
        refreshing = messageRepository.getUnreadCount(null, null)
                .subscribeOn(Schedulers.io())
                .subscribe(vo -> {
                    synchronized (TodayServiceImpl.this) {
                        if (generation == sessionGeneration) setUnread(vo.getTotalUnread());
                    }
                }, t -> Log.i(TAG, "未读数刷新失败：" + t.getMessage()));
    }

    @Override public synchronized void clearSessionState() {
        sessionGeneration++;
        refreshing.dispose();
        refreshing = Disposable.disposed();
        setUnread(0);
    }
}
