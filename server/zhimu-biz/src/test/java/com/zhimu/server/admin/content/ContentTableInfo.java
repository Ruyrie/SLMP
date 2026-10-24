package com.zhimu.server.admin.content;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.content.entity.MarketPriceCorrection;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.entity.NoticeRecipient;
import com.zhimu.server.admin.content.entity.NoticeTarget;
import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.entity.MarketPriceConfig;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/** 纯单元测试初始化 Lambda Wrapper 需要的表信息缓存（幂等）。 */
public final class ContentTableInfo {

    private static boolean initialized;

    private ContentTableInfo() {
    }

    public static synchronized void ensure() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(Notice.class, NoticeTarget.class, NoticeRecipient.class,
                MarketPrice.class, MarketPriceConfig.class, MarketPriceCorrection.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
