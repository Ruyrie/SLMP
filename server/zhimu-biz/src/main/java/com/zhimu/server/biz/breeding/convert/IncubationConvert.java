package com.zhimu.server.biz.breeding.convert;

import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingDates;
import com.zhimu.server.biz.breeding.vo.IncubationDetailVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 孵化详情的 VO 转换（API-B28、B29、B30）。 */
@Component
public class IncubationConvert {

    public IncubationDetailVO detail(Incubation incubation, String farmName, String flockText, String breedName,
                                     String targetPenName, String offspringBreedName, String batchNo,
                                     Map<Long, UserBriefVO> users) {
        IncubationDetailVO vo = new IncubationDetailVO();
        vo.setIncubationId(incubation.getId());
        vo.setBreedingBatchId(incubation.getBreedingBatchId() == null ? 0 : incubation.getBreedingBatchId());
        vo.setFarmName(farmName);
        vo.setFlockText(flockText);
        vo.setBreedName(breedName);
        vo.setIncubationDays(incubation.getIncubationDays() == null ? 0 : incubation.getIncubationDays());
        vo.setSetDate(BeijingTime.format(incubation.getSetDate()));
        vo.setExpectedHatchDate(BeijingTime.format(incubation.getExpectedHatchDate()));
        vo.setSetCount(incubation.getSetCount() == null ? 0 : incubation.getSetCount());
        vo.setDaysToHatch(BreedingDates.daysFromToday(incubation.getExpectedHatchDate()));
        vo.setStatus(incubation.getStatus());
        vo.setStatusText(BreedingCodes.incubationStatusText(incubation.getStatus()));
        vo.setVersion(incubation.getVersion() == null ? 0 : incubation.getVersion());
        vo.setCurrentStep(currentStep(incubation.getStatus()));
        vo.setCandleDate(BeijingTime.format(incubation.getCandleDate()));
        vo.setFertileCount(incubation.getFertileCount());
        vo.setHatchDate(BeijingTime.format(incubation.getHatchDate()));
        vo.setHatchedCount(incubation.getHatchedCount());
        vo.setHealthyCount(incubation.getHealthyCount());
        vo.setWeakCount(incubation.getWeakCount());
        vo.setTransferToBatch(incubation.getTransferToBatch());
        vo.setTargetPenId(incubation.getTargetPenId());
        vo.setTargetPenName(targetPenName);
        vo.setOffspringBreedName(offspringBreedName);
        vo.setBatchId(incubation.getBatchId());
        vo.setBatchNo(batchNo);
        vo.setFertilityRate(rate(incubation.getFertileCount(), incubation.getSetCount()));
        vo.setHatchRate(rate(incubation.getHatchedCount(), incubation.getSetCount()));
        boolean transferred = incubation.getBatchId() != null;
        vo.setCanEdit(!BreedingCodes.INCUBATION_HATCHED.equals(incubation.getStatus()) || !transferred);
        vo.setCanRequestChange(transferred);
        vo.setChangeState(new ChangeRequestStateVO(vo.isCanEdit(), null, null));
        vo.setReminderText(reminderText(incubation));
        vo.setTimeline(timeline(incubation, users));
        return vo;
    }

    /** current_step：SET 2、CANDLED 3、HATCHED 0（与 App Mock 一致） */
    public static int currentStep(String status) {
        if (BreedingCodes.INCUBATION_SET.equals(status)) {
            return 2;
        }
        if (BreedingCodes.INCUBATION_CANDLED.equals(status)) {
            return 3;
        }
        return 0;
    }

    private static BigDecimal rate(Integer numerator, Integer denominator) {
        if (numerator == null || denominator == null || denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static String reminderText(Incubation incubation) {
        if (incubation.getExpectedHatchDate() == null
                || BreedingCodes.INCUBATION_HATCHED.equals(incubation.getStatus())) {
            return null;
        }
        int days = BreedingDates.daysFromToday(incubation.getExpectedHatchDate());
        return BreedingDates.reminderText(incubation.getExpectedHatchDate().minusDays(1),
                incubation.getExpectedHatchDate(), incubation.getRemindBeforeSentAt() != null,
                incubation.getRemindDueSentAt() != null, days);
    }

    private static List<IncubationDetailVO.TimelineItem> timeline(Incubation incubation,
                                                                  Map<Long, UserBriefVO> users) {
        List<IncubationDetailVO.TimelineItem> items = new ArrayList<>();
        if (incubation.getHatchAt() != null) {
            items.add(item("HATCH", "出雏", incubation.getHatchAt(), incubation.getHatchBy(), users));
        }
        if (incubation.getCandleAt() != null) {
            items.add(item("CANDLE", "照蛋", incubation.getCandleAt(), incubation.getCandleBy(), users));
        }
        if (incubation.getCreatedAt() != null) {
            items.add(item("SET", "入孵", incubation.getCreatedAt(), incubation.getCreatedBy(), users));
        }
        return items;
    }

    private static IncubationDetailVO.TimelineItem item(String step, String stepText, java.time.LocalDateTime at,
                                                        Long userId, Map<Long, UserBriefVO> users) {
        IncubationDetailVO.TimelineItem item = new IncubationDetailVO.TimelineItem();
        item.setStep(step);
        item.setStepText(stepText);
        item.setRegisteredAt(BeijingTime.format(at));
        item.setRegistrant(userId == null ? null : users.get(userId));
        return item;
    }
}
