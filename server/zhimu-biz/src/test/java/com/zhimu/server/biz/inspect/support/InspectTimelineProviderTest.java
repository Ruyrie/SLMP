package com.zhimu.server.biz.inspect.support;

import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.inspect.InspectFixtures;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.stock.StockTestMybatis;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 模块07 批次时间线扩展（INSPECT 记录）与 App mock timeline_3001.json 对齐：
 * record_type、biz_type、title、subtitle、route、occurred_at、is_today。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectTimelineProviderTest {

    @Mock private InspectionMapper inspectionMapper;
    @Mock private InspectionItemResultMapper itemResultMapper;
    @Mock private InspectSupport support;

    private InspectTimelineProvider provider;

    @BeforeEach
    void setUp() {
        StockTestMybatis.init(Inspection.class, InspectionItemResult.class);
        BeijingTime.setClock(InspectFixtures.CLOCK);
        provider = new InspectTimelineProvider(inspectionMapper, itemResultMapper, support);
        lenient().when(support.userName(10003L)).thenReturn("李明");
        lenient().when(support.userName(10004L)).thenReturn("张伟");
        lenient().when(itemResultMapper.selectByInspectionId(anyLong())).thenReturn(List.of(
                item(101L, "精神状态", "正常", 1011L, false, 1),
                item(102L, "采食", "正常", 1021L, false, 2),
                item(103L, "粪便", "水样", 1033L, true, 3)));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void normalInspectionBecomesInspectTimelineItem() {
        Inspection inspection = InspectFixtures.inspection(50124L, 2001L, 3003L,
                LocalDate.of(2026, 10, 19), "DONE", false, 10004L, null);
        when(inspectionMapper.selectList(any())).thenReturn(List.of(inspection));

        List<TimelineItemVO> items = provider.extraRecords(1001L, 3003L);

        assertThat(items).hasSize(1);
        TimelineItemVO item = items.get(0);
        assertThat(item.getRecordType()).isEqualTo("INSPECT");
        assertThat(item.getBizType()).isEqualTo("INSPECTION");
        assertThat(item.getBizId()).isEqualTo(50124L);
        assertThat(item.getBizDate()).isEqualTo("2026-10-19");
        assertThat(item.getOccurredAt()).isEqualTo("2026-10-19 09:12:00");
        assertThat(item.getTitle()).isEqualTo("巡检 · 3 项正常");
        assertThat(item.getSubtitle()).isEqualTo("精神状态、采食、粪便 · 张伟 · 现场");
        assertThat(item.getOperatorName()).isEqualTo("张伟");
        assertThat(item.getRoute()).isEqualTo("I04");
        assertThat(item.getIsToday()).isTrue();
        assertThat(item.getExpandable()).isFalse();
        assertThat(item.getAction()).isEqualTo("NONE");
    }

    @Test
    void abnormalInspectionKeepsAbnormalSummaryInTitle() {
        Inspection inspection = InspectFixtures.inspection(50121L, 2003L, 3001L,
                LocalDate.of(2026, 10, 19), "DONE", true, 10003L, 6001L);
        when(inspectionMapper.selectList(any())).thenReturn(List.of(inspection));
        when(support.userName(10003L)).thenReturn("李明");

        TimelineItemVO item = provider.extraRecords(1001L, 3001L).get(0);

        assertThat(item.getTitle()).isEqualTo("巡检 · 异常 · 粪便 水样");
        assertThat(item.getSubtitle()).startsWith("精神状态、采食、粪便").contains("李明").contains("现场");
        assertThat(item.getIsToday()).isTrue();
    }

    @Test
    void noBatchOrNoRecordReturnsEmpty() {
        assertThat(provider.extraRecords(null, 3001L)).isEmpty();
        assertThat(provider.extraRecords(1001L, null)).isEmpty();
        when(inspectionMapper.selectList(any())).thenReturn(List.of());
        assertThat(provider.extraRecords(1001L, 3001L)).isEmpty();
    }

    private static InspectionItemResult item(long itemId, String itemName, String optionName,
                                             long optionId, boolean abnormal, int sortNo) {
        InspectionItemResult result = new InspectionItemResult();
        result.setItemId(itemId);
        result.setItemName(itemName);
        result.setOptionId(optionId);
        result.setOptionName(optionName);
        result.setOptionAbnormal(abnormal);
        result.setSortNo(sortNo);
        return result;
    }
}
