package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.AiSession;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.entity.CourseTopic;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.ContinueWatchingVO;
import com.zhimu.server.biz.ai.vo.CourseCardVO;
import com.zhimu.server.biz.ai.vo.CourseCategoryVO;
import com.zhimu.server.biz.ai.vo.CoursePlayVO;
import com.zhimu.server.biz.ai.vo.KnowledgeDetailVO;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.biz.ai.vo.ProgressResultVO;
import com.zhimu.server.biz.ai.vo.RatingResultVO;
import com.zhimu.server.biz.ai.vo.SpeechResultVO;
import com.zhimu.server.biz.ai.vo.TopicDetailVO;
import com.zhimu.server.biz.ai.vo.TopicVO;
import com.zhimu.server.biz.ai.vo.WatchHistoryVO;
import com.zhimu.server.common.api.PageVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块11 测试夹具：固定北京时间 2026-10-19 09:00。
 */
public final class AiFixtures {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 9, 0);

    private AiFixtures() {
    }

    public static AiSession session(long id, long userId) {
        AiSession session = new AiSession();
        session.setId(id);
        session.setFarmId(1001L);
        session.setUserId(userId);
        session.setTitle("氟苯尼考粉用于猪多久可以出栏");
        session.setTopic(AiCodes.TOPIC_MEDICATION);
        session.setBatchId(3001L);
        session.setBatchSnapshotJson("{\"batch_id\":3001,\"batch_no\":\"ZM2610-031\",\"category_name\":\"生猪\","
                + "\"stage_name\":\"育肥猪\",\"breed_name\":\"杜长大\",\"pen_id\":2003,\"pen_name\":\"3 号育肥舍\","
                + "\"day_age\":96,\"current_count\":200,\"count_unit_text\":\"头\"}");
        session.setBatchLinkState(AiCodes.LINK_VALID);
        session.setRoundCount(1);
        session.setContextTruncated(0);
        session.setLastAskedAt(NOW.minusHours(1));
        session.setHasCorrection(0);
        session.setHasEpidemicAlert(0);
        session.setCreatedAt(NOW.minusHours(1));
        session.setUpdatedAt(NOW.minusHours(1));
        return session;
    }

    public static AiMessage message(long id, long sessionId, int roundNo) {
        AiMessage message = new AiMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setFarmId(1001L);
        message.setUserId(10001L);
        message.setRoundNo(roundNo);
        message.setClientMsgId("key-" + id);
        message.setClientType("APP_ANDROID");
        message.setQuestionText("氟苯尼考粉用于猪，多久可以出栏？");
        message.setBatchId(3001L);
        message.setBatchNo("ZM2610-031");
        message.setStatus(AiCodes.STATUS_ANSWERED);
        message.setAnswerType(AiCodes.ANSWER_NORMAL);
        message.setAnswerText("按平台休药期标准，氟苯尼考粉用于猪的休药期为 20 天[^1]。");
        message.setAskedAt(NOW.minusHours(1));
        message.setStartedAt(NOW.minusHours(1));
        message.setAnsweredAt(NOW.minusHours(1).plusSeconds(18));
        return message;
    }

    public static Course course(long id, long topicId, int sectionNo) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("腹泻的常见原因");
        course.setCategoryId(5102L);
        course.setCategoryName("疾病防治");
        course.setTopicId(topicId);
        course.setTopicName("仔猪腹泻防控");
        course.setSectionNo(sectionNo);
        course.setDurationSec(178);
        course.setSourceLabel("平台知识库");
        course.setCoverUrl("https://cdn.test/course/" + id + ".jpg");
        course.setShareUrl("https://web.test/course/" + id);
        course.setStatus("ON_SHELF");
        course.setPublishedAt(NOW.minusDays(3));
        course.setCreatedAt(NOW.minusDays(30));
        course.setUpdatedAt(NOW.minusDays(7));
        return course;
    }

    public static CourseTopic topic(long id) {
        CourseTopic topic = new CourseTopic();
        topic.setId(id);
        topic.setName("仔猪腹泻防控");
        topic.setCategoryId(5102L);
        topic.setCategoryName("疾病防治");
        topic.setIntro("从腹泻的常见原因讲起。");
        topic.setCoverUrl("https://cdn.test/topic/" + id + ".jpg");
        topic.setSortNo(1);
        topic.setStatus("ON_SHELF");
        return topic;
    }

    public static CourseProgress progress(long courseId, int position, int maxPosition, int watched,
                                          int duration, boolean completed) {
        CourseProgress progress = new CourseProgress();
        progress.setId(courseId + 9000);
        progress.setUserId(10001L);
        progress.setCourseId(courseId);
        progress.setPositionSec(position);
        progress.setMaxPositionSec(maxPosition);
        progress.setWatchedSec(watched);
        progress.setDurationSec(duration);
        progress.setProgressRatio(AiText.progressRatio(completed, maxPosition, duration));
        progress.setCompleted(completed ? 1 : 0);
        progress.setCompletedAt(completed ? NOW.minusDays(1) : null);
        progress.setLastWatchedAt(NOW.minusHours(courseId % 10));
        progress.setLastClientType("APP_ANDROID");
        progress.setLastQuality("P720");
        progress.setLastClientTime(NOW.minusHours(1));
        return progress;
    }

    public static ConnectedFixture fixture() {
        return new ConnectedFixture();
    }

    public static AiSessionItemVO sessionItem() {
        AiSessionItemVO vo = new AiSessionItemVO();
        vo.setId(1021L);
        vo.setTitle("氟苯尼考粉用于猪多久可以出栏");
        vo.setTopic(AiCodes.TOPIC_MEDICATION);
        vo.setBatchNo("ZM2610-031");
        vo.setRoundCount(1);
        vo.setLastAskedAt("2026-10-19 09:12:00");
        vo.setDateGroup("TODAY");
        vo.setDateGroupText("今天");
        vo.setHasCorrection(true);
        vo.setContextTruncated(false);
        vo.setHasEpidemicAlert(false);
        return vo;
    }

    public static AiSessionDetailVO sessionDetail() {
        AiSessionDetailVO vo = new AiSessionDetailVO();
        vo.setId(1015L);
        vo.setTitle("保育猪换料后怎么减少腹泻");
        vo.setRoundCount(3);
        vo.setLastRoundNo(3);
        vo.setBatchLinkState(AiCodes.LINK_VALID);
        vo.setBatch(batch());
        return vo;
    }

    public static LinkedBatchVO batch() {
        LinkedBatchVO vo = new LinkedBatchVO();
        vo.setBatchId(3003L);
        vo.setBatchNo("ZM2610-045");
        vo.setCategoryName("生猪");
        vo.setStageName("保育猪");
        vo.setBreedName("杜长大");
        vo.setPenId(2001L);
        vo.setPenName("1 号保育舍");
        vo.setDayAge(35);
        vo.setCurrentCount(320);
        vo.setCountUnitText("头");
        return vo;
    }

    public static AiMessageVO messageVo() {
        AiMessageVO vo = new AiMessageVO();
        vo.setId(5501L);
        vo.setSessionId(1021L);
        vo.setRoundNo(1);
        vo.setAskedAt("2026-10-19 09:12:00");
        vo.setQuestionText("氟苯尼考粉用于猪，多久可以出栏？");
        vo.setStatus(AiCodes.STATUS_ANSWERED);
        vo.setAnswerType(AiCodes.ANSWER_NORMAL);
        vo.setAnswerText("按平台休药期标准，氟苯尼考粉用于猪的休药期为 20 天[^1]。");
        vo.setCanRate(true);
        vo.setCanCopy(true);
        vo.setInContext(true);
        vo.setAnsweredAt("2026-10-19 09:12:18");
        return vo;
    }

    public static PageVO<AiSessionItemVO> sessionPage() {
        return PageVO.of(List.of(sessionItem()), 1, 1, 20);
    }

    public static PageVO<CourseCardVO> coursePage() {
        CourseCardVO card = new CourseCardVO();
        card.setId(6102L);
        card.setTitle("仔猪腹泻的日常防控");
        card.setCoverUrl("https://cdn.test/course/6102.jpg");
        card.setDurationSec(222);
        card.setPublishedAt("2026-10-16 10:00:00");
        card.setCategoryName("疾病防治");
        card.setLearnStatus(AiCodes.LEARN_LEARNING);
        card.setPositionSec(84);
        card.setProgressRatio(new BigDecimal("0.3784"));
        card.setTopicId(7001L);
        card.setTopicName("仔猪腹泻防控");
        card.setSectionNo(2);
        return PageVO.of(List.of(card), 1, 1, 20);
    }

    public static PageVO<TopicVO> topicPage() {
        TopicVO vo = new TopicVO();
        vo.setId(7001L);
        vo.setName("仔猪腹泻防控");
        vo.setCategoryName("疾病防治");
        vo.setCoverUrl("https://cdn.test/topic/7001.jpg");
        vo.setCourseCount(6);
        vo.setTotalDurationSec(1318);
        vo.setLearnedCount(2);
        vo.setLastLearnedAt("2026-10-18 20:15:00");
        return PageVO.of(List.of(vo), 1, 1, 20);
    }

    public static PageVO<WatchHistoryVO> historyPage() {
        WatchHistoryVO vo = new WatchHistoryVO();
        vo.setId(6102L);
        vo.setTitle("仔猪腹泻的日常防控");
        vo.setDurationSec(222);
        vo.setLearnStatus(AiCodes.LEARN_LEARNING);
        vo.setPositionSec(84);
        vo.setProgressRatio(new BigDecimal("0.3784"));
        vo.setLastWatchedAt("2026-10-18 20:15:00");
        return PageVO.of(List.of(vo), 1, 1, 20);
    }

    public static TopicDetailVO topicDetail() {
        TopicDetailVO vo = new TopicDetailVO();
        vo.setId(7001L);
        vo.setName("仔猪腹泻防控");
        vo.setCategoryName("疾病防治");
        vo.setCourseCount(6);
        vo.setTotalDurationSec(1318);
        vo.setLearnedCount(2);
        vo.setIntro("从腹泻的常见原因讲起。");
        vo.setAction(AiCodes.TOPIC_ACTION_CONTINUE);
        TopicDetailVO.Resume resume = new TopicDetailVO.Resume();
        resume.setCourseId(6102L);
        resume.setSectionNo(2);
        resume.setPositionSec(84);
        vo.setResume(resume);
        return vo;
    }

    public static ContinueWatchingVO continueWatching() {
        ContinueWatchingVO vo = new ContinueWatchingVO();
        vo.setCourseId(6102L);
        vo.setTitle("仔猪腹泻的日常防控");
        vo.setDurationSec(222);
        vo.setPositionSec(84);
        vo.setRemainingSec(138);
        vo.setProgressRatio(new BigDecimal("0.3784"));
        vo.setTopicName("仔猪腹泻防控");
        vo.setSectionNo(2);
        return vo;
    }

    public static CoursePlayVO coursePlay() {
        CoursePlayVO vo = new CoursePlayVO();
        vo.setId(6101L);
        vo.setTitle("腹泻的常见原因");
        vo.setCategoryName("疾病防治");
        vo.setDurationSec(178);
        vo.setSourceLabel("平台知识库");
        vo.setKnowledgeId(8104L);
        vo.setSectionNo(1);
        vo.setNextCourseId(6102L);
        CoursePlayVO.Progress progress = new CoursePlayVO.Progress();
        progress.setPositionSec(178);
        progress.setCompleted(true);
        vo.setProgress(progress);
        return vo;
    }

    public static ProgressResultVO progressResult() {
        ProgressResultVO vo = new ProgressResultVO();
        vo.setPositionSec(94);
        vo.setMaxPositionSec(94);
        vo.setProgressRatio(new BigDecimal("0.4234"));
        vo.setCompleted(false);
        vo.setJustCompleted(false);
        vo.setLearnStatus(AiCodes.LEARN_LEARNING);
        vo.setTopicLearnedCount(2);
        return vo;
    }

    public static RatingResultVO rating() {
        RatingResultVO vo = new RatingResultVO();
        vo.setRating(AiCodes.RATING_USEFUL);
        vo.setRatedAt("2026-10-19 09:41:00");
        return vo;
    }

    public static SpeechResultVO speech() {
        SpeechResultVO vo = new SpeechResultVO();
        vo.setRecognized(true);
        vo.setText("保育猪换料后拉稀，要停料吗？");
        vo.setDurationMs(3200);
        return vo;
    }

    public static KnowledgeDetailVO knowledge() {
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(8101L);
        vo.setTitle("休药期计算与出栏");
        vo.setCategoryName("用药规范");
        vo.setSourceType(AiCodes.SOURCE_PLATFORM);
        vo.setSourceTypeText("平台知识库");
        vo.setVersionNo(3);
        vo.setUpdatedAt("2026-10-12 10:00:00");
        vo.setHighlightParaKey("p3");
        vo.setCitedVersionNo(3);
        vo.setUpdatedAfterAnswer(false);
        return vo;
    }

    public static List<CourseCategoryVO> categories() {
        CourseCategoryVO vo = new CourseCategoryVO();
        vo.setId(5101L);
        vo.setName("饲养管理");
        vo.setSortNo(1);
        return List.of(vo);
    }

    /** 便于断言控制器返回的 data 形状 */
    public static final class ConnectedFixture {
        public List<AiSessionItemVO> sessions() {
            return new ArrayList<>(List.of(sessionItem()));
        }
    }
}
