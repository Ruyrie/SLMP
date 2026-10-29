package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.TemplateAdminConvert;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.InspectItem;
import com.zhimu.server.admin.base.entity.InspectOption;
import com.zhimu.server.admin.base.entity.InspectTemplate;
import com.zhimu.server.admin.base.entity.InspectTemplateVersion;
import com.zhimu.server.admin.base.event.InspectTemplatePublishedEvent;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.InspectItemMapper;
import com.zhimu.server.admin.base.mapper.InspectOptionMapper;
import com.zhimu.server.admin.base.mapper.InspectTemplateMapper;
import com.zhimu.server.admin.base.mapper.InspectTemplateVersionMapper;
import com.zhimu.server.admin.base.request.InspectItemSaveRequest;
import com.zhimu.server.admin.base.request.InspectTemplateCreateRequest;
import com.zhimu.server.admin.base.request.TemplateItemOrderRequest;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.request.TemplateRollbackRequest;
import com.zhimu.server.admin.base.service.InspectTemplateAdminService;
import com.zhimu.server.admin.base.support.BaseAdminTexts;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
import com.zhimu.server.admin.base.vo.IdNameVO;
import com.zhimu.server.admin.base.vo.InspectDraftChangeVO;
import com.zhimu.server.admin.base.vo.InspectItemVO;
import com.zhimu.server.admin.base.vo.InspectOptionVO;
import com.zhimu.server.admin.base.vo.InspectTemplateListVO;
import com.zhimu.server.admin.base.vo.InspectTemplateVO;
import com.zhimu.server.admin.base.vo.TemplateVersionVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-D39–D48 巡检项目模板。 */
@Service
@RequiredArgsConstructor
public class InspectTemplateAdminServiceImpl implements InspectTemplateAdminService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String SUPERSEDED = "SUPERSEDED";

    private final InspectTemplateMapper templateMapper;
    private final InspectTemplateVersionMapper versionMapper;
    private final InspectItemMapper itemMapper;
    private final InspectOptionMapper optionMapper;
    private final CategoryMapper categoryMapper;
    private final BaseAdminReadMapper readMapper;
    private final BaseTemplateSupport support;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<InspectTemplateListVO> list() {
        List<Category> categories = new ArrayList<>(categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getStatus, "ENABLED")
                .orderByAsc(Category::getSortNo).orderByAsc(Category::getId)));
        categories.sort(java.util.Comparator.comparing(Category::getSortNo,
                        java.util.Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Category::getId));
        Map<Long, InspectTemplate> byCategory = new HashMap<>();
        for (InspectTemplate template : templateMapper.selectList(Wrappers.lambdaQuery())) {
            byCategory.put(template.getCategoryId(), template);
        }
        List<InspectTemplateListVO> result = new ArrayList<>();
        for (Category category : categories) {
            InspectTemplate template = byCategory.get(category.getId());
            InspectTemplateListVO vo = new InspectTemplateListVO();
            vo.setCategoryId(category.getId());
            vo.setCategoryName(category.getName());
            if (template != null) {
                vo.setTemplateId(template.getId());
                vo.setTemplateName(template.getName());
                vo.setOnlineVersionNo(template.getOnlineVersionNo());
                vo.setHasDraft(Boolean.TRUE.equals(template.getHasDraft()));
                vo.setItemCount(itemCount(template));
            } else {
                vo.setItemCount(0);
                vo.setHasDraft(false);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public InspectTemplateVO detail(Long templateId) {
        return detail(requireTemplate(templateId));
    }

    @Override
    @Transactional
    public InspectTemplateVO create(InspectTemplateCreateRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Category category = support.requireEnabledCategory(request.getCategoryId());
        InspectTemplate existing = templateMapper.selectOne(Wrappers.<InspectTemplate>lambdaQuery()
                .eq(InspectTemplate::getCategoryId, category.getId()).last("LIMIT 1"));
        if (existing != null) {
            throw BaseErrors.duplicate("该品类已有巡检模板");
        }
        String name = StringUtils.hasText(request.getName())
                ? request.getName().trim() : category.getName() + "巡检模板";
        if (name.length() > 20) {
            throw BaseErrors.field("name", "模板名称最多 20 个字");
        }
        LocalDateTime now = BeijingTime.now();
        InspectTemplate template = new InspectTemplate();
        template.setCategoryId(category.getId());
        template.setName(name);
        template.setHasDraft(true);
        template.setUpdatedBy(adminId);
        template.setVersion(0);
        templateMapper.insert(template);
        newDraft(template.getId(), adminId, now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("CREATE").bizType("INSPECT_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(category.getName() + " " + template.getName())
                .after(Map.of("category_id", category.getId(), "name", template.getName()))
                .build());
        return detail(template);
    }

    @Override
    @Transactional
    public InspectItemVO addItem(Long templateId, InspectItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        validateItem(request);
        InspectTemplateVersion draft = createDraftIfNeeded(template, adminId);
        List<InspectItem> items = items(draft.getId());
        InspectItem same = findByName(items, request.getItemName().trim(), null);
        if (same != null && !"DELETED".equals(same.getChangeType())) {
            throw BaseErrors.duplicate("已有项目“" + request.getItemName().trim() + "”");
        }
        LocalDateTime now = BeijingTime.now();
        InspectItem item;
        if (same != null) {
            item = same;
            applyItem(item, request);
            replaceOptions(item, request.getOptions(), adminId);
            InspectItem source = sourceItem(same, publishedVersion(templateId));
            item.setChangeType(source == null ? "ADDED" : sameAsSource(item, source, options(item.getId()))
                    ? "NONE" : "MODIFIED");
            item.setUpdatedAt(now);
            itemMapper.updateById(item);
        } else {
            item = new InspectItem();
            item.setTemplateId(template.getId());
            item.setVersionId(draft.getId());
            item.setSourceItemId(null);
            item.setChangeType("ADDED");
            item.setSortNo(nextSortNo(items));
            applyItem(item, request);
            item.setCreatedBy(adminId);
            itemMapper.insert(item);
            replaceOptions(item, request.getOptions(), adminId);
        }
        refreshDraftItemCount(draft);
        touchDraft(template, adminId, now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("DRAFT_SAVE").bizType("INSPECT_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(item.getItemName())
                .after(Map.of("action", "ADD_ITEM", "item_id", item.getId()))
                .build());
        return itemVO(item);
    }

    @Override
    @Transactional
    public InspectItemVO updateItem(Long templateId, Long itemId, InspectItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        validateItem(request);
        InspectTemplateVersion draft = createDraftIfNeeded(template, adminId);
        InspectItem item = locateDraftItem(draft, itemId);
        if (item == null || "DELETED".equals(item.getChangeType())) {
            throw BaseErrors.notFound();
        }
        List<InspectItem> items = items(draft.getId());
        InspectItem same = findByName(items, request.getItemName().trim(), item.getId());
        if (same != null) {
            throw BaseErrors.duplicate("已有项目“" + request.getItemName().trim() + "”");
        }
        applyItem(item, request);
        replaceOptions(item, request.getOptions(), adminId);
        InspectItem source = sourceItem(item, publishedVersion(templateId));
        item.setChangeType(source == null ? "ADDED" : sameAsSource(item, source, options(item.getId()))
                ? "NONE" : "MODIFIED");
        item.setUpdatedAt(BeijingTime.now());
        itemMapper.updateById(item);
        refreshDraftItemCount(draft);
        touchDraft(template, adminId, BeijingTime.now());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("DRAFT_SAVE").bizType("INSPECT_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(item.getItemName())
                .after(Map.of("action", "UPDATE_ITEM", "item_id", item.getId()))
                .build());
        return itemVO(item);
    }

    @Override
    @Transactional
    public void deleteItem(Long templateId, Long itemId, Integer version) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(version, template.getVersion());
        InspectTemplateVersion draft = draftVersion(templateId);
        if (draft == null) {
            InspectTemplateVersion online = publishedVersion(templateId);
            InspectItem onlineItem = itemId == null ? null : itemMapper.selectById(itemId);
            if (onlineItem != null && online != null && online.getId().equals(onlineItem.getVersionId())) {
                throw new com.zhimu.server.common.error.BizException(
                        com.zhimu.server.common.error.ErrorCode.STATE_NOT_ALLOWED,
                        "已发布的项目不能删除，请停用");
            }
            throw BaseErrors.notFound();
        }
        InspectItem item = locateDraftItem(draft, itemId);
        if (item == null || "DELETED".equals(item.getChangeType())) {
            throw BaseErrors.notFound();
        }
        if (!"ADDED".equals(item.getChangeType())) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.STATE_NOT_ALLOWED,
                    "已发布的项目不能删除，请停用");
        }
        optionMapper.delete(Wrappers.<InspectOption>lambdaQuery().eq(InspectOption::getItemId, item.getId()));
        itemMapper.deleteById(item.getId());
        refreshDraftItemCount(draft);
        touchDraft(template, adminId, BeijingTime.now());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("DRAFT_SAVE").bizType("INSPECT_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(item.getItemName())
                .after(Map.of("action", "DELETE_ITEM", "item_id", itemId))
                .build());
    }

    @Override
    @Transactional
    public void order(Long templateId, TemplateItemOrderRequest request) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        InspectTemplateVersion draft = createDraftIfNeeded(template, adminId);
        List<InspectItem> items = items(draft.getId()).stream()
                .filter(item -> !"DELETED".equals(item.getChangeType())).toList();
        Map<Long, InspectItem> byId = new HashMap<>();
        Map<Long, InspectItem> bySource = new HashMap<>();
        for (InspectItem item : items) {
            byId.put(item.getId(), item);
            if (item.getSourceItemId() != null) {
                bySource.put(item.getSourceItemId(), item);
            }
        }
        // 没有草稿时页面拿的是线上项目 ID，自动建草稿后按 source_item_id 换成草稿项目
        List<InspectItem> ordered = new ArrayList<>();
        Set<Long> resolved = new HashSet<>();
        for (Long id : request.getItemIds()) {
            InspectItem item = id == null ? null : byId.getOrDefault(id, bySource.get(id));
            if (item == null || !resolved.add(item.getId())) {
                throw BaseErrors.field("item_ids", "请提交草稿全部未删除项目");
            }
            ordered.add(item);
        }
        if (resolved.size() != items.size()) {
            throw BaseErrors.field("item_ids", "请提交草稿全部未删除项目");
        }
        int sort = 1;
        for (InspectItem item : ordered) {
            item.setSortNo(sort++);
            item.setUpdatedAt(BeijingTime.now());
            itemMapper.updateById(item);
        }
        touchDraft(template, adminId, BeijingTime.now());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("DRAFT_SAVE").bizType("INSPECT_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .after(Map.of("action", "SORT", "item_ids", request.getItemIds()))
                .build());
    }

    @Override
    @Transactional
    public TemplateVersionVO<InspectItemVO> publish(Long templateId, TemplatePublishRequest request) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        if (!StringUtils.hasText(request.getChangeNote())) {
            throw BaseErrors.reasonEmpty("请填写修改说明");
        }
        InspectTemplateVersion draft = draftVersion(templateId);
        if (draft == null) {
            throw BaseErrors.stateChanged("没有未发布的修改，请刷新");
        }
        List<InspectItem> items = items(draft.getId());
        List<InspectItem> active = items.stream()
                .filter(item -> !"DELETED".equals(item.getChangeType())).toList();
        if (active.isEmpty()) {
            throw BaseErrors.param("请至少添加 1 个巡检项目");
        }
        for (InspectItem item : active) {
            List<InspectOption> options = options(item.getId());
            validatePersistedOptions(item, options);
        }
        for (InspectItem deleted : items) {
            if ("DELETED".equals(deleted.getChangeType())) {
                optionMapper.delete(Wrappers.<InspectOption>lambdaQuery()
                        .eq(InspectOption::getItemId, deleted.getId()));
                itemMapper.deleteById(deleted.getId());
            }
        }
        LocalDateTime now = BeijingTime.now();
        int versionNo = nextVersionNo(templateId);
        versionMapper.update(null, Wrappers.<InspectTemplateVersion>lambdaUpdate()
                .eq(InspectTemplateVersion::getId, draft.getId())
                .set(InspectTemplateVersion::getVersionNo, versionNo)
                .set(InspectTemplateVersion::getStatus, PUBLISHED)
                .set(InspectTemplateVersion::getDraftFlag, null)
                .set(InspectTemplateVersion::getChangeNote, request.getChangeNote().trim())
                .set(InspectTemplateVersion::getItemCount, active.size())
                .set(InspectTemplateVersion::getPublishedBy, adminId)
                .set(InspectTemplateVersion::getPublishedAt, now)
                .set(InspectTemplateVersion::getUpdatedBy, adminId)
                .set(InspectTemplateVersion::getUpdatedAt, now)
                .setSql("version = version + 1"));
        versionMapper.update(null, Wrappers.<InspectTemplateVersion>lambdaUpdate()
                .eq(InspectTemplateVersion::getTemplateId, templateId)
                .eq(InspectTemplateVersion::getStatus, PUBLISHED)
                .ne(InspectTemplateVersion::getId, draft.getId())
                .set(InspectTemplateVersion::getStatus, SUPERSEDED)
                .set(InspectTemplateVersion::getUpdatedBy, adminId)
                .set(InspectTemplateVersion::getUpdatedAt, now)
                .setSql("version = version + 1"));
        int rows = templateMapper.update(null, Wrappers.<InspectTemplate>lambdaUpdate()
                .eq(InspectTemplate::getId, templateId)
                .eq(InspectTemplate::getVersion, template.getVersion())
                .set(InspectTemplate::getOnlineVersionNo, versionNo)
                .set(InspectTemplate::getHasDraft, false)
                .set(InspectTemplate::getUpdatedBy, adminId)
                .set(InspectTemplate::getUpdatedAt, now)
                .set(InspectTemplate::getVersion, template.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        template.setOnlineVersionNo(versionNo);
        template.setHasDraft(false);
        template.setVersion(template.getVersion() + 1);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("PUBLISH").bizType("INSPECT_TEMPLATE")
                .bizId(templateId).targetName(template.getName()).reason(request.getChangeNote().trim())
                .after(Map.of("version_no", versionNo, "item_count", active.size()))
                .build());
        AfterCommit.run(() -> eventPublisher.publishEvent(
                new InspectTemplatePublishedEvent(templateId, draft.getId(), versionNo)));
        List<InspectItemVO> itemVOs = fillItems(active);
        TemplateVersionVO<InspectItemVO> vo = versionVO(versionMapper.selectById(draft.getId()), itemVOs);
        vo.setPublishedByName(support.adminNames().get(adminId));
        return vo;
    }

    @Override
    @Transactional
    public void discard(Long templateId, Integer version) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(version, template.getVersion());
        InspectTemplateVersion draft = draftVersion(templateId);
        if (draft == null) {
            throw BaseErrors.stateChanged("没有未发布的修改，请刷新");
        }
        for (InspectItem item : items(draft.getId())) {
            optionMapper.delete(Wrappers.<InspectOption>lambdaQuery().eq(InspectOption::getItemId, item.getId()));
        }
        itemMapper.delete(Wrappers.<InspectItem>lambdaQuery()
                .eq(InspectItem::getVersionId, draft.getId()));
        versionMapper.deleteById(draft.getId());
        touchDraft(template, adminId, BeijingTime.now(), false);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("DISCARD").bizType("INSPECT_TEMPLATE")
                .bizId(templateId).targetName(template.getName()).build());
    }

    @Override
    public List<TemplateVersionVO<InspectItemVO>> versions(Long templateId) {
        requireTemplate(templateId);
        Map<Long, String> adminNames = support.adminNames();
        List<TemplateVersionVO<InspectItemVO>> result = new ArrayList<>();
        for (InspectTemplateVersion version : versionMapper.selectList(Wrappers.<InspectTemplateVersion>lambdaQuery()
                .eq(InspectTemplateVersion::getTemplateId, templateId)
                .isNotNull(InspectTemplateVersion::getVersionNo)
                .orderByDesc(InspectTemplateVersion::getVersionNo))) {
            TemplateVersionVO<InspectItemVO> vo = versionVO(version, fillItems(items(version.getId())));
            vo.setPublishedByName(version.getPublishedBy() == null ? null
                    : adminNames.get(version.getPublishedBy()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public InspectTemplateVO rollback(Long templateId, TemplateRollbackRequest request) {
        Long adminId = AdminContext.requireAdminId();
        InspectTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        InspectTemplateVersion target = versionMapper.selectOne(Wrappers.<InspectTemplateVersion>lambdaQuery()
                .eq(InspectTemplateVersion::getTemplateId, templateId)
                .eq(InspectTemplateVersion::getVersionNo, request.getTargetVersionNo())
                .last("LIMIT 1"));
        if (target == null) {
            throw BaseErrors.notFound();
        }
        if (Objects.equals(template.getOnlineVersionNo(), target.getVersionNo())) {
            throw BaseErrors.stateChanged("当前已是第 " + target.getVersionNo() + " 版，不需要回退");
        }
        InspectTemplateVersion draft = draftVersion(templateId);
        if (draft != null) {
            for (InspectItem item : items(draft.getId())) {
                optionMapper.delete(Wrappers.<InspectOption>lambdaQuery()
                        .eq(InspectOption::getItemId, item.getId()));
            }
            itemMapper.delete(Wrappers.<InspectItem>lambdaQuery()
                    .eq(InspectItem::getVersionId, draft.getId()));
            versionMapper.deleteById(draft.getId());
        }
        LocalDateTime now = BeijingTime.now();
        InspectTemplateVersion newDraft = newDraft(templateId, adminId, now);
        copyItems(target.getId(), newDraft, templateId, adminId);
        newDraft.setItemCount(countItems(newDraft.getId()));
        versionMapper.updateById(newDraft);
        touchDraft(template, adminId, now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_INSPECT_TEMPLATE").opAction("DRAFT_SAVE").bizType("INSPECT_TEMPLATE")
                .bizId(templateId).targetName(template.getName())
                .reason("回退到第 " + target.getVersionNo() + " 版")
                .after(Map.of("rollback_to", target.getVersionNo()))
                .build());
        return detail(template);
    }

    // ==================== 内部 ====================

    private InspectTemplate requireTemplate(Long templateId) {
        InspectTemplate template = templateId == null ? null : templateMapper.selectById(templateId);
        if (template == null) {
            throw BaseErrors.notFound();
        }
        return template;
    }

    private void requireVersion(Integer requested, Integer current) {
        if (requested == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(requested, current)) {
            throw BaseErrors.versionConflict();
        }
    }

    private InspectTemplateVersion newDraft(Long templateId, Long adminId, LocalDateTime now) {
        InspectTemplateVersion draft = new InspectTemplateVersion();
        draft.setTemplateId(templateId);
        draft.setStatus(DRAFT);
        draft.setDraftFlag(1);
        draft.setItemCount(0);
        draft.setUpdatedBy(adminId);
        draft.setVersion(0);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        versionMapper.insert(draft);
        return draft;
    }

    private InspectTemplateVersion createDraftIfNeeded(InspectTemplate template, Long adminId) {
        InspectTemplateVersion draft = draftVersion(template.getId());
        if (draft != null) {
            return draft;
        }
        LocalDateTime now = BeijingTime.now();
        draft = newDraft(template.getId(), adminId, now);
        InspectTemplateVersion online = publishedVersion(template.getId());
        if (online != null) {
            copyItems(online.getId(), draft, template.getId(), adminId);
            draft.setItemCount(countItems(draft.getId()));
            versionMapper.updateById(draft);
        }
        return draft;
    }

    private void touchDraft(InspectTemplate template, Long adminId, LocalDateTime now) {
        touchDraft(template, adminId, now, true);
    }

    private void touchDraft(InspectTemplate template, Long adminId, LocalDateTime now, boolean hasDraft) {
        int rows = templateMapper.update(null, Wrappers.<InspectTemplate>lambdaUpdate()
                .eq(InspectTemplate::getId, template.getId())
                .eq(InspectTemplate::getVersion, template.getVersion())
                .set(InspectTemplate::getHasDraft, hasDraft)
                .set(InspectTemplate::getUpdatedBy, adminId)
                .set(InspectTemplate::getUpdatedAt, now)
                .set(InspectTemplate::getVersion, template.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        template.setHasDraft(hasDraft);
        template.setVersion(template.getVersion() + 1);
    }

    private InspectTemplateVersion draftVersion(Long templateId) {
        return versionMapper.selectOne(Wrappers.<InspectTemplateVersion>lambdaQuery()
                .eq(InspectTemplateVersion::getTemplateId, templateId)
                .eq(InspectTemplateVersion::getDraftFlag, 1)
                .last("LIMIT 1"));
    }

    private InspectTemplateVersion publishedVersion(Long templateId) {
        return versionMapper.selectOne(Wrappers.<InspectTemplateVersion>lambdaQuery()
                .eq(InspectTemplateVersion::getTemplateId, templateId)
                .eq(InspectTemplateVersion::getStatus, PUBLISHED)
                .orderByDesc(InspectTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    private List<InspectItem> items(Long versionId) {
        return itemMapper.selectList(Wrappers.<InspectItem>lambdaQuery()
                .eq(InspectItem::getVersionId, versionId)
                .orderByAsc(InspectItem::getSortNo)
                .orderByAsc(InspectItem::getId));
    }

    private List<InspectOption> options(Long itemId) {
        return optionMapper.selectList(Wrappers.<InspectOption>lambdaQuery()
                .eq(InspectOption::getItemId, itemId)
                .orderByAsc(InspectOption::getSortNo)
                .orderByAsc(InspectOption::getId));
    }

    private int countItems(Long versionId) {
        return Math.toIntExact(itemMapper.selectCount(Wrappers.<InspectItem>lambdaQuery()
                .eq(InspectItem::getVersionId, versionId)
                .ne(InspectItem::getChangeType, "DELETED")));
    }

    private int itemCount(InspectTemplate template) {
        InspectTemplateVersion draft = draftVersion(template.getId());
        if (draft != null) {
            return countItems(draft.getId());
        }
        InspectTemplateVersion online = publishedVersion(template.getId());
        return online == null ? 0 : countItems(online.getId());
    }

    private int nextVersionNo(Long templateId) {
        InspectTemplateVersion latest = versionMapper.selectOne(Wrappers.<InspectTemplateVersion>lambdaQuery()
                .eq(InspectTemplateVersion::getTemplateId, templateId)
                .isNotNull(InspectTemplateVersion::getVersionNo)
                .orderByDesc(InspectTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private void refreshDraftItemCount(InspectTemplateVersion draft) {
        int count = countItems(draft.getId());
        versionMapper.update(null, Wrappers.<InspectTemplateVersion>lambdaUpdate()
                .eq(InspectTemplateVersion::getId, draft.getId())
                .set(InspectTemplateVersion::getItemCount, count));
        draft.setItemCount(count);
    }

    private int nextSortNo(List<InspectItem> items) {
        int max = 0;
        for (InspectItem item : items) {
            if (item.getSortNo() != null && item.getSortNo() > max) {
                max = item.getSortNo();
            }
        }
        return max + 1;
    }

    private void copyItems(Long sourceVersionId, InspectTemplateVersion target, Long templateId, Long adminId) {
        int sort = 1;
        for (InspectItem source : items(sourceVersionId)) {
            if ("DELETED".equals(source.getChangeType())) {
                continue;
            }
            InspectItem copy = new InspectItem();
            copy.setTemplateId(templateId);
            copy.setVersionId(target.getId());
            copy.setSourceItemId(source.getId());
            copy.setChangeType("NONE");
            copy.setSortNo(sort++);
            copy.setItemName(source.getItemName());
            copy.setCheckNote(source.getCheckNote());
            copy.setEnabled(source.getEnabled());
            copy.setDisableReason(source.getDisableReason());
            copy.setCreatedBy(adminId);
            itemMapper.insert(copy);
            for (InspectOption option : options(source.getId())) {
                InspectOption optionCopy = new InspectOption();
                optionCopy.setItemId(copy.getId());
                optionCopy.setSortNo(option.getSortNo());
                optionCopy.setOptionName(option.getOptionName());
                optionCopy.setIsAbnormal(option.getIsAbnormal());
                optionCopy.setCreatedBy(adminId);
                optionMapper.insert(optionCopy);
            }
        }
    }

    private InspectItem locateDraftItem(InspectTemplateVersion draft, Long itemId) {
        InspectItem item = itemId == null ? null : itemMapper.selectById(itemId);
        if (item != null && draft.getId().equals(item.getVersionId())) {
            return item;
        }
        return itemMapper.selectOne(Wrappers.<InspectItem>lambdaQuery()
                .eq(InspectItem::getVersionId, draft.getId())
                .eq(InspectItem::getSourceItemId, itemId)
                .last("LIMIT 1"));
    }

    private InspectItem sourceItem(InspectItem item, InspectTemplateVersion online) {
        if (item.getSourceItemId() == null || online == null) {
            return null;
        }
        InspectItem source = itemMapper.selectById(item.getSourceItemId());
        return source != null && online.getId().equals(source.getVersionId()) ? source : null;
    }

    private void validateItem(InspectItemSaveRequest request) {
        if (!StringUtils.hasText(request.getItemName()) || request.getItemName().trim().length() > 6) {
            throw BaseErrors.field("item_name", "项目名称最多 6 个字");
        }
        validateOptions(request.getOptions());
        if (Boolean.FALSE.equals(request.getEnabled()) && !StringUtils.hasText(request.getDisableReason())) {
            throw BaseErrors.reasonEmpty("请填写停用原因");
        }
    }

    private static void validateOptions(List<InspectItemSaveRequest.Option> options) {
        if (options == null || options.size() < 2 || options.size() > 6) {
            throw BaseErrors.field("options", "结果数量必须为 2–6 个");
        }
        Set<String> names = new HashSet<>();
        boolean hasNormal = false;
        for (int i = 0; i < options.size(); i++) {
            InspectItemSaveRequest.Option option = options.get(i);
            if (!StringUtils.hasText(option.getOptionName())) {
                throw BaseErrors.field("options", "请填写结果名称");
            }
            String name = option.getOptionName().trim();
            if (name.length() > 6) {
                throw BaseErrors.field("options", "结果名称最多 6 个字");
            }
            if (!names.add(name)) {
                throw BaseErrors.field("options", "结果名称不能重复");
            }
            if (Boolean.TRUE.equals(option.getIsAbnormal())) {
                if (i == 0) {
                    throw BaseErrors.field("options", "第一个结果须判定为正常");
                }
            } else {
                hasNormal = true;
            }
        }
        if (!hasNormal) {
            throw BaseErrors.field("options", "至少保留 1 个判定为正常的结果");
        }
    }

    private void validatePersistedOptions(InspectItem item, List<InspectOption> options) {
        if (options == null || options.size() < 2 || options.size() > 6) {
            throw BaseErrors.field("options", "项目“" + item.getItemName() + "”的结果数量必须为 2–6 个");
        }
        boolean hasNormal = false;
        for (int i = 0; i < options.size(); i++) {
            if (i == 0 && Boolean.TRUE.equals(options.get(i).getIsAbnormal())) {
                throw BaseErrors.field("options", "项目“" + item.getItemName() + "”的第一个结果须判定为正常");
            }
            if (!Boolean.TRUE.equals(options.get(i).getIsAbnormal())) {
                hasNormal = true;
            }
        }
        if (!hasNormal) {
            throw BaseErrors.field("options", "项目“" + item.getItemName() + "”至少保留 1 个判定为正常的结果");
        }
    }

    private void replaceOptions(InspectItem item, List<InspectItemSaveRequest.Option> requests, Long adminId) {
        List<InspectOption> existing = options(item.getId());
        Map<Long, InspectOption> byId = new LinkedHashMap<>();
        Map<String, InspectOption> byName = new HashMap<>();
        for (InspectOption option : existing) {
            byId.put(option.getId(), option);
            byName.put(option.getOptionName(), option);
        }
        // 没有草稿时页面提交的是线上项目的选项 ID：自动建草稿后按名称对应到草稿副本
        List<InspectOption> matched = new ArrayList<>();
        Set<Long> keep = new HashSet<>();
        for (InspectItemSaveRequest.Option request : requests) {
            InspectOption option = request.getOptionId() == null ? null : byId.get(request.getOptionId());
            if (option == null && request.getOptionId() != null && item.getSourceItemId() != null) {
                InspectOption source = optionMapper.selectById(request.getOptionId());
                if (source != null && item.getSourceItemId().equals(source.getItemId())) {
                    option = byName.get(source.getOptionName());
                }
            }
            if (option != null && !keep.add(option.getId())) {
                option = null;
            }
            matched.add(option);
        }
        // 先删不再保留的选项，保留项改名时先换临时名，再按顺序写入，避免撞 (item_id, option_name) 唯一键
        for (InspectOption option : existing) {
            if (!keep.contains(option.getId())) {
                optionMapper.deleteById(option.getId());
            }
        }
        for (int i = 0; i < requests.size(); i++) {
            InspectOption option = matched.get(i);
            if (option != null && !option.getOptionName().equals(requests.get(i).getOptionName().trim())) {
                option.setOptionName("#" + (option.getId() % 100000));
                optionMapper.updateById(option);
            }
        }
        int sort = 1;
        for (int i = 0; i < requests.size(); i++) {
            InspectItemSaveRequest.Option request = requests.get(i);
            InspectOption option = matched.get(i);
            if (option == null) {
                option = new InspectOption();
                option.setItemId(item.getId());
                option.setCreatedBy(adminId);
            }
            option.setSortNo(sort++);
            option.setOptionName(request.getOptionName().trim());
            option.setIsAbnormal(Boolean.TRUE.equals(request.getIsAbnormal()));
            if (option.getId() == null) {
                optionMapper.insert(option);
            } else {
                optionMapper.updateById(option);
            }
        }
    }

    private static void applyItem(InspectItem item, InspectItemSaveRequest request) {
        item.setItemName(request.getItemName().trim());
        item.setCheckNote(StringUtils.hasText(request.getCheckNote()) ? request.getCheckNote().trim() : null);
        boolean enabled = request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled());
        item.setEnabled(enabled);
        item.setDisableReason(enabled ? null
                : StringUtils.hasText(request.getDisableReason()) ? request.getDisableReason().trim() : null);
    }

    private static InspectItem findByName(List<InspectItem> items, String name, Long excludeId) {
        for (InspectItem item : items) {
            if (excludeId != null && excludeId.equals(item.getId())) {
                continue;
            }
            if (name.equalsIgnoreCase(item.getItemName())) {
                return item;
            }
        }
        return null;
    }

    private boolean sameAsSource(InspectItem item, InspectItem source, List<InspectOption> itemOptions) {
        if (!Objects.equals(item.getItemName(), source.getItemName())
                || !Objects.equals(item.getCheckNote(), source.getCheckNote())
                || !Objects.equals(item.getEnabled(), source.getEnabled())
                || !Objects.equals(item.getDisableReason(), source.getDisableReason())) {
            return false;
        }
        List<InspectOption> sourceOptions = source.getId() == null ? List.of() : options(source.getId());
        return sameOptions(itemOptions, sourceOptions);
    }

    private static boolean sameOptions(List<InspectOption> left, List<InspectOption> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!Objects.equals(left.get(i).getOptionName(), right.get(i).getOptionName())
                    || !Objects.equals(left.get(i).getIsAbnormal(), right.get(i).getIsAbnormal())) {
                return false;
            }
        }
        return true;
    }

    private InspectTemplateVO detail(InspectTemplate template) {
        InspectTemplateVO vo = new InspectTemplateVO();
        vo.setTemplateId(template.getId());
        vo.setName(template.getName());
        Category category = categoryMapper.selectById(template.getCategoryId());
        vo.setCategory(new IdNameVO(category == null ? null : category.getId(),
                category == null ? null : category.getName()));
        vo.setOnlineVersionNo(template.getOnlineVersionNo());
        InspectTemplateVersion online = publishedVersion(template.getId());
        if (online != null) {
            vo.setPublishedAt(online.getPublishedAt());
            vo.setPublishedByName(online.getPublishedBy() == null ? null
                    : support.adminNames().get(online.getPublishedBy()));
        }
        vo.setPenCount(readMapper.countEnabledPensByCategory(template.getCategoryId()));
        vo.setHasDraft(Boolean.TRUE.equals(template.getHasDraft()));
        InspectTemplateVersion draft = draftVersion(template.getId());
        List<InspectItem> items = items(draft != null ? draft.getId()
                : online == null ? -1L : online.getId());
        vo.setItems(fillItems(items));
        if (draft != null) {
            vo.setDraftChanges(draftChanges(items, online));
        }
        List<InspectItem> preview = online == null ? List.of() : items(online.getId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled())).toList();
        vo.setPreviewItems(fillItems(preview));
        vo.setVersion(template.getVersion());
        return vo;
    }

    private List<InspectItemVO> fillItems(List<InspectItem> items) {
        Map<Long, List<InspectOption>> optionsByItem = new HashMap<>();
        for (InspectItem item : items) {
            if (!"DELETED".equals(item.getChangeType())) {
                optionsByItem.put(item.getId(), options(item.getId()));
            }
        }
        List<InspectItemVO> result = new ArrayList<>();
        for (InspectItem item : items) {
            if ("DELETED".equals(item.getChangeType())) {
                continue;
            }
            result.add(TemplateAdminConvert.inspectItem(item, optionsByItem.get(item.getId())));
        }
        return result;
    }

    private List<InspectDraftChangeVO> draftChanges(List<InspectItem> draftItems,
                                                    InspectTemplateVersion online) {
        Map<Long, InspectItem> sources = new HashMap<>();
        if (online != null) {
            for (InspectItem source : items(online.getId())) {
                sources.put(source.getId(), source);
            }
        }
        List<InspectDraftChangeVO> result = new ArrayList<>();
        for (InspectItem item : draftItems) {
            if ("ADDED".equals(item.getChangeType())) {
                result.add(change("ADDED", item.getItemName(), null, null,
                        TemplateAdminConvert.optionsValue(options(item.getId()))));
                continue;
            }
            InspectItem source = item.getSourceItemId() == null ? null
                    : sources.get(item.getSourceItemId());
            if ("DELETED".equals(item.getChangeType())) {
                result.add(change("DELETED", item.getItemName(),
                        TemplateAdminConvert.optionsValue(source == null ? List.of() : options(source.getId())),
                        null, null));
                continue;
            }
            if (!"MODIFIED".equals(item.getChangeType()) || source == null) {
                continue;
            }
            if (!Objects.equals(item.getItemName(), source.getItemName())) {
                result.add(change("MODIFIED", item.getItemName(), "项目名称",
                        source.getItemName(), item.getItemName()));
            }
            if (!Objects.equals(item.getCheckNote(), source.getCheckNote())) {
                result.add(change("MODIFIED", item.getItemName(), "检查说明",
                        source.getCheckNote(), item.getCheckNote()));
            }
            if (!Objects.equals(item.getEnabled(), source.getEnabled())) {
                result.add(change("MODIFIED", item.getItemName(), "启用",
                        Boolean.TRUE.equals(source.getEnabled()) ? "启用" : "停用",
                        Boolean.TRUE.equals(item.getEnabled()) ? "启用" : "停用"));
            }
            List<InspectOption> itemOptions = options(item.getId());
            List<InspectOption> sourceOptions = options(source.getId());
            if (!sameOptions(itemOptions, sourceOptions)) {
                result.add(change("MODIFIED", item.getItemName(), "可选结果",
                        TemplateAdminConvert.optionsValue(sourceOptions),
                        TemplateAdminConvert.optionsValue(itemOptions)));
            }
        }
        return result;
    }

    private static InspectDraftChangeVO change(String changeType, String itemName, String fieldName,
                                               String before, String after) {
        InspectDraftChangeVO vo = new InspectDraftChangeVO();
        vo.setChangeType(changeType);
        vo.setItemName(itemName);
        vo.setFieldName(fieldName);
        vo.setBefore(before);
        vo.setAfter(after);
        return vo;
    }

    private InspectItemVO itemVO(InspectItem item) {
        InspectItemVO vo = TemplateAdminConvert.inspectItem(item, options(item.getId()));
        if ("MODIFIED".equals(item.getChangeType()) && item.getSourceItemId() != null) {
            InspectItem source = itemMapper.selectById(item.getSourceItemId());
            if (source != null) {
                vo.setBefore(TemplateAdminConvert.inspectItem(source, options(source.getId())));
            }
        }
        return vo;
    }

    private TemplateVersionVO<InspectItemVO> versionVO(InspectTemplateVersion version, List<InspectItemVO> items) {
        TemplateVersionVO<InspectItemVO> vo = new TemplateVersionVO<>();
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setStatusText(BaseAdminTexts.versionStatus(version.getStatus()));
        vo.setChangeNote(version.getChangeNote());
        vo.setItemCount(version.getItemCount());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setItems(items);
        return vo;
    }
}
