package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.InspectItem;
import com.zhimu.server.admin.base.entity.InspectTemplate;
import com.zhimu.server.admin.base.entity.InspectTemplateVersion;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.InspectItemMapper;
import com.zhimu.server.admin.base.mapper.InspectOptionMapper;
import com.zhimu.server.admin.base.mapper.InspectTemplateMapper;
import com.zhimu.server.admin.base.mapper.InspectTemplateVersionMapper;
import com.zhimu.server.admin.base.request.InspectItemSaveRequest;
import com.zhimu.server.admin.base.service.InspectTemplateAdminService;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** D39–D48 关键分支：版本冲突、删除线上项目 42207、结果校验 40001。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectTemplateAdminServiceImplTest {

    @Mock private InspectTemplateMapper templateMapper;
    @Mock private InspectTemplateVersionMapper versionMapper;
    @Mock private InspectItemMapper itemMapper;
    @Mock private InspectOptionMapper optionMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private BaseAdminReadMapper readMapper;
    @Mock private BaseTemplateSupport support;
    @Mock private BaseLogs logs;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InspectTemplateAdminServiceImpl service;
    private InspectTemplate template;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.base.AdminBaseTableInfo.ensure();
        service = new InspectTemplateAdminServiceImpl(templateMapper, versionMapper, itemMapper, optionMapper,
                categoryMapper, readMapper, support, logs, eventPublisher);
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
        template = new InspectTemplate();
        template.setId(11L);
        template.setCategoryId(101L);
        template.setName("育肥猪巡检模板");
        template.setOnlineVersionNo(2);
        template.setHasDraft(false);
        template.setVersion(2);
        when(templateMapper.selectById(11L)).thenReturn(template);
        when(templateMapper.update(any(), any())).thenReturn(1);
        when(support.dictNames(anyString())).thenReturn(Map.of());
        when(support.adminNames()).thenReturn(Map.of(1L, "周敏"));
        Category category = new Category();
        category.setId(101L);
        category.setName("育肥猪");
        category.setStatus("ENABLED");
        when(categoryMapper.selectById(101L)).thenReturn(category);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void deletePublishedItemWithoutDraftReturns42207() {
        InspectTemplateVersion online = version(1101L, 2);
        when(versionMapper.selectOne(any())).thenReturn(null, online);
        InspectItem onlineItem = new InspectItem();
        onlineItem.setId(101L);
        onlineItem.setVersionId(1101L);
        onlineItem.setChangeType("NONE");
        when(itemMapper.selectById(101L)).thenReturn(onlineItem);

        assertThatThrownBy(() -> service.deleteItem(11L, 101L, 2))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(42207))
                .hasMessage("已发布的项目不能删除，请停用");
    }

    @Test
    void addItemRejectsOptionsWithoutNormalFirst() {
        InspectItemSaveRequest request = itemRequest();
        InspectItemSaveRequest.Option abnormal = new InspectItemSaveRequest.Option();
        abnormal.setOptionName("沉郁");
        abnormal.setIsAbnormal(true);
        InspectItemSaveRequest.Option normal = new InspectItemSaveRequest.Option();
        normal.setOptionName("正常");
        normal.setIsAbnormal(false);
        request.setOptions(List.of(abnormal, normal));

        assertThatThrownBy(() -> service.addItem(11L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40001))
                .hasMessage("第一个结果须判定为正常");
    }

    @Test
    void addItemRejectsDuplicateName() {
        InspectTemplateVersion draft = version(1102L, null);
        draft.setDraftFlag(1);
        when(versionMapper.selectOne(any())).thenReturn(draft);
        InspectItem existing = new InspectItem();
        existing.setId(201L);
        existing.setVersionId(1102L);
        existing.setItemName("粪便");
        existing.setChangeType("NONE");
        when(itemMapper.selectList(any())).thenReturn(List.of(existing));
        when(optionMapper.selectList(any())).thenReturn(List.of());
        InspectItemSaveRequest request = itemRequest();
        request.setOptions(List.of(option("正常", false), option("水样", true)));

        assertThatThrownBy(() -> service.addItem(11L, request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40905))
                .hasMessage("已有项目“粪便”");
    }

    @Test
    void orderAcceptsOnlineItemIdsAfterDraftAutoCreated() {
        InspectTemplateVersion draft = version(1102L, null);
        draft.setDraftFlag(1);
        when(versionMapper.selectOne(any())).thenReturn(draft);
        InspectItem first = draftItem(301L, 101L, "精神状态", 1);
        InspectItem second = draftItem(302L, 102L, "采食", 2);
        when(itemMapper.selectList(any())).thenReturn(List.of(first, second));
        com.zhimu.server.admin.base.request.TemplateItemOrderRequest request =
                new com.zhimu.server.admin.base.request.TemplateItemOrderRequest();
        request.setItemIds(List.of(102L, 101L));
        request.setVersion(2);

        service.order(11L, request);

        assertThat(second.getSortNo()).isEqualTo(1);
        assertThat(first.getSortNo()).isEqualTo(2);
    }

    @Test
    void updateItemWithOnlineOptionIdsDeletesStaleCopiesBeforeInsert() {
        InspectTemplateVersion draft = version(1102L, null);
        draft.setDraftFlag(1);
        when(versionMapper.selectOne(any())).thenReturn(draft);
        InspectItem copy = draftItem(301L, 101L, "粪便", 1);
        when(itemMapper.selectById(101L)).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(copy);
        when(itemMapper.selectList(any())).thenReturn(List.of(copy));
        com.zhimu.server.admin.base.entity.InspectOption normalCopy = optionRow(401L, 301L, "正常", false);
        com.zhimu.server.admin.base.entity.InspectOption watery = optionRow(402L, 301L, "水样", true);
        when(optionMapper.selectList(any())).thenReturn(List.of(normalCopy, watery));
        when(optionMapper.selectById(1011L)).thenReturn(optionRow(1011L, 101L, "正常", false));
        when(optionMapper.selectById(1012L)).thenReturn(optionRow(1012L, 101L, "水样", true));
        InspectItemSaveRequest request = itemRequest();
        InspectItemSaveRequest.Option keepNormal = option("正常", false);
        keepNormal.setOptionId(1011L);
        InspectItemSaveRequest.Option keepWatery = option("水样", true);
        keepWatery.setOptionId(1012L);
        request.setOptions(List.of(keepNormal, keepWatery, option("带血", true)));

        service.updateItem(11L, 101L, request);

        // 线上选项 ID 按名称对应到草稿副本：两条原样保留，只新增“带血”，不会先插同名再删
        org.mockito.Mockito.verify(optionMapper, org.mockito.Mockito.never()).deleteById(any(Long.class));
        org.mockito.Mockito.verify(optionMapper, org.mockito.Mockito.times(1))
                .insert(any(com.zhimu.server.admin.base.entity.InspectOption.class));
    }

    private static InspectItem draftItem(Long id, Long sourceId, String name, int sort) {
        InspectItem item = new InspectItem();
        item.setId(id);
        item.setTemplateId(11L);
        item.setVersionId(1102L);
        item.setSourceItemId(sourceId);
        item.setItemName(name);
        item.setChangeType("NONE");
        item.setEnabled(true);
        item.setSortNo(sort);
        return item;
    }

    private static com.zhimu.server.admin.base.entity.InspectOption optionRow(Long id, Long itemId, String name,
                                                                               boolean abnormal) {
        com.zhimu.server.admin.base.entity.InspectOption option = new com.zhimu.server.admin.base.entity.InspectOption();
        option.setId(id);
        option.setItemId(itemId);
        option.setOptionName(name);
        option.setIsAbnormal(abnormal);
        return option;
    }

    private static InspectItemSaveRequest itemRequest() {
        InspectItemSaveRequest request = new InspectItemSaveRequest();
        request.setItemName("粪便");
        request.setCheckNote("看栏内粪便形状和颜色");
        request.setEnabled(true);
        request.setVersion(2);
        return request;
    }

    private static InspectItemSaveRequest.Option option(String name, boolean abnormal) {
        InspectItemSaveRequest.Option option = new InspectItemSaveRequest.Option();
        option.setOptionName(name);
        option.setIsAbnormal(abnormal);
        return option;
    }

    private static InspectTemplateVersion version(Long id, Integer versionNo) {
        InspectTemplateVersion version = new InspectTemplateVersion();
        version.setId(id);
        version.setTemplateId(11L);
        version.setVersionNo(versionNo);
        version.setStatus(versionNo == null ? "DRAFT" : "PUBLISHED");
        version.setVersion(0);
        return version;
    }
}
