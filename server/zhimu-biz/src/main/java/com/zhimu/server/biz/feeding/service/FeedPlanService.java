package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedPlan;
import com.zhimu.server.biz.feeding.entity.FeedPlanMeal;
import com.zhimu.server.biz.feeding.entity.FeedPlanStage;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMealMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanStageMapper;
import com.zhimu.server.biz.feeding.request.FeedPlanSaveRequest;
import com.zhimu.server.biz.feeding.request.PlanStage;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.feeding.support.FeedConvert;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.FeedPlanVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** API-G09–G12 饲喂方案（技术准备文档 2.5.4、2.6.3、2.7.1、2.7.2）。 */
@Service
@RequiredArgsConstructor
public class FeedPlanService {

    private final FeedPlanMapper planMapper;
    private final FeedPlanStageMapper stageMapper;
    private final FeedPlanMealMapper mealMapper;
    private final FeedTaskService taskService;
    private final FeedLookup lookup;
    private final StdQueryService stdQueryService;

    /** G09 方案批次选择：当前养殖场在养批次，按栏舍编号排序 */
    public List<FeedPlanVO.BatchOption> batchOptions() {
        lookup.requireManager("你没有查看饲喂方案的权限");
        List<BatchInfo> batches = lookup.requireActiveBatches(lookup.farmId());
        List<FeedPlanVO.BatchOption> options = new ArrayList<>();
        for (BatchInfo batch : batches) {
            FeedPlan plan = plan(batch.batchId());
            FeedPlanVO.BatchOption vo = new FeedPlanVO.BatchOption();
            vo.setBatchId(batch.batchId());
            vo.setBatchNo(batch.batchNo());
            vo.setPenNames(lookup.penNames(batch.penIds()));
            vo.setBreedName(lookup.breedName(batch.breedId()));
            vo.setDayAge(FeedCalc.dayAge(batch, BeijingTime.today()));
            vo.setHasPlan(plan != null);
            options.add(vo);
        }
        options.sort(Comparator.comparing(FeedPlanVO.BatchOption::getPenNames,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return options;
    }

    /** G10 饲喂方案 */
    public FeedPlanVO.Plan getPlan(Long batchId) {
        lookup.requireManager("你没有查看饲喂方案的权限");
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        return toPlan(batch);
    }

    /** G11 套用品种标准（只读、不落库） */
    public FeedPlanVO.StdApply getStandard(Long batchId) {
        lookup.requireManager("你没有查看饲喂方案的权限");
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        int dayAge = FeedCalc.dayAge(batch, BeijingTime.today());
        BreedStandardsVO standards = stdQueryService.standards(batch.breedId(), dayAge);
        FeedPlanVO.StdApply vo = new FeedPlanVO.StdApply();
        if (standards == null || standards.getFeed() == null) {
            vo.setStdAvailable(false);
            vo.setDefaultAbnormalThreshold(new BigDecimal("0.1000"));
            return vo;
        }
        vo.setStdAvailable(true);
        vo.setStdVersionId(standards.getFeed().getVersionId());
        vo.setStdName(stdName(batch.breedId()));
        vo.setDefaultAbnormalThreshold(standards.getFeed().getDeviationRate() == null
                ? new BigDecimal("0.1000") : standards.getFeed().getDeviationRate());
        List<FeedStockPort.FeedBrief> feeds = lookup.feeds();
        for (BreedStandardsVO.FeedStageVO stage : standards.getFeed().getStages()) {
            FeedPlanVO.StdStage item = new FeedPlanVO.StdStage();
            item.setStdStageId(stage.getStageId());
            item.setStageName(stage.getStageName());
            item.setDayAgeStart(stage.getDayAgeStart());
            item.setDayAgeEnd(stage.getDayAgeEnd());
            item.setStdFeedName(stage.getSuggestedFeedName());
            item.setStdFeedTypeCode(stage.getFeedTypeCode());
            item.setDailyQty(stage.getDailyFeed());
            item.setMealsPerDay(stage.getMealsPerDay());
            item.setMealTimes(stage.getMealTimes() == null ? new ArrayList<>() : new ArrayList<>(stage.getMealTimes()));
            FeedStockPort.FeedBrief matched = matchFeed(feeds, stage.getSuggestedFeedName());
            if (matched != null) {
                item.setMatchedFeedId(matched.feedId());
                item.setMatchedFeedName(matched.feedName());
            }
            vo.getStages().add(item);
        }
        return vo;
    }

    /** G12 保存方案 */
    @Transactional
    public FeedPlanVO.Plan save(Long batchId, FeedPlanSaveRequest request) {
        lookup.requireManager("你没有查看饲喂方案的权限");
        BatchInfo batch = lookup.requireActiveBatch(batchId);
        if (request == null) {
            throw FeedErrors.field("stages", "请设置饲喂方案");
        }
        validateThreshold(request.getAbnormalThreshold());
        List<PlanStage> stages = request.getStages();
        validateStages(stages, FeedCalc.dayAge(batch, BeijingTime.today()));
        FeedPlan plan = plan(batchId);
        boolean firstSave = plan == null;
        if (plan != null) {
            if (request.getVersion() == null) {
                throw FeedErrors.field("version", "缺少版本号");
            }
            if (!request.getVersion().equals(plan.getVersion())) {
                throw FeedErrors.versionConflict();
            }
        } else if (request.getVersion() != null) {
            throw FeedErrors.versionConflict();
        }
        if (request.getAppliedStdVersionId() != null) {
            Long current = stdQueryService.currentFeedVersionId(batch.breedId());
            if (current == null || !current.equals(request.getAppliedStdVersionId())) {
                throw FeedErrors.field("applied_std_version_id", "饲喂标准已更新，请重新套用");
            }
        }
        if (plan == null) {
            plan = new FeedPlan();
            plan.setFarmId(lookup.farmId());
            plan.setBatchId(batchId);
            plan.setBreedId(batch.breedId());
            plan.setAbnormalThreshold(request.getAbnormalThreshold());
            applyStd(plan, request.getAppliedStdVersionId());
            plan.setVersion(0);
            planMapper.insert(plan);
        } else {
            plan.setAbnormalThreshold(request.getAbnormalThreshold());
            if (request.getAppliedStdVersionId() != null) {
                applyStd(plan, request.getAppliedStdVersionId());
            }
            if (planMapper.updateById(plan) == 0) {
                throw FeedErrors.versionConflict();
            }
        }
        Map<Long, FeedPlanStage> existing = new HashMap<>();
        for (FeedPlanStage stage : stages(plan.getId())) {
            existing.put(stage.getId(), stage);
        }
        List<FeedPlanStage> saved = new ArrayList<>();
        Set<Long> kept = new HashSet<>();
        int sortNo = 0;
        for (PlanStage requestStage : stages) {
            sortNo++;
            FeedPlanStage stage;
            if (requestStage.getStageId() != null) {
                stage = existing.get(requestStage.getStageId());
                if (stage == null) {
                    throw FeedErrors.field("stages", "阶段不属于这个方案");
                }
                if ("ENDED".equals(state(stage, FeedCalc.dayAge(batch, BeijingTime.today())))
                        && changed(stage, requestStage)) {
                    throw FeedErrors.field("stages", "已结束的阶段不能修改");
                }
                fillStage(stage, requestStage, plan, batch);
                stage.setSortNo(sortNo);
                if (stageMapper.updateById(stage) == 0) {
                    throw FeedErrors.versionConflict();
                }
            } else {
                stage = new FeedPlanStage();
                stage.setFarmId(lookup.farmId());
                stage.setPlanId(plan.getId());
                stage.setBatchId(batchId);
                stage.setDeleted(false);
                fillStage(stage, requestStage, plan, batch);
                stage.setSortNo(sortNo);
                stageMapper.insert(stage);
            }
            kept.add(stage.getId());
            saved.add(stage);
            saveMeals(stage, requestStage.getMealTimes());
        }
        for (FeedPlanStage old : existing.values()) {
            if (!kept.contains(old.getId())) {
                stageMapper.deleteById(old.getId());
            }
        }
        if (firstSave) {
            taskService.generateForBatch(batch, plan, saved);
        }
        return toPlan(batch);
    }

    private void validateThreshold(BigDecimal threshold) {
        if (threshold == null) {
            throw FeedErrors.field("abnormal_threshold", "请填写投喂异常阈值");
        }
        if (threshold.compareTo(new BigDecimal("0.01")) < 0 || threshold.compareTo(new BigDecimal("1.00")) > 0) {
            throw FeedErrors.field("abnormal_threshold", "偏差比例要在 1% 到 100% 之间");
        }
        if (threshold.scale() > 4) {
            throw FeedErrors.field("abnormal_threshold", "偏差比例最多 4 位小数");
        }
    }

    private void validateStages(List<PlanStage> stages, int currentDayAge) {
        if (stages == null || stages.isEmpty()) {
            throw FeedErrors.field("stages", "请至少设置一个生长阶段");
        }
        Integer previousEnd = null;
        boolean covered = false;
        for (int i = 0; i < stages.size(); i++) {
            PlanStage stage = stages.get(i);
            String path = "stages[" + i + "]";
            if (stage == null) {
                throw FeedErrors.field(path, "阶段内容不能为空");
            }
            if (stage.getStageName() == null || stage.getStageName().isBlank()
                    || stage.getStageName().trim().length() > 20) {
                throw FeedErrors.field(path + ".stage_name", "阶段名称要填写 1–20 个字");
            }
            if (stage.getDayAgeStart() == null || stage.getDayAgeStart() <= 0) {
                throw FeedErrors.field(path + ".day_age_start", "起始日龄要大于 0");
            }
            if (previousEnd != null && stage.getDayAgeStart() != previousEnd + 1) {
                throw FeedErrors.field(path + ".day_age_start", "各阶段要按日龄连续");
            }
            if (stage.getDayAgeEnd() != null) {
                if (stage.getDayAgeEnd() < stage.getDayAgeStart()) {
                    throw FeedErrors.field(path + ".day_age_end", "结束日龄不能小于起始日龄");
                }
                if (i == stages.size() - 1) {
                    // 最后一段允许填写结束日龄，也允许为空表示至出栏
                }
            } else if (i < stages.size() - 1) {
                throw FeedErrors.field(path + ".day_age_end", "只有最后一个阶段可以不填结束日龄");
            }
            if ((stage.getDayAgeEnd() == null && currentDayAge >= stage.getDayAgeStart())
                    || (stage.getDayAgeEnd() != null && currentDayAge >= stage.getDayAgeStart()
                    && currentDayAge <= stage.getDayAgeEnd())) {
                covered = true;
            }
            previousEnd = stage.getDayAgeEnd();
            if (stage.getFeedId() == null) {
                throw FeedErrors.field(path + ".feed_id", "请选择饲料");
            }
            FeedStockPort.FeedBrief feed = lookup.feed(stage.getFeedId());
            if (feed == null) {
                throw FeedErrors.field(path + ".feed_id", "请选择饲料");
            }
            if (stage.getDailyQtyPerHead() == null || stage.getDailyQtyPerHead().signum() <= 0) {
                throw FeedErrors.field(path + ".daily_qty_per_head", "每头日投喂量要大于 0 千克");
            }
            if (stage.getDailyQtyPerHead().compareTo(new BigDecimal("99.999")) > 0) {
                throw FeedErrors.field(path + ".daily_qty_per_head", "每头日投喂量不能超过 99.999 千克");
            }
            if (stage.getDailyQtyPerHead().scale() > 3) {
                throw FeedErrors.field(path + ".daily_qty_per_head", "每头日投喂量最多 3 位小数");
            }
            if (stage.getMealsPerDay() == null || stage.getMealsPerDay() < 1 || stage.getMealsPerDay() > 4) {
                throw FeedErrors.field(path + ".meals_per_day", "每日餐次要在 1 到 4 之间");
            }
            List<String> times = stage.getMealTimes();
            if (times == null || times.size() != stage.getMealsPerDay()) {
                throw FeedErrors.field(path + ".meal_times", "投喂时间个数要与餐次一致");
            }
            Set<LocalTime> unique = new LinkedHashSet<>();
            for (String time : times) {
                LocalTime parsed = FeedCalc.parseTime(time);
                if (parsed == null) {
                    throw FeedErrors.field(path + ".meal_times", "投喂时间格式应为 HH:mm");
                }
                unique.add(parsed);
            }
            if (unique.size() != times.size()) {
                throw FeedErrors.field(path + ".meal_times", "投喂时间不能重复");
            }
        }
        if (!covered) {
            throw FeedErrors.field("stages", "当前日龄须落在某一阶段内");
        }
    }

    private void fillStage(FeedPlanStage stage, PlanStage request, FeedPlan plan, BatchInfo batch) {
        stage.setStageName(request.getStageName().trim());
        stage.setDayAgeStart(request.getDayAgeStart());
        stage.setDayAgeEnd(request.getDayAgeEnd());
        stage.setFeedId(request.getFeedId());
        stage.setDailyQtyPerHead(request.getDailyQtyPerHead());
        stage.setMealsPerDay(request.getMealsPerDay());
        stage.setBatchId(batch.batchId());
    }

    private void saveMeals(FeedPlanStage stage, List<String> times) {
        List<FeedCalc.MealSlot> slots = FeedCalc.mealSlots(times);
        mealMapper.delete(Wrappers.<FeedPlanMeal>lambdaQuery().eq(FeedPlanMeal::getStageId, stage.getId()));
        for (FeedCalc.MealSlot slot : slots) {
            FeedPlanMeal meal = new FeedPlanMeal();
            meal.setFarmId(stage.getFarmId());
            meal.setPlanId(stage.getPlanId());
            meal.setStageId(stage.getId());
            meal.setMealNo(slot.mealNo());
            meal.setMealCode(slot.mealCode());
            meal.setMealName(slot.mealName());
            meal.setFeedTime(slot.feedTime());
            mealMapper.insert(meal);
        }
    }

    private void applyStd(FeedPlan plan, Long stdVersionId) {
        if (stdVersionId == null) {
            return;
        }
        plan.setStdVersionId(stdVersionId);
        plan.setStdAppliedAt(BeijingTime.now());
        plan.setStdAppliedBy(UserContext.requireUserId());
    }

    private FeedPlanVO.Plan toPlan(BatchInfo batch) {
        FeedPlan plan = plan(batch.batchId());
        FeedPlanVO.Plan vo = new FeedPlanVO.Plan();
        vo.setHasPlan(plan != null);
        vo.setPlanId(plan == null ? null : plan.getId());
        vo.setBatch(toBatch(batch));
        vo.setFeedOptions(new ArrayList<>(lookup.feeds().stream().map(FeedConvert::option).toList()));
        vo.setTodayGeneratedMeals(plan == null ? 0 : taskService.countToday(batch.batchId()));
        if (plan == null) {
            vo.setAbnormalThreshold(new BigDecimal("0.1000"));
            vo.setStdCard(stdCard(batch, null));
            vo.setVersion(null);
            return vo;
        }
        vo.setAbnormalThreshold(plan.getAbnormalThreshold());
        vo.setVersion(plan.getVersion());
        List<FeedPlanStage> stages = stages(plan.getId());
        Map<Long, List<FeedPlanMeal>> meals = meals(stages);
        int dayAge = FeedCalc.dayAge(batch, BeijingTime.today());
        int adjusted = 0;
        for (FeedPlanStage stage : stages) {
            FeedPlanVO.Stage item = toStage(batch, stage, meals.getOrDefault(stage.getId(), List.of()), dayAge);
            if (!item.getAdjustedFields().isEmpty()) {
                adjusted++;
            }
            vo.getStages().add(item);
        }
        FeedPlanVO.StdCard card = stdCard(batch, plan);
        card.setAdjustedCount(adjusted);
        vo.setStdCard(card);
        return vo;
    }

    private FeedPlanVO.Batch toBatch(BatchInfo batch) {
        FeedPlanVO.Batch vo = new FeedPlanVO.Batch();
        vo.setBatchId(batch.batchId());
        vo.setBatchNo(batch.batchNo());
        vo.setPenNames(lookup.penNames(batch.penIds()));
        vo.setBreedName(lookup.breedName(batch.breedId()));
        vo.setDayAge(FeedCalc.dayAge(batch, BeijingTime.today()));
        vo.setCurrentCount(batch.currentCount());
        vo.setEntryDate(BeijingTime.format(batch.entryDate()));
        vo.setEntryAgeDays(batch.entryAgeDays());
        return vo;
    }

    private FeedPlanVO.StdCard stdCard(BatchInfo batch, FeedPlan plan) {
        FeedPlanVO.StdCard card = new FeedPlanVO.StdCard();
        Long current = stdQueryService.currentFeedVersionId(batch.breedId());
        card.setStdAvailable(current != null || (plan != null && plan.getStdVersionId() != null));
        card.setStdName(stdName(batch.breedId()));
        if (plan != null) {
            card.setAppliedAt(BeijingTime.format(plan.getStdAppliedAt()));
            UserBriefVO appliedBy = lookup.userBrief(plan.getStdAppliedBy());
            card.setAppliedByName(appliedBy == null ? null : appliedBy.getName());
            card.setStdUpdated(plan.getStdVersionId() != null && current != null
                    && !plan.getStdVersionId().equals(current));
        } else {
            card.setStdUpdated(false);
        }
        card.setAdjustedCount(0);
        return card;
    }

    private FeedPlanVO.Stage toStage(BatchInfo batch, FeedPlanStage stage, List<FeedPlanMeal> meals, int dayAge) {
        FeedPlanVO.Stage vo = new FeedPlanVO.Stage();
        vo.setStageId(stage.getId());
        vo.setSortNo(stage.getSortNo());
        vo.setStageName(stage.getStageName());
        vo.setDayAgeStart(stage.getDayAgeStart());
        vo.setDayAgeEnd(stage.getDayAgeEnd());
        String state = state(stage, dayAge);
        vo.setState(state);
        vo.setEditable(!"ENDED".equals(state) && batch.active());
        int entryAge = batch.entryAgeDays() == null ? 0 : batch.entryAgeDays();
        LocalDate entry = batch.entryDate();
        LocalDate start = entry.plusDays(stage.getDayAgeStart() - entryAge);
        LocalDate end = stage.getDayAgeEnd() == null ? null : entry.plusDays(stage.getDayAgeEnd() - entryAge);
        if ("CURRENT".equals(state)) {
            vo.setExecStartDate(BeijingTime.format(start.isBefore(entry) ? entry : start));
            vo.setExecEndDate(BeijingTime.format(end));
            vo.setTodayPlanQty(FeedCalc.round2(nz(stage.getDailyQtyPerHead())
                    .multiply(BigDecimal.valueOf(batch.currentCount() == null ? 0 : batch.currentCount()))));
            vo.setPerMealQty(FeedCalc.planQty(stage.getDailyQtyPerHead(), batch.currentCount(), stage.getMealsPerDay()));
        } else if ("UPCOMING".equals(state)) {
            vo.setEnterDate(BeijingTime.format(start));
            vo.setEstimateDailyQty(FeedCalc.round2(nz(stage.getDailyQtyPerHead())
                    .multiply(BigDecimal.valueOf(batch.currentCount() == null ? 0 : batch.currentCount()))));
        }
        FeedStockPort.FeedBrief feed = lookup.feed(stage.getFeedId());
        vo.setFeedId(stage.getFeedId());
        vo.setFeedName(feed == null ? null : feed.feedName());
        vo.setLowStock(feed != null && feed.lowStock());
        vo.setDailyQtyPerHead(stage.getDailyQtyPerHead());
        vo.setStdDailyQty(stage.getStdDailyQty());
        vo.setMealsPerDay(stage.getMealsPerDay());
        List<FeedPlanVO.MealTime> times = new ArrayList<>();
        for (FeedPlanMeal meal : meals) {
            FeedPlanVO.MealTime time = new FeedPlanVO.MealTime();
            time.setMealNo(meal.getMealNo());
            time.setMealCode(meal.getMealCode());
            time.setMealName(meal.getMealName());
            time.setFeedTime(meal.getFeedTime() == null ? null : meal.getFeedTime().format(BeijingTime.TIME));
            times.add(time);
        }
        vo.setMealTimes(times);
        vo.setAdjustedFields(adjusted(stage, feed));
        return vo;
    }

    private List<String> adjusted(FeedPlanStage stage, FeedStockPort.FeedBrief feed) {
        List<String> fields = new ArrayList<>();
        if (stage.getStdDailyQty() != null && stage.getDailyQtyPerHead() != null
                && stage.getDailyQtyPerHead().compareTo(stage.getStdDailyQty()) != 0) {
            fields.add("daily_qty");
        }
        if (stage.getStdFeedName() != null && feed != null && !stage.getStdFeedName().equals(feed.feedName())) {
            fields.add("feed");
        }
        if (stage.getStdMealsPerDay() != null && stage.getMealsPerDay() != null
                && !stage.getStdMealsPerDay().equals(stage.getMealsPerDay())) {
            fields.add("meals");
        }
        if (stage.getStdMealTimes() != null) {
            String current = mealTimes(stage.getId());
            if (current != null && !current.equals(stage.getStdMealTimes())) {
                fields.add("meal_times");
            }
        }
        return fields;
    }

    private String mealTimes(Long stageId) {
        List<FeedPlanMeal> meals = mealMapper.selectList(Wrappers.<FeedPlanMeal>lambdaQuery()
                .eq(FeedPlanMeal::getStageId, stageId).orderByAsc(FeedPlanMeal::getMealNo));
        StringBuilder sb = new StringBuilder();
        for (FeedPlanMeal meal : meals) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(meal.getFeedTime().format(BeijingTime.TIME));
        }
        return sb.toString();
    }

    private boolean changed(FeedPlanStage old, PlanStage request) {
        return !java.util.Objects.equals(old.getStageName(), request.getStageName())
                || !java.util.Objects.equals(old.getDayAgeStart(), request.getDayAgeStart())
                || !java.util.Objects.equals(old.getDayAgeEnd(), request.getDayAgeEnd())
                || !java.util.Objects.equals(old.getFeedId(), request.getFeedId())
                || !java.util.Objects.equals(old.getDailyQtyPerHead(), request.getDailyQtyPerHead())
                || !java.util.Objects.equals(old.getMealsPerDay(), request.getMealsPerDay());
    }

    private String state(FeedPlanStage stage, int dayAge) {
        if (stage.getDayAgeEnd() != null && dayAge > stage.getDayAgeEnd()) {
            return "ENDED";
        }
        if (dayAge >= stage.getDayAgeStart()
                && (stage.getDayAgeEnd() == null || dayAge <= stage.getDayAgeEnd())) {
            return "CURRENT";
        }
        return "UPCOMING";
    }

    private FeedPlan plan(Long batchId) {
        return planMapper.selectOne(Wrappers.<FeedPlan>lambdaQuery()
                .eq(FeedPlan::getBatchId, batchId).last("LIMIT 1"));
    }

    private List<FeedPlanStage> stages(Long planId) {
        return stageMapper.selectList(Wrappers.<FeedPlanStage>lambdaQuery()
                .eq(FeedPlanStage::getPlanId, planId)
                .orderByAsc(FeedPlanStage::getSortNo)
                .orderByAsc(FeedPlanStage::getId));
    }

    private Map<Long, List<FeedPlanMeal>> meals(List<FeedPlanStage> stages) {
        Map<Long, List<FeedPlanMeal>> result = new LinkedHashMap<>();
        if (stages.isEmpty()) {
            return result;
        }
        List<Long> ids = stages.stream().map(FeedPlanStage::getId).toList();
        List<FeedPlanMeal> meals = mealMapper.selectList(Wrappers.<FeedPlanMeal>lambdaQuery()
                .in(FeedPlanMeal::getStageId, ids).orderByAsc(FeedPlanMeal::getMealNo));
        for (FeedPlanMeal meal : meals) {
            result.computeIfAbsent(meal.getStageId(), k -> new ArrayList<>()).add(meal);
        }
        return result;
    }

    private FeedStockPort.FeedBrief matchFeed(List<FeedStockPort.FeedBrief> feeds, String name) {
        if (feeds == null) {
            return null;
        }
        for (FeedStockPort.FeedBrief feed : feeds) {
            if (name != null && name.equals(feed.feedName())) {
                return feed;
            }
        }
        return feeds.isEmpty() ? null : feeds.get(0);
    }

    private String stdName(Long breedId) {
        String name = lookup.breedName(breedId);
        return name == null ? "品种标准" : name + "标准";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
