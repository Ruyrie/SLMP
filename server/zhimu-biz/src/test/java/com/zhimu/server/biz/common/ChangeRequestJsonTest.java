package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.vo.OptionVO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * before_json / after_json 统一 schema：{record_card, fields, compare_keys, before, after}；
 * recalc_tags 用“、”分隔存取；DATE 不下发 min/max；未知 input_type 按 TEXT。
 */
class ChangeRequestJsonTest {

    private static final ChangeRecord RECORD = new ChangeRecord(BizType.FEED_RECORD, 7001L, 1001L, 3001L, 2003L,
            "3 号育肥舍 · 10月18日 晚餐", "投喂记录 · 3 号育肥舍", "ZM2610-031", "3", false, false);

    private static final ChangeForm FORM = new ChangeForm(
            List.of(new ChangeCardItem("饲料", "育肥全价料")),
            List.of(new ChangeField("actual_qty", "实际投喂量", "140", "千克", ChangeField.NUMBER, List.of(),
                    BigDecimal.ZERO, new BigDecimal("1000"), List.of("头均采食量"), "计划 160 千克")),
            List.of("修改实际投喂量会同步调整饲料库存出库数量"),
            "需要审核",
            false, true, "作废说明", List.of("库存（退回饲料）"));

    @Test
    void beforeJsonHasAllSchemaKeysAndEmptyAfter() {
        Map<String, ChangeValidation.FieldValue> values = ChangeRequestJson.fieldValues(FORM);

        JsonNode node = TestMvc.json(ChangeRequestJson.beforeJson(RECORD, FORM, values));

        assertThat(node.has("record_card")).isTrue();
        assertThat(node.has("fields")).isTrue();
        assertThat(node.has("compare_keys")).isTrue();
        assertThat(node.has("before")).isTrue();
        assertThat(node.has("after")).isTrue();
        assertThat(node.path("record_card").path(0).path("label").asString()).isEqualTo("饲料");
        assertThat(node.path("fields").path(0).path("field_code").asString()).isEqualTo("actual_qty");
        assertThat(node.path("fields").path(0).path("input_type").asString()).isEqualTo("NUMBER");
        assertThat(node.path("compare_keys").path(0).asString()).isEqualTo("actual_qty");
        assertThat(node.path("before").path("actual_qty").path("value").asString()).isEqualTo("140");
        assertThat(node.path("before").path("actual_qty").path("display").asString()).isEqualTo("140 千克");
        assertThat(node.path("after").isEmpty()).isTrue();
    }

    @Test
    void afterJsonCarriesMainFieldAndAfterValue() {
        Map<String, ChangeValidation.FieldValue> values = ChangeRequestJson.fieldValues(FORM);
        ChangeValidation validation = ChangeValidation.of("actual_qty", "实际投喂量", "千克", "140", "140 千克",
                "160", "160 千克", List.of(ChangeImpact.INVENTORY, ChangeImpact.COST), List.of("头均采食量"));

        String json = ChangeRequestJson.afterJson(RECORD, FORM, values, validation);

        assertThat(ChangeRequestJson.afterValue(json, "actual_qty")).isEqualTo("160");
        assertThat(ChangeRequestJson.afterValue(json, "feed_id")).isNull();
        JsonNode node = TestMvc.json(json);
        assertThat(node.path("after").path("actual_qty").path("display").asString()).isEqualTo("160 千克");
        assertThat(node.path("before").path("actual_qty").path("value").asString()).isEqualTo("140");
    }

    @Test
    void joinAndSplitRecalcTags() {
        assertThat(ChangeRequestJson.joinTags(List.of("头均采食量", "料肉比", "饲料成本", "经营报表")))
                .isEqualTo("头均采食量、料肉比、饲料成本、经营报表");
        assertThat(ChangeRequestJson.splitTags("头均采食量、料肉比、饲料成本、经营报表"))
                .containsExactly("头均采食量", "料肉比", "饲料成本", "经营报表");
        assertThat(ChangeRequestJson.joinTags(List.of())).isNull();
        assertThat(ChangeRequestJson.splitTags(null)).isEmpty();
    }

    @Test
    void dateFieldHasNoMinMaxAndUnknownTypeFallsBackToText() {
        ChangeField date = new ChangeField("record_date", "日期", "2026-10-18", null, "date", List.of(),
                BigDecimal.ONE, new BigDecimal("9"), List.of(), null);
        ChangeField unknown = new ChangeField("note", "说明", "正常", null, "something", List.of(), null, null,
                List.of(), null);
        assertThat(date.inputType()).isEqualTo(ChangeField.DATE);
        assertThat(date.min()).isNull();
        assertThat(date.max()).isNull();
        assertThat(unknown.inputType()).isEqualTo(ChangeField.TEXT);
    }

    @Test
    void selectFieldHintListsOptions() {
        ChangeField select = new ChangeField("feed_id", "饲料", "育肥全价料", null, ChangeField.SELECT,
                List.of(OptionVO.of("4001", "育肥全价料"), OptionVO.of("4002", "保育料")), null, null,
                List.of(), null);
        Map<String, ChangeValidation.FieldValue> values = new LinkedHashMap<>();
        values.put("feed_id", new ChangeValidation.FieldValue("饲料", "育肥全价料", "育肥全价料", null));
        JsonNode node = TestMvc.json(ChangeRequestJson.beforeJson(RECORD,
                new ChangeForm(List.of(), List.of(select), List.of(), null, false, false, null, List.of()), values));
        assertThat(node.path("fields").path(0).path("options").size()).isEqualTo(2);
    }

    @Test
    void impactJsonContainsRecalcTagsAndValidation() {
        String json = ChangeRequestJson.impactJson(List.of(ChangeImpact.COST),
                List.of("饲料成本"), new com.zhimu.server.biz.common.change.ChangeApplyResult(4, "已重新计算批次收益"),
                "PASS", null);
        JsonNode node = TestMvc.json(json);
        assertThat(node.path("impacts").path(0).asString()).isEqualTo("COST");
        assertThat(node.path("recalc_tags").path(0).asString()).isEqualTo("饲料成本");
        assertThat(node.path("recalc_count").asInt()).isEqualTo(4);
        assertThat(node.path("validation").path("level").asString()).isEqualTo("PASS");
    }
}
