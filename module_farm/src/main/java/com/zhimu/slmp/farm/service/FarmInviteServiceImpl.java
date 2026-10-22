package com.zhimu.slmp.farm.service;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmInviteService;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.repository.MemberRepository;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 邀请与转让处理（API-F21、F22、F30、F31），供模块03 消息中心 T06、通知详情 T07 调用。
 * 同意邀请 / 接受转让成功后调 API-F02 取该养殖场的上下文返回：
 * 当前没有养殖场或该养殖场就是当前养殖场时写入 FarmContext（角色变化后导航随之重建）；否则不切换，由调用方提示“切换过去”。
 */
@FeatureScope
public class FarmInviteServiceImpl implements IFarmInviteService {

    private final MemberRepository memberRepository;
    private final FarmRepository farmRepository;
    private final FarmContext farmContext;

    @Inject
    public FarmInviteServiceImpl(@NonNull MemberRepository memberRepository, @NonNull FarmRepository farmRepository,
                                 @NonNull FarmContext farmContext) {
        this.memberRepository = memberRepository;
        this.farmRepository = farmRepository;
        this.farmContext = farmContext;
    }

    @NonNull
    @Override
    public Single<FarmContextVO> acceptInvite(long inviteId) {
        return memberRepository.acceptInvite(inviteId)
                .flatMap(vo -> farmRepository.getContext(vo.getFarmId()))
                .doOnSuccess(this::updateIfCurrentOrEmpty);
    }

    @NonNull
    @Override
    public Completable rejectInvite(long inviteId) {
        return memberRepository.rejectInvite(inviteId);
    }

    @NonNull
    @Override
    public Single<FarmContextVO> acceptTransfer(long transferId) {
        return farmRepository.acceptTransfer(transferId)
                .flatMap(vo -> farmRepository.getContext(vo.getFarmId()))
                .doOnSuccess(this::updateIfCurrentOrEmpty);
    }

    @NonNull
    @Override
    public Completable rejectTransfer(long transferId) {
        return farmRepository.rejectTransfer(transferId);
    }

    private void updateIfCurrentOrEmpty(@NonNull FarmContextVO ctx) {
        Long current = farmContext.currentFarmId();
        if (current == null || current == ctx.getFarmId()) {
            farmContext.update(ctx);
        }
    }
}
