package com.zhimu.server.biz.today;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.entity.TodoHandleLog;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 纯单元测试里使用 MyBatis-Plus LambdaQueryWrapper / LambdaUpdateWrapper 前需要先注册实体的 TableInfo，
 * 否则会报 can not find lambda cache for this entity。Spring 环境由 MyBatis 自动完成。
 */
public final class TodoTableInfo {

    private static boolean initialized;

    private TodoTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : List.of(Todo.class, TodoHandleLog.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        initialized = true;
    }
}
