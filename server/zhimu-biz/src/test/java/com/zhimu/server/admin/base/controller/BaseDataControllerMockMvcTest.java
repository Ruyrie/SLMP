package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.AdminBaseTestMvc;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.vo.BaseCategoryBreedVO;
import com.zhimu.server.admin.base.vo.BaseCategoryVO;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** U09/U10/U12：数组结构、snake_case、X-Farm-Id 透传、40401；并确认没有 @RequireFarm。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseDataControllerMockMvcTest {

    @Mock private CategoryQueryService categoryQueryService;
    @Mock private DictQueryService dictQueryService;
    @Mock private StdQueryService stdQueryService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = AdminBaseTestMvc.builder(
                new BaseDataController(categoryQueryService, dictQueryService, stdQueryService)).build();
    }

    @Test
    void u09ReturnsArrayWithExpectedFields() throws Exception {
        BaseCategoryVO category = new BaseCategoryVO();
        category.setId(101L);
        category.setName("育肥猪");
        category.setSpecies("PIG");
        category.setSpeciesText("猪");
        category.setCountUnit("HEAD");
        category.setCountUnitText("头");
        category.setIsBreeding(false);
        BaseCategoryBreedVO breed = new BaseCategoryBreedVO();
        breed.setId(1101L);
        breed.setName("杜长大");
        breed.setHasStdWeight(true);
        breed.setHasFeedStd(true);
        breed.setHasImmuneTemplate(true);
        category.getBreeds().add(breed);
        when(categoryQueryService.enabledTree()).thenReturn(List.of(category));

        mvc.perform(get("/api/v1/base/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].is_breeding").value(false))
                .andExpect(jsonPath("$.data[0].species_text").value("猪"))
                .andExpect(jsonPath("$.data[0].breeds[0].id").value(1101))
                .andExpect(jsonPath("$.data[0].breeds[0].has_std_weight").value(true))
                .andExpect(jsonPath("$.data[0].breeds[0].has_immune_template").value(true));
    }

    @Test
    void u10PassesSpeciesCategoryAndFarmHeader() throws Exception {
        BaseDictItemVO item = new BaseDictItemVO();
        item.setId(501L);
        item.setCode("NURSERY");
        item.setName("保育舍");
        item.setRemarkRequired(false);
        item.setIsSystem(false);
        item.setSortNo(1);
        when(dictQueryService.listForApp("PEN_TYPE", "PIG", null, 1001L)).thenReturn(List.of(item));

        mvc.perform(get("/api/v1/base/dicts/PEN_TYPE")
                        .param("species", "PIG").header("X-Farm-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(501))
                .andExpect(jsonPath("$.data[0].is_system").value(false))
                .andExpect(jsonPath("$.data[0].remark_required").value(false));
    }

    @Test
    void u10CategoryIdFilter() throws Exception {
        when(dictQueryService.listForApp(eq("SYMPTOM"), eq(null), eq(101L), eq(null)))
                .thenReturn(List.of());
        mvc.perform(get("/api/v1/base/dicts/SYMPTOM").param("category_id", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void u12ReturnsWeightFeedAndAtDay() throws Exception {
        BreedStandardsVO vo = new BreedStandardsVO();
        vo.setBreedId(1101L);
        BreedStandardsVO.WeightStandardVO weight = new BreedStandardsVO.WeightStandardVO();
        weight.setVersionId(101L);
        weight.setVersionNo(1);
        weight.setToleranceRate(new BigDecimal("0.1000"));
        weight.setSuggestedWeighTimes(2);
        weight.setWeighIntervalDays(14);
        BreedStandardsVO.WeightPointVO point = new BreedStandardsVO.WeightPointVO();
        point.setDayAge(90);
        point.setStdWeight(new BigDecimal("62.0"));
        point.setLower(new BigDecimal("55.8"));
        point.setUpper(new BigDecimal("68.2"));
        weight.setPoints(List.of(point));
        vo.setWeight(weight);
        BreedStandardsVO.AtDayVO atDay = new BreedStandardsVO.AtDayVO();
        atDay.setDayAge(96);
        atDay.setStdWeight(new BigDecimal("68.6"));
        atDay.setLower(new BigDecimal("61.7"));
        atDay.setUpper(new BigDecimal("75.5"));
        atDay.setStageName("育肥前期");
        atDay.setStageShortName("育肥猪");
        vo.setAtDay(atDay);
        when(stdQueryService.standards(1101L, 96)).thenReturn(vo);

        mvc.perform(get("/api/v1/base/breeds/1101/standards").param("day_age", "96"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breed_id").value(1101))
                .andExpect(jsonPath("$.data.weight.weigh_interval_days").value(14))
                .andExpect(jsonPath("$.data.weight.points[0].std_weight").value(62.0))
                .andExpect(jsonPath("$.data.at_day.std_weight").value(68.6))
                .andExpect(jsonPath("$.data.at_day.lower").value(61.7))
                .andExpect(jsonPath("$.data.at_day.upper").value(75.5))
                .andExpect(jsonPath("$.data.at_day.stage_short_name").value("育肥猪"));
    }

    @Test
    void u10InvalidDictTypeReturns40401() throws Exception {
        when(dictQueryService.listForApp("NOPE", null, null, null))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/v1/base/dicts/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void publicBaseDataApiDoesNotRequireFarm() {
        assertThat(BaseDataController.class.getAnnotation(RequireFarm.class)).isNull();
        assertThat(BaseDataController.class.getMethods())
                .allSatisfy(method -> assertThat(method.getAnnotation(RequireFarm.class)).isNull());
    }
}
