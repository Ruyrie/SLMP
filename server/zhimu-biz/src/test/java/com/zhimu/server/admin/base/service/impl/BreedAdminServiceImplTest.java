package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.request.BreedSaveRequest;
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

/** D05/D06 关键分支：种畜必填妊娠期、禽类孵化期、待妊检天数、后代同畜种、名称重复、停用原因。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreedAdminServiceImplTest {

    @Mock private BreedMapper breedMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private BaseLogs logs;
    @Mock private ApplicationEventPublisher eventPublisher;

    private BreedAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new BreedAdminServiceImpl(breedMapper, categoryMapper, logs, eventPublisher);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        lenient().when(breedMapper.selectList(any())).thenReturn(List.of());
        lenient().when(breedMapper.update(any(), any())).thenReturn(1);
        lenient().when(categoryMapper.selectById(101L)).thenReturn(category(101L, "育肥猪", "PIG", false));
        lenient().when(categoryMapper.selectById(102L)).thenReturn(category(102L, "种猪", "PIG", true));
        lenient().when(categoryMapper.selectById(201L)).thenReturn(category(201L, "肉鸡", "POULTRY", false));
        lenient().when(categoryMapper.selectById(301L)).thenReturn(category(301L, "肉牛", "CATTLE", false));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void createBreedUnderBreedingCategoryRequiresGestation() {
        BreedSaveRequest request = request(102L, "长大二元");
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .hasMessage("请填写妊娠期");
    }

    @Test
    void poultryRequiresIncubationAndRejectsGestation() {
        BreedSaveRequest request = request(201L, "白羽肉鸡");
        assertThatThrownBy(() -> service.create(request)).hasMessage("请填写孵化期");
        request.setIncubationDays(21);
        request.setGestationDays(114);
        assertThatThrownBy(() -> service.create(request)).hasMessage("禽类不能填写妊娠期");
    }

    @Test
    void pregnancyCheckDaysMustBeLessThanGestation() {
        BreedSaveRequest request = request(102L, "长大二元");
        request.setGestationDays(114);
        request.setPregnancyCheckDays(114);
        assertThatThrownBy(() -> service.create(request)).hasMessage("待妊检天数必须小于妊娠期");
    }

    @Test
    void offspringMustBeSameSpecies() {
        BreedSaveRequest request = request(101L, "杜长大");
        request.setGestationDays(114);
        request.setOffspringBreedId(2101L);
        Breed poultry = new Breed();
        poultry.setId(2101L);
        poultry.setCategoryId(201L);
        poultry.setStatus("ENABLED");
        when(breedMapper.selectById(2101L)).thenReturn(poultry);
        assertThatThrownBy(() -> service.create(request)).hasMessage("后代默认品种须与本品种同畜种");
    }

    @Test
    void createDefaultsPregnancy21AndSaves() {
        doAnswer(invocation -> {
            Breed breed = invocation.getArgument(0);
            breed.setId(1201L);
            return 1;
        }).when(breedMapper).insert(any(Breed.class));
        BreedSaveRequest request = request(102L, "长大二元");
        request.setGestationDays(114);
        request.setDescription("长白 × 大白二元杂交母猪");
        var vo = service.create(request);
        assertThat(vo.getId()).isEqualTo(1201L);
        assertThat(vo.getPregnancyCheckDays()).isEqualTo(21);
        assertThat(vo.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void duplicateNameInCategoryReturns40905() {
        Breed existing = new Breed();
        existing.setId(1101L);
        existing.setCategoryId(101L);
        existing.setName("杜长大");
        when(breedMapper.selectList(any())).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(request(101L, "杜长大")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40905))
                .hasMessage("育肥猪下已有品种“杜长大”");
    }

    @Test
    void disableWithoutReasonReturns40004() {
        Breed existing = new Breed();
        existing.setId(1101L);
        existing.setCategoryId(101L);
        existing.setName("杜长大");
        existing.setStatus("ENABLED");
        existing.setVersion(2);
        when(breedMapper.selectById(1101L)).thenReturn(existing);
        BreedSaveRequest request = request(101L, "杜长大");
        request.setVersion(2);
        request.setStatus("DISABLED");
        assertThatThrownBy(() -> service.update(1101L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004))
                .hasMessage("请填写修改原因");
    }

    @Test
    void updateChangingGestationRequiresReasonAndVersion() {
        Breed existing = new Breed();
        existing.setId(1201L);
        existing.setCategoryId(102L);
        existing.setName("长大二元");
        existing.setStatus("ENABLED");
        existing.setGestationDays(114);
        existing.setPregnancyCheckDays(21);
        existing.setVersion(2);
        when(breedMapper.selectById(1201L)).thenReturn(existing);
        BreedSaveRequest request = request(102L, "长大二元");
        request.setGestationDays(115);
        request.setPregnancyCheckDays(21);
        request.setVersion(2);
        assertThatThrownBy(() -> service.update(1201L, request)).hasMessage("请填写修改原因");

        request.setChangeReason("核对品种资料");
        var vo = service.update(1201L, request);
        assertThat(vo.getGestationDays()).isEqualTo(115);
        assertThat(vo.getVersion()).isEqualTo(3);
    }

    private static BreedSaveRequest request(Long categoryId, String name) {
        BreedSaveRequest request = new BreedSaveRequest();
        request.setCategoryId(categoryId);
        request.setName(name);
        return request;
    }

    private static Category category(Long id, String name, String species, boolean breeding) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSpecies(species);
        category.setIsBreeding(breeding);
        category.setStatus("ENABLED");
        return category;
    }
}
