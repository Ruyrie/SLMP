package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.model.vo.FarmContextVO;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 邀请与转让处理（模块02 实现，模块03 消息中心与待办调用）。接受后返回新的当前养殖场上下文。
 */
public interface IFarmInviteService {

    @NonNull
    Single<FarmContextVO> acceptInvite(long inviteId);

    @NonNull
    Completable rejectInvite(long inviteId);

    @NonNull
    Single<FarmContextVO> acceptTransfer(long transferId);

    @NonNull
    Completable rejectTransfer(long transferId);
}
