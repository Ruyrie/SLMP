package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** API-L16：批次时间线的投喂 / 称重记录与 App Mock timeline_3001 对齐。 */
@ExtendWith(MockitoExtension.class)
class FeedingTimelineProviderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private FeedRecordMapper recordMapper;
    @Mock
    private WeighRecordMapper weighRecordMapper;
    @Mock
    private FeedLookup lookup;

    private FeedingTimelineProvider provider;

    @BeforeEach
    void setUp() {
        com.zhimu.server.biz.feeding.FeedingTableInfo.ensure();
        provider = new FeedingTimelineProvider(recordMapper, weighRecordMapper, lookup);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void feedRecordBecomesExpandableTimelineItem() {
        FeedRecord record = new FeedRecord();
        record.setId(7106L);
        record.setBatchId(3001L);
        record.setFeedDate(LocalDate.of(2026, 10, 19));
        record.setMealName("早餐");
        record.setFeedId(5101L);
        record.setPlanQty(new BigDecimal("160.00"));
        record.setActualQty(new BigDecimal("158.00"));
        record.setCreatedBy(10003L);
        record.setExecutorId(10003L);
        record.setCreatedAt(LocalDateTime.of(2026, 10, 19, 7, 32));
        record.setStatus("NORMAL");
        record.setRemark("采食正常");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        when(weighRecordMapper.selectList(any())).thenReturn(List.of());
        when(lookup.feed(5101L)).thenReturn(new FeedStockPort.FeedBrief(5101L, "育肥全价料", "KG",
                new BigDecimal("500.000"), 1, true, true, null));
        when(lookup.memberBriefs(any())).thenReturn(Map.of(
                10003L, brief(10003L, "李明", "STAFF", "员工"),
                10001L, brief(10001L, "王建国", "OWNER", "所有者")));

        List<TimelineItemVO> items = provider.extraRecords(1001L, 3001L);

        assertThat(items).hasSize(1);
        TimelineItemVO item = items.get(0);
        assertThat(item.getRecordType()).isEqualTo("FEED");
        assertThat(item.getBizType()).isEqualTo("FEED_RECORD");
        assertThat(item.getBizId()).isEqualTo(7106L);
        assertThat(item.getBizDate()).isEqualTo("2026-10-19");
        assertThat(item.getOccurredAt()).isEqualTo("2026-10-19 07:32:00");
        assertThat(item.getTitle()).isEqualTo("投喂 · 早餐 158 千克");
        assertThat(item.getSubtitle()).isEqualTo("育肥全价料 · 计划 160 千克 · 李明");
        assertThat(item.getOperatorName()).isEqualTo("李明");
        assertThat(item.getIsToday()).isTrue();
        assertThat(item.getExpandable()).isTrue();
        assertThat(item.getAction()).isEqualTo("NONE");
        assertThat(item.getRoute()).isNull();
        assertThat(item.getDetail()).extracting(TimelineItemVO.KeyValue::getKey)
                .containsExactly("登记人", "登记时间", "备注");
        assertThat(item.getDetail().get(1).getValue()).isEqualTo("今天 07:32");
        assertThat(item.getDetail().get(2).getValue()).isEqualTo("采食正常");
    }

    @Test
    void weighRecordRoutesToGrowthCurve() {
        WeighRecord record = new WeighRecord();
        record.setId(3301L);
        record.setBatchId(3001L);
        record.setWeighDate(LocalDate.of(2026, 10, 16));
        record.setMode("SAMPLE");
        record.setSampleCount(20);
        record.setAvgWeight(new BigDecimal("60.00"));
        record.setCreatedBy(10003L);
        record.setCreatedAt(LocalDateTime.of(2026, 10, 16, 9, 0));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(weighRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(lookup.memberBriefs(any())).thenReturn(Map.of(10003L, brief(10003L, "李明", "STAFF", "员工")));

        List<TimelineItemVO> items = provider.extraRecords(1001L, 3001L);

        assertThat(items).hasSize(1);
        TimelineItemVO item = items.get(0);
        assertThat(item.getRecordType()).isEqualTo("WEIGH");
        assertThat(item.getBizType()).isEqualTo("WEIGH_RECORD");
        assertThat(item.getTitle()).isEqualTo("称重 · 平均 60.0 千克");
        assertThat(item.getSubtitle()).isEqualTo("抽样 20 头 · 李明");
        assertThat(item.getRoute()).isEqualTo("G06");
        assertThat(item.getExpandable()).isFalse();
        assertThat(item.getIsToday()).isFalse();
    }

    @Test
    void emptyBatchReturnsNothing() {
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(weighRecordMapper.selectList(any())).thenReturn(List.of());

        assertThat(provider.extraRecords(1001L, 3009L)).isEmpty();
        assertThat(provider.extraRecords(1001L, null)).isEmpty();
    }

    private static UserBriefVO brief(Long userId, String name, String role, String roleText) {
        return UserBriefVO.builder().userId(userId).name(name).role(role).roleText(roleText).build();
    }
}
