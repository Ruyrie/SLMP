package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.mapper.FarmMemberMapper;
import com.zhimu.server.biz.farm.request.CreateFarmRequest;
import com.zhimu.server.biz.farm.request.UpdateRegistrationRequest;
import com.zhimu.server.biz.farm.service.FarmAuditService;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNameLock;
import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.support.FarmValidations;
import com.zhimu.server.biz.farm.support.JsonLists;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** API-F05 新建提交、F06 审核结果、F07 修改注册信息 / 重新提交 */
@Service
@RequiredArgsConstructor
public class FarmAuditServiceImpl implements FarmAuditService {

    private final FarmMapper farmMapper;
    private final FarmAuditMapper auditMapper;
    private final FarmMemberMapper memberMapper;
    private final FarmLookup lookup;
    private final FarmAssembler assembler;
    private final FarmNameGuard nameGuard;
    private final FarmNameLock nameLock;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final BizMediaService bizMedia;
    private final BaseDataQueryService baseData;

    @Override
    @Transactional
    public FarmAuditVO create(Long userId, CreateFarmRequest request) {
        FarmValidations.NormalizedRegistration reg =
                FarmValidations.normalizeRegistration(request, baseData);
        return nameLock.call(userId, () -> {
            nameGuard.assertUnique(userId, reg.name(), null);
            bizMedia.validateForBind(reg.qualificationFileIds(), FileScene.FARM_QUAL, userId,
                    "qualification_file_ids");
            LocalDateTime now = BeijingTime.now();
            Farm farm = new Farm();
            applyRegistration(farm, reg);
            farm.setOwnerUserId(userId);
            farm.setCoordType("GCJ02");
            farm.setAuditStatus("PENDING");
            farm.setDisabled(false);
            farm.setFrozen(false);
            farm.setVersion(0);
            farm.setDeleted(false);
            farm.setCreatedBy(userId);
            farm.setUpdatedBy(userId);
            farmMapper.insert(farm);

            FarmMember member = new FarmMember();
            member.setFarmId(farm.getId());
            member.setUserId(userId);
            member.setRole(FarmRole.OWNER.name());
            member.setStatus("ACTIVE");
            member.setActiveFlag(true);
            member.setJoinType("CREATE");
            member.setJoinedAt(now);
            member.setLastSwitchAt(now);
            member.setVersion(0);
            member.setCreatedBy(userId);
            member.setUpdatedBy(userId);
            memberMapper.insert(member);

            FarmAudit audit = new FarmAudit();
            audit.setFarmId(farm.getId());
            audit.setSubmitNo(1);
            audit.setStatus("PENDING");
            audit.setPendingFlag(true);
            audit.setSubmittedAt(now);
            audit.setSnapshotJson(snapshotJson(reg));
            audit.setRequestId(RequestContext.idempotencyKey());
            audit.setVersion(0);
            audit.setCreatedBy(userId);
            auditMapper.insert(audit);

            farm.setLatestAuditId(audit.getId());
            TenantSkip.run(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                    .eq(Farm::getId, farm.getId())
                    .set(Farm::getLatestAuditId, audit.getId())));
            bizMedia.replace(BizType.FARM, farm.getId(), farm.getId(), reg.qualificationFileIds());

            logs.record(OperationLogEntry.builder()
                    .operatorType("USER")
                    .operatorId(userId)
                    .opType("RECORD_EDIT")
                    .opAction("CREATE")
                    .bizType(BizType.FARM_AUDIT.name())
                    .bizId(audit.getId())
                    .farmId(farm.getId())
                    .targetName(farm.getName())
                    .searchText(farm.getName())
                    .after(reg.name())
                    .build());
            return assembler.audit(farm, audit, List.of(audit));
        });
    }

    @Override
    public FarmAuditVO getAudit(Long farmId, Long userId) {
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
        if (!FarmRole.OWNER.name().equals(member.getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
        FarmStatus status = FarmStatuses.of(farm);
        if (FarmStatuses.approved(status)) {
            FarmAuditVO vo = new FarmAuditVO();
            vo.setFarmId(farm.getId());
            vo.setName(farm.getName());
            vo.setStatus(status.name());
            vo.setStatusText(switch (status) {
                case DISABLED -> "已停用";
                case FROZEN -> "已冻结";
                default -> "已通过";
            });
            return vo;
        }
        FarmAudit latest = latestAudit(farmId);
        return assembler.audit(farm, latest, allAudits(farmId));
    }

    @Override
    @Transactional
    public FarmAuditVO resubmit(Long farmId, Long userId, UpdateRegistrationRequest request) {
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
        if (!FarmRole.OWNER.name().equals(member.getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status == FarmStatus.NORMAL) {
            throw FarmErrors.statusForbidden("名称、地址和资质图请通过资料变更修改");
        }
        if (status == FarmStatus.DISABLED) {
            throw FarmErrors.statusForbidden(
                    FarmStatuses.MSG_DISABLED_RESTRICT.formatted(farm.getName()));
        }
        if (status != FarmStatus.PENDING && status != FarmStatus.REJECTED) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        FarmValidations.NormalizedRegistration reg =
                FarmValidations.normalizeRegistration(request, baseData);
        FarmValidations.requireVersion(request.getAuditVersion());
        FarmAudit latest = latestAudit(farmId);
        if (latest == null) {
            throw FarmErrors.stateChanged("审核记录已更新，请刷新后再修改");
        }
        if (!Objects.equals(request.getAuditVersion(), latest.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        bizMedia.validateForBind(reg.qualificationFileIds(), FileScene.FARM_QUAL, userId,
                "qualification_file_ids", new BizRef(BizType.FARM, farmId));
        return nameLock.call(userId, () -> {
            nameGuard.assertUnique(userId, reg.name(), farmId);
            LocalDateTime now = BeijingTime.now();
            FarmSnapshot previous = assembler.snapshot(latest.getSnapshotJson(), farm);
            FarmSnapshot next = new FarmSnapshot(reg.name(), reg.category().id(), reg.scale(),
                    reg.contactName(), reg.contactPhone(), reg.address(), reg.province(), reg.city(),
                    reg.district(), reg.township(), reg.adcode(), reg.longitude(), reg.latitude(),
                    reg.locAccuracy(), reg.qualificationFileIds());
            List<Long> reshoot = JsonLists.readLongs(latest.getReshootFileIds());
            String summary = changeSummary(previous, next, reshoot);
            FarmAudit current;
            if ("PENDING".equals(farm.getAuditStatus())) {
                if (!"PENDING".equals(latest.getStatus())) {
                    throw FarmErrors.stateChanged("审核结果已更新，请刷新后再修改");
                }
                latest.setSnapshotJson(CacheJson.write(next));
                latest.setSubmittedAt(now);
                latest.setChangeSummary(summary);
                latest.setPendingFlag(true);
                if (auditMapper.updateById(latest) == 0) {
                    throw FarmErrors.stateChanged("审核结果已更新，请刷新后再修改");
                }
                current = latest;
            } else {
                FarmAudit audit = new FarmAudit();
                audit.setFarmId(farmId);
                audit.setSubmitNo(maxSubmitNo(farmId) + 1);
                audit.setStatus("PENDING");
                audit.setPendingFlag(true);
                audit.setSubmittedAt(now);
                audit.setSnapshotJson(CacheJson.write(next));
                audit.setChangeSummary(summary);
                audit.setRequestId(RequestContext.idempotencyKey());
                audit.setVersion(0);
                audit.setCreatedBy(userId);
                auditMapper.insert(audit);
                current = audit;
            }
            applyRegistration(farm, reg);
            farm.setAuditStatus("PENDING");
            farm.setLatestAuditId(current.getId());
            farm.setUpdatedBy(userId);
            int farmRows = farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                    .eq(Farm::getId, farmId)
                    .eq(Farm::getVersion, farm.getVersion())
                    .set(Farm::getName, reg.name())
                    .set(Farm::getMainCategoryId, reg.category().id())
                    .set(Farm::getScale, reg.scale())
                    .set(Farm::getContactName, reg.contactName())
                    .set(Farm::getContactPhone, reg.contactPhone())
                    .set(Farm::getAddress, reg.address())
                    .set(Farm::getProvince, reg.province())
                    .set(Farm::getCity, reg.city())
                    .set(Farm::getDistrict, reg.district())
                    .set(Farm::getTownship, reg.township())
                    .set(Farm::getAdcode, reg.adcode())
                    .set(Farm::getLongitude, reg.longitude())
                    .set(Farm::getLatitude, reg.latitude())
                    .set(Farm::getLocAccuracy, reg.locAccuracy())
                    .set(Farm::getAuditStatus, "PENDING")
                    .set(Farm::getLatestAuditId, current.getId())
                    .set(Farm::getUpdatedBy, userId)
                    .setSql("version = version + 1"));
            if (farmRows == 0) {
                throw FarmErrors.versionConflict();
            }
            farm.setVersion(farm.getVersion() + 1);
            bizMedia.replace(BizType.FARM, farmId, farmId, reg.qualificationFileIds());
            cache.evict(farmId, userId);
            logs.record(OperationLogEntry.builder()
                    .operatorType("USER")
                    .operatorId(userId)
                    .opType("RECORD_EDIT")
                    .opAction("UPDATE")
                    .bizType(BizType.FARM_AUDIT.name())
                    .bizId(current.getId())
                    .farmId(farmId)
                    .targetName(farm.getName())
                    .searchText(farm.getName())
                    .before(previous.name())
                    .after(next.name())
                    .extraText(summary)
                    .build());
            return assembler.audit(farm, current, allAudits(farmId));
        });
    }

    // ===== 共用 =====

    private FarmAudit latestAudit(Long farmId) {
        return TenantSkip.call(() -> auditMapper.selectOne(Wrappers.<FarmAudit>lambdaQuery()
                .eq(FarmAudit::getFarmId, farmId)
                .orderByDesc(FarmAudit::getSubmitNo)
                .orderByDesc(FarmAudit::getId)
                .last("LIMIT 1")));
    }

    private List<FarmAudit> allAudits(Long farmId) {
        return TenantSkip.call(() -> auditMapper.selectList(Wrappers.<FarmAudit>lambdaQuery()
                .eq(FarmAudit::getFarmId, farmId)
                .orderByDesc(FarmAudit::getSubmitNo)
                .orderByDesc(FarmAudit::getId)));
    }

    private int maxSubmitNo(Long farmId) {
        FarmAudit latest = latestAudit(farmId);
        return latest == null || latest.getSubmitNo() == null ? 0 : latest.getSubmitNo();
    }

    private static void applyRegistration(Farm farm, FarmValidations.NormalizedRegistration reg) {
        farm.setName(reg.name());
        farm.setMainCategoryId(reg.category().id());
        farm.setScale(reg.scale());
        farm.setContactName(reg.contactName());
        farm.setContactPhone(reg.contactPhone());
        farm.setAddress(reg.address());
        farm.setProvince(reg.province());
        farm.setCity(reg.city());
        farm.setDistrict(reg.district());
        farm.setTownship(reg.township());
        farm.setAdcode(reg.adcode());
        farm.setLongitude(reg.longitude());
        farm.setLatitude(reg.latitude());
        farm.setLocAccuracy(reg.locAccuracy());
    }

    private static String snapshotJson(FarmValidations.NormalizedRegistration reg) {
        return CacheJson.write(new FarmSnapshot(reg.name(), reg.category().id(), reg.scale(),
                reg.contactName(), reg.contactPhone(), reg.address(), reg.province(), reg.city(),
                reg.district(), reg.township(), reg.adcode(), reg.longitude(), reg.latitude(),
                reg.locAccuracy(), reg.qualificationFileIds()));
    }

    /** 与上一次提交的快照比较生成 change_summary */
    static String changeSummary(FarmSnapshot previous, FarmSnapshot next, List<Long> reshootIds) {
        if (previous == null) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(trim(previous.name()), trim(next.name()))) {
            fields.add("名称");
        }
        if (!Objects.equals(previous.mainCategoryId(), next.mainCategoryId())) {
            fields.add("主要品类");
        }
        if (!Objects.equals(previous.scale(), next.scale())) {
            fields.add("规模");
        }
        if (!Objects.equals(trim(previous.contactName()), trim(next.contactName()))) {
            fields.add("联系人");
        }
        if (!Objects.equals(previous.contactPhone(), next.contactPhone())) {
            fields.add("联系电话");
        }
        boolean addressChanged = !Objects.equals(trim(previous.address()), trim(next.address()))
                || !Objects.equals(previous.province(), next.province())
                || !Objects.equals(previous.city(), next.city())
                || !Objects.equals(previous.district(), next.district())
                || !Objects.equals(previous.township(), next.township())
                || !Objects.equals(previous.adcode(), next.adcode())
                || !sameDecimal(previous.longitude(), next.longitude())
                || !sameDecimal(previous.latitude(), next.latitude());
        if (addressChanged) {
            fields.add("地址");
        }
        Set<Long> before = new HashSet<>(previous.fileIds());
        Set<Long> after = new HashSet<>(next.fileIds());
        boolean qualChanged = !before.equals(after);
        Set<Long> reshoot = new HashSet<>(reshootIds == null ? List.of() : reshootIds);
        int replaced = 0;
        if (!reshoot.isEmpty()) {
            for (Long fileId : reshoot) {
                if (before.contains(fileId) && !after.contains(fileId)) {
                    replaced++;
                }
            }
        }
        if (replaced > 0 && fields.isEmpty()) {
            return "重新拍摄了被标为“需重拍”的 " + replaced + " 张资质图，其他注册信息未修改";
        }
        if (qualChanged) {
            fields.add("资质图");
        }
        if (fields.isEmpty()) {
            return "未修改";
        }
        return String.join("、", fields) + "已修改，其他注册信息未修改";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameDecimal(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
