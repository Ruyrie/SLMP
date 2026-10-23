package com.zhimu.server.biz.farm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.farm.convert.FarmAssembler;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.request.SubmitFarmChangeRequest;
import com.zhimu.server.biz.farm.service.FarmChangeService;
import com.zhimu.server.biz.farm.support.FarmErrors;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.farm.support.FarmNameGuard;
import com.zhimu.server.biz.farm.support.FarmNameLock;
import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.biz.farm.support.FarmStatuses;
import com.zhimu.server.biz.farm.support.FarmValidations;
import com.zhimu.server.biz.farm.vo.FarmChangeVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** API-F13 最近一次资料变更、F14 提交资料变更 */
@Service
@RequiredArgsConstructor
public class FarmChangeServiceImpl implements FarmChangeService {

    private final FarmChangeMapper changeMapper;
    private final FarmLookup lookup;
    private final FarmAssembler assembler;
    private final FarmNameGuard nameGuard;
    private final FarmNameLock nameLock;
    private final FarmLogs logs;
    private final BizMediaService bizMedia;

    @Override
    public FarmChangeVO latest(Long farmId, Long userId) {
        Farm farm = requireFarm(farmId);
        requireOwner(farm, userId);
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (!FarmStatuses.readable(status)) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        FarmChange change = latestVisible(farmId);
        return change == null ? null : assembler.change(farm, change);
    }

    @Override
    @Transactional
    public FarmChangeVO submit(Long farmId, Long userId, SubmitFarmChangeRequest request) {
        Farm farm = requireFarm(farmId);
        requireOwner(farm, userId);
        FarmStatus status = FarmStatuses.of(farm);
        if (status == FarmStatus.FROZEN) {
            throw FarmErrors.frozen(farm.getName());
        }
        if (status == FarmStatus.DISABLED) {
            throw FarmErrors.statusForbidden(
                    FarmStatuses.MSG_DISABLED_RESTRICT.formatted(farm.getName()));
        }
        if (status != FarmStatus.NORMAL) {
            throw FarmErrors.statusForbidden(FarmStatuses.MSG_PENDING_RESTRICT);
        }
        FarmChange pending = pendingChange(farmId);
        if (pending != null) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.PENDING_EXISTS,
                    "资料变更审核中，审核期间不能再次提交。");
        }
        FarmValidations.NormalizedChange change =
                FarmValidations.normalizeChange(request.getName(), request.getAddress(),
                        request.getProvince(), request.getCity(), request.getDistrict(),
                        request.getTownship(), request.getAdcode(), request.getLongitude(),
                        request.getLatitude(), request.getLocAccuracy(),
                        request.getQualificationFileIds(), request.getRemark());
        FarmSnapshot before = assembler.snapshotOf(farm);
        Set<Long> beforeIds = new HashSet<>(before.fileIds());
        Set<Long> afterIds = new HashSet<>(change.qualificationFileIds());
        boolean nameChanged = !trim(before.name()).equals(trim(change.name()));
        boolean addressChanged = !trim(before.address()).equals(trim(change.address()))
                || !Objects.equals(before.province(), change.province())
                || !Objects.equals(before.city(), change.city())
                || !Objects.equals(before.district(), change.district())
                || !Objects.equals(before.township(), change.township())
                || !Objects.equals(before.adcode(), change.adcode())
                || !sameDecimal(before.longitude(), change.longitude())
                || !sameDecimal(before.latitude(), change.latitude());
        boolean qualChanged = !beforeIds.equals(afterIds);
        if (!nameChanged && !addressChanged && !qualChanged) {
            throw FarmValidations.invalid(FarmValidations.MSG_NO_CHANGE);
        }
        bizMedia.validateForBind(change.qualificationFileIds(), FileScene.FARM_QUAL, userId,
                "qualification_file_ids", new BizRef(BizType.FARM, farmId));
        return nameLock.call(userId, () -> {
            if (nameChanged) {
                nameGuard.assertUnique(userId, change.name(), farmId);
            }
            FarmChange row = new FarmChange();
            row.setFarmId(farmId);
            row.setChangeNo(maxChangeNo(farmId) + 1);
            row.setStatus("PENDING");
            row.setPendingFlag(true);
            row.setNameChanged(nameChanged);
            row.setAddressChanged(addressChanged);
            row.setQualChanged(qualChanged);
            row.setNewName(change.name());
            row.setNewAddress(change.address());
            row.setNewProvince(change.province());
            row.setNewCity(change.city());
            row.setNewDistrict(change.district());
            row.setNewTownship(change.township());
            row.setNewAdcode(change.adcode());
            row.setNewLongitude(change.longitude());
            row.setNewLatitude(change.latitude());
            row.setNewLocAccuracy(change.locAccuracy());
            row.setRemark(change.remark());
            row.setBeforeJson(CacheJson.write(before));
            row.setRequestId(RequestContext.idempotencyKey());
            row.setVersion(0);
            row.setCreatedBy(userId);
            changeMapper.insert(row);
            bizMedia.replace(BizType.FARM_CHANGE, row.getId(), farmId, change.qualificationFileIds());
            logs.record(OperationLogEntry.builder()
                    .operatorType("USER")
                    .operatorId(userId)
                    .opType("RECORD_EDIT")
                    .opAction("CREATE")
                    .bizType(BizType.FARM_CHANGE.name())
                    .bizId(row.getId())
                    .farmId(farmId)
                    .targetName(farm.getName())
                    .searchText(farm.getName())
                    .after(String.join("、", changedFields(nameChanged, addressChanged, qualChanged)))
                    .build());
            return assembler.change(farm, row);
        });
    }

    private static String[] changedFields(boolean name, boolean address, boolean qual) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        if (name) {
            fields.add("名称");
        }
        if (address) {
            fields.add("地址");
        }
        if (qual) {
            fields.add("资质图");
        }
        return fields.toArray(new String[0]);
    }

    private Farm requireFarm(Long farmId) {
        Farm farm = lookup.findFarmIncludingDeleted(farmId);
        if (farm == null) {
            throw FarmErrors.notFound(null);
        }
        if (Boolean.TRUE.equals(farm.getDeleted())) {
            throw FarmErrors.deleted(farm.getName());
        }
        return farm;
    }

    private void requireOwner(Farm farm, Long userId) {
        FarmMember member = lookup.findActiveMember(farm.getId(), userId);
        if (member == null) {
            throw FarmErrors.notMember(farm.getName());
        }
        if (!FarmRole.OWNER.name().equals(member.getRole())) {
            throw FarmErrors.roleForbidden(FarmErrors.MSG_NO_PERMISSION);
        }
    }

    private FarmChange latestVisible(Long farmId) {
        return TenantSkip.call(() -> changeMapper.selectOne(Wrappers.<FarmChange>lambdaQuery()
                .eq(FarmChange::getFarmId, farmId)
                .ne(FarmChange::getStatus, "CANCELED")
                .orderByDesc(FarmChange::getCreatedAt)
                .orderByDesc(FarmChange::getId)
                .last("LIMIT 1")));
    }

    private FarmChange pendingChange(Long farmId) {
        return TenantSkip.call(() -> changeMapper.selectOne(Wrappers.<FarmChange>lambdaQuery()
                .eq(FarmChange::getFarmId, farmId)
                .eq(FarmChange::getStatus, "PENDING")
                .last("LIMIT 1")));
    }

    private int maxChangeNo(Long farmId) {
        FarmChange latest = TenantSkip.call(() -> changeMapper.selectOne(Wrappers.<FarmChange>lambdaQuery()
                .eq(FarmChange::getFarmId, farmId)
                .orderByDesc(FarmChange::getChangeNo)
                .last("LIMIT 1")));
        return latest == null || latest.getChangeNo() == null ? 0 : latest.getChangeNo();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameDecimal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
