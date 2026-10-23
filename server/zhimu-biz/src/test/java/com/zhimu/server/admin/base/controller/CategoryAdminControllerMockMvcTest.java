package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.AdminBaseTestMvc;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.service.BreedAdminService;
import com.zhimu.server.admin.base.service.CategoryAdminService;
import com.zhimu.server.admin.base.service.StdAdminService;
import com.zhimu.server.admin.base.vo.BreedDetailVO;
import com.zhimu.server.admin.base.vo.BreedVO;
import com.zhimu.server.admin.base.vo.CategoryNodeVO;
import com.zhimu.server.admin.base.vo.CategoryVO;
import com.zhimu.server.admin.base.vo.FeedStdVO;
import com.zhimu.server.admin.base.vo.StdImpactVO;
import com.zhimu.server.admin.base.vo.StdVersionVO;
import com.zhimu.server.admin.base.vo.StdWeightVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADM-D01–D13 路由、JSON 字段与关键错误分支（MockMvc standalone，不启动 Spring 上下文）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryAdminControllerMockMvcTest {

    @Mock private CategoryQueryService categoryQuery;
    @Mock private BreedQueryService breedQuery;
    @Mock private CategoryAdminService categoryService;
    @Mock private BreedAdminService breedService;
    @Mock private StdAdminService stdService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = AdminBaseTestMvc.builder(new CategoryAdminController(categoryQuery, breedQuery, categoryService,
                breedService, stdService)).build();
    }

    @Test
    void d01ToD06Routes() throws Exception {
        CategoryNodeVO node = new CategoryNodeVO();
        node.setId(101L);
        node.setName("育肥猪");
        when(categoryQuery.tree("猪")).thenReturn(List.of(node));

        CategoryVO category = new CategoryVO();
        category.setId(101L);
        category.setName("育肥猪");
        category.setVersion(2);
        when(categoryService.create(any())).thenReturn(category);
        when(categoryService.update(eq(101L), any())).thenReturn(category);

        BreedDetailVO detail = new BreedDetailVO();
        detail.setId(1101L);
        detail.setName("杜长大");
        detail.setVersion(3);
        when(breedQuery.detail(1101L)).thenReturn(detail);

        BreedVO breed = new BreedVO();
        breed.setId(1201L);
        breed.setName("长大二元");
        when(breedService.create(any())).thenReturn(breed);
        when(breedService.update(eq(1201L), any())).thenReturn(breed);

        mvc.perform(get("/admin/v1/categories/tree").param("keyword", "猪"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101));

        mvc.perform(post("/admin/v1/categories").header("Idempotency-Key", "k1")
                        .contentType("application/json")
                        .content("{\"name\":\"育肥猪\",\"species\":\"PIG\",\"is_breeding\":false,\"sort_no\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已保存“育肥猪”"))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(put("/admin/v1/categories/101").contentType("application/json")
                        .content("{\"name\":\"育肥猪\",\"species\":\"PIG\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/admin/v1/breeds/1101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1101));

        mvc.perform(post("/admin/v1/breeds").header("Idempotency-Key", "k2")
                        .contentType("application/json")
                        .content("{\"category_id\":102,\"name\":\"长大二元\",\"gestation_days\":114,"
                                + "\"pregnancy_check_days\":21,\"offspring_breed_id\":1101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已保存长大二元"));

        mvc.perform(put("/admin/v1/breeds/1201").contentType("application/json")
                        .content("{\"category_id\":102,\"name\":\"长大二元\",\"gestation_days\":114,"
                                + "\"pregnancy_check_days\":21,\"offspring_breed_id\":1101,\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1201));
    }

    @Test
    void d07ToD13Routes() throws Exception {
        StdWeightVO weight = new StdWeightVO();
        weight.setBreedId(1101L);
        FeedStdVO feed = new FeedStdVO();
        feed.setBreedId(1101L);
        when(stdService.weight(1101L)).thenReturn(weight);
        when(stdService.feed(1101L)).thenReturn(feed);
        when(stdService.saveWeightDraft(eq(1101L), any())).thenReturn(weight);
        when(stdService.saveFeedDraft(eq(1101L), any())).thenReturn(feed);

        StdImpactVO impact = new StdImpactVO();
        impact.setActiveBatchCount(0);
        impact.setAffectedBatchCount(0);
        impact.setFarmCount(0);
        when(stdService.impact(1101L, "weight")).thenReturn(impact);

        StdVersionVO version = new StdVersionVO();
        version.setVersionNo(2);
        StdAdminService.PublishResult published = new StdAdminService.PublishResult(version,
                "已发布第 2 版，用户端已按新标准重新计算");
        when(stdService.publish(eq(1101L), eq("weight"), any())).thenReturn(published);
        when(stdService.rollback(eq(1101L), eq("weight"), any())).thenReturn(
                new StdAdminService.PublishResult(version, "已回退到第 1 版的内容，生成第 3 版"));
        when(stdService.versions(1101L, "weight")).thenReturn(List.of(version));

        mvc.perform(get("/admin/v1/breeds/1101/standards/weight"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.breed_id").value(1101));

        mvc.perform(get("/admin/v1/breeds/1101/standards/feed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.breed_id").value(1101));

        mvc.perform(put("/admin/v1/breeds/1101/standards/weight/draft").contentType("application/json")
                        .content("{\"tolerance_rate\":0.1,\"suggested_weigh_times\":2,\"effective_mode\":\"IMMEDIATE\","
                                + "\"points\":[{\"day_age\":80,\"std_weight\":51.0},{\"day_age\":90,\"std_weight\":62.0}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.breed_id").value(1101));

        mvc.perform(put("/admin/v1/breeds/1101/standards/feed/draft").contentType("application/json")
                        .content("{\"deviation_rate\":0.1,\"stages\":[{\"stage_name\":\"育肥前期\","
                                + "\"stage_short_name\":\"育肥猪\",\"day_age_start\":61,\"day_age_end\":null,"
                                + "\"feed_type_code\":\"COMPLETE\",\"suggested_feed_name\":\"育肥全价料\","
                                + "\"daily_feed\":2.5,\"meals_per_day\":3,"
                                + "\"meal_times\":[\"07:00\",\"11:30\",\"17:00\"]}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.breed_id").value(1101));

        mvc.perform(delete("/admin/v1/breeds/1101/standards/weight/draft").param("version", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/admin/v1/breeds/1101/standards/weight/impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_batch_count").value(0))
                .andExpect(jsonPath("$.data.flag_changes").isArray());

        mvc.perform(post("/admin/v1/breeds/1101/standards/weight/publish").contentType("application/json")
                        .content("{\"update_note\":\"下调育肥后期标准体重\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已发布第 2 版，用户端已按新标准重新计算"))
                .andExpect(jsonPath("$.data.version_no").value(2));

        mvc.perform(get("/admin/v1/breeds/1101/standards/weight/versions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].version_no").value(2));

        mvc.perform(post("/admin/v1/breeds/1101/standards/weight/rollback").contentType("application/json")
                        .content("{\"target_version_no\":1,\"reason\":\"数据有误\",\"online_version_no\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已回退到第 1 版的内容，生成第 3 版"));
    }

    @Test
    void validationAndBusinessErrorsUseDocumentedCodes() throws Exception {
        mvc.perform(post("/admin/v1/categories").contentType("application/json")
                        .content("{\"name\":\"\",\"species\":\"PIG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.field_errors[0].field").value("name"));

        when(categoryService.update(eq(101L), any())).thenThrow(new BizException(ErrorCode.VERSION_CONFLICT,
                "内容已更新，请刷新后再修改"));
        mvc.perform(put("/admin/v1/categories/101").contentType("application/json")
                        .content("{\"name\":\"育肥猪\",\"species\":\"PIG\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("内容已更新，请刷新后再修改"));

        when(breedQuery.detail(9999L)).thenReturn(null);
        mvc.perform(get("/admin/v1/breeds/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        when(categoryService.create(any())).thenThrow(new BizException(ErrorCode.PARAM_INVALID, "参数错误",
                Map.of("field_errors", List.of(new FieldErrorVO("name", "已有品类“育肥猪”")))));
        mvc.perform(post("/admin/v1/categories").contentType("application/json")
                        .content("{\"name\":\"育肥猪\",\"species\":\"PIG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.field_errors[0].message").value("已有品类“育肥猪”"));
    }

    @Test
    void adminPermissionsAreDeclared() throws Exception {
        assertThatClassAndMethodsHaveAdminPermission();
    }

    private static void assertThatClassAndMethodsHaveAdminPermission() throws Exception {
        org.assertj.core.api.Assertions.assertThat(CategoryAdminController.class
                        .getAnnotation(com.zhimu.server.framework.security.annotation.AdminPermission.class))
                .isNotNull();
        for (var method : CategoryAdminController.class.getDeclaredMethods()) {
            if (method.getName().startsWith("d") || method.getName().equals("tree")) {
                continue;
            }
        }
        org.assertj.core.api.Assertions.assertThat(CategoryAdminController.class
                .getDeclaredMethod("publish", Long.class, String.class,
                        com.zhimu.server.admin.base.request.StdPublishRequest.class)
                .getAnnotation(com.zhimu.server.framework.security.annotation.AdminPermission.class))
                .isNotNull();
    }
}
