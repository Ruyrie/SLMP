package com.zhimu.server.biz.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.support.AccountPersonalDataGuard;
import com.zhimu.server.biz.ai.convert.CourseConvert;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CourseMedia;
import com.zhimu.server.biz.ai.entity.CoursePlayRecord;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.CourseCategoryMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.support.CourseMediaResolver;
import com.zhimu.server.biz.ai.mapper.CoursePlayRecordMapper;
import com.zhimu.server.biz.ai.mapper.CourseProgressMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.biz.ai.query.CourseQuery;
import com.zhimu.server.biz.ai.query.TopicQuery;
import com.zhimu.server.biz.ai.request.CourseProgressRequest;
import com.zhimu.server.biz.ai.service.CourseService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.vo.ContinueWatchingVO;
import com.zhimu.server.biz.ai.vo.CourseCardVO;
import com.zhimu.server.biz.ai.vo.CourseCategoryVO;
import com.zhimu.server.biz.ai.vo.CoursePlayVO;
import com.zhimu.server.biz.ai.vo.ProgressResultVO;
import com.zhimu.server.biz.ai.vo.TopicDetailVO;
import com.zhimu.server.biz.ai.vo.TopicVO;
import com.zhimu.server.biz.ai.vo.WatchHistoryVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * API-K14–K21。
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private static final Set<String> EVENTS = Set.of(AiCodes.EVENT_START, AiCodes.EVENT_TICK, AiCodes.EVENT_PAUSE,
            AiCodes.EVENT_SEEK, AiCodes.EVENT_SWITCH, AiCodes.EVENT_LEAVE, AiCodes.EVENT_END);
    private static final Set<String> QUALITIES = Set.of(AiCodes.QUALITY_720, AiCodes.QUALITY_480);
    private static final Set<String> ENTRY_TYPES = Set.of(AiCodes.ENTRY_COURSE, AiCodes.ENTRY_TOPIC,
            AiCodes.ENTRY_CONTINUE, AiCodes.ENTRY_HISTORY, AiCodes.ENTRY_KNOWLEDGE);
    private static final int MAX_KEYWORD = 30;

    private final CourseCategoryMapper categoryMapper;
    private final CourseTopicMapper topicMapper;
    private final CourseMapper courseMapper;
    private final CourseMediaResolver mediaResolver;
    private final CourseProgressMapper progressMapper;
    private final CoursePlayRecordMapper playRecordMapper;
    private final CourseConvert convert;
    private final AccountPersonalDataGuard personalDataGuard;

    @Override
    public List<CourseCategoryVO> categories() {
        List<CourseCategoryVO> result = new ArrayList<>();
        List<CourseCategory> rows = categoryMapper.selectList(Wrappers.<CourseCategory>lambdaQuery()
                .eq(CourseCategory::getStatus, "ENABLED")
                .orderByAsc(CourseCategory::getSortNo)
                .orderByAsc(CourseCategory::getId));
        for (CourseCategory row : rows) {
            CourseCategoryVO vo = new CourseCategoryVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setSortNo(row.getSortNo() == null ? 0 : row.getSortNo());
            result.add(vo);
        }
        return result;
    }

    @Override
    public ContinueWatchingVO continueWatching(Long userId) {
        List<CourseProgress> recent = progressMapper.selectRecentIncomplete(userId, 10);
        for (CourseProgress progress : recent) {
            Course course = courseMapper.selectById(progress.getCourseId());
            if (course != null && "ON_SHELF".equals(course.getStatus())) {
                return convert.toContinue(course, progress);
            }
        }
        return null;
    }

    @Override
    public PageVO<TopicVO> topics(Long userId, TopicQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        List<CourseTopic> topics = topicMapper.selectList(Wrappers.<CourseTopic>lambdaQuery()
                .eq(CourseTopic::getStatus, "ON_SHELF"));
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, "ON_SHELF")
                .isNotNull(Course::getTopicId));
        Map<Long, List<Course>> byTopic = new LinkedHashMap<>();
        for (Course course : courses) {
            byTopic.computeIfAbsent(course.getTopicId(), key -> new ArrayList<>()).add(course);
        }
        Map<Long, CourseProgress> progressByCourse = progressByCourse(userId, courses);
        List<TopicVO> all = new ArrayList<>();
        for (CourseTopic topic : topics) {
            TopicVO vo = new TopicVO();
            vo.setId(topic.getId());
            vo.setName(topic.getName());
            vo.setCategoryName(topic.getCategoryName());
            vo.setCoverUrl(convert.topicCover(topic));
            List<Course> topicCourses = byTopic.getOrDefault(topic.getId(), List.of());
            vo.setCourseCount(topicCourses.size());
            vo.setTotalDurationSec(topicCourses.stream().mapToInt(c -> value(c.getDurationSec())).sum());
            int learned = 0;
            LocalDateTime last = null;
            for (Course course : topicCourses) {
                CourseProgress progress = progressByCourse.get(course.getId());
                if (progress != null && progress.getCompleted() != null && progress.getCompleted() == 1) {
                    learned++;
                }
                if (progress != null && progress.getLastWatchedAt() != null
                        && (last == null || progress.getLastWatchedAt().isAfter(last))) {
                    last = progress.getLastWatchedAt();
                }
            }
            vo.setLearnedCount(learned);
            vo.setLastLearnedAt(BeijingTime.format(last));
            all.add(vo);
        }
        if ("RECENT_LEARNED".equalsIgnoreCase(query.getSort())) {
            all.sort(Comparator.comparing(TopicVO::getLastLearnedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            Map<Long, Integer> sortNos = new LinkedHashMap<>();
            topics.forEach(t -> sortNos.put(t.getId(), t.getSortNo() == null ? 0 : t.getSortNo()));
            all.sort(Comparator.comparing(vo -> sortNos.getOrDefault(vo.getId(), 0)));
        }
        return slice(all, page, size);
    }

    @Override
    public TopicDetailVO topicDetail(Long userId, Long topicId) {
        CourseTopic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw AiErrors.notFound();
        }
        if ("OFF_SHELF".equals(topic.getStatus())) {
            throw AiErrors.offShelf("这个专题已下架");
        }
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, topicId)
                .eq(Course::getStatus, "ON_SHELF")
                .orderByAsc(Course::getSectionNo)
                .orderByAsc(Course::getId));
        Map<Long, CourseProgress> progressByCourse = progressByCourse(userId, courses);
        TopicDetailVO vo = new TopicDetailVO();
        vo.setId(topic.getId());
        vo.setName(topic.getName());
        vo.setCategoryName(topic.getCategoryName());
        vo.setCoverUrl(convert.topicCover(topic));
        vo.setIntro(topic.getIntro());
        vo.setCourseCount(courses.size());
        vo.setTotalDurationSec(courses.stream().mapToInt(c -> value(c.getDurationSec())).sum());
        List<CourseCardVO> cards = new ArrayList<>();
        int learned = 0;
        LocalDateTime last = null;
        for (Course course : courses) {
            CourseProgress progress = progressByCourse.get(course.getId());
            cards.add(convert.toCard(course, progress));
            if (progress != null && progress.getCompleted() != null && progress.getCompleted() == 1) {
                learned++;
            }
            if (progress != null && progress.getLastWatchedAt() != null
                    && (last == null || progress.getLastWatchedAt().isAfter(last))) {
                last = progress.getLastWatchedAt();
            }
        }
        vo.setCourses(cards);
        vo.setLearnedCount(learned);
        vo.setLastLearnedAt(BeijingTime.format(last));
        applyResume(vo, courses, progressByCourse, learned);
        return vo;
    }

    private void applyResume(TopicDetailVO vo, List<Course> courses, Map<Long, CourseProgress> progressByCourse,
                             int learned) {
        if (courses.isEmpty()) {
            vo.setAction(AiCodes.TOPIC_ACTION_START);
            return;
        }
        if (learned >= courses.size()) {
            vo.setAction(AiCodes.TOPIC_ACTION_REVIEW);
            return;
        }
        CourseProgress latest = null;
        Course latestCourse = null;
        for (Course course : courses) {
            CourseProgress progress = progressByCourse.get(course.getId());
            if (progress != null && progress.getLastWatchedAt() != null
                    && (latest == null || progress.getLastWatchedAt().isAfter(latest.getLastWatchedAt()))) {
                latest = progress;
                latestCourse = course;
            }
        }
        if (latest == null || latestCourse == null) {
            vo.setAction(AiCodes.TOPIC_ACTION_START);
            return;
        }
        if (!isCompleted(latest) && latest.getPositionSec() != null && latest.getPositionSec() > 0) {
            vo.setAction(AiCodes.TOPIC_ACTION_CONTINUE);
            TopicDetailVO.Resume resume = new TopicDetailVO.Resume();
            resume.setCourseId(latestCourse.getId());
            resume.setSectionNo(latestCourse.getSectionNo() == null ? 0 : latestCourse.getSectionNo());
            resume.setPositionSec(latest.getPositionSec());
            vo.setResume(resume);
            return;
        }
        // 最近观看的一节已学完：取其后第一节未学完的
        for (Course course : courses) {
            if (course.getSectionNo() != null && latestCourse.getSectionNo() != null
                    && course.getSectionNo() > latestCourse.getSectionNo()) {
                CourseProgress progress = progressByCourse.get(course.getId());
                if (progress == null || !isCompleted(progress)) {
                    vo.setAction(AiCodes.TOPIC_ACTION_CONTINUE);
                    return;
                }
            }
        }
        vo.setAction(AiCodes.TOPIC_ACTION_REVIEW);
    }

    @Override
    public PageVO<CourseCardVO> courses(Long userId, CourseQuery query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (keyword != null && keyword.length() > MAX_KEYWORD) {
            throw AiErrors.fieldError("keyword", "搜索内容不能超过 30 字");
        }
        var wrapper = Wrappers.<Course>lambdaQuery().eq(Course::getStatus, "ON_SHELF");
        if (query.getCategoryId() != null) {
            wrapper.eq(Course::getCategoryId, query.getCategoryId());
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Course::getTitle, keyword).or().like(Course::getTopicName, keyword));
        }
        if ("DURATION_ASC".equalsIgnoreCase(query.getSort())) {
            wrapper.orderByAsc(Course::getDurationSec);
        } else {
            wrapper.orderByDesc(Course::getPublishedAt);
        }
        wrapper.orderByDesc(Course::getId);
        List<Course> all = courseMapper.selectList(wrapper);
        Map<Long, CourseProgress> progressByCourse = progressByCourse(userId, all);
        List<CourseCardVO> cards = new ArrayList<>();
        String learnStatus = query.getLearnStatus();
        for (Course course : all) {
            CourseProgress progress = progressByCourse.get(course.getId());
            if (learnStatus != null && !learnStatus.isBlank()
                    && !learnStatus.equalsIgnoreCase(CourseConvert.learnStatus(progress))) {
                continue;
            }
            cards.add(convert.toCard(course, progress));
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        return PageVO.of(sublist(cards, page, size), cards.size(), page, size);
    }

    @Override
    public CoursePlayVO coursePlay(Long userId, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || "DRAFT".equals(course.getStatus())) {
            throw AiErrors.notFound();
        }
        if ("OFF_SHELF".equals(course.getStatus())) {
            throw AiErrors.offShelf("这个视频已下架");
        }
        CourseProgress progress = progressMapper.selectByUserAndCourse(userId, courseId);
        List<CourseMedia> media = mediaResolver.resolve(course);
        CoursePlayVO vo = convert.toPlay(course, media, progress);
        if (course.getTopicId() != null) {
            List<Course> topicCourses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                    .eq(Course::getTopicId, course.getTopicId())
                    .eq(Course::getStatus, "ON_SHELF"));
            CoursePlayVO.Topic topic = new CoursePlayVO.Topic();
            topic.setId(course.getTopicId());
            topic.setName(course.getTopicName());
            topic.setCourseCount(topicCourses.size());
            topic.setLearnedCount((int) topicCourses.stream()
                    .map(c -> progressMapper.selectByUserAndCourse(userId, c.getId()))
                    .filter(CourseServiceImpl::isCompleted)
                    .count());
            vo.setTopic(topic);
            Course next = nextSection(course);
            vo.setNextCourseId(next == null ? null : next.getId());
        } else {
            List<Course> related = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                    .eq(Course::getCategoryId, course.getCategoryId())
                    .eq(Course::getStatus, "ON_SHELF")
                    .ne(Course::getId, courseId)
                    .orderByDesc(Course::getPublishedAt)
                    .orderByDesc(Course::getId)
                    .last("LIMIT 5"));
            List<CourseCardVO> cards = new ArrayList<>();
            Map<Long, CourseProgress> progressMap = progressByCourse(userId, related);
            for (Course item : related) {
                cards.add(convert.toCard(item, progressMap.get(item.getId())));
            }
            vo.setRelatedCourses(cards);
        }
        return vo;
    }

    private Course nextSection(Course course) {
        if (course.getTopicId() == null || course.getSectionNo() == null) {
            return null;
        }
        List<Course> next = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, course.getTopicId())
                .eq(Course::getStatus, "ON_SHELF")
                .gt(Course::getSectionNo, course.getSectionNo())
                .orderByAsc(Course::getSectionNo)
                .last("LIMIT 1"));
        return next.isEmpty() ? null : next.get(0);
    }

    @Override
    @Transactional
    public ProgressResultVO reportProgress(Long userId, Long courseId, CourseProgressRequest request) {
        if (request == null) {
            throw AiErrors.invalid("参数不正确");
        }
        validateProgress(request);
        personalDataGuard.requireActive(userId);
        Course course = courseMapper.selectById(courseId);
        if (course == null || "DRAFT".equals(course.getStatus())) {
            throw AiErrors.notFound();
        }
        int duration = value(course.getDurationSec());
        int position = Math.min(Math.max(request.getPositionSec(), 0), Math.max(duration, 0));
        String clientType = clientType();
        LocalDateTime now = BeijingTime.now();

        CoursePlayRecord record = playRecordMapper.selectByToken(userId, request.getPlayToken());
        int watched = Math.min(Math.max(request.getWatchedSec(), 0), 60);
        if (record != null && request.getClientTime().isAfter(record.getLastClientTime())) {
            long interval = Math.max(Duration.between(record.getLastClientTime(), request.getClientTime()).getSeconds(), 0);
            watched = (int) Math.min(watched, interval * 2 + 5);
        }
        upsertPlayRecord(record, userId, courseId, request, position, watched, clientType, now);

        CourseProgress progress = progressMapper.selectByUserAndCourse(userId, courseId);
        boolean oldCompleted = isCompleted(progress);
        int oldMax = progress == null || progress.getMaxPositionSec() == null ? 0 : progress.getMaxPositionSec();
        int oldWatched = progress == null || progress.getWatchedSec() == null ? 0 : progress.getWatchedSec();
        int maxPosition = Math.max(oldMax, position);
        int totalWatched = oldWatched + watched;
        boolean completed = oldCompleted
                || (duration > 0 && maxPosition >= duration * 0.9 && totalWatched >= duration * 0.6);
        boolean justCompleted = !oldCompleted && completed;

        if (progress == null) {
            progress = new CourseProgress();
            progress.setUserId(userId);
            progress.setCourseId(courseId);
            progress.setPositionSec(position);
            progress.setMaxPositionSec(maxPosition);
            progress.setWatchedSec(totalWatched);
            progress.setDurationSec(duration);
            progress.setProgressRatio(convertRatio(completed, maxPosition, duration));
            progress.setCompleted(completed ? 1 : 0);
            progress.setCompletedAt(completed ? now : null);
            progress.setLastWatchedAt(now);
            progress.setLastClientType(clientType);
            progress.setLastQuality(request.getQuality());
            progress.setLastClientTime(request.getClientTime());
            progressMapper.insert(progress);
        } else {
            if (request.getClientTime().isAfter(progress.getLastClientTime())) {
                progress.setPositionSec(position);
                progress.setLastWatchedAt(now);
                progress.setLastClientType(clientType);
                progress.setLastQuality(request.getQuality());
                progress.setLastClientTime(request.getClientTime());
            }
            progress.setMaxPositionSec(maxPosition);
            progress.setWatchedSec(totalWatched);
            progress.setDurationSec(duration);
            progress.setProgressRatio(convertRatio(completed, maxPosition, duration));
            if (justCompleted) {
                progress.setCompleted(1);
                progress.setCompletedAt(now);
            }
            if (progress.getCompleted() == null) {
                progress.setCompleted(completed ? 1 : 0);
            }
            if (progress.getLastWatchedAt() == null) {
                progress.setLastWatchedAt(now);
            }
            progressMapper.updateById(progress);
        }

        ProgressResultVO vo = new ProgressResultVO();
        vo.setPositionSec(progress.getPositionSec());
        vo.setMaxPositionSec(progress.getMaxPositionSec());
        vo.setProgressRatio(convertRatio(completed, value(progress.getPositionSec()), duration));
        vo.setCompleted(completed);
        vo.setJustCompleted(justCompleted);
        vo.setLearnStatus(CourseConvert.learnStatus(progress));
        if (course.getTopicId() != null) {
            List<CourseProgress> topicProgress = progressMapper.selectByUserAndTopic(userId, course.getTopicId());
            vo.setTopicLearnedCount((int) topicProgress.stream().filter(CourseServiceImpl::isCompleted).count());
        }
        return vo;
    }

    private void upsertPlayRecord(CoursePlayRecord record, Long userId, Long courseId,
                                  CourseProgressRequest request, int position, int watched,
                                  String clientType, LocalDateTime now) {
        if (record == null) {
            record = new CoursePlayRecord();
            record.setUserId(userId);
            record.setCourseId(courseId);
            record.setPlayToken(request.getPlayToken());
            record.setEntryType(request.getEntryType() == null ? AiCodes.ENTRY_COURSE : request.getEntryType());
            record.setClientType(clientType);
            record.setNetworkType(request.getNetworkType());
            record.setQuality(request.getQuality());
            record.setStartPositionSec("START".equals(request.getEvent()) ? position : 0);
            record.setMaxPositionSec(position);
            record.setWatchedSec(watched);
            record.setAskAiClicked(Boolean.TRUE.equals(request.getAskAiClicked()) ? 1 : 0);
            record.setKnowledgeClicked(Boolean.TRUE.equals(request.getKnowledgeClicked()) ? 1 : 0);
            record.setStartedAt(now);
            record.setLastReportAt(now);
            record.setLastClientTime(request.getClientTime());
            if (isEndEvent(request.getEvent())) {
                record.setEndedAt(now);
                record.setEndPositionSec(position);
            }
            playRecordMapper.insert(record);
            return;
        }
        if (request.getClientTime().isAfter(record.getLastClientTime())) {
            record.setLastClientTime(request.getClientTime());
            record.setLastReportAt(now);
            record.setMaxPositionSec(Math.max(value(record.getMaxPositionSec()), position));
            record.setWatchedSec(value(record.getWatchedSec()) + watched);
            record.setQuality(request.getQuality());
            record.setNetworkType(request.getNetworkType());
        }
        if (Boolean.TRUE.equals(request.getAskAiClicked())) {
            record.setAskAiClicked(1);
        }
        if (Boolean.TRUE.equals(request.getKnowledgeClicked())) {
            record.setKnowledgeClicked(1);
        }
        if (isEndEvent(request.getEvent())) {
            record.setEndedAt(now);
            record.setEndPositionSec(position);
        }
        playRecordMapper.updateById(record);
    }

    @Override
    public PageVO<WatchHistoryVO> history(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        List<CourseProgress> all = progressMapper.selectList(Wrappers.<CourseProgress>lambdaQuery()
                .eq(CourseProgress::getUserId, userId)
                .orderByDesc(CourseProgress::getLastWatchedAt));
        List<WatchHistoryVO> rows = new ArrayList<>();
        for (CourseProgress progress : all) {
            Course course = courseMapper.selectById(progress.getCourseId());
            if (course == null || !"ON_SHELF".equals(course.getStatus())) {
                continue;
            }
            rows.add(convert.toHistory(course, progress));
        }
        return PageVO.of(sublist(rows, safePage, safeSize), rows.size(), safePage, safeSize);
    }

    private void validateProgress(CourseProgressRequest request) {
        if (request.getPlayToken() == null || request.getPlayToken().isBlank()
                || request.getPlayToken().length() > 64) {
            throw AiErrors.fieldError("play_token", "参数不正确");
        }
        if (request.getEvent() == null || !EVENTS.contains(request.getEvent())) {
            throw AiErrors.fieldError("event", "参数不正确");
        }
        if (request.getPositionSec() == null || request.getPositionSec() < 0) {
            throw AiErrors.fieldError("position_sec", "参数不正确");
        }
        if (request.getWatchedSec() == null || request.getWatchedSec() < 0) {
            throw AiErrors.fieldError("watched_sec", "参数不正确");
        }
        if (request.getQuality() == null || !QUALITIES.contains(request.getQuality())) {
            throw AiErrors.fieldError("quality", "参数不正确");
        }
        if (request.getClientTime() == null
                || Duration.between(request.getClientTime(), BeijingTime.now()).abs().toHours() >= 24) {
            throw AiErrors.fieldError("client_time", "参数不正确");
        }
        if (request.getEntryType() != null && !ENTRY_TYPES.contains(request.getEntryType())) {
            throw AiErrors.fieldError("entry_type", "参数不正确");
        }
        if (AiCodes.EVENT_START.equals(request.getEvent())
                && (request.getEntryType() == null || request.getEntryType().isBlank())) {
            throw AiErrors.fieldError("entry_type", "参数不正确");
        }
    }

    private Map<Long, CourseProgress> progressByCourse(Long userId, List<Course> courses) {
        Map<Long, CourseProgress> result = new LinkedHashMap<>();
        if (courses == null || courses.isEmpty()) {
            return result;
        }
        List<Long> ids = courses.stream().map(Course::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        for (CourseProgress progress : progressMapper.selectByUserAndCourses(userId, ids)) {
            result.put(progress.getCourseId(), progress);
        }
        return result;
    }

    private static String clientType() {
        ClientType type = RequestContext.clientType();
        return type == null || type == ClientType.ADMIN_WEB ? ClientType.APP_ANDROID.name() : type.name();
    }

    private static boolean isCompleted(CourseProgress progress) {
        return progress != null && progress.getCompleted() != null && progress.getCompleted() == 1;
    }

    private static boolean isEndEvent(String event) {
        return AiCodes.EVENT_LEAVE.equals(event) || AiCodes.EVENT_END.equals(event)
                || AiCodes.EVENT_SWITCH.equals(event);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static java.math.BigDecimal convertRatio(boolean completed, int maxPosition, int duration) {
        return CourseConvert.ratio(completed, maxPosition, duration);
    }

    private static <T> List<T> sublist(List<T> list, int page, int size) {
        int from = Math.min((page - 1) * size, list.size());
        int to = Math.min(from + size, list.size());
        return new ArrayList<>(list.subList(from, to));
    }

    private static <T> PageVO<T> slice(List<T> list, int page, int size) {
        return PageVO.of(sublist(list, page, size), list.size(), page, size);
    }
}
