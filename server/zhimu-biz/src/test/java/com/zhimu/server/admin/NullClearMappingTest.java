package com.zhimu.server.admin;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.admin.base.AdminBaseTableInfo;
import com.zhimu.server.admin.base.entity.ImmuneTemplateItem;
import com.zhimu.server.admin.base.entity.InspectItem;
import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.entity.KnowledgeVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认更新策略会跳过 null：这些可选字段在编辑时允许清空，updateById 必须始终写入，
 * 否则“清空检查说明 / 切换免疫计划方式 / 清空关键词”保存后旧值还在。
 */
class NullClearMappingTest {

    @BeforeAll
    static void init() {
        AdminBaseTableInfo.ensure();
        ContentTableInfo.ensure();
    }

    @Test
    void inspectItemOptionalFieldsAlwaysUpdated() {
        assertAlways(InspectItem.class, "checkNote", "disableReason");
    }

    @Test
    void immuneItemScheduleFieldsAlwaysUpdated() {
        assertAlways(ImmuneTemplateItem.class, "dayAge", "planMonth", "planDay", "advanceDays", "doseUnit",
                "vaccinateMethodCode", "remark");
    }

    @Test
    void knowledgeVersionKeywordsAlwaysUpdated() {
        assertAlways(KnowledgeVersion.class, "applicableCategoryIds", "keywords");
    }

    private static void assertAlways(Class<?> type, String... properties) {
        for (String property : properties) {
            TableFieldInfo field = TableInfoHelper.getTableInfo(type).getFieldList().stream()
                    .filter(f -> f.getProperty().equals(property)).findFirst().orElseThrow();
            assertThat(field.getUpdateStrategy()).as(type.getSimpleName() + "." + property)
                    .isEqualTo(FieldStrategy.ALWAYS);
        }
    }
}
