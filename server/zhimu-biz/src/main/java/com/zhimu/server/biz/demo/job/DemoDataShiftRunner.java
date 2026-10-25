package com.zhimu.server.biz.demo.job;

import com.zhimu.server.common.util.BeijingTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动时把演示示例数据平移到“今天”（开关 zhimu.demo.shift.enabled，dev 默认开，环境变量 ZMT_DEMO_SHIFT 可关闭）。
 * <p>
 * 执行时机：全部单例创建完成（Flyway 迁移已结束）之后、Web 服务启动和定时任务注册之前，
 * 因此一定早于饲喂补生成、消毒任务生成、巡检与待办每日生成等 ApplicationReadyEvent 启动任务。
 * “今天”取 {@link BeijingTime}，受演示时钟 ZMT_FIXED_DATE 影响。失败只记日志，不阻断启动。
 */
@Slf4j
@Component
public class DemoDataShiftRunner implements SmartInitializingSingleton {

    public static final String ENABLED = "zhimu.demo.shift.enabled";

    private final Environment environment;
    private final DemoDataShifter shifter;

    @Autowired
    public DemoDataShiftRunner(Environment environment, DataSource dataSource) {
        this(environment, new DemoDataShifter(dataSource));
    }

    DemoDataShiftRunner(Environment environment, DemoDataShifter shifter) {
        this.environment = environment;
        this.shifter = shifter;
    }

    @Override
    public void afterSingletonsInstantiated() {
        run();
    }

    /** @return 是否执行了平移 */
    boolean run() {
        if (!environment.getProperty(ENABLED, Boolean.class, false)) {
            return false;
        }
        try {
            return shifter.shiftQuietly(BeijingTime.today(), BeijingTime.now()).executed();
        } catch (RuntimeException e) {
            log.error("演示数据平移失败，不影响启动", e);
            return false;
        }
    }
}
