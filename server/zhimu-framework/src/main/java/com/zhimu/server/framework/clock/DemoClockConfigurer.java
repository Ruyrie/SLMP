package com.zhimu.server.framework.clock;

import com.zhimu.server.common.util.BeijingTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 演示时钟：配置 zhimu.clock.fixed-date（yyyy-MM-dd）时，在其他 Bean 创建之前把 {@link BeijingTime} 的“今天”平移到该日期，
 * 时刻照常流逝（过了午夜进入下一天）；为空时使用真实时间。只用于演示与联调，生产环境不要配置。
 */
@Slf4j
@Component
public class DemoClockConfigurer implements BeanFactoryPostProcessor, EnvironmentAware, Ordered, DisposableBean {

    public static final String PROPERTY = "zhimu.clock.fixed-date";

    private Environment environment;
    private boolean applied;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String value = environment.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            return;
        }
        LocalDate fixedDate;
        try {
            fixedDate = LocalDate.parse(value.trim(), BeijingTime.DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(PROPERTY + " 格式应为 yyyy-MM-dd，当前为：" + value, e);
        }
        Clock system = Clock.system(BeijingTime.ZONE);
        long days = ChronoUnit.DAYS.between(LocalDate.now(system), fixedDate);
        BeijingTime.setClock(Clock.offset(system, Duration.ofDays(days)));
        applied = true;
        log.warn("演示时钟已启用：今天按 {} 计算（平移 {} 天，时间照常流逝），当前北京时间 {}",
                fixedDate, days, BeijingTime.format(BeijingTime.now()));
    }

    @Override
    public void destroy() {
        if (applied) {
            BeijingTime.setClock(null);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
