package com.zhimu.server.biz.ai.convert;

import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseMedia;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.support.CourseCoverResolver;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiText;
import com.zhimu.server.biz.ai.vo.ContinueWatchingVO;
import com.zhimu.server.biz.ai.vo.CourseCardVO;
import com.zhimu.server.biz.ai.vo.CoursePlayVO;
import com.zhimu.server.biz.ai.vo.VideoSourceVO;
import com.zhimu.server.biz.ai.vo.WatchHistoryVO;
import com.zhimu.server.common.util.BeijingTime;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 课堂 VO 组装：课程卡、继续观看、播放信息、观看记录。
 */
@Component
@RequiredArgsConstructor
public class CourseConvert {
    private final CourseCoverResolver covers;

    public String topicCover(CourseTopic topic) {
        return covers.resolve(topic);
    }

    public CourseCardVO toCard(Course course, CourseProgress progress) {
        CourseCardVO vo = new CourseCardVO();
        fillCard(vo, course, progress);
        return vo;
    }

    public WatchHistoryVO toHistory(Course course, CourseProgress progress) {
        WatchHistoryVO vo = new WatchHistoryVO();
        fillCard(vo, course, progress);
        vo.setLastWatchedAt(progress == null ? null : BeijingTime.format(progress.getLastWatchedAt()));
        return vo;
    }

    private void fillCard(CourseCardVO vo, Course course, CourseProgress progress) {
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setCoverUrl(covers.resolve(course));
        vo.setDurationSec(course.getDurationSec() == null ? 0 : course.getDurationSec());
        vo.setPublishedAt(BeijingTime.format(course.getPublishedAt()));
        vo.setCategoryName(course.getCategoryName());
        boolean completed = progress != null && progress.getCompleted() != null && progress.getCompleted() == 1;
        int maxPosition = progress == null || progress.getMaxPositionSec() == null ? 0 : progress.getMaxPositionSec();
        vo.setLearnStatus(AiText.learnStatus(completed, maxPosition));
        vo.setPositionSec(progress == null || progress.getPositionSec() == null ? 0 : progress.getPositionSec());
        // 展示进度与“看到”的续播位置一致；不改写持久化的最远观看位置。
        vo.setProgressRatio(AiText.progressRatio(completed, vo.getPositionSec(), vo.getDurationSec()));
        vo.setTopicId(course.getTopicId());
        vo.setTopicName(course.getTopicName());
        vo.setSectionNo(course.getSectionNo());
    }

    public ContinueWatchingVO toContinue(Course course, CourseProgress progress) {
        ContinueWatchingVO vo = new ContinueWatchingVO();
        vo.setCourseId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setCoverUrl(covers.resolve(course));
        vo.setCategoryName(course.getCategoryName());
        int duration = course.getDurationSec() == null ? 0 : course.getDurationSec();
        int position = progress.getPositionSec() == null ? 0 : progress.getPositionSec();
        vo.setDurationSec(duration);
        vo.setPositionSec(position);
        vo.setRemainingSec(Math.max(duration - position, 0));
        vo.setProgressRatio(AiText.progressRatio(progress.getCompleted() != null && progress.getCompleted() == 1,
                position, duration));
        vo.setTopicName(course.getTopicName());
        vo.setSectionNo(course.getSectionNo());
        return vo;
    }

    public CoursePlayVO toPlay(Course course, List<CourseMedia> media, CourseProgress progress) {
        CoursePlayVO vo = new CoursePlayVO();
        vo.setId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setCategoryName(course.getCategoryName());
        vo.setDurationSec(course.getDurationSec() == null ? 0 : course.getDurationSec());
        vo.setSourceLabel(course.getSourceLabel());
        vo.setUpdatedAt(BeijingTime.format(course.getUpdatedAt()));
        vo.setCoverUrl(covers.resolve(course));
        vo.setShareUrl(course.getShareUrl());
        vo.setKnowledgeId(course.getKnowledgeId());
        vo.setSectionNo(course.getSectionNo());
        List<VideoSourceVO> sources = new ArrayList<>();
        for (CourseMedia item : media) {
            VideoSourceVO source = new VideoSourceVO();
            source.setQuality(item.getQuality());
            source.setHlsUrl(item.getUrl());
            source.setSizeBytes(item.getSizeBytes() == null ? 0 : item.getSizeBytes());
            sources.add(source);
        }
        vo.setSources(sources);
        CoursePlayVO.Progress p = new CoursePlayVO.Progress();
        boolean completed = progress != null && progress.getCompleted() != null && progress.getCompleted() == 1;
        int position = progress == null || progress.getPositionSec() == null ? 0 : progress.getPositionSec();
        int duration = vo.getDurationSec();
        p.setPositionSec(position);
        p.setCompleted(completed);
        p.setResumePositionSec(AiText.resumePosition(completed, position, duration));
        vo.setProgress(p);
        return vo;
    }

    /** 进度比例统一保留 4 位小数 */
    public static BigDecimal ratio(boolean completed, int maxPositionSec, int durationSec) {
        return AiText.progressRatio(completed, maxPositionSec, durationSec);
    }

    /** 未学完的最近学习课程判断 */
    public static boolean learning(CourseProgress progress) {
        return progress != null
                && (progress.getCompleted() == null || progress.getCompleted() == 0)
                && progress.getPositionSec() != null && progress.getPositionSec() > 0;
    }

    public static String learnStatus(CourseProgress progress) {
        if (progress == null) {
            return AiCodes.LEARN_NOT_STARTED;
        }
        boolean completed = progress.getCompleted() != null && progress.getCompleted() == 1;
        int max = progress.getMaxPositionSec() == null ? 0 : progress.getMaxPositionSec();
        return AiText.learnStatus(completed, max);
    }
}
