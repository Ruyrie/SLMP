package com.zhimu.server.biz.account.convert;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.test.FixedTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserConvertTest {

    private final AccountProperties properties = new AccountProperties();

    @BeforeEach
    void setUp() {
        FixedTime.set();
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
    }

    @Test
    void 找回后7天的可注销日期() {
        User user = new User();
        user.setRecoveredAt(LocalDateTime.of(2026, 10, 19, 10, 25));
        assertThat(UserConvert.cancelAvailableDate(user, properties)).isEqualTo(java.time.LocalDate.of(2026, 10, 26));
        assertThat(UserConvert.cancelRestricted(user, properties)).isTrue();
        assertThat(UserConvert.toRecoveryInfo(user, properties).getCancelAvailableDate()).isEqualTo("2026-10-26");
    }

    @Test
    void 超过7天不再限制() {
        User user = new User();
        user.setRecoveredAt(LocalDateTime.of(2026, 10, 10, 10, 0));
        assertThat(UserConvert.cancelAvailableDate(user, properties)).isNull();
        assertThat(UserConvert.cancelRestricted(user, properties)).isFalse();
    }

    @Test
    void 姓名首字按码点截取() {
        assertThat(UserConvert.initial("😀小明")).isEqualTo("😀");
        assertThat(UserConvert.initial("  王建国 ")).isEqualTo("王");
        assertThat(UserConvert.initial("")).isNull();
    }
}
