package com.zhimu.server.biz.breeding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.entity.MatingRecord;
import com.zhimu.server.biz.breeding.entity.PregnancyCheck;
import com.zhimu.server.biz.breeding.entity.WeaningRecord;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 纯单元测试里没有 MyBatis 上下文，LambdaQueryWrapper 需要先初始化表信息缓存。
 * 每个使用 Mapper 包装器的测试在 setUp 调用一次即可（幂等）。
 */
public final class BreedingTableInfo {

    private static boolean initialized;

    private BreedingTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(BreedingAnimal.class, BreedingCycle.class, MatingRecord.class,
                PregnancyCheck.class, FarrowingRecord.class, WeaningRecord.class, Incubation.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
