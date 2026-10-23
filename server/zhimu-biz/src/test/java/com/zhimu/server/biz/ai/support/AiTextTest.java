package com.zhimu.server.biz.ai.support;

import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话标题、日期分组、主题、学习状态与进度口径。
 */
class AiTextTest {

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(AiFixtures.NOW.atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void sessionTitleRemovesPunctuationAndTruncates() {
        assertThat(AiText.sessionTitle("氟苯尼考粉用于猪，多久可以出栏？")).isEqualTo("氟苯尼考粉用于猪多久可以出栏");
        assertThat(AiText.sessionTitle("   ")).isEqualTo("图片提问");
        assertThat(AiText.sessionTitle("猪".repeat(60))).hasSize(50);
    }

    @Test
    void dateGroupUsesBeijingDate() {
        assertThat(AiText.dateGroup(AiFixtures.NOW)).isEqualTo("TODAY");
        assertThat(AiText.dateGroup(LocalDateTime.of(2026, 10, 18, 20, 0))).isEqualTo("YESTERDAY");
        assertThat(AiText.dateGroup(LocalDateTime.of(2026, 10, 15, 9, 0))).isEqualTo("LAST_7_DAYS");
        assertThat(AiText.dateGroup(LocalDateTime.of(2026, 9, 28, 8, 50))).isEqualTo("EARLIER");
        assertThat(AiText.dateGroupText("TODAY")).isEqualTo("今天");
        assertThat(AiText.dateGroupText("YESTERDAY")).isEqualTo("昨天");
        assertThat(AiText.dateGroupText("LAST_7_DAYS")).isEqualTo("近 7 天");
        assertThat(AiText.dateGroupText("EARLIER")).isEqualTo("更早");
    }

    @Test
    void topicFallsBackToKeyword() {
        assertThat(AiText.topicOf("氟苯尼考休药期多久")).isEqualTo(AiCodes.TOPIC_MEDICATION);
        assertThat(AiText.topicOf("育肥猪采食量下降")).isEqualTo(AiCodes.TOPIC_FEEDING);
        assertThat(AiText.topicOf("仔猪拉稀怎么办")).isEqualTo(AiCodes.TOPIC_DISEASE);
        assertThat(AiText.topicOf("猪瘟疫苗间隔")).isEqualTo(AiCodes.TOPIC_IMMUNE);
        assertThat(AiText.topicOf("带猪消毒")).isEqualTo(AiCodes.TOPIC_DISINFECT);
        assertThat(AiText.topicOf("今天天气")).isEqualTo(AiCodes.TOPIC_OTHER);
    }

    @Test
    void progressAndResumeRules() {
        assertThat(AiText.learnStatus(true, 178)).isEqualTo(AiCodes.LEARN_COMPLETED);
        assertThat(AiText.learnStatus(false, 84)).isEqualTo(AiCodes.LEARN_LEARNING);
        assertThat(AiText.learnStatus(false, 0)).isEqualTo(AiCodes.LEARN_NOT_STARTED);
        assertThat(AiText.progressRatio(false, 84, 222)).isEqualByComparingTo("0.3784");
        assertThat(AiText.progressRatio(true, 84, 222)).isEqualByComparingTo("1.0000");
        assertThat(AiText.resumePosition(false, 84, 222)).isEqualTo(84);
        assertThat(AiText.resumePosition(true, 222, 222)).isZero();
        assertThat(AiText.resumePosition(false, 219, 222)).isZero();
    }

    @Test
    void sanitizerKeepsOnlyWhitelistedTags() {
        String html = "<p class=\"x\">休药<strong>20 天</strong></p><script>alert(1)</script><ul><li>a</li></ul><br/>";
        assertThat(AiHtmlSanitizer.sanitize(html)).isEqualTo("休药<strong>20 天</strong><ul><li>a</li></ul><br/>");
    }
}
