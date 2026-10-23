package com.zhimu.server.biz.today;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.entity.TodoHandleLog;
import com.zhimu.server.biz.today.entity.TodoRemind;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/** 纯单元测试里没有 MyBatis 上下文，LambdaQueryWrapper 需要先初始化表信息缓存（幂等）。 */
public final class TodayTableInfo {

    private static boolean initialized;

    private TodayTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(Todo.class, TodoRemind.class, TodoHandleLog.class, Message.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
