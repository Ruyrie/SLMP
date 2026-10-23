package com.zhimu.server.biz.herd.support.change;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/** 单元测试中初始化 MyBatis-Plus 的实体列缓存，让 LambdaUpdateWrapper 能解析列名（不启动 Spring）。 */
final class ChangeTableInfo {

    private static boolean initialized;

    private ChangeTableInfo() {
    }

    static synchronized void init(Class<?>... types) {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : types) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
