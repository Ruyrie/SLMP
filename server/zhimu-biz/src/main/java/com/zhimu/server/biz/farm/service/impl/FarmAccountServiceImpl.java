package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.api.AccountFarmStats;
import com.zhimu.server.biz.farm.api.CancelExitFarm;
import com.zhimu.server.biz.farm.api.CancelFarmDisposal;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.entity.MemberPen;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.farm.mapper.MemberPenMapper;
import com.zhimu.server.biz.farm.query.FarmListRow;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 模块02 提供给模块01 的账号相关能力（FarmAccountService） */
@Service
@RequiredArgsConstructor
public class FarmAccountServiceImpl implements FarmAccountService {

    private static final Map<FarmStatus, Integer> STATUS_ORDER = Map.of(
            FarmStatus.NORMAL, 0,
            FarmStatus.DISABLED, 1,
            FarmStatus.PENDING, 2,
            FarmStatus.REJECTED, 3,
            FarmStatus.FROZEN, 4);

    private final FarmMapper farmMapper;
    private final FarmMemberMapper memberMapper;
    private final FarmAuditMapper auditMapper;
    private final FarmChangeMapper changeMapper;
    private final FarmInviteMapper inviteMapper;
    private final FarmTransferMapper transferMapper;
    private final MemberPenMapper memberPenMapper;
    private final FarmLookup lookup;
    private final AccountQueryService accountQuery;
    private final BaseDataQueryService baseData;
    private final PenQueryService penQuery;
    private final BizMediaService bizMedia;
    private final FarmCache cache;
    private final FarmNotify notify;
    private final FarmLogs logs;

    @Override
    public Long resolveDefaultFarm(Long userId, Long lastFarmId) {
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        if (rows.isEmpty()) {
            return null;
        }
        if (lastFarmId != null) {
            for (FarmListRow row : rows) {
                if (Objects.equals(row.getFarmId(), lastFarmId) && !isFrozen(row)) {
                    return lastFarmId;
                }
            }
        }
        List<FarmListRow> candidates = new ArrayList<>(rows.stream().filter(row -> !isFrozen(row)).toList());
        candidates.sort((a, b) -> {
            FarmStatus as = statusOf(a);
            FarmStatus bs = statusOf(b);
            int order = Integer.compare(STATUS_ORDER.get(as), STATUS_ORDER.get(bs));
            if (order != 0) {
                return order;
            }
            if (as == FarmStatus.NORMAL) {
                int bySwitch = compareDesc(a.getLastSwitchAt(), b.getLastSwitchAt());
                if (bySwitch != 0) {
                    return bySwitch;
                }
            }
            return compareDesc(a.getCreatedAt(), b.getCreatedAt());
        });
        return candidates.isEmpty() ? null : candidates.get(0).getFarmId();
    }

    @Override
    public FarmBriefVO findMyFarmBrief(Long userId, Long farmId) {
        if (farmId == null) {
            return null;
        }
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        for (FarmListRow row : rows) {
            if (Objects.equals(row.getFarmId(), farmId) && !isFrozen(row)) {
                return brief(row);
            }
        }
        return null;
    }

    @Override
    public AccountFarmStats farmStats(Long userId) {
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        int pending = 0;
        int rejected = 0;
        for (FarmListRow row : rows) {
            if (!"OWNER".equals(row.getRole())) {
                continue;
            }
            FarmStatus status = statusOf(row);
            if (status == FarmStatus.PENDING) {
                pending++;
            } else if (status == FarmStatus.REJECTED) {
                rejected++;
            }
        }
        return new AccountFarmStats(rows.size(), pending, rejected);
    }

    @Override
    public List<CancelOwnedFarm> listOwnedFarmsForCancel(Long userId) {
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        List<CancelOwnedFarm> result = new ArrayList<>();
        for (FarmListRow row : rows) {
            if (!"OWNER".equals(row.getRole())) {
                continue;
            }
            Farm farm = farm(row);
            FarmStatus status = FarmStatuses.of(farm);
            CancelFarmDisposal disposal = switch (status) {
                case FROZEN -> CancelFarmDisposal.WAIT_UNFREEZE;
                case NORMAL -> CancelFarmDisposal.TRANSFER_OR_DISABLE;
                case DISABLED -> CancelFarmDisposal.RETAIN_180_DAYS;
                case PENDING, REJECTED -> CancelFarmDisposal.DELETE_NOW;
            };
            List<FarmMember> members = lookup.activeMembers(row.getFarmId());
            int memberCount = (int) members.stream()
                    .filter(member -> !Objects.equals(member.getUserId(), userId)).count();
            boolean hasManager = members.stream().anyMatch(member -> "MANAGER".equals(member.getRole()));
            FarmTransfer pending = pendingTransfer(row.getFarmId());
            String pendingName = pending == null ? null
                    : nameOf(accountQuery.getBrief(pending.getToUserId()));
            boolean transferable = status == FarmStatus.NORMAL && hasManager && pending == null;
            Integer stock = null;
            String countUnitText = null;
            CategoryInfo category = row.getMainCategoryId() == null ? null
                    : baseData.getCategory(row.getMainCategoryId());
            if (category != null) {
                countUnitText = category.countUnitText();
            }
            if (status == FarmStatus.NORMAL || status == FarmStatus.DISABLED) {
                Map<Long, Integer> stocks = penQuery.farmCurrentStock(List.of(row.getFarmId()));
                stock = stocks == null ? 0 : stocks.getOrDefault(row.getFarmId(), 0);
            }
            result.add(new CancelOwnedFarm(row.getFarmId(), row.getName(), status.name(), status.getText(),
                    stock, countUnitText, memberCount, disposal, transferable, pendingName,
                    row.getDisabledAt()));
        }
        return result;
    }

    @Override
    public List<CancelExitFarm> listExitFarmsForCancel(Long userId) {
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        List<CancelExitFarm> result = new ArrayList<>();
        for (FarmListRow row : rows) {
            if ("OWNER".equals(row.getRole())) {
                continue;
            }
            result.add(new CancelExitFarm(row.getFarmId(), row.getName(), row.getRole(),
                    "MANAGER".equals(row.getRole()) ? "场长" : "员工"));
        }
        return result;
    }

    @Override
    @Transactional
    public void handleAccountCancelled(Long userId, String phone) {
        LocalDateTime now = BeijingTime.now();
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        for (FarmListRow row : rows) {
            Farm farm = farm(row);
            if ("OWNER".equals(row.getRole())) {
                cancelPendingOfFarm(farm.getId(), now);
                farm.setDeleteType("ACCOUNT_CANCEL");
                farm.setDeletedAt(now);
                farm.setUpdatedBy(userId);
                TenantSkip.run(() -> farmMapper.updateById(farm));
                TenantSkip.run(() -> farmMapper.deleteById(farm.getId()));
                bizMedia.removeAll(BizType.FARM, farm.getId());
                List<Long> others = lookup.activeMembers(farm.getId()).stream()
                        .map(FarmMember::getUserId)
                        .filter(id -> !Objects.equals(id, userId)).toList();
                cache.evictUsers(farm.getId(), others);
                notify.farmDeleted(farm, others);
            } else {
                FarmMember member = lookup.findActiveMember(farm.getId(), userId);
                if (member == null) {
                    continue;
                }
                TenantSkip.run(() -> memberPenMapper.delete(Wrappers.<MemberPen>lambdaQuery()
                        .eq(MemberPen::getFarmId, farm.getId())
                        .eq(MemberPen::getMemberId, member.getId())));
                TenantSkip.run(() -> memberMapper.update(new FarmMember(), Wrappers.<FarmMember>lambdaUpdate()
                        .eq(FarmMember::getFarmId, farm.getId())
                        .eq(FarmMember::getId, member.getId())
                        .set(FarmMember::getStatus, "ACCOUNT_CANCELED")
                        .set(FarmMember::getActiveFlag, null)
                        .set(FarmMember::getLeftAt, now)
                        .set(FarmMember::getUpdatedBy, userId)
                        .setSql("version = version + 1")));
                TenantSkip.run(() -> transferMapper.update(new FarmTransfer(),
                        Wrappers.<FarmTransfer>lambdaUpdate()
                                .eq(FarmTransfer::getFarmId, farm.getId())
                                .eq(FarmTransfer::getToUserId, userId)
                                .eq(FarmTransfer::getStatus, "PENDING")
                                .set(FarmTransfer::getStatus, "CANCELED")
                                .set(FarmTransfer::getPendingFlag, null)
                                .set(FarmTransfer::getHandledAt, now)
                                .set(FarmTransfer::getCancelReason, "账号已注销")));
                cache.evict(farm.getId(), userId);
            }
        }
        if (phone != null) {
            List<FarmInvite> invites = TenantSkip.call(() -> inviteMapper.selectList(
                    Wrappers.<FarmInvite>lambdaQuery()
                            .eq(FarmInvite::getInviteePhone, phone)
                            .eq(FarmInvite::getStatus, "PENDING")));
            for (FarmInvite invite : invites) {
                TenantSkip.run(() -> inviteMapper.update(new FarmInvite(),
                        Wrappers.<FarmInvite>lambdaUpdate()
                                .eq(FarmInvite::getId, invite.getId())
                                .eq(FarmInvite::getStatus, "PENDING")
                                .set(FarmInvite::getStatus, "CANCELED")
                                .set(FarmInvite::getPendingFlag, null)
                                .set(FarmInvite::getHandledAt, now)
                                .set(FarmInvite::getCancelReason, "对方账号已注销")));
            }
        }
        logs.record(com.zhimu.server.framework.oplog.OperationLogEntry.builder()
                .operatorType("SYSTEM")
                .opType("FARM")
                .opAction("DELETE")
                .bizType(BizType.FARM.name())
                .extraText("账号注销联动：删除本人创建的养殖场并退出其他养殖场")
                .build());
    }

    // ===== 共用 =====

    private void cancelPendingOfFarm(Long farmId, LocalDateTime now) {
        TenantSkip.run(() -> auditMapper.update(new FarmAudit(), Wrappers.<FarmAudit>lambdaUpdate()
                .eq(FarmAudit::getFarmId, farmId)
                .eq(FarmAudit::getStatus, "PENDING")
                .set(FarmAudit::getStatus, "CANCELED")
                .set(FarmAudit::getPendingFlag, null)
                .set(FarmAudit::getCancelReason, "所有者注销账号")));
        TenantSkip.run(() -> changeMapper.update(new FarmChange(), Wrappers.<FarmChange>lambdaUpdate()
                .eq(FarmChange::getFarmId, farmId)
                .eq(FarmChange::getStatus, "PENDING")
                .set(FarmChange::getStatus, "CANCELED")
                .set(FarmChange::getPendingFlag, null)
                .set(FarmChange::getCancelReason, "养殖场已删除")));
        TenantSkip.run(() -> inviteMapper.update(new FarmInvite(), Wrappers.<FarmInvite>lambdaUpdate()
                .eq(FarmInvite::getFarmId, farmId)
                .eq(FarmInvite::getStatus, "PENDING")
                .set(FarmInvite::getStatus, "CANCELED")
                .set(FarmInvite::getPendingFlag, null)
                .set(FarmInvite::getHandledAt, now)
                .set(FarmInvite::getCancelReason, "养殖场已删除")));
        TenantSkip.run(() -> transferMapper.update(new FarmTransfer(), Wrappers.<FarmTransfer>lambdaUpdate()
                .eq(FarmTransfer::getFarmId, farmId)
                .eq(FarmTransfer::getStatus, "PENDING")
                .set(FarmTransfer::getStatus, "CANCELED")
                .set(FarmTransfer::getPendingFlag, null)
                .set(FarmTransfer::getHandledAt, now)
                .set(FarmTransfer::getCancelReason, "养殖场已删除")));
    }

    private FarmTransfer pendingTransfer(Long farmId) {
        return TenantSkip.call(() -> transferMapper.selectOne(Wrappers.<FarmTransfer>lambdaQuery()
                .eq(FarmTransfer::getFarmId, farmId)
                .eq(FarmTransfer::getStatus, "PENDING")
                .last("LIMIT 1")));
    }

    private FarmBriefVO brief(FarmListRow row) {
        FarmStatus status = statusOf(row);
        CategoryInfo category = row.getMainCategoryId() == null ? null
                : baseData.getCategory(row.getMainCategoryId());
        return FarmBriefVO.builder()
                .farmId(row.getFarmId())
                .name(row.getName())
                .speciesText(category == null ? null : category.speciesText())
                .status(status.name())
                .statusText(status.getText())
                .role(row.getRole())
                .build();
    }

    private static String nameOf(AccountBrief brief) {
        return brief == null ? null : brief.name();
    }

    private static boolean isFrozen(FarmListRow row) {
        return Boolean.TRUE.equals(row.getFrozen());
    }

    private static FarmStatus statusOf(FarmListRow row) {
        if (Boolean.TRUE.equals(row.getFrozen())) {
            return FarmStatus.FROZEN;
        }
        if (Boolean.TRUE.equals(row.getDisabled())) {
            return FarmStatus.DISABLED;
        }
        if ("PENDING".equals(row.getAuditStatus())) {
            return FarmStatus.PENDING;
        }
        if ("REJECTED".equals(row.getAuditStatus())) {
            return FarmStatus.REJECTED;
        }
        return FarmStatus.NORMAL;
    }

    private static Farm farm(FarmListRow row) {
        Farm farm = new Farm();
        farm.setId(row.getFarmId());
        farm.setName(row.getName());
        farm.setOwnerUserId(row.getOwnerUserId());
        farm.setAuditStatus(row.getAuditStatus());
        farm.setDisabled(row.getDisabled());
        farm.setFrozen(row.getFrozen());
        farm.setVersion(row.getVersion());
        farm.setCreatedAt(row.getCreatedAt());
        return farm;
    }

    private static int compareDesc(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }
}
