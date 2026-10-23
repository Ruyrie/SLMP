package com.zhimu.server.admin.base;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.FeedStdVersion;
import com.zhimu.server.admin.base.entity.InspectItem;
import com.zhimu.server.admin.base.entity.InspectOption;
import com.zhimu.server.admin.base.entity.InspectTemplate;
import com.zhimu.server.admin.base.entity.InspectTemplateVersion;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import com.zhimu.server.admin.base.entity.StdWeightVersion;
import com.zhimu.server.admin.base.entity.SymptomDisease;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/** 纯单元测试没有 MyBatis 上下文，LambdaQueryWrapper 使用前需初始化 13 张表的表信息缓存（幂等）。 */
public final class AdminBaseTableInfo {

    private static boolean initialized;

    private AdminBaseTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(Category.class, Breed.class, StdWeightVersion.class, StdWeightPoint.class,
                FeedStdVersion.class, FeedStdStage.class, InspectTemplate.class, InspectTemplateVersion.class,
                InspectItem.class, InspectOption.class, DictItem.class, DictItemScope.class, SymptomDisease.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
