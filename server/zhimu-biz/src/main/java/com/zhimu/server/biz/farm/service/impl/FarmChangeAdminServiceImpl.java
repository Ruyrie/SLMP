package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.api.FarmChangeAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmNotify;
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

/** 管理端资料变更审核（ADM-R08/R09）：通过时回写 t_farm 与 FARM 资质图，驳回时只改申请行。 */
@Service
@RequiredArgsConstructor
public class FarmChangeAdminServiceImpl implements FarmChangeAdminService {

    private final FarmChangeMapper changeMapper;
    private final FarmMapper farmMapper;
    private final BizMediaService bizMedia;
    private final FarmCache cache;
    private final FarmLogs logs;
    private final FarmNotify notify;

    @Override
    @Transactional
    public void approve(Long changeId, Long adminId, String opinion, int version) {
        Context context = require(changeId, version);
        FarmChange change = context.change();
        Farm farm = context.farm();
        LocalDateTime now = BeijingTime.now();
        int changeRows = TenantSkip.call(() -> changeMapper.update(new FarmChange(),
                Wrappers.<FarmChange>lambdaUpdate()
                        .eq(FarmChange::getId, change.getId())
                        .eq(FarmChange::getStatus, "PENDING")
                        .eq(FarmChange::getVersion, change.getVersion())
                        .set(FarmChange::getStatus, "APPROVED")
                        .set(FarmChange::getPendingFlag, null)
                        .set(FarmChange::getAuditedBy, adminId)
                        .set(FarmChange::getAuditedAt, now)
                        .set(FarmChange::getAuditOpinion, opinion)
                        .set(FarmChange::getRejectReason, null)
                        .setSql("version = version + 1")));
        if (changeRows == 0) {
            throw FarmErrors.versionConflict();
        }
        int farmRows = TenantSkip.call(() -> farmMapper.update(new Farm(), Wrappers.<Farm>lambdaUpdate()
                .eq(Farm::getId, farm.getId())
                .eq(Farm::getVersion, farm.getVersion())
                .set(Farm::getName, change.getNewName())
                .set(Farm::getAddress, change.getNewAddress())
                .set(Farm::getProvince, change.getNewProvince())
                .set(Farm::getCity, change.getNewCity())
                .set(Farm::getDistrict, change.getNewDistrict())
                .set(Farm::getTownship, change.getNewTownship())
                .set(Farm::getAdcode, change.getNewAdcode())
                .set(Farm::getLongitude, change.getNewLongitude())
                .set(Farm::getLatitude, change.getNewLatitude())
                .set(Farm::getLocAccuracy, change.getNewLocAccuracy())
                .set(Farm::getUpdatedBy, adminId)
                .setSql("version = version + 1")));
        if (farmRows == 0) {
            throw FarmErrors.versionConflict();
        }
        List<Long> qualificationFileIds = bizMedia.listFileIds(BizType.FARM_CHANGE, change.getId());
        bizMedia.replace(BizType.FARM, farm.getId(), farm.getId(),
                qualificationFileIds == null ? List.of() : qualificationFileIds);
        change.setStatus("APPROVED");
        change.setAuditedBy(adminId);
        change.setAuditedAt(now);
        change.setAuditOpinion(opinion);
        change.setVersion(change.getVersion() + 1);
        farm.setName(change.getNewName());
        farm.setAddress(change.getNewAddress());
        farm.setProvince(change.getNewProvince());
        farm.setCity(change.getNewCity());
        farm.setDistrict(change.getNewDistrict());
        farm.setTownship(change.getNewTownship());
        farm.setAdcode(change.getNewAdcode());
        farm.setLongitude(change.getNewLongitude());
        farm.setLatitude(change.getNewLatitude());
        farm.setLocAccuracy(change.getNewLocAccuracy());
        farm.setVersion(farm.getVersion() + 1);
        cache.evictFarmMembers(farm.getId());
        notify.changeResult(farm, change.getId(), true, opinion);
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("FARM_CHANGE")
                .opAction("APPROVE")
                .bizType(BizType.FARM_CHANGE.name())
                .bizId(change.getId())
                .farmId(farm.getId())
                .targetName(farm.getName())
                .searchText(farm.getName())
                .before(Map.of("name", context.oldName()))
                .after(Map.of("name", change.getNewName()))
                .reason(opinion)
                .build());
    }

    @Override
    @Transactional
    public void reject(Long changeId, Long adminId, String reason, int version) {
        Context context = require(changeId, version);
        FarmChange change = context.change();
        Farm farm = context.farm();
        LocalDateTime now = BeijingTime.now();
        int rows = TenantSkip.call(() -> changeMapper.update(new FarmChange(),
                Wrappers.<FarmChange>lambdaUpdate()
                        .eq(FarmChange::getId, change.getId())
                        .eq(FarmChange::getStatus, "PENDING")
                        .eq(FarmChange::getVersion, change.getVersion())
                        .set(FarmChange::getStatus, "REJECTED")
                        .set(FarmChange::getPendingFlag, null)
                        .set(FarmChange::getAuditedBy, adminId)
                        .set(FarmChange::getAuditedAt, now)
                        .set(FarmChange::getRejectReason, reason)
                        .set(FarmChange::getAuditOpinion, null)
                        .setSql("version = version + 1")));
        if (rows == 0) {
            throw FarmErrors.versionConflict();
        }
        change.setStatus("REJECTED");
        change.setAuditedBy(adminId);
        change.setAuditedAt(now);
        change.setRejectReason(reason);
        change.setVersion(change.getVersion() + 1);
        notify.changeResult(farm, change.getId(), false, reason);
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("FARM_CHANGE")
                .opAction("REJECT")
                .bizType(BizType.FARM_CHANGE.name())
                .bizId(change.getId())
                .farmId(farm.getId())
                .targetName(farm.getName())
                .searchText(farm.getName())
                .reason(reason)
                .build());
    }

    @Override
    public long countPending() {
        Long count = TenantSkip.call(() -> changeMapper.selectCount(
                Wrappers.<FarmChange>lambdaQuery().eq(FarmChange::getStatus, "PENDING")));
        return count == null ? 0 : count;
    }

    private Context require(Long changeId, int version) {
        FarmChange change = TenantSkip.call(() -> changeMapper.selectById(changeId));
        if (change == null) {
            throw FarmErrors.notFound(null);
        }
        if (!Objects.equals(version, change.getVersion())) {
            throw FarmErrors.versionConflict();
        }
        if (!"PENDING".equals(change.getStatus())) {
            throw FarmErrors.stateChanged("该资料变更已处理，请刷新后重试");
        }
        Farm farm = TenantSkip.call(() -> farmMapper.selectIncludingDeleted(change.getFarmId()));
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        return new Context(change, farm, farm.getName());
    }

    private record Context(FarmChange change, Farm farm, String oldName) {
    }
}
