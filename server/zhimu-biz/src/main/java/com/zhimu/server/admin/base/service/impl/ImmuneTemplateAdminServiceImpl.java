package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.TemplateAdminConvert;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.ImmuneTemplate;
import com.zhimu.server.admin.base.entity.ImmuneTemplateItem;
import com.zhimu.server.admin.base.entity.ImmuneTemplateVersion;
import com.zhimu.server.admin.base.event.ImmuneTemplatePublishedEvent;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.CategoryMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateItemMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateMapper;
import com.zhimu.server.admin.base.mapper.ImmuneTemplateVersionMapper;
import com.zhimu.server.admin.base.query.ImmuneTemplateQuery;
import com.zhimu.server.admin.base.request.ImmuneItemSaveRequest;
import com.zhimu.server.admin.base.request.ImmuneTemplateCreateRequest;
import com.zhimu.server.admin.base.request.TemplateItemOrderRequest;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.request.TemplateRollbackRequest;
import com.zhimu.server.admin.base.service.ImmuneTemplateAdminService;
import com.zhimu.server.admin.base.support.BaseAdminTexts;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
import com.zhimu.server.admin.base.support.DictTypes;
import com.zhimu.server.admin.base.vo.IdNameVO;
import com.zhimu.server.admin.base.vo.ImmuneDraftChangeVO;
import com.zhimu.server.admin.base.vo.ImmuneItemVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateGroupVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateVO;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-D14–D23 免疫程序模板：草稿、项目、发布、版本与回退。 */
@Service
@RequiredArgsConstructor
public class ImmuneTemplateAdminServiceImpl implements ImmuneTemplateAdminService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String SUPERSEDED = "SUPERSEDED";
    private static final Set<String> DOSE_UNITS = Set.of("DOSE_PER_HEAD", "ML_PER_HEAD", "DOSE_PER_BIRD");

    private final ImmuneTemplateMapper templateMapper;
    private final ImmuneTemplateVersionMapper versionMapper;
    private final ImmuneTemplateItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final BreedMapper breedMapper;
    private final DrugCatalogMapper drugCatalogMapper;
    private final BaseAdminReadMapper readMapper;
    private final BaseTemplateSupport support;
    private final BaseLogs logs;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    @Override
    public List<ImmuneTemplateGroupVO> list(ImmuneTemplateQuery query) {
        String keyword = query != null && StringUtils.hasText(query.getKeyword())
                ? query.getKeyword().trim() : null;
        String status = normalizeStatus(query == null ? null : query.getStatus());
        List<ImmuneTemplate> templates = new ArrayList<>(templateMapper.selectList(Wrappers.lambdaQuery()));
        Map<Long, ImmuneTemplate> byBreed = new HashMap<>();
        for (ImmuneTemplate template : templates) {
            byBreed.put(template.getBreedId(), template);
        }
        List<Category> categories = new ArrayList<>(categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSortNo).orderByAsc(Category::getId)));
        categories.sort(Comparator.comparing(Category::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Category::getId));
        List<Breed> breeds = new ArrayList<>(breedMapper.selectList(Wrappers.<Breed>lambdaQuery()
                .eq(Breed::getStatus, "ENABLED")
                .orderByAsc(Breed::getSortNo).orderByAsc(Breed::getId)));
        breeds.sort(Comparator.comparing(Breed::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Breed::getId));
        Map<Long, List<Breed>> breedsByCategory = new LinkedHashMap<>();
        for (Breed breed : breeds) {
            breedsByCategory.computeIfAbsent(breed.getCategoryId(), key -> new ArrayList<>()).add(breed);
        }
        List<ImmuneTemplateGroupVO> result = new ArrayList<>();
        for (Category category : categories) {
            List<Breed> categoryBreeds = breedsByCategory.get(category.getId());
            if (categoryBreeds == null || categoryBreeds.isEmpty()) {
                continue;
            }
            if (query != null && query.getCategoryId() != null
                    && !query.getCategoryId().equals(category.getId())) {
                continue;
            }
            ImmuneTemplateGroupVO group = new ImmuneTemplateGroupVO();
            group.setCategoryId(category.getId());
            group.setCategoryName(category.getName());
            for (Breed breed : categoryBreeds) {
                if (query != null && query.getBreedId() != null && !query.getBreedId().equals(breed.getId())) {
                    continue;
                }
                ImmuneTemplate template = byBreed.get(breed.getId());
                if (keyword != null && !matchesKeyword(keyword, category.getName(), breed.getName(),
                        template == null ? null : template.getName())) {
                    continue;
                }
                if (!matchesStatus(status, template)) {
                    continue;
                }
                group.getItems().add(groupItem(template, breed));
            }
            if (!group.getItems().isEmpty()) {
                result.add(group);
            }
        }
        return result;
    }

    @Override
    public ImmuneTemplateVO detail(Long templateId) {
        ImmuneTemplate template = requireTemplate(templateId);
        return detail(template);
    }

    @Override
    @Transactional
    public ImmuneTemplateVO create(ImmuneTemplateCreateRequest request) {
        Long adminId = AdminContext.requireAdminId();
        Breed breed = support.requireEnabledBreed(request.getBreedId());
        Category category = support.requireCategory(breed.getCategoryId());
        ImmuneTemplate existing = templateMapper.selectOne(Wrappers.<ImmuneTemplate>lambdaQuery()
                .eq(ImmuneTemplate::getBreedId, breed.getId()).last("LIMIT 1"));
        if (existing != null) {
            throw BaseErrors.duplicate("该品种已有模板");
        }
        String name = StringUtils.hasText(request.getName())
                ? request.getName().trim() : breed.getName() + category.getName();
        if (name.length() > 30) {
            throw BaseErrors.field("name", "模板名称最多 30 个字");
        }
        LocalDateTime now = BeijingTime.now();
        ImmuneTemplate template = new ImmuneTemplate();
        template.setBreedId(breed.getId());
        template.setName(name);
        template.setHasDraft(true);
        template.setHasByDate(false);
        template.setCopiedFromId(request.getCopyFromTemplateId());
        template.setUpdatedBy(adminId);
        template.setVersion(0);
        templateMapper.insert(template);

        ImmuneTemplateVersion draft = newDraft(template.getId(), adminId, now);
        String action = "CREATE";
        if (request.getCopyFromTemplateId() != null) {
            ImmuneTemplate source = requireTemplate(request.getCopyFromTemplateId());
            ImmuneTemplateVersion sourceVersion = publishedVersion(source.getId());
            if (sourceVersion == null) {
                throw BaseErrors.field("copy_from_template_id", "请选择已发布的模板");
            }
            copyItems(sourceVersion.getId(), draft, "ADDED", template.getId(), adminId);
            draft.setItemCount(countItems(draft.getId()));
            versionMapper.updateById(draft);
            template.setHasByDate(hasByDate(draft.getId()));
            action = "COPY";
            templateMapper.update(null, Wrappers.<ImmuneTemplate>lambdaUpdate()
                    .eq(ImmuneTemplate::getId, template.getId())
                    .eq(ImmuneTemplate::getVersion, 0)
                    .set(ImmuneTemplate::getHasByDate, template.getHasByDate())
                    .set(ImmuneTemplate::getUpdatedBy, adminId)
                    .set(ImmuneTemplate::getUpdatedAt, now)
                    .set(ImmuneTemplate::getVersion, 1));
            template.setVersion(1);
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction(action).bizType("IMMUNE_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(breed.getName() + " " + template.getName())
                .after(Map.of("breed_id", breed.getId(), "name", template.getName()))
                .build());
        return detail(template);
    }

    @Override
    @Transactional
    public ImmuneItemVO addItem(Long templateId, ImmuneItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        Category category = support.requireCategory(requireBreed(template.getBreedId()).getCategoryId());
        String species = category.getSpecies();
        validateItem(request, species);
        ImmuneTemplateVersion draft = createDraftIfNeeded(template, adminId);

        String vaccineName = vaccineNames(List.of(request.getVaccineId())).get(request.getVaccineId());
        List<ImmuneTemplateItem> items = items(draft.getId());
        ImmuneTemplateItem same = findByVaccineRound(items, request.getVaccineId(), request.getDoseRound().trim(),
                null);
        if (same != null && !"DELETED".equals(same.getChangeType())) {
            throw BaseErrors.duplicate("已有“" + TemplateAdminConvert.labelWithoutDose(vaccineName,
                    request.getDoseRound().trim()) + "”");
        }
        LocalDateTime now = BeijingTime.now();
        ImmuneTemplateItem item;
        if (same != null) {
            item = same;
            applyFields(item, request);
            ImmuneTemplateItem source = sourceItem(draft, item, publishedVersion(template.getId()));
            item.setChangeType(source == null ? "ADDED" : sameAsSource(item, source) ? "NONE" : "MODIFIED");
            item.setUpdatedAt(now);
            itemMapper.updateById(item);
        } else {
            item = new ImmuneTemplateItem();
            item.setTemplateId(template.getId());
            item.setVersionId(draft.getId());
            item.setSourceItemId(null);
            item.setChangeType("ADDED");
            item.setSortNo(nextSortNo(items));
            applyFields(item, request);
            itemMapper.insert(item);
        }
        refreshDraftItemCount(draft);
        touchDraft(template, adminId, now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("DRAFT_SAVE").bizType("IMMUNE_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(vaccineName + " " + request.getDoseRound())
                .after(Map.of("action", "ADD_ITEM", "vaccine_id", request.getVaccineId(),
                        "dose_round", request.getDoseRound().trim()))
                .build());
        return itemVO(item, template);
    }

    @Override
    @Transactional
    public ImmuneItemVO updateItem(Long templateId, Long itemId, ImmuneItemSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        Category category = support.requireCategory(requireBreed(template.getBreedId()).getCategoryId());
        validateItem(request, category.getSpecies());
        ImmuneTemplateVersion draft = createDraftIfNeeded(template, adminId);
        ImmuneTemplateItem item = locateDraftItem(draft, itemId);
        if (item == null || "DELETED".equals(item.getChangeType())) {
            throw BaseErrors.notFound();
        }
        String vaccineName = vaccineNames(List.of(request.getVaccineId())).get(request.getVaccineId());
        List<ImmuneTemplateItem> items = items(draft.getId());
        ImmuneTemplateItem same = findByVaccineRound(items, request.getVaccineId(), request.getDoseRound().trim(),
                item.getId());
        if (same != null) {
            throw BaseErrors.duplicate("已有“" + TemplateAdminConvert.labelWithoutDose(vaccineName,
                    request.getDoseRound().trim()) + "”");
        }
        applyFields(item, request);
        ImmuneTemplateItem source = sourceItem(draft, item, publishedVersion(template.getId()));
        item.setChangeType(changeType(item, source));
        item.setUpdatedAt(BeijingTime.now());
        itemMapper.updateById(item);
        refreshDraftItemCount(draft);
        touchDraft(template, adminId, BeijingTime.now());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("DRAFT_SAVE").bizType("IMMUNE_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(vaccineName + " " + request.getDoseRound())
                .after(Map.of("action", "UPDATE_ITEM", "item_id", item.getId()))
                .build());
        return itemVO(item, template);
    }

    @Override
    @Transactional
    public void deleteItem(Long templateId, Long itemId, Integer version) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(version, template.getVersion());
        ImmuneTemplateVersion draft = createDraftIfNeeded(template, adminId);
        ImmuneTemplateItem item = locateDraftItem(draft, itemId);
        if (item == null || "DELETED".equals(item.getChangeType())) {
            throw BaseErrors.notFound();
        }
        String vaccineName = vaccineNames(List.of(item.getVaccineId())).get(item.getVaccineId());
        if ("ADDED".equals(item.getChangeType())) {
            itemMapper.deleteById(item.getId());
        } else {
            item.setChangeType("DELETED");
            item.setUpdatedAt(BeijingTime.now());
            itemMapper.updateById(item);
        }
        refreshDraftItemCount(draft);
        touchDraft(template, adminId, BeijingTime.now());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("DRAFT_SAVE").bizType("IMMUNE_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .searchText(vaccineName + " " + item.getDoseRound())
                .after(Map.of("action", "DELETE_ITEM", "item_id", itemId))
                .build());
    }

    @Override
    @Transactional
    public void order(Long templateId, TemplateItemOrderRequest request) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        ImmuneTemplateVersion draft = createDraftIfNeeded(template, adminId);
        List<ImmuneTemplateItem> items = items(draft.getId()).stream()
                .filter(item -> !"DELETED".equals(item.getChangeType())).toList();
        Map<Long, ImmuneTemplateItem> byId = new HashMap<>();
        Map<Long, ImmuneTemplateItem> bySource = new HashMap<>();
        for (ImmuneTemplateItem item : items) {
            byId.put(item.getId(), item);
            if (item.getSourceItemId() != null) {
                bySource.put(item.getSourceItemId(), item);
            }
        }
        // 没有草稿时页面拿的是线上项目 ID，自动建草稿后按 source_item_id 换成草稿项目
        List<ImmuneTemplateItem> ordered = new ArrayList<>();
        Set<Long> resolved = new HashSet<>();
        for (Long id : request.getItemIds()) {
            ImmuneTemplateItem item = id == null ? null : byId.getOrDefault(id, bySource.get(id));
            if (item == null || !resolved.add(item.getId())) {
                throw BaseErrors.field("item_ids", "请提交草稿全部未删除项目");
            }
            ordered.add(item);
        }
        if (resolved.size() != items.size()) {
            throw BaseErrors.field("item_ids", "请提交草稿全部未删除项目");
        }
        int sort = 1;
        for (ImmuneTemplateItem item : ordered) {
            item.setSortNo(sort++);
            item.setUpdatedAt(BeijingTime.now());
            itemMapper.updateById(item);
        }
        touchDraft(template, adminId, BeijingTime.now());
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("DRAFT_SAVE").bizType("IMMUNE_TEMPLATE")
                .bizId(template.getId()).targetName(template.getName())
                .after(Map.of("action", "SORT", "item_ids", request.getItemIds()))
                .build());
    }

    @Override
    @Transactional
    public TemplateVersionVO<ImmuneItemVO> publish(Long templateId, TemplatePublishRequest request) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        if (!StringUtils.hasText(request.getChangeNote())) {
            throw BaseErrors.reasonEmpty("请填写修改说明");
        }
        ImmuneTemplateVersion draft = draftVersion(templateId);
        if (draft == null) {
            throw BaseErrors.stateChanged("没有未发布的修改，请刷新");
        }
        ImmuneTemplateVersion oldOnline = publishedVersion(templateId);
        List<ImmuneTemplateItem> items = items(draft.getId());
        List<ImmuneTemplateItem> active = items.stream()
                .filter(item -> !"DELETED".equals(item.getChangeType())).toList();
        if (active.isEmpty()) {
            throw BaseErrors.param("请至少添加 1 个免疫项目");
        }
        String species = support.speciesOfBreed(template.getBreedId());
        for (ImmuneTemplateItem item : active) {
            support.requireVaccine(item.getVaccineId(), species);
        }
        itemMapper.delete(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                .eq(ImmuneTemplateItem::getVersionId, draft.getId())
                .eq(ImmuneTemplateItem::getChangeType, "DELETED"));
        LocalDateTime now = BeijingTime.now();
        int versionNo = nextVersionNo(templateId);
        versionMapper.update(null, Wrappers.<ImmuneTemplateVersion>lambdaUpdate()
                .eq(ImmuneTemplateVersion::getId, draft.getId())
                .set(ImmuneTemplateVersion::getVersionNo, versionNo)
                .set(ImmuneTemplateVersion::getStatus, PUBLISHED)
                .set(ImmuneTemplateVersion::getDraftFlag, null)
                .set(ImmuneTemplateVersion::getChangeNote, request.getChangeNote().trim())
                .set(ImmuneTemplateVersion::getItemCount, active.size())
                .set(ImmuneTemplateVersion::getPublishedBy, adminId)
                .set(ImmuneTemplateVersion::getPublishedAt, now)
                .set(ImmuneTemplateVersion::getUpdatedBy, adminId)
                .set(ImmuneTemplateVersion::getUpdatedAt, now)
                .setSql("version = version + 1"));
        replacePublished(templateId, draft.getId(), adminId, now);
        boolean hasByDate = active.stream().anyMatch(item -> "BY_DATE".equals(item.getScheduleType()));
        int rows = templateMapper.update(null, Wrappers.<ImmuneTemplate>lambdaUpdate()
                .eq(ImmuneTemplate::getId, templateId)
                .eq(ImmuneTemplate::getVersion, template.getVersion())
                .set(ImmuneTemplate::getOnlineVersionNo, versionNo)
                .set(ImmuneTemplate::getHasDraft, false)
                .set(ImmuneTemplate::getHasByDate, hasByDate)
                .set(ImmuneTemplate::getUpdatedBy, adminId)
                .set(ImmuneTemplate::getUpdatedAt, now)
                .set(ImmuneTemplate::getVersion, template.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        template.setOnlineVersionNo(versionNo);
        template.setHasDraft(false);
        template.setHasByDate(hasByDate);
        template.setVersion(template.getVersion() + 1);
        List<ImmuneItemVO> itemVOs = fillItems(active, oldOnline);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("PUBLISH").bizType("IMMUNE_TEMPLATE")
                .bizId(templateId).targetName(template.getName()).reason(request.getChangeNote().trim())
                .after(Map.of("version_no", versionNo, "item_count", active.size()))
                .build());
        AfterCommit.run(() -> eventPublisher.publishEvent(
                new ImmuneTemplatePublishedEvent(templateId, draft.getId(), versionNo)));
        TemplateVersionVO<ImmuneItemVO> vo = versionVO(versionMapper.selectById(draft.getId()), itemVOs);
        vo.setPublishedByName(support.adminNames().get(adminId));
        return vo;
    }

    @Override
    @Transactional
    public void discard(Long templateId, Integer version) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(version, template.getVersion());
        ImmuneTemplateVersion draft = draftVersion(templateId);
        if (draft == null) {
            throw BaseErrors.stateChanged("没有未发布的修改，请刷新");
        }
        itemMapper.delete(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                .eq(ImmuneTemplateItem::getVersionId, draft.getId()));
        versionMapper.deleteById(draft.getId());
        touchDraft(template, adminId, BeijingTime.now(), false);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("DISCARD").bizType("IMMUNE_TEMPLATE")
                .bizId(templateId).targetName(template.getName()).build());
    }

    @Override
    public List<TemplateVersionVO<ImmuneItemVO>> versions(Long templateId) {
        ImmuneTemplate template = requireTemplate(templateId);
        Map<Long, String> adminNames = support.adminNames();
        ImmuneTemplateVersion online = publishedVersion(templateId);
        List<TemplateVersionVO<ImmuneItemVO>> result = new ArrayList<>();
        for (ImmuneTemplateVersion version : versionMapper.selectList(Wrappers.<ImmuneTemplateVersion>lambdaQuery()
                .eq(ImmuneTemplateVersion::getTemplateId, templateId)
                .isNotNull(ImmuneTemplateVersion::getVersionNo)
                .orderByDesc(ImmuneTemplateVersion::getVersionNo))) {
            TemplateVersionVO<ImmuneItemVO> vo = versionVO(version,
                    fillItems(items(version.getId()), online));
            vo.setPublishedByName(version.getPublishedBy() == null ? null
                    : adminNames.get(version.getPublishedBy()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public ImmuneTemplateVO rollback(Long templateId, TemplateRollbackRequest request) {
        Long adminId = AdminContext.requireAdminId();
        ImmuneTemplate template = requireTemplate(templateId);
        requireVersion(request.getVersion(), template.getVersion());
        ImmuneTemplateVersion target = versionMapper.selectOne(Wrappers.<ImmuneTemplateVersion>lambdaQuery()
                .eq(ImmuneTemplateVersion::getTemplateId, templateId)
                .eq(ImmuneTemplateVersion::getVersionNo, request.getTargetVersionNo())
                .last("LIMIT 1"));
        if (target == null) {
            throw BaseErrors.notFound();
        }
        if (Objects.equals(template.getOnlineVersionNo(), target.getVersionNo())) {
            throw BaseErrors.stateChanged("当前已是第 " + target.getVersionNo() + " 版，不需要回退");
        }
        ImmuneTemplateVersion draft = draftVersion(templateId);
        if (draft != null) {
            itemMapper.delete(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                    .eq(ImmuneTemplateItem::getVersionId, draft.getId()));
            versionMapper.deleteById(draft.getId());
        }
        LocalDateTime now = BeijingTime.now();
        ImmuneTemplateVersion newDraft = newDraft(templateId, adminId, now);
        copyItems(target.getId(), newDraft, "NONE", templateId, adminId);
        newDraft.setItemCount(countItems(newDraft.getId()));
        versionMapper.updateById(newDraft);
        touchDraft(template, adminId, now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_IMMUNE_TEMPLATE").opAction("DRAFT_SAVE").bizType("IMMUNE_TEMPLATE")
                .bizId(templateId).targetName(template.getName())
                .reason("回退到第 " + target.getVersionNo() + " 版")
                .after(Map.of("rollback_to", target.getVersionNo()))
                .build());
        return detail(template);
    }

    // ==================== 内部 ====================

    private ImmuneTemplate requireTemplate(Long templateId) {
        ImmuneTemplate template = templateId == null ? null : templateMapper.selectById(templateId);
        if (template == null) {
            throw BaseErrors.notFound();
        }
        return template;
    }

    private Breed requireBreed(Long breedId) {
        Breed breed = breedMapper.selectById(breedId);
        if (breed == null) {
            throw BaseErrors.notFound();
        }
        return breed;
    }

    private void requireVersion(Integer requested, Integer current) {
        if (requested == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(requested, current)) {
            throw BaseErrors.versionConflict();
        }
    }

    private ImmuneTemplateVersion newDraft(Long templateId, Long adminId, LocalDateTime now) {
        ImmuneTemplateVersion draft = new ImmuneTemplateVersion();
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

    /** 首次修改时自动创建草稿：复制线上项为 NONE，保留 source_item_id */
    private ImmuneTemplateVersion createDraftIfNeeded(ImmuneTemplate template, Long adminId) {
        ImmuneTemplateVersion draft = draftVersion(template.getId());
        if (draft != null) {
            return draft;
        }
        LocalDateTime now = BeijingTime.now();
        draft = newDraft(template.getId(), adminId, now);
        ImmuneTemplateVersion online = publishedVersion(template.getId());
        if (online != null) {
            copyItems(online.getId(), draft, "NONE", template.getId(), adminId);
            draft.setItemCount(countItems(draft.getId()));
            versionMapper.updateById(draft);
        }
        return draft;
    }

    private void touchDraft(ImmuneTemplate template, Long adminId, LocalDateTime now) {
        touchDraft(template, adminId, now, true);
    }

    private void touchDraft(ImmuneTemplate template, Long adminId, LocalDateTime now, boolean hasDraft) {
        int rows = templateMapper.update(null, Wrappers.<ImmuneTemplate>lambdaUpdate()
                .eq(ImmuneTemplate::getId, template.getId())
                .eq(ImmuneTemplate::getVersion, template.getVersion())
                .set(ImmuneTemplate::getHasDraft, hasDraft)
                .set(ImmuneTemplate::getUpdatedBy, adminId)
                .set(ImmuneTemplate::getUpdatedAt, now)
                .set(ImmuneTemplate::getVersion, template.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        template.setHasDraft(hasDraft);
        template.setVersion(template.getVersion() + 1);
    }

    private void replacePublished(Long templateId, Long publishedVersionId, Long adminId, LocalDateTime now) {
        versionMapper.update(null, Wrappers.<ImmuneTemplateVersion>lambdaUpdate()
                .eq(ImmuneTemplateVersion::getTemplateId, templateId)
                .eq(ImmuneTemplateVersion::getStatus, PUBLISHED)
                .ne(ImmuneTemplateVersion::getId, publishedVersionId)
                .set(ImmuneTemplateVersion::getStatus, SUPERSEDED)
                .set(ImmuneTemplateVersion::getUpdatedBy, adminId)
                .set(ImmuneTemplateVersion::getUpdatedAt, now)
                .setSql("version = version + 1"));
    }

    private ImmuneTemplateVersion draftVersion(Long templateId) {
        return versionMapper.selectOne(Wrappers.<ImmuneTemplateVersion>lambdaQuery()
                .eq(ImmuneTemplateVersion::getTemplateId, templateId)
                .eq(ImmuneTemplateVersion::getDraftFlag, 1)
                .last("LIMIT 1"));
    }

    private ImmuneTemplateVersion publishedVersion(Long templateId) {
        return versionMapper.selectOne(Wrappers.<ImmuneTemplateVersion>lambdaQuery()
                .eq(ImmuneTemplateVersion::getTemplateId, templateId)
                .eq(ImmuneTemplateVersion::getStatus, PUBLISHED)
                .orderByDesc(ImmuneTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    private List<ImmuneTemplateItem> items(Long versionId) {
        return itemMapper.selectList(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                .eq(ImmuneTemplateItem::getVersionId, versionId)
                .orderByAsc(ImmuneTemplateItem::getSortNo)
                .orderByAsc(ImmuneTemplateItem::getId));
    }

    private int countItems(Long versionId) {
        return Math.toIntExact(itemMapper.selectCount(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                .eq(ImmuneTemplateItem::getVersionId, versionId)
                .ne(ImmuneTemplateItem::getChangeType, "DELETED")));
    }

    private int nextVersionNo(Long templateId) {
        ImmuneTemplateVersion latest = versionMapper.selectOne(Wrappers.<ImmuneTemplateVersion>lambdaQuery()
                .eq(ImmuneTemplateVersion::getTemplateId, templateId)
                .isNotNull(ImmuneTemplateVersion::getVersionNo)
                .orderByDesc(ImmuneTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private void copyItems(Long sourceVersionId, ImmuneTemplateVersion target, String changeType,
                           Long templateId, Long adminId) {
        int sort = 1;
        for (ImmuneTemplateItem source : items(sourceVersionId)) {
            if ("DELETED".equals(source.getChangeType())) {
                continue;
            }
            ImmuneTemplateItem copy = new ImmuneTemplateItem();
            copy.setTemplateId(templateId);
            copy.setVersionId(target.getId());
            copy.setSourceItemId(source.getId());
            copy.setChangeType(changeType);
            copy.setSortNo(sort++);
            copy.setVaccineId(source.getVaccineId());
            copy.setDoseRound(source.getDoseRound());
            copy.setScheduleType(source.getScheduleType());
            copy.setDayAge(source.getDayAge());
            copy.setPlanMonth(source.getPlanMonth());
            copy.setPlanDay(source.getPlanDay());
            copy.setAdvanceDays(source.getAdvanceDays());
            copy.setDose(source.getDose());
            copy.setDoseUnit(source.getDoseUnit());
            copy.setVaccinateMethodCode(source.getVaccinateMethodCode());
            copy.setRemark(source.getRemark());
            itemMapper.insert(copy);
        }
    }

    private void refreshDraftItemCount(ImmuneTemplateVersion draft) {
        int count = countItems(draft.getId());
        versionMapper.update(null, Wrappers.<ImmuneTemplateVersion>lambdaUpdate()
                .eq(ImmuneTemplateVersion::getId, draft.getId())
                .set(ImmuneTemplateVersion::getItemCount, count));
        draft.setItemCount(count);
    }

    private int nextSortNo(List<ImmuneTemplateItem> items) {
        int max = 0;
        for (ImmuneTemplateItem item : items) {
            if (item.getSortNo() != null && item.getSortNo() > max) {
                max = item.getSortNo();
            }
        }
        return max + 1;
    }

    private static ImmuneTemplateItem findByVaccineRound(List<ImmuneTemplateItem> items, Long vaccineId,
                                                         String doseRound, Long excludeId) {
        for (ImmuneTemplateItem item : items) {
            if (excludeId != null && excludeId.equals(item.getId())) {
                continue;
            }
            if (Objects.equals(vaccineId, item.getVaccineId())
                    && doseRound.equalsIgnoreCase(item.getDoseRound())) {
                return item;
            }
        }
        return null;
    }

    private ImmuneTemplateItem locateDraftItem(ImmuneTemplateVersion draft, Long itemId) {
        ImmuneTemplateItem item = itemId == null ? null : itemMapper.selectById(itemId);
        if (item != null && draft.getId().equals(item.getVersionId())) {
            return item;
        }
        return itemMapper.selectOne(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                .eq(ImmuneTemplateItem::getVersionId, draft.getId())
                .eq(ImmuneTemplateItem::getSourceItemId, itemId)
                .last("LIMIT 1"));
    }

    private ImmuneTemplateItem sourceItem(ImmuneTemplateVersion draft, ImmuneTemplateItem item,
                                          ImmuneTemplateVersion online) {
        if (item.getSourceItemId() == null || online == null) {
            return null;
        }
        return itemMapper.selectById(item.getSourceItemId());
    }

    private void validateItem(ImmuneItemSaveRequest request, String species) {
        String scheduleType = request.getScheduleType() == null ? null
                : request.getScheduleType().trim().toUpperCase();
        if (!"BY_DAY_AGE".equals(scheduleType) && !"BY_DATE".equals(scheduleType)) {
            throw BaseErrors.field("schedule_type", "请选择计划时间");
        }
        if ("BY_DAY_AGE".equals(scheduleType)) {
            if (request.getDayAge() == null || request.getDayAge() < 1 || request.getDayAge() > 999) {
                throw BaseErrors.field("day_age", "接种日龄必须在 1–999 之间");
            }
        } else {
            validateDate(request.getPlanMonth(), request.getPlanDay());
            if (request.getAdvanceDays() == null || request.getAdvanceDays() < 1
                    || request.getAdvanceDays() > 60) {
                throw BaseErrors.field("advance_days", "提前生成必须在 1–60 天之间");
            }
        }
        if (request.getDose() == null || request.getDose().signum() <= 0) {
            throw BaseErrors.field("dose", "剂量必须大于 0");
        }
        if (request.getDose() != null && request.getDose().scale() > 2) {
            throw BaseErrors.field("dose", "剂量最多 2 位小数");
        }
        if (!DOSE_UNITS.contains(request.getDoseUnit())) {
            throw BaseErrors.field("dose_unit", "请选择正确的剂量单位");
        }
        support.requireApplicableDict(DictTypes.VACCINATE_METHOD, request.getVaccinateMethodCode(), species,
                "vaccinate_method_code", "该接种方式不适用于" + BaseAdminTexts.species(species));
        support.requireVaccine(request.getVaccineId(), species);
    }

    private static void validateDate(Integer month, Integer day) {
        if (month == null || day == null || month < 1 || month > 12 || day < 1) {
            throw BaseErrors.field("plan_month", "请选择正确的日期");
        }
        if (month == 2 && day == 29) {
            throw BaseErrors.field("plan_day", "请选择正确的日期");
        }
        if (day > YearMonth.of(2025, month).lengthOfMonth()) {
            throw BaseErrors.field("plan_day", "请选择正确的日期");
        }
    }

    private static void applyFields(ImmuneTemplateItem item, ImmuneItemSaveRequest request) {
        boolean byDate = "BY_DATE".equalsIgnoreCase(request.getScheduleType());
        item.setVaccineId(request.getVaccineId());
        item.setDoseRound(request.getDoseRound().trim());
        item.setScheduleType(byDate ? "BY_DATE" : "BY_DAY_AGE");
        item.setDayAge(byDate ? null : request.getDayAge());
        item.setPlanMonth(byDate ? request.getPlanMonth() : null);
        item.setPlanDay(byDate ? request.getPlanDay() : null);
        item.setAdvanceDays(byDate ? request.getAdvanceDays() : null);
        item.setDose(request.getDose());
        item.setDoseUnit(request.getDoseUnit());
        item.setVaccinateMethodCode(request.getVaccinateMethodCode());
        item.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
    }

    private static String changeType(ImmuneTemplateItem item, ImmuneTemplateItem source) {
        if (source == null) {
            return "ADDED";
        }
        return sameAsSource(item, source) ? "NONE" : "MODIFIED";
    }

    private static boolean sameAsSource(ImmuneTemplateItem item, ImmuneTemplateItem source) {
        return Objects.equals(item.getVaccineId(), source.getVaccineId())
                && Objects.equals(item.getDoseRound(), source.getDoseRound())
                && Objects.equals(item.getScheduleType(), source.getScheduleType())
                && Objects.equals(item.getDayAge(), source.getDayAge())
                && Objects.equals(item.getPlanMonth(), source.getPlanMonth())
                && Objects.equals(item.getPlanDay(), source.getPlanDay())
                && Objects.equals(item.getAdvanceDays(), source.getAdvanceDays())
                && sameDecimal(item.getDose(), source.getDose())
                && Objects.equals(item.getDoseUnit(), source.getDoseUnit())
                && Objects.equals(item.getVaccinateMethodCode(), source.getVaccinateMethodCode())
                && Objects.equals(item.getRemark(), source.getRemark());
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private ImmuneTemplateVO detail(ImmuneTemplate template) {
        ImmuneTemplateVO vo = new ImmuneTemplateVO();
        vo.setTemplateId(template.getId());
        vo.setName(template.getName());
        Breed breed = breedMapper.selectById(template.getBreedId());
        Category category = breed == null ? null : categoryMapper.selectById(breed.getCategoryId());
        vo.setBreed(new IdNameVO(breed == null ? null : breed.getId(), breed == null ? null : breed.getName()));
        vo.setCategory(new IdNameVO(category == null ? null : category.getId(),
                category == null ? null : category.getName()));
        vo.setOnlineVersionNo(template.getOnlineVersionNo());
        ImmuneTemplateVersion online = publishedVersion(template.getId());
        if (online != null) {
            vo.setPublishedAt(online.getPublishedAt());
            vo.setPublishedByName(online.getPublishedBy() == null ? null
                    : support.adminNames().get(online.getPublishedBy()));
        }
        vo.setActiveBatchCount(readMapper.countActiveBatchesByTemplate(template.getId()));
        if (Boolean.TRUE.equals(template.getHasByDate())) {
            vo.setBreedingFarmCount(readMapper.countBreedingFarmsByBreed(template.getBreedId()));
        }
        vo.setHasDraft(Boolean.TRUE.equals(template.getHasDraft()));
        ImmuneTemplateVersion draft = draftVersion(template.getId());
        List<ImmuneTemplateItem> items = items(draft != null ? draft.getId()
                : online == null ? -1L : online.getId());
        vo.setItems(fillItems(items, online));
        if (draft != null) {
            vo.setDraftChanges(draftChanges(items, online));
        }
        vo.setVersion(template.getVersion());
        return vo;
    }

    private List<ImmuneItemVO> fillItems(List<ImmuneTemplateItem> items, ImmuneTemplateVersion online) {
        Map<Long, String> vaccineNames = vaccineNames(items.stream().map(ImmuneTemplateItem::getVaccineId)
                .toList());
        Map<String, String> methodNames = support.dictNames(DictTypes.VACCINATE_METHOD);
        Map<Long, ImmuneTemplateItem> sources = new HashMap<>();
        if (online != null) {
            for (ImmuneTemplateItem source : items(online.getId())) {
                sources.put(source.getId(), source);
            }
        }
        List<ImmuneItemVO> result = new ArrayList<>();
        for (ImmuneTemplateItem item : items) {
            if ("DELETED".equals(item.getChangeType())) {
                continue;
            }
            ImmuneItemVO vo = TemplateAdminConvert.immuneItem(item, vaccineNames, methodNames);
            if ("MODIFIED".equals(item.getChangeType()) && item.getSourceItemId() != null) {
                ImmuneTemplateItem source = sources.get(item.getSourceItemId());
                if (source != null) {
                    vo.setBefore(TemplateAdminConvert.immuneItem(source, vaccineNames, methodNames));
                }
            }
            result.add(vo);
        }
        return result;
    }

    private List<ImmuneDraftChangeVO> draftChanges(List<ImmuneTemplateItem> draftItems,
                                                   ImmuneTemplateVersion online) {
        Map<Long, String> vaccineNames = vaccineNames(draftItems.stream()
                .map(ImmuneTemplateItem::getVaccineId).toList());
        Map<Long, ImmuneTemplateItem> sources = new HashMap<>();
        if (online != null) {
            for (ImmuneTemplateItem source : items(online.getId())) {
                sources.put(source.getId(), source);
            }
        }
        List<ImmuneDraftChangeVO> result = new ArrayList<>();
        for (ImmuneTemplateItem item : draftItems) {
            String vaccineName = vaccineNames.get(item.getVaccineId());
            if ("ADDED".equals(item.getChangeType())) {
                result.add(change("ADDED", vaccineName, item.getDoseRound(), null, null,
                        TemplateAdminConvert.scheduleValue(item)));
                continue;
            }
            ImmuneTemplateItem source = item.getSourceItemId() == null ? null
                    : sources.get(item.getSourceItemId());
            if ("DELETED".equals(item.getChangeType())) {
                result.add(change("DELETED", vaccineName, item.getDoseRound(), null,
                        TemplateAdminConvert.scheduleValue(source), null));
                continue;
            }
            if (!"MODIFIED".equals(item.getChangeType()) || source == null) {
                continue;
            }
            if (!Objects.equals(item.getVaccineId(), source.getVaccineId())) {
                result.add(change("MODIFIED", vaccineName, item.getDoseRound(), "疫苗",
                        vaccineNames.get(source.getVaccineId()), vaccineName));
            }
            if (!Objects.equals(item.getDoseRound(), source.getDoseRound())) {
                result.add(change("MODIFIED", vaccineName, item.getDoseRound(), "免次",
                        source.getDoseRound(), item.getDoseRound()));
            }
            if (!Objects.equals(item.getScheduleType(), source.getScheduleType())
                    || !Objects.equals(item.getDayAge(), source.getDayAge())
                    || !Objects.equals(item.getPlanMonth(), source.getPlanMonth())
                    || !Objects.equals(item.getPlanDay(), source.getPlanDay())
                    || !Objects.equals(item.getAdvanceDays(), source.getAdvanceDays())) {
                result.add(change("MODIFIED", vaccineName, item.getDoseRound(), "计划时间",
                        TemplateAdminConvert.scheduleValue(source), TemplateAdminConvert.scheduleValue(item)));
            }
            if (!sameDecimal(item.getDose(), source.getDose())
                    || !Objects.equals(item.getDoseUnit(), source.getDoseUnit())) {
                result.add(change("MODIFIED", vaccineName, item.getDoseRound(), "剂量",
                        TemplateAdminConvert.doseValue(source.getDose(), source.getDoseUnit()),
                        TemplateAdminConvert.doseValue(item.getDose(), item.getDoseUnit())));
            }
            if (!Objects.equals(item.getVaccinateMethodCode(), source.getVaccinateMethodCode())) {
                result.add(change("MODIFIED", vaccineName, item.getDoseRound(), "接种方式",
                        source.getVaccinateMethodCode(), item.getVaccinateMethodCode()));
            }
            if (!Objects.equals(item.getRemark(), source.getRemark())) {
                result.add(change("MODIFIED", vaccineName, item.getDoseRound(), "备注",
                        source.getRemark(), item.getRemark()));
            }
        }
        return result;
    }

    private static ImmuneDraftChangeVO change(String changeType, String vaccineName, String doseRound,
                                              String fieldName, String before, String after) {
        ImmuneDraftChangeVO vo = new ImmuneDraftChangeVO();
        vo.setChangeType(changeType);
        vo.setVaccineName(vaccineName);
        vo.setDoseRound(doseRound);
        vo.setFieldName(fieldName);
        vo.setBefore(before);
        vo.setAfter(after);
        return vo;
    }

    private ImmuneItemVO itemVO(ImmuneTemplateItem item, ImmuneTemplate template) {
        Map<Long, String> vaccineNames = vaccineNames(List.of(item.getVaccineId()));
        ImmuneItemVO vo = TemplateAdminConvert.immuneItem(item, vaccineNames,
                support.dictNames(DictTypes.VACCINATE_METHOD));
        if ("MODIFIED".equals(item.getChangeType()) && item.getSourceItemId() != null) {
            ImmuneTemplateItem source = itemMapper.selectById(item.getSourceItemId());
            if (source != null) {
                vo.setBefore(TemplateAdminConvert.immuneItem(source, vaccineNames,
                        support.dictNames(DictTypes.VACCINATE_METHOD)));
            }
        }
        return vo;
    }

    private TemplateVersionVO<ImmuneItemVO> versionVO(ImmuneTemplateVersion version, List<ImmuneItemVO> items) {
        TemplateVersionVO<ImmuneItemVO> vo = new TemplateVersionVO<>();
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

    private Map<Long, String> vaccineNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        List<Long> distinct = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return map;
        }
        for (DrugCatalog catalog : drugCatalogMapper.selectList(Wrappers.<DrugCatalog>lambdaQuery()
                .in(DrugCatalog::getId, distinct))) {
            map.put(catalog.getId(), catalog.getName());
        }
        return map;
    }

    private boolean hasByDate(Long versionId) {
        return itemMapper.selectCount(Wrappers.<ImmuneTemplateItem>lambdaQuery()
                .eq(ImmuneTemplateItem::getVersionId, versionId)
                .eq(ImmuneTemplateItem::getScheduleType, "BY_DATE")
                .ne(ImmuneTemplateItem::getChangeType, "DELETED")) > 0;
    }

    private ImmuneTemplateGroupVO.Item groupItem(ImmuneTemplate template, Breed breed) {
        ImmuneTemplateGroupVO.Item item = new ImmuneTemplateGroupVO.Item();
        item.setBreedId(breed.getId());
        item.setBreedName(breed.getName());
        if (template == null) {
            item.setItemCount(0);
            item.setActiveBatchCount(0);
            item.setHasDraft(false);
            item.setHasByDate(false);
            return item;
        }
        item.setTemplateId(template.getId());
        item.setName(template.getName());
        item.setOnlineVersionNo(template.getOnlineVersionNo());
        item.setItemCount(itemCount(template));
        item.setActiveBatchCount(readMapper.countActiveBatchesByTemplate(template.getId()));
        item.setHasDraft(Boolean.TRUE.equals(template.getHasDraft()));
        item.setHasByDate(Boolean.TRUE.equals(template.getHasByDate()));
        return item;
    }

    private int itemCount(ImmuneTemplate template) {
        ImmuneTemplateVersion draft = draftVersion(template.getId());
        if (draft != null) {
            return countItems(draft.getId());
        }
        ImmuneTemplateVersion online = publishedVersion(template.getId());
        return online == null ? 0 : countItems(online.getId());
    }

    private static boolean matchesKeyword(String keyword, String categoryName, String breedName, String name) {
        return contains(categoryName, keyword) || contains(breedName, keyword) || contains(name, keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private static boolean matchesStatus(String status, ImmuneTemplate template) {
        if (status == null) {
            return true;
        }
        return switch (status) {
            case "CONFIGURED" -> template != null;
            case "UNCONFIGURED" -> template == null;
            case "DRAFT" -> template != null && Boolean.TRUE.equals(template.getHasDraft());
            default -> true;
        };
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String value = status.trim().toUpperCase();
        if (!Set.of("CONFIGURED", "UNCONFIGURED", "DRAFT").contains(value)) {
            throw BaseErrors.field("status", "请选择正确的状态");
        }
        return value;
    }
}
