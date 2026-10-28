package com.zhimu.server.admin.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.entity.ContentApplicable;
import com.zhimu.server.admin.content.entity.KnowledgeVersion;
import com.zhimu.server.admin.content.mapper.ContentApplicableMapper;
import com.zhimu.server.admin.content.mapper.ContentStatMapper;
import com.zhimu.server.admin.content.mapper.KnowledgeTaskMapper;
import com.zhimu.server.admin.content.mapper.KnowledgeVersionMapper;
import com.zhimu.server.admin.content.query.AdminKnowledgeQuery;
import com.zhimu.server.admin.content.request.KnowledgePublishRequest;
import com.zhimu.server.admin.content.request.KnowledgeRollbackRequest;
import com.zhimu.server.admin.content.request.KnowledgeSaveRequest;
import com.zhimu.server.admin.content.request.OffShelfRequest;
import com.zhimu.server.admin.content.service.ContentCategoryAdminService;
import com.zhimu.server.admin.content.service.KnowledgeAdminService;
import com.zhimu.server.admin.content.service.KnowledgeParagraphService;
import com.zhimu.server.admin.content.service.KnowledgeTaskService;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentLogs;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.support.KnowledgeHtml;
import com.zhimu.server.admin.content.vo.KnowledgeEditVO;
import com.zhimu.server.admin.content.vo.KnowledgePageVO;
import com.zhimu.server.admin.content.vo.KnowledgeRowVO;
import com.zhimu.server.admin.content.vo.KnowledgeVO;
import com.zhimu.server.admin.content.vo.KnowledgeVersionItemVO;
import com.zhimu.server.admin.content.vo.KnowledgeVersionVO;
import com.zhimu.server.admin.content.vo.StatAggregates;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeParagraphMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 知识库管理（ADM-C44–C56）。 */
@Service
@RequiredArgsConstructor
public class KnowledgeAdminServiceImpl implements KnowledgeAdminService {

    private static final String BIZ_KNOWLEDGE = "KNOWLEDGE";
    private static final Set<String> SOURCE_TYPES = Set.of("PLATFORM", "DISEASE");
    private static final Set<String> DISPLAY_TYPES = Set.of("STANDARD", "ARTICLE");

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeParagraphMapper paragraphMapper;
    private final KnowledgeVersionMapper versionMapper;
    private final KnowledgeTaskMapper taskMapper;
    private final ContentApplicableMapper applicableMapper;
    private final ContentStatMapper statMapper;
    private final CourseMapper courseMapper;
    private final ContentCategoryAdminService categoryService;
    private final KnowledgeParagraphService paragraphService;
    private final KnowledgeTaskService taskService;
    private final com.zhimu.server.admin.content.support.ContentNames names;
    private final ContentLogs logs;

    @Value("${zhimu.content.knowledge.frequent-cite-threshold:50}")
    private int frequentCiteThreshold;

    @Override
    public KnowledgePageVO page(AdminKnowledgeQuery query) {
        int pageNo = query.pageOrDefault();
        int size = query.sizeOrDefault();
        var wrapper = baseWrapper(query);
        wrapper.orderByDesc(AiKnowledge::getUpdatedAt).orderByDesc(AiKnowledge::getId);
        Page<AiKnowledge> page = knowledgeMapper.selectPage(new Page<>(pageNo, size), wrapper);
        Map<Long, String> adminNames = names.adminNames(page.getRecords().stream()
                .map(AiKnowledge::getUpdatedBy).filter(Objects::nonNull).toList());
        Map<Long, AiKnowledgeParagraph> videos = videoParagraphs(page.getRecords());
        Map<Long, Course> courses = courses(videos.values());
        List<KnowledgeRowVO> rows = new ArrayList<>();
        for (AiKnowledge knowledge : page.getRecords()) {
            rows.add(toRow(knowledge, adminNames, videos, courses));
        }
        KnowledgePageVO result = new KnowledgePageVO(rows, page.getTotal(), pageNo, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", Math.toIntExact(count(query, null)));
        tabs.put("ON_SHELF", Math.toIntExact(count(query, "ON_SHELF")));
        tabs.put("DRAFT", Math.toIntExact(count(query, "DRAFT")));
        tabs.put("OFF_SHELF", Math.toIntExact(count(query, "OFF_SHELF")));
        result.withTabCounts(tabs);
        result.setTaskPendingCount(Math.toIntExact(taskMapper.countPending()));
        return result;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiKnowledge> baseWrapper(
            AdminKnowledgeQuery query) {
        var wrapper = Wrappers.<AiKnowledge>lambdaQuery();
        if (query.getStatus() != null && !query.getStatus().isBlank()
                && !"ALL".equalsIgnoreCase(query.getStatus())) {
            wrapper.eq(AiKnowledge::getStatus, query.getStatus().toUpperCase());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(AiKnowledge::getCategoryId, query.getCategoryId());
        }
        if (query.getSourceType() != null && !query.getSourceType().isBlank()
                && !"ALL".equalsIgnoreCase(query.getSourceType())) {
            wrapper.eq(AiKnowledge::getSourceType, query.getSourceType().toUpperCase());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AiKnowledge::getTitle, keyword).or().like(AiKnowledge::getKeywords, keyword));
        }
        return wrapper;
    }

    private long count(AdminKnowledgeQuery query, String status) {
        var wrapper = baseWrapper(query);
        wrapper.eq(status != null, AiKnowledge::getStatus, status);
        return knowledgeMapper.selectCount(wrapper);
    }

    private KnowledgeRowVO toRow(AiKnowledge knowledge, Map<Long, String> adminNames,
                                 Map<Long, AiKnowledgeParagraph> videos, Map<Long, Course> courses) {
        KnowledgeRowVO vo = new KnowledgeRowVO();
        vo.setId(knowledge.getId());
        vo.setTitle(knowledge.getTitle());
        vo.setCategoryId(knowledge.getCategoryId());
        vo.setCategoryName(knowledge.getCategoryName());
        vo.setSourceType(knowledge.getSourceType());
        vo.setSourceTypeText(ContentTexts.sourceType(knowledge.getSourceType()));
        vo.setDisplayType(knowledge.getDisplayType());
        vo.setIsMajorEpidemic(knowledge.getIsMajorEpidemic() != null && knowledge.getIsMajorEpidemic() == 1);
        vo.setStatus(knowledge.getStatus());
        vo.setStatusText(ContentTexts.contentStatus(knowledge.getStatus()));
        vo.setVersionLabel(versionLabel(knowledge));
        vo.setIsReadonly("DRUG_CATALOG".equals(knowledge.getSourceType()));
        vo.setSourceRefId("DRUG_CATALOG".equals(knowledge.getSourceType()) ? knowledge.getSourceRefId() : null);
        vo.setCiteCount30d(knowledge.getCiteCount30d());
        vo.setUsefulRate30d(knowledge.getUsefulRate30d());
        vo.setUpdatedAt(knowledge.getUpdatedAt());
        vo.setUpdatedByName(adminNames.get(knowledge.getUpdatedBy()));
        AiKnowledgeParagraph video = videos.get(knowledge.getId());
        if (video != null) {
            Course course = courses.get(video.getCourseId());
            vo.setHasVideo(true);
            vo.setVideoTitle(course == null ? null : course.getTitle());
            vo.setVideoDurationSec(course == null ? null : course.getDurationSec());
        } else {
            vo.setHasVideo(knowledge.getHasVideo() != null && knowledge.getHasVideo() == 1);
        }
        vo.setHasDraft(versionMapper.selectDraft(knowledge.getId()) != null);
        return vo;
    }

    private Map<Long, AiKnowledgeParagraph> videoParagraphs(List<AiKnowledge> rows) {
        Map<Long, AiKnowledgeParagraph> map = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            return map;
        }
        List<Long> ids = rows.stream().map(AiKnowledge::getId).toList();
        for (AiKnowledgeParagraph paragraph : paragraphMapper.selectList(Wrappers.<AiKnowledgeParagraph>lambdaQuery()
                .in(AiKnowledgeParagraph::getKnowledgeId, ids)
                .eq(AiKnowledgeParagraph::getParaType, "VIDEO")
                .orderByAsc(AiKnowledgeParagraph::getParaNo))) {
            map.putIfAbsent(paragraph.getKnowledgeId(), paragraph);
        }
        return map;
    }

    private Map<Long, Course> courses(java.util.Collection<AiKnowledgeParagraph> paragraphs) {
        List<Long> ids = paragraphs.stream().map(AiKnowledgeParagraph::getCourseId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Course> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Course course : courseMapper.selectBatchIds(ids)) {
            map.put(course.getId(), course);
        }
        return map;
    }

    private static String versionLabel(AiKnowledge knowledge) {
        if ("DRUG_CATALOG".equals(knowledge.getSourceType())) {
            return "自动同步";
        }
        return knowledge.getVersionNo() == null || knowledge.getVersionNo() <= 0
                ? null : "版本 " + knowledge.getVersionNo();
    }

    @Override
    public KnowledgeEditVO edit(Long id) {
        AiKnowledge knowledge = requireKnowledge(id);
        KnowledgeEditVO vo = new KnowledgeEditVO();
        vo.setId(knowledge.getId());
        vo.setSourceType(knowledge.getSourceType());
        vo.setStatus(knowledge.getStatus());
        vo.setIsMajorEpidemic(knowledge.getIsMajorEpidemic() != null && knowledge.getIsMajorEpidemic() == 1);
        vo.setDisplayType(knowledge.getDisplayType());
        vo.setVersion(knowledge.getVersion());
        vo.setCiteCount30d(knowledge.getCiteCount30d());
        vo.setUsefulRate30d(knowledge.getUsefulRate30d());
        vo.setStatAt(knowledge.getStatAt());

        KnowledgeVersion online = onlineVersion(knowledge);
        if (online != null) {
            KnowledgeEditVO.OnlineInfo info = new KnowledgeEditVO.OnlineInfo();
            info.setVersionNo(online.getVersionNo());
            info.setPublishedAt(online.getPublishedAt());
            info.setPublishedByName(names.adminName(online.getPublishedBy()));
            vo.setOnline(info);
            vo.setOnlineContent(snapshot(online));
        } else {
            vo.setOnlineContent(snapshotOf(knowledge));
        }

        KnowledgeVersion draft = versionMapper.selectDraft(id);
        if (draft != null) {
            KnowledgeEditVO.Draft item = new KnowledgeEditVO.Draft();
            fillSnapshot(item, draft);
            item.setDraftId(draft.getId());
            item.setChangeNote(draft.getChangeNote());
            item.setSourceTaskId(draft.getSourceTaskId());
            item.setSavedByName(names.adminName(draft.getUpdatedBy() != null ? draft.getUpdatedBy() : draft.getCreatedBy()));
            item.setSavedAt(draft.getUpdatedAt());
            vo.setDraft(item);
            Long owner = draft.getUpdatedBy() != null ? draft.getUpdatedBy() : draft.getCreatedBy();
            if (owner != null && !Objects.equals(owner, AdminContext.adminId())) {
                KnowledgeEditVO.DraftOwner draftOwner = new KnowledgeEditVO.DraftOwner();
                draftOwner.setName(names.adminName(owner));
                draftOwner.setSavedAt(draft.getUpdatedAt());
                vo.setDraftOwner(draftOwner);
            }
        }

        Map<String, Integer> cites = new HashMap<>();
        for (StatAggregates.ParagraphCiteAgg row : statMapper.selectParagraphCites(id,
                BeijingTime.now().minusDays(30))) {
            cites.put(row.getParaKey(), row.getCitations() == null ? 0 : row.getCitations().intValue());
        }
        for (AiKnowledgeParagraph paragraph : paragraphService.onlineParagraphs(id)) {
            if ("IMAGE".equals(paragraph.getParaType()) || "VIDEO".equals(paragraph.getParaType())) {
                continue;
            }
            KnowledgeEditVO.ParagraphInfo info = new KnowledgeEditVO.ParagraphInfo();
            info.setParaKey(paragraph.getParaKey());
            info.setCiteCount30d(cites.getOrDefault(paragraph.getParaKey(), 0));
            info.setIsFrequent(info.getCiteCount30d() >= frequentCiteThreshold);
            vo.getParagraphs().add(info);
        }
        return vo;
    }

    private KnowledgeVersion onlineVersion(AiKnowledge knowledge) {
        if (knowledge.getOnlineVersionId() != null) {
            KnowledgeVersion version = versionMapper.selectById(knowledge.getOnlineVersionId());
            if (version != null) {
                return version;
            }
        }
        if (knowledge.getVersionNo() != null && knowledge.getVersionNo() > 0) {
            return versionMapper.selectByVersionNo(knowledge.getId(), knowledge.getVersionNo());
        }
        return null;
    }

    private KnowledgeEditVO.ContentSnapshot snapshot(KnowledgeVersion version) {
        KnowledgeEditVO.ContentSnapshot snapshot = new KnowledgeEditVO.ContentSnapshot();
        snapshot.setTitle(version.getTitle());
        snapshot.setCategoryId(version.getCategoryId());
        snapshot.setCategoryName(categoryService.nameOf(version.getCategoryId()));
        snapshot.setDisplayType(version.getDisplayType());
        snapshot.setIsMajorEpidemic(version.getIsMajorEpidemic() != null && version.getIsMajorEpidemic() == 1);
        snapshot.setApplicableCategoryIds(parseIds(version.getApplicableCategoryIds()));
        snapshot.setKeywords(splitKeywords(version.getKeywords()));
        snapshot.setContent(version.getContent());
        return snapshot;
    }

    private void fillSnapshot(KnowledgeEditVO.ContentSnapshot snapshot, KnowledgeVersion version) {
        snapshot.setTitle(version.getTitle());
        snapshot.setCategoryId(version.getCategoryId());
        snapshot.setCategoryName(categoryService.nameOf(version.getCategoryId()));
        snapshot.setDisplayType(version.getDisplayType());
        snapshot.setIsMajorEpidemic(version.getIsMajorEpidemic() != null && version.getIsMajorEpidemic() == 1);
        snapshot.setApplicableCategoryIds(parseIds(version.getApplicableCategoryIds()));
        snapshot.setKeywords(splitKeywords(version.getKeywords()));
        snapshot.setContent(version.getContent());
    }

    /** 没有版本记录的旧数据：用条目字段与线上段落还原。 */
    private KnowledgeEditVO.ContentSnapshot snapshotOf(AiKnowledge knowledge) {
        KnowledgeEditVO.ContentSnapshot snapshot = new KnowledgeEditVO.ContentSnapshot();
        snapshot.setTitle(knowledge.getTitle());
        snapshot.setCategoryId(knowledge.getCategoryId());
        snapshot.setCategoryName(knowledge.getCategoryName());
        snapshot.setDisplayType(knowledge.getDisplayType());
        snapshot.setIsMajorEpidemic(knowledge.getIsMajorEpidemic() != null && knowledge.getIsMajorEpidemic() == 1);
        snapshot.setApplicableCategoryIds(applicableIds(knowledge.getId()));
        snapshot.setKeywords(splitKeywords(knowledge.getKeywords()));
        List<KnowledgeHtml.Paragraph> paragraphs = new ArrayList<>();
        for (AiKnowledgeParagraph paragraph : paragraphService.onlineParagraphs(knowledge.getId())) {
            paragraphs.add(new KnowledgeHtml.Paragraph(paragraph.getParaKey(), paragraph.getParaType(),
                    paragraph.getContentHtml(), paragraph.getContentText(), paragraph.getCourseId(),
                    paragraph.getImageFileId(), paragraph.getImageUrl()));
        }
        snapshot.setContent(KnowledgeHtml.render(paragraphs));
        return snapshot;
    }

    @Override
    @Transactional
    public KnowledgeVO create(KnowledgeSaveRequest request) {
        String sourceType = request.getSourceType() == null ? null : request.getSourceType().toUpperCase();
        if (sourceType == null || !SOURCE_TYPES.contains(sourceType)) {
            throw ContentErrors.field("source_type", "来源不正确");
        }
        String title = requireTitle(request.getTitle());
        var category = categoryService.requireEnabled(request.getCategoryId());
        String submit = request.getSubmitAction() == null ? "DRAFT" : request.getSubmitAction().toUpperCase();
        if (!"DRAFT".equals(submit) && !"PUBLISH".equals(submit)) {
            throw ContentErrors.field("submit_action", "提交方式不正确");
        }
        String display = displayType(request.getDisplayType(), sourceType, null);
        boolean major = Boolean.TRUE.equals(request.getIsMajorEpidemic());
        String keywords = keywordsOf(request.getKeywords());
        validateApplicable(request.getApplicableCategoryIds());
        KnowledgeHtml.Parsed parsed = paragraphService.resolve(null, request.getContent());

        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setTitle(title);
        knowledge.setCategoryId(category.getId());
        knowledge.setCategoryName(category.getName());
        knowledge.setSourceType(sourceType);
        knowledge.setDisplayType(display);
        knowledge.setIsMajorEpidemic(major ? 1 : 0);
        knowledge.setStatus("DRAFT");
        knowledge.setVersionNo(0);
        knowledge.setKeywords(keywords);
        knowledge.setHasVideo(hasVideo(parsed) ? 1 : 0);
        knowledge.setSortNo(nextSortNo());
        knowledge.setUpdatedBy(AdminContext.requireAdminId());
        knowledge.setVersion(0);
        knowledgeMapper.insert(knowledge);

        if ("PUBLISH".equals(submit)) {
            String note = request.getChangeNote() == null || request.getChangeNote().isBlank()
                    ? "创建条目" : request.getChangeNote().trim();
            KnowledgeVersion version = newVersion(knowledge, 1, "PUBLISHED", null, title, category.getId(),
                    display, major, keywords, request.getApplicableCategoryIds(), parsed, note, null, null);
            knowledge.setStatus("ON_SHELF");
            knowledge.setVersionNo(1);
            knowledge.setOnlineVersionId(version.getId());
            knowledge.setPublishedAt(BeijingTime.now());
            knowledge.setPublishedBy(AdminContext.requireAdminId());
            knowledgeMapper.updateById(knowledge);
            paragraphService.replace(knowledge.getId(), 1, parsed, true);
            syncApplicable(knowledge.getId(), request.getApplicableCategoryIds());
            writeLog("CREATE", knowledge.getId(), title, null, null,
                    Map.of("status", "ON_SHELF", "version_no", 1));
        } else {
            KnowledgeVersion draft = draftVersion(knowledge, title, category.getId(), display, major, keywords,
                    request.getApplicableCategoryIds(), parsed, request.getChangeNote(), request.getSourceTaskId(), null);
            writeLog("CREATE", knowledge.getId(), title, null, null, Map.of("status", "DRAFT"));
            return knowledgeVO(knowledge, null);
        }
        return knowledgeVO(knowledge, 1);
    }

    @Override
    @Transactional
    public KnowledgeVO saveDraft(Long id, KnowledgeSaveRequest request) {
        AiKnowledge knowledge = requireKnowledge(id);
        if ("DRUG_CATALOG".equals(knowledge.getSourceType())) {
            throw ContentErrors.forbidden("兽药目录同步的条目请在兽药疫苗目录中维护");
        }
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), knowledge.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        KnowledgeVersion existing = versionMapper.selectDraft(id);
        String title = requireTitle(request.getTitle());
        var category = categoryService.requireEnabled(request.getCategoryId());
        String display = displayType(request.getDisplayType(), knowledge.getSourceType(),
                existing != null ? existing.getDisplayType() : knowledge.getDisplayType());
        boolean major = request.getIsMajorEpidemic() != null ? request.getIsMajorEpidemic()
                : knowledge.getIsMajorEpidemic() != null && knowledge.getIsMajorEpidemic() == 1;
        String keywords = request.getKeywords() == null && existing != null
                ? existing.getKeywords() : keywordsOf(request.getKeywords());
        List<Long> applicable = request.getApplicableCategoryIds() == null && existing != null
                ? parseIds(existing.getApplicableCategoryIds()) : request.getApplicableCategoryIds();
        validateApplicable(applicable);
        KnowledgeHtml.Parsed parsed = paragraphService.resolve(id, request.getContent());
        String changeNote = request.getChangeNote() == null && existing != null
                ? existing.getChangeNote() : request.getChangeNote();

        draftVersion(knowledge, title, category.getId(), display, major, keywords, applicable, parsed,
                changeNote, request.getSourceTaskId(), existing);

        if (knowledge.getVersionNo() == null || knowledge.getVersionNo() <= 0) {
            knowledge.setTitle(title);
            knowledge.setCategoryId(category.getId());
            knowledge.setCategoryName(category.getName());
            knowledge.setDisplayType(display);
            knowledge.setIsMajorEpidemic(major ? 1 : 0);
            knowledge.setKeywords(keywords);
            knowledge.setHasVideo(hasVideo(parsed) ? 1 : 0);
        }
        knowledge.setUpdatedBy(AdminContext.requireAdminId());
        if (knowledgeMapper.updateById(knowledge) == 0) {
            throw ContentErrors.versionConflict();
        }
        return knowledgeVO(knowledge, null);
    }

    @Override
    @Transactional
    public KnowledgeVO publish(Long id, KnowledgePublishRequest request) {
        AiKnowledge knowledge = requireKnowledge(id);
        if ("DRUG_CATALOG".equals(knowledge.getSourceType())) {
            throw ContentErrors.forbidden("兽药目录同步的条目请在兽药疫苗目录中维护");
        }
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), knowledge.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        KnowledgeVersion draft = versionMapper.selectDraft(id);
        if (draft == null) {
            throw ContentErrors.stateChanged("没有可发布的草稿");
        }
        KnowledgeHtml.Parsed parsed = paragraphService.resolve(id, draft.getContent());
        int next = versionMapper.selectMaxVersionNo(id) + 1;
        Long onlineVersionId = knowledge.getOnlineVersionId();
        if (onlineVersionId != null) {
            versionMapper.update(null, Wrappers.<KnowledgeVersion>lambdaUpdate()
                    .eq(KnowledgeVersion::getId, onlineVersionId)
                    .set(KnowledgeVersion::getStatus, "SUPERSEDED"));
        }
        draft.setStatus("PUBLISHED");
        draft.setVersionNo(next);
        draft.setDraftFlag(null);
        draft.setChangeNote(request.getChangeNote());
        draft.setPublishedAt(BeijingTime.now());
        draft.setPublishedBy(AdminContext.requireAdminId());
        draft.setUpdatedBy(AdminContext.requireAdminId());
        if (versionMapper.updateById(draft) == 0) {
            throw ContentErrors.versionConflict();
        }

        knowledge.setTitle(draft.getTitle());
        knowledge.setCategoryId(draft.getCategoryId());
        knowledge.setCategoryName(categoryService.nameOf(draft.getCategoryId()));
        knowledge.setDisplayType(draft.getDisplayType());
        knowledge.setIsMajorEpidemic(draft.getIsMajorEpidemic() == null ? 0 : draft.getIsMajorEpidemic());
        knowledge.setKeywords(draft.getKeywords());
        knowledge.setHasVideo(hasVideo(parsed) ? 1 : 0);
        knowledge.setVersionNo(next);
        knowledge.setOnlineVersionId(draft.getId());
        knowledge.setPublishedAt(BeijingTime.now());
        knowledge.setPublishedBy(AdminContext.requireAdminId());
        if ("DRAFT".equals(knowledge.getStatus())) {
            knowledge.setStatus("ON_SHELF");
        }
        knowledge.setUpdatedBy(AdminContext.requireAdminId());
        if (knowledgeMapper.updateById(knowledge) == 0) {
            throw ContentErrors.versionConflict();
        }
        boolean online = "ON_SHELF".equals(knowledge.getStatus());
        paragraphService.replace(id, next, parsed, online);
        syncApplicable(id, parseIds(draft.getApplicableCategoryIds()));
        taskService.completeForKnowledge(id, next);
        writeLog("PUBLISH", id, knowledge.getTitle(), null,
                Map.of("version_no", next - 1), Map.of("version_no", next));
        KnowledgeVO vo = knowledgeVO(knowledge, next);
        vo.setOnlineVersionNo(next);
        return vo;
    }

    @Override
    public List<KnowledgeVersionItemVO> versions(Long id) {
        AiKnowledge knowledge = requireKnowledge(id);
        List<KnowledgeVersion> versions = versionMapper.selectPublished(id);
        Map<Long, String> adminNames = names.adminNames(versions.stream()
                .map(KnowledgeVersion::getPublishedBy).filter(Objects::nonNull).toList());
        List<KnowledgeVersionItemVO> result = new ArrayList<>();
        for (KnowledgeVersion version : versions) {
            KnowledgeVersionItemVO vo = new KnowledgeVersionItemVO();
            vo.setVersionNo(version.getVersionNo());
            vo.setIsOnline(Objects.equals(version.getVersionNo(), knowledge.getVersionNo()));
            vo.setPublishedAt(version.getPublishedAt());
            vo.setPublishedByName(adminNames.get(version.getPublishedBy()));
            vo.setChangeNote(version.getChangeNote());
            vo.setRollbackFromNo(version.getRollbackFromNo());
            result.add(vo);
        }
        return result;
    }

    @Override
    public KnowledgeVersionVO version(Long id, Integer versionNo) {
        AiKnowledge knowledge = requireKnowledge(id);
        KnowledgeVersion target = versionNo == null ? null : versionMapper.selectByVersionNo(id, versionNo);
        if (target == null) {
            throw ContentErrors.notFound();
        }
        KnowledgeVersion online = onlineVersion(knowledge);
        KnowledgeVersionVO vo = new KnowledgeVersionVO();
        vo.setVersionNo(target.getVersionNo());
        vo.setTitle(target.getTitle());
        vo.setCategoryName(categoryService.nameOf(target.getCategoryId()));
        vo.setKeywords(splitKeywords(target.getKeywords()));
        vo.setContent(target.getContent());
        List<Long> applicable = parseIds(target.getApplicableCategoryIds());
        if (!applicable.isEmpty()) {
            vo.setApplicableCategoryNames(statMapper.selectProductCategories(applicable).stream()
                    .map(StatAggregates.CategoryRef::getName).toList());
        }
        if (online != null) {
            compare(vo, target, online);
            paragraphDiff(vo, target, online);
        }
        return vo;
    }

    private void compare(KnowledgeVersionVO vo, KnowledgeVersion target, KnowledgeVersion online) {
        addCompare(vo, "title", "标题", target.getTitle(), online.getTitle());
        addCompare(vo, "category", "分类", categoryService.nameOf(target.getCategoryId()),
                categoryService.nameOf(online.getCategoryId()));
        addCompare(vo, "display_type", "展示类型", target.getDisplayType(), online.getDisplayType());
        addCompare(vo, "is_major_epidemic", "重大动物疫病",
                target.getIsMajorEpidemic() != null && target.getIsMajorEpidemic() == 1 ? "是" : "否",
                online.getIsMajorEpidemic() != null && online.getIsMajorEpidemic() == 1 ? "是" : "否");
        addCompare(vo, "keywords", "关键词", target.getKeywords(), online.getKeywords());
        addCompare(vo, "applicable_category", "适用品类",
                applicableNames(target.getApplicableCategoryIds()), applicableNames(online.getApplicableCategoryIds()));
    }

    private void addCompare(KnowledgeVersionVO vo, String field, String text, String oldValue, String newValue) {
        KnowledgeVersionVO.CompareItem item = new KnowledgeVersionVO.CompareItem();
        item.setField(field);
        item.setFieldText(text);
        item.setOldValue(nullToEmpty(oldValue));
        item.setNewValue(nullToEmpty(newValue));
        item.setChanged(!Objects.equals(nullToEmpty(oldValue), nullToEmpty(newValue)));
        vo.getCompare().add(item);
    }

    private String applicableNames(String ids) {
        List<Long> values = parseIds(ids);
        if (values.isEmpty()) {
            return "全部品类";
        }
        return statMapper.selectProductCategories(values).stream()
                .map(StatAggregates.CategoryRef::getName).reduce((a, b) -> a + "、" + b).orElse("");
    }

    private void paragraphDiff(KnowledgeVersionVO vo, KnowledgeVersion target, KnowledgeVersion online) {
        Map<String, String> oldMap = new LinkedHashMap<>();
        for (KnowledgeHtml.Paragraph p : KnowledgeHtml.parse(online.getContent(), null).paragraphs()) {
            oldMap.put(p.paraKey(), nullToEmpty(p.contentText()));
        }
        Set<String> seen = new HashSet<>();
        for (KnowledgeHtml.Paragraph p : KnowledgeHtml.parse(target.getContent(), null).paragraphs()) {
            seen.add(p.paraKey());
            KnowledgeVersionVO.ParagraphDiff diff = new KnowledgeVersionVO.ParagraphDiff();
            diff.setParaKey(p.paraKey());
            if (!oldMap.containsKey(p.paraKey())) {
                diff.setDiff("ADDED");
            } else if (!Objects.equals(oldMap.get(p.paraKey()), nullToEmpty(p.contentText()))) {
                diff.setDiff("MODIFIED");
            } else {
                diff.setDiff("SAME");
            }
            vo.getParagraphDiff().add(diff);
        }
        for (String key : oldMap.keySet()) {
            if (!seen.contains(key)) {
                KnowledgeVersionVO.ParagraphDiff diff = new KnowledgeVersionVO.ParagraphDiff();
                diff.setParaKey(key);
                diff.setDiff("REMOVED");
                vo.getParagraphDiff().add(diff);
            }
        }
    }

    @Override
    @Transactional
    public KnowledgeVO rollback(Long id, KnowledgeRollbackRequest request) {
        AiKnowledge knowledge = requireKnowledge(id);
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), knowledge.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (Objects.equals(request.getTargetVersionNo(), knowledge.getVersionNo())) {
            throw ContentErrors.param("不能回退到线上版本");
        }
        KnowledgeVersion target = versionMapper.selectByVersionNo(id, request.getTargetVersionNo());
        if (target == null) {
            throw ContentErrors.notFound();
        }
        Long affected = statMapper.countCitationMessagesByVersion(id, knowledge.getVersionNo(),
                BeijingTime.now().minusDays(30));
        KnowledgeHtml.Parsed parsed = KnowledgeHtml.parse(target.getContent(), null);
        int next = versionMapper.selectMaxVersionNo(id) + 1;
        KnowledgeVersion online = onlineVersion(knowledge);
        if (online != null) {
            versionMapper.update(null, Wrappers.<KnowledgeVersion>lambdaUpdate()
                    .eq(KnowledgeVersion::getId, online.getId())
                    .set(KnowledgeVersion::getStatus, "SUPERSEDED"));
        }
        KnowledgeVersion version = newVersion(knowledge, next, "PUBLISHED", null, target.getTitle(),
                target.getCategoryId(), target.getDisplayType(),
                target.getIsMajorEpidemic() != null && target.getIsMajorEpidemic() == 1,
                target.getKeywords(), parseIds(target.getApplicableCategoryIds()), parsed,
                "回退到版本 " + target.getVersionNo(), target.getVersionNo(),
                target.getSourceTaskId());

        knowledge.setTitle(target.getTitle());
        knowledge.setCategoryId(target.getCategoryId());
        knowledge.setCategoryName(categoryService.nameOf(target.getCategoryId()));
        knowledge.setDisplayType(target.getDisplayType());
        knowledge.setIsMajorEpidemic(target.getIsMajorEpidemic() == null ? 0 : target.getIsMajorEpidemic());
        knowledge.setKeywords(target.getKeywords());
        knowledge.setHasVideo(hasVideo(parsed) ? 1 : 0);
        knowledge.setVersionNo(next);
        knowledge.setOnlineVersionId(version.getId());
        knowledge.setPublishedAt(BeijingTime.now());
        knowledge.setPublishedBy(AdminContext.requireAdminId());
        knowledge.setUpdatedBy(AdminContext.requireAdminId());
        if (knowledgeMapper.updateById(knowledge) == 0) {
            throw ContentErrors.versionConflict();
        }
        paragraphService.replace(id, next, parsed, "ON_SHELF".equals(knowledge.getStatus()));
        syncApplicable(id, parseIds(target.getApplicableCategoryIds()));
        writeLog("ROLLBACK", id, knowledge.getTitle(), request.getReason(),
                Map.of("version_no", next - 1), Map.of("version_no", next));
        KnowledgeVO vo = knowledgeVO(knowledge, next);
        vo.setNewVersionNo(next);
        vo.setAffectedAnswers30d(affected == null ? 0 : affected.intValue());
        return vo;
    }

    @Override
    @Transactional
    public KnowledgeVO offShelf(Long id, OffShelfRequest request) {
        AiKnowledge knowledge = requireKnowledge(id);
        if (request.getVersion() == null || !Objects.equals(request.getVersion(), knowledge.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (!"ON_SHELF".equals(knowledge.getStatus())) {
            throw ContentErrors.stateChanged("条目已下架，列表已刷新");
        }
        knowledge.setStatus("OFF_SHELF");
        knowledge.setOffReason(request.getOffReason());
        knowledge.setOffAt(BeijingTime.now());
        knowledge.setOffBy(AdminContext.requireAdminId());
        knowledge.setUpdatedBy(AdminContext.requireAdminId());
        if (knowledgeMapper.updateById(knowledge) == 0) {
            throw ContentErrors.versionConflict();
        }
        paragraphMapper.update(null, Wrappers.<AiKnowledgeParagraph>lambdaUpdate()
                .eq(AiKnowledgeParagraph::getKnowledgeId, id)
                .set(AiKnowledgeParagraph::getIsOnline, 0));
        writeLog("OFF_SHELF", id, knowledge.getTitle(), request.getOffReason(),
                Map.of("status", "ON_SHELF"), Map.of("status", "OFF_SHELF"));
        return knowledgeVO(knowledge, null);
    }

    @Override
    @Transactional
    public KnowledgeVO onShelf(Long id, Integer version) {
        AiKnowledge knowledge = requireKnowledge(id);
        if (version == null || !Objects.equals(version, knowledge.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (knowledge.getVersionNo() == null || knowledge.getVersionNo() <= 0) {
            throw ContentErrors.stateChanged("条目还没有发布过，请先发布");
        }
        if (!"OFF_SHELF".equals(knowledge.getStatus())) {
            throw ContentErrors.stateChanged("条目状态未变化，列表已刷新");
        }
        knowledge.setStatus("ON_SHELF");
        knowledge.setUpdatedBy(AdminContext.requireAdminId());
        if (knowledgeMapper.updateById(knowledge) == 0) {
            throw ContentErrors.versionConflict();
        }
        paragraphMapper.update(null, Wrappers.<AiKnowledgeParagraph>lambdaUpdate()
                .eq(AiKnowledgeParagraph::getKnowledgeId, id)
                .set(AiKnowledgeParagraph::getIsOnline, 1));
        writeLog("ON_SHELF", id, knowledge.getTitle(), null, Map.of("status", "OFF_SHELF"),
                Map.of("status", "ON_SHELF"));
        return knowledgeVO(knowledge, null);
    }

    @Override
    @Transactional
    public void deleteDraft(Long id, Integer version) {
        AiKnowledge knowledge = requireKnowledge(id);
        if (version == null || !Objects.equals(version, knowledge.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        if (!"DRAFT".equals(knowledge.getStatus())) {
            throw ContentErrors.stateChanged("只能删除草稿");
        }
        versionMapper.delete(Wrappers.<KnowledgeVersion>lambdaQuery()
                .eq(KnowledgeVersion::getKnowledgeId, id)
                .eq(KnowledgeVersion::getStatus, "DRAFT"));
        applicableMapper.deleteByBiz(BIZ_KNOWLEDGE, id);
        knowledgeMapper.deleteById(id);
        writeLog("DELETE", id, knowledge.getTitle(), null, null, null);
    }

    // ===== 版本与段落 =====

    private KnowledgeVersion draftVersion(AiKnowledge knowledge, String title, Long categoryId, String display,
                                          boolean major, String keywords, List<Long> applicable,
                                          KnowledgeHtml.Parsed parsed, String changeNote, Long sourceTaskId,
                                          KnowledgeVersion existing) {
        KnowledgeVersion draft = existing == null ? new KnowledgeVersion() : existing;
        draft.setKnowledgeId(knowledge.getId());
        draft.setVersionNo(null);
        draft.setStatus("DRAFT");
        draft.setDraftFlag(1);
        draft.setTitle(title);
        draft.setCategoryId(categoryId);
        draft.setDisplayType(display);
        draft.setIsMajorEpidemic(major ? 1 : 0);
        draft.setApplicableCategoryIds(idsOf(applicable));
        draft.setKeywords(keywords);
        draft.setContent(parsed.content());
        draft.setContentText(parsed.contentText());
        draft.setChangeNote(changeNote);
        draft.setSourceTaskId(sourceTaskId);
        draft.setUpdatedBy(AdminContext.requireAdminId());
        if (existing == null) {
            draft.setCreatedBy(AdminContext.requireAdminId());
            versionMapper.insert(draft);
        } else {
            versionMapper.updateById(draft);
        }
        return draft;
    }

    private KnowledgeVersion newVersion(AiKnowledge knowledge, int versionNo, String status, Integer draftFlag,
                                        String title, Long categoryId, String display, boolean major,
                                        String keywords, List<Long> applicable, KnowledgeHtml.Parsed parsed,
                                        String changeNote, Integer rollbackFromNo, Long sourceTaskId) {
        KnowledgeVersion version = new KnowledgeVersion();
        version.setKnowledgeId(knowledge.getId());
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setDraftFlag(draftFlag);
        version.setTitle(title);
        version.setCategoryId(categoryId);
        version.setDisplayType(display);
        version.setIsMajorEpidemic(major ? 1 : 0);
        version.setApplicableCategoryIds(idsOf(applicable));
        version.setKeywords(keywords);
        version.setContent(parsed.content());
        version.setContentText(parsed.contentText());
        version.setChangeNote(changeNote);
        version.setRollbackFromNo(rollbackFromNo);
        version.setSourceTaskId(sourceTaskId);
        version.setPublishedBy(AdminContext.requireAdminId());
        version.setPublishedAt(BeijingTime.now());
        version.setCreatedBy(AdminContext.requireAdminId());
        version.setUpdatedBy(AdminContext.requireAdminId());
        versionMapper.insert(version);
        return version;
    }

    private boolean hasVideo(KnowledgeHtml.Parsed parsed) {
        return parsed.paragraphs().stream().anyMatch(p -> "VIDEO".equals(p.paraType()));
    }

    // ===== 校验与工具 =====

    private AiKnowledge requireKnowledge(Long id) {
        AiKnowledge knowledge = id == null ? null : knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw ContentErrors.notFound();
        }
        return knowledge;
    }

    private static String requireTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            throw ContentErrors.field("title", "请填写标题");
        }
        if (value.length() > 40) {
            throw ContentErrors.field("title", "标题不能超过 40 字");
        }
        return value;
    }

    private static String displayType(String requested, String sourceType, String fallback) {
        if (requested != null && !requested.isBlank()) {
            String value = requested.toUpperCase();
            if (!DISPLAY_TYPES.contains(value)) {
                throw ContentErrors.field("display_type", "展示类型不正确");
            }
            return value;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "DISEASE".equals(sourceType) ? "STANDARD" : "ARTICLE";
    }

    private static String keywordsOf(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String value = keyword.trim();
            if (value.length() > 10) {
                throw ContentErrors.field("keywords", "每个关键词不能超过 10 字");
            }
            distinct.add(value);
        }
        if (distinct.size() > 8) {
            throw ContentErrors.field("keywords", "关键词最多 8 个");
        }
        return distinct.isEmpty() ? null : String.join("、", distinct);
    }

    private void validateApplicable(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        List<Long> found = statMapper.selectProductCategories(new ArrayList<>(distinct)).stream()
                .map(StatAggregates.CategoryRef::getId).toList();
        if (found.size() != distinct.size()) {
            throw ContentErrors.field("applicable_category_ids", "适用品类不存在或已停用");
        }
    }

    private void syncApplicable(Long knowledgeId, List<Long> ids) {
        applicableMapper.deleteByBiz(BIZ_KNOWLEDGE, knowledgeId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : new LinkedHashSet<>(ids)) {
            ContentApplicable entity = new ContentApplicable();
            entity.setBizType(BIZ_KNOWLEDGE);
            entity.setBizId(knowledgeId);
            entity.setCategoryId(id);
            applicableMapper.insert(entity);
        }
    }

    private List<Long> applicableIds(Long knowledgeId) {
        return applicableMapper.selectByBiz(BIZ_KNOWLEDGE, knowledgeId).stream()
                .map(ContentApplicable::getCategoryId).toList();
    }

    private static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                try {
                    ids.add(Long.valueOf(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 历史快照里的脏数据忽略
                }
            }
        }
        return ids;
    }

    private static String idsOf(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().distinct().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse(null);
    }

    private static List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String part : keywords.split("、")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private int nextSortNo() {
        List<AiKnowledge> last = knowledgeMapper.selectList(Wrappers.<AiKnowledge>lambdaQuery()
                .orderByDesc(AiKnowledge::getSortNo).last("LIMIT 1"));
        return last.isEmpty() || last.get(0).getSortNo() == null ? 1 : last.get(0).getSortNo() + 1;
    }

    private KnowledgeVO knowledgeVO(AiKnowledge knowledge, Integer newVersionNo) {
        AiKnowledge fresh = knowledgeMapper.selectById(knowledge.getId());
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(knowledge.getId());
        vo.setStatus(fresh == null ? knowledge.getStatus() : fresh.getStatus());
        vo.setOnlineVersionNo(fresh == null ? knowledge.getVersionNo() : fresh.getVersionNo());
        vo.setNewVersionNo(newVersionNo);
        vo.setAffectedAnswers30d(null);
        vo.setVersion(fresh == null ? knowledge.getVersion() : fresh.getVersion());
        return vo;
    }

    private void writeLog(String action, Long id, String title, String reason, Object before, Object after) {
        Map<String, Object> beforeFields = new LinkedHashMap<>();
        Map<String, Object> afterFields = new LinkedHashMap<>();
        if (before instanceof Map<?, ?> map) {
            map.forEach((k, v) -> beforeFields.put(String.valueOf(k), v));
        }
        if (after instanceof Map<?, ?> map) {
            map.forEach((k, v) -> afterFields.put(String.valueOf(k), v));
        }
        logs.record(OperationLogEntry.builder()
                .opType("KNOWLEDGE")
                .opAction(action)
                .bizType("KNOWLEDGE")
                .bizId(id)
                .targetName(title)
                .reason(reason)
                .before(beforeFields.isEmpty() ? null : beforeFields)
                .after(afterFields.isEmpty() ? null : afterFields)
                .build());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
