package com.zhimu.server.biz.ai.convert;

import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.entity.CourseProgress;
import com.zhimu.server.biz.ai.support.CourseCoverResolver;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CourseConvertTest {
    private final CourseConvert convert = new CourseConvert(mock(CourseCoverResolver.class),
            new com.zhimu.server.biz.ai.support.AiShareLinks("https://web.test"));

    @Test
    void displayRatioUsesResumePositionAndCurrentDurationWithoutRewritingHistory() {
        Course course = new Course(); course.setId(6201L); course.setDurationSec(634);
        CourseProgress progress = new CourseProgress();
        progress.setPositionSec(1); progress.setMaxPositionSec(310); progress.setDurationSec(310);
        progress.setProgressRatio(BigDecimal.ONE); progress.setWatchedSec(1); progress.setCompleted(0);
        assertThat(convert.toCard(course, progress).getProgressRatio()).isEqualByComparingTo("0.0016");
        assertThat(convert.toContinue(course, progress).getProgressRatio()).isEqualByComparingTo("0.0016");
        assertThat(convert.toContinue(course, progress).getRemainingSec()).isEqualTo(633);
        assertThat(progress.getMaxPositionSec()).isEqualTo(310);
        assertThat(progress.getDurationSec()).isEqualTo(310);
        assertThat(progress.getProgressRatio()).isEqualByComparingTo("1");
        assertThat(progress.getCompleted()).isZero();
    }

    @Test
    void previouslyCompletedCoursesStayCompletedDuringReplay() {
        Course course = new Course(); course.setDurationSec(634);
        CourseProgress progress = new CourseProgress();
        progress.setPositionSec(1); progress.setMaxPositionSec(178); progress.setCompleted(1);
        assertThat(convert.toCard(course, progress).getProgressRatio()).isEqualByComparingTo("1");
        assertThat(convert.toCard(course, progress).getLearnStatus()).isEqualTo("COMPLETED");
    }
}
