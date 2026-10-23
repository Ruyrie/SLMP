package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.query.FarmListQuery;
import com.zhimu.server.biz.farm.query.FarmListRow;
import com.zhimu.server.biz.farm.query.NameCheckQuery;
import com.zhimu.server.biz.farm.request.FarmVersionRequest;
import com.zhimu.server.biz.farm.request.UpdateFarmBasicRequest;
import com.zhimu.server.biz.farm.service.FarmService;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.support.FarmValidations;
import com.zhimu.server.biz.farm.vo.FarmBasicVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.biz.farm.vo.NameCheckVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API-F01–F04、F09–F12 与 F08 删除 */
@Service
@RequiredArgsConstructor
public class FarmServiceImpl implements FarmService {

    private static final Map<FarmStatus, Integer> STATUS_ORDER = Map.of(
            FarmStatus.NORMAL, 0,
            FarmStatus.DISABLED, 1,
            FarmStatus.PENDING, 2,
            FarmStatus.REJECTED, 3,
            FarmStatus.FROZEN, 4);

    private final FarmMapper farmMapper;
    private final FarmMemberMapper memberMapper;
    private final FarmAuditMapper auditMapper;
    private final FarmLookup lookup;
    private final FarmAssembler assembler;
    private final FarmNameGuard nameGuard;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;
    private final AccountQueryService accountQuery;
    private final BizMediaService bizMedia;

    @Override
    public PageVO<FarmListItemVO> listFarms(Long userId, Long currentFarmId, FarmListQuery query) {
        List<FarmListRow> rows = TenantSkip.call(() -> farmMapper.selectRowsOfUser(userId));
        List<ListEntry> entries = new ArrayList<>();
        for (FarmListRow row : rows) {
            Farm farm = farmFromRow(row);
            FarmStatus status = FarmStatuses.of(farm);
            FarmRole role = FarmRole.valueOf(row.getRole());
            entries.add(new ListEntry(farm, memberFromRow(row), status, role, row.getLastSwitchAt(),
                    row.getCreatedAt()));
        }
        int all = entries.size();
        int created = (int) entries.stream().filter(e -> e.role() == FarmRole.OWNER).count();
        int joined = all - created;
        int pendingAudit = (int) entries.stream()
                .filter(e -> e.role() == FarmRole.OWNER && e.status() == FarmStatus.PENDING).count();

        String relation = query.relationOrDefault();
        List<ListEntry> filtered = switch (relation) {
            case "CREATED" -> entries.stream().filter(e -> e.role() == FarmRole.OWNER).toList();
            case "JOINED" -> entries.stream().filter(e -> e.role() != FarmRole.OWNER).toList();
            default -> entries;
        };
        filtered = new ArrayList<>(filtered);
        filtered.sort(listComparator(currentFarmId));

        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<ListEntry> pageEntries = filtered.subList(from, to);

        List<Long> stockFarmIds = pageEntries.stream()
                .filter(e -> e.role() != FarmRole.STAFF && FarmStatuses.readable(e.status()))
                .map(e -> e.farm().getId()).toList();
        Map<Long, Integer> stocks = stockFarmIds.isEmpty() ? Map.of()
                : assembler.penCurrentStock(stockFarmIds);

        List<FarmListItemVO> list = new ArrayList<>();
        for (ListEntry entry : pageEntries) {
            Integer stock = entry.role() == FarmRole.STAFF ? null
                    : stocks.getOrDefault(entry.farm().getId(), 0);
            int penCount = entry.role() == FarmRole.STAFF
                    ? lookup.memberPens(entry.farm().getId(), entry.member().getId()).size() : 0;
            list.add(assembler.listItem(entry.farm(), entry.member(), entry.status(), entry.role(),
                    Objects.equals(currentFarmId, entry.farm().getId()), stock, penCount));
        }
        Map<String, Integer> tabCounts = new LinkedHashMap<>();
        tabCounts.put("all", all);
        tabCounts.put("created", created);
        tabCounts.put("joined", joined);
        tabCounts.put("pending_audit", pendingAudit);
        return new PageVO<>(list, filtered.size(), page, size).withTabCounts(tabCounts);
    }

    @Override
    public FarmContextVO getContext(Long farmId, Long userId) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = lookup.findActiveMember(farmId, userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        return assembler.context(farm, member);
    }

    @Override
    @Transactional
    public FarmContextVO switchFarm(Long farmId, Long userId) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = lookup.findActiveMember(farmId, userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status != FarmStatus.NORMAL && status != FarmStatus.DISABLED) {
            throw FarmErrors.statusForbidden(farm.getName() + "还在审核中，审核通过后才能切换");
        }
        LocalDateTime now = BeijingTime.now();
        TenantSkip.run(() -> memberMapper.update(new FarmMember(), Wrappers.<FarmMember>lambdaUpdate()
                .eq(FarmMember::getFarmId, farmId)
                .eq(FarmMember::getId, member.getId())
                .set(FarmMember::getLastSwitchAt, now)));
        member.setLastSwitchAt(now);
        accountQuery.recordLastFarm(userId, farmId);
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("UPDATE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .extraText("切换当前养殖场")
                .build());
        return assembler.context(farm, member);
    }

    @Override
    public NameCheckVO checkName(Long userId, NameCheckQuery query) {
        String name = FarmValidations.trim(query.getName());
        if (name.isEmpty()) {
            throw FarmValidations.invalid("name", FarmValidations.MSG_NAME_EMPTY);
        }
        if (FarmValidations.length(name) > FarmValidations.NAME_MAX) {
            throw FarmValidations.invalid("name", FarmValidations.MSG_NAME_TOO_LONG);
        }
        boolean exists = nameGuard.exists(userId, name, query.getExcludeFarmId());
        NameCheckVO vo = new NameCheckVO();
        vo.setAvailable(!exists);
        vo.setMessage(exists ? FarmValidations.MSG_NAME_DUP.formatted(name) : null);
        return vo;
    }

    @Override
    public FarmDetailVO detail(Long farmId, Long userId) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = requireMember(farm, userId);
        FarmRole role = FarmRole.valueOf(member.getRole());
        if (role != FarmRole.OWNER && role != FarmRole.MANAGER) {
            throw FarmErrors.memberViewForbidden(farm.getName());
        }
        requireReadable(farm);
        return assembler.detail(farm, member);
    }

    @Override
    @Transactional
    public FarmBasicVO updateBasic(Long farmId, Long userId, UpdateFarmBasicRequest request) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = requireMember(farm, userId);
        requireManagerView(farm, member);
        requireNormal(farm);
        FarmValidations.requireVersion(request.getVersion());
        if (!Objects.equals(request.getVersion(), farm.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        boolean any = request.getContactName() != null || request.getContactPhone() != null
                || request.getIntro() != null || request.getScale() != null;
        if (!any) {
            throw FarmValidations.invalid("请至少修改一项");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (request.getContactName() != null) {
            FarmValidations.validateContactName(request.getContactName());
            before.put("联系人", farm.getContactName());
            farm.setContactName(FarmValidations.trim(request.getContactName()));
            after.put("联系人", farm.getContactName());
        }
        if (request.getContactPhone() != null) {
            before.put("联系电话", FarmValidations.maskedPhone(farm.getContactPhone()));
            farm.setContactPhone(FarmValidations.normalizePhone(request.getContactPhone()));
            after.put("联系电话", FarmValidations.maskedPhone(farm.getContactPhone()));
        }
        if (request.getScale() != null) {
            FarmValidations.validateScale(request.getScale());
            before.put("规模", farm.getScale());
            farm.setScale(request.getScale());
            after.put("规模", farm.getScale());
        }
        if (request.getIntro() != null) {
            FarmValidations.validateIntro(request.getIntro());
            before.put("简介", farm.getIntro());
            farm.setIntro(FarmValidations.trim(request.getIntro()));
            after.put("简介", farm.getIntro());
        }
        farm.setUpdatedBy(userId);
        int rows = farmMapper.updateById(farm);
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("UPDATE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .before(before)
                .after(after)
                .build());
        return assembler.basic(farm);
    }

    @Override
    @Transactional
    public FarmDetailVO disable(Long farmId, Long userId, FarmVersionRequest request) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = requireMember(farm, userId);
        requireOwner(farm, member);
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.DISABLED) {
            throw FarmErrors.stateChanged(farm.getName() + "已停用");
        }
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status != FarmStatus.NORMAL) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        FarmValidations.requireVersion(request.getVersion());
        if (!Objects.equals(request.getVersion(), farm.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        farm.setDisabled(true);
        farm.setDisabledAt(BeijingTime.now());
        farm.setDisabledBy(userId);
        farm.setUpdatedBy(userId);
        if (farmMapper.updateById(farm) == 0) {
            throw FarmErrors.versionConflict();
        }
        List<Long> receivers = otherMemberIds(farmId, userId);
        cache.evictFarmMembers(farmId);
        notify.farmDisabled(farm, receivers);
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("DISABLE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .before(Map.of("status", "NORMAL"))
                .after(Map.of("status", "DISABLED"))
                .build());
        return assembler.detail(farm, member);
    }

    @Override
    @Transactional
    public FarmDetailVO enable(Long farmId, Long userId, FarmVersionRequest request) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = requireMember(farm, userId);
        requireOwner(farm, member);
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status != FarmStatus.DISABLED) {
            throw FarmErrors.stateChanged(farm.getName() + "没有停用");
        }
        FarmValidations.requireVersion(request.getVersion());
        if (!Objects.equals(request.getVersion(), farm.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        farm.setDisabled(false);
        farm.setDisabledAt(null);
        farm.setDisabledBy(null);
        farm.setUpdatedBy(userId);
        int rows = farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farmId)
                .eq(Farm::getVersion, farm.getVersion())
                .set(Farm::getDisabled, false)
                .set(Farm::getDisabledAt, null)
                .set(Farm::getDisabledBy, null)
                .set(Farm::getUpdatedBy, userId)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        farm.setVersion(farm.getVersion() + 1);
        List<Long> receivers = otherMemberIds(farmId, userId);
        cache.evictFarmMembers(farmId);
        notify.farmEnabled(farm, receivers);
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("ENABLE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .before(Map.of("status", "DISABLED"))
                .after(Map.of("status", "NORMAL"))
                .build());
        return assembler.detail(farm, member);
    }

    @Override
    @Transactional
    public String deleteFarm(Long farmId, Long userId) {
        Farm farm = requireNotDeleted(farmId);
        FarmMember member = lookup.findActiveMember(farmId, userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        requireOwner(farm, member);
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status != FarmStatus.PENDING && status != FarmStatus.REJECTED) {
            throw FarmErrors.statusForbidden("已通过审核的养殖场不能删除，可以停用");
        }
        List<Long> memberUserIds = lookup.activeMembers(farmId).stream().map(FarmMember::getUserId).toList();
        farm.setDeleteType("OWNER_DELETE");
        farm.setDeletedAt(BeijingTime.now());
        farm.setUpdatedBy(userId);
        if (farmMapper.updateById(farm) == 0) {
            throw FarmErrors.versionConflict();
        }
        if (farmMapper.deleteById(farmId) == 0) {
            throw FarmErrors.stateChanged("养殖场已被删除");
        }
        TenantSkip.run(() -> auditMapper.update(new FarmAudit(), Wrappers.<FarmAudit>lambdaUpdate()
                .eq(FarmAudit::getFarmId, farmId)
                .eq(FarmAudit::getStatus, "PENDING")
                .set(FarmAudit::getStatus, "CANCELED")
                .set(FarmAudit::getPendingFlag, null)
                .set(FarmAudit::getCancelReason, "所有者删除养殖场")));
        bizMedia.removeAll(BizType.FARM, farmId);
        cache.evictUsers(farmId, memberUserIds);
        notify.farmDeleted(farm, memberUserIds.stream().filter(id -> !id.equals(userId)).toList());
        logs.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .opType("RECORD_EDIT")
                .opAction("DELETE")
                .bizType(BizType.FARM.name())
                .bizId(farmId)
                .farmId(farmId)
                .targetName(farm.getName())
                .searchText(farm.getName())
                .reason("所有者删除未通过审核的养殖场")
                .build());
        return farm.getName();
    }

    // ===== 共用 =====

    private Farm requireNotDeleted(Long farmId) {
        Farm farm = lookup.findFarmIncludingDeleted(farmId);
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        return farm;
    }

    private FarmMember requireMember(Farm farm, Long userId) {
        FarmMember member = lookup.findActiveMember(farm.getId(), userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        return member;
    }

    private void requireOwner(Farm farm, FarmMember member) {
        if (!"OWNER".equals(member.getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
    }

    private void requireManagerView(Farm farm, FarmMember member) {
        if (!"OWNER".equals(member.getRole()) && !"MANAGER".equals(member.getRole())) {
            throw FarmErrors.memberViewForbidden(farm.getName());
        }
    }

    private void requireReadable(Farm farm) {
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (!FarmStatuses.readable(status)) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
    }

    private void requireNormal(Farm farm) {
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status != FarmStatus.NORMAL) {
            throw FarmErrors.statusForbidden(status == FarmStatus.DISABLED
                    ? FarmStatuses.MSG_DISABLED_RESTRICT.formatted(farm.getName())
                    : FarmStatuses.MSG_PENDING_RESTRICT);
        }
    }

    private List<Long> otherMemberIds(Long farmId, Long ownerUserId) {
        return lookup.activeMembers(farmId).stream()
                .map(FarmMember::getUserId)
                .filter(id -> !id.equals(ownerUserId))
                .toList();
    }

    private Comparator<ListEntry> listComparator(Long currentFarmId) {
        return (a, b) -> {
            boolean ac = Objects.equals(currentFarmId, a.farm().getId());
            boolean bc = Objects.equals(currentFarmId, b.farm().getId());
            if (ac != bc) {
                return ac ? -1 : 1;
            }
            int order = Integer.compare(STATUS_ORDER.get(a.status()), STATUS_ORDER.get(b.status()));
            if (order != 0) {
                return order;
            }
            if (a.status() == FarmStatus.NORMAL) {
                LocalDateTime at = a.lastSwitchAt();
                LocalDateTime bt = b.lastSwitchAt();
                if (at == null && bt == null) {
                    return compareCreatedDesc(a, b);
                }
                if (at == null) {
                    return 1;
                }
                if (bt == null) {
                    return -1;
                }
                int result = bt.compareTo(at);
                return result != 0 ? result : compareCreatedDesc(a, b);
            }
            return compareCreatedDesc(a, b);
        };
    }

    private static int compareCreatedDesc(ListEntry a, ListEntry b) {
        LocalDateTime ac = a.createdAt();
        LocalDateTime bc = b.createdAt();
        if (ac == null && bc == null) {
            return 0;
        }
        if (ac == null) {
            return 1;
        }
        if (bc == null) {
            return -1;
        }
        return bc.compareTo(ac);
    }

    private static Farm farmFromRow(FarmListRow row) {
        Farm farm = new Farm();
        farm.setId(row.getFarmId());
        farm.setName(row.getName());
        farm.setOwnerUserId(row.getOwnerUserId());
        farm.setMainCategoryId(row.getMainCategoryId());
        farm.setScale(row.getScale());
        farm.setContactName(row.getContactName());
        farm.setContactPhone(row.getContactPhone());
        farm.setIntro(row.getIntro());
        farm.setAddress(row.getAddress());
        farm.setProvince(row.getProvince());
        farm.setCity(row.getCity());
        farm.setDistrict(row.getDistrict());
        farm.setTownship(row.getTownship());
        farm.setAdcode(row.getAdcode());
        farm.setLongitude(row.getLongitude());
        farm.setLatitude(row.getLatitude());
        farm.setAuditStatus(row.getAuditStatus());
        farm.setApprovedAt(row.getApprovedAt());
        farm.setDisabled(row.getDisabled());
        farm.setDisabledAt(row.getDisabledAt());
        farm.setFrozen(row.getFrozen());
        farm.setFrozenAt(row.getFrozenAt());
        farm.setVersion(row.getVersion());
        farm.setCreatedAt(row.getCreatedAt());
        return farm;
    }

    private static FarmMember memberFromRow(FarmListRow row) {
        FarmMember member = new FarmMember();
        member.setId(row.getMemberId());
        member.setFarmId(row.getFarmId());
        member.setUserId(row.getUserId());
        member.setRole(row.getRole());
        member.setJoinType(row.getJoinType());
        member.setLastSwitchAt(row.getLastSwitchAt());
        return member;
    }

    private record ListEntry(Farm farm, FarmMember member, FarmStatus status, FarmRole role,
                             LocalDateTime lastSwitchAt, LocalDateTime createdAt) {
    }
}
