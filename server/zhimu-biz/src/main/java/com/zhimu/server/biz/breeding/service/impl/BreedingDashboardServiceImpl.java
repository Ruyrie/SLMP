package com.zhimu.server.biz.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.breeding.convert.BreedingRecordConvert;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.entity.MatingRecord;
import com.zhimu.server.biz.breeding.entity.PregnancyCheck;
import com.zhimu.server.biz.breeding.entity.WeaningRecord;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.breeding.mapper.MatingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.PregnancyCheckMapper;
import com.zhimu.server.biz.breeding.mapper.WeaningRecordMapper;
import com.zhimu.server.biz.breeding.query.BreedingDueQuery;
import com.zhimu.server.biz.breeding.service.BreedingDashboardService;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingDates;
import com.zhimu.server.biz.breeding.support.BreedingErrors;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.vo.BreedingOverviewVO;
import com.zhimu.server.biz.breeding.vo.FarrowDueItemVO;
import com.zhimu.server.biz.breeding.vo.HatchDueItemVO;
import com.zhimu.server.biz.breeding.vo.PendingCheckVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.biz.breeding.vo.SubmissionVO;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** API-B01、B14、B22、B23、B32。 */
@Service
@RequiredArgsConstructor
public class BreedingDashboardServiceImpl implements BreedingDashboardService {

    private final BreedingAnimalMapper animalMapper;
    private final BreedingCycleMapper cycleMapper;
    private final MatingRecordMapper matingMapper;
    private final PregnancyCheckMapper checkMapper;
    private final FarrowingRecordMapper farrowingMapper;
    private final WeaningRecordMapper weaningMapper;
    private final IncubationMapper incubationMapper;
    private final BreedingSupport support;
    private final BreedingRecordConvert recordConvert;
    private final PenQueryService penQueryService;
    private final FarmQueryService farmQueryService;
    private final AccountQueryService accountQueryService;

    @Override
    public BreedingOverviewVO overview() {
        FarmContext.FarmMember viewer = support.viewer();
        Long farmId = viewer.farmId();
        List<BreedingAnimal> animals = animalMapper.selectList(Wrappers.<BreedingAnimal>lambdaQuery()
                .orderByAsc(BreedingAnimal::getId));
        List<Incubation> incubations = incubationMapper.selectList(Wrappers.<Incubation>lambdaQuery());
        List<BreedingCycle> cycles = cyclesOf(animals);
        Map<Long, BreedingCycle> cycleMap = new HashMap<>();
        cycles.forEach(cycle -> cycleMap.put(cycle.getId(), cycle));
        boolean farmerView = viewer.isManagerView();
        Set<Long> penScope = viewer.isStaff() && viewer.penIds() != null ? viewer.penIds() : null;

        BreedingOverviewVO vo = new BreedingOverviewVO();
        String species = resolveSpecies(animals, incubations);
        boolean poultry = "POULTRY".equals(species);
        vo.setFarmKind(poultry ? "POULTRY" : "LIVESTOCK");
        vo.setFarmKindText(poultry ? "禽类" : "畜类");
        vo.setAnimalLabel(BreedingCodes.animalLabel(species));
        vo.setCountUnit(poultry ? "BIRD" : "HEAD");
        vo.setCountUnitText(poultry ? "只" : "头");
        vo.setHasIncubation(!incubations.isEmpty());

        List<BreedingAnimal> pregnant = animals.stream()
                .filter(animal -> BreedingCodes.STATUS_PREGNANT.equals(animal.getStatus()))
                .toList();
        int farrowCount = (int) pregnant.stream()
                .filter(animal -> penScope == null || penScope.contains(animal.getPenId()))
                .count();
        List<Incubation> dueIncubations = incubations.stream()
                .filter(inc -> BreedingCodes.INCUBATION_SET.equals(inc.getStatus())
                        || BreedingCodes.INCUBATION_CANDLED.equals(inc.getStatus()))
                .toList();
        int hatchCount = (int) dueIncubations.stream()
                .filter(inc -> penScope == null || penScope.contains(inc.getPenId()))
                .count();
        vo.setFarrowDueCount(farrowCount);
        vo.setHatchDueCount(hatchCount);

        if (farmerView) {
            BreedingOverviewVO.Counts counts = new BreedingOverviewVO.Counts();
            counts.setEmpty(0);
            counts.setMated(0);
            counts.setPregnant(0);
            counts.setLactating(0);
            counts.setExited(0);
            for (BreedingAnimal animal : animals) {
                switch (animal.getStatus()) {
                    case BreedingCodes.STATUS_EMPTY -> counts.setEmpty(counts.getEmpty() + 1);
                    case BreedingCodes.STATUS_MATED -> counts.setMated(counts.getMated() + 1);
                    case BreedingCodes.STATUS_PREGNANT -> counts.setPregnant(counts.getPregnant() + 1);
                    case BreedingCodes.STATUS_LACTATING -> counts.setLactating(counts.getLactating() + 1);
                    case BreedingCodes.STATUS_CULLED, BreedingCodes.STATUS_DEAD ->
                            counts.setExited(counts.getExited() + 1);
                    default -> {
                    }
                }
            }
            counts.setAll(counts.getEmpty() + counts.getMated() + counts.getPregnant()
                    + counts.getLactating());
            vo.setCounts(counts);

            LocalDate today = BreedingDates.today();
            List<BreedingOverviewVO.DueSoonItem> dueSoon = new ArrayList<>();
            for (BreedingAnimal animal : pregnant) {
                BreedingCycle cycle = animal.getCurrentCycleId() == null ? null
                        : cycleMap.get(animal.getCurrentCycleId());
                if (cycle == null || cycle.getExpectedDueDate() == null) {
                    continue;
                }
                int days = BreedingDates.daysBetween(today, cycle.getExpectedDueDate());
                if (days > 3) {
                    continue;
                }
                BreedingOverviewVO.DueSoonItem item = new BreedingOverviewVO.DueSoonItem();
                item.setAnimalId(animal.getId());
                item.setEarTag(animal.getEarTag());
                item.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
                item.setDaysToDue(days);
                dueSoon.add(item);
            }
            dueSoon.sort(Comparator.comparingInt(BreedingOverviewVO.DueSoonItem::getDaysToDue)
                    .thenComparing(BreedingOverviewVO.DueSoonItem::getEarTag,
                            Comparator.nullsLast(Comparator.naturalOrder())));
            vo.setDueSoon(dueSoon);
            vo.setDueSoonTotal(dueSoon.size());

            List<PenBriefVO> penOptions = new ArrayList<>();
            List<PenInfo> pens = penQueryService.listPens(farmId, false);
            if (pens != null) {
                pens.stream()
                        .filter(pen -> "FARROWING".equals(pen.penType()) || "SOW".equals(pen.penType()))
                        .sorted(Comparator.comparingInt((PenInfo pen) -> "FARROWING".equals(pen.penType()) ? 0 : 1)
                                .thenComparing(PenInfo::penCode, Comparator.nullsLast(Comparator.naturalOrder())))
                        .forEach(pen -> penOptions.add(PenBriefVO.builder()
                                .penId(pen.penId()).name(pen.penName()).penTypeText(pen.penTypeText()).build()));
            }
            vo.setPenOptions(penOptions);
            vo.setSwitchFarms(switchFarms(farmId, viewer.userId()));
        } else {
            vo.setSwitchFarms(List.of());
        }
        return vo;
    }

    @Override
    public PageVO<PendingCheckVO> pendingChecks(PageQuery query) {
        support.viewer();
        List<BreedingAnimal> animals = animalMapper.selectList(Wrappers.<BreedingAnimal>lambdaQuery()
                .eq(BreedingAnimal::getStatus, BreedingCodes.STATUS_MATED));
        Map<Long, BreedingCycle> cycles = new HashMap<>();
        cycleMapper.selectList(Wrappers.<BreedingCycle>lambdaQuery()
                        .eq(BreedingCycle::getStatus, BreedingCodes.CYCLE_MATED))
                .forEach(cycle -> cycles.put(cycle.getId(), cycle));
        Map<Long, BreedQueryService.BreedInfo> breeds = support.breeds(
                animals.stream().map(BreedingAnimal::getBreedId).filter(Objects::nonNull).distinct().toList());
        LocalDate today = BreedingDates.today();
        List<PendingRow> rows = new ArrayList<>();
        for (BreedingAnimal animal : animals) {
            BreedingCycle cycle = animal.getCurrentCycleId() == null ? null
                    : cycles.get(animal.getCurrentCycleId());
            if (cycle == null || cycle.getCheckDueDate() == null || today.isBefore(cycle.getCheckDueDate())) {
                continue;
            }
            PendingCheckVO vo = new PendingCheckVO();
            vo.setAnimalId(animal.getId());
            vo.setCycleId(cycle.getId());
            vo.setAnimalVersion(animal.getVersion() == null ? 0 : animal.getVersion());
            vo.setParity(animal.getParity() == null ? 0 : animal.getParity());
            vo.setDaysSinceMating(BreedingDates.daysBetween(cycle.getFirstMatingDate(), today));
            vo.setEarTag(animal.getEarTag());
            BreedQueryService.BreedInfo breed = breeds.get(animal.getBreedId());
            vo.setBreedName(breed == null ? null : breed.name());
            vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
            vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
            rows.add(new PendingRow(vo, vo.getDaysSinceMating()));
        }
        rows.sort(Comparator.comparingInt(PendingRow::daysSinceMating).reversed()
                .thenComparing(row -> row.vo().getEarTag(), Comparator.nullsLast(Comparator.naturalOrder())));
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<PendingCheckVO> list = rows.subList(from, to).stream().map(PendingRow::vo).toList();
        return new PageVO<>(list, rows.size(), page, size);
    }

    @Override
    public PageVO<FarrowDueItemVO> farrowDue(BreedingDueQuery query) {
        FarmContext.FarmMember viewer = support.viewer();
        boolean farmerView = viewer.isManagerView();
        Set<Long> penScope = viewer.isStaff() && viewer.penIds() != null ? viewer.penIds() : null;
        String keyword = query.keywordOrNull();
        List<BreedingAnimal> animals = animalMapper.selectList(Wrappers.<BreedingAnimal>lambdaQuery()
                .eq(BreedingAnimal::getStatus, BreedingCodes.STATUS_PREGNANT)
                .like(keyword != null, BreedingAnimal::getEarTag, keyword)
                .orderByAsc(BreedingAnimal::getEarTag));
        Map<Long, BreedingCycle> cycles = new HashMap<>();
        cycleMapper.selectList(Wrappers.<BreedingCycle>lambdaQuery()
                        .in(BreedingCycle::getStatus, BreedingCodes.CYCLE_PREGNANT))
                .forEach(cycle -> cycles.put(cycle.getId(), cycle));
        Map<Long, BreedQueryService.BreedInfo> breeds = support.breeds(
                animals.stream().map(BreedingAnimal::getBreedId).filter(Objects::nonNull).distinct().toList());
        Map<Long, PenInfo> pens = support.pens(animals.stream().map(BreedingAnimal::getPenId)
                .filter(Objects::nonNull).distinct().toList());
        LocalDate today = BreedingDates.today();
        List<DueRow<FarrowDueItemVO>> rows = new ArrayList<>();
        for (BreedingAnimal animal : animals) {
            if (penScope != null && !penScope.contains(animal.getPenId())) {
                continue;
            }
            BreedingCycle cycle = animal.getCurrentCycleId() == null ? null
                    : cycles.get(animal.getCurrentCycleId());
            if (cycle == null || cycle.getExpectedDueDate() == null) {
                continue;
            }
            int days = BreedingDates.daysBetween(today, cycle.getExpectedDueDate());
            FarrowDueItemVO vo = new FarrowDueItemVO();
            vo.setGroup(BreedingCodes.dueGroup(days));
            vo.setGroupText(BreedingCodes.groupText(vo.getGroup()));
            vo.setPenId(animal.getPenId() == null ? 0 : animal.getPenId());
            PenInfo pen = pens.get(animal.getPenId());
            vo.setPenName(pen == null ? null : pen.penName());
            LocalDate remindBefore = cycle.getExpectedDueDate().minusDays(BreedingDates.REMIND_BEFORE_DAYS);
            vo.setRemindBeforeDate(BeijingTime.format(remindBefore));
            vo.setRemindBeforeSentAt(BeijingTime.format(cycle.getRemindBeforeSentAt()));
            vo.setRemindDueSentAt(BeijingTime.format(cycle.getRemindDueSentAt()));
            vo.setReminderText(BreedingDates.reminderText(remindBefore, cycle.getExpectedDueDate(),
                    cycle.getRemindBeforeSentAt() != null, cycle.getRemindDueSentAt() != null, days));
            // TODO 模块02 提供“栏舍负责员工姓名”查询后接入 t_member_pen；本批返回空列表。
            vo.setStaffNames(List.of());
            vo.setCanRegister(farmerView && days <= 0);
            vo.setAnimalId(animal.getId());
            vo.setEarTag(animal.getEarTag());
            BreedQueryService.BreedInfo breed = breeds.get(animal.getBreedId());
            vo.setBreedName(breed == null ? null : breed.name());
            vo.setParity(animal.getParity() == null ? 0 : animal.getParity());
            vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
            vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
            vo.setDaysToDue(days);
            vo.setCanOpenArchive(farmerView);
            rows.add(new DueRow<>(vo, days, vo.getGroup(), animal.getId()));
        }
        rows.sort(Comparator.comparingInt(DueRow<FarrowDueItemVO>::days)
                .thenComparing(row -> row.vo().getEarTag(), Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, Integer> tabCounts = dueTabCounts(rows);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<FarrowDueItemVO> list = rows.subList(from, to).stream().map(DueRow::vo).toList();
        return new PageVO<>(list, rows.size(), page, size).withTabCounts(tabCounts);
    }

    @Override
    public PageVO<HatchDueItemVO> hatchDue(BreedingDueQuery query) {
        FarmContext.FarmMember viewer = support.viewer();
        boolean farmerView = viewer.isManagerView();
        Set<Long> penScope = viewer.isStaff() && viewer.penIds() != null ? viewer.penIds() : null;
        String keyword = query.keywordOrNull();
        List<Incubation> incubations = incubationMapper.selectList(Wrappers.<Incubation>lambdaQuery()
                .in(Incubation::getStatus, BreedingCodes.INCUBATION_SET, BreedingCodes.INCUBATION_CANDLED)
                .orderByAsc(Incubation::getExpectedHatchDate)
                .orderByAsc(Incubation::getId));
        Map<Long, PenInfo> pens = support.pens(incubations.stream().map(Incubation::getPenId)
                .filter(Objects::nonNull).distinct().toList());
        LocalDate today = BreedingDates.today();
        List<DueRow<HatchDueItemVO>> rows = new ArrayList<>();
        for (Incubation incubation : incubations) {
            if (penScope != null && !penScope.contains(incubation.getPenId())) {
                continue;
            }
            BatchInfo batch = support.batchOrNull(incubation.getBreedingBatchId());
            String batchNo = batch == null ? null : batch.batchNo();
            PenInfo pen = pens.get(incubation.getPenId());
            String penName = pen == null ? null : pen.penName();
            if (keyword != null) {
                boolean matched = (batchNo != null && batchNo.contains(keyword))
                        || (penName != null && penName.contains(keyword));
                if (!matched) {
                    continue;
                }
            }
            int days = BreedingDates.daysFromToday(incubation.getExpectedHatchDate());
            HatchDueItemVO vo = new HatchDueItemVO();
            vo.setGroup(BreedingCodes.dueGroup(days));
            vo.setGroupText(BreedingCodes.groupText(vo.getGroup()));
            vo.setPenId(incubation.getPenId() == null ? 0 : incubation.getPenId());
            vo.setPenName(penName);
            LocalDate remindBefore = incubation.getExpectedHatchDate() == null ? null
                    : incubation.getExpectedHatchDate().minusDays(1);
            vo.setRemindBeforeDate(BeijingTime.format(remindBefore));
            vo.setRemindBeforeSentAt(BeijingTime.format(incubation.getRemindBeforeSentAt()));
            vo.setRemindDueSentAt(BeijingTime.format(incubation.getRemindDueSentAt()));
            vo.setReminderText(BreedingDates.reminderText(remindBefore, incubation.getExpectedHatchDate(),
                    incubation.getRemindBeforeSentAt() != null, incubation.getRemindDueSentAt() != null, days));
            // TODO 模块02 提供栏舍负责员工查询后接入。
            vo.setStaffNames(List.of());
            vo.setCanRegister(farmerView && days <= 0);
            vo.setIncubationId(incubation.getId());
            vo.setBreedingBatchId(incubation.getBreedingBatchId() == null ? 0 : incubation.getBreedingBatchId());
            vo.setFlockText(IncubationServiceImpl.flockText(penName, batchNo));
            vo.setSetDate(BeijingTime.format(incubation.getSetDate()));
            vo.setSetCount(incubation.getSetCount() == null ? 0 : incubation.getSetCount());
            vo.setFertileCount(BreedingCodes.INCUBATION_CANDLED.equals(incubation.getStatus())
                    ? incubation.getFertileCount() : null);
            vo.setExpectedHatchDate(BeijingTime.format(incubation.getExpectedHatchDate()));
            vo.setDaysToHatch(days);
            vo.setCanOpen(farmerView);
            rows.add(new DueRow<>(vo, days, vo.getGroup(), incubation.getId()));
        }
        rows.sort(Comparator.comparingInt(DueRow<HatchDueItemVO>::days)
                .thenComparing(DueRow::id, Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, Integer> tabCounts = dueTabCounts(rows);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<HatchDueItemVO> list = rows.subList(from, to).stream().map(DueRow::vo).toList();
        return new PageVO<>(list, rows.size(), page, size).withTabCounts(tabCounts);
    }

    @Override
    public SubmissionVO submission(String clientRequestId, String type) {
        support.viewer();
        if (type == null || !List.of("MATING", "PREG_CHECK", "FARROWING", "WEANING", "INCUBATION", "HATCH")
                .contains(type)) {
            throw BreedingErrors.invalid("type", "提交类型不正确");
        }
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw BreedingErrors.invalid("client_request_id", "缺少请求编号");
        }
        Long userId = UserContext.requireUserId();
        SubmissionVO vo = new SubmissionVO();
        vo.setType(type);
        switch (type) {
            case "MATING" -> {
                MatingRecord record = matingMapper.selectOne(Wrappers.<MatingRecord>lambdaQuery()
                        .eq(MatingRecord::getClientRequestId, clientRequestId)
                        .eq(MatingRecord::getCreatedBy, userId).last("LIMIT 1"));
                if (record != null) {
                    BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
                    BreedingCycle cycle = support.requireCycle(record.getCycleId());
                    fill(vo, record.getId(), recordConvert.registerResult(BreedingCodes.RECORD_MATING,
                            record.getId(), animal, cycle.getId(), record.getIsFirst(),
                            cycle.getExpectedDueDate(), null, null, null));
                }
            }
            case "PREG_CHECK" -> {
                PregnancyCheck record = checkMapper.selectOne(Wrappers.<PregnancyCheck>lambdaQuery()
                        .eq(PregnancyCheck::getClientRequestId, clientRequestId)
                        .eq(PregnancyCheck::getCreatedBy, userId).last("LIMIT 1"));
                if (record != null) {
                    BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
                    BreedingCycle cycle = support.requireCycle(record.getCycleId());
                    fill(vo, record.getId(), recordConvert.registerResult(BreedingCodes.RECORD_PREG_CHECK,
                            record.getId(), animal, cycle.getId(), null, cycle.getExpectedDueDate(), null, null,
                            null));
                }
            }
            case "FARROWING" -> {
                FarrowingRecord record = farrowingMapper.selectOne(Wrappers.<FarrowingRecord>lambdaQuery()
                        .eq(FarrowingRecord::getClientRequestId, clientRequestId)
                        .eq(FarrowingRecord::getCreatedBy, userId).last("LIMIT 1"));
                if (record != null) {
                    BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
                    fill(vo, record.getId(), recordConvert.registerResult(BreedingCodes.RECORD_FARROWING,
                            record.getId(), animal, record.getCycleId(), null, null, record.getBatchId(),
                            support.batchNo(record.getBatchId()), true));
                }
            }
            case "WEANING" -> {
                WeaningRecord record = weaningMapper.selectOne(Wrappers.<WeaningRecord>lambdaQuery()
                        .eq(WeaningRecord::getClientRequestId, clientRequestId)
                        .eq(WeaningRecord::getCreatedBy, userId).last("LIMIT 1"));
                if (record != null) {
                    BreedingAnimal animal = support.requireAnimal(record.getAnimalId());
                    fill(vo, record.getId(), recordConvert.registerResult(BreedingCodes.RECORD_WEANING,
                            record.getId(), animal, record.getCycleId(), null, null, null, null, null));
                }
            }
            case "INCUBATION" -> {
                Incubation record = incubationMapper.selectOne(Wrappers.<Incubation>lambdaQuery()
                        .eq(Incubation::getSetRequestId, clientRequestId)
                        .eq(Incubation::getCreatedBy, userId).last("LIMIT 1"));
                if (record != null) {
                    fill(vo, record.getId(), incubationResult(record));
                }
            }
            default -> {
                Incubation record = incubationMapper.selectOne(Wrappers.<Incubation>lambdaQuery()
                        .eq(Incubation::getHatchRequestId, clientRequestId)
                        .eq(Incubation::getHatchBy, userId).last("LIMIT 1"));
                if (record != null) {
                    fill(vo, record.getId(), incubationResult(record));
                }
            }
        }
        return vo;
    }

    private RegisterResultVO incubationResult(Incubation record) {
        RegisterResultVO result = new RegisterResultVO();
        result.setRecordType(BreedingCodes.RECORD_INCUBATION);
        result.setRecordId(record.getId());
        result.setIncubationId(record.getId());
        result.setExpectedHatchDate(BeijingTime.format(record.getExpectedHatchDate()));
        result.setBatchId(record.getBatchId());
        result.setBatchNo(support.batchNo(record.getBatchId()));
        result.setTodoCompleted(BreedingCodes.INCUBATION_HATCHED.equals(record.getStatus()));
        return result;
    }

    private static void fill(SubmissionVO vo, Long recordId, RegisterResultVO result) {
        vo.setSaved(true);
        vo.setRecordId(recordId);
        vo.setResult(result);
    }

    // ===== 共用 =====

    private List<BreedingCycle> cyclesOf(List<BreedingAnimal> animals) {
        List<Long> cycleIds = animals.stream().map(BreedingAnimal::getCurrentCycleId)
                .filter(Objects::nonNull).distinct().toList();
        if (cycleIds.isEmpty()) {
            return List.of();
        }
        return cycleMapper.selectBatchIds(cycleIds);
    }

    private String resolveSpecies(List<BreedingAnimal> animals, List<Incubation> incubations) {
        if (!incubations.isEmpty()) {
            return "POULTRY";
        }
        for (BreedingAnimal animal : animals) {
            CategoryInfo category = support.categoryOfBreed(animal.getBreedId());
            if (category != null && category.species() != null) {
                return category.species();
            }
        }
        return "PIG";
    }

    private List<BreedingOverviewVO.SwitchFarm> switchFarms(Long currentFarmId, Long userId) {
        List<FarmBriefVO> farms = farmQueryService.listFarmsOfUser(userId);
        if (farms == null || farms.isEmpty()) {
            return List.of();
        }
        List<Long> otherFarmIds = farms.stream()
                .filter(farm -> !Objects.equals(farm.getFarmId(), currentFarmId))
                .filter(farm -> "NORMAL".equals(farm.getStatus()))
                .map(FarmBriefVO::getFarmId)
                .toList();
        if (otherFarmIds.isEmpty()) {
            return List.of();
        }
        List<Incubation> others = TenantSkip.call(() -> incubationMapper.selectList(
                Wrappers.<Incubation>lambdaQuery()
                        .in(Incubation::getFarmId, otherFarmIds)
                        .in(Incubation::getStatus, BreedingCodes.INCUBATION_SET, BreedingCodes.INCUBATION_CANDLED)));
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Incubation incubation : others) {
            counts.merge(incubation.getFarmId(), 1, Integer::sum);
        }
        List<BreedingOverviewVO.SwitchFarm> result = new ArrayList<>();
        for (FarmBriefVO farm : farms) {
            Integer count = counts.get(farm.getFarmId());
            if (count == null || count == 0 || Objects.equals(farm.getFarmId(), currentFarmId)) {
                continue;
            }
            BreedingOverviewVO.SwitchFarm item = new BreedingOverviewVO.SwitchFarm();
            item.setFarmId(farm.getFarmId());
            item.setFarmName(farm.getName());
            item.setHatchDueCount(count);
            result.add(item);
        }
        result.sort(Comparator.comparingLong(BreedingOverviewVO.SwitchFarm::getFarmId));
        return result;
    }

    private static Map<String, Integer> dueTabCounts(List<? extends DueRow<?>> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("TODAY", 0);
        counts.put("WITHIN_7", 0);
        counts.put("LATER", 0);
        for (DueRow<?> row : rows) {
            counts.merge(row.group(), 1, Integer::sum);
        }
        return counts;
    }

    private record PendingRow(PendingCheckVO vo, int daysSinceMating) {
    }

    private record DueRow<T>(T vo, int days, String group, Long id) {
    }
}
