package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.request.CategorySaveRequest;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** D02/D03 关键分支：名称重复、版本冲突、已有品种不能改畜种、停用原因必填。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryAdminServiceImplTest {

    @Mock private CategoryMapper categoryMapper;
    @Mock private BreedMapper breedMapper;
    @Mock private BaseLogs logs;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CategoryAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new CategoryAdminServiceImpl(categoryMapper, breedMapper, logs, eventPublisher);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        lenient().when(categoryMapper.selectList(any())).thenReturn(List.of());
        lenient().when(categoryMapper.update(any(), any())).thenReturn(1);
        lenient().when(breedMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void createDerivesCountUnitAndDefaultsEnabled() {
        doAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(101L);
            return 1;
        }).when(categoryMapper).insert(any(Category.class));
        CategorySaveRequest request = request("育肥猪", "PIG");
        var vo = service.create(request);
        assertThat(vo.getId()).isEqualTo(101L);
        assertThat(vo.getCountUnit()).isEqualTo("HEAD");
        assertThat(vo.getStatus()).isEqualTo("ENABLED");
        assertThat(vo.getIsBreeding()).isFalse();
    }

    @Test
    void poultryDerivesBirdAndCannotBeBreeding() {
        doAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(201L);
            return 1;
        }).when(categoryMapper).insert(any(Category.class));
        assertThat(service.create(request("肉鸡", "POULTRY")).getCountUnit()).isEqualTo("BIRD");

        CategorySaveRequest breeding = request("种鸡", "POULTRY");
        breeding.setIsBreeding(true);
        assertThatThrownBy(() -> service.create(breeding))
                .isInstanceOf(BizException.class)
                .hasMessage("禽类不能设为种畜品类");
    }

    @Test
    void duplicateNameReturns40905() {
        Category existing = category(1L, "育肥猪", "PIG");
        when(categoryMapper.selectList(any())).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(request("育肥猪", "PIG")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    assertThat(((BizException) e).getCode()).isEqualTo(40905);
                    assertThat(e).hasMessage("已有品类“育肥猪”");
                });
    }

    @Test
    void updateVersionMismatchReturns40902() {
        when(categoryMapper.selectById(101L)).thenReturn(category(101L, "育肥猪", "PIG"));
        CategorySaveRequest request = request("育肥猪", "PIG");
        request.setVersion(99);
        assertThatThrownBy(() -> service.update(101L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40902));
    }

    @Test
    void cannotChangeSpeciesWhenBreedsExist() {
        Category existing = category(101L, "育肥猪", "PIG");
        existing.setVersion(2);
        when(categoryMapper.selectById(101L)).thenReturn(existing);
        when(breedMapper.selectCount(any())).thenReturn(1L);
        CategorySaveRequest request = request("育肥猪", "POULTRY");
        request.setVersion(2);
        assertThatThrownBy(() -> service.update(101L, request))
                .isInstanceOf(BizException.class)
                .hasMessage("该品类已有品种，不能修改畜种");
    }

    @Test
    void disableWithoutReasonReturns40004() {
        Category existing = category(101L, "育肥猪", "PIG");
        existing.setVersion(2);
        when(categoryMapper.selectById(101L)).thenReturn(existing);
        CategorySaveRequest request = request("育肥猪", "PIG");
        request.setVersion(2);
        request.setStatus("DISABLED");
        assertThatThrownBy(() -> service.update(101L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004));
    }

    private static CategorySaveRequest request(String name, String species) {
        CategorySaveRequest request = new CategorySaveRequest();
        request.setName(name);
        request.setSpecies(species);
        return request;
    }

    private static Category category(Long id, String name, String species) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSpecies(species);
        category.setCountUnit("PIG".equals(species) ? "HEAD" : "BIRD");
        category.setStatus("ENABLED");
        category.setSortNo(1);
        category.setVersion(1);
        return category;
    }
}
