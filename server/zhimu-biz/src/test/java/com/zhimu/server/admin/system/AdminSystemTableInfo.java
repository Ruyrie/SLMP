package com.zhimu.server.admin.system;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.system.entity.Admin;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/** 纯单元测试里初始化 Admin 的 Lambda 表信息缓存（幂等），并提供取 UPDATE SET 片段的小工具。 */
public final class AdminSystemTableInfo {

    private static boolean initialized;

    private AdminSystemTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized || TableInfoHelper.getTableInfo(Admin.class) != null) {
            initialized = true;
            return;
        }
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Admin.class);
        initialized = true;
    }

    /** 把捕获到的若干 update(null, wrapper) 的 SET 片段拼在一起，便于断言某列被显式写入 */
    public static String sqlSets(List<? extends Wrapper<?>> wrappers) {
        StringBuilder sb = new StringBuilder();
        for (Wrapper<?> wrapper : wrappers) {
            sb.append(wrapper.getSqlSet()).append(';');
        }
        return sb.toString();
    }
}
