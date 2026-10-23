package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.biz.farm.query.MemberQuery;
import com.zhimu.server.biz.farm.request.UpdateMemberPensRequest;
import com.zhimu.server.biz.farm.request.UpdateMemberRoleRequest;
import com.zhimu.server.biz.farm.service.FarmMemberService;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.vo.MemberChangeResultVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberItemVO;
import com.zhimu.server.biz.farm.vo.MemberRemoveResultVO;
import com.zhimu.server.biz.farm.vo.MemberSummaryVO;
import com.zhimu.server.biz.farm.vo.PenOptionVO;
import com.zhimu.server.biz.farm.vo.RemovalImpactVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** API-F15–F18、F23–F27 */
@Service
@RequiredArgsConstructor
public class FarmMemberServiceImpl implements FarmMemberService {

    private static final Map<String, Integer> ROLE_ORDER = Map.of("OWNER", 0, "MANAGER", 1, "STAFF", 2);

    private final FarmMemberMapper memberMapper;
    private final MemberPenMapper memberPenMapper;
    private final FarmTransferMapper transferMapper;
    private final FarmLookup lookup;
    private final FarmAssembler assembler;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;
    private final PenQueryService penQuery;

    @Override
    public MemberSummaryVO summary(Long farmId, Long userId) {
        Viewer viewer = viewer(farmId, userId);
        requireManagerView(viewer);
        requireReadable(viewer);
        return assembler.memberSummary(viewer.farm());
    }

    @Override
    public PageVO<MemberItemVO> list(Long farmId, Long userId, MemberQuery query) {
        Viewer viewer = viewer(farmId, userId);
        requireManagerView(viewer);
        requireReadable(viewer);
        String role = query.roleOrDefault();
        if (!List.of("ALL", "OWNER", "MANAGER", "STAFF").contains(role)) {
            throw com.zhimu.server.biz.farm.support.FarmValidations.invalid("role", "角色筛选不正确");
        }
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        if (com.zhimu.server.biz.farm.support.FarmValidations.length(keyword) > 20) {
            throw com.zhimu.server.biz.farm.support.FarmValidations.invalid("keyword",
                    "搜索内容不能超过 20 个字");
        }
        List<FarmMember> members = lookup.activeMembers(farmId).stream()
                .filter(m -> "ALL".equals(role) || role.equals(m.getRole()))
                .toList();
        Map<Long, AccountBrief> accounts = assembler.accounts(
                members.stream().map(FarmMember::getUserId).toList());
        List<FarmMember> filtered = new ArrayList<>();
        for (FarmMember member : members) {
            if (keyword.isEmpty() || matches(accounts.get(member.getUserId()), keyword)) {
                filtered.add(member);
            }
        }
        filtered.sort(Comparator
                .comparingInt((FarmMember m) -> ROLE_ORDER.getOrDefault(m.getRole(), 9))
                .thenComparing(FarmMember::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FarmMember::getId));
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<MemberItemVO> list = new ArrayList<>();
        for (FarmMember member : filtered.subList(from, to)) {
            list.add(assembler.memberItem(viewer.farm(), member, viewer.member(),
                    accounts.get(member.getUserId()), viewer.status()));
        }
        return new PageVO<>(list, filtered.size(), page, size);
    }

    @Override
    public MemberDetailVO detail(Long farmId, Long memberId, Long userId) {
        Viewer viewer = viewer(farmId, userId);
        requireManagerView(viewer);
        requireReadable(viewer);
        FarmMember target = requireMember(viewer.farm(), memberId);
        return assembler.memberDetail(viewer.farm(), target, viewer.member(),
                assembler.account(target.getUserId()), viewer.status());
    }

    @Override
    public List<PenOptionVO> penOptions(Long farmId, Long memberId, Long userId) {
        Viewer viewer = viewer(farmId, userId);
        requireReadable(viewer);
        FarmMember target = null;
        if (memberId == null) {
            if (!"OWNER".equals(viewer.member().getRole())) {
                throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
            }
        } else {
            target = requireMember(viewer.farm(), memberId);
            if (!"STAFF".equals(target.getRole())) {
                throw FarmErrors.stateChanged("该成员不是员工，不需要分配栏舍");
            }
        }
        return assembler.penOptions(farmId, memberId, target);
    }

    @Override
    public RemovalImpactVO removalImpact(Long farmId, Long memberId, Long userId) {
        Viewer viewer = viewer(farmId, userId);
        requireOwner(viewer);
        requireReadable(viewer);
        FarmMember target = requireMember(viewer.farm(), memberId);
        AccountBrief account = assembler.account(target.getUserId());
        RemovalImpactVO vo = new RemovalImpactVO();
        vo.setMemberId(target.getId());
        vo.setMemberName(account == null ? null : account.name());
        vo.setRole(target.getRole());
        vo.setPendingWorkTodoCount(0);
        vo.setOrphanPens("STAFF".equals(target.getRole())
                ? orphanPens(farmId, target.getId()) : List.of());
        return vo;
    }

    @Override
    @Transactional
    public MemberChangeResultVO updateRole(Long farmId, Long memberId, Long userId,
                                           UpdateMemberRoleRequest request) {
        Viewer viewer = viewer(farmId, userId);
        requireOwner(viewer);
        requireNormal(viewer);
        FarmMember target = requireMember(viewer.farm(), memberId);
        if (Objects.equals(target.getUserId(), userId)) {
            throw FarmErrors.roleForbidden("不能修改自己的角色");
        }
        if ("OWNER".equals(target.getRole())) {
            throw FarmErrors.roleForbidden("所有者的角色只能通过转让所有权变更");
        }
        String role = request.getRole();
        if (!"MANAGER".equals(role) && !"STAFF".equals(role)) {
            throw com.zhimu.server.biz.farm.support.FarmValidations.invalid("role",
                    "请选择场长或员工");
        }
        com.zhimu.server.biz.farm.support.FarmValidations.requireVersion(request.getVersion());
        if (!Objects.equals(request.getVersion(), target.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        MemberChangeResultVO result = new MemberChangeResultVO();
        if (role.equals(target.getRole())) {
            result.setMember(assembler.memberDetail(viewer.farm(), target, viewer.member(),
                    assembler.account(target.getUserId()), viewer.status()));
            result.setNewlyUnassignedPens(List.of());
            return result;
        }
        List<Long> oldPens = lookup.memberPens(farmId, target.getId()).stream()
                .map(MemberPen::getPenId).toList();
        List<Long> unassigned = "STAFF".equals(target.getRole()) && "MANAGER".equals(role)
                ? newlyUnassigned(farmId, target.getId(), oldPens, List.of()) : List.of();
        String beforeRole = target.getRole();
        target.setRole(role);
        target.setUpdatedBy(userId);
        if (memberMapper.updateById(target) == 0) {
            throw FarmErrors.versionConflict();
        }
        if ("MANAGER".equals(role)) {
            TenantSkip.run(() -> memberPenMapper.delete(Wrappers.<MemberPen>lambdaQuery()
                    .eq(MemberPen::getFarmId, farmId)
                    .eq(MemberPen::getMemberId, target.getId())));
        } else {
            // 受让人改为员工后不再具有接收所有权的资格，取消待确认的转让
            TenantSkip.run(() -> transferMapper.update(new FarmTransfer(),
                    Wrappers.<FarmTransfer>lambdaUpdate()
                            .eq(FarmTransfer::getFarmId, farmId)
                            .eq(FarmTransfer::getToMemberId, target.getId())
                            .eq(FarmTransfer::getStatus, "PENDING")
                            .set(FarmTransfer::getStatus, "CANCELED")
                            .set(FarmTransfer::getPendingFlag, null)
                            .set(FarmTransfer::getHandledAt, BeijingTime.now())
                            .set(FarmTransfer::getCancelReason, "对方已不是本场场长")));
        }
        cache.evict(farmId, target.getUserId());
        notify.roleChanged(viewer.farm(), target, FarmRole.valueOf(role).getText());
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("UPDATE")
                .bizType(BizType.FARM_MEMBER.name())
                .bizId(target.getId())
                .farmId(farmId)
                .targetName(assembler.account(target.getUserId()) == null ? null
                        : assembler.account(target.getUserId()).name())
                .before(Map.of("role", beforeRole))
                .after(Map.of("role", role))
                .build());
        result.setMember(assembler.memberDetail(viewer.farm(), target, viewer.member(),
                assembler.account(target.getUserId()), viewer.status()));
        result.setNewlyUnassignedPens(assembler.penBriefs(unassigned, farmId));
        return result;
    }

    @Override
    @Transactional
    public MemberChangeResultVO updatePens(Long farmId, Long memberId, Long userId,
                                           UpdateMemberPensRequest request) {
        Viewer viewer = viewer(farmId, userId);
        requireManagerView(viewer);
        requireNormal(viewer);
        FarmMember target = requireMember(viewer.farm(), memberId);
        if (!"STAFF".equals(target.getRole())) {
            throw FarmErrors.stateChanged("该成员不是员工，不需要分配栏舍");
        }
        com.zhimu.server.biz.farm.support.FarmValidations.requireVersion(request.getVersion());
        if (!Objects.equals(request.getVersion(), target.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        Set<Long> penIds = validatePens(farmId, request.getPenIds());
        List<Long> oldPens = lookup.memberPens(farmId, target.getId()).stream()
                .map(MemberPen::getPenId).toList();
        List<Long> unassigned = newlyUnassigned(farmId, target.getId(), oldPens, new ArrayList<>(penIds));
        TenantSkip.run(() -> memberPenMapper.delete(Wrappers.<MemberPen>lambdaQuery()
                .eq(MemberPen::getFarmId, farmId)
                .eq(MemberPen::getMemberId, target.getId())));
        for (Long penId : penIds) {
            MemberPen memberPen = new MemberPen();
            memberPen.setFarmId(farmId);
            memberPen.setMemberId(target.getId());
            memberPen.setUserId(target.getUserId());
            memberPen.setPenId(penId);
            memberPen.setCreatedBy(userId);
            TenantSkip.run(() -> memberPenMapper.insert(memberPen));
        }
        target.setUpdatedBy(userId);
        if (memberMapper.updateById(target) == 0) {
            throw FarmErrors.versionConflict();
        }
        cache.evict(farmId, target.getUserId());
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("UPDATE")
                .bizType(BizType.FARM_MEMBER.name())
                .bizId(target.getId())
                .farmId(farmId)
                .before(Map.of("pen_ids", oldPens))
                .after(Map.of("pen_ids", penIds))
                .build());
        MemberChangeResultVO result = new MemberChangeResultVO();
        result.setMember(assembler.memberDetail(viewer.farm(), target, viewer.member(),
                assembler.account(target.getUserId()), viewer.status()));
        result.setNewlyUnassignedPens(assembler.penBriefs(unassigned, farmId));
        return result;
    }

    @Override
    @Transactional
    public MemberRemoveResultVO remove(Long farmId, Long memberId, Long userId, int version) {
        Viewer viewer = viewer(farmId, userId);
        requireOwner(viewer);
        requireReadable(viewer);
        FarmMember target = requireMember(viewer.farm(), memberId);
        if (Objects.equals(target.getUserId(), userId)) {
            throw FarmErrors.roleForbidden("不能移出自己");
        }
        if ("OWNER".equals(target.getRole())) {
            throw FarmErrors.roleForbidden("不能移出所有者");
        }
        if (!Objects.equals(version, target.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        List<Long> oldPens = "STAFF".equals(target.getRole())
                ? lookup.memberPens(farmId, target.getId()).stream().map(MemberPen::getPenId).toList()
                : List.of();
        List<Long> unassigned = "STAFF".equals(target.getRole())
                ? newlyUnassigned(farmId, target.getId(), oldPens, List.of()) : List.of();
        leave(viewer.farm(), target, "REMOVED", userId, oldPens, true);
        MemberRemoveResultVO result = new MemberRemoveResultVO();
        result.setUnassignedPens(assembler.penBriefs(unassigned, farmId));
        return result;
    }

    @Override
    @Transactional
    public String quit(Long farmId, Long userId) {
        Viewer viewer = viewer(farmId, userId);
        if ("OWNER".equals(viewer.member().getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_OWNER_ONLY_QUIT);
        }
        if (!FarmStatuses.approved(viewer.status())) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        FarmMember target = viewer.member();
        List<Long> oldPens = "STAFF".equals(target.getRole())
                ? lookup.memberPens(farmId, target.getId()).stream().map(MemberPen::getPenId).toList()
                : List.of();
        leave(viewer.farm(), target, "QUIT", userId, oldPens, false);
        return viewer.farm().getName();
    }

    // ===== 共用 =====

    private void leave(Farm farm, FarmMember target, String status, Long operatorId, List<Long> oldPens,
                       boolean notifyRemoved) {
        TenantSkip.run(() -> memberPenMapper.delete(Wrappers.<MemberPen>lambdaQuery()
                .eq(MemberPen::getFarmId, farm.getId())
                .eq(MemberPen::getMemberId, target.getId())));
        target.setStatus(status);
        target.setActiveFlag(null);
        target.setLeftAt(BeijingTime.now());
        target.setRemovedBy(operatorId);
        target.setUpdatedBy(operatorId);
        int rows = memberMapper.update(new FarmMember(), Wrappers.<FarmMember>lambdaUpdate()
                .eq(FarmMember::getFarmId, farm.getId())
                .eq(FarmMember::getId, target.getId())
                .eq(FarmMember::getVersion, target.getVersion())
                .set(FarmMember::getStatus, status)
                .set(FarmMember::getActiveFlag, null)
                .set(FarmMember::getLeftAt, target.getLeftAt())
                .set(FarmMember::getRemovedBy, operatorId)
                .set(FarmMember::getUpdatedBy, operatorId)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        target.setVersion(target.getVersion() + 1);
        TenantSkip.run(() -> transferMapper.update(new FarmTransfer(),
                Wrappers.<FarmTransfer>lambdaUpdate()
                        .eq(FarmTransfer::getFarmId, farm.getId())
                        .eq(FarmTransfer::getToMemberId, target.getId())
                        .eq(FarmTransfer::getStatus, "PENDING")
                        .set(FarmTransfer::getStatus, "CANCELED")
                        .set(FarmTransfer::getPendingFlag, null)
                        .set(FarmTransfer::getHandledAt, BeijingTime.now())
                        .set(FarmTransfer::getCancelReason, "对方已不是本场场长")));
        cache.evict(farm.getId(), target.getUserId());
        if (notifyRemoved) {
            notify.memberRemoved(farm, target);
        }
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(operatorId)
                .opType("RECORD_EDIT")
                .opAction("DELETE")
                .bizType(BizType.FARM_MEMBER.name())
                .bizId(target.getId())
                .farmId(farm.getId())
                .extraText("QUIT".equals(status) ? "主动退出" : null)
                .before(Map.of("role", target.getRole(), "pen_ids", oldPens))
                .after(Map.of("status", status))
                .build());
    }

    private Set<Long> validatePens(Long farmId, List<Long> penIds) {
        Set<Long> values = new LinkedHashSet<>();
        if (penIds != null) {
            values.addAll(penIds);
        }
        if (values.isEmpty()) {
            return values;
        }
        List<PenInfo> enabled = penQuery.listPens(farmId, false);
        Set<Long> available = new HashSet<>();
        if (enabled != null) {
            for (PenInfo pen : enabled) {
                available.add(pen.penId());
            }
        }
        for (Long penId : values) {
            if (penId == null || !available.contains(penId)) {
                throw new com.zhimu.server.common.error.BizException(
                        com.zhimu.server.common.error.ErrorCode.PARAM_INVALID, "栏舍已停用，请重新选择");
            }
        }
        return values;
    }

    /** 操作前由该成员负责、操作后无其他员工负责的完整集合 */
    private List<Long> newlyUnassigned(Long farmId, Long memberId, List<Long> oldPens, List<Long> newPens) {
        Set<Long> keep = new HashSet<>(newPens);
        Set<Long> others = new HashSet<>();
        for (FarmMember member : lookup.activeMembers(farmId)) {
            if ("STAFF".equals(member.getRole()) && !Objects.equals(member.getId(), memberId)) {
                others.add(member.getId());
            }
        }
        Set<Long> covered = new HashSet<>();
        for (MemberPen pen : lookup.pensOfFarm(farmId)) {
            if (others.contains(pen.getMemberId())) {
                covered.add(pen.getPenId());
            }
        }
        List<Long> result = new ArrayList<>();
        for (Long penId : oldPens) {
            if (!keep.contains(penId) && !covered.contains(penId)) {
                result.add(penId);
            }
        }
        return result;
    }

    private List<PenBriefVO> orphanPens(Long farmId, Long memberId) {
        List<Long> oldPens = lookup.memberPens(farmId, memberId).stream()
                .map(MemberPen::getPenId).toList();
        return assembler.penBriefs(newlyUnassigned(farmId, memberId, oldPens, List.of()), farmId);
    }

    private static boolean matches(AccountBrief account, String keyword) {
        if (account == null) {
            return false;
        }
        if (account.name() != null && account.name().contains(keyword)) {
            return true;
        }
        return account.phone() != null && account.phone().contains(keyword);
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

    private FarmMember requireMember(Farm farm, Long memberId) {
        FarmMember target = lookup.findActiveMemberById(farm.getId(), memberId);
        if (target == null) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.NOT_FOUND,
                    "该成员已不在" + farm.getName());
        }
        return target;
    }

    private static void requireManagerView(Viewer viewer) {
        if (!"OWNER".equals(viewer.member().getRole()) && !"MANAGER".equals(viewer.member().getRole())) {
            throw FarmErrors.memberViewForbidden(viewer.farm().getName());
        }
    }

    private static void requireOwner(Viewer viewer) {
        if (!"OWNER".equals(viewer.member().getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
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
