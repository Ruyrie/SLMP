package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.entity.AiCorrectionRow;
import com.zhimu.server.admin.content.entity.AiQaReview;
import com.zhimu.server.admin.content.mapper.AiCorrectionAdminMapper;
import com.zhimu.server.admin.content.mapper.AiQaAdminMapper;
import com.zhimu.server.admin.content.mapper.AiQaReviewMapper;
import com.zhimu.server.admin.content.mapper.CitationRow;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.NameRow;
import com.zhimu.server.admin.content.mapper.QaDetailRow;
import com.zhimu.server.admin.content.mapper.QaRoundRow;
import com.zhimu.server.admin.content.mapper.QaRow;
import com.zhimu.server.admin.content.mapper.QaSummaryRow;
import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.AiQaVos;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.CacheJson;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WA-C05 AI 问答与更正（ADM-C57–C64）。 */
@Service
@RequiredArgsConstructor
public class AiQaAdminService {

    private static final TypeReference<LinkedHashMap<String, Object>> CONTEXT_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> IMAGE_IDS_TYPE = new TypeReference<>() {
    };
    private static final Set<String> SEGMENTS = Set.of("ALL", "PENDING_USELESS", "NO_BASIS", "SPECIAL", "CORRECTED");
    private static final Pattern QUESTION_WORDS = Pattern.compile("(吗|呢|怎么办|怎么|如何|能不能|可以|是否|多久|多少|什么|为什么)");
    private static final Pattern PUNCTUATION = Pattern.compile("[\\s？?。！!，,、；;：:（）()【】\\[\\]“”\"'·…—\\-]+");

    private final AiQaAdminMapper qaMapper;
    private final AiCorrectionAdminMapper correctionMapper;
    private final AiQaReviewMapper reviewMapper;
    private final ContentLookupMapper lookupMapper;
    private final FileService fileService;
    private final OperationLogService operationLogService;
    private final AdminMessageService adminMessageService;
    private final AdminPermissionService adminPermissionService;
    private final ContentExportService exportService;

    // ===================== 查询 =====================

    public AiQaVos.SummaryVO summary(ContentQueries.QaSummaryQuery query) {
        LocalDate to = query.getDateTo() == null ? BeijingTime.today() : query.getDateTo();
        LocalDate from = query.getDateFrom() == null ? to.minusDays(6) : query.getDateFrom();
        validateRange(from, to);
        QaSummaryRow row = qaMapper.selectSummary(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        AiQaVos.SummaryVO vo = new AiQaVos.SummaryVO();
        vo.setDateFrom(from);
        vo.setDateTo(to);
        if (row == null) {
            return vo;
        }
        vo.setQaCount(nvl(row.getQaCount()));
        vo.setAskerCount(nvl(row.getAskerCount()));
        vo.setRatedCount(nvl(row.getRatedCount()));
        vo.setUsefulCount(nvl(row.getUsefulCount()));
        vo.setPendingUselessCount(nvl(row.getPendingUselessCount()));
        vo.setNoBasisCount(nvl(row.getNoBasisCount()));
        vo.setUsefulRate(vo.getRatedCount() == 0 ? null
                : BigDecimal.valueOf(vo.getUsefulCount()).divide(BigDecimal.valueOf(vo.getRatedCount()), 4, RoundingMode.HALF_UP));
        vo.setNoBasisRatio(vo.getQaCount() == 0 ? null
                : BigDecimal.valueOf(vo.getNoBasisCount()).divide(BigDecimal.valueOf(vo.getQaCount()), 4, RoundingMode.HALF_UP));
        return vo;
    }

    public PageVO<AiQaVos.RowVO> page(ContentQueries.QaQuery query) {
        LocalDate to = query.getDateTo() == null ? BeijingTime.today() : query.getDateTo();
        LocalDate from = query.getDateFrom() == null ? to.minusDays(6) : query.getDateFrom();
        validateRange(from, to);
        String segment = StringUtils.hasText(query.getSegment()) ? query.getSegment().trim().toUpperCase() : "ALL";
        if (!SEGMENTS.contains(segment)) {
            throw ContentErrors.field("segment", "分段不正确");
        }
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        if (keyword != null && keyword.codePointCount(0, keyword.length()) > 40) {
            throw ContentErrors.field("keyword", "搜索关键词不能超过 40 字");
        }
        List<Long> farmIds = keyword == null ? List.of() : lookupMapper.selectFarmIdsByName(keyword);
        List<Long> userIds = keyword == null ? List.of() : lookupMapper.selectUserIdsByName(keyword);
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = to.plusDays(1).atStartOfDay();
        boolean onlySpotCheck = Boolean.TRUE.equals(query.getIsSpotCheck());
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        IPage<QaRow> result = qaMapper.selectPage(new Page<>(page, size), query, fromTime, toTime,
                farmIds, userIds, segment, onlySpotCheck);
        List<AiQaVos.RowVO> list = result.getRecords().stream().map(this::toRow).toList();
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", (int) qaMapper.countPage(query, fromTime, toTime, farmIds, userIds, "ALL", onlySpotCheck));
        for (String key : List.of("PENDING_USELESS", "NO_BASIS", "SPECIAL", "CORRECTED")) {
            tabs.put(key, (int) qaMapper.countPage(query, fromTime, toTime, farmIds, userIds, key, onlySpotCheck));
        }
        return PageVO.of(list, result.getTotal(), page, size).withTabCounts(tabs);
    }

    /** ADM-C65 导出问答记录（不含批次快照与回答全文） */
    public AiQaVos.ExportVO export(ContentQueries.QaQuery query) {
        LocalDate to = query.getDateTo() == null ? BeijingTime.today() : query.getDateTo();
        LocalDate from = query.getDateFrom() == null ? to.minusDays(6) : query.getDateFrom();
        validateRange(from, to);
        String segment = StringUtils.hasText(query.getSegment()) ? query.getSegment().trim().toUpperCase() : "ALL";
        if (!SEGMENTS.contains(segment)) {
            throw ContentErrors.field("segment", "分段不正确");
        }
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        List<Long> farmIds = keyword == null ? List.of() : lookupMapper.selectFarmIdsByName(keyword);
        List<Long> userIds = keyword == null ? List.of() : lookupMapper.selectUserIdsByName(keyword);
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = to.plusDays(1).atStartOfDay();
        boolean onlySpotCheck = Boolean.TRUE.equals(query.getIsSpotCheck());
        long total = qaMapper.countPage(query, fromTime, toTime, farmIds, userIds, segment, onlySpotCheck);
        if (total > ContentExportService.MAX_ROWS) {
            throw ContentErrors.field("page", "导出超过 5 万行，请缩小筛选范围");
        }
        List<QaRow> rows = qaMapper.selectForExport(query, fromTime, toTime, farmIds, userIds,
                segment, onlySpotCheck);
        List<List<Object>> values = new ArrayList<>();
        for (QaRow row : rows) {
            values.add(List.of(
                    truncate(row.getQuestionText(), 300) == null ? "" : truncate(row.getQuestionText(), 300),
                    row.getFarmName() == null ? "" : row.getFarmName(),
                    row.getAskerName() == null ? "" : row.getAskerName(),
                    row.getBatchNo() == null ? "" : row.getBatchNo(),
                    ContentTexts.answerType(row.getAnswerType()) == null ? "" : ContentTexts.answerType(row.getAnswerType()),
                    ContentTexts.rating(row.getRating()) == null ? "" : ContentTexts.rating(row.getRating()),
                    ContentTexts.handleStatus(row.getHandleStatus()) == null ? ""
                            : ContentTexts.handleStatus(row.getHandleStatus()),
                    row.getAskedAt() == null ? "" : BeijingTime.format(row.getAskedAt())));
        }
        ContentExportService.ExportResult result = exportService.export(
                "AI问答记录_" + BeijingTime.today().format(DateTimeFormatter.BASIC_ISO_DATE),
                "AI问答记录",
                List.of("问题", "养殖场", "提问人", "关联批次", "回答类型", "评价", "处理状态", "提问时间"),
                values);
        AiQaVos.ExportVO vo = new AiQaVos.ExportVO();
        vo.setFileId(result.fileId());
        vo.setFileName(result.fileName());
        vo.setStatus(result.status());
        vo.setUrl(result.url());
        vo.setRowCount(result.rowCount());
        return vo;
    }

    public AiQaVos.DetailVO detail(Long messageId) {        QaDetailRow row = qaMapper.selectDetail(messageId);
        if (row == null) {
            throw ContentErrors.notFound();
        }
        AiQaVos.DetailVO vo = new AiQaVos.DetailVO();
        vo.setMessageId(row.getMessageId());
        vo.setHandleStatus(row.getHandleStatus());
        vo.setHandleStatusText(ContentTexts.handleStatus(row.getHandleStatus()));
        vo.setFocusRoundNo(row.getRoundNo());
        vo.setAnswerType(row.getAnswerType());
        vo.setAnswerTypeText(ContentTexts.answerType(row.getAnswerType()));
        vo.setRating(row.getRating());
        vo.setRatingText("OFF_TOPIC".equals(row.getAnswerType()) ? "不能评价" : ContentTexts.rating(row.getRating()));

        AiQaVos.SessionVO session = new AiQaVos.SessionVO();
        session.setSessionId(row.getSessionId());
        session.setFarmName(row.getFarmName());
        session.setAskerName(row.getAskerName());
        session.setAskerRole(roleText(row.getAskerRole()));
        session.setStartedAt(row.getSessionStartedAt());
        session.setRoundCount(row.getRoundCount());
        vo.setSession(session);
        if (row.getBatchSnapshotJson() != null && !row.getBatchSnapshotJson().isBlank()) {
            try {
                vo.setContext(CacheJson.read(row.getBatchSnapshotJson(), CONTEXT_TYPE));
            } catch (RuntimeException ignored) {
                vo.setContext(null);
            }
        }
        List<QaRoundRow> rounds = qaMapper.selectRounds(row.getSessionId());
        List<Long> messageIds = rounds.stream().map(QaRoundRow::getMessageId).toList();
        Map<Long, List<CitationRow>> citations = new HashMap<>();
        if (!messageIds.isEmpty()) {
            for (CitationRow citation : qaMapper.selectCitations(messageIds)) {
                citations.computeIfAbsent(citation.getMessageId(), key -> new ArrayList<>()).add(citation);
            }
        }
        List<AiQaVos.RoundVO> roundVos = new ArrayList<>();
        for (QaRoundRow round : rounds) {
            AiQaVos.RoundVO roundVo = new AiQaVos.RoundVO();
            roundVo.setMessageId(round.getMessageId());
            roundVo.setRoundNo(round.getRoundNo());
            roundVo.setAskedAt(round.getAskedAt());
            roundVo.setQuestionText(round.getQuestionText());
            roundVo.setStatus(round.getStatus());
            roundVo.setAnswerType(round.getAnswerType());
            roundVo.setAnswerTypeText(ContentTexts.answerType(round.getAnswerType()));
            roundVo.setAnswerText(round.getAnswerText());
            roundVo.setRating(round.getRating());
            roundVo.setRatingText("OFF_TOPIC".equals(round.getAnswerType()) ? "不能评价"
                    : ContentTexts.rating(round.getRating()));
            roundVo.setRatedAt(round.getRatedAt());
            for (Long fileId : imageIds(round.getImageFileIds())) {
                var file = fileService.fileVo(fileId);
                if (file != null) {
                    roundVo.getImages().add(file);
                }
            }
            for (CitationRow citation : citations.getOrDefault(round.getMessageId(), List.of())) {
                AiQaVos.CitationVO citationVo = new AiQaVos.CitationVO();
                citationVo.setSeqNo(citation.getSeqNo());
                citationVo.setTitle(citation.getTitleSnapshot());
                citationVo.setSourceType(citation.getSourceType());
                citationVo.setIconType(citation.getIconType());
                citationVo.setKnowledgeId(citation.getKnowledgeId());
                citationVo.setVersionNo(citation.getKnowledgeVersionNo());
                citationVo.setParaKey(citation.getParaKey());
                roundVo.getCitations().add(citationVo);
            }
            roundVos.add(roundVo);
        }
        vo.setRounds(roundVos);
        AiQaReview review = reviewMapper.selectOne(Wrappers.<AiQaReview>lambdaQuery()
                .eq(AiQaReview::getMessageId, messageId).last("LIMIT 1"));
        if (review != null && Boolean.TRUE.equals(review.getIsSpotCheck())) {
            AiQaVos.SpotCheckVO spot = new AiQaVos.SpotCheckVO();
            spot.setTitle(review.getSpotCheckTitle());
            spot.setContent(review.getSpotCheckContent());
            spot.setCheckedAt(review.getSpotCheckedAt());
            vo.setSpotCheck(spot);
        }
        AiCorrectionRow published = correctionMapper.selectPublished(messageId);
        if (published != null) {
            vo.setCorrection(toCorrection(published));
        }
        vo.setSuggestTaskTarget(firstPlatformCitation(row.getMessageId(), citations));
        vo.setCanCorrect("ANSWERED".equals(row.getStatus()) && published == null);
        vo.setCanUpdateCorrection(published != null);
        vo.setCanNoAction("PENDING".equals(row.getHandleStatus()));
        vo.setCanCreateTask("PENDING".equals(row.getHandleStatus()));
        return vo;
    }

    // ===================== 写操作 =====================

    @Transactional
    public AiQaVos.CorrectionVO publishCorrection(Long messageId, ContentRequests.CorrectionPublishRequest request) {
        QaDetailRow row = qaMapper.selectDetail(messageId);
        if (row == null) {
            throw ContentErrors.notFound();
        }
        if (!"ANSWERED".equals(row.getStatus())) {
            throw ContentErrors.stateChanged("这条回答未完成，不能补充更正说明");
        }
        if (correctionMapper.selectPublished(messageId) != null) {
            throw ContentErrors.stateChanged("这条回答已有更正说明，请修改原说明");
        }
        String content = content(request == null ? null : request.getContent());
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        AiCorrectionRow correction = new AiCorrectionRow();
        correction.setMessageId(messageId);
        correction.setSessionId(row.getSessionId());
        correction.setFarmId(row.getFarmId());
        correction.setUserId(row.getUserId());
        correction.setContent(content);
        correction.setStatus("PUBLISHED");
        correction.setPublishedFlag(true);
        correction.setPublishedAt(now);
        correction.setFirstPublishedAt(now);
        correction.setPublishedBy(adminId);
        correction.setVersion(0);
        correction.setCreatedBy(adminId);
        try {
            correctionMapper.insert(correction);
        } catch (DuplicateKeyException e) {
            throw ContentErrors.stateChanged("这条回答已有更正说明，请修改原说明");
        }
        changeHandleStatusBestEffort(messageId, row.getHandleStatus(), "CORRECTED");
        qaMapper.recomputeSessionCorrection(row.getSessionId());
        upsertReview(messageId, row.getSessionId(), "CORRECT");
        if (request != null && Boolean.TRUE.equals(request.getCreateTask())) {
            createTaskMessage(messageId, row, request.getTaskTitle(), request.getTaskTargetKnowledgeId(),
                    request.getAssigneeId(), content, now);
        }
        log(messageId, "PUBLISH", row.getQuestionText(), null, "已发布更正说明");
        return toCorrection(correctionMapper.selectByIdRow(correction.getId()));
    }

    @Transactional
    public AiQaVos.CorrectionVO updateCorrection(Long messageId, ContentRequests.CorrectionUpdateRequest request) {
        AiCorrectionRow correction = correctionMapper.selectPublished(messageId);
        if (correction == null) {
            throw ContentErrors.stateChanged("更正说明已撤回，列表已刷新");
        }
        if (request == null || request.getVersion() == null || !request.getVersion().equals(correction.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String content = content(request.getContent());
        LocalDateTime now = BeijingTime.now();
        int rows = correctionMapper.update(null, Wrappers.<AiCorrectionRow>lambdaUpdate()
                .eq(AiCorrectionRow::getId, correction.getId())
                .eq(AiCorrectionRow::getVersion, request.getVersion())
                .eq(AiCorrectionRow::getStatus, "PUBLISHED")
                .set(AiCorrectionRow::getContent, content)
                .set(AiCorrectionRow::getPublishedAt, now)
                .set(AiCorrectionRow::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw ContentErrors.versionConflict();
        }
        log(messageId, "UPDATE", null, null, "已修改更正说明");
        return toCorrection(correctionMapper.selectPublished(messageId));
    }

    @Transactional
    public AiQaVos.CorrectionVO withdrawCorrection(Long messageId,
                                                   ContentRequests.CorrectionWithdrawRequest request) {
        AiCorrectionRow correction = correctionMapper.selectPublished(messageId);
        if (correction == null) {
            throw ContentErrors.stateChanged("更正说明已撤回，列表已刷新");
        }
        String reason = request == null ? null : trim(request.getWithdrawReason());
        if (reason == null) {
            throw ContentErrors.reasonEmpty("请填写撤回原因");
        }
        if (reason.codePointCount(0, reason.length()) > 100) {
            throw ContentErrors.field("withdraw_reason", "撤回原因不能超过 100 字");
        }
        if (request.getVersion() == null || !request.getVersion().equals(correction.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        int rows = correctionMapper.update(null, Wrappers.<AiCorrectionRow>lambdaUpdate()
                .eq(AiCorrectionRow::getId, correction.getId())
                .eq(AiCorrectionRow::getVersion, request.getVersion())
                .eq(AiCorrectionRow::getStatus, "PUBLISHED")
                .set(AiCorrectionRow::getStatus, "WITHDRAWN")
                .set(AiCorrectionRow::getPublishedFlag, null)
                .set(AiCorrectionRow::getWithdrawnAt, now)
                .set(AiCorrectionRow::getWithdrawnBy, adminId)
                .set(AiCorrectionRow::getWithdrawReason, reason)
                .set(AiCorrectionRow::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw ContentErrors.versionConflict();
        }
        changeHandleStatusBestEffort(messageId, "CORRECTED", "PENDING");
        qaMapper.recomputeSessionCorrection(correction.getSessionId());
        upsertReview(messageId, correction.getSessionId(), "WITHDRAW");
        log(messageId, "WITHDRAW", null, reason, "已撤回更正说明");
        return toCorrection(correctionMapper.selectByIdRow(correction.getId()));
    }

    @Transactional
    public void noAction(Long messageId, ContentRequests.NoActionRequest request) {
        QaDetailRow row = qaMapper.selectDetail(messageId);
        if (row == null) {
            throw ContentErrors.notFound();
        }
        if (!"PENDING".equals(row.getHandleStatus())) {
            throw ContentErrors.stateChanged("这条问答已处理，列表已刷新");
        }
        int rows = qaMapper.updateHandleStatus(messageId, "PENDING", "NO_ACTION");
        if (rows == 0) {
            throw ContentErrors.stateChanged("这条问答已处理，列表已刷新");
        }
        upsertReview(messageId, row.getSessionId(), "NO_ACTION");
        log(messageId, "NO_ACTION", row.getQuestionText(), null, "已标为无需处理");
    }

    public AiQaVos.TaskSuggestionVO taskSuggestion(Long messageId) {
        QaDetailRow row = qaMapper.selectDetail(messageId);
        if (row == null) {
            throw ContentErrors.notFound();
        }
        AiQaVos.TaskSuggestionVO vo = new AiQaVos.TaskSuggestionVO();
        vo.setSuggestedTitle(suggestTitle(row.getQuestionText()));
        LocalDateTime from = BeijingTime.now().minusDays(30);
        List<String> keywords = keywords(row.getQuestionText());
        vo.setSimilarKeywords(keywords);
        vo.setSimilarCount30d((int) qaMapper.countSimilarNoBasis(from, keywords, messageId));
        List<QaRoundRow> rounds = qaMapper.selectRounds(row.getSessionId());
        List<Long> ids = rounds.stream().map(QaRoundRow::getMessageId).toList();
        if (!ids.isEmpty()) {
            for (CitationRow citation : qaMapper.selectCitations(ids)) {
                if (("PLATFORM".equals(citation.getSourceType()) || "DISEASE".equals(citation.getSourceType()))
                        && citation.getCategoryNameSnapshot() != null) {
                    vo.setSuggestedCategoryId(lookupMapper.selectCategoryIdByName(citation.getCategoryNameSnapshot()));
                    break;
                }
            }
        }
        return vo;
    }

    @Transactional
    public AiQaVos.TaskRefVO createTask(Long messageId, ContentRequests.TaskCreateRequest request) {
        QaDetailRow row = qaMapper.selectDetail(messageId);
        if (row == null) {
            throw ContentErrors.notFound();
        }
        if ("TASKED".equals(row.getHandleStatus())) {
            throw ContentErrors.stateChanged("这条问答已有待处理的补充任务");
        }
        String title = request == null ? null : trim(request.getTitle());
        if (title == null || title.codePointCount(0, title.length()) > 40) {
            throw ContentErrors.field("title", "请填写任务标题，最多 40 字");
        }
        Long assigneeId = request.getAssigneeId() == null ? AdminContext.requireAdminId() : request.getAssigneeId();
        if (!adminPermissionService.permissionsOf(assigneeId).contains("WA-C04:HANDLE")) {
            throw ContentErrors.field("assignee_id", "被指派人没有知识库处理权限");
        }
        LocalDate dueDate = request.getDueDate() == null ? BeijingTime.today().plusDays(7) : request.getDueDate();
        if (dueDate.isBefore(BeijingTime.today())) {
            throw ContentErrors.field("due_date", "期望完成日期不能早于今天");
        }
        LocalDateTime now = BeijingTime.now();
        if (!"CORRECTED".equals(row.getHandleStatus())) {
            changeHandleStatusBestEffort(messageId, row.getHandleStatus(), "TASKED");
        }
        upsertReview(messageId, row.getSessionId(), "TASK");
        Long currentAdmin = AdminContext.requireAdminId();
        if (!assigneeId.equals(currentAdmin)) {
            adminMessageService.send(AdminMessageCommand.builder()
                    .adminId(assigneeId)
                    .msgType("KNOWLEDGE_TASK_ASSIGNED")
                    .level("NORMAL")
                    .title("你有一个知识补充任务：" + title)
                    .summary("期望 " + DateTimeFormatter.ofPattern("M月d日").format(dueDate) + " 完成")
                    .bizType("AI_MESSAGE")
                    .bizId(messageId)
                    .routePage("WA-C04")
                    .mergeKey("KNOWLEDGE_TASK:" + messageId)
                    .build());
        }
        log(messageId, "TASK_CREATE", row.getQuestionText(), null, "已创建知识补充任务：" + title);
        AiQaVos.TaskRefVO vo = new AiQaVos.TaskRefVO();
        vo.setTitle(title);
        vo.setStatus("PENDING");
        return vo;
    }

    // ===================== 内部 =====================

    private AiQaVos.RowVO toRow(QaRow row) {
        AiQaVos.RowVO vo = new AiQaVos.RowVO();
        vo.setMessageId(row.getMessageId());
        vo.setSessionId(row.getSessionId());
        vo.setRoundNo(row.getRoundNo());
        vo.setQuestionText(row.getQuestionText());
        vo.setImageCount(imageIds(row.getImageFileIds()).size());
        vo.setFarmId(row.getFarmId());
        vo.setFarmName(row.getFarmName());
        vo.setAskerId(row.getAskerId());
        vo.setAskerName(row.getAskerName());
        vo.setBatchNo(row.getBatchNo());
        vo.setAnswerType(row.getAnswerType());
        vo.setAnswerTypeText(ContentTexts.answerType(row.getAnswerType()));
        vo.setRating(row.getRating());
        vo.setRatingText("OFF_TOPIC".equals(row.getAnswerType()) ? "不能评价" : ContentTexts.rating(row.getRating()));
        vo.setCanRate(!"OFF_TOPIC".equals(row.getAnswerType()));
        vo.setHandleStatus(row.getHandleStatus());
        vo.setHandleStatusText(ContentTexts.handleStatus(row.getHandleStatus()));
        vo.setIsSpotCheck(Boolean.TRUE.equals(row.getIsSpotCheck()));
        vo.setAskedAt(row.getAskedAt());
        return vo;
    }

    private AiQaVos.CorrectionVO toCorrection(AiCorrectionRow row) {
        AiQaVos.CorrectionVO vo = new AiQaVos.CorrectionVO();
        vo.setCorrectionId(row.getId());
        vo.setMessageId(row.getMessageId());
        vo.setContent(row.getContent());
        vo.setStatus(row.getStatus());
        vo.setStatusText("PUBLISHED".equals(row.getStatus()) ? "已发布" : "已撤回");
        vo.setPublishedAt(row.getPublishedAt());
        vo.setFirstPublishedAt(row.getFirstPublishedAt());
        vo.setPublishedByName(adminName(row.getPublishedBy()));
        vo.setWithdrawnAt(row.getWithdrawnAt());
        vo.setWithdrawReason(row.getWithdrawReason());
        vo.setVersion(row.getVersion());
        return vo;
    }

    private AiQaVos.KnowledgeTargetVO firstPlatformCitation(Long messageId,
                                                           Map<Long, List<CitationRow>> citations) {
        for (CitationRow citation : citations.getOrDefault(messageId, List.of())) {
            if ("PLATFORM".equals(citation.getSourceType()) || "DISEASE".equals(citation.getSourceType())) {
                AiQaVos.KnowledgeTargetVO target = new AiQaVos.KnowledgeTargetVO();
                target.setKnowledgeId(citation.getKnowledgeId());
                target.setTitle(citation.getTitleSnapshot());
                return target;
            }
        }
        return null;
    }

    private void createTaskMessage(Long messageId, QaDetailRow row, String taskTitle, Long targetKnowledgeId,
                                   Long assigneeId, String content, LocalDateTime now) {
        Long target = assigneeId == null ? AdminContext.requireAdminId() : assigneeId;
        if (!adminPermissionService.permissionsOf(target).contains("WA-C04:HANDLE")) {
            throw ContentErrors.field("assignee_id", "被指派人没有知识库处理权限");
        }
        if (!target.equals(AdminContext.requireAdminId())) {
            adminMessageService.send(AdminMessageCommand.builder()
                    .adminId(target)
                    .msgType("KNOWLEDGE_TASK_ASSIGNED")
                    .level("NORMAL")
                    .title("你有一个知识补充任务：" + (taskTitle == null ? suggestTitle(row.getQuestionText()) : taskTitle))
                    .summary(content.length() > 100 ? content.substring(0, 100) : content)
                    .bizType("AI_MESSAGE")
                    .bizId(messageId)
                    .routePage("WA-C04")
                    .mergeKey("KNOWLEDGE_TASK:" + messageId)
                    .build());
        }
    }

    private void changeHandleStatusBestEffort(Long messageId, String current, String target) {
        int rows = qaMapper.updateHandleStatus(messageId, current, target);
        if (rows == 0 && !java.util.Objects.equals(current, target)) {
            QaDetailRow latest = qaMapper.selectDetail(messageId);
            if (latest != null && !java.util.Objects.equals(latest.getHandleStatus(), target)) {
                qaMapper.updateHandleStatus(messageId, latest.getHandleStatus(), target);
            }
        }
    }

    private void upsertReview(Long messageId, Long sessionId, String action) {
        AiQaReview review = reviewMapper.selectOne(Wrappers.<AiQaReview>lambdaQuery()
                .eq(AiQaReview::getMessageId, messageId).last("LIMIT 1"));
        LocalDateTime now = BeijingTime.now();
        Long adminId = AdminContext.requireAdminId();
        if (review == null) {
            review = new AiQaReview();
            review.setMessageId(messageId);
            review.setSessionId(sessionId);
            review.setIsSpotCheck(false);
            review.setHandledBy(adminId);
            review.setHandledAt(now);
            review.setLastAction(action);
            reviewMapper.insert(review);
        } else {
            review.setHandledBy(adminId);
            review.setHandledAt(now);
            review.setLastAction(action);
            reviewMapper.updateById(review);
        }
    }

    private void log(Long messageId, String action, String question, String reason, String extra) {
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("AI_CORRECTION")
                .opAction(action)
                .bizType("AI_MESSAGE")
                .bizId(messageId)
                .targetName(question == null ? null : truncate(question, 30))
                .reason(reason)
                .extraText(extra)
                .result("SUCCESS")
                .build());
    }

    private String adminName(Long id) {
        if (id == null) {
            return null;
        }
        List<NameRow> rows = lookupMapper.selectAdminNames(List.of(id));
        return rows.isEmpty() ? null : rows.getFirst().getName();
    }

    private static List<Long> imageIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = CacheJson.read(json, IMAGE_IDS_TYPE);
            return ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).toList();
        } catch (RuntimeException e) {
            List<Long> ids = new ArrayList<>();
            Matcher matcher = Pattern.compile("\\d+").matcher(json);
            while (matcher.find()) {
                ids.add(Long.valueOf(matcher.group()));
            }
            return ids;
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ContentErrors.field("date_from", "开始日期不能晚于结束日期");
        }
        if (from.plusDays(90).isBefore(to.plusDays(1))) {
            throw ContentErrors.field("date_from", "时间范围不能超过 90 天");
        }
    }

    private static String content(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw ContentErrors.field("content", "请填写更正说明");
        }
        if (value.codePointCount(0, value.length()) > 200) {
            throw ContentErrors.field("content", "更正说明不能超过 200 字");
        }
        return value;
    }

    private static String suggestTitle(String question) {
        if (question == null) {
            return null;
        }
        String cleaned = PUNCTUATION.matcher(question).replaceAll("");
        cleaned = QUESTION_WORDS.matcher(cleaned).replaceAll("");
        return truncate(cleaned, 20);
    }

    private static List<String> keywords(String question) {
        if (question == null) {
            return List.of();
        }
        String cleaned = PUNCTUATION.matcher(question).replaceAll("");
        cleaned = QUESTION_WORDS.matcher(cleaned).replaceAll("");
        if (cleaned.length() < 2) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        int step = Math.max(2, Math.min(4, cleaned.length() / 3));
        for (int i = 0; i + 2 <= cleaned.length() && result.size() < 3; i += step) {
            result.add(cleaned.substring(i, Math.min(cleaned.length(), i + 2)));
        }
        return new ArrayList<>(result);
    }

    private static String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "OWNER" -> "所有者";
            case "MANAGER" -> "场长";
            case "STAFF" -> "员工";
            default -> null;
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.codePointCount(0, value.length()) <= max ? value
                : value.substring(0, value.offsetByCodePoints(0, max));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
