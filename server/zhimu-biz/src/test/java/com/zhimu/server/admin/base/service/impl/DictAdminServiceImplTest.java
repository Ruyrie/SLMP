package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.AdminBaseTableInfo;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DictItemMapper;
import com.zhimu.server.admin.base.mapper.DictItemScopeMapper;
import com.zhimu.server.admin.base.query.DictItemQuery;
import com.zhimu.server.admin.base.request.DictItemSaveRequest;
import com.zhimu.server.admin.base.request.DictStatusRequest;
import com.zhimu.server.admin.base.request.ItemOrderRequest;
import com.zhimu.server.admin.base.support.BaseDataReadSupport;
import com.zhimu.server.admin.base.support.BaseLogs;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D49–D54 服务分支：8 类导航、兜底/系统项不能停用、停用原因、版本冲突、同范围重名、排序。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DictAdminServiceImplTest {

    @Mock private DictItemMapper dictItemMapper;
    @Mock private DictItemScopeMapper scopeMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private BaseDataReadSupport support;
    @Mock private BaseLogs logs;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FinanceCategoryDictSupport finance;

    private DictAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        AdminBaseTableInfo.ensure();
        service = new DictAdminServiceImpl(dictItemMapper, scopeMapper, categoryMapper, support, logs, eventPublisher,
                finance);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        lenient().when(dictItemMapper.update(any(), any())).thenReturn(1);
        lenient().when(dictItemMapper.insert(any(DictItem.class))).thenAnswer(invocation -> {
            DictItem item = invocation.getArgument(0);
            item.setId(999L);
            return 1;
        });
        lenient().when(scopeMapper.insert(any(DictItemScope.class))).thenReturn(1);
        lenient().when(scopeMapper.delete(any())).thenReturn(1);
        lenient().when(categoryMapper.selectList(any())).thenReturn(List.of());
        lenient().when(support.scopesByItem(any())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void d49ReturnsEightTypesWithCounts() {
        when(dictItemMapper.selectList(any())).thenReturn(List.of(
                item(501L, "PEN_TYPE", "NURSERY", "保育舍", "ENABLED", false, false),
                item(502L, "PEN_TYPE", "FATTENING", "育肥舍", "ENABLED", false, false),
                item(401L, "SYMPTOM", "DIARRHEA", "腹泻", "ENABLED", false, false)));
        when(finance.counts()).thenReturn(Map.of("EXPENSE_CATEGORY", 7, "INCOME_CATEGORY", 5));
        var types = service.types();
        assertThat(types).hasSize(10);
        assertThat(types.get(0).getDictType()).isEqualTo("PEN_TYPE");
        assertThat(types.get(0).getItemCount()).isEqualTo(2);
        assertThat(types).extracting("dictType").contains("PAYMENT_METHOD");
        assertThat(types.get(8).getDictType()).isEqualTo("EXPENSE_CATEGORY");
        assertThat(types.get(8).getItemCount()).isEqualTo(7);
        assertThat(types.get(8).getPlatformOnly()).isTrue();
        assertThat(types.get(9).getDictType()).isEqualTo("INCOME_CATEGORY");
    }

    @Test
    void disableFallbackOrSystemReturns42207() {
        DictItem fallback = item(604L, "DEATH_REASON", "OTHER", "其他", "ENABLED", true, false);
        fallback.setVersion(2);
        when(dictItemMapper.selectById(604L)).thenReturn(fallback);
        assertThatThrownBy(() -> service.changeStatus("DEATH_REASON", 604L, status("DISABLED", "x", 2)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(42207))
                .hasMessage("‘其他’是兜底选项，不能停用");

        DictItem system = item(3201L, "SCRAP_REASON", "PLATFORM_RECALL", "平台停用", "ENABLED", false, true);
        system.setVersion(1);
        when(dictItemMapper.selectById(3201L)).thenReturn(system);
        assertThatThrownBy(() -> service.changeStatus("SCRAP_REASON", 3201L, status("DISABLED", "x", 1)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(42207))
                .hasMessage("‘平台停用’是系统选项，不能停用");
    }

    @Test
    void disableWithoutReasonReturns40004AndVersionConflict40902() {
        DictItem item = item(3109L, "FEED_TYPE", "ADDITIVE", "添加剂", "ENABLED", false, false);
        item.setVersion(2);
        when(dictItemMapper.selectById(3109L)).thenReturn(item);
        assertThatThrownBy(() -> service.changeStatus("FEED_TYPE", 3109L, status("DISABLED", null, 2)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004));
        assertThatThrownBy(() -> service.changeStatus("FEED_TYPE", 3109L, status("DISABLED", "合并", 9)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40902));
    }

    @Test
    void duplicateNameInSameScopeReturns40905() {
        DictItem existing = item(501L, "PEN_TYPE", "NURSERY", "保育舍", "ENABLED", false, false);
        when(dictItemMapper.selectList(any())).thenReturn(List.of(existing));
        DictItemScope scope = new DictItemScope();
        scope.setDictItemId(501L);
        scope.setScopeType("SPECIES");
        scope.setSpecies("PIG");
        scope.setCategoryId(0L);
        when(support.scopesByItem(any())).thenReturn(Map.of(501L, List.of(scope)));

        DictItemSaveRequest request = new DictItemSaveRequest();
        request.setName("保育舍");
        DictItemSaveRequest.Scope sc = new DictItemSaveRequest.Scope();
        sc.setScopeType("SPECIES");
        sc.setSpecies("PIG");
        request.setScopes(List.of(sc));
        assertThatThrownBy(() -> service.create("PEN_TYPE", request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40905))
                .hasMessage("已有“保育舍”");
    }

    @Test
    void symptomRequiresCategoryScope() {
        DictItemSaveRequest request = new DictItemSaveRequest();
        request.setName("新症状");
        when(categoryMapper.selectById(101L)).thenReturn(category(101L));
        assertThatThrownBy(() -> service.create("SYMPTOM", request)).hasMessage("请选择适用品类");
    }

    @Test
    void orderSavesSortNo() {
        DictItem first = item(501L, "PEN_TYPE", "NURSERY", "保育舍", "ENABLED", false, false);
        DictItem second = item(502L, "PEN_TYPE", "FATTENING", "育肥舍", "ENABLED", false, false);
        when(dictItemMapper.selectList(any())).thenReturn(List.of(first, second));
        ItemOrderRequest request = new ItemOrderRequest();
        request.setItemIds(List.of(502L, 501L));
        service.order("PEN_TYPE", request);
        verify(dictItemMapper, org.mockito.Mockito.times(2)).update(any(), any());
        verify(logs).record(org.mockito.ArgumentMatchers.argThat(entry -> "SORT".equals(entry.getOpAction())));
    }

    @Test
    void itemsFiltersBySpeciesAndCategory() {
        DictItem pig = item(501L, "PEN_TYPE", "NURSERY", "保育舍", "ENABLED", false, false);
        DictItem poultry = item(505L, "PEN_TYPE", "BROODING", "育雏舍", "ENABLED", false, false);
        when(dictItemMapper.selectList(any())).thenReturn(List.of(pig, poultry));
        Map<Long, List<DictItemScope>> scopes = new LinkedHashMap<>();
        scopes.put(501L, List.of(scope(501L, "SPECIES", "PIG", 0L)));
        scopes.put(505L, List.of(scope(505L, "SPECIES", "POULTRY", 0L)));
        when(support.scopesByItem(any())).thenReturn(scopes);
        when(categoryMapper.selectById(201L)).thenReturn(category(201L));

        DictItemQuery query = new DictItemQuery();
        query.setCategoryId(201L);
        assertThat(service.items("PEN_TYPE", query)).extracting("id").containsExactly(505L);
    }

    @Test
    void symptomOrderRequiresCategoryAndMatchingScope() {
        DictItem pig = item(401L, "SYMPTOM", "DIARRHEA", "腹泻", "ENABLED", false, false);
        DictItem poultry = item(413L, "SYMPTOM", "LOW_INTAKE", "减料", "ENABLED", false, false);
        when(dictItemMapper.selectList(any())).thenReturn(List.of(pig, poultry), List.of(pig), List.of(pig));
        Map<Long, List<DictItemScope>> scopes = new LinkedHashMap<>();
        scopes.put(401L, List.of(scope(401L, "CATEGORY", "", 101L)));
        scopes.put(413L, List.of(scope(413L, "CATEGORY", "", 201L)));
        when(support.scopesByItem(any())).thenReturn(scopes);

        ItemOrderRequest missing = new ItemOrderRequest();
        missing.setItemIds(List.of(401L, 413L));
        assertThatThrownBy(() -> service.order("SYMPTOM", missing)).hasMessage("请选择症状适用品类");

        ItemOrderRequest wrongCategory = new ItemOrderRequest();
        wrongCategory.setItemIds(List.of(401L));
        wrongCategory.setCategoryId(201L);
        assertThatThrownBy(() -> service.order("SYMPTOM", wrongCategory)).hasMessage("所选症状不属于该品类");

        ItemOrderRequest ok = new ItemOrderRequest();
        ok.setItemIds(List.of(401L));
        ok.setCategoryId(101L);
        service.order("SYMPTOM", ok);
        verify(dictItemMapper).update(any(), any());
    }

    private static DictStatusRequest status(String status, String reason, int version) {
        DictStatusRequest request = new DictStatusRequest();
        request.setStatus(status);
        request.setReason(reason);
        request.setVersion(version);
        return request;
    }

    private static DictItem item(Long id, String type, String code, String name, String status, boolean fallback,
                                 boolean system) {
        DictItem item = new DictItem();
        item.setId(id);
        item.setDictType(type);
        item.setCode(code);
        item.setName(name);
        item.setStatus(status);
        item.setIsFallback(fallback);
        item.setIsSystem(system);
        item.setSortNo(1);
        item.setVersion(1);
        return item;
    }

    private static DictItemScope scope(Long itemId, String type, String species, long categoryId) {
        DictItemScope scope = new DictItemScope();
        scope.setDictItemId(itemId);
        scope.setScopeType(type);
        scope.setSpecies(species);
        scope.setCategoryId(categoryId);
        return scope;
    }

    private static Category category(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setName(id == 101L ? "育肥猪" : "肉鸡");
        category.setSpecies(id == 101L ? "PIG" : "POULTRY");
        category.setStatus("ENABLED");
        return category;
    }
}
