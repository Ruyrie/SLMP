package com.zhimu.server.biz.account.test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.account.entity.AccountCancel;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/** 单元测试中让 MyBatis-Plus 的 LambdaUpdateWrapper 能解析实体列（不启动 Spring）。 */
public final class MybatisTestSupport {

    private static boolean initialized;

    private MybatisTestSupport() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : new Class<?>[]{User.class, RecoveryCode.class, AccountRecovery.class,
                AccountCancel.class}) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
