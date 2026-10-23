package com.zhimu.server.biz.feeding.service;

import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordItemMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.feeding.request.WeighCycleRequest;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.WeighVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** 称重周期修改口径（G23）与员工 40303。 */
@ExtendWith(MockitoExtension.class)
class WeighServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private WeighRecordMapper recordMapper;
    @Mock
    private WeighRecordItemMapper itemMapper;
    @Mock
    private BatchGrowthMapper growthMapper;
    @Mock
    private FeedLookup lookup;
    @Mock
    private StdQueryService stdQueryService;

    private WeighService service;

    @BeforeEach
    void setUp() {
        service = new WeighService(recordMapper, itemMapper, growthMapper, lookup, stdQueryService);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZONE));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
        BeijingTime.setClock(null);
    }

    @Test
    void updateCycleRecalculatesIntervalAndNextDate() {
        when(lookup.requireActiveBatch(3001L)).thenReturn(batch());
        when(growthMapper.selectOne(any())).thenReturn(growth());
        when(growthMapper.updateById(any(BatchGrowth.class))).thenReturn(1);
        when(stdQueryService.standards(1101L, null)).thenReturn(null);
        WeighCycleRequest request = new WeighCycleRequest();
        request.setPerMonth(3);

        WeighVO.Cycle cycle = service.updateCycle(3001L, request);

        assertThat(cycle.getPerMonth()).isEqualTo(3);
        assertThat(cycle.getIntervalDays()).isEqualTo(9);
        assertThat(cycle.getCustomized()).isTrue();
        assertThat(cycle.getSuggestedPerMonth()).isEqualTo(2);
        assertThat(cycle.getNextWeighDate()).isEqualTo("2026-10-28");
    }

    @Test
    void staffChangingCycleGets40303() {
        doThrow(FeedErrors.roleForbidden("你没有修改称重周期的权限")).when(lookup).requireManager(anyString());
        WeighCycleRequest request = new WeighCycleRequest();
        request.setPerMonth(3);

        assertThatThrownBy(() -> service.updateCycle(3001L, request))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(40303);
    }

    private static BatchInfo batch() {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 101L,
                LocalDate.of(2026, 10, 1), 200, "ACTIVE", List.of(2003L), 78, 205, 5, 0, 2003L);
    }

    private static BatchGrowth growth() {
        BatchGrowth growth = new BatchGrowth();
        growth.setId(601L);
        growth.setFarmId(1001L);
        growth.setBatchId(3001L);
        growth.setWeighCyclePerMonth(2);
        growth.setCycleCustomized(false);
        growth.setLastWeighDate(LocalDate.of(2026, 10, 19));
        growth.setLastAvgWeight(new java.math.BigDecimal("60.00"));
        growth.setGrowthStatus("SLOW");
        growth.setNextWeighDate(LocalDate.of(2026, 11, 2));
        growth.setVersion(0);
        return growth;
    }
}
