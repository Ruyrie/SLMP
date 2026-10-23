package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.api.FarmAuditAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.mapper.FarmAuditMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.service.impl.FarmAuditAdminServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmAuditAdminServiceImplTest {

    @Mock private FarmAuditMapper auditMapper;
    @Mock private FarmMapper farmMapper;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;

    private FarmAuditAdminService service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmAuditAdminServiceImpl(auditMapper, farmMapper, cache, logs, notify);
        lenient().when(auditMapper.selectById(3001L)).thenReturn(audit("PENDING", 2));
        lenient().when(farmMapper.selectIncludingDeleted(1003L)).thenReturn(farm());
        lenient().when(auditMapper.update(any(FarmAudit.class), any())).thenReturn(1);
        lenient().when(farmMapper.update(any(Farm.class), any())).thenReturn(1);
    }

    @Test
    void approveUpdatesAuditAndFarmAndNotifiesOwner() {
        service.approve(3001L, 9L, "资质清晰", 2);

        verify(auditMapper).update(any(FarmAudit.class), any());
        verify(farmMapper).update(any(Farm.class), any());
        verify(cache).evictFarmMembers(1003L);
        verify(notify).auditResult(any(), any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("资质清晰"));
    }

    @Test
    void rejectUpdatesAuditAndFarmWithReshootFiles() {
        service.reject(3001L, 9L, "照片模糊", List.of(9002L), 2);

        verify(auditMapper).update(any(FarmAudit.class), any());
        verify(farmMapper).update(any(Farm.class), any());
        verify(notify).auditResult(any(), any(), org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq("照片模糊"));
    }

    @Test
    void wrongVersionReturns40902() {
        assertThatThrownBy(() -> service.approve(3001L, 9L, "ok", 1))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40902);
    }

    @Test
    void processedAuditReturns40903() {
        when(auditMapper.selectById(3001L)).thenReturn(audit("APPROVED", 2));
        assertThatThrownBy(() -> service.approve(3001L, 9L, "ok", 2))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(40903);
    }

    @Test
    void countPendingReturnsMapperCount() {
        when(auditMapper.selectCount(any())).thenReturn(2L);
        assertThat(service.countPending()).isEqualTo(2L);
    }

    private static FarmAudit audit(String status, int version) {
        FarmAudit audit = new FarmAudit();
        audit.setId(3001L);
        audit.setFarmId(1003L);
        audit.setSubmitNo(1);
        audit.setStatus(status);
        audit.setPendingFlag("PENDING".equals(status));
        audit.setVersion(version);
        audit.setReshootFileIds("[]");
        return audit;
    }

    private static Farm farm() {
        Farm farm = new Farm();
        farm.setId(1003L);
        farm.setName("北湾牛场");
        farm.setOwnerUserId(10001L);
        farm.setAuditStatus("PENDING");
        farm.setVersion(2);
        farm.setDeleted(false);
        return farm;
    }
}
