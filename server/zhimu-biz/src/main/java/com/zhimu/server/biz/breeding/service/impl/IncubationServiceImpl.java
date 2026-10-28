package com.zhimu.server.biz.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.breeding.convert.IncubationConvert;
import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.breeding.request.CandlingRequest;
import com.zhimu.server.biz.breeding.request.HatchRequest;
import com.zhimu.server.biz.breeding.request.IncubationRequest;
import com.zhimu.server.biz.breeding.request.IncubationUpdateRequest;
import com.zhimu.server.biz.breeding.service.IncubationService;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingDates;
import com.zhimu.server.biz.breeding.support.BreedingErrors;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.support.BreedingValidations;
import com.zhimu.server.biz.breeding.vo.IncubationDetailVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.SelfBredBatchService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** API-B26、B28–B31。 */
@Service
@RequiredArgsConstructor
public class IncubationServiceImpl implements IncubationService {

    /** 自繁批次来源记录类型（t_batch.source_record_type） */
    static final String SOURCE_INCUBATION = "INCUBATION";

    private final IncubationMapper incubationMapper;
    private final BreedingSupport support;
    private final IncubationConvert convert;
    private final OperationLogRecorder operationLogRecorder;
    private final SelfBredBatchService selfBredBatchService;

    @Override
    @Transactional
    public RegisterResultVO create(IncubationRequest request, String idempotencyKey) {
        String requestId = requireRequestId(idempotencyKey);
        if (request.getBreedingBatchId() == null) {
            throw BreedingErrors.invalid("breeding_batch_id", "请选择种禽群");
        }
        BatchInfo batch = support.batchOrNull(request.getBreedingBatchId());
        if (batch == null || !Objects.equals(batch.farmId(), support.farmId())) {
            throw BreedingErrors.notFound();
        }
        if (!batch.active()) {
            throw BreedingErrors.stateChanged("该种禽批次已结束，请重新选择");
        }
        CategoryInfo category = support.categoryQueryInfo(batch.categoryId());
        if (category == null || !"POULTRY".equals(category.species())) {
            throw BreedingErrors.invalid("breeding_batch_id", "请选择种禽群");
        }
        BreedQueryService.BreedInfo breed = support.breed(batch.breedId());
        if (breed == null || breed.incubationDays() == null) {
            throw BreedingErrors.invalid("breeding_batch_id", "该品种没有设置孵化期，暂不能推算出雏日");
        }
        LocalDate setDate = BreedingValidations.date("set_date", request.getSetDate(), "请选择入孵日期");
        BreedingValidations.notFuture("set_date", setDate, "入孵日期不能晚于今天");
        if (batch.entryDate() != null && setDate.isBefore(batch.entryDate())) {
            throw BreedingErrors.invalid("set_date", "入孵日期不能早于种用批次入栏日期 "
                    + BreedingCodes.monthDay(batch.entryDate()));
        }
        Integer setCount = request.getSetCount();
        if (setCount == null) {
            throw BreedingErrors.invalid("set_count", "请填写入孵蛋数");
        }
        if (setCount < 1 || setCount > 999_999) {
            throw BreedingErrors.invalid("set_count", "入孵蛋数需为 1–999,999");
        }
        Long userId = UserContext.requireUserId();
        Incubation incubation = new Incubation();
        incubation.setFarmId(support.farmId());
        incubation.setBreedingBatchId(batch.batchId());
        incubation.setPenId(support.primaryPenOfBatch(batch));
        incubation.setBreedId(batch.breedId());
        incubation.setIncubationDays(breed.incubationDays());
        incubation.setSetDate(setDate);
        incubation.setSetCount(setCount);
        incubation.setExpectedHatchDate(setDate.plusDays(breed.incubationDays()));
        incubation.setStatus(BreedingCodes.INCUBATION_SET);
        incubation.setCapacityConfirmed(false);
        incubation.setSetRequestId(requestId);
        incubation.setVersion(1);
        incubation.setUpdatedBy(userId);
        try {
            incubationMapper.insert(incubation);
        } catch (DuplicateKeyException ex) {
            throw new BizException(com.zhimu.server.common.error.ErrorCode.DUPLICATE_SUBMIT);
        }
        log(incubation.getId(), "CREATE", Map.of("set_count", setCount));
        RegisterResultVO vo = new RegisterResultVO();
        vo.setRecordType(BreedingCodes.RECORD_INCUBATION);
        vo.setRecordId(incubation.getId());
        vo.setIncubationId(incubation.getId());
        vo.setExpectedHatchDate(BeijingTime.format(incubation.getExpectedHatchDate()));
        return vo;
    }

    @Override
    public IncubationDetailVO detail(Long incubationId) {
        return detailOf(require(incubationId));
    }

    @Override
    @Transactional
    public IncubationDetailVO update(Long incubationId, IncubationUpdateRequest request) {
        Incubation incubation = require(incubationId);
        BreedingValidations.checkVersion(request.getVersion(), incubation.getVersion());
        if (BreedingCodes.INCUBATION_HATCHED.equals(incubation.getStatus()) && incubation.getBatchId() != null) {
            throw BreedingErrors.changeRequired("健雏已转为新批次，修改需提交修改申请");
        }
        boolean hatched = BreedingCodes.INCUBATION_HATCHED.equals(incubation.getStatus());
        BatchInfo batch = support.batchOrNull(incubation.getBreedingBatchId());
        if (request.getSetDate() != null || request.getSetCount() != null || request.getCandleDate() != null
                || request.getFertileCount() != null) {
            if (hatched) {
                throw BreedingErrors.stateChanged("已登记出雏，入孵与照蛋信息不能修改");
            }
            if (request.getSetDate() != null) {
                LocalDate setDate = BreedingValidations.date("set_date", request.getSetDate(), "请选择入孵日期");
                BreedingValidations.notFuture("set_date", setDate, "入孵日期不能晚于今天");
                if (batch != null && batch.entryDate() != null && setDate.isBefore(batch.entryDate())) {
                    throw BreedingErrors.invalid("set_date", "入孵日期不能早于种用批次入栏日期 "
                            + BreedingCodes.monthDay(batch.entryDate()));
                }
                incubation.setSetDate(setDate);
                incubation.setExpectedHatchDate(setDate.plusDays(incubation.getIncubationDays()));
            }
            if (request.getSetCount() != null) {
                if (request.getSetCount() < 1 || request.getSetCount() > 999_999) {
                    throw BreedingErrors.invalid("set_count", "入孵蛋数需为 1–999,999");
                }
                incubation.setSetCount(request.getSetCount());
            }
            if (request.getCandleDate() != null) {
                LocalDate candleDate = BreedingValidations.date("candle_date", request.getCandleDate(),
                        "请选择照蛋日期");
                BreedingValidations.notFuture("candle_date", candleDate, "照蛋日期不能晚于今天");
                if (candleDate.isBefore(incubation.getSetDate())) {
                    throw BreedingErrors.invalid("candle_date", "照蛋日期不能早于入孵日期");
                }
                incubation.setCandleDate(candleDate);
            }
            if (request.getFertileCount() != null) {
                if (request.getFertileCount() < 0 || request.getFertileCount() > incubation.getSetCount()) {
                    throw BreedingErrors.invalid("fertile_count", "不能多于入孵蛋数 "
                            + BreedingSupport.formatNumber(incubation.getSetCount()) + " 枚");
                }
                incubation.setFertileCount(request.getFertileCount());
            }
        }
        if (request.getHatchDate() != null || request.getHatchedCount() != null
                || request.getHealthyCount() != null) {
            if (!hatched) {
                throw BreedingErrors.stateChanged("尚未登记出雏，请先登记出雏");
            }
            applyHatchFields(incubation, request.getHatchDate(), request.getHatchedCount(),
                    request.getHealthyCount());
        }
        incubation.setUpdatedBy(UserContext.requireUserId());
        if (incubationMapper.updateById(incubation) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(incubation.getId(), "UPDATE", null);
        return detailOf(incubation);
    }

    @Override
    @Transactional
    public IncubationDetailVO candling(Long incubationId, CandlingRequest request, String idempotencyKey) {
        requireRequestId(idempotencyKey);
        Incubation incubation = require(incubationId);
        if (request.getVersion() != null) {
            BreedingValidations.checkVersion(request.getVersion(), incubation.getVersion());
        }
        if (BreedingCodes.INCUBATION_HATCHED.equals(incubation.getStatus())) {
            throw BreedingErrors.stateChanged("本次孵化已登记出雏，不能重复登记");
        }
        LocalDate candleDate = BreedingValidations.date("candle_date", request.getCandleDate(),
                "请选择照蛋日期");
        BreedingValidations.notFuture("candle_date", candleDate, "照蛋日期不能晚于今天");
        if (candleDate.isBefore(incubation.getSetDate())) {
            throw BreedingErrors.invalid("candle_date", "照蛋日期不能早于入孵日期");
        }
        if (request.getFertileCount() == null) {
            throw BreedingErrors.invalid("fertile_count", "请填写受精蛋数");
        }
        if (request.getFertileCount() < 0 || request.getFertileCount() > incubation.getSetCount()) {
            throw BreedingErrors.invalid("fertile_count", "不能多于入孵蛋数 "
                    + BreedingSupport.formatNumber(incubation.getSetCount()) + " 枚");
        }
        if (BreedingCodes.INCUBATION_CANDLED.equals(incubation.getStatus())) {
            if (candleDate.equals(incubation.getCandleDate())
                    && request.getFertileCount().equals(incubation.getFertileCount())) {
                return detailOf(incubation);
            }
            throw BreedingErrors.stateChanged("已登记照蛋，请在更多菜单中修改");
        }
        Long userId = UserContext.requireUserId();
        incubation.setCandleDate(candleDate);
        incubation.setFertileCount(request.getFertileCount());
        incubation.setCandleBy(userId);
        incubation.setCandleAt(BeijingTime.now());
        incubation.setStatus(BreedingCodes.INCUBATION_CANDLED);
        incubation.setUpdatedBy(userId);
        if (incubationMapper.updateById(incubation) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(incubation.getId(), "UPDATE", Map.of("fertile_count", request.getFertileCount()));
        return detailOf(incubation);
    }

    @Override
    @Transactional
    public RegisterResultVO hatch(Long incubationId, HatchRequest request, String idempotencyKey) {
        String requestId = requireRequestId(idempotencyKey);
        Incubation incubation = require(incubationId);
        BreedingValidations.checkVersion(request.getVersion(), incubation.getVersion());
        if (BreedingCodes.INCUBATION_SET.equals(incubation.getStatus())) {
            throw BreedingErrors.stateChanged("请先登记照蛋");
        }
        if (BreedingCodes.INCUBATION_HATCHED.equals(incubation.getStatus())) {
            throw BreedingErrors.stateChanged("本次孵化已登记出雏，不能重复登记");
        }
        Integer hatched = request.getHatchedCount();
        Integer healthy = request.getHealthyCount();
        if (hatched == null) {
            throw BreedingErrors.invalid("hatched_count", "请填写出雏数");
        }
        if (hatched < 0 || hatched > incubation.getFertileCount()) {
            throw BreedingErrors.invalid("hatched_count", "不能多于受精蛋数 "
                    + BreedingSupport.formatNumber(incubation.getFertileCount()) + " 枚");
        }
        if (healthy == null) {
            throw BreedingErrors.invalid("healthy_count", "请填写健雏数");
        }
        if (healthy < 0 || healthy > hatched) {
            throw BreedingErrors.invalid("healthy_count", "健雏数不能多于出雏数 "
                    + BreedingSupport.formatNumber(hatched) + " 只");
        }
        boolean transfer = request.getTransferToBatch() == null || request.getTransferToBatch();
        if (healthy == 0 && transfer) {
            throw BreedingErrors.invalid("transfer_to_batch", "健雏数为 0，不能转为新批次");
        }
        Long targetPenId = request.getTargetPenId();
        Long offspringBreedId = request.getOffspringBreedId() == null ? incubation.getBreedId()
                : request.getOffspringBreedId();
        if (transfer) {
            if (targetPenId == null) {
                throw BreedingErrors.invalid("target_pen_id", "请选择入栏鸡舍");
            }
            PenInfo pen = support.pen(targetPenId);
            if (pen == null || !pen.enabled() || !support.farmId().equals(pen.farmId())) {
                throw BreedingErrors.invalid("target_pen_id", "请选择入栏鸡舍");
            }
            support.checkCapacity(targetPenId, pen.penName(), support.unitOfBreed(offspringBreedId), healthy,
                    Boolean.TRUE.equals(request.getCapacityConfirmed()));
        }
        Long userId = UserContext.requireUserId();
        applyHatchFields(incubation, request.getHatchDate(), hatched, healthy);
        incubation.setStatus(BreedingCodes.INCUBATION_HATCHED);
        incubation.setTransferToBatch(transfer);
        incubation.setTargetPenId(transfer ? targetPenId : null);
        incubation.setOffspringBreedId(transfer ? offspringBreedId : null);
        incubation.setCapacityConfirmed(Boolean.TRUE.equals(request.getCapacityConfirmed()));
        // 转为新批次：同一事务创建自繁批次（数量 = 健雏、入栏日期 = 出雏日期、日龄 0、来源 INCUBATION + 记录 ID）
        SelfBredBatchService.CreatedBatch createdBatch = null;
        if (transfer) {
            createdBatch = selfBredBatchService.create(targetPenId, offspringBreedId, healthy,
                    incubation.getHatchDate(), Boolean.TRUE.equals(request.getCapacityConfirmed()),
                    SOURCE_INCUBATION, incubation.getId());
        }
        incubation.setBatchId(createdBatch == null ? null : createdBatch.batchId());
        incubation.setHatchBy(userId);
        incubation.setHatchAt(BeijingTime.now());
        incubation.setHatchRequestId(requestId);
        incubation.setUpdatedBy(userId);
        if (incubationMapper.updateById(incubation) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(incubation.getId(), "UPDATE", Map.of("healthy_count", healthy));
        RegisterResultVO vo = new RegisterResultVO();
        vo.setRecordType(BreedingCodes.RECORD_INCUBATION);
        vo.setRecordId(incubation.getId());
        vo.setIncubationId(incubation.getId());
        vo.setExpectedHatchDate(BeijingTime.format(incubation.getExpectedHatchDate()));
        vo.setBatchId(incubation.getBatchId());
        vo.setBatchNo(createdBatch != null ? createdBatch.batchNo() : support.batchNo(incubation.getBatchId()));
        vo.setTodoCompleted(true);
        return vo;
    }

    private void applyHatchFields(Incubation incubation, String hatchDateValue, Integer hatched, Integer healthy) {
        LocalDate hatchDate = hatchDateValue == null ? incubation.getHatchDate()
                : BreedingValidations.date("hatch_date", hatchDateValue, "请选择出雏日期");
        if (hatchDate == null) {
            throw BreedingErrors.invalid("hatch_date", "请选择出雏日期");
        }
        BreedingValidations.notFuture("hatch_date", hatchDate, "出雏日期不能晚于今天");
        LocalDate base = incubation.getCandleDate() == null ? incubation.getSetDate() : incubation.getCandleDate();
        if (base != null && hatchDate.isBefore(base)) {
            throw BreedingErrors.invalid("hatch_date", "出雏日期不能早于照蛋日期");
        }
        if (hatched == null || healthy == null) {
            throw BreedingErrors.invalid("hatched_count", "请填写出雏数与健雏数");
        }
        if (incubation.getFertileCount() != null && hatched > incubation.getFertileCount()) {
            throw BreedingErrors.invalid("hatched_count", "不能多于受精蛋数 "
                    + BreedingSupport.formatNumber(incubation.getFertileCount()) + " 枚");
        }
        if (healthy > hatched) {
            throw BreedingErrors.invalid("healthy_count", "健雏数不能多于出雏数 "
                    + BreedingSupport.formatNumber(hatched) + " 只");
        }
        incubation.setHatchDate(hatchDate);
        incubation.setHatchedCount(hatched);
        incubation.setHealthyCount(healthy);
        incubation.setWeakCount(hatched - healthy);
    }

    private Incubation require(Long incubationId) {
        if (incubationId == null) {
            throw BreedingErrors.notFound();
        }
        Incubation incubation = incubationMapper.selectById(incubationId);
        if (incubation == null) {
            throw BreedingErrors.notFound();
        }
        return incubation;
    }

    private IncubationDetailVO detailOf(Incubation incubation) {
        BatchInfo batch = support.batchOrNull(incubation.getBreedingBatchId());
        PenInfo pen = support.pen(incubation.getPenId());
        PenInfo targetPen = support.pen(incubation.getTargetPenId());
        String flockText = flockText(pen == null ? null : pen.penName(), batch == null ? null : batch.batchNo());
        Set<Long> userIds = new HashSet<>();
        userIds.add(incubation.getCreatedBy());
        userIds.add(incubation.getCandleBy());
        userIds.add(incubation.getHatchBy());
        Map<Long, UserBriefVO> users = support.users(userIds);
        FarmContext.FarmMember viewer = FarmContext.get();
        return convert.detail(incubation, viewer == null ? null : viewer.farmName(), flockText,
                support.breedName(incubation.getBreedId()), targetPen == null ? null : targetPen.penName(),
                support.breedName(incubation.getOffspringBreedId()),
                // batch_no 与 batch_id 成对：健雏转入的新批次号（App 出雏步骤“新批次”行）；种禽批次号已在 flock_text 里
                support.batchNo(incubation.getBatchId()), users);
    }

    public static String flockText(String penName, String batchNo) {
        if (batchNo == null) {
            return penName;
        }
        return penName == null ? batchNo : penName + " · " + batchNo;
    }

    private static String requireRequestId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BreedingErrors.invalid("缺少幂等键，请重新提交");
        }
        return idempotencyKey.trim();
    }

    private void log(Long incubationId, String opAction, Object after) {
        FarmContext.FarmMember viewer = FarmContext.get();
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(UserContext.requireUserId())
                .operatorRole(viewer == null ? null : viewer.role().name())
                .opType("RECORD_EDIT")
                .opAction(opAction)
                .bizType(BizType.INCUBATION.name())
                .bizId(incubationId)
                .farmId(support.farmId())
                .after(after)
                .build());
    }
}
