package com.zhimu.server.biz.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.breeding.convert.BreedingRecordConvert;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.MatingRecord;
import com.zhimu.server.biz.breeding.entity.PregnancyCheck;
import com.zhimu.server.biz.breeding.entity.WeaningRecord;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.MatingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.PregnancyCheckMapper;
import com.zhimu.server.biz.breeding.mapper.WeaningRecordMapper;
import com.zhimu.server.biz.breeding.request.FarrowingRequest;
import com.zhimu.server.biz.breeding.request.FarrowingUpdateRequest;
import com.zhimu.server.biz.breeding.request.MatingRequest;
import com.zhimu.server.biz.breeding.request.MatingUpdateRequest;
import com.zhimu.server.biz.breeding.request.PregnancyCheckRequest;
import com.zhimu.server.biz.breeding.request.PregnancyCheckUpdateRequest;
import com.zhimu.server.biz.breeding.request.WeaningRequest;
import com.zhimu.server.biz.breeding.request.WeaningUpdateRequest;
import com.zhimu.server.biz.breeding.service.BreedingRecordService;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingDates;
import com.zhimu.server.biz.breeding.support.BreedingErrors;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.support.BreedingValidations;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.FarrowingDetailVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** API-B12–B21：猪的配种 → 妊检 → 产仔 → 断奶。 */
@Service
@RequiredArgsConstructor
public class BreedingRecordServiceImpl implements BreedingRecordService {

    private final BreedingAnimalMapper animalMapper;
    private final BreedingCycleMapper cycleMapper;
    private final MatingRecordMapper matingMapper;
    private final PregnancyCheckMapper checkMapper;
    private final FarrowingRecordMapper farrowingMapper;
    private final WeaningRecordMapper weaningMapper;
    private final BreedingSupport support;
    private final BreedingRecordConvert convert;
    private final OperationLogRecorder operationLogRecorder;

    // ===== 配种 =====

    @Override
    @Transactional
    public RegisterResultVO createMating(MatingRequest request, String idempotencyKey) {
        String requestId = requireRequestId(idempotencyKey);
        if (request.getAnimalId() == null) {
            throw BreedingErrors.invalid("animal_id", "请选择母猪");
        }
        BreedingAnimal animal = support.requireAnimal(request.getAnimalId());
        if (BreedingCodes.exited(animal.getStatus())) {
            throw exitedMessage(animal, "登记配种");
        }
        LocalDate matingDate = BreedingValidations.date("mating_date", request.getMatingDate(),
                "请选择配种日期");
        BreedingValidations.notFuture("mating_date", matingDate, "配种日期不能晚于今天");
        String method = request.getMatingMethod();
        if (method == null || !List.of("ARTIFICIAL", "NATURAL").contains(method)) {
            throw BreedingErrors.invalid("mating_method", "请选择配种方式");
        }
        String boarInfo = BreedingValidations.optionalText("boar_info", request.getBoarInfo(), 20, "最多 20 个字");
        Long userId = UserContext.requireUserId();
        BreedingCycle cycle;
        boolean first;
        if (BreedingCodes.STATUS_EMPTY.equals(animal.getStatus())) {
            if (animal.getLastCycleEndDate() != null && matingDate.isBefore(animal.getLastCycleEndDate())) {
                throw BreedingErrors.invalid("mating_date", "配种日期不能早于"
                        + lastCycleEndLabel(animal) + " " + BreedingCodes.monthDay(animal.getLastCycleEndDate()));
            }
            if (animal.getBirthDate() != null && matingDate.isBefore(animal.getBirthDate())) {
                throw BreedingErrors.invalid("mating_date", "配种日期不能早于出生日期");
            }
            BreedQueryService.BreedInfo breed = support.breed(animal.getBreedId());
            if (breed == null || breed.gestationDays() == null) {
                throw BreedingErrors.invalid("mating_date", "该品种没有设置妊娠期，暂不能推算预产期");
            }
            int checkDays = breed.pregnancyCheckDays() == null ? 21 : breed.pregnancyCheckDays();
            cycle = new BreedingCycle();
            cycle.setFarmId(support.farmId());
            cycle.setAnimalId(animal.getId());
            cycle.setFirstMatingDate(matingDate);
            cycle.setLastMatingDate(matingDate);
            cycle.setMatingCount(1);
            cycle.setGestationDays(breed.gestationDays());
            cycle.setPregnancyCheckDays(checkDays);
            cycle.setCheckDueDate(matingDate.plusDays(checkDays));
            cycle.setExpectedDueDate(matingDate.plusDays(breed.gestationDays()));
            cycle.setStatus(BreedingCodes.CYCLE_MATED);
            cycle.setVersion(1);
            cycle.setUpdatedBy(userId);
            cycleMapper.insert(cycle);
            animal.setStatus(BreedingCodes.STATUS_MATED);
            animal.setCurrentCycleId(cycle.getId());
            animal.setUpdatedBy(userId);
            if (animalMapper.updateById(animal) == 0) {
                throw BreedingErrors.versionConflict();
            }
            first = true;
        } else if (BreedingCodes.STATUS_MATED.equals(animal.getStatus())) {
            if (animal.getCurrentCycleId() == null) {
                throw stateChanged(animal);
            }
            cycle = support.requireCycle(animal.getCurrentCycleId());
            if (matingDate.isBefore(cycle.getFirstMatingDate())) {
                throw BreedingErrors.invalid("mating_date", "配种日期不能早于本周期首次配种日期 "
                        + BreedingCodes.monthDay(cycle.getFirstMatingDate()));
            }
            cycle.setLastMatingDate(matingDate.isAfter(cycle.getLastMatingDate())
                    ? matingDate : cycle.getLastMatingDate());
            cycle.setMatingCount((cycle.getMatingCount() == null ? 0 : cycle.getMatingCount()) + 1);
            cycle.setUpdatedBy(userId);
            if (cycleMapper.updateById(cycle) == 0) {
                throw BreedingErrors.versionConflict();
            }
            first = false;
        } else {
            throw stateChanged(animal);
        }
        MatingRecord record = new MatingRecord();
        record.setFarmId(support.farmId());
        record.setAnimalId(animal.getId());
        record.setCycleId(cycle.getId());
        record.setMatingDate(matingDate);
        record.setMatingMethod(method);
        record.setBoarInfo(boarInfo);
        record.setIsFirst(first);
        record.setClientRequestId(requestId);
        record.setVersion(1);
        record.setUpdatedBy(userId);
        try {
            matingMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw duplicateSubmit();
        }
        log(BizType.MATING, record.getId(), "CREATE", Map.of("mating_date", matingDate, "cycle_id", cycle.getId()));
        return convert.registerResult(BreedingCodes.RECORD_MATING, record.getId(), animal, cycle.getId(), first,
                cycle.getExpectedDueDate(), null, null, null);
    }

    @Override
    @Transactional
    public BreedingRecordVO updateMating(Long recordId, MatingUpdateRequest request) {
        MatingRecord record = requireMating(recordId);
        BreedingCycle cycle = support.requireCycle(record.getCycleId());
        if (cycle.getFarrowingId() != null) {
            throw BreedingErrors.stateChanged("本胎已登记产仔，配种记录不能修改");
        }
        BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
        if (!cycle.getId().equals(animal.getCurrentCycleId())) {
            throw stateChanged(animal);
        }
        BreedingValidations.checkVersion(request.getVersion(), record.getVersion());
        if (request.getMatingDate() != null) {
            LocalDate matingDate = BreedingValidations.date("mating_date", request.getMatingDate(),
                    "请选择配种日期");
            BreedingValidations.notFuture("mating_date", matingDate, "配种日期不能晚于今天");
            if (Boolean.TRUE.equals(record.getIsFirst())) {
                LocalDate last = latestMatingDate(cycle.getId(), record.getId());
                if (last != null && matingDate.isAfter(last)) {
                    throw BreedingErrors.invalid("mating_date", "首次配种日期不能晚于本周期其他配种日期");
                }
                PregnancyCheck check = checkMapper.selectOne(Wrappers.<PregnancyCheck>lambdaQuery()
                        .eq(PregnancyCheck::getCycleId, cycle.getId()).last("LIMIT 1"));
                if (check != null && matingDate.isAfter(check.getCheckDate())) {
                    throw BreedingErrors.invalid("mating_date", "首次配种日期不能晚于妊检日期");
                }
                cycle.setFirstMatingDate(matingDate);
                cycle.setCheckDueDate(matingDate.plusDays(cycle.getPregnancyCheckDays()));
                cycle.setExpectedDueDate(matingDate.plusDays(cycle.getGestationDays()));
                if (Boolean.FALSE.equals(record.getIsFirst()) || cycle.getLastMatingDate() == null
                        || cycle.getLastMatingDate().isBefore(matingDate)) {
                    cycle.setLastMatingDate(matingDate);
                }
                if (cycleMapper.updateById(cycle) == 0) {
                    throw BreedingErrors.versionConflict();
                }
            }
            record.setMatingDate(matingDate);
        }
        if (request.getMatingMethod() != null) {
            if (!List.of("ARTIFICIAL", "NATURAL").contains(request.getMatingMethod())) {
                throw BreedingErrors.invalid("mating_method", "请选择配种方式");
            }
            record.setMatingMethod(request.getMatingMethod());
        }
        if (request.getBoarInfo() != null) {
            record.setBoarInfo(BreedingValidations.optionalText("boar_info", request.getBoarInfo(), 20,
                    "最多 20 个字"));
        }
        record.setUpdatedBy(UserContext.requireUserId());
        if (matingMapper.updateById(record) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.MATING, record.getId(), "UPDATE", null);
        return convert.mating(record, registrant(record.getCreatedBy()));
    }

    // ===== 妊检 =====

    @Override
    @Transactional
    public RegisterResultVO createCheck(PregnancyCheckRequest request, String idempotencyKey) {
        String requestId = requireRequestId(idempotencyKey);
        if (request.getAnimalId() == null) {
            throw BreedingErrors.invalid("animal_id", "请选择母猪");
        }
        BreedingAnimal animal = support.requireAnimal(request.getAnimalId());
        if (BreedingCodes.exited(animal.getStatus())) {
            throw exitedMessage(animal, "登记妊检");
        }
        if (!BreedingCodes.STATUS_MATED.equals(animal.getStatus())) {
            throw stateChanged(animal);
        }
        if (request.getCycleId() == null || !request.getCycleId().equals(animal.getCurrentCycleId())) {
            throw stateChanged(animal);
        }
        BreedingCycle cycle = support.requireCycle(animal.getCurrentCycleId());
        if (checkMapper.selectCount(Wrappers.<PregnancyCheck>lambdaQuery()
                .eq(PregnancyCheck::getCycleId, cycle.getId())) > 0) {
            throw BreedingErrors.stateChanged("本次配种已登记妊检");
        }
        LocalDate checkDate = BreedingValidations.date("check_date", request.getCheckDate(), "请选择妊检日期");
        BreedingValidations.notFuture("check_date", checkDate, "妊检日期不能晚于今天");
        if (checkDate.isBefore(cycle.getFirstMatingDate())) {
            throw BreedingErrors.invalid("check_date", "妊检日期不能早于配种日期 "
                    + BreedingCodes.monthDay(cycle.getFirstMatingDate()));
        }
        String result = request.getResult();
        if (result == null || !List.of("POSITIVE", "NEGATIVE").contains(result)) {
            throw BreedingErrors.invalid("result", "请选择妊检结果");
        }
        String method = request.getCheckMethod() == null ? "ULTRASOUND" : request.getCheckMethod();
        if (!List.of("ULTRASOUND", "OBSERVATION", "OTHER").contains(method)) {
            throw BreedingErrors.invalid("check_method", "请选择妊检方式");
        }
        Long userId = UserContext.requireUserId();
        PregnancyCheck record = new PregnancyCheck();
        record.setFarmId(support.farmId());
        record.setAnimalId(animal.getId());
        record.setCycleId(cycle.getId());
        record.setCheckDate(checkDate);
        record.setResult(result);
        record.setCheckMethod(method);
        record.setClientRequestId(requestId);
        record.setVersion(1);
        record.setUpdatedBy(userId);
        try {
            checkMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw duplicateSubmit();
        }
        cycle.setPregCheckId(record.getId());
        cycle.setUpdatedBy(userId);
        if ("POSITIVE".equals(result)) {
            cycle.setStatus(BreedingCodes.CYCLE_PREGNANT);
            animal.setStatus(BreedingCodes.STATUS_PREGNANT);
        } else {
            cycle.setStatus(BreedingCodes.CYCLE_FAILED);
            cycle.setEndDate(checkDate);
            cycle.setEndReason("NEGATIVE");
            animal.setStatus(BreedingCodes.STATUS_EMPTY);
            animal.setCurrentCycleId(null);
            animal.setLastCycleEndDate(checkDate);
        }
        animal.setUpdatedBy(userId);
        if (cycleMapper.updateById(cycle) == 0) {
            throw BreedingErrors.versionConflict();
        }
        if (animalMapper.updateById(animal) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.PREG_CHECK, record.getId(), "CREATE", Map.of("result", result));
        return convert.registerResult(BreedingCodes.RECORD_PREG_CHECK, record.getId(), animal, cycle.getId(),
                null, cycle.getExpectedDueDate(), null, null, null);
    }

    @Override
    @Transactional
    public BreedingRecordVO updateCheck(Long recordId, PregnancyCheckUpdateRequest request) {
        PregnancyCheck record = requireCheck(recordId);
        BreedingCycle cycle = support.requireCycle(record.getCycleId());
        BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
        if (cycle.getFarrowingId() != null) {
            throw BreedingErrors.stateChanged("本胎已登记产仔，妊检记录不能修改");
        }
        BreedingValidations.checkVersion(request.getVersion(), record.getVersion());
        Long userId = UserContext.requireUserId();
        if (request.getCheckDate() != null) {
            LocalDate checkDate = BreedingValidations.date("check_date", request.getCheckDate(),
                    "请选择妊检日期");
            BreedingValidations.notFuture("check_date", checkDate, "妊检日期不能晚于今天");
            if (checkDate.isBefore(cycle.getFirstMatingDate())) {
                throw BreedingErrors.invalid("check_date", "妊检日期不能早于配种日期 "
                        + BreedingCodes.monthDay(cycle.getFirstMatingDate()));
            }
            record.setCheckDate(checkDate);
        }
        if (request.getCheckMethod() != null) {
            if (!List.of("ULTRASOUND", "OBSERVATION", "OTHER").contains(request.getCheckMethod())) {
                throw BreedingErrors.invalid("check_method", "请选择妊检方式");
            }
            record.setCheckMethod(request.getCheckMethod());
        }
        if (request.getResult() != null && !request.getResult().equals(record.getResult())) {
            if (!List.of("POSITIVE", "NEGATIVE").contains(request.getResult())) {
                throw BreedingErrors.invalid("result", "请选择妊检结果");
            }
            if ("NEGATIVE".equals(request.getResult())) {
                if (!BreedingCodes.STATUS_PREGNANT.equals(animal.getStatus())
                        || !cycle.getId().equals(animal.getCurrentCycleId())) {
                    throw stateChanged(animal);
                }
                animal.setStatus(BreedingCodes.STATUS_EMPTY);
                animal.setCurrentCycleId(null);
                animal.setLastCycleEndDate(record.getCheckDate());
                cycle.setStatus(BreedingCodes.CYCLE_FAILED);
                cycle.setEndDate(record.getCheckDate());
                cycle.setEndReason("NEGATIVE");
            } else {
                if (!BreedingCodes.STATUS_EMPTY.equals(animal.getStatus())
                        || animal.getCurrentCycleId() != null
                        || !record.getCheckDate().equals(animal.getLastCycleEndDate())) {
                    throw stateChanged(animal);
                }
                animal.setStatus(BreedingCodes.STATUS_PREGNANT);
                animal.setCurrentCycleId(cycle.getId());
                animal.setLastCycleEndDate(null);
                cycle.setStatus(BreedingCodes.CYCLE_PREGNANT);
                cycle.setEndDate(null);
                cycle.setEndReason(null);
                cycle.setPregCheckId(record.getId());
            }
            record.setResult(request.getResult());
            animal.setUpdatedBy(userId);
            cycle.setUpdatedBy(userId);
            if (animalMapper.updateById(animal) == 0 || cycleMapper.updateById(cycle) == 0) {
                throw BreedingErrors.versionConflict();
            }
        }
        record.setUpdatedBy(userId);
        if (checkMapper.updateById(record) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.PREG_CHECK, record.getId(), "UPDATE", null);
        return convert.check(record, registrant(record.getCreatedBy()));
    }

    // ===== 产仔 =====

    @Override
    @Transactional
    public RegisterResultVO createFarrowing(FarrowingRequest request, String idempotencyKey) {
        String requestId = requireRequestId(idempotencyKey);
        if (request.getAnimalId() == null) {
            throw BreedingErrors.invalid("animal_id", "请选择母猪");
        }
        BreedingAnimal animal = support.requireAnimal(request.getAnimalId());
        if (BreedingCodes.exited(animal.getStatus())) {
            throw exitedMessage(animal, "登记产仔");
        }
        if (!BreedingCodes.STATUS_PREGNANT.equals(animal.getStatus()) || animal.getCurrentCycleId() == null) {
            throw stateChanged(animal);
        }
        BreedingCycle cycle = support.requireCycle(animal.getCurrentCycleId());
        if (farrowingMapper.selectCount(Wrappers.<FarrowingRecord>lambdaQuery()
                .eq(FarrowingRecord::getCycleId, cycle.getId())) > 0) {
            throw BreedingErrors.stateChanged(animal.getEarTag() + " 本胎已登记产仔，不能重复登记");
        }
        LocalDate farrowDate = BreedingValidations.date("farrow_date", request.getFarrowDate(),
                "请选择产仔日期");
        BreedingValidations.notFuture("farrow_date", farrowDate, "产仔日期不能晚于今天");
        if (farrowDate.isBefore(cycle.getFirstMatingDate())) {
            throw BreedingErrors.invalid("farrow_date", "产仔日期不能早于配种日期 "
                    + BreedingCodes.monthDay(cycle.getFirstMatingDate()));
        }
        Counts counts = validateCounts(request.getTotalBorn(), request.getLiveBorn(), request.getStillborn());
        boolean transfer = request.getTransferToBatch() == null || request.getTransferToBatch();
        if (counts.liveBorn() == 0 && transfer) {
            throw BreedingErrors.invalid("transfer_to_batch",
                    "活仔数为 0，不能转为新批次。请关闭“活仔一键转为新批次”。");
        }
        Long targetPenId = request.getTargetPenId() == null ? animal.getPenId() : request.getTargetPenId();
        Long offspringBreedId = offspringBreed(request.getOffspringBreedId(), animal.getBreedId());
        String unit = support.unitOfBreed(offspringBreedId);
        if (transfer) {
            requirePen(targetPenId, "请选择入栏栏舍");
            PenInfo pen = support.pen(targetPenId);
            support.checkCapacity(targetPenId, pen == null ? null : pen.penName(), unit, counts.liveBorn(),
                    Boolean.TRUE.equals(request.getCapacityConfirmed()));
        }
        Long userId = UserContext.requireUserId();
        FarrowingRecord record = new FarrowingRecord();
        record.setFarmId(support.farmId());
        record.setAnimalId(animal.getId());
        record.setCycleId(cycle.getId());
        record.setParityNo((animal.getParity() == null ? 0 : animal.getParity()) + 1);
        record.setFarrowDate(farrowDate);
        record.setTotalBorn(counts.totalBorn());
        record.setLiveBorn(counts.liveBorn());
        record.setStillborn(counts.stillborn());
        record.setTransferToBatch(transfer);
        record.setTargetPenId(targetPenId);
        record.setOffspringBreedId(offspringBreedId);
        record.setCapacityConfirmed(Boolean.TRUE.equals(request.getCapacityConfirmed()));
        // TODO 模块04 提供写入口（如 BatchCommandService.createSelfBredBatch）后改为同一事务创建自繁批次：
        //  入栏栏舍 targetPenId、数量 = 活仔、入栏日期 = farrowDate、日龄 0、source = SELF_BRED、
        //  source_record_type = FARROWING；当前批次只读，batch_id 暂空，App 按未转批次显示。
        record.setBatchId(null);
        record.setClientRequestId(requestId);
        record.setVersion(1);
        record.setUpdatedBy(userId);
        try {
            farrowingMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw duplicateSubmit();
        }
        animal.setParity((animal.getParity() == null ? 0 : animal.getParity()) + 1);
        if (counts.liveBorn() > 0) {
            animal.setStatus(BreedingCodes.STATUS_LACTATING);
        } else {
            animal.setStatus(BreedingCodes.STATUS_EMPTY);
            animal.setCurrentCycleId(null);
            animal.setLastCycleEndDate(farrowDate);
        }
        animal.setUpdatedBy(userId);
        cycle.setStatus(BreedingCodes.CYCLE_FARROWED);
        cycle.setFarrowingId(record.getId());
        if (counts.liveBorn() == 0) {
            cycle.setEndDate(farrowDate);
            cycle.setEndReason("NO_LIVE");
        }
        cycle.setUpdatedBy(userId);
        if (animalMapper.updateById(animal) == 0) {
            throw BreedingErrors.versionConflict();
        }
        if (cycleMapper.updateById(cycle) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.FARROWING, record.getId(), "CREATE", Map.of("live_born", counts.liveBorn()));
        return convert.registerResult(BreedingCodes.RECORD_FARROWING, record.getId(), animal, cycle.getId(),
                null, null, null, null, true);
    }

    @Override
    public FarrowingDetailVO farrowing(Long farrowingId) {
        FarrowingRecord record = farrowingMapper.selectById(farrowingId);
        if (record == null) {
            throw BreedingErrors.notFound();
        }
        BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
        PenInfo targetPen = support.pen(record.getTargetPenId());
        String offspringBreedName = support.breedName(record.getOffspringBreedId());
        BatchInfo batch = support.batchOrNull(record.getBatchId());
        FarrowingDetailVO vo = new FarrowingDetailVO();
        vo.setFarrowingId(record.getId());
        vo.setAnimalId(record.getAnimalId());
        vo.setCycleId(record.getCycleId() == null ? 0 : record.getCycleId());
        vo.setTargetPenId(record.getTargetPenId());
        vo.setOffspringBreedId(record.getOffspringBreedId());
        vo.setBatchId(record.getBatchId());
        vo.setEarTag(animal.getEarTag());
        vo.setFarrowDate(BeijingTime.format(record.getFarrowDate()));
        vo.setTargetPenName(targetPen == null ? null : targetPen.penName());
        vo.setOffspringBreedName(offspringBreedName);
        vo.setBatchNo(batch == null ? null : batch.batchNo());
        vo.setCreatedAt(BeijingTime.format(record.getCreatedAt()));
        vo.setParityNo(record.getParityNo() == null ? 0 : record.getParityNo());
        vo.setTotalBorn(record.getTotalBorn() == null ? 0 : record.getTotalBorn());
        vo.setLiveBorn(record.getLiveBorn() == null ? 0 : record.getLiveBorn());
        vo.setStillborn(record.getStillborn() == null ? 0 : record.getStillborn());
        vo.setVersion(record.getVersion() == null ? 0 : record.getVersion());
        vo.setBatchCurrentCount(batch == null ? null : batch.currentCount());
        vo.setTransferToBatch(Boolean.TRUE.equals(record.getTransferToBatch()));
        boolean transferred = record.getBatchId() != null;
        vo.setCanRequestChange(transferred);
        vo.setChangeState(new ChangeRequestStateVO(!transferred, null, null));
        vo.setRegistrant(registrant(record.getCreatedBy()));
        return vo;
    }

    @Override
    @Transactional
    public BreedingRecordVO updateFarrowing(Long farrowingId, FarrowingUpdateRequest request) {
        FarrowingRecord record = farrowingMapper.selectById(farrowingId);
        if (record == null) {
            throw BreedingErrors.notFound();
        }
        if (record.getBatchId() != null) {
            throw BreedingErrors.changeRequired("已转为新批次的产仔记录需提交修改申请");
        }
        BreedingCycle cycle = support.requireCycle(record.getCycleId());
        if (cycle.getWeaningId() != null) {
            throw BreedingErrors.stateChanged("本胎已登记断奶，产仔记录不能修改");
        }
        BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
        BreedingValidations.checkVersion(request.getVersion(), record.getVersion());
        LocalDate farrowDate = record.getFarrowDate();
        if (request.getFarrowDate() != null) {
            farrowDate = BreedingValidations.date("farrow_date", request.getFarrowDate(), "请选择产仔日期");
            BreedingValidations.notFuture("farrow_date", farrowDate, "产仔日期不能晚于今天");
            if (farrowDate.isBefore(cycle.getFirstMatingDate())) {
                throw BreedingErrors.invalid("farrow_date", "产仔日期不能早于配种日期 "
                        + BreedingCodes.monthDay(cycle.getFirstMatingDate()));
            }
        }
        int total = request.getTotalBorn() == null ? record.getTotalBorn() : request.getTotalBorn();
        int live = request.getLiveBorn() == null ? record.getLiveBorn() : request.getLiveBorn();
        int still = request.getStillborn() == null ? record.getStillborn() : request.getStillborn();
        Counts counts = validateCounts(total, live, still);
        int oldLive = record.getLiveBorn() == null ? 0 : record.getLiveBorn();
        Long userId = UserContext.requireUserId();
        if (oldLive > 0 && counts.liveBorn() == 0) {
            if (!BreedingCodes.STATUS_LACTATING.equals(animal.getStatus())
                    || !cycle.getId().equals(animal.getCurrentCycleId())) {
                throw stateChanged(animal);
            }
            animal.setStatus(BreedingCodes.STATUS_EMPTY);
            animal.setCurrentCycleId(null);
            animal.setLastCycleEndDate(farrowDate);
            cycle.setEndDate(farrowDate);
            cycle.setEndReason("NO_LIVE");
            animal.setUpdatedBy(userId);
            cycle.setUpdatedBy(userId);
            if (animalMapper.updateById(animal) == 0 || cycleMapper.updateById(cycle) == 0) {
                throw BreedingErrors.versionConflict();
            }
        } else if (oldLive == 0 && counts.liveBorn() > 0) {
            if (!BreedingCodes.STATUS_EMPTY.equals(animal.getStatus()) || animal.getCurrentCycleId() != null) {
                throw stateChanged(animal);
            }
            animal.setStatus(BreedingCodes.STATUS_LACTATING);
            animal.setCurrentCycleId(cycle.getId());
            animal.setLastCycleEndDate(null);
            cycle.setEndDate(null);
            cycle.setEndReason(null);
            animal.setUpdatedBy(userId);
            cycle.setUpdatedBy(userId);
            if (animalMapper.updateById(animal) == 0 || cycleMapper.updateById(cycle) == 0) {
                throw BreedingErrors.versionConflict();
            }
        }
        record.setFarrowDate(farrowDate);
        record.setTotalBorn(counts.totalBorn());
        record.setLiveBorn(counts.liveBorn());
        record.setStillborn(counts.stillborn());
        record.setUpdatedBy(userId);
        if (farrowingMapper.updateById(record) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.FARROWING, record.getId(), "UPDATE", null);
        return convert.farrowing(record, support.batchNo(record.getBatchId()), registrant(record.getCreatedBy()));
    }

    // ===== 断奶 =====

    @Override
    @Transactional
    public RegisterResultVO createWeaning(WeaningRequest request, String idempotencyKey) {
        String requestId = requireRequestId(idempotencyKey);
        if (request.getAnimalId() == null) {
            throw BreedingErrors.invalid("animal_id", "请选择母猪");
        }
        BreedingAnimal animal = support.requireAnimal(request.getAnimalId());
        if (BreedingCodes.exited(animal.getStatus())) {
            throw exitedMessage(animal, "登记断奶");
        }
        if (!BreedingCodes.STATUS_LACTATING.equals(animal.getStatus()) || animal.getCurrentCycleId() == null) {
            throw stateChanged(animal);
        }
        BreedingCycle cycle = support.requireCycle(animal.getCurrentCycleId());
        if (cycle.getWeaningId() != null) {
            throw BreedingErrors.stateChanged("本胎已登记断奶，不能重复登记");
        }
        FarrowingRecord farrowing = cycle.getFarrowingId() == null ? null
                : farrowingMapper.selectById(cycle.getFarrowingId());
        if (farrowing == null) {
            throw BreedingErrors.stateChanged(animal.getEarTag() + " 的状态已变化，请刷新后再登记");
        }
        LocalDate weanDate = BreedingValidations.date("wean_date", request.getWeanDate(), "请选择断奶日期");
        BreedingValidations.notFuture("wean_date", weanDate, "断奶日期不能晚于今天");
        if (weanDate.isBefore(farrowing.getFarrowDate())) {
            throw BreedingErrors.invalid("wean_date", "断奶日期不能早于产仔日期 "
                    + BreedingCodes.monthDay(farrowing.getFarrowDate()));
        }
        int maxCount = farrowing.getLiveBorn() == null ? 0 : farrowing.getLiveBorn();
        int weaned = request.getWeanedCount() == null ? -1 : request.getWeanedCount();
        if (weaned < 0) {
            throw BreedingErrors.invalid("weaned_count", "请填写断奶仔猪数");
        }
        if (weaned > maxCount) {
            throw BreedingErrors.invalid("weaned_count", "不能多于产仔时的活仔 " + maxCount + " 头");
        }
        BigDecimal weight = BreedingValidations.optionalWeight("litter_weight", request.getLitterWeight());
        Long userId = UserContext.requireUserId();
        WeaningRecord record = new WeaningRecord();
        record.setFarmId(support.farmId());
        record.setAnimalId(animal.getId());
        record.setCycleId(cycle.getId());
        record.setFarrowingId(farrowing.getId());
        record.setWeanDate(weanDate);
        record.setWeanedCount(weaned);
        record.setLitterWeight(weight);
        record.setClientRequestId(requestId);
        record.setVersion(1);
        record.setUpdatedBy(userId);
        try {
            weaningMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw duplicateSubmit();
        }
        cycle.setStatus(BreedingCodes.CYCLE_WEANED);
        cycle.setWeaningId(record.getId());
        cycle.setEndDate(weanDate);
        cycle.setEndReason("WEANED");
        cycle.setUpdatedBy(userId);
        animal.setStatus(BreedingCodes.STATUS_EMPTY);
        animal.setCurrentCycleId(null);
        animal.setLastWeanDate(weanDate);
        animal.setLastCycleEndDate(weanDate);
        animal.setUpdatedBy(userId);
        if (cycleMapper.updateById(cycle) == 0 || animalMapper.updateById(animal) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.WEANING, record.getId(), "CREATE", Map.of("weaned_count", weaned));
        return convert.registerResult(BreedingCodes.RECORD_WEANING, record.getId(), animal, cycle.getId(),
                null, null, null, null, null);
    }

    @Override
    @Transactional
    public BreedingRecordVO updateWeaning(Long recordId, WeaningUpdateRequest request) {
        WeaningRecord record = weaningMapper.selectById(recordId);
        if (record == null) {
            throw BreedingErrors.notFound();
        }
        BreedingValidations.checkVersion(request.getVersion(), record.getVersion());
        BreedingCycle cycle = support.requireCycle(record.getCycleId());
        FarrowingRecord farrowing = farrowingMapper.selectById(record.getFarrowingId());
        BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
        LocalDate weanDate = record.getWeanDate();
        if (request.getWeanDate() != null) {
            weanDate = BreedingValidations.date("wean_date", request.getWeanDate(), "请选择断奶日期");
            BreedingValidations.notFuture("wean_date", weanDate, "断奶日期不能晚于今天");
            if (farrowing != null && weanDate.isBefore(farrowing.getFarrowDate())) {
                throw BreedingErrors.invalid("wean_date", "断奶日期不能早于产仔日期 "
                        + BreedingCodes.monthDay(farrowing.getFarrowDate()));
            }
            if (animal.getCurrentCycleId() != null && !animal.getCurrentCycleId().equals(cycle.getId())) {
                BreedingCycle next = support.requireCycle(animal.getCurrentCycleId());
                if (weanDate.isAfter(next.getFirstMatingDate())) {
                    throw BreedingErrors.invalid("wean_date", "断奶日期不能晚于下一次配种日期 "
                            + BreedingCodes.monthDay(next.getFirstMatingDate()));
                }
            }
            record.setWeanDate(weanDate);
        }
        if (request.getWeanedCount() != null) {
            int maxCount = farrowing == null || farrowing.getLiveBorn() == null ? 0 : farrowing.getLiveBorn();
            if (request.getWeanedCount() < 0 || request.getWeanedCount() > maxCount) {
                throw BreedingErrors.invalid("weaned_count", "不能多于产仔时的活仔 " + maxCount + " 头");
            }
            record.setWeanedCount(request.getWeanedCount());
        }
        if (request.getLitterWeight() != null) {
            record.setLitterWeight(BreedingValidations.optionalWeight("litter_weight",
                    request.getLitterWeight()));
        }
        Long userId = UserContext.requireUserId();
        record.setUpdatedBy(userId);
        if (weaningMapper.updateById(record) == 0) {
            throw BreedingErrors.versionConflict();
        }
        if (animal.getCurrentCycleId() == null && weanDate.equals(animal.getLastWeanDate())) {
            animal.setLastWeanDate(record.getWeanDate());
            animal.setLastCycleEndDate(record.getWeanDate());
            animal.setUpdatedBy(userId);
            animalMapper.updateById(animal);
        }
        log(BizType.WEANING, record.getId(), "UPDATE", null);
        return convert.weaning(record, registrant(record.getCreatedBy()));
    }

    // ===== 共用 =====

    private static String requireRequestId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BreedingErrors.invalid("缺少幂等键，请重新提交");
        }
        return idempotencyKey.trim();
    }

    private static BizException duplicateSubmit() {
        return new BizException(com.zhimu.server.common.error.ErrorCode.DUPLICATE_SUBMIT);
    }

    private static BizException exitedMessage(BreedingAnimal animal, String action) {
        String type = "DEAD".equals(animal.getStatus()) ? "死亡" : "淘汰";
        return BreedingErrors.stateChanged(animal.getEarTag() + " 已" + type + "，不能" + action);
    }

    private static BizException stateChanged(BreedingAnimal animal) {
        return BreedingErrors.stateChanged(animal.getEarTag() + " 的状态已变化，请刷新后再登记");
    }

    private MatingRecord requireMating(Long recordId) {
        MatingRecord record = matingMapper.selectById(recordId);
        if (record == null) {
            throw BreedingErrors.notFound();
        }
        return record;
    }

    private PregnancyCheck requireCheck(Long recordId) {
        PregnancyCheck record = checkMapper.selectById(recordId);
        if (record == null) {
            throw BreedingErrors.notFound();
        }
        return record;
    }

    private LocalDate latestMatingDate(Long cycleId, Long excludeId) {
        return matingMapper.selectList(Wrappers.<MatingRecord>lambdaQuery()
                        .eq(MatingRecord::getCycleId, cycleId)
                        .ne(excludeId != null, MatingRecord::getId, excludeId))
                .stream().map(MatingRecord::getMatingDate).max(LocalDate::compareTo).orElse(null);
    }

    private String lastCycleEndLabel(BreedingAnimal animal) {
        if (animal.getCurrentCycleId() == null) {
            List<BreedingCycle> cycles = cycleMapper.selectList(Wrappers.<BreedingCycle>lambdaQuery()
                    .eq(BreedingCycle::getAnimalId, animal.getId())
                    .orderByDesc(BreedingCycle::getFirstMatingDate));
            if (!cycles.isEmpty() && cycles.get(0).getEndReason() != null) {
                return switch (cycles.get(0).getEndReason()) {
                    case "NEGATIVE" -> "上次妊检日期";
                    case "NO_LIVE" -> "上次产仔日期";
                    default -> "上次断奶日期";
                };
            }
        }
        return "上次断奶日期";
    }

    private static Counts validateCounts(Integer totalBorn, Integer liveBorn, Integer stillborn) {
        if (totalBorn == null) {
            throw BreedingErrors.invalid("total_born", "请填写总产仔数");
        }
        if (totalBorn < 1) {
            throw BreedingErrors.invalid("total_born", "总产仔数至少为 1 头");
        }
        if (totalBorn > 50) {
            throw BreedingErrors.invalid("total_born", "总产仔数不能超过 50 头");
        }
        if (liveBorn == null) {
            throw BreedingErrors.invalid("live_born", "请填写活仔数");
        }
        if (liveBorn < 0 || liveBorn > totalBorn) {
            throw BreedingErrors.invalid("live_born", "活仔数不能多于总产仔 " + totalBorn + " 头");
        }
        if (stillborn == null) {
            throw BreedingErrors.invalid("stillborn", "请填写死胎数");
        }
        if (stillborn < 0 || stillborn > totalBorn) {
            throw BreedingErrors.invalid("stillborn", "死胎数不能多于总产仔 " + totalBorn + " 头");
        }
        if (liveBorn + stillborn != totalBorn) {
            throw BreedingErrors.invalid("live_born", "活仔 " + liveBorn + " 头 + 死胎 " + stillborn
                    + " 头 = " + (liveBorn + stillborn) + " 头，与总产仔 " + totalBorn + " 头不一致");
        }
        return new Counts(totalBorn, liveBorn, stillborn);
    }

    private Long offspringBreed(Long requested, Long motherBreedId) {
        Long breedId = requested;
        if (breedId == null) {
            BreedQueryService.BreedInfo mother = support.breed(motherBreedId);
            breedId = mother == null ? null : mother.offspringBreedId();
            if (breedId == null) {
                breedId = motherBreedId;
            }
        }
        if (breedId == null) {
            return null;
        }
        BreedQueryService.BreedInfo breed = support.breed(breedId);
        if (breed == null || !breed.enabled()) {
            throw BreedingErrors.invalid("offspring_breed_id", "请选择后代品种");
        }
        CategoryInfo motherCategory = support.categoryOfBreed(motherBreedId);
        CategoryInfo offspringCategory = support.categoryOfBreed(breedId);
        if (requested != null && motherCategory != null && offspringCategory != null
                && !motherCategory.id().equals(offspringCategory.id())) {
            throw BreedingErrors.invalid("offspring_breed_id", "后代品种需与母本同品类");
        }
        return breedId;
    }

    private void requirePen(Long penId, String message) {
        if (penId == null) {
            throw BreedingErrors.invalid("target_pen_id", message);
        }
        PenInfo pen = support.pen(penId);
        if (pen == null || !pen.enabled() || !support.farmId().equals(pen.farmId())) {
            throw BreedingErrors.invalid("target_pen_id", message);
        }
    }

    private UserBriefVO registrant(Long userId) {
        return support.user(userId);
    }

    private void log(BizType bizType, Long bizId, String opAction, Object after) {
        FarmContext.FarmMember viewer = FarmContext.get();
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(UserContext.requireUserId())
                .operatorRole(viewer == null ? null : viewer.role().name())
                .opType("RECORD_EDIT")
                .opAction(opAction)
                .bizType(bizType.name())
                .bizId(bizId)
                .farmId(support.farmId())
                .after(after)
                .build());
    }

    private record Counts(int totalBorn, int liveBorn, int stillborn) {
    }
}
