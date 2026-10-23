package com.zhimu.server.biz.breeding.convert;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.WeaningRecord;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingDates;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalDetailVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalItemVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalOptionVO;
import com.zhimu.server.common.util.BeijingTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** 种畜档案 / 列表 / 下拉的 VO 转换（字段名以 App module_breed 的 VO 为准）。 */
@Component
public class BreedingAnimalConvert {

    public BreedingAnimalItemVO item(BreedingAnimal animal, String breedName, String penName,
                                     BreedingCycle cycle, FarrowingRecord latestFarrow) {
        BreedingAnimalItemVO vo = new BreedingAnimalItemVO();
        vo.setAnimalId(animal.getId());
        vo.setBreedId(animal.getBreedId() == null ? 0 : animal.getBreedId());
        vo.setPenId(animal.getPenId() == null ? 0 : animal.getPenId());
        vo.setEarTag(animal.getEarTag());
        vo.setBreedName(breedName);
        vo.setPenName(penName);
        vo.setStatus(animal.getStatus());
        vo.setStatusText(BreedingCodes.statusText(animal.getStatus()));
        vo.setParity(animal.getParity() == null ? 0 : animal.getParity());
        vo.setVersion(animal.getVersion() == null ? 0 : animal.getVersion());
        vo.setNextAction(BreedingCodes.nextAction(animal.getStatus()));
        vo.setNextActionText(BreedingCodes.nextActionText(vo.getNextAction()));
        if (BreedingCodes.exited(animal.getStatus())) {
            vo.setExitType(animal.getExitType());
            vo.setExitTypeText(BreedingCodes.exitTypeText(animal.getExitType()));
            vo.setExitDate(BeijingTime.format(animal.getExitDate()));
            vo.setExitReason(animal.getExitReason());
            return vo;
        }
        LocalDate today = BreedingDates.today();
        boolean nursing = BreedingCodes.STATUS_LACTATING.equals(animal.getStatus());
        if (cycle != null) {
            if (!nursing) {
                vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
                vo.setDaysSinceMating(BreedingDates.daysBetween(cycle.getFirstMatingDate(), today));
                vo.setPendingCheck(pendingCheck(animal, cycle, today));
            }
            if (BreedingCodes.STATUS_PREGNANT.equals(animal.getStatus()) && cycle.getExpectedDueDate() != null) {
                vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
                vo.setDaysToDue(BreedingDates.daysBetween(today, cycle.getExpectedDueDate()));
            }
        }
        if (nursing) {
            fillNursing(vo, latestFarrow, today);
        } else if (BreedingCodes.STATUS_EMPTY.equals(animal.getStatus())) {
            vo.setLastWeanDate(BeijingTime.format(animal.getLastWeanDate()));
        }
        return vo;
    }

    public BreedingAnimalDetailVO detail(BreedingAnimal animal, String breedName, String penName, String animalLabel,
                                         BreedingCycle cycle, FarrowingRecord latestFarrow,
                                         List<FarrowingRecord> farrowings, WeaningRecord latestWean) {
        BreedingAnimalDetailVO vo = new BreedingAnimalDetailVO();
        vo.setAnimalId(animal.getId());
        vo.setBreedId(animal.getBreedId() == null ? 0 : animal.getBreedId());
        vo.setPenId(animal.getPenId() == null ? 0 : animal.getPenId());
        vo.setEarTag(animal.getEarTag());
        vo.setBreedName(breedName);
        vo.setPenName(penName);
        vo.setStatus(animal.getStatus());
        vo.setStatusText(BreedingCodes.statusText(animal.getStatus()));
        vo.setParity(animal.getParity() == null ? 0 : animal.getParity());
        vo.setVersion(animal.getVersion() == null ? 0 : animal.getVersion());
        vo.setNextAction(BreedingCodes.nextAction(animal.getStatus()));
        vo.setNextActionText(BreedingCodes.nextActionText(vo.getNextAction()));
        vo.setAnimalLabel(animalLabel);
        vo.setFilingDate(BeijingTime.format(animal.getFilingDate()));
        vo.setSource(animal.getSource());
        vo.setSourceText(BreedingCodes.sourceText(animal.getSource()));
        vo.setInitialParity(animal.getInitialParity() == null ? 0 : animal.getInitialParity());
        vo.setBirthDate(BeijingTime.format(animal.getBirthDate()));
        boolean exited = BreedingCodes.exited(animal.getStatus());
        vo.setCanEdit(!exited);
        vo.setCanExit(!exited);
        if (exited) {
            vo.setExitType(animal.getExitType());
            vo.setExitTypeText(BreedingCodes.exitTypeText(animal.getExitType()));
            vo.setExitDate(BeijingTime.format(animal.getExitDate()));
            vo.setExitReason(animal.getExitReason());
        }
        LocalDate today = BreedingDates.today();
        if (cycle != null) {
            BreedingAnimalDetailVO.CurrentCycle current = new BreedingAnimalDetailVO.CurrentCycle();
            current.setCycleId(cycle.getId());
            current.setStatus(cycle.getStatus());
            current.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
            current.setMatingCount(cycle.getMatingCount() == null ? 0 : cycle.getMatingCount());
            boolean nursing = BreedingCodes.CYCLE_FARROWED.equals(cycle.getStatus());
            if (!nursing) {
                current.setDaysSinceMating(BreedingDates.daysBetween(cycle.getFirstMatingDate(), today));
                current.setCheckDueDate(BeijingTime.format(cycle.getCheckDueDate()));
                current.setPendingCheck(pendingCheck(animal, cycle, today));
                current.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
                current.setDaysToDue(BreedingDates.daysBetween(today, cycle.getExpectedDueDate()));
                vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
                vo.setDaysSinceMating(current.getDaysSinceMating());
                vo.setPendingCheck(current.getPendingCheck());
                vo.setExpectedDueDate(current.getExpectedDueDate());
                vo.setDaysToDue(current.getDaysToDue());
            } else {
                if (latestFarrow != null && cycle.getId().equals(latestFarrow.getCycleId())) {
                    current.setFarrowDate(BeijingTime.format(latestFarrow.getFarrowDate()));
                    current.setLiveBorn(latestFarrow.getLiveBorn());
                    current.setNursingDays(BreedingDates.daysBetween(latestFarrow.getFarrowDate(), today));
                    vo.setLastFarrowDate(BeijingTime.format(latestFarrow.getFarrowDate()));
                    vo.setLiveBorn(latestFarrow.getLiveBorn());
                    vo.setNursingDays(current.getNursingDays());
                }
            }
            vo.setCurrentCycle(current);
        } else if (BreedingCodes.STATUS_EMPTY.equals(animal.getStatus())) {
            vo.setLastWeanDate(BeijingTime.format(animal.getLastWeanDate()));
            if (latestFarrow != null) {
                vo.setLastFarrowDate(BeijingTime.format(latestFarrow.getFarrowDate()));
                vo.setLiveBorn(latestFarrow.getLiveBorn());
            }
        }
        BreedingAnimalDetailVO.Stats stats = stats(farrowings, latestWean);
        vo.setStats(stats);
        return vo;
    }

    public BreedingAnimalOptionVO option(BreedingAnimal animal, String breedName, String penName, BreedingCycle cycle,
                                         FarrowingRecord latestFarrow, String purpose, Integer gestationDays) {
        BreedingAnimalOptionVO vo = new BreedingAnimalOptionVO();
        vo.setAnimalId(animal.getId());
        vo.setEarTag(animal.getEarTag());
        vo.setBreedName(breedName);
        vo.setParity(animal.getParity() == null ? 0 : animal.getParity());
        vo.setStatus(animal.getStatus());
        vo.setStatusText(BreedingCodes.statusText(animal.getStatus()));
        vo.setPenId(animal.getPenId() == null ? 0 : animal.getPenId());
        vo.setPenName(penName);
        vo.setVersion(animal.getVersion() == null ? 0 : animal.getVersion());
        LocalDate today = BreedingDates.today();
        if ("MATING".equals(purpose)) {
            vo.setGestationDays(gestationDays);
            if (BreedingCodes.STATUS_MATED.equals(animal.getStatus()) && cycle != null) {
                vo.setFirstMatingDate(BeijingTime.format(cycle.getFirstMatingDate()));
            } else if (BreedingCodes.STATUS_EMPTY.equals(animal.getStatus())) {
                vo.setLastWeanDate(BeijingTime.format(animal.getLastWeanDate()));
            }
        } else if ("FARROWING".equals(purpose) && cycle != null) {
            vo.setExpectedDueDate(BeijingTime.format(cycle.getExpectedDueDate()));
            vo.setDaysToDue(BreedingDates.daysBetween(today, cycle.getExpectedDueDate()));
        } else if ("WEANING".equals(purpose)) {
            if (latestFarrow != null) {
                vo.setLastFarrowDate(BeijingTime.format(latestFarrow.getFarrowDate()));
                vo.setLiveBorn(latestFarrow.getLiveBorn());
            }
        }
        return vo;
    }

    private static Boolean pendingCheck(BreedingAnimal animal, BreedingCycle cycle, LocalDate today) {
        if (!BreedingCodes.STATUS_MATED.equals(animal.getStatus()) || cycle.getCheckDueDate() == null) {
            return null;
        }
        return !today.isBefore(cycle.getCheckDueDate());
    }

    private static void fillNursing(BreedingAnimalItemVO vo, FarrowingRecord latestFarrow, LocalDate today) {
        if (latestFarrow == null) {
            return;
        }
        vo.setLastFarrowDate(BeijingTime.format(latestFarrow.getFarrowDate()));
        vo.setLiveBorn(latestFarrow.getLiveBorn());
        vo.setNursingDays(BreedingDates.daysBetween(latestFarrow.getFarrowDate(), today));
    }

    private static BreedingAnimalDetailVO.Stats stats(List<FarrowingRecord> farrowings, WeaningRecord latestWean) {
        BreedingAnimalDetailVO.Stats stats = new BreedingAnimalDetailVO.Stats();
        stats.setFarrowingCount(farrowings == null ? 0 : farrowings.size());
        if (farrowings != null && !farrowings.isEmpty()) {
            int liveTotal = farrowings.stream()
                    .map(FarrowingRecord::getLiveBorn)
                    .filter(v -> v != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            stats.setAvgLiveBorn(BigDecimal.valueOf(liveTotal)
                    .divide(BigDecimal.valueOf(farrowings.size()), 1, RoundingMode.HALF_UP));
        }
        if (latestWean != null) {
            stats.setLastWeanDate(BeijingTime.format(latestWean.getWeanDate()));
            stats.setLastWeanedCount(latestWean.getWeanedCount());
        }
        return stats;
    }
}
