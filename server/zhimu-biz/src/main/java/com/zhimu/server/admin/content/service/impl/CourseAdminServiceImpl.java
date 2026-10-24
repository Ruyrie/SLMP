package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.entity.ContentApplicable;
import com.zhimu.server.admin.content.mapper.ContentApplicableMapper;
import com.zhimu.server.admin.content.mapper.ContentStatMapper;
import com.zhimu.server.admin.content.query.AdminCourseQuery;
import com.zhimu.server.admin.content.request.CourseSaveRequest;
import com.zhimu.server.admin.content.request.OffShelfRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.service.CourseAdminService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentMediaSupport;
import com.zhimu.server.admin.content.support.ContentNames;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.CourseDetailVO;
import com.zhimu.server.admin.content.vo.CourseRowVO;
import com.zhimu.server.admin.content.vo.CourseSlotVO;
import com.zhimu.server.admin.content.vo.CourseVO;
import com.zhimu.server.admin.content.vo.CoverCandidateVO;
import com.zhimu.server.admin.content.vo.TranscodeStateVO;
import com.zhimu.server.admin.content.vo.VideoInfoVO;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CourseMedia;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.CourseCategoryMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CourseMediaMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.entity.FileVariant;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.TranscodeService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.enums.VariantCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 养殖课堂课程管理（ADM-C22–C35）。
 */
@Service
@RequiredArgsConstructor
public class CourseAdminServiceImpl implements CourseAdminService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ON_SHELF", "OFF_SHELF");

    private final CourseMapper courseMapper;
    private final CourseTopicMapper topicMapper;
    private final CourseCategoryMapper categoryMapper;
    private final CourseMediaMapper mediaMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ContentApplicableMapper applicableMapper;
    private final ContentStatMapper statMapper;
    private final ContentCategoryAdminService categoryService;
    private final ContentMediaSupport media;
    private final ContentNames names;
    private final ContentLogs logs;
    private final TranscodeService transcodeService;

    @Override
    public PageVO<CourseRowVO> page(AdminCourseQuery query) {
        int pageNo = query.pageOrDefault();
        int size = query.sizeOrDefault();
        var wrapper = baseWrapper(query);
        if (query.getTopicId() != null) {
            wrapper.orderByAsc(Course::getSectionNo).orderByAsc(Course::getId);
        } else {
            wrapper.orderByDesc(Course::getUpdatedAt).orderByDesc(Course::getId);
        }
        Page<Course> page = courseMapper.selectPage(new Page<>(pageNo, size), wrapper);

        Map<Long, String> adminNames = names.adminNames(page.getRecords().stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getUpdatedBy(), c.getCreatedBy(), c.getOnShelfBy()))
                .filter(Objects::nonNull).toList());
        Map<Long, StatAggregatesPlay> plays = playAggAll();
        Map<Long, Long> finishes = finishAgg();
        Map<Long, List<CourseMedia>> mediaMap = mediaByCourse(page.getRecords());

        List<CourseRowVO> rows = new ArrayList<>();
        for (Course course : page.getRecords()) {
            rows.add(toRow(course, adminNames, plays, finishes, mediaMap));
        }
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", Math.toIntExact(count(query, null)));
        tabs.put("ON_SHELF", Math.toIntExact(count(query, "ON_SHELF")));
        tabs.put("OFF_SHELF", Math.toIntExact(count(query, "OFF_SHELF")));
        tabs.put("DRAFT", Math.toIntExact(count(query, "DRAFT")));
        return PageVO.of(rows, page.getTotal(), pageNo, size).withTabCounts(tabs);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Course> baseWrapper(
            AdminCourseQuery query) {
        var wrapper = Wrappers.<Course>lambdaQuery();
        if (query.getTopicId() != null) {
            wrapper.eq(Course::getTopicId, query.getTopicId());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(Course::getCategoryId, query.getCategoryId());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()
                && !"ALL".equalsIgnoreCase(query.getStatus())) {
            wrapper.eq(Course::getStatus, query.getStatus().toUpperCase());
        }
        if (query.getTranscodeStatus() != null && !query.getTranscodeStatus().isBlank()
                && !"ALL".equalsIgnoreCase(query.getTranscodeStatus())) {
            wrapper.eq(Course::getTranscodeStatus, query.getTranscodeStatus().toUpperCase());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Course::getTitle, keyword).or().like(Course::getTopicName, keyword));
        }
        return wrapper;
    }

    private long count(AdminCourseQuery query, String status) {
        var wrapper = baseWrapper(query);
        wrapper.eq(status != null, Course::getStatus, status);
        return courseMapper.selectCount(wrapper);
    }

    @Override
    public CourseDetailVO detail(Long id) {
        Course course = requireCourse(id);
        Map<Long, String> adminNames = names.adminNames(java.util.stream.Stream.of(
                course.getUpdatedBy(), course.getCreatedBy(), course.getOnShelfBy()).filter(Objects::nonNull).toList());
        CourseDetailVO vo = new CourseDetailVO();
        fillRow(vo, course, adminNames, playAggAll(), finishAgg(), mediaByCourse(List.of(course)));
        vo.setIntro(course.getIntro());
        vo.setKnowledgeId(course.getKnowledgeId());
        if (course.getKnowledgeId() != null) {
            AiKnowledge knowledge = knowledgeMapper.selectById(course.getKnowledgeId());
            vo.setKnowledgeTitle(knowledge == null ? null : knowledge.getTitle());
        }
        vo.setApplicableCategoryIds(applicableMapper.selectByBiz(BIZ_COURSE, id).stream()
                .map(ContentApplicable::getCategoryId).toList());
        FileEntity video = course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        if (video != null) {
            VideoInfoVO info = media.videoInfo(video);
            vo.setVideo(info);
            vo.setTranscodeError(video.getTranscodeError());
            vo.setUploadStatus(video.getUploadStatus());
        }
        vo.setCoverFileId(course.getCoverFileId());
        vo.setCoverSource(course.getCoverSource());
        vo.setCoverCandidates(media.coverCandidates(course.getVideoFileId()));
        vo.setOnShelfAt(course.getOnShelfAt());
        vo.setOnShelfByName(adminNames.get(course.getOnShelfBy()));
        vo.setOffShelfAt(course.getOffShelfAt());
        vo.setOffReason(course.getOffReason());
        vo.setVersion(course.getVersion());
        return vo;
    }

    private static final String BIZ_COURSE = "COURSE";

    @Override
    @Transactional
    public CourseVO create(CourseSaveRequest request) {
        String submit = submitAction(request, "DRAFT");
        FileEntity video = request.getVideoFileId() == null ? null : media.requireCourseVideo(request.getVideoFileId());
        CourseCategory category = resolveCategory(request.getCategoryId(), submit);
        CourseTopic topic = resolveTopic(request.getTopicId(), request.getSectionNo(), submit);
        AiKnowledge knowledge = resolveKnowledge(request.getKnowledgeId());
        validateTitle(request.getTitle(), video);
        validateApplicable(request.getApplicableCategoryIds());

        String title = titleOf(request.getTitle(), video);
        FileEntity cover = resolveCover(request, video, submit);
        LocalDateTime now = BeijingTime.now();
        Long adminId = AdminContext.requireAdminId();

        Course course = new Course();
        course.setTitle(title);
        course.setIntro(blankToNull(request.getIntro()));
        course.setCategoryId(category.getId());
        course.setCategoryName(category.getName());
        course.setTopicId(topic == null ? null : topic.getId());
        course.setTopicName(topic == null ? null : topic.getName());
        course.setSectionNo(topic == null ? null : request.getSectionNo());
        course.setKnowledgeId(knowledge == null ? null : knowledge.getId());
        course.setVideoFileId(video == null ? null : video.getId());
        course.setDurationSec(video == null ? null : durationSec(video));
        course.setTranscodeStatus(video == null ? TranscodeStatus.NONE.name() : transcodeOf(video));
        course.setCoverFileId(cover == null ? null : cover.getId());
        course.setCoverSource(cover == null ? null : request.getCoverSource());
        course.setCoverUrl(cover == null ? null : media.publicUrl(cover));
        course.setSourceLabel("平台知识库");
        course.setStatus("DRAFT");
        course.setPlayCount(0);
        course.setViewerCount(0);
        course.setFinishCount(0);
        course.setUpdatedBy(adminId);
        course.setVersion(0);
        int shifted = shiftSections(course.getTopicId(), course.getSectionNo(), null);
        if ("ON_SHELF".equals(submit)) {
            checkShelfReady(title, category, topic, course.getSectionNo(), video, cover);
            course.setStatus("ON_SHELF");
            course.setOnShelfAt(now);
            course.setOnShelfBy(adminId);
            course.setPublishedAt(now);
        }
        courseMapper.insert(course);
        syncApplicable(course.getId(), request.getApplicableCategoryIds());
        media.markBound(course.getVideoFileId(), true);
        media.markBound(course.getCoverFileId(), true);
        writeLog("CREATE", course.getId(), title, null, null, Map.of("status", course.getStatus()));
        return vo(course, shifted);
    }

    @Override
    @Transactional
    public CourseVO update(Long id, CourseSaveRequest request) {
        Course course = requireCourse(id);
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), course.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String submit = submitAction(request, "ON_SHELF".equals(course.getStatus()) ? "ON_SHELF" : "DRAFT");
        FileEntity video = request.getVideoFileId() == null ? null : media.requireCourseVideo(request.getVideoFileId());
        if ("ON_SHELF".equals(course.getStatus()) && request.getVideoFileId() != null
                && !Objects.equals(request.getVideoFileId(), course.getVideoFileId())) {
            throw ContentErrors.stateChanged("请先下架课程再更换视频");
        }
        FileEntity effectiveVideo = video != null ? video
                : course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        CourseCategory category = request.getCategoryId() != null
                ? categoryService.requireEnabled(request.getCategoryId())
                : categoryService.requireEnabled(course.getCategoryId());
        CourseTopic topic = resolveTopic(request.getTopicId(), request.getSectionNo(), submit);
        AiKnowledge knowledge = resolveKnowledge(request.getKnowledgeId());
        validateTitle(request.getTitle(), effectiveVideo);
        validateApplicable(request.getApplicableCategoryIds());
        String title = titleOf(request.getTitle(), effectiveVideo);
        FileEntity cover = request.getCoverFileId() == null && course.getCoverFileId() != null
                ? fileMapper.selectById(course.getCoverFileId())
                : resolveCover(request, effectiveVideo, submit);

        Map<String, Object> before = snapshot(course);
        Long oldVideo = course.getVideoFileId();
        Long oldCover = course.getCoverFileId();
        Integer section = topic == null ? null : request.getSectionNo();
        boolean sectionChanged = !Objects.equals(course.getTopicId(), topic == null ? null : topic.getId())
                || !Objects.equals(course.getSectionNo(), section);
        int shifted = sectionChanged ? shiftSections(topic == null ? null : topic.getId(), section, id) : 0;

        course.setTitle(title);
        course.setIntro(blankToNull(request.getIntro()));
        course.setCategoryId(category.getId());
        course.setCategoryName(category.getName());
        course.setTopicId(topic == null ? null : topic.getId());
        course.setTopicName(topic == null ? null : topic.getName());
        course.setSectionNo(section);
        course.setKnowledgeId(knowledge == null ? null : knowledge.getId());
        if (video != null) {
            course.setVideoFileId(video.getId());
            course.setDurationSec(durationSec(video));
            course.setTranscodeStatus(transcodeOf(video));
        }
        if (cover != null) {
            course.setCoverFileId(cover.getId());
            course.setCoverSource(request.getCoverSource());
            course.setCoverUrl(media.publicUrl(cover));
        }
        course.setUpdatedBy(AdminContext.requireAdminId());
        if ("ON_SHELF".equals(submit)) {
            checkShelfReady(title, category, topic, section, effectiveVideo, cover != null ? cover
                    : course.getCoverFileId() == null ? null : fileMapper.selectById(course.getCoverFileId()));
            if (!"ON_SHELF".equals(course.getStatus())) {
                course.setStatus("ON_SHELF");
                course.setOnShelfAt(BeijingTime.now());
                course.setOnShelfBy(AdminContext.requireAdminId());
                course.setPublishedAt(BeijingTime.now());
            }
        } else if (!"ON_SHELF".equals(course.getStatus())) {
            course.setStatus("DRAFT");
        }
        if (courseMapper.updateById(course) == 0) {
            throw ContentErrors.versionConflict();
        }
        if (video != null && !Objects.equals(oldVideo, video.getId())) {
            media.markBound(oldVideo, false);
            media.markBound(video.getId(), true);
        }
        if (cover != null && !Objects.equals(oldCover, cover.getId())) {
            media.markBound(oldCover, false);
            media.markBound(cover.getId(), true);
        }
        syncApplicable(id, request.getApplicableCategoryIds());
        writeLog("UPDATE", id, title, null, before, snapshot(course));
        return vo(course, shifted);
    }

    @Override
    public List<TranscodeStateVO> transcodeStates(String ids) {
        if (ids == null || ids.isBlank()) {
            throw ContentErrors.param("请选择要查询的课程");
        }
        List<Long> courseIds;
        try {
            courseIds = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException e) {
            throw ContentErrors.param("课程编号不正确");
        }
        if (courseIds.size() > 20) {
            throw ContentErrors.param("一次最多查询 20 个课程");
        }
        List<TranscodeStateVO> result = new ArrayList<>();
        for (Long courseId : courseIds) {
            Course course = courseMapper.selectById(courseId);
            if (course != null) {
                result.add(state(course));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public TranscodeStateVO retryTranscode(Long id) {
        Course course = requireCourse(id);
        if (!TranscodeStatus.FAILED.name().equals(course.getTranscodeStatus())) {
            throw ContentErrors.stateChanged("只有转码失败的课程可以重试");
        }
        FileEntity video = course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        if (video == null) {
            throw ContentErrors.stateChanged("原片已清理，请重新上传");
        }
        if (!media.rawExists(video)) {
            throw new ContentErrorsDeletedException();
        }
        transcodeService.retry(video.getId());
        course.setTranscodeStatus(TranscodeStatus.WAITING.name());
        course.setUpdatedBy(AdminContext.requireAdminId());
        courseMapper.updateById(course);
        writeLog("RETRY_TRANSCODE", id, course.getTitle(), null, null,
                Map.of("transcode_status", TranscodeStatus.WAITING.name()));
        return state(course);
    }

    /** 41001 原片已清理，请重新上传 */
    private static final class ContentErrorsDeletedException extends com.zhimu.server.common.error.BizException {
        private ContentErrorsDeletedException() {
            super(com.zhimu.server.common.error.ErrorCode.RECORD_DELETED, "原片已清理，请重新上传");
        }
    }

    @Override
    @Transactional
    public CourseVO onShelf(Long id, Integer version) {
        Course course = requireCourse(id);
        if (version == null || !Objects.equals(version, course.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (!"DRAFT".equals(course.getStatus()) && !"OFF_SHELF".equals(course.getStatus())) {
            throw ContentErrors.stateChanged("课程已上架，列表已刷新");
        }
        FileEntity video = course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        FileEntity cover = course.getCoverFileId() == null ? null : fileMapper.selectById(course.getCoverFileId());
        CourseCategory category = categoryMapper.selectById(course.getCategoryId());
        CourseTopic topic = course.getTopicId() == null ? null : topicMapper.selectById(course.getTopicId());
        checkShelfReady(course.getTitle(), category, topic, course.getSectionNo(), video, cover);
        LocalDateTime now = BeijingTime.now();
        course.setStatus("ON_SHELF");
        course.setOnShelfAt(now);
        course.setOnShelfBy(AdminContext.requireAdminId());
        course.setPublishedAt(now);
        course.setOffShelfAt(null);
        course.setOffShelfBy(null);
        course.setOffReason(null);
        course.setUpdatedBy(AdminContext.requireAdminId());
        if (courseMapper.updateById(course) == 0) {
            throw ContentErrors.versionConflict();
        }
        writeLog("ON_SHELF", id, course.getTitle(), null, Map.of("status", "OFF_SHELF"),
                Map.of("status", "ON_SHELF"));
        return vo(course, 0);
    }

    @Override
    @Transactional
    public CourseVO offShelf(Long id, OffShelfRequest request) {
        Course course = requireCourse(id);
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), course.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (!"ON_SHELF".equals(course.getStatus())) {
            throw ContentErrors.stateChanged("课程已下架，列表已刷新");
        }
        course.setStatus("OFF_SHELF");
        course.setOffShelfAt(BeijingTime.now());
        course.setOffShelfBy(AdminContext.requireAdminId());
        course.setOffReason(request.getOffReason());
        course.setUpdatedBy(AdminContext.requireAdminId());
        if (courseMapper.updateById(course) == 0) {
            throw ContentErrors.versionConflict();
        }
        writeLog("OFF_SHELF", id, course.getTitle(), request.getOffReason(), Map.of("status", "ON_SHELF"),
                Map.of("status", "OFF_SHELF"));
        return vo(course, 0);
    }

    @Override
    @Transactional
    public void deleteDraft(Long id, Integer version) {
        Course course = requireCourse(id);
        if (version == null || !Objects.equals(version, course.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (!"DRAFT".equals(course.getStatus())) {
            throw ContentErrors.stateChanged("只能删除草稿");
        }
        media.markBound(course.getVideoFileId(), false);
        media.markBound(course.getCoverFileId(), false);
        applicableMapper.deleteByBiz(BIZ_COURSE, id);
        courseMapper.deleteById(id);
        writeLog("DELETE", id, course.getTitle(), null, snapshot(course), null);
    }

    // ===== 组装 =====

    private CourseRowVO toRow(Course course, Map<Long, String> adminNames,
                              Map<Long, StatAggregatesPlay> plays, Map<Long, Long> finishes,
                              Map<Long, List<CourseMedia>> mediaMap) {
        CourseRowVO vo = new CourseRowVO();
        fillRow(vo, course, adminNames, plays, finishes, mediaMap);
        return vo;
    }

    private void fillRow(CourseRowVO vo, Course course, Map<Long, String> adminNames,
                         Map<Long, StatAggregatesPlay> plays, Map<Long, Long> finishes,
                         Map<Long, List<CourseMedia>> mediaMap) {
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setDurationSec(course.getDurationSec());
        vo.setCategoryId(course.getCategoryId());
        vo.setCategoryName(course.getCategoryName());
        vo.setTopicId(course.getTopicId());
        vo.setTopicName(course.getTopicName());
        vo.setSectionNo(course.getSectionNo());
        FileEntity video = course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        if (video != null) {
            vo.setUploadStatus(video.getUploadStatus());
        }
        vo.setTranscodeStatus(course.getTranscodeStatus());
        vo.setTranscodeStatusText(ContentTexts.transcodeStatus(course.getTranscodeStatus()));
        Map<String, FileVariant> variants = media.variantMap(course.getVideoFileId());
        FileVariant p720 = variants.get(VariantCode.HLS_720P.name());
        FileVariant p480 = variants.get(VariantCode.HLS_480P.name());
        vo.setP720(slot(p720));
        vo.setP480(slot(p480));
        vo.setStatus(course.getStatus());
        vo.setStatusText(ContentTexts.contentStatus(course.getStatus()));
        if (!"DRAFT".equals(course.getStatus())) {
            StatAggregatesPlay play = plays.get(course.getId());
            int viewer = play == null || play.viewers == null ? 0 : play.viewers.intValue();
            long finish = finishes.getOrDefault(course.getId(), 0L);
            vo.setPlayCount(play == null || play.plays == null ? 0 : play.plays.intValue());
            vo.setFinishRate(viewer == 0 ? null : BigDecimal.valueOf(finish)
                    .divide(BigDecimal.valueOf(viewer), 4, RoundingMode.HALF_UP));
        }
        vo.setUpdatedAt(course.getUpdatedAt());
        Long adminId = course.getUpdatedBy() != null ? course.getUpdatedBy() : course.getCreatedBy();
        vo.setUpdatedByName(adminNames.get(adminId));
        vo.setCanOnShelf(canOnShelf(course, video, p720, p480));
    }

    private CourseSlotVO slot(FileVariant variant) {
        if (variant == null) {
            return null;
        }
        CourseSlotVO vo = new CourseSlotVO();
        vo.setStatus(variant.getStatus());
        vo.setProgress(variant.getProgress());
        return vo;
    }

    private boolean canOnShelf(Course course, FileEntity video, FileVariant p720, FileVariant p480) {
        return video != null
                && TranscodeStatus.SUCCESS.name().equals(video.getTranscodeStatus())
                && p720 != null && TranscodeStatus.SUCCESS.name().equals(p720.getStatus())
                && p480 != null && TranscodeStatus.SUCCESS.name().equals(p480.getStatus())
                && course.getCoverFileId() != null
                && course.getTitle() != null && !course.getTitle().isBlank()
                && course.getCategoryId() != null
                && (course.getTopicId() == null || course.getSectionNo() != null);
    }

    private TranscodeStateVO state(Course course) {
        TranscodeStateVO vo = new TranscodeStateVO();
        vo.setCourseId(course.getId());
        FileEntity video = course.getVideoFileId() == null ? null : fileMapper.selectById(course.getVideoFileId());
        if (video != null) {
            vo.setFileId(video.getId());
            vo.setUploadStatus(video.getUploadStatus());
            vo.setTranscodeError(video.getTranscodeError());
        }
        vo.setTranscodeStatus(course.getTranscodeStatus());
        Map<String, FileVariant> variants = media.variantMap(course.getVideoFileId());
        vo.setP720(slot(variants.get(VariantCode.HLS_720P.name())));
        vo.setP480(slot(variants.get(VariantCode.HLS_480P.name())));
        List<CoverCandidateVO> covers = media.coverCandidates(course.getVideoFileId());
        vo.setCoverCandidates(covers);
        vo.setCoversReady(!covers.isEmpty());
        return vo;
    }

    private Map<Long, List<CourseMedia>> mediaByCourse(List<Course> courses) {
        Map<Long, List<CourseMedia>> map = new HashMap<>();
        List<Long> ids = courses.stream().map(Course::getId).toList();
        if (ids.isEmpty()) {
            return map;
        }
        for (CourseMedia media : mediaMapper.selectPlayableByCourseIds(ids)) {
            map.computeIfAbsent(media.getCourseId(), key -> new ArrayList<>()).add(media);
        }
        return map;
    }

    private Map<Long, StatAggregatesPlay> playAggAll() {
        Map<Long, StatAggregatesPlay> result = new LinkedHashMap<>();
        for (var row : statMapper.selectPlayAggAll()) {
            result.put(row.getCourseId(), new StatAggregatesPlay(row.getPlays(), row.getViewers()));
        }
        return result;
    }

    private Map<Long, Long> finishAgg() {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (var row : statMapper.selectFinishAgg()) {
            result.put(row.getCourseId(), row.getFinishes() == null ? 0 : row.getFinishes());
        }
        return result;
    }

    private static final class StatAggregatesPlay {
        private final Long plays;
        private final Long viewers;

        private StatAggregatesPlay(Long plays, Long viewers) {
            this.plays = plays;
            this.viewers = viewers;
        }
    }

    // ===== 校验与工具 =====

    private Course requireCourse(Long id) {
        Course course = id == null ? null : courseMapper.selectById(id);
        if (course == null) {
            throw ContentErrors.notFound();
        }
        return course;
    }

    private static String submitAction(CourseSaveRequest request, String defaultValue) {
        String value = request.getSubmitAction();
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String action = value.toUpperCase();
        if (!"DRAFT".equals(action) && !"ON_SHELF".equals(action)) {
            throw ContentErrors.field("submit_action", "提交方式不正确");
        }
        return action;
    }

    private CourseCategory resolveCategory(Long categoryId, String submit) {
        if (categoryId != null) {
            return categoryService.requireEnabled(categoryId);
        }
        if ("ON_SHELF".equals(submit)) {
            throw ContentErrors.field("category_id", "请选择分类");
        }
        List<CourseCategory> enabled = categoryMapper.selectList(Wrappers.<CourseCategory>lambdaQuery()
                .eq(CourseCategory::getStatus, "ENABLED")
                .orderByAsc(CourseCategory::getSortNo).orderByAsc(CourseCategory::getId)
                .last("LIMIT 1"));
        if (enabled.isEmpty()) {
            throw ContentErrors.field("category_id", "请选择分类");
        }
        return enabled.get(0);
    }

    private CourseTopic resolveTopic(Long topicId, Integer sectionNo, String submit) {
        if (topicId == null) {
            return null;
        }
        if (sectionNo == null || sectionNo < 1) {
            throw ContentErrors.field("section_no", "加入专题时请填写节次");
        }
        CourseTopic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw ContentErrors.field("topic_id", "专题不存在");
        }
        return topic;
    }

    private AiKnowledge resolveKnowledge(Long knowledgeId) {
        if (knowledgeId == null) {
            return null;
        }
        AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null || !"ON_SHELF".equals(knowledge.getStatus())) {
            throw ContentErrors.field("knowledge_id", "关联的知识条目未上架");
        }
        return knowledge;
    }

    private void validateTitle(String title, FileEntity video) {
        if (title != null && title.length() > 30) {
            throw ContentErrors.field("title", "标题不能超过 30 字");
        }
        if ((title == null || title.isBlank()) && video == null) {
            throw ContentErrors.field("title", "请填写课程标题");
        }
    }

    private static String titleOf(String title, FileEntity video) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (video == null || video.getOriginalName() == null) {
            throw ContentErrors.field("title", "请填写课程标题");
        }
        String name = video.getOriginalName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.length() > 30 ? name.substring(0, 30) : name;
    }

    private void validateApplicable(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.size() > 10) {
            throw ContentErrors.field("applicable_category_ids", "适用品类最多 10 个");
        }
        List<Long> found = statMapper.selectProductCategories(new ArrayList<>(distinct)).stream()
                .map(row -> row.getId()).toList();
        if (found.size() != distinct.size()) {
            throw ContentErrors.field("applicable_category_ids", "适用品类不存在或已停用");
        }
    }

    private FileEntity resolveCover(CourseSaveRequest request, FileEntity video, String submit) {
        if (request.getCoverFileId() == null) {
            if ("ON_SHELF".equals(submit)) {
                throw ContentErrors.stateChanged("请选择封面");
            }
            return null;
        }
        FileEntity cover = media.require(request.getCoverFileId());
        if ("AUTO".equals(request.getCoverSource())) {
            if (video == null || !Objects.equals(cover.getParentFileId(), video.getId())
                    || !FileScene.VIDEO_COVER.name().equals(cover.getScene())) {
                throw ContentErrors.field("cover_file_id", "请选择该视频的封面候选");
            }
        } else {
            if (!FileScene.COURSE_COVER.name().equals(cover.getScene())
                    || !"ADMIN".equals(cover.getUploaderType())) {
                throw ContentErrors.field("cover_file_id", "封面无效，请重新上传");
            }
            if (!UploadStatus.READY.name().equals(cover.getUploadStatus())) {
                throw ContentErrors.field("cover_file_id", "封面尚未上传完成，请稍后重试");
            }
        }
        return cover;
    }

    private void checkShelfReady(String title, CourseCategory category, CourseTopic topic, Integer sectionNo,
                                 FileEntity video, FileEntity cover) {
        if (title == null || title.isBlank()) {
            throw ContentErrors.field("title", "请填写课程标题");
        }
        if (category == null || !"ENABLED".equals(category.getStatus())) {
            throw ContentErrors.field("category_id", "请选择分类");
        }
        if (topic != null && (sectionNo == null || sectionNo < 1)) {
            throw ContentErrors.field("section_no", "加入专题时请填写节次");
        }
        if (video == null || !TranscodeStatus.SUCCESS.name().equals(video.getTranscodeStatus())) {
            throw ContentErrors.stateChanged("720P 和 480P 都转码完成后才能上架");
        }
        Map<String, FileVariant> variants = media.variantMap(video.getId());
        FileVariant p720 = variants.get(VariantCode.HLS_720P.name());
        FileVariant p480 = variants.get(VariantCode.HLS_480P.name());
        if (p720 == null || !TranscodeStatus.SUCCESS.name().equals(p720.getStatus())
                || p480 == null || !TranscodeStatus.SUCCESS.name().equals(p480.getStatus())) {
            throw ContentErrors.stateChanged("720P 和 480P 都转码完成后才能上架");
        }
        if (cover == null) {
            throw ContentErrors.stateChanged("请选择封面");
        }
    }

    private int shiftSections(Long topicId, Integer sectionNo, Long excludeId) {
        if (topicId == null || sectionNo == null || sectionNo < 1) {
            return 0;
        }
        var wrapper = Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, topicId)
                .ge(Course::getSectionNo, sectionNo);
        if (excludeId != null) {
            wrapper.ne(Course::getId, excludeId);
        }
        long count = courseMapper.selectCount(wrapper);
        if (count == 0) {
            return 0;
        }
        courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .eq(Course::getTopicId, topicId)
                .ge(Course::getSectionNo, sectionNo)
                .ne(excludeId != null, Course::getId, excludeId)
                .setSql("section_no = section_no + 1")
                .set(Course::getUpdatedAt, BeijingTime.now()));
        return (int) count;
    }

    private void syncApplicable(Long courseId, List<Long> categoryIds) {
        applicableMapper.deleteByBiz(BIZ_COURSE, courseId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : new LinkedHashSet<>(categoryIds)) {
            ContentApplicable entity = new ContentApplicable();
            entity.setBizType(BIZ_COURSE);
            entity.setBizId(courseId);
            entity.setCategoryId(categoryId);
            applicableMapper.insert(entity);
        }
    }

    private static Integer durationSec(FileEntity video) {
        return video.getDurationMs() == null ? null : (int) Math.round(video.getDurationMs() / 1000.0);
    }

    private static String transcodeOf(FileEntity video) {
        return video.getTranscodeStatus() == null ? TranscodeStatus.NONE.name() : video.getTranscodeStatus();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CourseVO vo(Course course, int shifted) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setStatus(course.getStatus());
        vo.setTranscodeStatus(course.getTranscodeStatus());
        vo.setShiftedCount(shifted);
        vo.setVersion(course.getVersion());
        return vo;
    }

    private Map<String, Object> snapshot(Course course) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", course.getTitle());
        map.put("category_id", course.getCategoryId());
        map.put("topic_id", course.getTopicId());
        map.put("section_no", course.getSectionNo());
        map.put("status", course.getStatus());
        map.put("transcode_status", course.getTranscodeStatus());
        return map;
    }

    private void writeLog(String action, Long id, String title, String reason,
                          Object before, Object after) {
        Map<String, Object> beforeFields = new LinkedHashMap<>();
        Map<String, Object> afterFields = new LinkedHashMap<>();
        if (before instanceof Map<?, ?> map) {
            map.forEach((k, v) -> beforeFields.put(String.valueOf(k), v));
        }
        if (after instanceof Map<?, ?> map) {
            map.forEach((k, v) -> afterFields.put(String.valueOf(k), v));
        }
        logs.record(OperationLogEntry.builder()
                .opType("COURSE")
                .opAction(action)
                .bizType("COURSE")
                .bizId(id)
                .targetName(title)
                .reason(reason)
                .before(beforeFields.isEmpty() ? null : beforeFields)
                .after(afterFields.isEmpty() ? null : afterFields)
                .build());
    }
}
