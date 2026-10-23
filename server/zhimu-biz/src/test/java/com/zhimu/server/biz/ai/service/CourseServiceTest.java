package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.convert.CourseConvert;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseCategory;
import com.zhimu.server.biz.ai.entity.CoursePlayRecord;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.mapper.CourseCategoryMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.mapper.CourseMediaMapper;
import com.zhimu.server.biz.ai.mapper.CoursePlayRecordMapper;
import com.zhimu.server.biz.ai.mapper.CourseProgressMapper;
import com.zhimu.server.biz.ai.mapper.CourseTopicMapper;
import com.zhimu.server.biz.ai.query.CourseQuery;
import com.zhimu.server.biz.ai.query.TopicQuery;
import com.zhimu.server.biz.ai.request.CourseProgressRequest;
import com.zhimu.server.biz.ai.service.impl.CourseServiceImpl;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiFixtures;
import com.zhimu.server.biz.ai.vo.CourseCardVO;
import com.zhimu.server.biz.ai.vo.CourseCategoryVO;
import com.zhimu.server.biz.ai.vo.ProgressResultVO;
import com.zhimu.server.biz.ai.vo.TopicDetailVO;
import com.zhimu.server.biz.ai.vo.TopicVO;
import com.zhimu.server.biz.ai.vo.WatchHistoryVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API-K14–K21：分类、专题续学、课程分页、进度上报的学完判定与乱序拦截。
 */
class CourseServiceTest {

    private final CourseCategoryMapper categoryMapper = mock(CourseCategoryMapper.class);
    private final CourseTopicMapper topicMapper = mock(CourseTopicMapper.class);
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final CourseMediaMapper mediaMapper = mock(CourseMediaMapper.class);
    private final CourseProgressMapper progressMapper = mock(CourseProgressMapper.class);
    private final CoursePlayRecordMapper playRecordMapper = mock(CoursePlayRecordMapper.class);
    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(AiFixtures.NOW.atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        service = new CourseServiceImpl(categoryMapper, topicMapper, courseMapper, mediaMapper, progressMapper,
                playRecordMapper, new CourseConvert());
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void categoriesOnlyEnabledOrdered() {
        CourseCategory category = new CourseCategory();
        category.setId(5101L);
        category.setName("饲养管理");
        category.setSortNo(1);
        category.setStatus("ENABLED");
        when(categoryMapper.selectList(any())).thenReturn(List.of(category));
        List<CourseCategoryVO> list = service.categories();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getName()).isEqualTo("饲养管理");
    }

    @Test
    void topicDetailContinuesFromLatestUnfinishedCourse() {
        when(topicMapper.selectById(7001L)).thenReturn(AiFixtures.topic(7001L));
        Course first = AiFixtures.course(6101L, 7001L, 1);
        first.setDurationSec(178);
        Course second = AiFixtures.course(6102L, 7001L, 2);
        second.setDurationSec(222);
        Course third = AiFixtures.course(6103L, 7001L, 3);
        when(courseMapper.selectList(any())).thenReturn(List.of(first, second, third));
        CourseProgress firstDone = AiFixtures.progress(6101L, 178, 178, 178, 178, true);
        CourseProgress secondLearning = AiFixtures.progress(6102L, 84, 84, 84, 222, false);
        secondLearning.setLastWatchedAt(AiFixtures.NOW.minusHours(1));
        firstDone.setLastWatchedAt(AiFixtures.NOW.minusDays(1));
        when(progressMapper.selectByUserAndCourses(eq(10001L), any())).thenReturn(List.of(firstDone, secondLearning));

        TopicDetailVO vo = service.topicDetail(10001L, 7001L);

        assertThat(vo.getAction()).isEqualTo(AiCodes.TOPIC_ACTION_CONTINUE);
        assertThat(vo.getResume().getCourseId()).isEqualTo(6102L);
        assertThat(vo.getResume().getPositionSec()).isEqualTo(84);
        assertThat(vo.getLearnedCount()).isEqualTo(1);
        assertThat(vo.getCourses()).hasSize(3);
    }

    @Test
    void topicDetailReviewsWhenAllCompleted() {
        when(topicMapper.selectById(7001L)).thenReturn(AiFixtures.topic(7001L));
        Course first = AiFixtures.course(6101L, 7001L, 1);
        Course second = AiFixtures.course(6102L, 7001L, 2);
        when(courseMapper.selectList(any())).thenReturn(List.of(first, second));
        when(progressMapper.selectByUserAndCourses(eq(10001L), any())).thenReturn(List.of(
                AiFixtures.progress(6101L, 178, 178, 178, 178, true),
                AiFixtures.progress(6102L, 222, 222, 222, 222, true)));

        TopicDetailVO vo = service.topicDetail(10001L, 7001L);

        assertThat(vo.getAction()).isEqualTo(AiCodes.TOPIC_ACTION_REVIEW);
        assertThat(vo.getResume()).isNull();
    }

    @Test
    void topicOffShelfReturns41001() {
        CourseTopic topic = AiFixtures.topic(7001L);
        topic.setStatus("OFF_SHELF");
        when(topicMapper.selectById(7001L)).thenReturn(topic);
        assertThatThrownBy(() -> service.topicDetail(10001L, 7001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(41001);
    }

    @Test
    void courseListFiltersLearnStatusAndPaginates() {
        Course first = AiFixtures.course(6101L, 7001L, 1);
        Course second = AiFixtures.course(6102L, 7001L, 2);
        when(courseMapper.selectList(any())).thenReturn(List.of(first, second));
        when(progressMapper.selectByUserAndCourses(eq(10001L), any())).thenReturn(List.of(
                AiFixtures.progress(6101L, 178, 178, 178, 178, true)));
        CourseQuery query = new CourseQuery();
        query.setLearnStatus(AiCodes.LEARN_COMPLETED);
        query.setPage(1);
        query.setSize(20);

        PageVO<CourseCardVO> page = service.courses(10001L, query);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).getId()).isEqualTo(6101L);
        assertThat(page.getList().get(0).getLearnStatus()).isEqualTo(AiCodes.LEARN_COMPLETED);
    }

    @Test
    void courseKeywordLongerThanThirtyRejected() {
        CourseQuery query = new CourseQuery();
        query.setKeyword("长".repeat(31));
        assertThatThrownBy(() -> service.courses(10001L, query))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40001);
    }

    @Test
    void reportProgressCompletesWhenMaxPositionAndWatchedReachThreshold() {
        Course course = AiFixtures.course(6102L, 0L, 2);
        course.setDurationSec(100);
        course.setTopicId(null);
        when(courseMapper.selectById(6102L)).thenReturn(course);
        when(progressMapper.selectByUserAndCourse(10001L, 6102L)).thenReturn(null);
        CourseProgressRequest request = request(AiCodes.EVENT_END, 95, 60);
        request.setClientTime(AiFixtures.NOW.minusSeconds(10));

        ProgressResultVO vo = service.reportProgress(10001L, 6102L, request);

        assertThat(vo.isCompleted()).isTrue();
        assertThat(vo.isJustCompleted()).isTrue();
        assertThat(vo.getLearnStatus()).isEqualTo(AiCodes.LEARN_COMPLETED);
        assertThat(vo.getProgressRatio()).isEqualByComparingTo("1.0000");
        ArgumentCaptor<CourseProgress> captor = ArgumentCaptor.forClass(CourseProgress.class);
        verify(progressMapper).insert(captor.capture());
        assertThat(captor.getValue().getMaxPositionSec()).isEqualTo(95);
        assertThat(captor.getValue().getWatchedSec()).isEqualTo(60);
        verify(playRecordMapper).insert(any(CoursePlayRecord.class));
    }

    @Test
    void reportProgressClampsWatchedByServerInterval() {
        Course course = AiFixtures.course(6102L, 0L, 2);
        course.setDurationSec(222);
        course.setTopicId(null);
        when(courseMapper.selectById(6102L)).thenReturn(course);
        CoursePlayRecord record = new CoursePlayRecord();
        record.setUserId(10001L);
        record.setCourseId(6102L);
        record.setPlayToken("token-1");
        record.setLastClientTime(AiFixtures.NOW.minusSeconds(15));
        when(playRecordMapper.selectByToken(10001L, "token-1")).thenReturn(record);
        CourseProgress existing = AiFixtures.progress(6102L, 84, 84, 10, 222, false);
        existing.setLastClientTime(AiFixtures.NOW.minusSeconds(60));
        when(progressMapper.selectByUserAndCourse(10001L, 6102L)).thenReturn(existing);
        CourseProgressRequest request = request(AiCodes.EVENT_TICK, 94, 60);
        request.setClientTime(AiFixtures.NOW.minusSeconds(5));

        service.reportProgress(10001L, 6102L, request);

        ArgumentCaptor<CourseProgress> captor = ArgumentCaptor.forClass(CourseProgress.class);
        verify(progressMapper).updateById(captor.capture());
        // 10 秒间隔最多 10*2+5=25 秒，原 10 秒，合计 35 秒
        assertThat(captor.getValue().getWatchedSec()).isEqualTo(35);
        assertThat(captor.getValue().getPositionSec()).isEqualTo(94);
    }

    @Test
    void olderClientTimeDoesNotOverwritePosition() {
        Course course = AiFixtures.course(6102L, 0L, 2);
        course.setDurationSec(222);
        course.setTopicId(null);
        when(courseMapper.selectById(6102L)).thenReturn(course);
        when(playRecordMapper.selectByToken(anyLong(), any())).thenReturn(null);
        CourseProgress existing = AiFixtures.progress(6102L, 84, 84, 84, 222, false);
        existing.setLastClientTime(AiFixtures.NOW);
        when(progressMapper.selectByUserAndCourse(10001L, 6102L)).thenReturn(existing);
        CourseProgressRequest request = request(AiCodes.EVENT_TICK, 20, 5);
        request.setClientTime(AiFixtures.NOW.minusMinutes(1));

        service.reportProgress(10001L, 6102L, request);

        ArgumentCaptor<CourseProgress> captor = ArgumentCaptor.forClass(CourseProgress.class);
        verify(progressMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPositionSec()).isEqualTo(84);
        assertThat(captor.getValue().getMaxPositionSec()).isEqualTo(84);
    }

    @Test
    void reportProgressRejectsMissingPlayToken() {
        CourseProgressRequest request = request(AiCodes.EVENT_START, 0, 0);
        request.setPlayToken(null);
        assertThatThrownBy(() -> service.reportProgress(10001L, 6102L, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40001);
        verify(progressMapper, never()).insert(any(CourseProgress.class));
    }

    @Test
    void historyExcludesOffShelfCourses() {
        Course on = AiFixtures.course(6102L, 7001L, 2);
        Course off = AiFixtures.course(6301L, 0L, 1);
        off.setStatus("OFF_SHELF");
        when(progressMapper.selectList(any())).thenReturn(List.of(
                AiFixtures.progress(6102L, 84, 84, 84, 222, false),
                AiFixtures.progress(6301L, 100, 100, 100, 266, false)));
        when(courseMapper.selectById(6102L)).thenReturn(on);
        when(courseMapper.selectById(6301L)).thenReturn(off);

        PageVO<WatchHistoryVO> page = service.history(10001L, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).getId()).isEqualTo(6102L);
        assertThat(page.getList().get(0).getLastWatchedAt()).isNotBlank();
    }

    @Test
    void topicsSortByRecentLearned() {
        when(topicMapper.selectList(any())).thenReturn(List.of(AiFixtures.topic(7001L), topic(7002L, 2)));
        Course first = AiFixtures.course(6102L, 7001L, 2);
        Course second = AiFixtures.course(6201L, 7002L, 1);
        when(courseMapper.selectList(any())).thenReturn(List.of(first, second));
        CourseProgress firstProgress = AiFixtures.progress(6102L, 84, 84, 84, 222, false);
        firstProgress.setLastWatchedAt(AiFixtures.NOW.minusHours(2));
        CourseProgress secondProgress = AiFixtures.progress(6201L, 10, 10, 10, 310, false);
        secondProgress.setLastWatchedAt(AiFixtures.NOW.minusHours(1));
        when(progressMapper.selectByUserAndCourses(eq(10001L), any()))
                .thenReturn(List.of(firstProgress, secondProgress));
        when(progressMapper.selectByUserAndCourses(eq(10001L), eq(List.of(6102L, 6201L))))
                .thenReturn(List.of(firstProgress, secondProgress));
        TopicQuery query = new TopicQuery();
        query.setSort("RECENT_LEARNED");

        PageVO<TopicVO> page = service.topics(10001L, query);

        assertThat(page.getList()).extracting(TopicVO::getId).containsExactly(7002L, 7001L);
    }

    private static CourseProgressRequest request(String event, int position, int watched) {
        CourseProgressRequest request = new CourseProgressRequest();
        request.setPlayToken("token-1");
        request.setEvent(event);
        request.setPositionSec(position);
        request.setWatchedSec(watched);
        request.setQuality("P720");
        request.setNetworkType("WIFI");
        request.setEntryType("COURSE");
        request.setClientTime(AiFixtures.NOW.minusSeconds(5));
        return request;
    }

    private static CourseTopic topic(long id, int sortNo) {
        CourseTopic topic = AiFixtures.topic(id);
        topic.setName("育肥猪精细化饲喂");
        topic.setSortNo(sortNo);
        return topic;
    }
}
