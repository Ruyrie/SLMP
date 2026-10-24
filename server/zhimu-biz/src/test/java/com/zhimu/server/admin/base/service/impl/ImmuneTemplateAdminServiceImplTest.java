package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.ImmuneTemplate;
import com.zhimu.server.admin.base.entity.ImmuneTemplateItem;
import com.zhimu.server.admin.base.entity.ImmuneTemplateVersion;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateItemMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateVersionMapper;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.service.ImmuneTemplateAdminService;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
import com.zhimu.server.admin.base.vo.TemplateVersionVO;
import com.zhimu.server.admin.base.vo.ImmuneItemVO;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D14–D23 关键分支：版本冲突、无草稿、发布生成新版本并置旧版历史。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImmuneTemplateAdminServiceImplTest {

    @Mock private ImmuneTemplateMapper templateMapper;
    @Mock private ImmuneTemplateVersionMapper versionMapper;
    @Mock private ImmuneTemplateItemMapper itemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private BreedMapper breedMapper;
    @Mock private DrugCatalogMapper drugCatalogMapper;
    @Mock private BaseAdminReadMapper readMapper;
    @Mock private BaseTemplateSupport support;
    @Mock private BaseLogs logs;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ImmuneTemplateAdminServiceImpl service;
    private ImmuneTemplate template;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new ImmuneTemplateAdminServiceImpl(templateMapper, versionMapper, itemMapper, categoryMapper,
                breedMapper, drugCatalogMapper, readMapper, support, logs, eventPublisher);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        template = new ImmuneTemplate();
        template.setId(21L);
        template.setBreedId(1101L);
        template.setName("杜长大育肥猪");
        template.setOnlineVersionNo(4);
        template.setHasDraft(true);
        template.setVersion(5);
        when(templateMapper.selectById(21L)).thenReturn(template);
        when(templateMapper.update(any(), any())).thenReturn(1);
        Breed breed = new Breed();
        breed.setId(1101L);
        breed.setCategoryId(101L);
        breed.setName("杜长大");
        when(breedMapper.selectById(1101L)).thenReturn(breed);
        when(support.speciesOfBreed(1101L)).thenReturn("PIG");
        when(support.adminNames()).thenReturn(Map.of(1L, "周敏"));
        when(support.dictNames(anyString())).thenReturn(Map.of());
        when(drugCatalogMapper.selectList(any())).thenReturn(List.of());
        lenient().when(itemMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void publishWithoutDraftReturns40903() {
        when(versionMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.publish(21L, publishRequest(5)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40903))
                .hasMessage("没有未发布的修改，请刷新");
    }

    @Test
    void publishWithStaleVersionReturns40902() {
        assertThatThrownBy(() -> service.publish(21L, publishRequest(4)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40902))
                .hasMessage("内容已更新，请刷新后再修改");
    }

    @Test
    void publishCreatesNextVersionAndMarksOldPublishedSuperseded() {
        ImmuneTemplateVersion draft = version(211L, null, "DRAFT", 1);
        ImmuneTemplateVersion online = version(210L, 4, "PUBLISHED", null);
        ImmuneTemplateVersion published = version(211L, 5, "PUBLISHED", null);
        published.setItemCount(1);
        when(versionMapper.selectOne(any())).thenReturn(draft, online, online);
        when(versionMapper.selectById(211L)).thenReturn(published);
        ImmuneTemplateItem item = item();
        when(itemMapper.selectList(any())).thenReturn(List.of(item), List.of(item));

        TemplateVersionVO<ImmuneItemVO> result = service.publish(21L, publishRequest(5));

        assertThat(result.getVersionNo()).isEqualTo(5);
        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getPublishedByName()).isEqualTo("周敏");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getVaccineName()).isNull();
        assertThat(template.getOnlineVersionNo()).isEqualTo(5);
        assertThat(template.getHasDraft()).isFalse();
        assertThat(template.getVersion()).isEqualTo(6);
        verify(versionMapper, org.mockito.Mockito.times(2)).update(any(), any());
        verify(eventPublisher).publishEvent(any(com.zhimu.server.admin.base.event.ImmuneTemplatePublishedEvent.class));
    }

    @Test
    void publishRequiresChangeNote() {
        TemplatePublishRequest request = publishRequest(5);
        request.setChangeNote("  ");
        assertThatThrownBy(() -> service.publish(21L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40004))
                .hasMessage("请填写修改说明");
    }

    private static TemplatePublishRequest publishRequest(int version) {
        TemplatePublishRequest request = new TemplatePublishRequest();
        request.setVersion(version);
        request.setChangeNote("按新版免疫程序建议调整");
        return request;
    }

    private static ImmuneTemplateVersion version(Long id, Integer versionNo, String status, Integer draftFlag) {
        ImmuneTemplateVersion version = new ImmuneTemplateVersion();
        version.setId(id);
        version.setTemplateId(21L);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setDraftFlag(draftFlag);
        version.setVersion(0);
        return version;
    }

    private static ImmuneTemplateItem item() {
        ImmuneTemplateItem item = new ImmuneTemplateItem();
        item.setId(2101L);
        item.setTemplateId(21L);
        item.setVersionId(211L);
        item.setChangeType("NONE");
        item.setSortNo(1);
        item.setVaccineId(7001L);
        item.setDoseRound("首免");
        item.setScheduleType("BY_DAY_AGE");
        item.setDayAge(25);
        item.setDose(new BigDecimal("1.00"));
        item.setDoseUnit("DOSE_PER_HEAD");
        item.setVaccinateMethodCode("IM");
        return item;
    }
}
