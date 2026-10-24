package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.BannedDrug;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.mapper.BannedDrugMapper;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.request.BannedDrugCreateRequest;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
import com.zhimu.server.admin.base.support.DrugAdminSupport;
import com.zhimu.server.admin.base.vo.BannedDrugVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D30–D34 关键分支：目录标记禁用药、重复标记 40903、原因为空 40004。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BannedDrugAdminServiceImplTest {

    @Mock private BannedDrugMapper bannedDrugMapper;
    @Mock private DrugCatalogMapper catalogMapper;
    @Mock private BaseAdminReadMapper readMapper;
    @Mock private BaseTemplateSupport support;
    @Mock private DrugAdminSupport drugSupport;
    @Mock private BaseLogs logs;

    private BannedDrugAdminServiceImpl service;
    private DrugCatalog catalog;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new BannedDrugAdminServiceImpl(bannedDrugMapper, catalogMapper, readMapper, support,
                drugSupport, logs);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        catalog = new DrugCatalog();
        catalog.setId(7202L);
        catalog.setName("氟苯尼考粉");
        catalog.setDrugCategory("VET_DRUG");
        catalog.setVersion(3);
        when(drugSupport.requireCatalog(7202L)).thenReturn(catalog);
        when(catalogMapper.update(any(), any())).thenReturn(1);
        when(bannedDrugMapper.selectOne(any())).thenReturn(null);
        when(bannedDrugMapper.insert(any(BannedDrug.class))).thenAnswer(invocation -> {
            BannedDrug drug = invocation.getArgument(0);
            drug.setId(9906L);
            return 1;
        });
        when(support.adminNames()).thenReturn(Map.of(1L, "周敏"));
        when(readMapper.stockTotalsByCatalog(7202L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void banCatalogMarksDrugAndCatalog() {
        BannedDrugCreateRequest request = request();
        BannedDrugVO vo = service.create(request);

        assertThat(vo.getId()).isEqualTo(9906L);
        assertThat(vo.getName()).isEqualTo("氟苯尼考粉");
        assertThat(vo.getBannedClassText()).isEqualTo("抗菌药");
        assertThat(vo.getListedByName()).isEqualTo("周敏");
        verify(bannedDrugMapper).insert(any(BannedDrug.class));
        verify(catalogMapper).update(any(), any());
    }

    @Test
    void banAlreadyBannedCatalogReturns40903() {
        catalog.setBanned(true);
        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903))
                .hasMessage("该药品已列为禁用药");
    }

    @Test
    void banWithoutReasonReturns40004() {
        BannedDrugCreateRequest request = request();
        request.setReason(" ");
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004))
                .hasMessage("请填写列入原因");
    }

    private static BannedDrugCreateRequest request() {
        BannedDrugCreateRequest request = new BannedDrugCreateRequest();
        request.setCatalogId(7202L);
        request.setBannedClass("ANTIBACTERIAL");
        request.setReason("农业农村部公告停止使用");
        return request;
    }
}
