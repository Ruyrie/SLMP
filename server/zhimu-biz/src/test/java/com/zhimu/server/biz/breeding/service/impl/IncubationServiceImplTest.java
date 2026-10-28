package com.zhimu.server.biz.breeding.service.impl;

import com.zhimu.server.biz.breeding.convert.IncubationConvert;
import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.breeding.request.HatchRequest;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.SelfBredBatchService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 出雏登记：选“转为新批次”时同一事务创建自繁批次并回填 batch_id / batch_no。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncubationServiceImplTest {

    private static final Long FARM_ID = 1002L;
    private static final Long INCUBATION_ID = 6201L;
    private static final Long TARGET_PEN_ID = 2202L;
    private static final Long BREED_ID = 1201L;

    @Mock private IncubationMapper incubationMapper;
    @Mock private BreedingSupport support;
    @Mock private IncubationConvert convert;
    @Mock private OperationLogRecorder operationLogRecorder;
    @Mock private SelfBredBatchService selfBredBatchService;

    private IncubationServiceImpl service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 28, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        FarmContext.set(new FarmContext.FarmMember(FARM_ID, "西岭鸡场", 10002L, 5102L, FarmRole.OWNER, Set.of(),
                FarmStatus.NORMAL));
        UserContext.set(new UserContext.LoginUser(10002L, ClientType.APP_ANDROID, 1, "jti"));
        service = new IncubationServiceImpl(incubationMapper, support, convert, operationLogRecorder,
                selfBredBatchService);
        when(support.farmId()).thenReturn(FARM_ID);
        when(support.pen(TARGET_PEN_ID)).thenReturn(new PenInfo(TARGET_PEN_ID, FARM_ID, "C02", "2 号鸡舍",
                "BROODING", "育雏舍", 10000, "ENABLED"));
        when(support.unitOfBreed(anyLong())).thenReturn("只");
        when(incubationMapper.selectById(INCUBATION_ID)).thenReturn(candled());
        when(incubationMapper.updateById(any(Incubation.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        FarmContext.clear();
        UserContext.clear();
    }

    @Test
    void hatchTransferCreatesSelfBredBatchInSameTransaction() {
        when(selfBredBatchService.create(anyLong(), anyLong(), anyInt(), any(), anyBoolean(), anyString(), anyLong()))
                .thenReturn(new SelfBredBatchService.CreatedBatch(3299L, "ZM2610-099"));

        RegisterResultVO vo = service.hatch(INCUBATION_ID, request(true), "hatch-req-1");

        verify(selfBredBatchService).create(TARGET_PEN_ID, BREED_ID, 7600, LocalDate.of(2026, 10, 27), false,
                "INCUBATION", INCUBATION_ID);
        ArgumentCaptor<Incubation> saved = ArgumentCaptor.forClass(Incubation.class);
        verify(incubationMapper).updateById(saved.capture());
        assertThat(saved.getValue().getBatchId()).isEqualTo(3299L);
        assertThat(saved.getValue().getStatus()).isEqualTo(BreedingCodes.INCUBATION_HATCHED);
        assertThat(saved.getValue().getHealthyCount()).isEqualTo(7600);
        assertThat(saved.getValue().getWeakCount()).isEqualTo(200);
        assertThat(vo.getBatchId()).isEqualTo(3299L);
        assertThat(vo.getBatchNo()).isEqualTo("ZM2610-099");
    }

    @Test
    void hatchWithoutTransferKeepsBatchEmpty() {
        RegisterResultVO vo = service.hatch(INCUBATION_ID, request(false), "hatch-req-2");

        verify(selfBredBatchService, never()).create(anyLong(), anyLong(), anyInt(), any(), anyBoolean(),
                anyString(), anyLong());
        assertThat(vo.getBatchId()).isNull();
    }

    private HatchRequest request(boolean transfer) {
        HatchRequest request = new HatchRequest();
        request.setHatchDate("2026-10-27");
        request.setHatchedCount(7800);
        request.setHealthyCount(7600);
        request.setTransferToBatch(transfer);
        request.setTargetPenId(transfer ? TARGET_PEN_ID : null);
        request.setCapacityConfirmed(false);
        request.setVersion(2);
        return request;
    }

    private Incubation candled() {
        Incubation incubation = new Incubation();
        incubation.setId(INCUBATION_ID);
        incubation.setFarmId(FARM_ID);
        incubation.setBreedingBatchId(3201L);
        incubation.setPenId(2201L);
        incubation.setBreedId(BREED_ID);
        incubation.setIncubationDays(21);
        incubation.setSetDate(LocalDate.of(2026, 10, 6));
        incubation.setSetCount(9000);
        incubation.setCandleDate(LocalDate.of(2026, 10, 13));
        incubation.setFertileCount(8200);
        incubation.setExpectedHatchDate(LocalDate.of(2026, 10, 27));
        incubation.setStatus(BreedingCodes.INCUBATION_CANDLED);
        incubation.setVersion(2);
        return incubation;
    }
}
