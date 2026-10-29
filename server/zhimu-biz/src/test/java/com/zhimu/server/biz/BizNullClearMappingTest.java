package com.zhimu.server.biz;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.finance.entity.BizReportSummary;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.Inspection;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认更新策略会跳过 null：这些字段在业务里会被“置空”（妊检阴性解除当前周期、撤销离场、取消异常标记等），
 * 实体都是先读后改再 updateById，必须始终写入，否则旧值留在库里。
 */
class BizNullClearMappingTest {

    @Test
    void breedingStateFieldsAlwaysUpdated() {
        assertAlways(BreedingAnimal.class, "currentCycleId", "lastCycleEndDate", "exitType", "exitDate",
                "exitReason", "statusBeforeExit", "exitBy", "exitAt");
        assertAlways(BreedingCycle.class, "endDate", "endReason");
    }

    @Test
    void inspectionOptionalFieldsAlwaysUpdated() {
        assertAlways(Inspection.class, "abnormalSource", "abnormalSummary", "manualReason", "manualMarkedBy",
                "manualMarkedAt", "remark");
        assertAlways(AbnormalReport.class, "unavailableSince");
    }

    @Test
    void reportSummaryErrorAlwaysUpdated() {
        assertAlways(BizReportSummary.class, "errorMessage");
    }

    private static void assertAlways(Class<?> type, String... properties) {
        TableInfo info = TableInfoHelper.getTableInfo(type);
        if (info == null) {
            info = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
        for (String property : properties) {
            TableFieldInfo field = info.getFieldList().stream()
                    .filter(f -> f.getProperty().equals(property)).findFirst().orElseThrow();
            assertThat(field.getUpdateStrategy()).as(type.getSimpleName() + "." + property)
                    .isEqualTo(FieldStrategy.ALWAYS);
        }
    }
}
