package com.zhimu.server.biz.herd.support;

import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 42205/42206 结构化 data：App 需要 pen_id / stock_count 展示“在栏数已变为 N 头”。 */
class HerdErrorsTest {

    @Test
    void countExceededCarriesPenId() {
        BizException error = HerdErrors.countExceeded(2003L, "3 号育肥舍", 195, 200);

        assertThat(error.getCode()).isEqualTo(42205);
        assertThat(error.getMessage()).isEqualTo("3 号育肥舍的在栏数已变为 195 头，不能超过 195 头");
        assertThat(data(error)).containsEntry("pen_id", 2003L)
                .containsEntry("pen_name", "3 号育肥舍")
                .containsEntry("stock_count", 195);
    }

    @Test
    void capacityConfirmCarriesNeedConfirmAndBreakdown() {
        BizException error = HerdErrors.capacityConfirm(2004L, "4 号育肥舍", "入栏", 220, 180, 240, 20);

        assertThat(error.getCode()).isEqualTo(42206);
        assertThat(error.getMessage()).isEqualTo("入栏后超过栏舍容量。4 号育肥舍容量 220 头，现有 180 头，入栏后共 240 头，超出 20 头。");
        assertThat(data(error)).containsEntry("need_confirm", true)
                .containsEntry("pen_id", 2004L)
                .containsEntry("capacity", 220)
                .containsEntry("current", 180)
                .containsEntry("after", 240)
                .containsEntry("over", 20);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(BizException error) {
        return (Map<String, Object>) error.getData();
    }
}
