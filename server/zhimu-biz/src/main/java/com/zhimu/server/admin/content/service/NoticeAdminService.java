package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.entity.NoticeRecipient;
import com.zhimu.server.admin.content.entity.NoticeTarget;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.NameRow;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.mapper.NoticeRecipientMapper;
import com.zhimu.server.admin.content.mapper.NoticeTargetMapper;
import com.zhimu.server.admin.content.mapper.RoleStatRow;
import com.zhimu.server.admin.content.mapper.TimelineRow;
import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.support.NoticeRichText;
import com.zhimu.server.admin.content.vo.NoticeVos;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.support.AdminLogTexts;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** WA-C01 通知发布：列表、指标、详情、保存、定时、发送、取消、撤回、删除草稿。 */
@Service
@RequiredArgsConstructor
public class NoticeAdminService {

    private static final Set<String> ACTIONS = Set.of("DRAFT", "SCHEDULE", "SEND_NOW");
    private static final Set<String> STATUSES = Set.of("DRAFT", "SCHEDULED", "SENT", "REVOKED", "CANCELED");
    private static final List<String> ROLE_ORDER = List.of("OWNER", "MANAGER", "STAFF", "NONE");

    private final NoticeMapper noticeMapper;
    private final NoticeTargetMapper targetMapper;
    private final NoticeRecipientMapper recipientMapper;
    private final NoticeAudienceService audienceService;
    private final NoticeStatService statService;
    private final FileService fileService;
    private final ContentLookupMapper lookupMapper;
    private final OperationLogService operationLogService;
    private final MessageService messageService;
    private final ContentExportService exportService;

    // ===================== 查询 =====================

    public NoticeVos.SummaryVO summary() {
        LocalDateTime today = BeijingTime.today().atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1);
        LocalDateTime nextMonth = monthStart.plusMonths(1);
        LocalDateTime from30 = today.minusDays(30);

        NoticeVos.SummaryVO vo = new NoticeVos.SummaryVO();
        vo.setMonthSentCount(noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .in(Notice::getStatus, "SENT", "REVOKED")
                .ge(Notice::getSentAt, monthStart).lt(Notice::getSentAt, nextMonth)).intValue());
        vo.setMonthImportantCount(noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .in(Notice::getStatus, "SENT", "REVOKED")
                .eq(Notice::getLevel, "IMPORTANT")
                .ge(Notice::getSentAt, monthStart).lt(Notice::getSentAt, nextMonth)).intValue());
        vo.setMonthRevokedCount(noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "REVOKED")
                .ge(Notice::getSentAt, monthStart).lt(Notice::getSentAt, nextMonth)).intValue());
        vo.setScheduledCount(noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "SCHEDULED")).intValue());
        vo.setDraftCount(noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "DRAFT")).intValue());

        List<Notice> recent = noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "SENT")
                .ge(Notice::getSentAt, from30));
        long delivered = 0;
        long read = 0;
        for (Notice notice : recent) {
            delivered += nvl(notice.getDeliveredCount());
            read += nvl(notice.getReadCount());
        }
        vo.setAvgReadRate30d(delivered == 0 ? null : ratio(read, delivered));

        List<Notice> next = noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "SCHEDULED")
                .orderByAsc(Notice::getScheduledAt)
                .last("LIMIT 1"));
        vo.setNextScheduledAt(next.isEmpty() ? null : next.getFirst().getScheduledAt());
        return vo;
    }

    public PageVO<NoticeVos.RowVO> page(ContentQueries.NoticeQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        String keyword = trim(query.getKeyword());
        if (keyword != null && keyword.codePointCount(0, keyword.length()) > 50) {
            throw ContentErrors.field("keyword", "搜索关键词不能超过 50 字");
        }
        var wrapper = Wrappers.<Notice>lambdaQuery()
                .eq(StringUtils.hasText(query.getLevel()), Notice::getLevel, upper(query.getLevel()))
                .eq(StringUtils.hasText(query.getScopeType()), Notice::getScopeType, upper(query.getScopeType()))
                .like(keyword != null, Notice::getTitle, keyword)
                .eq(StringUtils.hasText(query.getStatus()), Notice::getStatus, upper(query.getStatus()))
                .orderByDesc(Notice::getUpdatedAt)
                .orderByDesc(Notice::getId);
        IPage<Notice> result = noticeMapper.selectPage(new Page<>(page, size), wrapper);
        List<Notice> records = result.getRecords();
        Map<Long, List<NoticeTarget>> targets = loadTargets(records.stream().map(Notice::getId).toList());
        Map<Long, String> creatorNames = adminNames(records.stream().map(Notice::getCreatedBy).toList());
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> farmIds = new HashSet<>();
        targets.values().forEach(list -> list.forEach(t -> {
            if ("CATEGORY".equals(t.getTargetType())) {
                categoryIds.add(parseLong(t.getTargetValue()));
            }
            if ("FARM".equals(t.getTargetType())) {
                farmIds.add(parseLong(t.getTargetValue()));
            }
        }));
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : names(lookupMapper.selectCategoryNames(new ArrayList<>(categoryIds)));
        Map<Long, String> farmNames = new HashMap<>();
        if (!farmIds.isEmpty()) {
            audienceService.farms(new ArrayList<>(farmIds)).forEach(f -> farmNames.put(f.getFarmId(), f.getName()));
        }
        List<NoticeVos.RowVO> list = new ArrayList<>();
        for (Notice notice : records) {
            list.add(toRow(notice, targets.getOrDefault(notice.getId(), List.of()),
                    creatorNames.get(notice.getCreatedBy()), categoryNames, farmNames));
        }
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", noticeMapper.selectCount(baseWrapper(keyword, query)).intValue());
        for (String status : List.of("DRAFT", "SCHEDULED", "SENT", "REVOKED", "CANCELED")) {
            tabs.put(status, noticeMapper.selectCount(baseWrapper(keyword, query)
                    .eq(Notice::getStatus, status)).intValue());
        }
        return PageVO.of(list, result.getTotal(), page, size).withTabCounts(tabs);
    }

    /** 写操作后返回最新状态（发送后用于取实际送达数） */
    public NoticeVos.NoticeVO listOne(Long id) {
        return toNoticeVO(requireExisting(id));
    }

    /** ADM-C10 导出阅读数据 */
    public NoticeVos.ExportVO export(ContentQueries.NoticeQuery query) {
        String keyword = trim(query.getKeyword());
        var wrapper = baseWrapper(keyword, query)
                .in(Notice::getStatus, "SENT", "REVOKED")
                .orderByDesc(Notice::getSentAt)
                .orderByDesc(Notice::getId);
        long total = noticeMapper.selectCount(wrapper);
        if (total > ContentExportService.MAX_ROWS) {
            throw ContentErrors.field("page", "导出超过 5 万行，请缩小筛选范围");
        }
        List<Notice> notices = noticeMapper.selectList(wrapper.last("LIMIT " + ContentExportService.MAX_ROWS));
        Map<Long, Map<String, RoleStatRow>> stats = new HashMap<>();
        if (!notices.isEmpty()) {
            List<Long> ids = notices.stream().map(Notice::getId).toList();
            for (RoleStatRow row : recipientMapper.selectRoleStatsBatch(ids)) {
                stats.computeIfAbsent(row.getNoticeId(), key -> new HashMap<>())
                        .put(row.getRoleSnapshot() == null ? "NONE" : row.getRoleSnapshot(), row);
            }
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Notice notice : notices) {
            Map<String, RoleStatRow> roleStats = stats.getOrDefault(notice.getId(), Map.of());
            List<NoticeTarget> targets = targets(notice.getId());
            rows.add(List.of(
                    notice.getTitle(),
                    ContentTexts.noticeLevel(notice.getLevel()),
                    scopeText(notice.getScopeType(), targets),
                    notice.getSentAt() == null ? "" : BeijingTime.format(notice.getSentAt()),
                    nvl(notice.getDeliveredCount()),
                    nvl(notice.getReadCount()),
                    percent(nvl(notice.getReadCount()), nvl(notice.getDeliveredCount())),
                    rolePercent(roleStats.get("OWNER")),
                    rolePercent(roleStats.get("MANAGER")),
                    rolePercent(roleStats.get("STAFF")),
                    nvl(notice.getPushOpenCount()),
                    notice.getRevokedAt() == null ? "" : BeijingTime.format(notice.getRevokedAt())));
        }
        ContentExportService.ExportResult result = exportService.export(
                "通知阅读数据_" + BeijingTime.today().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                "通知阅读数据",
                List.of("标题", "级别", "接收范围", "发送时间", "送达", "已读", "阅读率",
                        "所有者阅读率", "场长阅读率", "员工阅读率", "推送点开", "撤回时间"),
                rows);
        NoticeVos.ExportVO vo = new NoticeVos.ExportVO();
        vo.setFileId(result.fileId());
        vo.setFileName(result.fileName());
        vo.setStatus(result.status());
        vo.setUrl(result.url());
        vo.setRowCount(result.rowCount());
        return vo;
    }

    public NoticeVos.DetailVO detail(Long id) {        Notice notice = requireExisting(id);
        List<NoticeTarget> targets = targets(id);
        if ("SENT".equals(notice.getStatus())) {
            notice = statService.refreshIfStale(notice);
        }
        NoticeVos.DetailVO vo = new NoticeVos.DetailVO();
        Map<Long, String> creatorNames = adminNames(List.of(notice.getCreatedBy() == null ? 0L : notice.getCreatedBy()));
        List<NoticeTarget> finalTargets = targets;
        BeanUtils.copyProperties(toRow(notice, targets, creatorNames.get(notice.getCreatedBy()), Map.of(), Map.of()), vo);
        vo.setContent(notice.getContent());
        if (notice.getImageFileId() != null) {
            vo.setImage(fileService.fileVo(notice.getImageFileId()));
        }
        vo.setSendMode(notice.getSendMode());
        vo.setSendModeText("SCHEDULED".equals(notice.getSendMode()) ? "定时发送" : "立即发送");
        List<String> roles = new ArrayList<>();
        List<String> provinces = new ArrayList<>();
        List<Long> categories = new ArrayList<>();
        List<Long> farms = new ArrayList<>();
        for (NoticeTarget target : finalTargets) {
            switch (target.getTargetType()) {
                case "ROLE" -> roles.add(target.getTargetValue());
                case "PROVINCE" -> provinces.add(target.getTargetValue());
                case "CATEGORY" -> categories.add(parseLong(target.getTargetValue()));
                case "FARM" -> farms.add(parseLong(target.getTargetValue()));
                default -> {
                }
            }
        }
        vo.setRoles(roles);
        vo.setProvinceCodes(provinces);
        vo.setCategoryIds(categories);
        vo.setFarms(audienceService.farms(farms));
        vo.setEstAccountCount(nvl(notice.getEstAccountCount()));
        vo.setEstFarmCount(nvl(notice.getEstFarmCount()));
        if ("SENT".equals(notice.getStatus()) || "REVOKED".equals(notice.getStatus())) {
            int delivered = nvl(notice.getDeliveredCount());
            int read = nvl(notice.getReadCount());
            vo.setDeliveredCount(delivered);
            vo.setReadCount(read);
            vo.setUnreadCount(Math.max(0, delivered - read));
            vo.setPushOpenCount(nvl(notice.getPushOpenCount()));
            vo.setStatAt(notice.getStatAt());
            vo.setRoleStats(roleStats(notice.getId()));
        }
        String creatorName = creatorNames.get(notice.getCreatedBy());
        NoticeVos.Actor creator = new NoticeVos.Actor();
        creator.setName(creatorName);
        creator.setAt(notice.getCreatedAt());
        vo.setCreator(creator);
        if (notice.getSentBy() != null) {
            NoticeVos.Actor sender = new NoticeVos.Actor();
            sender.setName(adminNames(List.of(notice.getSentBy())).get(notice.getSentBy()));
            sender.setAt(notice.getSentAt());
            vo.setSender(sender);
        }
        if (notice.getRevokedBy() != null) {
            vo.setRevokedByName(adminNames(List.of(notice.getRevokedBy())).get(notice.getRevokedBy()));
        }
        vo.setRevokeReason(notice.getRevokeReason());
        if (notice.getCanceledBy() != null) {
            vo.setCanceledByName(adminNames(List.of(notice.getCanceledBy())).get(notice.getCanceledBy()));
        }
        vo.setCancelReason(notice.getCancelReason());
        vo.setTimeline(timeline(notice));
        vo.setVersion(notice.getVersion());
        return vo;
    }

    // ===================== 写操作 =====================

    @Transactional
    public NoticeVos.NoticeVO create(ContentRequests.NoticeSaveRequest request) {
        String action = action(request.getSubmitAction());
        Notice notice = new Notice();
        notice.setTitle(requireTitle(request.getTitle()));
        notice.setLevel(level(request.getLevel()));
        notice.setScopeType(scope(request.getScopeType()));
        notice.setCopiedFromId(validateCopy(request.getCopiedFromId()));
        notice.setDispatchStatus("NONE");
        notice.setDeleted(false);
        notice.setVersion(0);
        notice.setDeliveredCount(0);
        notice.setReadCount(0);
        notice.setPushOpenCount(0);
        notice.setCreatedBy(AdminContext.requireAdminId());
        notice.setUpdatedBy(AdminContext.requireAdminId());
        fillContent(notice, request, action);
        fillAudience(notice, request, action, true);
        applyAction(notice, action, request.getScheduledAt(), true);
        noticeMapper.insert(notice);
        saveTargets(notice.getId(), notice.getScopeType(), request);
        log(notice, action.equals("SCHEDULE") ? "SCHEDULE" : action.equals("SEND_NOW") ? "SEND" : "CREATE",
                null, null, action.equals("SEND_NOW") ? "预计送达 " + notice.getEstAccountCount() + " 个账号" : null,
                afterOf(notice));
        return toNoticeVO(notice);
    }

    @Transactional
    public NoticeVos.NoticeVO update(Long id, ContentRequests.NoticeSaveRequest request) {
        Notice existing = requireExisting(id);
        if (!Set.of("DRAFT", "SCHEDULED").contains(existing.getStatus())) {
            throw ContentErrors.stateChanged("这条通知已发送或已取消，不能再编辑，列表已刷新");
        }
        if (request.getVersion() == null) {
            throw ContentErrors.field("version", "缺少版本号");
        }
        if (!request.getVersion().equals(existing.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String action = action(request.getSubmitAction());
        Notice update = new Notice();
        update.setTitle(requireTitle(request.getTitle()));
        update.setLevel(level(request.getLevel()));
        update.setScopeType(scope(request.getScopeType()));
        update.setUpdatedBy(AdminContext.requireAdminId());
        fillContent(update, request, action);
        fillAudience(update, request, action, false);
        applyAction(update, action, request.getScheduledAt(), false);
        if (request.getCopiedFromId() != null) {
            update.setCopiedFromId(validateCopy(request.getCopiedFromId()));
        }
        var wrapper = Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, id)
                .eq(Notice::getVersion, request.getVersion())
                .in(Notice::getStatus, "DRAFT", "SCHEDULED")
                .set(Notice::getTitle, update.getTitle())
                .set(Notice::getLevel, update.getLevel())
                .set(Notice::getContent, update.getContent())
                .set(Notice::getContentLength, update.getContentLength())
                .set(Notice::getImageFileId, request.getImageFileId())
                .set(Notice::getScopeType, update.getScopeType())
                .set(Notice::getSendMode, update.getSendMode())
                .set(Notice::getScheduledAt, update.getScheduledAt())
                .set(Notice::getStatus, update.getStatus())
                .set(Notice::getEstAccountCount, update.getEstAccountCount())
                .set(Notice::getEstFarmCount, update.getEstFarmCount())
                .set(Notice::getDispatchStatus, update.getDispatchStatus())
                .set(Notice::getSentAt, update.getSentAt())
                .set(Notice::getSentBy, update.getSentBy())
                .set(Notice::getUpdatedBy, update.getUpdatedBy())
                .set(Notice::getUpdatedAt, BeijingTime.now())
                .setSql("version = version + 1");
        int rows = noticeMapper.update(null, wrapper);
        if (rows == 0) {
            throw conflictOrState(id);
        }
        saveTargets(id, update.getScopeType(), request);
        log(update, action.equals("SCHEDULE") ? "SCHEDULE" : action.equals("SEND_NOW") ? "SEND" : "UPDATE",
                null, null, null, afterOf(update));
        Notice saved = noticeMapper.selectById(id);
        return toNoticeVO(saved);
    }

    @Transactional
    public NoticeVos.NoticeVO cancel(Long id, ContentRequests.NoticeReasonRequest request) {
        Notice notice = requireExisting(id);
        String reason = reason(request == null ? null : request.getReason(), "请填写取消原因");
        if (!"SCHEDULED".equals(notice.getStatus())) {
            throw ContentErrors.stateChanged("这条通知已到点发送，不能取消；如需收回请撤回");
        }
        Integer version = request == null ? null : request.getVersion();
        if (version == null || !version.equals(notice.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        LocalDateTime now = BeijingTime.now();
        int rows = noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, id)
                .eq(Notice::getStatus, "SCHEDULED")
                .eq(Notice::getVersion, version)
                .set(Notice::getStatus, "CANCELED")
                .set(Notice::getCanceledAt, now)
                .set(Notice::getCanceledBy, AdminContext.requireAdminId())
                .set(Notice::getCancelReason, reason)
                .set(Notice::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw conflictOrState(id, "这条通知已到点发送，不能取消；如需收回请撤回");
        }
        Notice saved = noticeMapper.selectById(id);
        log(saved, "CANCEL", reason, null, null, null);
        return toNoticeVO(saved);
    }

    @Transactional
    public NoticeVos.NoticeVO revoke(Long id, ContentRequests.NoticeReasonRequest request) {
        Notice notice = requireExisting(id);
        String reason = reason(request == null ? null : request.getReason(), "请填写撤回原因");
        if (!"SENT".equals(notice.getStatus())) {
            throw ContentErrors.stateChanged("只有已发送的通知可以撤回");
        }
        Integer version = request == null ? null : request.getVersion();
        if (version == null || !version.equals(notice.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        LocalDateTime now = BeijingTime.now();
        int readBefore = nvl(notice.getReadCount());
        int rows = noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, id)
                .eq(Notice::getStatus, "SENT")
                .eq(Notice::getVersion, version)
                .set(Notice::getStatus, "REVOKED")
                .set(Notice::getRevokedAt, now)
                .set(Notice::getRevokedBy, AdminContext.requireAdminId())
                .set(Notice::getRevokeReason, reason)
                .set(Notice::getReadBeforeRevoke, readBefore)
                .set(Notice::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw conflictOrState(id, "只有已发送的通知可以撤回");
        }
        messageService.hideUnreadByBiz(BizType.NOTICE, id, "NOTICE_REVOKED");
        recipientMapper.markUnreadHidden(id);
        Notice saved = noticeMapper.selectById(id);
        log(saved, "REVOKE", reason, null, "撤回前已读 " + readBefore + " 个账号", null);
        return toNoticeVO(saved);
    }

    @Transactional
    public void delete(Long id, Integer version) {
        Notice notice = noticeMapper.selectAnyById(id);
        if (notice == null) {
            throw ContentErrors.notFound();
        }
        if (Boolean.TRUE.equals(notice.getDeleted())) {
            throw ContentErrors.noticeDeleted();
        }
        if (!"DRAFT".equals(notice.getStatus())) {
            throw ContentErrors.stateChanged("只能删除草稿");
        }
        if (version != null && !version.equals(notice.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        noticeMapper.deleteById(id);
        log(notice, "DELETE", null, null, null, null);
    }

    // ===================== 内部 =====================

    private void fillContent(Notice notice, ContentRequests.NoticeSaveRequest request, String action) {
        String content = request.getContent();
        if ("DRAFT".equals(action) && !StringUtils.hasText(content)) {
            notice.setContent(null);
            notice.setContentLength(0);
        } else {
            String sanitized = NoticeRichText.sanitize(content);
            int length = NoticeRichText.contentLength(sanitized);
            if ("DRAFT".equals(action) && !StringUtils.hasText(sanitized)) {
                notice.setContent(null);
                notice.setContentLength(0);
            } else {
                if (!StringUtils.hasText(sanitized)) {
                    throw ContentErrors.field("content", "请填写正文");
                }
                if (length > NoticeRichText.CONTENT_MAX) {
                    throw ContentErrors.field("content", "正文不能超过 2000 字");
                }
                notice.setContent(sanitized);
                notice.setContentLength(length);
            }
        }
        if (request.getImageFileId() != null) {
            validateImage(request.getImageFileId());
        }
        notice.setImageFileId(request.getImageFileId());
    }

    private void fillAudience(Notice notice, ContentRequests.NoticeSaveRequest request, String action,
                              boolean creating) {
        String scope = notice.getScopeType();
        if (!"DRAFT".equals(action)) {
            audienceService.validate(scope, audienceService.cleanRoles(request.getRoles()),
                    audienceService.cleanStrings(request.getProvinceCodes()),
                    audienceService.cleanIds(request.getFarmIds()));
        }
        NoticeAudienceService.Result result;
        try {
            result = audienceService.resolve(scope, request.getRoles(), request.getProvinceCodes(),
                    request.getCategoryIds(), request.getFarmIds(), !"DRAFT".equals(action));
        } catch (BizException e) {
            if ("DRAFT".equals(action)) {
                result = new NoticeAudienceService.Result(List.of(), 0);
            } else {
                throw e;
            }
        }
        notice.setEstAccountCount(result.recipients().size());
        notice.setEstFarmCount(result.farmCount());
        if (!"DRAFT".equals(action) && result.recipients().isEmpty()) {
            throw ContentErrors.field("scope_type", "当前条件下没有可送达的账号");
        }
        if ("DRAFT".equals(action) && request.getScopeType() != null
                && !"ALL".equals(scope) && result.recipients().isEmpty()) {
            // 草稿允许条件不完整，预计送达保持 0
            notice.setEstAccountCount(0);
            notice.setEstFarmCount(0);
        }
    }

    private void applyAction(Notice notice, String action, LocalDateTime scheduledAt, boolean creating) {
        LocalDateTime now = BeijingTime.now();
        Long adminId = AdminContext.requireAdminId();
        switch (action) {
            case "DRAFT" -> {
                notice.setStatus("DRAFT");
                notice.setSendMode("NOW");
                notice.setScheduledAt(null);
                notice.setDispatchStatus("NONE");
                notice.setSentAt(null);
                notice.setSentBy(null);
            }
            case "SCHEDULE" -> {
                if (scheduledAt == null) {
                    throw ContentErrors.field("scheduled_at", "请选择定时发送时间");
                }
                if (scheduledAt.isBefore(now.plusMinutes(10))) {
                    throw ContentErrors.field("scheduled_at", "定时发送时间至少在 10 分钟以后");
                }
                notice.setStatus("SCHEDULED");
                notice.setSendMode("SCHEDULED");
                notice.setScheduledAt(scheduledAt);
                notice.setDispatchStatus("NONE");
                notice.setSentAt(null);
                notice.setSentBy(adminId);
            }
            default -> {
                notice.setStatus("SENT");
                notice.setSendMode("NOW");
                notice.setScheduledAt(null);
                notice.setDispatchStatus("PENDING");
                notice.setSentAt(now);
                notice.setSentBy(adminId);
            }
        }
        if (!creating) {
            notice.setUpdatedBy(adminId);
        }
    }

    private void saveTargets(Long noticeId, String scope, ContentRequests.NoticeSaveRequest request) {
        targetMapper.delete(Wrappers.<NoticeTarget>lambdaQuery().eq(NoticeTarget::getNoticeId, noticeId));
        if ("ALL".equals(scope)) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        List<NoticeTarget> targets = new ArrayList<>();
        if ("CONDITION".equals(scope)) {
            for (String role : audienceService.cleanRoles(request.getRoles())) {
                targets.add(target(noticeId, "ROLE", role, now));
            }
            for (String province : audienceService.cleanStrings(request.getProvinceCodes())) {
                targets.add(target(noticeId, "PROVINCE", province, now));
            }
            for (Long categoryId : audienceService.cleanIds(request.getCategoryIds())) {
                targets.add(target(noticeId, "CATEGORY", String.valueOf(categoryId), now));
            }
        } else if ("FARM".equals(scope)) {
            for (Long farmId : audienceService.cleanIds(request.getFarmIds())) {
                targets.add(target(noticeId, "FARM", String.valueOf(farmId), now));
            }
        }
        for (NoticeTarget target : targets) {
            targetMapper.insert(target);
        }
    }

    private NoticeTarget target(Long noticeId, String type, String value, LocalDateTime now) {
        NoticeTarget target = new NoticeTarget();
        target.setNoticeId(noticeId);
        target.setTargetType(type);
        target.setTargetValue(value);
        target.setCreatedAt(now);
        return target;
    }

    private NoticeVos.RowVO toRow(Notice notice, List<NoticeTarget> targets, String creatorName,
                                  Map<Long, String> categoryNames, Map<Long, String> farmNames) {
        NoticeVos.RowVO vo = new NoticeVos.RowVO();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setImageCount(notice.getImageFileId() == null ? 0 : 1);
        vo.setContentLength(nvl(notice.getContentLength()));
        vo.setLevel(notice.getLevel());
        vo.setLevelText(ContentTexts.noticeLevel(notice.getLevel()));
        vo.setScopeType(notice.getScopeType());
        vo.setScopeText(scopeText(notice.getScopeType(), targets));
        vo.setConditionText(conditionText(notice.getScopeType(), targets, categoryNames, farmNames));
        boolean sent = "SENT".equals(notice.getStatus()) || "REVOKED".equals(notice.getStatus());
        vo.setAccountCount(sent ? nvl(notice.getDeliveredCount()) : nvl(notice.getEstAccountCount()));
        vo.setStatus(notice.getStatus());
        vo.setStatusText(ContentTexts.noticeStatus(notice.getStatus()));
        vo.setScheduledAt(notice.getScheduledAt());
        vo.setSentAt(notice.getSentAt());
        vo.setRevokedAt(notice.getRevokedAt());
        vo.setCanceledAt(notice.getCanceledAt());
        if ("SENT".equals(notice.getStatus()) && nvl(notice.getDeliveredCount()) > 0) {
            vo.setReadRate(ratio(nvl(notice.getReadCount()), nvl(notice.getDeliveredCount())));
        }
        vo.setReadBeforeRevoke(notice.getReadBeforeRevoke());
        vo.setCreatorName(creatorName);
        vo.setUpdatedAt(notice.getUpdatedAt());
        return vo;
    }

    private String scopeText(String scopeType, List<NoticeTarget> targets) {
        if ("ALL".equals(scopeType)) {
            return "全部用户";
        }
        if ("FARM".equals(scopeType)) {
            return "指定养殖场";
        }
        List<String> roles = targets.stream().filter(t -> "ROLE".equals(t.getTargetType()))
                .map(NoticeTarget::getTargetValue).toList();
        if (roles.isEmpty()) {
            return "全部角色";
        }
        return String.join("、", roles.stream().map(ContentTexts::noticeRole).toList());
    }

    private String conditionText(String scopeType, List<NoticeTarget> targets, Map<Long, String> categoryNames,
                                 Map<Long, String> farmNames) {
        List<String> parts = new ArrayList<>();
        if ("CONDITION".equals(scopeType)) {
            List<String> provinces = targets.stream().filter(t -> "PROVINCE".equals(t.getTargetType()))
                    .map(t -> ContentTexts.provinceName(t.getTargetValue())).toList();
            if (!provinces.isEmpty()) {
                parts.add(String.join("、", provinces));
            }
            List<String> categories = targets.stream().filter(t -> "CATEGORY".equals(t.getTargetType()))
                    .map(t -> categoryNames.getOrDefault(parseLong(t.getTargetValue()), "品类"))
                    .toList();
            if (!categories.isEmpty()) {
                parts.add(String.join("、", categories));
            }
        } else if ("FARM".equals(scopeType)) {
            List<String> names = targets.stream().filter(t -> "FARM".equals(t.getTargetType()))
                    .map(t -> farmNames.getOrDefault(parseLong(t.getTargetValue()), "养殖场"))
                    .toList();
            if (!names.isEmpty()) {
                parts.add(String.join("、", names));
            }
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private List<NoticeVos.RoleStat> roleStats(Long noticeId) {
        List<RoleStatRow> rows = recipientMapper.selectRoleStats(noticeId);
        Map<String, RoleStatRow> byRole = new LinkedHashMap<>();
        for (RoleStatRow row : rows) {
            byRole.put(row.getRoleSnapshot() == null ? "NONE" : row.getRoleSnapshot(), row);
        }
        List<NoticeVos.RoleStat> result = new ArrayList<>();
        for (String role : ROLE_ORDER) {
            RoleStatRow row = byRole.get(role);
            int delivered = row == null ? 0 : nvl(row.getDelivered());
            if ("NONE".equals(role) && delivered == 0) {
                continue;
            }
            NoticeVos.RoleStat stat = new NoticeVos.RoleStat();
            stat.setRole(role);
            stat.setRoleText(ContentTexts.noticeRole(role));
            stat.setDelivered(delivered);
            stat.setRead(row == null ? 0 : nvl(row.getReadCount()));
            stat.setRate(delivered == 0 ? null : ratio(nvl(stat.getRead()), delivered));
            result.add(stat);
        }
        return result;
    }

    private List<NoticeVos.TimelineItem> timeline(Notice notice) {
        List<NoticeVos.TimelineItem> items = new ArrayList<>();
        for (TimelineRow row : lookupMapper.selectTimeline("NOTICE", notice.getId())) {
            NoticeVos.TimelineItem item = new NoticeVos.TimelineItem();
            item.setAction(row.getOpAction());
            item.setActionText(AdminLogTexts.opActionText(row.getOpAction()));
            item.setTime(row.getCreatedAt());
            item.setAdminName(row.getOperatorName());
            StringBuilder desc = new StringBuilder();
            if (StringUtils.hasText(row.getOperatorName())) {
                desc.append(row.getOperatorName());
            }
            if (StringUtils.hasText(row.getNotifyText())) {
                desc.append(desc.isEmpty() ? "" : " · ").append(row.getNotifyText());
            } else if (StringUtils.hasText(row.getReason())) {
                desc.append(desc.isEmpty() ? "" : " · ").append(row.getReason());
            } else if (StringUtils.hasText(row.getExtraText())) {
                desc.append(desc.isEmpty() ? "" : " · ").append(row.getExtraText());
            }
            item.setDesc(desc.isEmpty() ? null : desc.toString());
            items.add(item);
        }
        return items;
    }

    private NoticeVos.NoticeVO toNoticeVO(Notice notice) {
        NoticeVos.NoticeVO vo = new NoticeVos.NoticeVO();
        vo.setId(notice.getId());
        vo.setStatus(notice.getStatus());
        vo.setStatusText(ContentTexts.noticeStatus(notice.getStatus()));
        vo.setScheduledAt(notice.getScheduledAt());
        vo.setSentAt(notice.getSentAt());
        vo.setEstAccountCount(notice.getEstAccountCount());
        vo.setDeliveredCount(notice.getDeliveredCount());
        vo.setRevokedAt(notice.getRevokedAt());
        vo.setReadBeforeRevoke(notice.getReadBeforeRevoke());
        vo.setVersion(notice.getVersion());
        return vo;
    }

    private void log(Notice notice, String action, String reason, String extra, String notify, Object after) {
        if (notice == null || notice.getId() == null) {
            return;
        }
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("NOTICE")
                .opAction(action)
                .bizType("NOTICE")
                .bizId(notice.getId())
                .targetName(notice.getTitle())
                .reason(reason)
                .extraText(extra)
                .notifyText(notify)
                .after(after)
                .result("SUCCESS")
                .build());
    }

    private static Map<String, Object> afterOf(Notice notice) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("title", notice.getTitle());
        after.put("level", notice.getLevel());
        after.put("scope_type", notice.getScopeType());
        after.put("status", notice.getStatus());
        after.put("scheduled_at", notice.getScheduledAt());
        return after;
    }

    private LambdaQueryWrapper<Notice> baseWrapper(String keyword, ContentQueries.NoticeQuery query) {
        return Wrappers.<Notice>lambdaQuery()
                .eq(StringUtils.hasText(query.getLevel()), Notice::getLevel, upper(query.getLevel()))
                .eq(StringUtils.hasText(query.getScopeType()), Notice::getScopeType, upper(query.getScopeType()))
                .like(keyword != null, Notice::getTitle, keyword);
    }

    private Notice requireExisting(Long id) {
        Notice notice = noticeMapper.selectAnyById(id);
        if (notice == null) {
            throw ContentErrors.notFound();
        }
        if (Boolean.TRUE.equals(notice.getDeleted())) {
            throw ContentErrors.noticeDeleted();
        }
        return notice;
    }

    private List<NoticeTarget> targets(Long noticeId) {
        return targetMapper.selectList(Wrappers.<NoticeTarget>lambdaQuery()
                .eq(NoticeTarget::getNoticeId, noticeId)
                .orderByAsc(NoticeTarget::getId));
    }

    private Map<Long, List<NoticeTarget>> loadTargets(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Map.of();
        }
        List<NoticeTarget> targets = targetMapper.selectList(Wrappers.<NoticeTarget>lambdaQuery()
                .in(NoticeTarget::getNoticeId, noticeIds)
                .orderByAsc(NoticeTarget::getId));
        Map<Long, List<NoticeTarget>> result = new LinkedHashMap<>();
        for (NoticeTarget target : targets) {
            result.computeIfAbsent(target.getNoticeId(), key -> new ArrayList<>()).add(target);
        }
        return result;
    }

    private Map<Long, String> adminNames(List<Long> ids) {
        List<Long> safe = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (safe.isEmpty()) {
            return Map.of();
        }
        return names(lookupMapper.selectAdminNames(safe));
    }

    private static Map<Long, String> names(List<NameRow> rows) {
        Map<Long, String> result = new HashMap<>();
        if (rows != null) {
            rows.forEach(row -> result.put(row.getId(), row.getName()));
        }
        return result;
    }

    private void validateImage(Long fileId) {
        FileEntity file = fileService.find(fileId);
        boolean valid = file != null
                && "NOTICE_IMAGE".equals(file.getScene())
                && "READY".equals(file.getUploadStatus())
                && ("image/jpeg".equalsIgnoreCase(file.getMimeType()) || "image/png".equalsIgnoreCase(file.getMimeType()))
                && file.getFileSize() != null && file.getFileSize() <= 5L * 1024 * 1024;
        if (!valid) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 JPG 或 PNG 图片，单张不超过 5MB");
        }
    }

    private static String requireTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            throw ContentErrors.field("title", "请填写通知标题");
        }
        if (value.codePointCount(0, value.length()) > NoticeRichText.TITLE_MAX) {
            throw ContentErrors.field("title", "标题不能超过 50 字");
        }
        return value;
    }

    private static String level(String level) {
        String value = upper(level);
        if (value == null) {
            return "NORMAL";
        }
        if (!Set.of("IMPORTANT", "NORMAL").contains(value)) {
            throw ContentErrors.field("level", "通知级别不正确");
        }
        return value;
    }

    private static String scope(String scope) {
        String value = upper(scope);
        if (value == null) {
            return "ALL";
        }
        if (!Set.of("ALL", "CONDITION", "FARM").contains(value)) {
            throw ContentErrors.field("scope_type", "请选择接收范围");
        }
        return value;
    }

    private static String action(String action) {
        String value = upper(action);
        if (value == null || !ACTIONS.contains(value)) {
            throw ContentErrors.field("submit_action", "提交动作不正确");
        }
        return value;
    }

    private static String reason(String raw, String emptyMessage) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw ContentErrors.reasonEmpty(emptyMessage);
        }
        if (value.codePointCount(0, value.length()) > 100) {
            throw ContentErrors.field("reason", "原因不能超过 100 个字");
        }
        return value;
    }

    private Long validateCopy(Long copiedFromId) {
        if (copiedFromId == null) {
            return null;
        }
        Notice source = noticeMapper.selectAnyById(copiedFromId);
        if (source == null || Boolean.TRUE.equals(source.getDeleted())) {
            throw ContentErrors.field("copied_from_id", "复制来源不存在");
        }
        return copiedFromId;
    }

    private BizException conflictOrState(Long id) {
        return conflictOrState(id, null);
    }

    private BizException conflictOrState(Long id, String stateMessage) {
        Notice current = noticeMapper.selectAnyById(id);
        if (current == null) {
            return ContentErrors.notFound();
        }
        if (Boolean.TRUE.equals(current.getDeleted())) {
            return ContentErrors.noticeDeleted();
        }
        if (stateMessage != null && !Set.of("DRAFT", "SCHEDULED").contains(current.getStatus())) {
            return ContentErrors.stateChanged(stateMessage);
        }
        return ContentErrors.versionConflict();
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? 0L : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static BigDecimal ratio(long read, long delivered) {
        if (delivered <= 0) {
            return null;
        }
        return BigDecimal.valueOf(read).divide(BigDecimal.valueOf(delivered), 4, RoundingMode.HALF_UP);
    }

    private static String percent(long read, long delivered) {
        return delivered <= 0 ? ""
                : BigDecimal.valueOf(read).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(delivered), 1, RoundingMode.HALF_UP) + "%";
    }

    private static String rolePercent(RoleStatRow row) {
        return row == null ? "" : percent(nvl(row.getReadCount()), nvl(row.getDelivered()));
    }
}
