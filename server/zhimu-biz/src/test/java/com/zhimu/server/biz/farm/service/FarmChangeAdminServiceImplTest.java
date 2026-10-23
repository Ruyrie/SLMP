package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.FarmTableInfo;
import com.zhimu.server.biz.farm.api.FarmChangeAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.mapper.FarmChangeMapper;
import com.zhimu.server.biz.farm.mapper.FarmMapper;
import com.zhimu.server.biz.farm.service.impl.FarmChangeAdminServiceImpl;
import com.zhimu.server.biz.farm.support.FarmCache;
import com.zhimu.server.biz.farm.support.FarmLogs;
import com.zhimu.server.biz.farm.support.FarmNotify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarmChangeAdminServiceImplTest {

    @Mock private FarmChangeMapper changeMapper;
    @Mock private FarmMapper farmMapper;
    @Mock private BizMediaService bizMedia;
    @Mock private FarmCache cache;
    @Mock private FarmLogs logs;
    @Mock private FarmNotify notify;

    private FarmChangeAdminService service;

    @BeforeEach
    void setUp() {
        FarmTableInfo.ensure();
        service = new FarmChangeAdminServiceImpl(changeMapper, farmMapper, bizMedia, cache, logs, notify);
        lenient().when(changeMapper.selectById(21L)).thenReturn(change());
        lenient().when(farmMapper.selectIncludingDeleted(1001L)).thenReturn(farm());
        lenient().when(changeMapper.update(any(FarmChange.class), any())).thenReturn(1);
        lenient().when(farmMapper.update(any(Farm.class), any())).thenReturn(1);
    }

    @Test
    void approveAppliesNewFieldsCopiesMediaAndNotifies() {
        service.approve(21L, 9L, "同意", 1);

        verify(farmMapper).update(any(Farm.class), any());
        verify(bizMedia).replace(any(), any(), any(), any());
        verify(cache).evictFarmMembers(1001L);
        verify(notify).changeResult(any(), any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("同意"));
    }

    @Test
    void rejectLeavesFarmUnchangedAndNotifies() {
        service.reject(21L, 9L, "资料不清晰", 1);

        verify(changeMapper).update(any(FarmChange.class), any());
        verify(farmMapper, org.mockito.Mockito.never()).update(any(Farm.class), any());
        verify(notify).changeResult(any(), any(), org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq("资料不清晰"));
    }

    @Test
    void countPendingReturnsMapperCount() {
        when(changeMapper.selectCount(any())).thenReturn(3L);
        assertThat(service.countPending()).isEqualTo(3L);
    }

    private static FarmChange change() {
        FarmChange change = new FarmChange();
        change.setId(21L);
        change.setFarmId(1001L);
        change.setChangeNo(1);
        change.setStatus("PENDING");
        change.setPendingFlag(true);
        change.setNewName("东山猪场");
        change.setNewAddress("新地址");
        change.setNewProvince("内蒙古自治区");
        change.setNewCity("赤峰市");
        change.setNewDistrict("松山区");
        change.setNewAdcode("150404");
        change.setNewLongitude(BigDecimal.valueOf(118.86));
        change.setNewLatitude(BigDecimal.valueOf(42.31));
        change.setVersion(1);
        return change;
    }

    private static Farm farm() {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setOwnerUserId(10001L);
        farm.setAuditStatus("APPROVED");
        farm.setVersion(2);
        farm.setDeleted(false);
        return farm;
    }
}
