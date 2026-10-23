package com.zhimu.server.biz.farm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmInvitePen;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.entity.MemberPen;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 纯单元测试里没有 MyBatis 上下文，LambdaQueryWrapper 需要先初始化表信息缓存。
 * 每个使用 Mapper 包装器的测试在 @BeforeAll 调用一次即可（幂等）。
 */
public final class FarmTableInfo {

    private static boolean initialized;

    private FarmTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(Farm.class, FarmAudit.class, FarmChange.class, FarmMember.class,
                FarmInvite.class, FarmInvitePen.class, MemberPen.class, FarmTransfer.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
