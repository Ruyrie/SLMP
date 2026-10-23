package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.FarmMainCategoryReadMapper;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.support.DictTypes;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** U10：启用项、species/category_id 过滤、X-Farm-Id 推断、非法 dict_type 40401。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DictQueryServiceImplTest {

    @Mock private DictItemMapper dictItemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private BaseDataReadSupport support;
    @Mock private FarmMainCategoryReadMapper farmMapper;

    private DictQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DictQueryServiceImpl(dictItemMapper, categoryMapper, support, farmMapper);
        DictItem nursery = item(501L, "PEN_TYPE", "NURSERY", "保育舍", 1);
        DictItem fattening = item(502L, "PEN_TYPE", "FATTENING", "育肥舍", 2);
        DictItem brooding = item(505L, "PEN_TYPE", "BROODING", "育雏舍", 5);
        Map<Long, List<DictItemScope>> scopes = new LinkedHashMap<>();
        scopes.put(501L, List.of(scope(501L, "SPECIES", "PIG", 0)));
        scopes.put(502L, List.of(scope(502L, "SPECIES", "PIG", 0)));
        scopes.put(505L, List.of(scope(505L, "SPECIES", "POULTRY", 0)));
        when(dictItemMapper.selectList(any())).thenReturn(List.of(nursery, fattening, brooding));
        when(support.scopesByItem(any())).thenReturn(scopes);
        Category pig = new Category();
        pig.setId(101L);
        pig.setName("育肥猪");
        pig.setSpecies("PIG");
        pig.setStatus("ENABLED");
        Category poultry = new Category();
        poultry.setId(201L);
        poultry.setName("肉鸡");
        poultry.setSpecies("POULTRY");
        poultry.setStatus("ENABLED");
        lenient().when(categoryMapper.selectById(101L)).thenReturn(pig);
        lenient().when(categoryMapper.selectById(201L)).thenReturn(poultry);
        lenient().when(farmMapper.selectMainCategoryId(1001L)).thenReturn(101L);
    }

    @Test
    void noFilterReturnsAllEnabledOrdered() {
        List<BaseDictItemVO> items = service.listForApp("PEN_TYPE", null, null, null);
        assertThat(items).extracting(BaseDictItemVO::getId).containsExactly(501L, 502L, 505L);
    }

    @Test
    void filtersBySpecies() {
        assertThat(service.listForApp("PEN_TYPE", "PIG", null, null))
                .extracting(BaseDictItemVO::getId).containsExactly(501L, 502L);
        assertThat(service.listForApp("PEN_TYPE", "POULTRY", null, null))
                .extracting(BaseDictItemVO::getId).containsExactly(505L);
    }

    @Test
    void filtersByCategorySpecies() {
        assertThat(service.listForApp("PEN_TYPE", null, 201L, null))
                .extracting(BaseDictItemVO::getId).containsExactly(505L);
    }

    @Test
    void infersSpeciesFromFarmMainCategory() {
        assertThat(service.listForApp("PEN_TYPE", null, null, 1001L))
                .extracting(BaseDictItemVO::getId).containsExactly(501L, 502L);
    }

    @Test
    void invalidDictTypeIs40401() {
        assertThatThrownBy(() -> service.listForApp("NOT_A_TYPE", null, null, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40401));
    }

    @Test
    void feedTypeUnionHasNineEnabledItemsUnscoped() {
        List<DictItem> feed = List.of(item(3101L, "FEED_TYPE", "COMPLETE", "全价料", 1),
                item(3102L, "FEED_TYPE", "NURSERY", "保育料", 2),
                item(3103L, "FEED_TYPE", "CONCENTRATE", "浓缩料", 3),
                item(3104L, "FEED_TYPE", "PREMIX", "预混料", 4),
                item(3105L, "FEED_TYPE", "RAW", "原料", 5),
                item(3106L, "FEED_TYPE", "ENERGY", "能量原料", 6),
                item(3107L, "FEED_TYPE", "PROTEIN", "蛋白原料", 7),
                item(3108L, "FEED_TYPE", "ROUGHAGE", "青粗饲料", 8),
                item(3109L, "FEED_TYPE", "ADDITIVE", "添加剂", 9));
        when(dictItemMapper.selectList(any())).thenReturn(feed);
        when(support.scopesByItem(any())).thenReturn(Map.of());
        assertThat(service.listForApp("FEED_TYPE", null, null, null)).hasSize(9);
    }

    @Test
    void paymentMethodIsEighthKnownTypeAndNormalizesCase() {
        when(dictItemMapper.selectList(any())).thenReturn(List.of(
                item(3301L, "PAYMENT_METHOD", "BANK_TRANSFER", "银行转账", 1),
                item(3302L, "PAYMENT_METHOD", "WECHAT", "微信", 2),
                item(3303L, "PAYMENT_METHOD", "ALIPAY", "支付宝", 3),
                item(3304L, "PAYMENT_METHOD", "CASH", "现金", 4)));
        when(support.scopesByItem(any())).thenReturn(Map.of());

        assertThat(DictTypes.ALL).containsExactly("PEN_TYPE", "DEATH_REASON", "SYMPTOM", "FEED_TYPE",
                "VACCINATE_METHOD", "DISINFECT_METHOD", "SCRAP_REASON", "PAYMENT_METHOD");
        assertThat(service.listForApp("payment_method", null, null, null))
                .extracting(BaseDictItemVO::getCode)
                .containsExactly("BANK_TRANSFER", "WECHAT", "ALIPAY", "CASH");
    }

    private static DictItem item(Long id, String type, String code, String name, int sort) {
        DictItem item = new DictItem();
        item.setId(id);
        item.setDictType(type);
        item.setCode(code);
        item.setName(name);
        item.setSortNo(sort);
        item.setStatus("ENABLED");
        item.setRemarkRequired(false);
        item.setIsSystem(false);
        item.setVersion(1);
        return item;
    }

    private static DictItemScope scope(Long itemId, String scopeType, String species, long categoryId) {
        DictItemScope scope = new DictItemScope();
        scope.setDictItemId(itemId);
        scope.setScopeType(scopeType);
        scope.setSpecies(species);
        scope.setCategoryId(categoryId);
        return scope;
    }
}
