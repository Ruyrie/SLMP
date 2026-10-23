package com.zhimu.server.biz.health;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import com.zhimu.server.biz.health.entity.DisinfectPlan;
import com.zhimu.server.biz.health.entity.DisinfectPlanPen;
import com.zhimu.server.biz.health.entity.DisinfectRecord;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 纯单元测试里没有 MyBatis 上下文，LambdaQueryWrapper / LambdaUpdateWrapper 需要先初始化表信息缓存。
 * 每个使用 Mapper 包装器的测试在 @BeforeAll 调用一次即可（幂等）。
 */
public final class HealthTableInfo {

    private static boolean initialized;

    private HealthTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(ImmunePlan.class, ImmuneRecord.class, MedicationRecord.class,
                BatchWithdrawal.class, DisinfectPlan.class, DisinfectPlanPen.class, DisinfectTask.class,
                DisinfectRecord.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
