package com.zhimu.server.biz.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.HashSet;
import java.util.Set;

/** 单测里让 MyBatis-Plus 的 Lambda 条件能解析实体列（不启动 Spring）。 */
public final class StockTestMybatis {

    private static final Set<Class<?>> INITIALIZED = new HashSet<>();

    private StockTestMybatis() {
    }

    public static synchronized void init(Class<?>... types) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : types) {
            if (INITIALIZED.add(type)) {
                TableInfoHelper.initTableInfo(assistant, type);
            }
        }
    }
}
