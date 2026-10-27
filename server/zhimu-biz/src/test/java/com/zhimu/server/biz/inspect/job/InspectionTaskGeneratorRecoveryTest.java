package com.zhimu.server.biz.inspect.job;

import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.job.mapper.InspectionJobMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InspectionTaskGeneratorRecoveryTest {
    private final LocalDate today = LocalDate.of(2026, 10, 25);
    private final InspectionMapper records = mock(InspectionMapper.class);
    private final InspectionJobMapper queries = mock(InspectionJobMapper.class);
    private final InspectionTaskGenerator generator = new InspectionTaskGenerator(records, queries);
    private final List<Inspection> stored = new ArrayList<>();

    @BeforeEach
    void setup() {
        when(queries.selectStockedPens(1001L)).thenReturn(List.of(pen(2001L, today.minusDays(10)),
                pen(2002L, today.minusDays(10))));
        when(records.selectDay(eq(1001L), any())).thenAnswer(call -> {
            LocalDate date = call.getArgument(1);
            return stored.stream().filter(row -> date.equals(row.getInspectDate())).toList();
        });
        when(queries.selectLastInspectDate(eq(1001L), any())).thenAnswer(call -> {
            LocalDate before = call.getArgument(1);
            return stored.stream().map(Inspection::getInspectDate).filter(date -> date.isBefore(before))
                    .max(LocalDate::compareTo).orElse(null);
        });
        doAnswer(call -> {
            stored.add(call.getArgument(0));
            return 1;
        }).when(records).insert(any(Inspection.class));
    }

    @Test
    void 部分生成后数据库短暂失败下轮只补缺少的栏舍() {
        doAnswer(call -> {
            Inspection row = call.getArgument(0);
            if (row.getPenId().equals(2002L)) throw new DataAccessResourceFailureException("offline");
            stored.add(row);
            return 1;
        }).when(records).insert(any(Inspection.class));
        assertThatThrownBy(() -> generator.backfill(1001L, today)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(stored).hasSize(1);

        doAnswer(call -> {
            stored.add(call.getArgument(0));
            return 1;
        }).when(records).insert(any(Inspection.class));
        assertThat(generator.backfill(1001L, today)).isEqualTo(1);
        assertThat(generator.backfill(1001L, today)).isZero();
        assertThat(stored).extracting(Inspection::getPenId).containsExactly(2001L, 2002L);
    }

    @Test
    void 已登记和已取消的任务不会因补偿被覆盖或重新生成() {
        Inspection done = InspectionTaskGenerator.placeholder(1001L, 2001L, 3001L, today, "DONE");
        done.setRegistrantId(10001L);
        Inspection canceled = InspectionTaskGenerator.placeholder(1001L, 2002L, 3001L, today, "CANCELLED");
        stored.addAll(List.of(done, canceled));

        assertThat(generator.backfill(1001L, today)).isZero();

        assertThat(done.getInspectStatus()).isEqualTo("DONE");
        assertThat(done.getRegistrantId()).isEqualTo(10001L);
        assertThat(canceled.getInspectStatus()).isEqualTo("CANCELLED");
        verify(records, never()).insert(any(Inspection.class));
    }

    @Test
    void 跨日新增当天任务而历史记录保留原日期() {
        generator.backfill(1001L, today);
        stored.get(0).setInspectStatus("DONE");
        stored.get(0).setRegistrantId(10001L);

        assertThat(generator.backfill(1001L, today.plusDays(1))).isEqualTo(2);
        assertThat(generator.backfill(1001L, today.plusDays(1))).isZero();

        assertThat(stored).hasSize(4);
        assertThat(stored.subList(0, 2)).extracting(Inspection::getInspectDate).containsOnly(today);
        assertThat(stored.subList(2, 4)).extracting(Inspection::getInspectDate).containsOnly(today.plusDays(1));
        assertThat(stored.get(0).getInspectStatus()).isEqualTo("DONE");
        assertThat(stored.get(0).getRegistrantId()).isEqualTo(10001L);
    }

    private StockedPenRow pen(Long id, LocalDate entryDate) {
        StockedPenRow row = new StockedPenRow();
        row.setPenId(id);
        row.setBatchId(3001L);
        row.setEntryDate(entryDate);
        row.setStockCount(100);
        return row;
    }
}
