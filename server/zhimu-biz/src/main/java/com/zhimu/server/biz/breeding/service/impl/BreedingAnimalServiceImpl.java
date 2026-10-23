package com.zhimu.server.biz.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.breeding.convert.BreedingAnimalConvert;
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
import com.zhimu.server.biz.breeding.query.BreedingAnimalQuery;
import com.zhimu.server.biz.breeding.query.BreedingOptionQuery;
import com.zhimu.server.biz.breeding.request.AnimalExitRequest;
import com.zhimu.server.biz.breeding.request.BreedingAnimalRequest;
import com.zhimu.server.biz.breeding.service.BreedingAnimalService;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingDates;
import com.zhimu.server.biz.breeding.support.BreedingErrors;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.support.BreedingValidations;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalDetailVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalItemVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalOptionVO;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.RegisterContextVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** API-B02、B04–B09、B11。 */
@Service
@RequiredArgsConstructor
public class BreedingAnimalServiceImpl implements BreedingAnimalService {

    private static final Map<String, Integer> STATUS_ORDER = Map.of(
            BreedingCodes.STATUS_PREGNANT, 0,
            BreedingCodes.STATUS_LACTATING, 1,
            BreedingCodes.STATUS_MATED, 2,
            BreedingCodes.STATUS_EMPTY, 3);

    private final BreedingAnimalMapper animalMapper;
    private final BreedingCycleMapper cycleMapper;
    private final MatingRecordMapper matingMapper;
    private final PregnancyCheckMapper checkMapper;
    private final FarrowingRecordMapper farrowingMapper;
    private final WeaningRecordMapper weaningMapper;
    private final BreedingSupport support;
    private final BreedingAnimalConvert convert;
    private final BreedingRecordConvert recordConvert;
    private final PenQueryService penQueryService;
    private final OperationLogRecorder operationLogRecorder;

    // ===== 查询 =====

    @Override
    public PageVO<BreedingAnimalItemVO> list(BreedingAnimalQuery query) {
        String status = query.statusOrDefault();
        if (!List.of("ALL", "EMPTY", "MATED", "PREGNANT", "LACTATING", "EXITED").contains(status)) {
            throw BreedingErrors.invalid("status", "筛选条件不正确");
        }
        String keyword = query.keywordOrNull();
        if (keyword != null && BreedingValidations.length(keyword) > 20) {
            throw BreedingErrors.invalid("keyword", "搜索内容不能超过 20 个字");
        }
        List<BreedingAnimal> all = animalMapper.selectList(baseQuery(keyword, query.getPenId()));
        Map<String, Integer> tabCounts = new LinkedHashMap<>();
        tabCounts.put("ALL", 0);
        tabCounts.put("EMPTY", 0);
        tabCounts.put("MATED", 0);
        tabCounts.put("PREGNANT", 0);
        tabCounts.put("LACTATING", 0);
        tabCounts.put("EXITED", 0);
        for (BreedingAnimal animal : all) {
            if (BreedingCodes.exited(animal.getStatus())) {
                tabCounts.merge("EXITED", 1, Integer::sum);
            } else {
                tabCounts.merge("ALL", 1, Integer::sum);
                if (STATUS_ORDER.containsKey(animal.getStatus())) {
                    tabCounts.merge(animal.getStatus(), 1, Integer::sum);
                }
            }
        }
        List<BreedingAnimal> filtered = new ArrayList<>();
        for (BreedingAnimal animal : all) {
            if (matchesFilter(animal, status)) {
                filtered.add(animal);
            }
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<BreedingAnimal> pageRows = filtered.subList(from, to);
        Map<Long, BreedingCycle> cycles = cyclesOf(pageRows);
        Map<Long, FarrowingRecord> latestFarrow = latestFarrowings(idsOf(pageRows));
        Map<Long, PenInfo> pens = support.pens(penIdsOf(pageRows));
        Map<Long, BreedQueryService.BreedInfo> breeds = support.breeds(breedIdsOf(pageRows));
        List<Row> rows = new ArrayList<>();
        for (BreedingAnimal animal : pageRows) {
            BreedingCycle cycle = animal.getCurrentCycleId() == null ? null : cycles.get(animal.getCurrentCycleId());
            PenInfo pen = pens.get(animal.getPenId());
            BreedQueryService.BreedInfo breed = breeds.get(animal.getBreedId());
            BreedingAnimalItemVO vo = convert.item(animal, breed == null ? null : breed.name(),
                    pen == null ? null : pen.penName(), cycle, latestFarrow.get(animal.getId()));
            rows.add(new Row(vo, pen, animal));
        }
        rows.sort(activeComparator(status));
        List<BreedingAnimalItemVO> list = rows.stream().map(Row::vo).toList();
        return new PageVO<>(list, filtered.size(), page, size).withTabCounts(tabCounts);
    }

    @Override
    public PageVO<BreedingAnimalOptionVO> options(BreedingOptionQuery query) {
        String purpose = query.getPurpose();
        if (!List.of("MATING", "FARROWING", "WEANING").contains(purpose)) {
            throw BreedingErrors.invalid("purpose", "登记类型不正确");
        }
        String keyword = query.keywordOrNull();
        List<String> statuses = switch (purpose) {
            case "MATING" -> List.of(BreedingCodes.STATUS_EMPTY, BreedingCodes.STATUS_MATED);
            case "FARROWING" -> List.of(BreedingCodes.STATUS_PREGNANT);
            default -> List.of(BreedingCodes.STATUS_LACTATING);
        };
        List<BreedingAnimal> animals = animalMapper.selectList(Wrappers.<BreedingAnimal>lambdaQuery()
                .in(BreedingAnimal::getStatus, statuses)
                .like(keyword != null, BreedingAnimal::getEarTag, keyword)
                .orderByAsc(BreedingAnimal::getEarTag));
        Map<Long, BreedingCycle> cycles = cyclesOf(animals);
        Map<Long, FarrowingRecord> latestFarrow = latestFarrowings(idsOf(animals));
        Map<Long, PenInfo> pens = support.pens(penIdsOf(animals));
        Map<Long, BreedQueryService.BreedInfo> breeds = support.breeds(breedIdsOf(animals));
        List<BreedingAnimalOptionVO> list = new ArrayList<>();
        for (BreedingAnimal animal : animals) {
            BreedingCycle cycle = animal.getCurrentCycleId() == null ? null : cycles.get(animal.getCurrentCycleId());
            PenInfo pen = pens.get(animal.getPenId());
            BreedQueryService.BreedInfo breed = breeds.get(animal.getBreedId());
            list.add(convert.option(animal, breed == null ? null : breed.name(),
                    pen == null ? null : pen.penName(), cycle, latestFarrow.get(animal.getId()), purpose,
                    breed == null ? null : breed.gestationDays()));
        }
        sortOptions(list, purpose);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, list.size());
        int to = Math.min(from + size, list.size());
        return new PageVO<>(list.subList(from, to), list.size(), page, size);
    }

    @Override
    public BreedingAnimalDetailVO detail(Long animalId) {
        return detailOf(support.requireAnimal(animalId));
    }

    @Override
    public PageVO<BreedingRecordVO> records(Long animalId, int page, int size) {
        BreedingAnimal animal = support.requireAnimal(animalId);
        List<RecordRow> rows = new ArrayList<>();
        List<MatingRecord> matings = matingMapper.selectList(Wrappers.<MatingRecord>lambdaQuery()
                .eq(MatingRecord::getAnimalId, animalId));
        List<PregnancyCheck> checks = checkMapper.selectList(Wrappers.<PregnancyCheck>lambdaQuery()
                .eq(PregnancyCheck::getAnimalId, animalId));
        List<FarrowingRecord> farrowings = farrowingMapper.selectList(Wrappers.<FarrowingRecord>lambdaQuery()
                .eq(FarrowingRecord::getAnimalId, animalId));
        List<WeaningRecord> weanings = weaningMapper.selectList(Wrappers.<WeaningRecord>lambdaQuery()
                .eq(WeaningRecord::getAnimalId, animalId));
        Set<Long> userIds = new HashSet<>();
        matings.forEach(r -> userIds.add(r.getCreatedBy()));
        checks.forEach(r -> userIds.add(r.getCreatedBy()));
        farrowings.forEach(r -> userIds.add(r.getCreatedBy()));
        weanings.forEach(r -> userIds.add(r.getCreatedBy()));
        if (BreedingCodes.exited(animal.getStatus())) {
            userIds.add(animal.getExitBy());
        }
        Map<Long, UserBriefVO> users = support.users(userIds);
        for (MatingRecord record : matings) {
            rows.add(new RecordRow(record.getMatingDate(), record.getCreatedAt(),
                    recordConvert.mating(record, users.get(record.getCreatedBy()))));
        }
        for (PregnancyCheck record : checks) {
            rows.add(new RecordRow(record.getCheckDate(), record.getCreatedAt(),
                    recordConvert.check(record, users.get(record.getCreatedBy()))));
        }
        for (FarrowingRecord record : farrowings) {
            rows.add(new RecordRow(record.getFarrowDate(), record.getCreatedAt(),
                    recordConvert.farrowing(record, support.batchNo(record.getBatchId()),
                            users.get(record.getCreatedBy()))));
        }
        for (WeaningRecord record : weanings) {
            rows.add(new RecordRow(record.getWeanDate(), record.getCreatedAt(),
                    recordConvert.weaning(record, users.get(record.getCreatedBy()))));
        }
        if (BreedingCodes.exited(animal.getStatus())) {
            rows.add(new RecordRow(animal.getExitDate(), animal.getExitAt(),
                    recordConvert.exit(animal, users.get(animal.getExitBy()))));
        }
        rows.sort(Comparator
                .comparing((RecordRow row) -> row.date(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> row.createdAt(), Comparator.nullsLast(Comparator.reverseOrder())));
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        List<BreedingRecordVO> list = rows.subList(from, to).stream().map(RecordRow::vo).toList();
        return new PageVO<>(list, rows.size(), safePage, safeSize);
    }

    @Override
    public RegisterContextVO registerContext(Long animalId, String type) {
        if (!List.of("MATING", "PREG_CHECK", "FARROWING", "WEANING").contains(type)) {
            throw BreedingErrors.invalid("type", "登记类型不正确");
        }
        BreedingAnimal animal = support.requireAnimal(animalId);
        BreedingCycle cycle = support.currentCycle(animal);
        RegisterContextVO vo = new RegisterContextVO();
        vo.setType(type);
        BreedQueryService.BreedInfo breed = support.breed(animal.getBreedId());
        PenInfo pen = support.pen(animal.getPenId());
        Integer gestationDays = breed == null ? null : breed.gestationDays();
        vo.setBreedName(breed == null ? null : breed.name());
        vo.setAnimal(convert.option(animal, vo.getBreedName(), pen == null ? null : pen.penName(), cycle,
                latestFarrow(animalId), type, gestationDays));
        LocalDate today = BreedingDates.today();
        switch (type) {
            case "MATING" -> {
                if (!List.of(BreedingCodes.STATUS_EMPTY, BreedingCodes.STATUS_MATED).contains(animal.getStatus())) {
                    throw stateChanged(animal);
                }
                vo.setGestationDays(gestationDays);
                LocalDate min = animal.getLastCycleEndDate() != null ? animal.getLastCycleEndDate()
                        : animal.getBirthDate();
                if (BreedingCodes.STATUS_MATED.equals(animal.getStatus()) && cycle != null) {
                    min = cycle.getFirstMatingDate();
                    vo.setCycleId(cycle.getId());
                    vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
                    vo.setDaysSinceMating(BreedingDates.daysBetween(cycle.getFirstMatingDate(), today));
                    vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
                    vo.setDaysToDue(BreedingDates.daysBetween(today, cycle.getExpectedDueDate()));
                }
                vo.setMinMatingDate(BeijingTime.format(min));
            }
            case "PREG_CHECK" -> {
                if (!BreedingCodes.STATUS_MATED.equals(animal.getStatus()) || cycle == null) {
                    throw stateChanged(animal);
                }
                vo.setCycleId(cycle.getId());
                vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
                vo.setDaysSinceMating(BreedingDates.daysBetween(cycle.getFirstMatingDate(), today));
                vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
                vo.setDaysToDue(BreedingDates.daysBetween(today, cycle.getExpectedDueDate()));
            }
            case "FARROWING" -> {
                if (!BreedingCodes.STATUS_PREGNANT.equals(animal.getStatus()) || cycle == null) {
                    throw stateChanged(animal);
                }
                vo.setCycleId(cycle.getId());
                vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
                vo.setDaysSinceMating(BreedingDates.daysBetween(cycle.getFirstMatingDate(), today));
                vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
                vo.setDaysToDue(BreedingDates.daysBetween(today, cycle.getExpectedDueDate()));
                RegisterContextVO.DefaultPen defaultPen = new RegisterContextVO.DefaultPen();
                defaultPen.setPenId(animal.getPenId() == null ? 0 : animal.getPenId());
                defaultPen.setPenName(pen == null ? null : pen.penName());
                defaultPen.setCapacity(pen == null || pen.capacity() == null ? 0 : pen.capacity());
                defaultPen.setCurrentCount(support.penCurrentCount(animal.getPenId()));
                defaultPen.setUnit(support.unitOfBreed(animal.getBreedId()));
                vo.setDefaultPen(defaultPen);
                Long offspringBreedId = breed == null ? null : breed.offspringBreedId();
                if (offspringBreedId == null && breed != null) {
                    offspringBreedId = breed.id();
                }
                if (offspringBreedId != null) {
                    RegisterContextVO.OffspringBreed offspring = new RegisterContextVO.OffspringBreed();
                    offspring.setBreedId(offspringBreedId);
                    offspring.setBreedName(support.breedName(offspringBreedId));
                    vo.setOffspringBreed(offspring);
                }
            }
            default -> {
                if (!BreedingCodes.STATUS_LACTATING.equals(animal.getStatus()) || cycle == null) {
                    throw stateChanged(animal);
                }
                FarrowingRecord farrowing = latestFarrow(animalId);
                if (farrowing != null) {
                    vo.setCycleId(cycle.getId());
                    vo.setFarrowingId(farrowing.getId());
                    vo.setFarrowDate(BeijingTime.format(farrowing.getFarrowDate()));
                    vo.setNursingDays(BreedingDates.daysBetween(farrowing.getFarrowDate(), today));
                    vo.setLiveBorn(farrowing.getLiveBorn());
                }
            }
        }
        return vo;
    }

    // ===== 档案写入 =====

    @Override
    @Transactional
    public BreedingAnimalDetailVO create(BreedingAnimalRequest request, String idempotencyKey) {
        Long farmId = support.farmId();
        Long userId = UserContext.requireUserId();
        String earTag = BreedingValidations.requireEarTag(request.getEarTag());
        if (animalMapper.selectCount(Wrappers.<BreedingAnimal>lambdaQuery()
                .eq(BreedingAnimal::getEarTag, earTag)) > 0) {
            throw BreedingErrors.earTagDuplicate(earTag);
        }
        BreedQueryService.BreedInfo breed = requireGestatingBreed(request.getBreedId());
        String source = requireSource(request.getSource());
        Long penId = requireEnabledPen(request.getPenId());
        LocalDate birthDate = BreedingValidations.optionalDate("birth_date", request.getBirthDate(),
                "出生日期格式不正确");
        BreedingValidations.notFuture("birth_date", birthDate, "出生日期不能晚于今天");
        int initialParity = request.getInitialParity() == null ? 0 : request.getInitialParity();
        if (initialParity < 0 || initialParity > 30) {
            throw BreedingErrors.invalid("initial_parity", "建档时胎次需为 0–30");
        }
        BreedingAnimal animal = new BreedingAnimal();
        animal.setFarmId(farmId);
        animal.setEarTag(earTag);
        animal.setBreedId(breed.id());
        animal.setBirthDate(birthDate);
        animal.setSource(source);
        animal.setPenId(penId);
        animal.setFilingDate(BreedingDates.today());
        animal.setInitialParity(initialParity);
        animal.setParity(initialParity);
        animal.setStatus(BreedingCodes.STATUS_EMPTY);
        animal.setVersion(1);
        animal.setUpdatedBy(userId);
        animalMapper.insert(animal);
        log(BizType.BREEDING_ANIMAL, animal.getId(), null, Map.of("ear_tag", earTag, "status", animal.getStatus()));
        return detailOf(animal);
    }

    @Override
    @Transactional
    public BreedingAnimalDetailVO update(Long animalId, BreedingAnimalRequest request) {
        BreedingAnimal animal = support.requireAnimal(animalId);
        if (BreedingCodes.exited(animal.getStatus())) {
            throw BreedingErrors.stateChanged(animal.getEarTag() + " 已"
                    + ("DEAD".equals(animal.getStatus()) ? "死亡" : "淘汰") + "，档案只能查看");
        }
        BreedingValidations.checkVersion(request.getVersion(), animal.getVersion());
        Long userId = UserContext.requireUserId();
        String oldEarTag = animal.getEarTag();
        Integer oldInitial = animal.getInitialParity() == null ? 0 : animal.getInitialParity();
        String earTag = request.getEarTag() == null ? animal.getEarTag()
                : BreedingValidations.requireEarTag(request.getEarTag());
        if (!Objects.equals(earTag, animal.getEarTag()) && animalMapper.selectCount(
                Wrappers.<BreedingAnimal>lambdaQuery()
                        .eq(BreedingAnimal::getEarTag, earTag)
                        .ne(BreedingAnimal::getId, animalId)) > 0) {
            throw BreedingErrors.earTagDuplicate(earTag);
        }
        Long breedId = animal.getBreedId();
        if (request.getBreedId() != null) {
            breedId = requireGestatingBreed(request.getBreedId()).id();
        }
        String source = request.getSource() == null ? animal.getSource() : requireSource(request.getSource());
        Long penId = request.getPenId() == null ? animal.getPenId() : requireEnabledPen(request.getPenId());
        LocalDate birthDate = animal.getBirthDate();
        if (request.getBirthDate() != null) {
            birthDate = BreedingValidations.optionalDate("birth_date", request.getBirthDate(), "出生日期格式不正确");
            BreedingValidations.notFuture("birth_date", birthDate, "出生日期不能晚于今天");
        }
        int newInitial = request.getInitialParity() == null ? oldInitial : request.getInitialParity();
        if (newInitial < 0 || newInitial > 30) {
            throw BreedingErrors.invalid("initial_parity", "建档时胎次需为 0–30");
        }
        int parity = Math.max(0, (animal.getParity() == null ? 0 : animal.getParity()) + (newInitial - oldInitial));
        animal.setEarTag(earTag);
        animal.setBreedId(breedId);
        animal.setBirthDate(birthDate);
        animal.setSource(source);
        animal.setPenId(penId);
        animal.setInitialParity(newInitial);
        animal.setParity(parity);
        animal.setUpdatedBy(userId);
        if (animalMapper.updateById(animal) == 0) {
            throw BreedingErrors.versionConflict();
        }
        log(BizType.BREEDING_ANIMAL, animal.getId(), Map.of("ear_tag", oldEarTag),
                Map.of("ear_tag", earTag, "pen_id", penId));
        return detailOf(animal);
    }

    @Override
    @Transactional
    public BreedingAnimalDetailVO exit(Long animalId, AnimalExitRequest request, String idempotencyKey) {
        BreedingAnimal animal = support.requireAnimal(animalId);
        if (BreedingCodes.exited(animal.getStatus())) {
            throw BreedingErrors.stateChanged(animal.getEarTag() + " 已"
                    + ("DEAD".equals(animal.getStatus()) ? "死亡" : "淘汰") + "，不能重复登记");
        }
        BreedingValidations.checkVersion(request.getVersion(), animal.getVersion());
        String exitType = request.getExitType() == null ? "CULL" : request.getExitType();
        if (!List.of("CULL", "DEATH").contains(exitType)) {
            throw BreedingErrors.invalid("exit_type", "请选择淘汰或死亡");
        }
        LocalDate exitDate = BreedingValidations.date("exit_date", request.getExitDate(), "请选择日期");
        BreedingValidations.notFuture("exit_date", exitDate, "日期不能晚于今天");
        LocalDate earliest = animal.getFilingDate() == null ? null : animal.getFilingDate();
        LocalDate latestRecord = latestRecordDate(animalId);
        if (latestRecord != null && (earliest == null || latestRecord.isAfter(earliest))) {
            earliest = latestRecord;
        }
        if (earliest != null && exitDate.isBefore(earliest)) {
            throw BreedingErrors.invalid("exit_date", "日期不能早于最近一条繁育记录日期 "
                    + BreedingCodes.monthDay(earliest));
        }
        String reason = BreedingValidations.requireText("reason", request.getReason(),
                "请填写原因", 50, "最多 50 字");
        Long userId = UserContext.requireUserId();
        animal.setStatusBeforeExit(animal.getStatus());
        animal.setStatus("DEATH".equals(exitType) ? BreedingCodes.STATUS_DEAD : BreedingCodes.STATUS_CULLED);
        animal.setExitType(exitType);
        animal.setExitDate(exitDate);
        animal.setExitReason(reason);
        animal.setExitBy(userId);
        animal.setExitAt(BeijingTime.now());
        animal.setUpdatedBy(userId);
        if (animalMapper.updateById(animal) == 0) {
            throw BreedingErrors.versionConflict();
        }
        if (animal.getCurrentCycleId() != null) {
            BreedingCycle cycle = cycleMapper.selectById(animal.getCurrentCycleId());
            if (cycle != null && cycle.getEndDate() == null) {
                cycle.setEndDate(exitDate);
                cycle.setEndReason("EXIT");
                cycle.setUpdatedBy(userId);
                cycleMapper.updateById(cycle);
            }
        }
        log(BizType.BREEDING_EXIT, animal.getId(), Map.of("status", animal.getStatusBeforeExit()),
                Map.of("status", animal.getStatus(), "exit_type", exitType), reason);
        return detailOf(animal);
    }

    // ===== 共用 =====

    private BreedingAnimalDetailVO detailOf(BreedingAnimal animal) {
        BreedingCycle cycle = support.currentCycle(animal);
        List<FarrowingRecord> farrowings = farrowingsOf(animal.getId());
        FarrowingRecord latestFarrow = farrowings.isEmpty() ? null : farrowings.get(0);
        WeaningRecord latestWean = latestWeaning(animal.getId());
        BreedQueryService.BreedInfo breed = support.breed(animal.getBreedId());
        PenInfo pen = support.pen(animal.getPenId());
        CategoryInfo category = support.categoryOfBreed(animal.getBreedId());
        String species = category == null ? null : category.species();
        return convert.detail(animal, breed == null ? null : breed.name(),
                pen == null ? null : pen.penName(), BreedingCodes.animalLabel(species), cycle, latestFarrow,
                farrowings, latestWean);
    }

    private List<FarrowingRecord> farrowingsOf(Long animalId) {
        return farrowingMapper.selectList(Wrappers.<FarrowingRecord>lambdaQuery()
                .eq(FarrowingRecord::getAnimalId, animalId)
                .orderByDesc(FarrowingRecord::getFarrowDate)
                .orderByDesc(FarrowingRecord::getId));
    }

    private FarrowingRecord latestFarrow(Long animalId) {
        List<FarrowingRecord> rows = farrowingMapper.selectList(Wrappers.<FarrowingRecord>lambdaQuery()
                .eq(FarrowingRecord::getAnimalId, animalId)
                .orderByDesc(FarrowingRecord::getFarrowDate)
                .orderByDesc(FarrowingRecord::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private WeaningRecord latestWeaning(Long animalId) {
        List<WeaningRecord> rows = weaningMapper.selectList(Wrappers.<WeaningRecord>lambdaQuery()
                .eq(WeaningRecord::getAnimalId, animalId)
                .orderByDesc(WeaningRecord::getWeanDate)
                .orderByDesc(WeaningRecord::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<Long, BreedingCycle> cyclesOf(List<BreedingAnimal> animals) {
        List<Long> cycleIds = animals.stream()
                .map(BreedingAnimal::getCurrentCycleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (cycleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BreedingCycle> map = new HashMap<>();
        for (BreedingCycle cycle : cycleMapper.selectBatchIds(cycleIds)) {
            map.put(cycle.getId(), cycle);
        }
        return map;
    }

    private Map<Long, FarrowingRecord> latestFarrowings(List<Long> animalIds) {
        if (animalIds.isEmpty()) {
            return Map.of();
        }
        List<FarrowingRecord> rows = farrowingMapper.selectList(Wrappers.<FarrowingRecord>lambdaQuery()
                .in(FarrowingRecord::getAnimalId, animalIds)
                .orderByDesc(FarrowingRecord::getFarrowDate)
                .orderByDesc(FarrowingRecord::getId));
        Map<Long, FarrowingRecord> latest = new LinkedHashMap<>();
        for (FarrowingRecord row : rows) {
            latest.putIfAbsent(row.getAnimalId(), row);
        }
        return latest;
    }

    private LocalDate latestRecordDate(Long animalId) {
        List<LocalDate> dates = new ArrayList<>();
        matingMapper.selectList(Wrappers.<MatingRecord>lambdaQuery().eq(MatingRecord::getAnimalId, animalId))
                .forEach(r -> dates.add(r.getMatingDate()));
        checkMapper.selectList(Wrappers.<PregnancyCheck>lambdaQuery().eq(PregnancyCheck::getAnimalId, animalId))
                .forEach(r -> dates.add(r.getCheckDate()));
        farrowingMapper.selectList(Wrappers.<FarrowingRecord>lambdaQuery().eq(FarrowingRecord::getAnimalId, animalId))
                .forEach(r -> dates.add(r.getFarrowDate()));
        weaningMapper.selectList(Wrappers.<WeaningRecord>lambdaQuery().eq(WeaningRecord::getAnimalId, animalId))
                .forEach(r -> dates.add(r.getWeanDate()));
        return dates.stream().filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BreedingAnimal> baseQuery(
            String keyword, Long penId) {
        return Wrappers.<BreedingAnimal>lambdaQuery()
                .like(keyword != null, BreedingAnimal::getEarTag, keyword)
                .eq(penId != null, BreedingAnimal::getPenId, penId)
                .orderByAsc(BreedingAnimal::getEarTag);
    }

    private static boolean matchesFilter(BreedingAnimal animal, String status) {
        if ("ALL".equals(status)) {
            return !BreedingCodes.exited(animal.getStatus());
        }
        if ("EXITED".equals(status)) {
            return BreedingCodes.exited(animal.getStatus());
        }
        return status.equals(animal.getStatus());
    }

    private Comparator<Row> activeComparator(String status) {
        if ("EXITED".equals(status)) {
            return Comparator.comparing((Row row) -> row.animal().getExitDate(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator
                .comparingInt((Row row) -> {
                    Integer days = row.vo().getDaysToDue();
                    return days != null && days <= 3 ? 0 : 1;
                })
                .thenComparingInt(row -> row.pen() == null ? 9 : "FARROWING".equals(row.pen().penType()) ? 0
                        : "SOW".equals(row.pen().penType()) ? 1 : 2)
                .thenComparing(row -> row.pen() == null ? "" : row.pen().penCode(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(row -> STATUS_ORDER.getOrDefault(row.animal().getStatus(), 9))
                .thenComparing(row -> row.vo().getExpectedDueDate(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> row.vo().getEarTag(), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static void sortOptions(List<BreedingAnimalOptionVO> list, String purpose) {
        Comparator<BreedingAnimalOptionVO> comparator = switch (purpose) {
            case "MATING" -> Comparator
                    .comparingInt((BreedingAnimalOptionVO vo) -> "EMPTY".equals(vo.getStatus()) ? 0 : 1)
                    .thenComparing(BreedingAnimalOptionVO::getLastWeanDate,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(BreedingAnimalOptionVO::getFirstMatingDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BreedingAnimalOptionVO::getEarTag, Comparator.nullsLast(Comparator.naturalOrder()));
            case "FARROWING" -> Comparator
                    .comparing(BreedingAnimalOptionVO::getExpectedDueDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BreedingAnimalOptionVO::getEarTag, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator
                    .comparing(BreedingAnimalOptionVO::getLastFarrowDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BreedingAnimalOptionVO::getEarTag, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        list.sort(comparator);
    }

    private static List<Long> idsOf(List<BreedingAnimal> animals) {
        return animals.stream().map(BreedingAnimal::getId).filter(Objects::nonNull).toList();
    }

    private static List<Long> penIdsOf(List<BreedingAnimal> animals) {
        return animals.stream().map(BreedingAnimal::getPenId).filter(Objects::nonNull).distinct().toList();
    }

    private static List<Long> breedIdsOf(List<BreedingAnimal> animals) {
        return animals.stream().map(BreedingAnimal::getBreedId).filter(Objects::nonNull).distinct().toList();
    }

    private BreedQueryService.BreedInfo requireGestatingBreed(Long breedId) {
        if (breedId == null) {
            throw BreedingErrors.invalid("breed_id", "请选择品种");
        }
        BreedQueryService.BreedInfo breed = support.breed(breedId);
        if (breed == null || !breed.enabled()) {
            throw BreedingErrors.invalid("breed_id", "请选择品种");
        }
        CategoryInfo category = support.categoryOfBreed(breedId);
        if (breed.gestationDays() == null || (category != null && "POULTRY".equals(category.species()))) {
            throw BreedingErrors.invalid("breed_id", "该品种没有设置妊娠期，暂不能建档");
        }
        return breed;
    }

    private static String requireSource(String source) {
        if (source == null || !List.of("PURCHASED", "SELF_BRED").contains(source)) {
            throw BreedingErrors.invalid("source", "请选择来源");
        }
        return source;
    }

    private Long requireEnabledPen(Long penId) {
        if (penId == null) {
            throw BreedingErrors.invalid("pen_id", "请选择栏舍");
        }
        List<PenInfo> enabled = penQueryService.listPens(support.farmId(), false);
        boolean found = enabled != null && enabled.stream().anyMatch(pen -> penId.equals(pen.penId()));
        if (!found) {
            throw BreedingErrors.invalid("pen_id", "请选择栏舍");
        }
        return penId;
    }

    private static BizException stateChanged(BreedingAnimal animal) {
        return BreedingErrors.stateChanged(animal.getEarTag() + " 的状态已变化，请刷新后再登记");
    }

    private void log(BizType bizType, Long bizId, Object before, Object after) {
        log(bizType, bizId, before, after, null);
    }

    private void log(BizType bizType, Long bizId, Object before, Object after, String reason) {
        FarmContext.FarmMember viewer = FarmContext.get();
        String opAction = bizType == BizType.BREEDING_EXIT ? "EXIT" : before == null ? "CREATE" : "UPDATE";
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(UserContext.requireUserId())
                .operatorRole(viewer == null ? null : viewer.role().name())
                .opType("RECORD_EDIT")
                .opAction(opAction)
                .bizType(bizType.name())
                .bizId(bizId)
                .farmId(support.farmId())
                .reason(reason)
                .before(before)
                .after(after)
                .build());
    }

    private record Row(BreedingAnimalItemVO vo, PenInfo pen, BreedingAnimal animal) {
    }

    private record RecordRow(LocalDate date, LocalDateTime createdAt, BreedingRecordVO vo) {
    }
}
