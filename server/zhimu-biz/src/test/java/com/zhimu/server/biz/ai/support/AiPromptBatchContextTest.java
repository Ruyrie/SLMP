package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptBatchContextTest {
    @Test
    void includesRealBatchAndDatedWeightInModelInput() {
        LinkedBatchVO batch = batch();
        batch.setLatestAvgWeight(new BigDecimal("60.0"));
        batch.setLatestWeighDate(LocalDate.of(2026, 10, 26));
        assertThat(prompt(batch)).contains("ZM2610-075", "存栏 119头", "日龄 95 天",
                "60.0 千克", "2026-10-26", "不代表今天的实时体重");
    }

    @Test
    void doesNotInventWeightWhenNoMeasurementExists() {
        assertThat(prompt(batch())).contains("暂无可用的称重记录，不要推测体重").doesNotContain("平均体重：0");
    }

    private String prompt(LinkedBatchVO batch) {
        return new AiPromptBuilder().build("请核对批次数据", null, batch, null, List.of(), List.of(), List.of())
                .getMessages().getFirst().getText();
    }

    private LinkedBatchVO batch() {
        LinkedBatchVO batch = new LinkedBatchVO();
        batch.setBatchNo("ZM2610-075");batch.setCurrentCount(119);batch.setDayAge(95);batch.setCountUnitText("头");
        return batch;
    }
}
