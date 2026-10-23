package com.zhimu.server.biz.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.finance.entity.BatchSettlement;
import com.zhimu.server.biz.finance.entity.BatchSettlementSupplement;
import com.zhimu.server.biz.finance.entity.ExpenseSubcategory;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 单元测试中让 MyBatis-Plus 的 Lambda 条件能解析模块10 实体列（不启动 Spring）。
 * 只有 in() 会在构造条件时立即解析列缓存，eq/ne 等走惰性解析，所以需要显式注册实体表信息。
 */
public final class FinanceTestTables {

    private static boolean initialized;

    private FinanceTestTables() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : new Class<?>[]{BatchSettlement.class, BatchSettlementSupplement.class,
                FinanceRecord.class, FinanceCategory.class, ExpenseSubcategory.class, SaleOrder.class}) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
        initialized = true;
    }
}
