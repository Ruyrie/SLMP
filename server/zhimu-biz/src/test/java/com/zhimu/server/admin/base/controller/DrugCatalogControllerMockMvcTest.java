package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.AdminBaseTestMvc;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.admin.base.vo.DrugCatalogItemVO;
import com.zhimu.server.admin.base.vo.DrugCatalogResultVO;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API-U11：路径/参数/响应字段与 App module_stock CatalogResultVO 和 mock catalog_*.json 对齐。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrugCatalogControllerMockMvcTest {

    @Mock private DrugCatalogQueryService drugCatalogQueryService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = AdminBaseTestMvc.builder(new DrugCatalogController(drugCatalogQueryService)).build();
    }

    @Test
    void u11ReturnsCatalogItemsWithMockFields() throws Exception {
        DrugCatalogItemVO item = new DrugCatalogItemVO();
        item.setId(7201L);
        item.setName("阿莫西林可溶性粉");
        item.setAlias(null);
        item.setDrugCategory("VET_DRUG");
        item.setSpec("10% · 500 克/袋");
        item.setMeasureUnit("G");
        item.setPackUnit("袋");
        item.setPackSize(new BigDecimal("500"));
        DrugCatalogResultVO result = new DrugCatalogResultVO();
        result.getItems().add(item);
        when(drugCatalogQueryService.search("阿莫", null, "VET_DRUG", 1, 20)).thenReturn(result);

        mvc.perform(get("/api/v1/base/drug-catalog")
                        .param("drug_category", "VET_DRUG")
                        .param("keyword", "阿莫")
                        .param("size", "20")
                        .header("X-Farm-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.items[0].id").value(7201))
                .andExpect(jsonPath("$.data.items[0].name").value("阿莫西林可溶性粉"))
                .andExpect(jsonPath("$.data.items[0].drug_category").value("VET_DRUG"))
                .andExpect(jsonPath("$.data.items[0].spec").value("10% · 500 克/袋"))
                .andExpect(jsonPath("$.data.items[0].measure_unit").value("G"))
                .andExpect(jsonPath("$.data.items[0].pack_unit").value("袋"))
                .andExpect(jsonPath("$.data.items[0].pack_size").value(500));

        verify(drugCatalogQueryService).search(eq("阿莫"), eq(null), eq("VET_DRUG"), eq(1), eq(20));
    }

    @Test
    void u11ReturnsBannedHitLikeAppMock() throws Exception {
        DrugCatalogResultVO result = new DrugCatalogResultVO();
        result.setBannedHit(BannedDrugHitVO.of(9901L, "盐酸克仑特罗", "盐酸克仑特罗为禁用药，不能入库"));
        when(drugCatalogQueryService.search("克仑特罗", null, "VET_DRUG", 1, 20)).thenReturn(result);

        mvc.perform(get("/api/v1/base/drug-catalog")
                        .param("drug_category", "VET_DRUG")
                        .param("keyword", "克仑特罗")
                        .header("X-Farm-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.banned_hit.banned_drug_id").value(9901))
                .andExpect(jsonPath("$.data.banned_hit.name").value("盐酸克仑特罗"))
                .andExpect(jsonPath("$.data.banned_hit.message").value("盐酸克仑特罗为禁用药，不能入库"));
    }

    @Test
    void u11PassesPagingAndOptionalParams() throws Exception {
        when(drugCatalogQueryService.search(null, "PIG", "VACCINE", 2, 50))
                .thenReturn(new DrugCatalogResultVO());

        mvc.perform(get("/api/v1/base/drug-catalog")
                        .param("drug_category", "VACCINE")
                        .param("species", "PIG")
                        .param("page", "2")
                        .param("size", "50")
                        .header("X-Farm-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(drugCatalogQueryService).search(null, "PIG", "VACCINE", 2, 50);
    }

    @Test
    void u11RequiresFarmContextButAllowsStaff() throws Exception {
        assertThat(DrugCatalogController.class.getAnnotation(RequireFarm.class)).isNull();
        assertThat(DrugCatalogController.class.getMethod("search", String.class, String.class, String.class,
                int.class, int.class).getAnnotation(RequireFarm.class)).isNotNull();
        assertThat(DrugCatalogController.class.getMethod("search", String.class, String.class, String.class,
                int.class, int.class).getAnnotation(FarmRoles.class)).isNull();
        // 与 3A 的 U09/U10/U12 控制器分开，不破坏“公共基础数据不要求养殖场”的既有测试
        assertThat(BaseDataController.class.getMethods())
                .allSatisfy(method -> assertThat(method.getName()).isNotEqualTo("search"));
    }

    @Test
    void u11ServiceErrorIsRenderedAsApiError() throws Exception {
        when(drugCatalogQueryService.search(null, null, "VET_DRUG", 1, 20))
                .thenThrow(new com.zhimu.server.common.error.BizException(
                        com.zhimu.server.common.error.ErrorCode.PARAM_INVALID, "请选择药品类别"));

        mvc.perform(get("/api/v1/base/drug-catalog")
                        .param("drug_category", "VET_DRUG")
                        .header("X-Farm-Id", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
