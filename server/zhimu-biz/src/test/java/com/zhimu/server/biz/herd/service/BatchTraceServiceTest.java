package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchTraceMapper;
import com.zhimu.server.biz.herd.support.BatchAccess;
import com.zhimu.server.biz.herd.support.BatchQrLinks;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.vo.BatchTraceVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.mybatis.TenantSkip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BatchTraceServiceTest {
    private static final String TOKEN = "k7Rm2xQ9pT";
    private final BatchMapper batches = mock(BatchMapper.class);
    private final BatchTraceMapper traces = mock(BatchTraceMapper.class);
    private final HerdSupport support = mock(HerdSupport.class);
    private final FarmLookup farms = mock(FarmLookup.class);
    private final BatchAccess access = mock(BatchAccess.class);
    private final BatchTraceService service = new BatchTraceService(batches, traces, support, farms, access,
            new BatchQrLinks("http://127.0.0.1:5173"));
    private Batch batch;
    private Farm farm;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        FarmContext.clear();
        batch = new Batch();
        batch.setId(3001L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-031");
        batch.setQrToken(TOKEN);
        batch.setTraceEnabled(true);
        batch.setVersion(7);
        batch.setUpdatedAt(LocalDateTime.of(2026, 10, 25, 8, 0));
        farm = new Farm();
        farm.setName("公开养殖场");
        farm.setAuditStatus("APPROVED");
        when(batches.selectByQrTokenIncludingDeleted(TOKEN)).thenReturn(batch);
        when(farms.findFarm(1001L)).thenReturn(farm);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void anonymousReadUsesResolvedFarmAndBatchAndRestoresTenantScope() {
        BatchTraceVO.Event event = new BatchTraceVO.Event();
        event.setType("WEIGH");
        event.setUpdatedAt(LocalDateTime.of(2026, 10, 26, 8, 0));
        when(traces.events(1001L, 3001L)).thenAnswer(invocation -> {
            assertThat(TenantSkip.isSkipped()).isTrue();
            return List.of(event);
        });
        BatchTraceVO result = service.read(TOKEN);
        assertThat(UserContext.userId()).isNull();
        assertThat(FarmContext.get()).isNull();
        assertThat(result.getBatchNo()).isEqualTo("ZM2610-031");
        assertThat(result.getFarmName()).isEqualTo("公开养殖场");
        assertThat(result.getUpdatedAt()).isEqualTo(event.getUpdatedAt());
        assertThat(TenantSkip.isSkipped()).isFalse();
        verify(traces).events(1001L, 3001L);
    }

    @Test
    void invalidOrUnknownTokenDoesNotReadAnyEvents() {
        for (String invalid : List.of("short", "../../test", "k7Rm2xQ9p_", TOKEN + "?x=1")) {
            assertNotFound(invalid);
        }
        assertNotFound("A7Rm2xQ9pT");
        verifyNoInteractions(traces);
    }

    @Test
    void existingAlphanumericTokensWithZeroAndLowercaseLRemainReadable() {
        for (String token : List.of("Xl8cQ2fN4w", "BRD000015A")) {
            batch.setQrToken(token);
            when(batches.selectByQrTokenIncludingDeleted(token)).thenReturn(batch);
            assertThat(service.read(token).getBatchNo()).isEqualTo(batch.getBatchNo());
        }
        verify(traces, times(2)).events(1001L, 3001L);
    }

    @Test
    void deletedDisabledAndUnknownPublicationStateAreNotPublic() {
        batch.setDeleted(true);
        assertNotFound(TOKEN);
        batch.setDeleted(false);
        batch.setTraceEnabled(false);
        assertNotFound(TOKEN);
        batch.setTraceEnabled(null);
        assertNotFound(TOKEN);
        verifyNoInteractions(traces);
        assertThat(TenantSkip.isSkipped()).isFalse();
    }

    @Test
    void pendingRejectedDisabledFrozenAndDeletedFarmsAreNotPublic() {
        for (String status : List.of("PENDING", "REJECTED")) {
            farm.setAuditStatus(status);
            assertNotFound(TOKEN);
        }
        farm.setAuditStatus("APPROVED");
        farm.setDisabled(true);
        assertNotFound(TOKEN);
        farm.setDisabled(false);
        farm.setFrozen(true);
        assertNotFound(TOKEN);
        farm.setFrozen(false);
        farm.setDeleted(true);
        assertNotFound(TOKEN);
        verifyNoInteractions(traces);
    }

    @Test
    void rotatingTokenInvalidatesOldLinkAndReturnsNewLink() {
        when(access.require(3001L)).thenReturn(batch);
        when(batches.updateById(any(Batch.class))).thenAnswer(invocation -> {
            batch.setVersion(batch.getVersion() + 1);
            return 1;
        });
        when(batches.selectByQrTokenIncludingDeleted(anyString())).thenAnswer(invocation ->
                batch.getQrToken().equals(invocation.getArgument(0)) ? batch : null);
        BatchTraceService.Settings result = service.settings(3001L,
                new BatchTraceService.Settings(true, true, 7, null));
        assertThat(result.version()).isEqualTo(8);
        assertThat(result.qrContent()).startsWith("http://127.0.0.1:5173/s/b/");
        assertThat(batch.getQrToken()).isNotEqualTo(TOKEN);
        assertNotFound(TOKEN);
        assertThat(service.read(batch.getQrToken()).getBatchNo()).isEqualTo("ZM2610-031");
    }

    @Test
    void staleSettingsAndMissingBodyDoNotUpdatePublication() {
        when(access.require(3001L)).thenReturn(batch);
        assertThatThrownBy(() -> service.settings(3001L, new BatchTraceService.Settings(false, true, 6, null)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.settings(3001L, null)).isInstanceOf(BizException.class);
        verify(batches, never()).updateById(any(Batch.class));
        assertThat(batch.getTraceEnabled()).isTrue();
        assertThat(batch.getQrToken()).isEqualTo(TOKEN);
    }

    @Test
    void rejectsCaseFoldedTokenEvenIfStorageUsesCaseInsensitiveCollation() {
        when(batches.selectByQrTokenIncludingDeleted("K7Rm2xQ9pT")).thenReturn(batch);
        assertNotFound("K7Rm2xQ9pT");
        verifyNoInteractions(traces);
    }

    private void assertNotFound(String token) {
        assertThatThrownBy(() -> service.read(token)).isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }
}
