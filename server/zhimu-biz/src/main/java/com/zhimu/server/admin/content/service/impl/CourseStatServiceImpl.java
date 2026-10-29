package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.mapper.ContentStatMapper;
import com.zhimu.server.admin.content.service.CourseStatService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentExportService;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.ContentExportVO;
import com.zhimu.server.admin.content.vo.CourseStatsVO;
import com.zhimu.server.admin.content.vo.CourseSummaryVO;
import com.zhimu.server.admin.content.vo.StatAggregates;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CoursePlayRecord;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CoursePlayRecordMapper;
import com.zhimu.server.biz.ai.mapper.CourseProgressMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程指标与课程数据：口径按管理端-02 2.7 节，播放与完播来自模块11 的播放记录 / 学习进度。
 */
@Service
@RequiredArgsConstructor
public class CourseStatServiceImpl implements CourseStatService {

    private static final int WINDOW_SEC = 16;
    private static final int STEP_SEC = 4;

    private final CourseMapper courseMapper;
    private final CourseTopicMapper topicMapper;
    private final CoursePlayRecordMapper playRecordMapper;
    private final CourseProgressMapper progressMapper;
    private final ContentStatMapper statMapper;
    private final ContentExportService exportService;
    private final ContentLogs logs;

    @Override
    public CourseSummaryVO summary() {
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery());
        Map<Long, StatAggregates.CoursePlayAgg> plays = playAggAll();
        Map<Long, Long> finishes = finishAgg();
        CourseSummaryVO vo = new CourseSummaryVO();
        vo.setOnShelfCount((int) courses.stream().filter(c -> "ON_SHELF".equals(c.getStatus())).count());
        vo.setTopicCount(Math.toIntExact(topicMapper.selectCount(Wrappers.<CourseTopic>lambdaQuery())));
        vo.setCategoryCount(statMapper.selectContentCategories().size());
        LocalDateTime from = BeijingTime.today().minusDays(30).atStartOfDay();
        vo.setPlays30d(statMapper.selectPlayAggSince(from).stream()
                .mapToLong(a -> a.getPlays() == null ? 0 : a.getPlays()).sum());
        vo.setViewers30d(statMapper.countViewersSince(from) == null ? 0 : statMapper.countViewersSince(from));
        List<BigDecimal> rates = new ArrayList<>();
        for (Course course : courses) {
            if (!"ON_SHELF".equals(course.getStatus())) {
                continue;
            }
            StatAggregates.CoursePlayAgg agg = plays.get(course.getId());
            long viewers = agg == null || agg.getViewers() == null ? 0 : agg.getViewers();
            if (viewers <= 0) {
                continue;
            }
            long finish = finishes.getOrDefault(course.getId(), 0L);
            rates.add(BigDecimal.valueOf(finish).divide(BigDecimal.valueOf(viewers), 4, RoundingMode.HALF_UP));
        }
        vo.setAvgFinishRate(rates.isEmpty() ? BigDecimal.ZERO
                : rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rates.size()), 4, RoundingMode.HALF_UP));
        vo.setTranscodingCount((int) courses.stream()
                .filter(c -> "PROCESSING".equals(c.getTranscodeStatus())
                        || "WAITING".equals(c.getTranscodeStatus())).count());
        vo.setFailedCount((int) courses.stream().filter(c -> "FAILED".equals(c.getTranscodeStatus())).count());

        CourseSummaryVO.Nav nav = new CourseSummaryVO.Nav();
        nav.setTotal(courses.size());
        Map<Long, Integer> topicOnShelf = new HashMap<>();
        courses.stream().filter(c -> "ON_SHELF".equals(c.getStatus()) && c.getTopicId() != null)
                .forEach(c -> topicOnShelf.merge(c.getTopicId(), 1, Integer::sum));
        for (CourseTopic topic : topicMapper.selectList(Wrappers.<CourseTopic>lambdaQuery()
                .orderByAsc(CourseTopic::getSortNo).orderByAsc(CourseTopic::getId))) {
            CourseSummaryVO.TopicNav item = new CourseSummaryVO.TopicNav();
            item.setId(topic.getId());
            item.setName(topic.getName());
            item.setOnShelfCount(topicOnShelf.getOrDefault(topic.getId(), 0));
            nav.getTopics().add(item);
        }
        Map<Long, Integer> categoryCounts = new HashMap<>();
        courses.forEach(c -> {
            if (c.getCategoryId() != null) {
                categoryCounts.merge(c.getCategoryId(), 1, Integer::sum);
            }
        });
        for (StatAggregates.CategoryRef category : statMapper.selectContentCategories()) {
            CourseSummaryVO.CategoryNav item = new CourseSummaryVO.CategoryNav();
            item.setId(category.getId());
            item.setName(category.getName());
            item.setCount(categoryCounts.getOrDefault(category.getId(), 0));
            nav.getCategories().add(item);
        }
        vo.setNav(nav);
        return vo;
    }

    @Override
    public CourseStatsVO stats(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw ContentErrors.notFound();
        }
        CourseStatsVO vo = new CourseStatsVO();
        vo.setStatAt(BeijingTime.now());
        if ("DRAFT".equals(course.getStatus())) {
            vo.setPlayCount(0);
            vo.setViewerCount(0);
            vo.setFinishCount(0);
            vo.setMobilePlayCount(0);
            vo.setAskAiCount(0);
            vo.setKnowledgeClickCount(0);
            return vo;
        }
        List<CoursePlayRecord> records = playRecordMapper.selectList(Wrappers.<CoursePlayRecord>lambdaQuery()
                .eq(CoursePlayRecord::getCourseId, courseId)
                .orderByAsc(CoursePlayRecord::getStartedAt));
        List<CourseProgress> progress = progressMapper.selectList(Wrappers.<CourseProgress>lambdaQuery()
                .eq(CourseProgress::getCourseId, courseId));
        int viewers = (int) progress.stream().map(CourseProgress::getUserId).filter(Objects::nonNull).distinct().count();
        int finished = (int) progress.stream().filter(p -> p.getCompleted() != null && p.getCompleted() == 1).count();
        int watched = progress.stream().mapToInt(p -> value(p.getWatchedSec())).sum();
        vo.setPlayCount(records.size());
        vo.setViewerCount(viewers);
        vo.setFinishCount(finished);
        vo.setFinishRate(viewers == 0 ? null : ratio(finished, viewers));
        vo.setAvgWatchSec(viewers == 0 ? null : watched / viewers);
        vo.setDaily(daily(course, records));
        vo.setProgressBuckets(buckets(progress, finished));
        vo.setTopExitRange(exitRange(course, records));
        vo.setClientShare(clientShare(records));
        vo.setQualityShare(qualityShare(records));
        vo.setMobilePlayCount((int) records.stream().filter(r -> isMobile(r.getNetworkType())).count());
        vo.setAskAiCount((int) records.stream().filter(r -> value(r.getAskAiClicked()) != 0).count());
        vo.setKnowledgeClickCount((int) records.stream().filter(r -> value(r.getKnowledgeClicked()) != 0).count());
        vo.setTopic(topicStat(course, progress));
        return vo;
    }

    private List<CourseStatsVO.Daily> daily(Course course, List<CoursePlayRecord> records) {
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (CoursePlayRecord record : records) {
            if (record.getStartedAt() != null) {
                counts.merge(record.getStartedAt().toLocalDate(), 1, Integer::sum);
            }
        }
        LocalDate today = BeijingTime.today();
        LocalDate start = course.getOnShelfAt() == null ? today.minusDays(29) : course.getOnShelfAt().toLocalDate();
        if (start.isBefore(today.minusDays(29))) {
            start = today.minusDays(29);
        }
        if (start.isAfter(today)) {
            start = today;
        }
        List<CourseStatsVO.Daily> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            CourseStatsVO.Daily item = new CourseStatsVO.Daily();
            item.setDate(date.toString());
            item.setPlays(counts.getOrDefault(date, 0));
            result.add(item);
        }
        return result;
    }

    private List<CourseStatsVO.Bucket> buckets(List<CourseProgress> progress, int finished) {
        int lt25 = 0;
        int p25 = 0;
        int p50 = 0;
        int p75 = 0;
        for (CourseProgress row : progress) {
            boolean completed = row.getCompleted() != null && row.getCompleted() == 1;
            if (completed) {
                continue;
            }
            double value = row.getProgressRatio() == null ? 0 : row.getProgressRatio().doubleValue();
            if (value < 0.25) {
                lt25++;
            } else if (value < 0.5) {
                p25++;
            } else if (value < 0.75) {
                p50++;
            } else {
                p75++;
            }
        }
        List<CourseStatsVO.Bucket> result = new ArrayList<>();
        result.add(bucket("LT25", lt25));
        result.add(bucket("P25_50", p25));
        result.add(bucket("P50_75", p50));
        result.add(bucket("P75_90", p75));
        result.add(bucket("FINISHED", finished));
        return result;
    }

    private CourseStatsVO.Bucket bucket(String code, int users) {
        CourseStatsVO.Bucket bucket = new CourseStatsVO.Bucket();
        bucket.setBucket(code);
        bucket.setUsers(users);
        return bucket;
    }

    private CourseStatsVO.ExitRange exitRange(Course course, List<CoursePlayRecord> records) {
        int duration = value(course.getDurationSec());
        if (duration <= 0) {
            return null;
        }
        List<Integer> ends = records.stream()
                .map(CoursePlayRecord::getEndPositionSec)
                .filter(Objects::nonNull)
                .filter(end -> end < duration * 0.9)
                .toList();
        if (ends.isEmpty()) {
            return null;
        }
        int bestStart = 0;
        int bestCount = -1;
        for (int start = 0; start + WINDOW_SEC <= duration; start += STEP_SEC) {
            int count = 0;
            for (Integer end : ends) {
                if (end >= start && end < start + WINDOW_SEC) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestStart = start;
            }
        }
        CourseStatsVO.ExitRange range = new CourseStatsVO.ExitRange();
        range.setStartSec(bestStart);
        range.setEndSec(Math.min(bestStart + WINDOW_SEC, duration));
        return range;
    }

    private CourseStatsVO.Share clientShare(List<CoursePlayRecord> records) {
        CourseStatsVO.Share share = new CourseStatsVO.Share();
        int total = records.size();
        long app = records.stream().filter(r -> "APP_ANDROID".equals(r.getClientType())).count();
        long web = total - app;
        share.setApp(ratio(app, total));
        share.setWeb(ratio(web, total));
        return share;
    }

    private CourseStatsVO.Share qualityShare(List<CoursePlayRecord> records) {
        CourseStatsVO.Share share = new CourseStatsVO.Share();
        long p720 = records.stream().filter(r -> "P720".equals(r.getQuality())).count();
        long p480 = records.stream().filter(r -> "P480".equals(r.getQuality())).count();
        long total = p720 + p480;
        share.setP720(ratio(p720, total));
        share.setP480(ratio(p480, total));
        return share;
    }

    private CourseStatsVO.TopicStat topicStat(Course course, List<CourseProgress> progress) {
        if (course.getTopicId() == null) {
            return null;
        }
        List<Course> sections = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTopicId, course.getTopicId())
                .eq(Course::getStatus, "ON_SHELF"));
        if (sections.isEmpty()) {
            return null;
        }
        Set<Long> sectionIds = sections.stream().map(Course::getId).collect(Collectors.toSet());
        List<CourseProgress> all = progressMapper.selectList(Wrappers.<CourseProgress>lambdaQuery()
                .in(CourseProgress::getCourseId, sectionIds));
        Map<Long, List<CourseProgress>> byUser = all.stream()
                .collect(Collectors.groupingBy(CourseProgress::getUserId));
        int allFinished = 0;
        int finishedTotal = 0;
        for (List<CourseProgress> rows : byUser.values()) {
            int completed = (int) rows.stream()
                    .filter(p -> p.getCompleted() != null && p.getCompleted() == 1).count();
            finishedTotal += completed;
            if (completed >= sections.size()) {
                allFinished++;
            }
        }
        CourseStatsVO.TopicStat stat = new CourseStatsVO.TopicStat();
        stat.setLearners(byUser.size());
        stat.setAllFinished(allFinished);
        stat.setTotalSections(sections.size());
        stat.setAvgFinished(byUser.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(finishedTotal).divide(BigDecimal.valueOf(byUser.size()), 4, RoundingMode.HALF_UP));
        return stat;
    }

    @Override
    public ContentExportVO export(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw ContentErrors.notFound();
        }
        if ("DRAFT".equals(course.getStatus())) {
            throw ContentErrors.stateChanged("草稿没有可导出的数据");
        }
        CourseStatsVO stats = stats(courseId);
        List<String> headers = List.of("项目", "数值");
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("课程", course.getTitle()));
        rows.add(row("播放次数", stats.getPlayCount()));
        rows.add(row("观看人数", stats.getViewerCount()));
        rows.add(row("学完人数", stats.getFinishCount()));
        rows.add(row("完播率", stats.getFinishRate()));
        rows.add(row("人均观看秒数", stats.getAvgWatchSec()));
        rows.add(row("移动网络播放", stats.getMobilePlayCount()));
        rows.add(row("点问 AI", stats.getAskAiCount()));
        rows.add(row("点图文原文", stats.getKnowledgeClickCount()));
        rows.add(row("统计到", stats.getStatAt()));
        rows.add(row("", ""));
        rows.add(row("日期", "播放次数"));
        for (CourseStatsVO.Daily daily : stats.getDaily()) {
            rows.add(row(daily.getDate(), daily.getPlays()));
        }
        rows.add(row("", ""));
        rows.add(row("进度区间", "人数"));
        for (CourseStatsVO.Bucket bucket : stats.getProgressBuckets()) {
            rows.add(row(bucket.getBucket(), bucket.getUsers()));
        }
        String fileName = "课程数据_" + course.getTitle() + "_" + BeijingTime.today().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        ContentExportVO export = exportService.export(fileName, "课程数据", headers, rows, "COURSE", courseId,
                "课程数据导出");
        logs.record(OperationLogEntry.builder()
                .opType("EXPORT")
                .opAction("EXPORT")
                .bizType("COURSE")
                .bizId(courseId)
                .targetName(course.getTitle())
                .extraText("课程数据导出 " + export.getRowCount() + " 行")
                .build());
        return export;
    }

    /** 导出行：没有播放记录时部分统计为 null（List.of 不收 null），缺的值写“—” */
    private static List<Object> row(Object label, Object value) {
        List<Object> row = new ArrayList<>(2);
        row.add(label == null ? "" : label);
        row.add(value == null ? "—" : value);
        return row;
    }

    private Map<Long, StatAggregates.CoursePlayAgg> playAggAll() {
        Map<Long, StatAggregates.CoursePlayAgg> result = new LinkedHashMap<>();
        for (StatAggregates.CoursePlayAgg row : statMapper.selectPlayAggAll()) {
            result.put(row.getCourseId(), row);
        }
        return result;
    }

    private Map<Long, Long> finishAgg() {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (StatAggregates.CourseFinishAgg row : statMapper.selectFinishAgg()) {
            result.put(row.getCourseId(), row.getFinishes() == null ? 0 : row.getFinishes());
        }
        return result;
    }

    private static BigDecimal ratio(long value, long total) {
        return total <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(value).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static boolean isMobile(String networkType) {
        if (networkType == null) {
            return false;
        }
        String value = networkType.toUpperCase();
        return value.contains("MOBILE") || value.contains("4G") || value.contains("5G");
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
