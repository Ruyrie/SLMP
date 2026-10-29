package com.zhimu.server.biz.common.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯单元测试没有 MyBatis 上下文：用到 LambdaUpdateWrapper（显式 SET NULL）的测试先初始化实体表信息，
 * 避免单独运行时报 “can not find lambda cache”。幂等。
 */
public final class TableInfos {

    private TableInfos() {
    }

    public static synchronized void ensure(Class<?>... types) {
        for (Class<?> type : types) {
            if (TableInfoHelper.getTableInfo(type) == null) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
            }
        }
    }
}
