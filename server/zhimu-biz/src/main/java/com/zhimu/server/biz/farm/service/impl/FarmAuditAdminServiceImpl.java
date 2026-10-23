package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.api.FarmAuditAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.JsonLists;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 管理端注册审核（ADM-R04/R05）：同事务改审核行与 t_farm，清成员缓存，写留痕，提交后发消息。 */
@Service
@RequiredArgsConstructor
public class FarmAuditAdminServiceImpl implements FarmAuditAdminService {

    private final FarmAuditMapper auditMapper;
    private final FarmMapper farmMapper;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;

    @Override
    @Transactional
    public void approve(Long auditId, Long adminId, String opinion, int version) {
        AuditContext context = require(auditId, version);
        FarmAudit audit = context.audit();
        Farm farm = context.farm();
        LocalDateTime now = BeijingTime.now();
        audit.setStatus("APPROVED");
        audit.setPendingFlag(null);
        audit.setAuditedBy(adminId);
        audit.setAuditedAt(now);
        audit.setAuditOpinion(opinion);
        audit.setRejectReason(null);
        int auditRows = TenantSkip.call(() -> auditMapper.update(new FarmAudit(),
                Wrappers.<FarmAudit>lambdaUpdate()
                        .eq(FarmAudit::getId, audit.getId())
                        .eq(FarmAudit::getStatus, "PENDING")
                        .eq(FarmAudit::getVersion, audit.getVersion())
                        .set(FarmAudit::getStatus, "APPROVED")
                        .set(FarmAudit::getPendingFlag, null)
                        .set(FarmAudit::getAuditedBy, adminId)
                        .set(FarmAudit::getAuditedAt, now)
                        .set(FarmAudit::getAuditOpinion, opinion)
                        .set(FarmAudit::getRejectReason, null)
                        .setSql("version = version + 1")));
        if (auditRows == 0) {
            throw FarmErrors.versionConflict();
        }
        audit.setVersion(audit.getVersion() + 1);
        farm.setAuditStatus("APPROVED");
        farm.setLatestAuditId(audit.getId());
        if (farm.getApprovedAt() == null) {
            farm.setApprovedAt(now);
        }
        farm.setUpdatedBy(adminId);
        int rows = TenantSkip.call(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farm.getId())
                .eq(Farm::getVersion, farm.getVersion())
                .set(Farm::getAuditStatus, "APPROVED")
                .set(Farm::getLatestAuditId, audit.getId())
                .set(Farm::getApprovedAt, farm.getApprovedAt())
                .set(Farm::getUpdatedBy, adminId)
                .setSql("version = version + 1")));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        farm.setVersion(farm.getVersion() + 1);
        cache.evictFarmMembers(farm.getId());
        notify.auditResult(farm, audit.getId(), true, opinion);
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("FARM_AUDIT")
                .opAction("APPROVE")
                .bizType(BizType.FARM_AUDIT.name())
                .bizId(audit.getId())
                .farmId(farm.getId())
                .targetName(farm.getName())
                .searchText(farm.getName())
                .before(Map.of("audit_status", "PENDING"))
                .after(Map.of("audit_status", "APPROVED"))
                .reason(opinion)
                .build());
    }

    @Override
    @Transactional
    public void reject(Long auditId, Long adminId, String reason, List<Long> reshootFileIds, int version) {
        AuditContext context = require(auditId, version);
        FarmAudit audit = context.audit();
        Farm farm = context.farm();
        LocalDateTime now = BeijingTime.now();
        audit.setStatus("REJECTED");
        audit.setPendingFlag(null);
        audit.setAuditedBy(adminId);
        audit.setAuditedAt(now);
        audit.setRejectReason(reason);
        audit.setAuditOpinion(null);
        audit.setReshootFileIds(JsonLists.writeLongs(reshootFileIds == null ? List.of() : reshootFileIds));
        int auditRows = TenantSkip.call(() -> auditMapper.update(new FarmAudit(),
                Wrappers.<FarmAudit>lambdaUpdate()
                        .eq(FarmAudit::getId, audit.getId())
                        .eq(FarmAudit::getStatus, "PENDING")
                        .eq(FarmAudit::getVersion, audit.getVersion())
                        .set(FarmAudit::getStatus, "REJECTED")
                        .set(FarmAudit::getPendingFlag, null)
                        .set(FarmAudit::getAuditedBy, adminId)
                        .set(FarmAudit::getAuditedAt, now)
                        .set(FarmAudit::getRejectReason, reason)
                        .set(FarmAudit::getAuditOpinion, null)
                        .set(FarmAudit::getReshootFileIds, audit.getReshootFileIds())
                        .setSql("version = version + 1")));
        if (auditRows == 0) {
            throw FarmErrors.versionConflict();
        }
        audit.setVersion(audit.getVersion() + 1);
        farm.setAuditStatus("REJECTED");
        farm.setLatestAuditId(audit.getId());
        farm.setUpdatedBy(adminId);
        int rows = TenantSkip.call(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farm.getId())
                .eq(Farm::getVersion, farm.getVersion())
                .set(Farm::getAuditStatus, "REJECTED")
                .set(Farm::getLatestAuditId, audit.getId())
                .set(Farm::getUpdatedBy, adminId)
                .setSql("version = version + 1")));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        farm.setVersion(farm.getVersion() + 1);
        cache.evictFarmMembers(farm.getId());
        notify.auditResult(farm, audit.getId(), false, reason);
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("FARM_AUDIT")
                .opAction("REJECT")
                .bizType(BizType.FARM_AUDIT.name())
                .bizId(audit.getId())
                .farmId(farm.getId())
                .targetName(farm.getName())
                .searchText(farm.getName())
                .reason(reason)
                .before(Map.of("audit_status", "PENDING"))
                .after(Map.of("audit_status", "REJECTED"))
                .build());
    }

    @Override
    public long countPending() {
        Long count = TenantSkip.call(() -> auditMapper.selectCount(
                Wrappers.<FarmAudit>lambdaQuery().eq(FarmAudit::getStatus, "PENDING")));
        return count == null ? 0 : count;
    }

    private AuditContext require(Long auditId, int version) {
        FarmAudit audit = TenantSkip.call(() -> auditMapper.selectById(auditId));
        if (audit == null) {
            throw FarmErrors.notFound(null);
        }
        if (!Objects.equals(version, audit.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        if (!"PENDING".equals(audit.getStatus())) {
            throw FarmErrors.stateChanged("该审核申请已处理，请刷新后重试");
        }
        Farm farm = TenantSkip.call(() -> farmMapper.selectIncludingDeleted(audit.getFarmId()));
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        return new AuditContext(audit, farm);
    }

    private record AuditContext(FarmAudit audit, Farm farm) {
    }
}
