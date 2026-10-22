package com.zhimu.slmp.farm.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.rxjava3.RxPagingSource;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.farm.model.request.InviteMemberRequest;
import com.zhimu.slmp.farm.model.request.UpdateMemberPensRequest;
import com.zhimu.slmp.farm.model.request.UpdateMemberRoleRequest;
import com.zhimu.slmp.farm.model.vo.InviteAcceptVO;
import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.farm.model.vo.MemberChangeResultVO;
import com.zhimu.slmp.farm.model.vo.MemberDetailVO;
import com.zhimu.slmp.farm.model.vo.MemberItemVO;
import com.zhimu.slmp.farm.model.vo.MemberRemoveResultVO;
import com.zhimu.slmp.farm.model.vo.MemberSummaryVO;
import com.zhimu.slmp.farm.model.vo.PenOptionVO;
import com.zhimu.slmp.farm.model.vo.RemovalImpactVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import io.reactivex.rxjava3.functions.Consumer;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 成员与邀请（API-F15–F26）。
 */
@FeatureScope
public class MemberRepository {

    private final FarmApi api;

    @Inject
    public MemberRepository(@NonNull FarmApi api) {
        this.api = api;
    }

    @NonNull
    public Single<MemberSummaryVO> getMemberSummary(long farmId) {
        return api.getMemberSummary(farmId).compose(RxResult.unwrap());
    }

    /** API-F16（role：ALL / MANAGER / STAFF；keyword 为空不传） */
    @NonNull
    public RxPagingSource<Integer, MemberItemVO> memberPagingSource(long farmId, @NonNull String role,
                                                                   @Nullable String keyword) {
        return memberPagingSource(farmId, role, keyword, null);
    }

    /** onFirstPage 在 io 线程收到第 1 页（读取 total） */
    @NonNull
    public RxPagingSource<Integer, MemberItemVO> memberPagingSource(long farmId, @NonNull String role,
                                                                   @Nullable String keyword,
                                                                   @Nullable Consumer<PageVO<MemberItemVO>> onFirstPage) {
        String k = blankToNull(keyword);
        return new PagedSource<>((page, size) -> api.listMembers(farmId, role, k, page, size)
                .compose(RxResult.unwrap()), onFirstPage);
    }

    @NonNull
    public Single<MemberDetailVO> getMemberDetail(long farmId, long memberId) {
        return api.getMemberDetail(farmId, memberId).compose(RxResult.unwrap());
    }

    /** API-F18：memberId 为空为邀请场景 */
    @NonNull
    public Single<List<PenOptionVO>> getPenOptions(long farmId, @Nullable Long memberId) {
        return api.getPenOptions(farmId, memberId).compose(RxResult.unwrap());
    }

    /** API-F19 */
    @NonNull
    public RxPagingSource<Integer, InviteItemVO> invitePagingSource(long farmId, @Nullable String keyword) {
        return invitePagingSource(farmId, keyword, null);
    }

    @NonNull
    public RxPagingSource<Integer, InviteItemVO> invitePagingSource(long farmId, @Nullable String keyword,
                                                                   @Nullable Consumer<PageVO<InviteItemVO>> onFirstPage) {
        String k = blankToNull(keyword);
        return new PagedSource<>((page, size) -> api.listInvites(farmId, k, page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }

    @NonNull
    public Single<InviteItemVO> sendInvite(long farmId, @NonNull InviteMemberRequest req, @NonNull String idemKey) {
        return api.sendInvite(farmId, req, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<InviteAcceptVO> acceptInvite(long inviteId) {
        return api.acceptInvite(inviteId).compose(RxResult.unwrap());
    }

    @NonNull
    public Completable rejectInvite(long inviteId) {
        return RxResult.completable(api.rejectInvite(inviteId));
    }

    @NonNull
    public Single<MemberChangeResultVO> updateRole(long farmId, long memberId, @NonNull UpdateMemberRoleRequest req) {
        return api.updateRole(farmId, memberId, req).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<MemberChangeResultVO> updatePens(long farmId, long memberId, @NonNull UpdateMemberPensRequest req) {
        return api.updatePens(farmId, memberId, req).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<RemovalImpactVO> getRemovalImpact(long farmId, long memberId) {
        return api.getRemovalImpact(farmId, memberId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<MemberRemoveResultVO> removeMember(long farmId, long memberId, int version) {
        return api.removeMember(farmId, memberId, version).compose(RxResult.unwrap());
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
