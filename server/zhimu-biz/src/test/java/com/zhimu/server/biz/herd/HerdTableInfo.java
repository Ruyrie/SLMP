package com.zhimu.server.biz.herd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.BatchTimeline;
import com.zhimu.server.biz.herd.entity.Pen;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/** 纯单元测试里没有 MyBatis 上下文，LambdaUpdateWrapper 需要先初始化表信息缓存（幂等）。 */
public final class HerdTableInfo {

    private static boolean initialized;

    private HerdTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(Batch.class, BatchEvent.class, BatchPen.class, BatchTimeline.class, Pen.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
