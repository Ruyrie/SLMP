package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.FeedStdVersion;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import com.zhimu.server.admin.base.entity.StdWeightVersion;
import com.zhimu.server.admin.base.event.StdWeightEffectiveEvent;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.FeedStdStageMapper;
import com.zhimu.server.admin.base.mapper.FeedStdVersionMapper;
import com.zhimu.server.admin.base.mapper.StdWeightPointMapper;
import com.zhimu.server.admin.base.mapper.StdWeightVersionMapper;
import com.zhimu.server.admin.base.request.FeedStdDraftRequest;
import com.zhimu.server.admin.base.request.StdPublishRequest;
import com.zhimu.server.admin.base.request.StdRollbackRequest;
import com.zhimu.server.admin.base.request.StdWeightDraftRequest;
import com.zhimu.server.admin.base.service.StdAdminService;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.StdCalculator;
import com.zhimu.server.admin.base.vo.FeedStdStageVO;
import com.zhimu.server.admin.base.vo.FeedStdVO;
import com.zhimu.server.admin.base.vo.FeedStdVersionVO;
import com.zhimu.server.admin.base.vo.StdImpactVO;
import com.zhimu.server.admin.base.vo.StdVersionVO;
import com.zhimu.server.admin.base.vo.StdWeightVO;
import com.zhimu.server.admin.base.vo.StdWeightVersionVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.Species;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** ADM-D07–D13 标准体重 / 饲喂标准。 */
@Service
@RequiredArgsConstructor
public class StdAdminServiceImpl implements StdAdminService {

    private static final Pattern MEAL_TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final BreedMapper breedMapper;
    private final CategoryMapper categoryMapper;
    private final StdWeightVersionMapper weightVersionMapper;
    private final StdWeightPointMapper weightPointMapper;
    private final FeedStdVersionMapper feedVersionMapper;
    private final FeedStdStageMapper feedStageMapper;
    private final DictItemMapper dictItemMapper;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    @Override
    public StdWeightVO weight(Long breedId) {
        requireBreed(breedId);
        StdWeightVO vo = new StdWeightVO();
        vo.setBreedId(breedId);
        StdWeightVersion online = latest(breedId, "PUBLISHED");
        Map<Integer, BigDecimal> onlineWeights = new LinkedHashMap<>();
        if (online != null) {
            List<StdWeightPoint> onlinePoints = points(online.getId());
            for (StdWeightPoint point : onlinePoints) {
                onlineWeights.put(point.getDayAge(), point.getStdWeight());
            }
            vo.setOnline(AdminBaseConvert.stdWeightVersion(online, onlinePoints, online.getToleranceRate(),
                    null, false));
        }
        StdWeightVersion scheduled = latest(breedId, "SCHEDULED");
        if (scheduled != null) {
            vo.setScheduled(AdminBaseConvert.stdWeightVersion(scheduled, points(scheduled.getId()),
                    scheduled.getToleranceRate(), null, false));
        }
        StdWeightVersion draft = draft(breedId);
        if (draft != null) {
            vo.setDraft(AdminBaseConvert.stdWeightVersion(draft, points(draft.getId()), draft.getToleranceRate(),
                    onlineWeights, true));
        }
        return vo;
    }

    @Override
    public FeedStdVO feed(Long breedId) {
        requireBreed(breedId);
        FeedStdVO vo = new FeedStdVO();
        vo.setBreedId(breedId);
        FeedStdVersion online = latestFeed(breedId, "PUBLISHED");
        Map<Integer, FeedStdStage> onlineBySort = new LinkedHashMap<>();
        if (online != null) {
            List<FeedStdStage> stages = stages(online.getId());
            for (FeedStdStage stage : stages) {
                onlineBySort.put(stage.getSortNo(), stage);
            }
            vo.setOnline(AdminBaseConvert.feedStdVersion(online, stages, null, false));
        }
        FeedStdVersion draft = draftFeed(breedId);
        if (draft != null) {
            vo.setDraft(AdminBaseConvert.feedStdVersion(draft, stages(draft.getId()), onlineBySort, true));
        }
        return vo;
    }

    @Override
    public List<StdVersionVO> versions(Long breedId, String stdType) {
        requireBreed(breedId);
        String type = stdType(stdType);
        List<StdVersionVO> result = new ArrayList<>();
        if ("weight".equals(type)) {
            for (StdWeightVersion version : weightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                    .eq(StdWeightVersion::getBreedId, breedId)
                    .isNotNull(StdWeightVersion::getVersionNo)
                    .orderByDesc(StdWeightVersion::getVersionNo))) {
                result.add(AdminBaseConvert.stdWeightVersionRecord(version, points(version.getId()),
                        version.getToleranceRate()));
            }
        } else {
            for (FeedStdVersion version : feedVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                    .eq(FeedStdVersion::getBreedId, breedId)
                    .isNotNull(FeedStdVersion::getVersionNo)
                    .orderByDesc(FeedStdVersion::getVersionNo))) {
                result.add(AdminBaseConvert.feedStdVersionRecord(version, stages(version.getId())));
            }
        }
        return result;
    }

    @Override
    public StdImpactVO impact(Long breedId, String stdType) {
        requireBreed(breedId);
        String type = stdType(stdType);
        boolean hasDraft = "weight".equals(type) ? draft(breedId) != null : draftFeed(breedId) != null;
        if (!hasDraft) {
            throw BaseErrors.stateChanged(BaseErrors.MSG_NO_DRAFT);
        }
        StdImpactVO vo = new StdImpactVO();
        vo.setActiveBatchCount(0);
        vo.setFarmCount(0);
        vo.setAffectedBatchCount(0);
        vo.setAppliedPlanCount(0);
        return vo;
    }

    // ==================== 草稿 ====================

    @Override
    @Transactional
    public StdWeightVO saveWeightDraft(Long breedId, StdWeightDraftRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Breed breed = requireBreed(breedId);
        Category category = categoryMapper.selectById(breed.getCategoryId());
        if (category != null && Boolean.TRUE.equals(category.getIsBreeding())) {
            throw BaseErrors.param("种畜品类不设标准体重");
        }
        validateWeight(breed, request);
        StdWeightVersion draft = draft(breedId);
        LocalDateTime now = BeijingTime.now();
        if (draft == null) {
            draft = new StdWeightVersion();
            draft.setBreedId(breedId);
            draft.setVersionNo(null);
            draft.setStatus("DRAFT");
            draft.setDraftFlag(1);
            draft.setToleranceRate(request.getToleranceRate());
            draft.setSuggestedWeighTimes(request.getSuggestedWeighTimes());
            draft.setEffectiveMode(request.getEffectiveMode());
            draft.setEffectiveDate("ON_DATE".equals(request.getEffectiveMode()) ? request.getEffectiveDate() : null);
            draft.setUpdatedBy(adminId);
            draft.setVersion(0);
            weightVersionMapper.insert(draft);
        } else {
            requireDraftVersion(request.getVersion(), draft.getVersion());
            int rows = weightVersionMapper.update(new StdWeightVersion(), Wrappers.<StdWeightVersion>lambdaUpdate()
                    .eq(StdWeightVersion::getId, draft.getId())
                    .eq(StdWeightVersion::getVersion, draft.getVersion())
                    .set(StdWeightVersion::getToleranceRate, request.getToleranceRate())
                    .set(StdWeightVersion::getSuggestedWeighTimes, request.getSuggestedWeighTimes())
                    .set(StdWeightVersion::getEffectiveMode, request.getEffectiveMode())
                    .set(StdWeightVersion::getEffectiveDate,
                            "ON_DATE".equals(request.getEffectiveMode()) ? request.getEffectiveDate() : null)
                    .set(StdWeightVersion::getUpdatedBy, adminId)
                    .set(StdWeightVersion::getUpdatedAt, now)
                    .set(StdWeightVersion::getVersion, draft.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
            weightPointMapper.delete(Wrappers.<StdWeightPoint>lambdaQuery()
                    .eq(StdWeightPoint::getVersionId, draft.getId()));
        }
        for (StdWeightDraftRequest.Point point : request.getPoints()) {
            StdWeightPoint entity = new StdWeightPoint();
            entity.setVersionId(draft.getId());
            entity.setDayAge(point.getDayAge());
            entity.setStdWeight(point.getStdWeight());
            weightPointMapper.insert(entity);
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_STD_WEIGHT").opAction("DRAFT_SAVE").bizType("STD_WEIGHT").bizId(draft.getId())
                .targetName(breed.getName()).searchText(breed.getName())
                .after(weight(breedId).getDraft()).build());
        return weight(breedId);
    }

    @Override
    @Transactional
    public FeedStdVO saveFeedDraft(Long breedId, FeedStdDraftRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Breed breed = requireBreed(breedId);
        validateFeed(request);
        FeedStdVersion draft = draftFeed(breedId);
        LocalDateTime now = BeijingTime.now();
        if (draft == null) {
            draft = new FeedStdVersion();
            draft.setBreedId(breedId);
            draft.setVersionNo(null);
            draft.setStatus("DRAFT");
            draft.setDraftFlag(1);
            draft.setDeviationRate(request.getDeviationRate());
            draft.setUpdatedBy(adminId);
            draft.setVersion(0);
            feedVersionMapper.insert(draft);
        } else {
            requireDraftVersion(request.getVersion(), draft.getVersion());
            int rows = feedVersionMapper.update(new FeedStdVersion(), Wrappers.<FeedStdVersion>lambdaUpdate()
                    .eq(FeedStdVersion::getId, draft.getId())
                    .eq(FeedStdVersion::getVersion, draft.getVersion())
                    .set(FeedStdVersion::getDeviationRate, request.getDeviationRate())
                    .set(FeedStdVersion::getUpdatedBy, adminId)
                    .set(FeedStdVersion::getUpdatedAt, now)
                    .set(FeedStdVersion::getVersion, draft.getVersion() + 1));
            if (rows == 0) {
                throw BaseErrors.versionConflict();
            }
            feedStageMapper.delete(Wrappers.<FeedStdStage>lambdaQuery()
                    .eq(FeedStdStage::getVersionId, draft.getId()));
        }
        int sort = 0;
        List<FeedStdDraftRequest.Stage> sorted = new ArrayList<>(request.getStages());
        sorted.sort(Comparator.comparing(FeedStdDraftRequest.Stage::getDayAgeStart));
        for (FeedStdDraftRequest.Stage stage : sorted) {
            FeedStdStage entity = new FeedStdStage();
            entity.setVersionId(draft.getId());
            entity.setSortNo(++sort);
            entity.setStageName(stage.getStageName().trim());
            entity.setStageShortName(stage.getStageShortName().trim());
            entity.setDayAgeStart(stage.getDayAgeStart());
            entity.setDayAgeEnd(stage.getDayAgeEnd());
            entity.setFeedTypeCode(stage.getFeedTypeCode());
            entity.setSuggestedFeedName(stage.getSuggestedFeedName().trim());
            entity.setDailyFeed(stage.getDailyFeed());
            entity.setMealsPerDay(stage.getMealsPerDay());
            entity.setMealTimes(AdminBaseConvert.joinMealTimes(stage.getMealTimes()));
            feedStageMapper.insert(entity);
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_FEED_STD").opAction("DRAFT_SAVE").bizType("FEED_STD").bizId(draft.getId())
                .targetName(breed.getName()).searchText(breed.getName())
                .after(feed(breedId).getDraft()).build());
        return feed(breedId);
    }

    @Override
    @Transactional
    public void discard(Long breedId, String stdType, Integer version) {
        AdminContext.requireAdminId();
        Breed breed = requireBreed(breedId);
        String type = stdType(stdType);
        if ("weight".equals(type)) {
            StdWeightVersion draft = draft(breedId);
            if (draft == null) {
                throw BaseErrors.stateChanged(BaseErrors.MSG_DRAFT_GONE);
            }
            requireDraftVersion(version, draft.getVersion());
            weightPointMapper.delete(Wrappers.<StdWeightPoint>lambdaQuery()
                    .eq(StdWeightPoint::getVersionId, draft.getId()));
            weightVersionMapper.deleteById(draft.getId());
            logs.record(OperationLogEntry.builder()
                    .opType("BASE_STD_WEIGHT").opAction("DISCARD").bizType("STD_WEIGHT").bizId(draft.getId())
                    .targetName(breed.getName()).searchText(breed.getName()).build());
        } else {
            FeedStdVersion draft = draftFeed(breedId);
            if (draft == null) {
                throw BaseErrors.stateChanged(BaseErrors.MSG_DRAFT_GONE);
            }
            requireDraftVersion(version, draft.getVersion());
            feedStageMapper.delete(Wrappers.<FeedStdStage>lambdaQuery()
                    .eq(FeedStdStage::getVersionId, draft.getId()));
            feedVersionMapper.deleteById(draft.getId());
            logs.record(OperationLogEntry.builder()
                    .opType("BASE_FEED_STD").opAction("DISCARD").bizType("FEED_STD").bizId(draft.getId())
                    .targetName(breed.getName()).searchText(breed.getName()).build());
        }
    }

    // ==================== 发布 / 回退 ====================

    @Override
    @Transactional
    public PublishResult publish(Long breedId, String stdType, StdPublishRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Breed breed = requireBreed(breedId);
        String type = stdType(stdType);
        if (!StringUtils.hasText(request.getUpdateNote())) {
            throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_PUBLISH);
        }
        if (request.getUpdateNote().length() > 100) {
            throw BaseErrors.field("update_note", "更新说明最多 100 个字");
        }
        if ("weight".equals(type)) {
            return publishWeight(breed, request, adminId);
        }
        return publishFeed(breed, request, adminId);
    }

    private PublishResult publishWeight(Breed breed, StdPublishRequest request, Long adminId) {
        Category category = categoryMapper.selectById(breed.getCategoryId());
        if (category != null && Boolean.TRUE.equals(category.getIsBreeding())) {
            throw BaseErrors.param("种畜品类不设标准体重");
        }
        StdWeightVersion draft = draft(breed.getId());
        if (draft == null) {
            throw BaseErrors.stateChanged(BaseErrors.MSG_DRAFT_GONE);
        }
        requireDraftVersion(request.getVersion(), draft.getVersion());
        List<StdWeightPoint> draftPoints = points(draft.getId());
        validateWeightPoints(draftPoints);
        if ("ON_DATE".equals(draft.getEffectiveMode())) {
            if (draft.getEffectiveDate() == null || !draft.getEffectiveDate().isAfter(BeijingTime.today())) {
                throw BaseErrors.field("effective_date", "生效日期必须晚于今天");
            }
        }
        int versionNo = nextVersionNo(breed.getId());
        LocalDateTime now = BeijingTime.now();
        if ("ON_DATE".equals(draft.getEffectiveMode())) {
            retireScheduled(breed.getId(), adminId);
        } else {
            retirePublishedAndScheduled(breed.getId(), adminId);
        }
        String status = "ON_DATE".equals(draft.getEffectiveMode()) ? "SCHEDULED" : "PUBLISHED";
        int rows = weightVersionMapper.update(new StdWeightVersion(), Wrappers.<StdWeightVersion>lambdaUpdate()
                .eq(StdWeightVersion::getId, draft.getId())
                .eq(StdWeightVersion::getVersion, draft.getVersion())
                .set(StdWeightVersion::getVersionNo, versionNo)
                .set(StdWeightVersion::getStatus, status)
                .set(StdWeightVersion::getDraftFlag, null)
                .set(StdWeightVersion::getUpdateNote, request.getUpdateNote())
                .set(StdWeightVersion::getPublishedBy, adminId)
                .set(StdWeightVersion::getPublishedAt, now)
                .set(StdWeightVersion::getEffectiveAt,
                        "IMMEDIATE".equals(draft.getEffectiveMode()) ? now : null)
                .set(StdWeightVersion::getUpdatedBy, adminId)
                .set(StdWeightVersion::getUpdatedAt, now)
                .set(StdWeightVersion::getVersion, draft.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        draft.setVersionNo(versionNo);
        draft.setStatus(status);
        draft.setUpdateNote(request.getUpdateNote());
        draft.setPublishedBy(adminId);
        draft.setPublishedAt(now);
        draft.setEffectiveAt("IMMEDIATE".equals(draft.getEffectiveMode()) ? now : null);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_STD_WEIGHT").opAction("PUBLISH").bizType("STD_WEIGHT").bizId(draft.getId())
                .targetName(breed.getName()).searchText(breed.getName()).reason(request.getUpdateNote())
                .after(AdminBaseConvert.stdWeightVersionRecord(draft, draftPoints, draft.getToleranceRate())).build());
        if ("IMMEDIATE".equals(draft.getEffectiveMode())) {
            publishWeightEffective(breed.getId(), draft.getId());
        }
        String message = "ON_DATE".equals(draft.getEffectiveMode())
                ? "已发布第 " + versionNo + " 版，将于 " + dateText(draft.getEffectiveDate()) + " 生效"
                : "已发布第 " + versionNo + " 版，用户端已按新标准重新计算";
        return new PublishResult(AdminBaseConvert.stdWeightVersionRecord(draft, draftPoints,
                draft.getToleranceRate()), message);
    }

    private PublishResult publishFeed(Breed breed, StdPublishRequest request, Long adminId) {
        FeedStdVersion draft = draftFeed(breed.getId());
        if (draft == null) {
            throw BaseErrors.stateChanged(BaseErrors.MSG_DRAFT_GONE);
        }
        requireDraftVersion(request.getVersion(), draft.getVersion());
        List<FeedStdStage> stages = stages(draft.getId());
        validateFeedEntities(stages);
        int versionNo = nextFeedVersionNo(breed.getId());
        LocalDateTime now = BeijingTime.now();
        FeedStdVersion old = latestFeed(breed.getId(), "PUBLISHED");
        if (old != null) {
            feedVersionMapper.update(new FeedStdVersion(), Wrappers.<FeedStdVersion>lambdaUpdate()
                    .eq(FeedStdVersion::getId, old.getId())
                    .set(FeedStdVersion::getStatus, "SUPERSEDED")
                    .set(FeedStdVersion::getUpdatedBy, adminId)
                    .set(FeedStdVersion::getUpdatedAt, now));
        }
        int rows = feedVersionMapper.update(new FeedStdVersion(), Wrappers.<FeedStdVersion>lambdaUpdate()
                .eq(FeedStdVersion::getId, draft.getId())
                .eq(FeedStdVersion::getVersion, draft.getVersion())
                .set(FeedStdVersion::getVersionNo, versionNo)
                .set(FeedStdVersion::getStatus, "PUBLISHED")
                .set(FeedStdVersion::getDraftFlag, null)
                .set(FeedStdVersion::getUpdateNote, request.getUpdateNote())
                .set(FeedStdVersion::getPublishedBy, adminId)
                .set(FeedStdVersion::getPublishedAt, now)
                .set(FeedStdVersion::getUpdatedBy, adminId)
                .set(FeedStdVersion::getUpdatedAt, now)
                .set(FeedStdVersion::getVersion, draft.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        draft.setVersionNo(versionNo);
        draft.setStatus("PUBLISHED");
        draft.setUpdateNote(request.getUpdateNote());
        draft.setPublishedBy(adminId);
        draft.setPublishedAt(now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_FEED_STD").opAction("PUBLISH").bizType("FEED_STD").bizId(draft.getId())
                .targetName(breed.getName()).searchText(breed.getName()).reason(request.getUpdateNote())
                .after(AdminBaseConvert.feedStdVersionRecord(draft, stages)).build());
        return new PublishResult(AdminBaseConvert.feedStdVersionRecord(draft, stages),
                "已发布第 " + versionNo + " 版");
    }

    @Override
    @Transactional
    public PublishResult rollback(Long breedId, String stdType, StdRollbackRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Breed breed = requireBreed(breedId);
        String type = stdType(stdType);
        if (!StringUtils.hasText(request.getReason())) {
            throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_ROLLBACK);
        }
        if (request.getReason().length() > 100) {
            throw BaseErrors.field("reason", "回退原因最多 100 个字");
        }
        if ("weight".equals(type)) {
            return rollbackWeight(breed, request, adminId);
        }
        return rollbackFeed(breed, request, adminId);
    }

    private PublishResult rollbackWeight(Breed breed, StdRollbackRequest request, Long adminId) {
        Category category = categoryMapper.selectById(breed.getCategoryId());
        if (category != null && Boolean.TRUE.equals(category.getIsBreeding())) {
            throw BaseErrors.param("种畜品类不设标准体重");
        }
        StdWeightVersion online = latest(breed.getId(), "PUBLISHED");
        if (online == null || !Objects.equals(request.getOnlineVersionNo(), online.getVersionNo())) {
            throw BaseErrors.versionConflict();
        }
        if (Objects.equals(request.getTargetVersionNo(), online.getVersionNo())) {
            throw BaseErrors.stateChanged("当前已是第 " + online.getVersionNo() + " 版，不需要回退");
        }
        StdWeightVersion target = version(breed.getId(), request.getTargetVersionNo());
        if (target == null) {
            throw BaseErrors.notFound();
        }
        if (!"PUBLISHED".equals(target.getStatus()) && !"SUPERSEDED".equals(target.getStatus())) {
            throw BaseErrors.stateChanged("该版本还未生效，不能回退");
        }
        int versionNo = nextVersionNo(breed.getId());
        LocalDateTime now = BeijingTime.now();
        retirePublishedAndScheduled(breed.getId(), adminId);
        StdWeightVersion created = new StdWeightVersion();
        created.setBreedId(breed.getId());
        created.setVersionNo(versionNo);
        created.setStatus("PUBLISHED");
        created.setDraftFlag(null);
        created.setToleranceRate(target.getToleranceRate());
        created.setSuggestedWeighTimes(target.getSuggestedWeighTimes());
        created.setEffectiveMode("IMMEDIATE");
        created.setEffectiveDate(null);
        created.setEffectiveAt(now);
        created.setUpdateNote(request.getReason());
        created.setRollbackFromNo(target.getVersionNo());
        created.setPublishedBy(adminId);
        created.setPublishedAt(now);
        created.setUpdatedBy(adminId);
        created.setVersion(0);
        weightVersionMapper.insert(created);
        for (StdWeightPoint point : points(target.getId())) {
            StdWeightPoint copy = new StdWeightPoint();
            copy.setVersionId(created.getId());
            copy.setDayAge(point.getDayAge());
            copy.setStdWeight(point.getStdWeight());
            weightPointMapper.insert(copy);
        }
        List<StdWeightPoint> points = points(created.getId());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_STD_WEIGHT").opAction("ROLLBACK").bizType("STD_WEIGHT").bizId(created.getId())
                .targetName(breed.getName()).searchText(breed.getName()).reason(request.getReason())
                .before(Map.of("version_no", online.getVersionNo()))
                .after(Map.of("version_no", versionNo, "rollback_from_no", target.getVersionNo())).build());
        publishWeightEffective(breed.getId(), created.getId());
        return new PublishResult(AdminBaseConvert.stdWeightVersionRecord(created, points,
                created.getToleranceRate()),
                "已回退到第 " + target.getVersionNo() + " 版的内容，生成第 " + versionNo + " 版");
    }

    private PublishResult rollbackFeed(Breed breed, StdRollbackRequest request, Long adminId) {
        FeedStdVersion online = latestFeed(breed.getId(), "PUBLISHED");
        if (online == null || !Objects.equals(request.getOnlineVersionNo(), online.getVersionNo())) {
            throw BaseErrors.versionConflict();
        }
        if (Objects.equals(request.getTargetVersionNo(), online.getVersionNo())) {
            throw BaseErrors.stateChanged("当前已是第 " + online.getVersionNo() + " 版，不需要回退");
        }
        FeedStdVersion target = feedVersion(breed.getId(), request.getTargetVersionNo());
        if (target == null) {
            throw BaseErrors.notFound();
        }
        if (!"PUBLISHED".equals(target.getStatus()) && !"SUPERSEDED".equals(target.getStatus())) {
            throw BaseErrors.stateChanged("该版本还未生效，不能回退");
        }
        int versionNo = nextFeedVersionNo(breed.getId());
        LocalDateTime now = BeijingTime.now();
        feedVersionMapper.update(new FeedStdVersion(), Wrappers.<FeedStdVersion>lambdaUpdate()
                .eq(FeedStdVersion::getId, online.getId())
                .set(FeedStdVersion::getStatus, "SUPERSEDED")
                .set(FeedStdVersion::getUpdatedBy, adminId)
                .set(FeedStdVersion::getUpdatedAt, now));
        FeedStdVersion created = new FeedStdVersion();
        created.setBreedId(breed.getId());
        created.setVersionNo(versionNo);
        created.setStatus("PUBLISHED");
        created.setDraftFlag(null);
        created.setDeviationRate(target.getDeviationRate());
        created.setUpdateNote(request.getReason());
        created.setRollbackFromNo(target.getVersionNo());
        created.setPublishedBy(adminId);
        created.setPublishedAt(now);
        created.setUpdatedBy(adminId);
        created.setVersion(0);
        feedVersionMapper.insert(created);
        List<FeedStdStage> targetStages = stages(target.getId());
        for (FeedStdStage stage : targetStages) {
            FeedStdStage copy = new FeedStdStage();
            copy.setVersionId(created.getId());
            copy.setSortNo(stage.getSortNo());
            copy.setStageName(stage.getStageName());
            copy.setStageShortName(stage.getStageShortName());
            copy.setDayAgeStart(stage.getDayAgeStart());
            copy.setDayAgeEnd(stage.getDayAgeEnd());
            copy.setFeedTypeCode(stage.getFeedTypeCode());
            copy.setSuggestedFeedName(stage.getSuggestedFeedName());
            copy.setDailyFeed(stage.getDailyFeed());
            copy.setMealsPerDay(stage.getMealsPerDay());
            copy.setMealTimes(stage.getMealTimes());
            feedStageMapper.insert(copy);
        }
        List<FeedStdStage> createdStages = stages(created.getId());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_FEED_STD").opAction("ROLLBACK").bizType("FEED_STD").bizId(created.getId())
                .targetName(breed.getName()).searchText(breed.getName()).reason(request.getReason())
                .before(Map.of("version_no", online.getVersionNo()))
                .after(Map.of("version_no", versionNo, "rollback_from_no", target.getVersionNo())).build());
        return new PublishResult(AdminBaseConvert.feedStdVersionRecord(created, createdStages),
                "已回退到第 " + target.getVersionNo() + " 版的内容，生成第 " + versionNo + " 版");
    }

    // ==================== 校验 ====================

    private void validateWeight(Breed breed, StdWeightDraftRequest request) {
        if (request.getToleranceRate() == null || request.getToleranceRate().compareTo(new BigDecimal("0.01")) < 0
                || request.getToleranceRate().compareTo(new BigDecimal("0.50")) > 0) {
            throw BaseErrors.field("tolerance_rate", "允许偏差必须在 0.01–0.50 之间");
        }
        if (request.getSuggestedWeighTimes() == null || request.getSuggestedWeighTimes() < 1
                || request.getSuggestedWeighTimes() > 8) {
            throw BaseErrors.field("suggested_weigh_times", "建议每月称重次数必须在 1–8 之间");
        }
        if (!"IMMEDIATE".equals(request.getEffectiveMode()) && !"ON_DATE".equals(request.getEffectiveMode())) {
            throw BaseErrors.field("effective_mode", "请选择生效方式");
        }
        if ("ON_DATE".equals(request.getEffectiveMode())) {
            if (request.getEffectiveDate() == null) {
                throw BaseErrors.field("effective_date", "请选择生效日期");
            }
            if (!request.getEffectiveDate().isAfter(BeijingTime.today())) {
                throw BaseErrors.field("effective_date", "生效日期必须晚于今天");
            }
        }
        if (request.getPoints() == null || request.getPoints().size() < 2) {
            throw BaseErrors.field("points", "至少填写 2 个日龄点");
        }
        Set<Integer> ages = new HashSet<>();
        List<StdWeightDraftRequest.Point> sorted = new ArrayList<>(request.getPoints());
        sorted.sort(Comparator.comparing(StdWeightDraftRequest.Point::getDayAge));
        BigDecimal previous = null;
        for (StdWeightDraftRequest.Point point : sorted) {
            if (point.getDayAge() == null || point.getStdWeight() == null) {
                throw BaseErrors.field("points", "请填写日龄与标准体重");
            }
            if (!ages.add(point.getDayAge())) {
                throw BaseErrors.field("points", "日龄不能重复");
            }
            if (point.getStdWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw BaseErrors.field("points", "标准体重必须大于 0");
            }
            if (previous != null && point.getStdWeight().compareTo(previous) < 0) {
                throw BaseErrors.field("points", "标准体重不能低于前一个日龄");
            }
            previous = point.getStdWeight();
        }
    }

    private void validateWeightPoints(List<StdWeightPoint> points) {
        if (points == null || points.size() < 2) {
            throw BaseErrors.field("points", "至少填写 2 个日龄点");
        }
        BigDecimal previous = null;
        for (StdWeightPoint point : StdCalculator.sorted(points)) {
            if (previous != null && point.getStdWeight().compareTo(previous) < 0) {
                throw BaseErrors.field("points", "标准体重不能低于前一个日龄");
            }
            previous = point.getStdWeight();
        }
    }

    private void validateFeed(FeedStdDraftRequest request) {
        if (request.getDeviationRate() == null
                || request.getDeviationRate().compareTo(new BigDecimal("0.01")) < 0
                || request.getDeviationRate().compareTo(new BigDecimal("0.50")) > 0) {
            throw BaseErrors.field("deviation_rate", "投喂异常判定默认值必须在 0.01–0.50 之间");
        }
        if (request.getStages() == null || request.getStages().isEmpty()) {
            throw BaseErrors.field("stages", "请至少填写 1 个饲喂阶段");
        }
        List<FeedStdDraftRequest.Stage> sorted = new ArrayList<>(request.getStages());
        sorted.sort(Comparator.comparing(FeedStdDraftRequest.Stage::getDayAgeStart,
                Comparator.nullsFirst(Integer::compareTo)));
        Integer previousEnd = null;
        for (int i = 0; i < sorted.size(); i++) {
            FeedStdDraftRequest.Stage stage = sorted.get(i);
            if (i == 0 && (stage.getDayAgeStart() == null || stage.getDayAgeStart() < 1)) {
                throw BaseErrors.field("stages", "首段起始日龄必须大于 0");
            }
            if (stage.getDayAgeStart() == null) {
                throw BaseErrors.field("stages", "请填写起始日龄");
            }
            if (previousEnd != null && stage.getDayAgeStart() != previousEnd + 1) {
                throw BaseErrors.field("stages", "各阶段日龄必须连续且不重叠");
            }
            if (stage.getDayAgeEnd() != null) {
                if (stage.getDayAgeStart() == null || stage.getDayAgeEnd() < stage.getDayAgeStart()) {
                    throw BaseErrors.field("stages", "各阶段日龄必须连续且不重叠");
                }
                if (i == sorted.size() - 1) {
                    previousEnd = stage.getDayAgeEnd();
                } else {
                    previousEnd = stage.getDayAgeEnd();
                }
            } else if (i != sorted.size() - 1) {
                throw BaseErrors.field("stages", "只有最后一个阶段可以不填结束日龄");
            }
            if (!StringUtils.hasText(stage.getStageName()) || !StringUtils.hasText(stage.getStageShortName())
                    || !StringUtils.hasText(stage.getSuggestedFeedName())) {
                throw BaseErrors.field("stages", "请填写阶段名称与建议饲料名称");
            }
            if (stage.getStageName().trim().length() > 10 || stage.getStageShortName().trim().length() > 10) {
                throw BaseErrors.field("stages", "阶段名称最多 10 个字");
            }
            if (stage.getSuggestedFeedName().trim().length() > 20) {
                throw BaseErrors.field("stages", "建议饲料名称最多 20 个字");
            }
            if (stage.getDailyFeed() == null || stage.getDailyFeed().compareTo(BigDecimal.ZERO) <= 0) {
                throw BaseErrors.field("stages", "每日投喂量必须大于 0");
            }
            if (stage.getMealsPerDay() == null || stage.getMealsPerDay() < 1 || stage.getMealsPerDay() > 4) {
                throw BaseErrors.field("stages", "每日餐次必须在 1–4 之间");
            }
            List<String> times = stage.getMealTimes();
            if (times == null || times.size() != stage.getMealsPerDay()) {
                throw BaseErrors.field("stages", "投喂时间个数要与餐次一致");
            }
            if (new HashSet<>(times).size() != times.size()) {
                throw BaseErrors.field("stages", "投喂时间不能重复");
            }
            for (String time : times) {
                if (time == null || !MEAL_TIME.matcher(time.trim()).matches()) {
                    throw BaseErrors.field("stages", "投喂时间格式应为 HH:mm");
                }
            }
            if (StringUtils.hasText(stage.getFeedTypeCode()) && feedTypeEnabled(stage.getFeedTypeCode()) == false) {
                throw BaseErrors.field("stages", "请选择启用的饲料类型");
            }
        }
    }

    private void validateFeedEntities(List<FeedStdStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw BaseErrors.field("stages", "请至少填写 1 个饲喂阶段");
        }
        Integer previousEnd = null;
        for (int i = 0; i < stages.size(); i++) {
            FeedStdStage stage = stages.get(i);
            if (previousEnd != null && !Objects.equals(stage.getDayAgeStart(), previousEnd + 1)) {
                throw BaseErrors.field("stages", "各阶段日龄必须连续且不重叠");
            }
            if (stage.getDayAgeEnd() == null && i != stages.size() - 1) {
                throw BaseErrors.field("stages", "只有最后一个阶段可以不填结束日龄");
            }
            previousEnd = stage.getDayAgeEnd();
        }
    }

    private boolean feedTypeEnabled(String code) {
        return dictItemMapper.selectCount(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, "FEED_TYPE")
                .eq(DictItem::getCode, code)
                .eq(DictItem::getStatus, "ENABLED")) > 0;
    }

    // ==================== 内部工具 ====================

    private Breed requireBreed(Long breedId) {
        Breed breed = breedId == null ? null : breedMapper.selectById(breedId);
        if (breed == null) {
            throw BaseErrors.notFound();
        }
        return breed;
    }

    private static String stdType(String stdType) {
        String type = stdType == null ? "" : stdType.trim().toLowerCase();
        if (!"weight".equals(type) && !"feed".equals(type)) {
            throw BaseErrors.notFound();
        }
        return type;
    }

    private static void requireDraftVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(requestVersion, currentVersion)) {
            throw BaseErrors.versionConflict();
        }
    }

    private List<StdWeightPoint> points(Long versionId) {
        return StdCalculator.sorted(weightPointMapper.selectList(Wrappers.<StdWeightPoint>lambdaQuery()
                .eq(StdWeightPoint::getVersionId, versionId)));
    }

    private List<FeedStdStage> stages(Long versionId) {
        List<FeedStdStage> list = new ArrayList<>(feedStageMapper.selectList(Wrappers.<FeedStdStage>lambdaQuery()
                .eq(FeedStdStage::getVersionId, versionId)
                .orderByAsc(FeedStdStage::getSortNo)));
        list.sort(Comparator.comparing(FeedStdStage::getSortNo));
        return list;
    }

    private StdWeightVersion latest(Long breedId, String status) {
        List<StdWeightVersion> list = weightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                .eq(StdWeightVersion::getBreedId, breedId)
                .eq(StdWeightVersion::getStatus, status)
                .orderByDesc(StdWeightVersion::getVersionNo)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private FeedStdVersion latestFeed(Long breedId, String status) {
        List<FeedStdVersion> list = feedVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                .eq(FeedStdVersion::getBreedId, breedId)
                .eq(FeedStdVersion::getStatus, status)
                .orderByDesc(FeedStdVersion::getVersionNo)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private StdWeightVersion draft(Long breedId) {
        List<StdWeightVersion> list = weightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                .eq(StdWeightVersion::getBreedId, breedId)
                .eq(StdWeightVersion::getDraftFlag, 1)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private FeedStdVersion draftFeed(Long breedId) {
        List<FeedStdVersion> list = feedVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                .eq(FeedStdVersion::getBreedId, breedId)
                .eq(FeedStdVersion::getDraftFlag, 1)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private StdWeightVersion version(Long breedId, Integer versionNo) {
        List<StdWeightVersion> list = weightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                .eq(StdWeightVersion::getBreedId, breedId)
                .eq(StdWeightVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private FeedStdVersion feedVersion(Long breedId, Integer versionNo) {
        List<FeedStdVersion> list = feedVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                .eq(FeedStdVersion::getBreedId, breedId)
                .eq(FeedStdVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private int nextVersionNo(Long breedId) {
        List<StdWeightVersion> list = weightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                .eq(StdWeightVersion::getBreedId, breedId)
                .isNotNull(StdWeightVersion::getVersionNo)
                .orderByDesc(StdWeightVersion::getVersionNo)
                .last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getVersionNo() == null ? 1 : list.get(0).getVersionNo() + 1;
    }

    private int nextFeedVersionNo(Long breedId) {
        List<FeedStdVersion> list = feedVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                .eq(FeedStdVersion::getBreedId, breedId)
                .isNotNull(FeedStdVersion::getVersionNo)
                .orderByDesc(FeedStdVersion::getVersionNo)
                .last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getVersionNo() == null ? 1 : list.get(0).getVersionNo() + 1;
    }

    private void retirePublishedAndScheduled(Long breedId, Long adminId) {
        LocalDateTime now = BeijingTime.now();
        weightVersionMapper.update(new StdWeightVersion(), Wrappers.<StdWeightVersion>lambdaUpdate()
                .eq(StdWeightVersion::getBreedId, breedId)
                .in(StdWeightVersion::getStatus, List.of("PUBLISHED", "SCHEDULED"))
                .set(StdWeightVersion::getStatus, "SUPERSEDED")
                .set(StdWeightVersion::getUpdatedBy, adminId)
                .set(StdWeightVersion::getUpdatedAt, now));
    }

    private void retireScheduled(Long breedId, Long adminId) {
        LocalDateTime now = BeijingTime.now();
        weightVersionMapper.update(new StdWeightVersion(), Wrappers.<StdWeightVersion>lambdaUpdate()
                .eq(StdWeightVersion::getBreedId, breedId)
                .eq(StdWeightVersion::getStatus, "SCHEDULED")
                .set(StdWeightVersion::getStatus, "SUPERSEDED")
                .set(StdWeightVersion::getUpdatedBy, adminId)
                .set(StdWeightVersion::getUpdatedAt, now));
    }

    private void publishWeightEffective(Long breedId, Long versionId) {
        AfterCommit.run(() -> eventPublisher.publishEvent(new StdWeightEffectiveEvent(breedId, versionId)));
    }

    private static String dateText(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }
}
