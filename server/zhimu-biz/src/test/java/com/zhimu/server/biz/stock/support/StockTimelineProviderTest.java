package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.stock.StockFixtures;
import com.zhimu.server.biz.stock.StockTestMybatis;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 模块09 批次时间线扩展（DRUG 记录）与 App mock timeline_3001_drug.json 对齐：
 * record_type = DRUG、biz_type 为来源记录类型、title“用药 · 药品名”、route D05（接种 D01 / 消毒 D08）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockTimelineProviderTest {

    @Mock private StockFlowMapper flowMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private StockLookups lookups;

    private StockTimelineProvider provider;

    @BeforeEach
    void setUp() {
        StockTestMybatis.init(StockFlow.class);
        BeijingTime.setClock(StockFixtures.CLOCK);
        provider = new StockTimelineProvider(flowMapper, drugMapper, drugLotMapper, lookups);
        Drug drug = StockFixtures.drug(5202L, "氟苯尼考粉", "VET_DRUG", 7202L, "G", "袋", "100.000");
        lenient().when(drugMapper.selectById(5202L)).thenReturn(drug);
        DrugLot lot = StockFixtures.lot(6111L, 5202L, "2025062", "2027-06-30", "1800.000", "252.00",
                "0.1400", 5);
        lenient().when(drugLotMapper.selectById(6111L)).thenReturn(lot);
        lenient().when(lookups.userName(10002L)).thenReturn("刘芳");
        lenient().when(lookups.userName(10003L)).thenReturn("李明");
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void medicationFlowBecomesDrugTimelineItemLikeMock() {
        StockFlow flow = flow(8102L, "MEDICATION_RECORD", 7102L, "MEDICATION_USE", "200.000", "2026-10-09");
        flow.setOperatorId(10002L);
        flow.setRelatedDesc(null);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));

        List<TimelineItemVO> items = provider.extraRecords(1001L, 3001L);

        assertThat(items).hasSize(1);
        TimelineItemVO item = items.get(0);
        assertThat(item.getRecordType()).isEqualTo("DRUG");
        assertThat(item.getBizType()).isEqualTo("MEDICATION_RECORD");
        assertThat(item.getBizId()).isEqualTo(7102L);
        assertThat(item.getBizDate()).isEqualTo("2026-10-09");
        assertThat(item.getOccurredAt()).isEqualTo("2026-10-09 15:00:00");
        assertThat(item.getTitle()).isEqualTo("用药 · 氟苯尼考粉");
        assertThat(item.getSubtitle()).isEqualTo("批号 2025062 · 200 克 · 刘芳");
        assertThat(item.getOperatorName()).isEqualTo("刘芳");
        assertThat(item.getRoute()).isEqualTo("D05");
        assertThat(item.getIsToday()).isFalse();
        assertThat(item.getExpandable()).isFalse();
        assertThat(item.getAction()).isEqualTo("NONE");
    }

    @Test
    void vaccineAndDisinfectUseDifferentRoutes() {
        StockFlow vaccine = flow(9501L, "IMMUNE_RECORD", 8812L, "VACCINE_USE", "100.000", "2026-10-12");
        vaccine.setDrugId(5202L);
        vaccine.setDrugLotId(6111L);
        StockFlow disinfect = flow(9505L, "DISINFECT_RECORD", 8701L, "DISINFECT_USE", "10.000", "2026-10-17");
        disinfect.setDrugId(5202L);
        disinfect.setDrugLotId(6111L);
        when(flowMapper.selectList(any())).thenReturn(List.of(vaccine, disinfect));

        List<TimelineItemVO> items = provider.extraRecords(1001L, 3001L);

        assertThat(items.get(0).getRoute()).isEqualTo("D01");
        assertThat(items.get(0).getTitle()).isEqualTo("接种 · 氟苯尼考粉");
        assertThat(items.get(1).getRoute()).isEqualTo("D08");
        assertThat(items.get(1).getTitle()).isEqualTo("消毒 · 氟苯尼考粉");
    }

    @Test
    void noFlowOrNoBatchReturnsEmpty() {
        assertThat(provider.extraRecords(null, 3001L)).isEmpty();
        assertThat(provider.extraRecords(1001L, null)).isEmpty();
        when(flowMapper.selectList(any())).thenReturn(List.of());
        assertThat(provider.extraRecords(1001L, 3001L)).isEmpty();
    }

    private static StockFlow flow(long id, String bizType, long bizId, String flowType, String qty,
                                  String bizDate) {
        StockFlow flow = StockFixtures.feedFlow(id, 0L, flowType, "OUT", qty, "0.1400", "28.00", bizType, bizId);
        flow.setMaterialType("DRUG");
        flow.setFeedId(null);
        flow.setDrugId(5202L);
        flow.setDrugLotId(6111L);
        flow.setBizDate(java.time.LocalDate.parse(bizDate));
        flow.setOccurAt(java.time.LocalDate.parse(bizDate).atTime(15, 0));
        flow.setOperatorId(10003L);
        return flow;
    }
}
