package com.zhimu.server.framework.clock;

import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoClockConfigurerTest {

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void shiftsTodayButKeepsTimeOfDay() {
        DemoClockConfigurer configurer = configurer("2026-10-19");
        configurer.postProcessBeanFactory(null);

        assertThat(BeijingTime.today()).isEqualTo(LocalDate.of(2026, 10, 19));
        LocalDateTime real = LocalDateTime.now(BeijingTime.ZONE);
        assertThat(Duration.between(real.toLocalTime(), BeijingTime.nowTime()).abs()).isLessThan(Duration.ofSeconds(5));

        configurer.destroy();
        assertThat(BeijingTime.today()).isEqualTo(LocalDate.now(BeijingTime.ZONE));
    }

    @Test
    void blankValueKeepsRealClock() {
        configurer(" ").postProcessBeanFactory(null);
        assertThat(BeijingTime.today()).isEqualTo(LocalDate.now(BeijingTime.ZONE));
    }

    @Test
    void invalidValueFailsFast() {
        assertThatThrownBy(() -> configurer("2026/10/19").postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DemoClockConfigurer.PROPERTY);
    }

    private static DemoClockConfigurer configurer(String value) {
        DemoClockConfigurer configurer = new DemoClockConfigurer();
        configurer.setEnvironment(new MockEnvironment().withProperty(DemoClockConfigurer.PROPERTY, value));
        return configurer;
    }
}
