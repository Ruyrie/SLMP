package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.AdminBaseTestMvc;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.vo.BaseCategoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * U09 与 App Mock categories.json 逐字段对比；同时验证第 1 批 BaseDataQueryService 的两个方法仍可用。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryQueryServiceImplTest {

    @Mock private CategoryMapper categoryMapper;
    @Mock private BreedMapper breedMapper;
    @Mock private BaseDataReadSupport support;

    private CategoryQueryServiceImpl service;

    private static final String EXPECTED = """
            [
              {"id":101,"name":"育肥猪","species":"PIG","species_text":"猪","count_unit":"HEAD",
               "count_unit_text":"头","is_breeding":false,
               "breeds":[
                 {"id":1101,"name":"杜长大","has_std_weight":true,"has_feed_std":true,"has_immune_template":true},
                 {"id":1102,"name":"长白","has_std_weight":true,"has_feed_std":true,"has_immune_template":true}
               ]},
              {"id":102,"name":"种猪","species":"PIG","species_text":"猪","count_unit":"HEAD",
               "count_unit_text":"头","is_breeding":true,
               "breeds":[
                 {"id":1201,"name":"长大二元","gestation_days":114,"pregnancy_check_days":21,
                  "offspring_breed_id":1101,"has_std_weight":false,"has_feed_std":true,"has_immune_template":true}
               ]},
              {"id":201,"name":"肉鸡","species":"POULTRY","species_text":"禽","count_unit":"BIRD",
               "count_unit_text":"只","is_breeding":false,
               "breeds":[
                 {"id":2101,"name":"白羽肉鸡","incubation_days":21,"has_std_weight":true,"has_feed_std":true,
                  "has_immune_template":true}
               ]},
              {"id":301,"name":"肉牛","species":"CATTLE","species_text":"牛","count_unit":"HEAD",
               "count_unit_text":"头","is_breeding":false,
               "breeds":[
                 {"id":3101,"name":"西门塔尔","has_std_weight":true,"has_feed_std":false,"has_immune_template":true}
               ]}
            ]
            """;

    @BeforeEach
    void setUp() {
        service = new CategoryQueryServiceImpl(categoryMapper, breedMapper, support);
        List<Category> categories = List.of(
                category(101L, "育肥猪", "PIG", "HEAD", false, 1),
                category(102L, "种猪", "PIG", "HEAD", true, 2),
                category(201L, "肉鸡", "POULTRY", "BIRD", false, 3),
                category(301L, "肉牛", "CATTLE", "HEAD", false, 4));
        Map<Long, List<Breed>> breeds = new LinkedHashMap<>();
        breeds.put(101L, List.of(breed(1101L, 101L, "杜长大", 1), breed(1102L, 101L, "长白", 2)));
        breeds.get(101L).get(0).setDescription("杜洛克 × 长白 × 大白三元杂交");
        breeds.put(102L, List.of(breed(1201L, 102L, "长大二元", 1, 114, 21, 1101L)));
        breeds.put(201L, List.of(breed(2101L, 201L, "白羽肉鸡", 1, null, null, null, 21)));
        breeds.put(301L, List.of(breed(3101L, 301L, "西门塔尔", 1)));
        when(support.categories()).thenReturn(categories);
        when(support.breedsByCategory()).thenReturn(breeds);
        when(support.publishedWeightBreedIds()).thenReturn(Set.of(1101L, 1102L, 2101L, 3101L));
        when(support.publishedFeedBreedIds()).thenReturn(Set.of(1101L, 1102L, 1201L, 2101L));
        lenient().when(support.hasImmuneTemplate(anyLong())).thenReturn(true);
        lenient().when(categoryMapper.selectById(101L)).thenReturn(categories.get(0));
        lenient().when(breedMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(2L);
    }

    @Test
    void u09MatchesAppCategoriesJsonFieldByField() {
        List<BaseCategoryVO> actual = service.enabledTree();
        JsonNode actualNode = AdminBaseTestMvc.MAPPER.readTree(AdminBaseTestMvc.MAPPER.writeValueAsString(actual));
        JsonNode expectedNode = AdminBaseTestMvc.json(EXPECTED);
        assertThat(actualNode).isEqualTo(expectedNode);
    }

    @Test
    void baseDataQueryServiceKeepsBatch1Signatures() {
        CategoryInfo info = service.getCategory(101L);
        assertThat(info).isNotNull();
        assertThat(info.id()).isEqualTo(101L);
        assertThat(info.species()).isEqualTo("PIG");
        assertThat(info.speciesText()).isEqualTo("猪");
        assertThat(info.countUnit()).isEqualTo("HEAD");
        assertThat(info.countUnitText()).isEqualTo("头");
        assertThat(info.isBreeding()).isFalse();
        assertThat(info.enabled()).isTrue();
        assertThat(service.listEnabledCategories()).hasSize(4).extracting(CategoryInfo::id)
                .containsExactly(101L, 102L, 201L, 301L);
        assertThat(service.getCategory(999L)).isNull();
    }

    @Test
    void treeKeywordOnlyReturnsMatchedBreedsCategory() {
        List<com.zhimu.server.admin.base.vo.CategoryNodeVO> nodes = service.tree("三元");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getId()).isEqualTo(101L);
        assertThat(nodes.get(0).getBreeds()).extracting(com.zhimu.server.admin.base.vo.CategoryNodeVO.BreedNodeVO::getId)
                .containsExactly(1101L);
        assertThat(service.tree("不存在").isEmpty()).isTrue();
    }

    private static Category category(Long id, String name, String species, String countUnit, boolean breeding,
                                     int sort) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSpecies(species);
        category.setCountUnit(countUnit);
        category.setIsBreeding(breeding);
        category.setSortNo(sort);
        category.setStatus("ENABLED");
        category.setVersion(1);
        return category;
    }

    private static Breed breed(Long id, Long categoryId, String name, int sort) {
        return breed(id, categoryId, name, sort, null, null, null, null);
    }

    private static Breed breed(Long id, Long categoryId, String name, int sort, Integer gestation,
                               Integer pregnancy, Long offspring) {
        return breed(id, categoryId, name, sort, gestation, pregnancy, offspring, null);
    }

    private static Breed breed(Long id, Long categoryId, String name, int sort, Integer gestation,
                               Integer pregnancy, Long offspring, Integer incubation) {
        Breed breed = new Breed();
        breed.setId(id);
        breed.setCategoryId(categoryId);
        breed.setName(name);
        breed.setSortNo(sort);
        breed.setStatus("ENABLED");
        breed.setGestationDays(gestation);
        breed.setPregnancyCheckDays(pregnancy);
        breed.setOffspringBreedId(offspring);
        breed.setIncubationDays(incubation);
        return breed;
    }
}
