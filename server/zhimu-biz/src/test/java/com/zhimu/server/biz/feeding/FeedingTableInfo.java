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
 * 纯单元测试里使用 MyBatis-Plus LambdaQueryWrapper 前需要先注册实体的 TableInfo，
 * 否则会报 can not find lambda cache for this entity。Spring 环境由 MyBatis 自动完成。
 */
public final class FeedingTableInfo {

    private static boolean initialized;

    private FeedingTableInfo() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        List<Class<?>> entities = List.of(FeedTask.class, FeedRecord.class, FeedPlan.class,
                FeedPlanStage.class, FeedPlanMeal.class, FeedExecDaily.class,
                BatchGrowth.class, WeighRecord.class, WeighRecordItem.class);
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        initialized = true;
    }
}
