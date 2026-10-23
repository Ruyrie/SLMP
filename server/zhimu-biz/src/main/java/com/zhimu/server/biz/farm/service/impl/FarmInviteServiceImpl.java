package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmInvitePen;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmInvitePenMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.biz.farm.query.InviteQuery;
import com.zhimu.server.biz.farm.request.InviteMemberRequest;
import com.zhimu.server.biz.farm.service.FarmInviteService;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.support.FarmValidations;
import com.zhimu.server.biz.farm.vo.InviteAcceptVO;
import com.zhimu.server.biz.farm.vo.InviteItemVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** API-F19–F22 邀请 */
@Service
@RequiredArgsConstructor
public class FarmInviteServiceImpl implements FarmInviteService {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    private final FarmInviteMapper inviteMapper;
    private final FarmInvitePenMapper invitePenMapper;
    private final FarmMemberMapper memberMapper;
    private final MemberPenMapper memberPenMapper;
    private final FarmLookup lookup;
    private final FarmAssembler assembler;
    private final AccountQueryService accountQuery;
    private final PenQueryService penQuery;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;

    @Override
    public PageVO<InviteItemVO> list(Long farmId, Long userId, InviteQuery query) {
        Viewer viewer = viewer(farmId, userId);
        if (!"OWNER".equals(viewer.member().getRole()) && !"MANAGER".equals(viewer.member().getRole())) {
            throw FarmErrors.memberViewForbidden(viewer.farm().getName());
        }
        requireReadable(viewer);
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        List<FarmInvite> invites = assembler.invitesOfFarm(farmId, false).stream()
                .filter(invite -> keyword.isEmpty() || invite.getInviteePhone().contains(keyword))
                .toList();
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, invites.size());
        int to = Math.min(from + size, invites.size());
        boolean ownerView = "OWNER".equals(viewer.member().getRole());
        List<InviteItemVO> list = new ArrayList<>();
        for (FarmInvite invite : invites.subList(from, to)) {
            list.add(assembler.inviteItem(viewer.farm(), invite, ownerView));
        }
        return new PageVO<>(list, invites.size(), page, size);
    }

    @Override
    @Transactional
    public InviteItemVO send(Long farmId, Long userId, InviteMemberRequest request) {
        Viewer viewer = viewer(farmId, userId);
        if (!"OWNER".equals(viewer.member().getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
        requireNormal(viewer);
        Farm farm = viewer.farm();
        String phone = FarmValidations.normalizeInviteePhone(request.getInviteePhone());
        AccountBrief me = accountQuery.getBrief(userId);
        if (me != null && phone.equals(me.phone())) {
            throw FarmValidations.invalid("invitee_phone", "不能邀请你自己");
        }
        String role = request.getRole();
        if (!"MANAGER".equals(role) && !"STAFF".equals(role)) {
            throw FarmValidations.invalid("role", "请选择场长或员工");
        }
        AccountBrief invitee = accountQuery.findByPhone(phone);
        if (invitee != null && lookup.findActiveMember(farmId, invitee.userId()) != null) {
            throw FarmValidations.invalid("invitee_phone",
                    "这个手机号已经是" + farm.getName() + "的成员");
        }
        FarmInvite pending = TenantSkip.call(() -> inviteMapper.selectOne(Wrappers.<FarmInvite>lambdaQuery()
                .eq(FarmInvite::getFarmId, farmId)
                .eq(FarmInvite::getInviteePhone, phone)
                .eq(FarmInvite::getStatus, "PENDING")
                .last("LIMIT 1")));
        if (pending != null) {
            String before = pending.getExpiresAt() == null ? ""
                    : pending.getExpiresAt().format(MONTH_DAY) + "前";
            throw FarmValidations.invalid("invitee_phone",
                    "已向这个手机号发出邀请，" + before + "有效");
        }
        List<Long> penIds = List.of();
        if ("STAFF".equals(role)) {
            penIds = validatePens(farmId, request.getPenIds());
        }
        FarmInvite resendOf = null;
        if (request.getResendOfId() != null) {
            resendOf = TenantSkip.call(() -> inviteMapper.selectById(request.getResendOfId()));
            if (resendOf == null || !Objects.equals(resendOf.getFarmId(), farmId)
                    || !("EXPIRED".equals(resendOf.getStatus()) || "REJECTED".equals(resendOf.getStatus()))) {
                throw FarmErrors.stateChanged("这条邀请已处理，列表已刷新");
            }
        }
        LocalDateTime now = BeijingTime.now();
        FarmInvite invite = new FarmInvite();
        invite.setFarmId(farmId);
        invite.setInviteePhone(phone);
        invite.setInviteeUserId(invitee == null ? null : invitee.userId());
        invite.setRole(role);
        invite.setStatus("PENDING");
        invite.setPendingFlag(true);
        invite.setExpiresAt(now.plusDays(7));
        invite.setResendOfId(resendOf == null ? null : resendOf.getId());
        invite.setRequestId(RequestContext.idempotencyKey());
        invite.setCreatedBy(userId);
        TenantSkip.run(() -> inviteMapper.insert(invite));
        for (Long penId : penIds) {
            FarmInvitePen invitePen = new FarmInvitePen();
            invitePen.setInviteId(invite.getId());
            invitePen.setFarmId(farmId);
            invitePen.setPenId(penId);
            invitePen.setCreatedBy(userId);
            TenantSkip.run(() -> invitePenMapper.insert(invitePen));
        }
        if (resendOf != null) {
            FarmInvite old = resendOf;
            TenantSkip.run(() -> inviteMapper.update(new FarmInvite(), Wrappers.<FarmInvite>lambdaUpdate()
                    .eq(FarmInvite::getId, old.getId())
                    .in(FarmInvite::getStatus, List.of("EXPIRED", "REJECTED"))
                    .set(FarmInvite::getStatus, "CANCELED")
                    .set(FarmInvite::getPendingFlag, null)
                    .set(FarmInvite::getHandledAt, now)
                    .set(FarmInvite::getCancelReason, "已重新邀请")));
        }
        if (invite.getInviteeUserId() != null) {
            notify.invite(farm, invite, me == null ? "养殖场所有者" : me.name());
        }
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("SEND")
                .bizType(BizType.FARM_INVITE.name())
                .bizId(invite.getId())
                .farmId(farmId)
                .targetName(FarmValidations.maskedPhone(phone))
                .searchText(farm.getName())
                .after(role)
                .build());
        return assembler.inviteItem(farm, invite, true);
    }

    @Override
    @Transactional
    public InviteAcceptVO accept(Long inviteId, Long userId) {
        FarmInvite invite = requireInviteForUser(inviteId, userId);
        if ("ACCEPTED".equals(invite.getStatus())) {
            FarmMember member = lookup.findActiveMember(invite.getFarmId(), userId);
            if (member != null && Objects.equals(member.getId(), invite.getMemberId())) {
                Farm farm = lookup.requireNotDeletedFarm(invite.getFarmId());
                return acceptVO(farm, member);
            }
            throw FarmErrors.stateChanged("这条邀请已失效");
        }
        if (!"PENDING".equals(invite.getStatus())) {
            throw FarmErrors.stateChanged("这条邀请已失效");
        }
        LocalDateTime now = BeijingTime.now();
        if (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(now)) {
            expire(invite, now);
            throw FarmErrors.stateChanged("邀请已过期，请联系对方重新邀请");
        }
        Farm farm = lookup.requireNotDeletedFarm(invite.getFarmId());
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status == FarmStatus.DISABLED) {
            throw FarmErrors.statusForbidden(farm.getName()
                    + "已停用，暂时不能加入，请联系所有者重新启用后再同意");
        }
        if (status != FarmStatus.NORMAL) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        if (lookup.findActiveMember(farm.getId(), userId) != null) {
            cancel(invite, "对方已是成员", now);
            throw FarmErrors.stateChanged("你已经是" + farm.getName() + "的成员");
        }
        FarmMember member = new FarmMember();
        member.setFarmId(farm.getId());
        member.setUserId(userId);
        member.setRole(invite.getRole());
        member.setStatus("ACTIVE");
        member.setActiveFlag(true);
        member.setJoinType("INVITE");
        member.setInviteId(invite.getId());
        member.setJoinedAt(now);
        member.setVersion(0);
        member.setCreatedBy(userId);
        member.setUpdatedBy(userId);
        TenantSkip.run(() -> memberMapper.insert(member));

        List<FarmInvitePen> invitePens = TenantSkip.call(() -> invitePenMapper.selectList(
                Wrappers.<FarmInvitePen>lambdaQuery()
                        .eq(FarmInvitePen::getInviteId, invite.getId())
                        .orderByAsc(FarmInvitePen::getId)));
        Set<Long> enabled = new HashSet<>();
        List<PenInfo> pens = penQuery.listPens(farm.getId(), false);
        if (pens != null) {
            pens.forEach(pen -> enabled.add(pen.penId()));
        }
        for (FarmInvitePen invitePen : invitePens) {
            if (!enabled.contains(invitePen.getPenId())) {
                continue;
            }
            MemberPen memberPen = new MemberPen();
            memberPen.setFarmId(farm.getId());
            memberPen.setMemberId(member.getId());
            memberPen.setUserId(userId);
            memberPen.setPenId(invitePen.getPenId());
            memberPen.setCreatedBy(userId);
            TenantSkip.run(() -> memberPenMapper.insert(memberPen));
        }
        int rows = TenantSkip.call(() -> inviteMapper.update(new FarmInvite(), Wrappers.<FarmInvite>lambdaUpdate()
                .eq(FarmInvite::getId, invite.getId())
                .eq(FarmInvite::getStatus, "PENDING")
                .set(FarmInvite::getStatus, "ACCEPTED")
                .set(FarmInvite::getPendingFlag, null)
                .set(FarmInvite::getHandledAt, now)
                .set(FarmInvite::getMemberId, member.getId())));
        if (rows == 0) {
            throw FarmErrors.stateChanged("这条邀请已失效");
        }
        invite.setStatus("ACCEPTED");
        invite.setHandledAt(now);
        invite.setMemberId(member.getId());
        cache.evict(farm.getId(), userId);
        AccountBrief me = accountQuery.getBrief(userId);
        notify.inviteAccepted(farm, member, me == null ? "新成员" : me.name());
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("APPROVE")
                .bizType(BizType.FARM_INVITE.name())
                .bizId(invite.getId())
                .farmId(farm.getId())
                .targetName(FarmValidations.maskedPhone(invite.getInviteePhone()))
                .after(invite.getRole())
                .build());
        return acceptVO(farm, member);
    }

    @Override
    @Transactional
    public void reject(Long inviteId, Long userId) {
        FarmInvite invite = requireInviteForUser(inviteId, userId);
        if (!"PENDING".equals(invite.getStatus())) {
            throw FarmErrors.stateChanged("这条邀请已失效");
        }
        LocalDateTime now = BeijingTime.now();
        if (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(now)) {
            expire(invite, now);
            throw FarmErrors.stateChanged("邀请已过期，请联系对方重新邀请");
        }
        int rows = TenantSkip.call(() -> inviteMapper.update(new FarmInvite(), Wrappers.<FarmInvite>lambdaUpdate()
                .eq(FarmInvite::getId, invite.getId())
                .eq(FarmInvite::getStatus, "PENDING")
                .set(FarmInvite::getStatus, "REJECTED")
                .set(FarmInvite::getPendingFlag, null)
                .set(FarmInvite::getHandledAt, now)));
        if (rows == 0) {
            throw FarmErrors.stateChanged("这条邀请已失效");
        }
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("REJECT")
                .bizType(BizType.FARM_INVITE.name())
                .bizId(invite.getId())
                .farmId(invite.getFarmId())
                .targetName(FarmValidations.maskedPhone(invite.getInviteePhone()))
                .build());
    }

    // ===== 共用 =====

    private InviteAcceptVO acceptVO(Farm farm, FarmMember member) {
        InviteAcceptVO vo = new InviteAcceptVO();
        vo.setFarmId(farm.getId());
        vo.setFarmName(farm.getName());
        vo.setRole(member.getRole());
        vo.setRoleText(FarmRole.valueOf(member.getRole()).getText());
        return vo;
    }

    private FarmInvite requireInviteForUser(Long inviteId, Long userId) {
        FarmInvite invite = TenantSkip.call(() -> inviteMapper.selectById(inviteId));
        if (invite == null) {
            throw FarmErrors.notFound(null);
        }
        AccountBrief me = accountQuery.getBrief(userId);
        if (me == null) {
            throw FarmErrors.notFound(null);
        }
        boolean phoneMatch = invite.getInviteePhone() != null && invite.getInviteePhone().equals(me.phone());
        boolean userMatch = invite.getInviteeUserId() != null && invite.getInviteeUserId().equals(userId);
        if (invite.getInviteeUserId() != null && !userMatch) {
            throw FarmErrors.notFound(null);
        }
        if (invite.getInviteeUserId() == null && !phoneMatch) {
            throw FarmErrors.notFound(null);
        }
        return invite;
    }

    private void expire(FarmInvite invite, LocalDateTime now) {
        TenantSkip.run(() -> inviteMapper.update(new FarmInvite(), Wrappers.<FarmInvite>lambdaUpdate()
                .eq(FarmInvite::getId, invite.getId())
                .eq(FarmInvite::getStatus, "PENDING")
                .set(FarmInvite::getStatus, "EXPIRED")
                .set(FarmInvite::getPendingFlag, null)
                .set(FarmInvite::getHandledAt, now)));
        invite.setStatus("EXPIRED");
        invite.setHandledAt(now);
    }

    private void cancel(FarmInvite invite, String reason, LocalDateTime now) {
        TenantSkip.run(() -> inviteMapper.update(new FarmInvite(), Wrappers.<FarmInvite>lambdaUpdate()
                .eq(FarmInvite::getId, invite.getId())
                .eq(FarmInvite::getStatus, "PENDING")
                .set(FarmInvite::getStatus, "CANCELED")
                .set(FarmInvite::getPendingFlag, null)
                .set(FarmInvite::getHandledAt, now)
                .set(FarmInvite::getCancelReason, reason)));
        invite.setStatus("CANCELED");
        invite.setHandledAt(now);
    }

    private List<Long> validatePens(Long farmId, List<Long> penIds) {
        if (penIds == null || penIds.isEmpty()) {
            return List.of();
        }
        Set<Long> enabled = new HashSet<>();
        List<PenInfo> pens = penQuery.listPens(farmId, false);
        if (pens != null) {
            pens.forEach(pen -> enabled.add(pen.penId()));
        }
        List<Long> result = new ArrayList<>();
        for (Long penId : penIds) {
            if (penId == null || !enabled.contains(penId)) {
                // F20 的栏舍校验错误只给 message，不带 field_errors（App 会显示到手机号框）
                throw new BizException(ErrorCode.PARAM_INVALID, "栏舍已停用，请重新选择");
            }
            result.add(penId);
        }
        return result;
    }

    private Viewer viewer(Long farmId, Long userId) {
        Farm farm = lookup.findFarmIncludingDeleted(farmId);
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        FarmMember member = lookup.findActiveMember(farmId, userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        return new Viewer(farm, member, FarmStatuses.of(farm));
    }

    private static void requireReadable(Viewer viewer) {
        if (viewer.status() == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(viewer.farm().getName());
        }
        if (!FarmStatuses.readable(viewer.status())) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
    }

    private static void requireNormal(Viewer viewer) {
        if (viewer.status() == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(viewer.farm().getName());
        }
        if (viewer.status() != FarmStatus.NORMAL) {
            throw FarmErrors.statusForbidden(viewer.status() == FarmStatus.DISABLED
                    ? FarmStatuses.MSG_DISABLED_RESTRICT.formatted(viewer.farm().getName())
                    : FarmStatuses.MSG_PENDING_RESTRICT);
        }
    }

    private record Viewer(Farm farm, FarmMember member, FarmStatus status) {
    }
}
