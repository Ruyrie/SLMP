package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.biz.herd.HerdTestMvc;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 模块04 响应 JSON 形状：snake_case、is_ 字段、员工裁剪（@ManagerView）。 */
class HerdVoJsonTest {

    @AfterEach
    void tearDown() {
        FarmContext.clear();
    }

    @Test
    void batchDetailUsesSnakeCaseAndHidesManagerFieldsForStaff() throws Exception {
        BatchDetailVO vo = new BatchDetailVO();
        vo.setBatchId(3001L);
        vo.setCurrentCount(200);
        vo.setDayAge(96);
        vo.setVersion(7);
        vo.setIntroUnitPrice(new BigDecimal("500.00"));
        vo.setPendingChangeRequestCount(1);
        BatchEventDetailVO.EntryInfo entry = new BatchEventDetailVO.EntryInfo();
        entry.setIntroUnitPrice(new BigDecimal("500.00"));
        vo.setEntryEventId(901L);

        FarmContext.set(member(FarmRole.OWNER));
        String ownerJson = HerdTestMvc.MAPPER.writeValueAsString(vo);
        assertThat(ownerJson).contains("\"batch_id\":3001", "\"current_count\":200", "\"day_age\":96",
                "\"version\":7", "\"intro_unit_price\":500.00", "\"pending_change_request_count\":1");

        FarmContext.set(member(FarmRole.STAFF));
        String staffJson = HerdTestMvc.MAPPER.writeValueAsString(vo);
        assertThat(staffJson).contains("\"batch_id\":3001")
                .doesNotContain("intro_unit_price", "pending_change_request_count");
    }

    @Test
    void booleanIsFieldsKeepIsPrefix() throws Exception {
        MyPenItemVO pen = new MyPenItemVO();
        pen.setPenId(2003L);
        pen.setIsEmpty(false);
        String penJson = HerdTestMvc.MAPPER.writeValueAsString(pen);
        assertThat(penJson).contains("\"is_empty\":false");

        PenStockItemVO stock = new PenStockItemVO();
        stock.setIsBreedingPen(true);
        stock.setInWithdrawal(false);
        String stockJson = HerdTestMvc.MAPPER.writeValueAsString(stock);
        assertThat(stockJson).contains("\"is_breeding_pen\":true", "\"in_withdrawal\":false");

        TimelineItemVO timeline = new TimelineItemVO();
        timeline.setId(8102L);
        timeline.setIsToday(true);
        String timelineJson = HerdTestMvc.MAPPER.writeValueAsString(timeline);
        assertThat(timelineJson).contains("\"id\":8102", "\"is_today\":true");
    }

    private static FarmContext.FarmMember member(FarmRole role) {
        return new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, role,
                role == FarmRole.STAFF ? Set.of(2003L) : Set.of(), FarmStatus.NORMAL);
    }
}
