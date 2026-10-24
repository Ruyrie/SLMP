package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.system.api.ExportService;
import com.zhimu.server.admin.system.mapper.readonly.AdminFarmQueryMapper;
import com.zhimu.server.admin.system.request.FarmFreezeRequest;
import com.zhimu.server.admin.system.vo.FreezeResultVO;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.api.FarmPlatformService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FarmAdminServiceImplTest {

    private final AdminFarmQueryMapper mapper = mock(AdminFarmQueryMapper.class);
    private final FarmPlatformService farmPlatformService = mock(FarmPlatformService.class);
    private final FarmAdminServiceImpl service = new FarmAdminServiceImpl(mapper,
            mock(BaseDataQueryService.class), mock(BizMediaService.class), farmPlatformService,
            mock(ExportService.class));

    @BeforeEach
    void setUp() {
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"),
                Set.of("WA-S04:VIEW", "WA-S04:HANDLE"), 0, "tok"));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void 冻结返回收到消息的成员数() {
        Farm farm = farm();
        when(mapper.selectFarm(1001L)).thenReturn(farm);
        when(farmPlatformService.listActiveUserIds(1001L)).thenReturn(List.of(10001L, 10002L, 10003L));
        FarmFreezeRequest request = new FarmFreezeRequest();
        request.setReason("证件被注销");
        request.setSmsOwner(true);
        request.setVersion(3);

        FreezeResultVO result = service.freeze(1001L, request);

        verify(farmPlatformService).freeze(1001L, 1L, "证件被注销", true, 3);
        assertThat(result.getMemberCount()).isEqualTo(3);
        assertThat(result.getFarmName()).isEqualTo("东山猪场");
    }

    @Test
    void 冻结原因为空返回40004() {
        FarmFreezeRequest request = new FarmFreezeRequest();
        request.setReason(" ");
        request.setVersion(3);
        assertThatThrownBy(() -> service.freeze(1001L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40004));
    }

    private static Farm farm() {
        Farm farm = new Farm();
        farm.setId(1001L);
        farm.setName("东山猪场");
        farm.setAuditStatus("APPROVED");
        farm.setDisabled(false);
        farm.setFrozen(false);
        farm.setVersion(3);
        return farm;
    }
}
