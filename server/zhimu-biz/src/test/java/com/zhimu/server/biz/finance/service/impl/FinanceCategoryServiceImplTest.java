package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.finance.entity.ExpenseSubcategory;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.mapper.ExpenseSubcategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 收支分类与子类口径：疑似重复只查自动来源分类、子类同分类内不重名、跨场子类不可见。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceCategoryServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Long FARM_ID = 1001L;

    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private ExpenseSubcategoryMapper subcategoryMapper;
    @Mock private FinanceRecordMapper recordMapper;
    @Mock private FinanceSupport support;

    private FinanceCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FinanceCategoryServiceImpl(categoryMapper, subcategoryMapper, recordMapper, support);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), ZONE));
        lenient().when(support.farmId()).thenReturn(FARM_ID);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void duplicateCheckReportsAutoSourceRecord() {
        when(categoryMapper.selectById(101L)).thenReturn(category(101L, "FEED", "饲料"));
        FinanceRecord hit = new FinanceRecord();
        hit.setId(8205L);
        hit.setFarmId(FARM_ID);
        hit.setCategoryId(101L);
        hit.setAmount(new BigDecimal("3200.00"));
        hit.setRecordDate(LocalDate.of(2026, 10, 19));
        hit.setSourceType("FEED_INBOUND");
        when(recordMapper.selectOne(any())).thenReturn(hit);

        FinanceRecordVos.DuplicateCheckVO vo = service.duplicateCheck("EXPENSE", 101L,
                new BigDecimal("3200"), LocalDate.of(2026, 10, 19), null);

        assertThat(vo.getHit()).isTrue();
        assertThat(vo.getRecordId()).isEqualTo(8205L);
        assertThat(vo.getCategoryName()).isEqualTo("饲料");
        assertThat(vo.getAmount()).isEqualByComparingTo("3200.00");
        assertThat(vo.getRecordDate()).isEqualTo("2026-10-19");
        assertThat(vo.getSourceLabel()).isEqualTo("来自饲料入库单");
    }

    @Test
    void duplicateCheckSkipsCategoriesWithoutAutoSource() {
        when(categoryMapper.selectById(107L)).thenReturn(category(107L, "UTILITY", "水电"));

        FinanceRecordVos.DuplicateCheckVO vo = service.duplicateCheck("EXPENSE", 107L,
                new BigDecimal("3200"), LocalDate.of(2026, 10, 19), null);

        assertThat(vo.getHit()).isFalse();
        assertThat(vo.getRecordId()).isNull();
    }

    @Test
    void duplicateCheckWithoutCategoryIsNotAHit() {
        assertThat(service.duplicateCheck("EXPENSE", null, new BigDecimal("3200"),
                LocalDate.of(2026, 10, 19), null).getHit()).isFalse();
    }

    @Test
    void addRejectsDuplicateNameInSameCategory() {
        when(categoryMapper.selectById(101L)).thenReturn(category(101L, "FEED", "饲料"));
        ExpenseSubcategory exists = subcategory(12L, 101L, FARM_ID, "玉米");
        when(subcategoryMapper.selectOne(any())).thenReturn(exists);

        FinanceRequests.SubcategoryRequest request = new FinanceRequests.SubcategoryRequest();
        request.setCategoryId(101L);
        request.setName("玉米");

        assertThatThrownBy(() -> service.add(request, "idem-sub-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("饲料下已有“玉米”")
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40905);
    }

    @Test
    void addTrimsNameAndInserts() {
        when(categoryMapper.selectById(101L)).thenReturn(category(101L, "FEED", "饲料"));
        when(subcategoryMapper.selectOne(any())).thenReturn(null);
        when(subcategoryMapper.selectCount(any())).thenReturn(3L);
        when(subcategoryMapper.insert(any(ExpenseSubcategory.class))).thenReturn(1);

        FinanceRequests.SubcategoryRequest request = new FinanceRequests.SubcategoryRequest();
        request.setCategoryId(101L);
        request.setName("  预混料  ");

        FinanceRecordVos.SubcategoryVO vo = service.add(request, "idem-sub-1");

        assertThat(vo.getName()).isEqualTo("预混料");
        assertThat(vo.getCreatedAt()).isNull();
    }

    @Test
    void addRejectsBlankNameWithFieldError() {
        when(categoryMapper.selectById(101L)).thenReturn(category(101L, "FEED", "饲料"));

        FinanceRequests.SubcategoryRequest request = new FinanceRequests.SubcategoryRequest();
        request.setCategoryId(101L);
        request.setName("   ");

        assertThatThrownBy(() -> service.add(request, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请输入子类名称");
    }

    @Test
    void renameRejectsSubcategoryOfAnotherFarm() {
        when(subcategoryMapper.selectById(12L)).thenReturn(subcategory(12L, 101L, 2002L, "玉米"));

        FinanceRequests.SubcategoryRequest request = new FinanceRequests.SubcategoryRequest();
        request.setName("玉米A");

        assertThatThrownBy(() -> service.rename(12L, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40401);
    }

    @Test
    void renameUpdatesOwnSubcategory() {
        when(subcategoryMapper.selectById(12L)).thenReturn(subcategory(12L, 101L, FARM_ID, "玉米"));
        when(categoryMapper.selectById(101L)).thenReturn(category(101L, "FEED", "饲料"));
        when(subcategoryMapper.selectOne(any())).thenReturn(null);
        when(subcategoryMapper.updateById(any(ExpenseSubcategory.class))).thenReturn(1);

        FinanceRequests.SubcategoryRequest request = new FinanceRequests.SubcategoryRequest();
        request.setName("玉米A");

        assertThat(service.rename(12L, request).getName()).isEqualTo("玉米A");
    }

    @Test
    void deleteMarksDeletedAtAndDeletesRow() {
        ExpenseSubcategory sub = subcategory(12L, 101L, FARM_ID, "玉米");
        when(subcategoryMapper.selectById(12L)).thenReturn(sub);
        when(subcategoryMapper.updateById(any(ExpenseSubcategory.class))).thenReturn(1);

        service.delete(12L);

        assertThat(sub.getDeletedAt()).isNotNull();
        verify(subcategoryMapper).deleteById(12L);
    }

    @Test
    void listGroupsSubcategoriesUnderTheirCategory() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(101L, "FEED", "饲料"), category(103L, "SALE", "卖猪")));
        when(subcategoryMapper.selectList(any())).thenReturn(List.of(subcategory(12L, 101L, FARM_ID, "预混料")));

        List<FinanceRecordVos.CategoryVO> vos = service.list("EXPENSE");

        assertThat(vos).hasSize(2);
        assertThat(vos.get(0).getId()).isEqualTo(101L);
        assertThat(vos.get(0).getAutoSource()).isTrue();
        assertThat(vos.get(0).getMarkOnly()).isFalse();
        assertThat(vos.get(0).getSubcategories()).extracting(FinanceRecordVos.SubcategoryVO::getName)
                .containsExactly("预混料");
        assertThat(vos.get(1).getSubcategories()).isEmpty();
    }

    private static FinanceCategory category(Long id, String code, String name) {
        FinanceCategory category = new FinanceCategory();
        category.setId(id);
        category.setCode(code);
        category.setName(name);
        category.setDirection("EXPENSE");
        category.setIcon("add_box");
        category.setSortNo(1);
        category.setStatus("ENABLED");
        category.setAutoSource("FEED".equals(code) || "SALE".equals(code) ? "FEED_INBOUND" : null);
        category.setMarkOnly(false);
        category.setIsFallback(false);
        return category;
    }

    private static ExpenseSubcategory subcategory(Long id, Long categoryId, Long farmId, String name) {
        ExpenseSubcategory sub = new ExpenseSubcategory();
        sub.setId(id);
        sub.setCategoryId(categoryId);
        sub.setFarmId(farmId);
        sub.setName(name);
        return sub;
    }
}
