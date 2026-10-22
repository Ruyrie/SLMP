package com.zhimu.slmp.today.ui.push;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.today.model.vo.PushExtras;
import com.zhimu.slmp.today.util.MessageOpenCoordinator;
import com.zhimu.slmp.today.util.NavTarget;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 推送中转（03 号 11.3 节、技术文档 2.7.3）：无有效会话时先登录；无 message_id（每日汇总）直接打开“今日”；
 * 否则调 API-T15（push_id 记录点击），need_switch 为真先切换养殖场，再按 2.6.7 规则打开；
 * 网络失败打开 T06；40401 打开 T06 并提示“记录不存在或无权查看”。
 */
public class PushRouterViewModel extends BaseViewModel {

    private final MessageOpenCoordinator coordinator;
    private final SessionManager sessionManager;

    private final MutableLiveData<Boolean> routing = new MutableLiveData<>(false);
    private final SingleLiveEvent<Void> requireLogin = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> finish = new SingleLiveEvent<>();

    /** 本次处理的推送附加字段（普通字段） */
    private PushExtras extras = new PushExtras();

    @Inject
    public PushRouterViewModel(@NonNull MessageOpenCoordinator coordinator, @NonNull SessionManager sessionManager) {
        this.coordinator = coordinator;
        this.sessionManager = sessionManager;
    }

    public LiveData<Boolean> getRouting() {
        return routing;
    }

    public SingleLiveEvent<Void> getRequireLogin() {
        return requireLogin;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    public SingleLiveEvent<Void> getFinish() {
        return finish;
    }

    public void handle(@NonNull PushExtras pushExtras) {
        if (Boolean.TRUE.equals(routing.getValue())) {
            return;
        }
        extras = pushExtras;
        if (!sessionManager.isLoggedIn()) {
            requireLogin.call();
            return;
        }
        route();
    }

    /** 登录页返回：成功后继续，取消则结束 */
    public void onLoginResult(boolean ok) {
        if (ok && sessionManager.isLoggedIn()) {
            route();
        } else {
            finish.call();
        }
    }

    private void route() {
        if (!extras.hasMessage()) {
            navEvent.setValue(NavTarget.of("T01"));
            return;
        }
        routing.setValue(true);
        disposables.add(coordinator.openById(extras.getMessageId(), extras.getPushId(), true)
                .defaultIfEmpty(NavTarget.of("T06"))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(target -> {
                    routing.setValue(false);
                    navEvent.setValue(target);
                }, t -> {
                    routing.setValue(false);
                    ApiException e = ApiException.from(t);
                    if (e.getCode() == ErrorCodes.NOT_FOUND) {
                        GlobalEvents.postMessage(e.getMessage() == null ? "记录不存在或无权查看" : e.getMessage());
                    } else if (!e.isNetwork() && e.getMessage() != null) {
                        GlobalEvents.postMessage(e.getMessage());
                    }
                    navEvent.setValue(NavTarget.of("T06"));
                }));
    }
}
