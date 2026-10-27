package com.zhimu.slmp.common.service;

import io.reactivex.rxjava3.core.Completable;

/** 注销成功后停止本机账号相关后台任务，由拥有任务的组件实现。 */
public interface IAccountCleanupService {
    Completable stopPendingWork();
}
