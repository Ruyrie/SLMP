package com.zhimu.server.biz.feeding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.FeedExecDaily;
import com.zhimu.server.biz.feeding.entity.FeedPlan;
import com.zhimu.server.biz.feeding.entity.FeedPlanMeal;
import com.zhimu.server.biz.feeding.entity.FeedPlanStage;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.entity.WeighRecordItem;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 纯单元测试里没有 MyBatis 上下文，LambdaQueryWrapper 需要先初始化表信息缓存。
 * 每个使用 Mapper 包装器的测试在 setUp 调用一次即可（幂等）。
 */
public final class FeedingTableInfo {

    private static boolean initialized;

    private FeedingTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(FeedTask.class, FeedPlan.class, FeedPlanStage.class, FeedPlanMeal.class,
                FeedRecord.class, WeighRecord.class, WeighRecordItem.class, BatchGrowth.class,
                FeedExecDaily.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
