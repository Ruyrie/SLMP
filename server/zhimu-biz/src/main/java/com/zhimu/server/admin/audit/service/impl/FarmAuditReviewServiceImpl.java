package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.FarmAuditAdminQuery;
import com.zhimu.server.admin.audit.query.FarmBriefRow;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.FarmAuditRejectRequest;
import com.zhimu.server.admin.audit.request.SmsResendRequest;
import com.zhimu.server.admin.audit.service.FarmAuditReviewService;
import com.zhimu.server.admin.audit.support.AuditReasonTemplates;
import com.zhimu.server.admin.audit.support.AuditSupport;
import com.zhimu.server.admin.audit.support.AuditTexts;
import com.zhimu.server.admin.audit.support.AuditWaitCalculator;
import com.zhimu.server.admin.audit.support.FarmSnapshotJson;
import com.zhimu.server.admin.audit.support.GeoDistanceUtil;
import com.zhimu.server.admin.audit.vo.AdminImageVO;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.FarmAuditDetailVO;
import com.zhimu.server.admin.audit.vo.FarmAuditItemVO;
import com.zhimu.server.admin.audit.vo.FarmAuditPageVO;
import com.zhimu.server.admin.audit.vo.ReviewVO;
import com.zhimu.server.admin.audit.vo.NoticeResendVO;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.biz.farm.api.FarmAuditAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.biz.farm.support.FarmNotify;
import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.biz.farm.support.JsonLists;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-R02–R05：注册审核列表与详情；通过/驳回复用模块02 的 FarmAuditAdminService。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmAuditReviewServiceImpl implements FarmAuditReviewService {

    private static final String MSG_NOT_FOUND = "申请不存在，列表已刷新";
    private static final String MSG_DELETED = "该申请已被所有者删除";

    private final AuditAdminQueryMapper queryMapper;
    private final FarmAuditAdminService farmAuditAdminService;
    private final BaseDataQueryService baseData;
    private final FileVoAssembler fileVoAssembler;
    private final FarmNotify farmNotify;
    private final OperationLogService operationLogService;

    @Override
    public FarmAuditPageVO list(FarmAuditAdminQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        String provincePrefix = AuditSupport.provincePrefix(query.getProvinceCode());
        long total = queryMapper.countFarmAudits(status, keyword, query.getCategoryId(), provincePrefix,
                query.getSubmitType());
        List<FarmAuditItemVO> rows = total == 0 ? List.of()
                : queryMapper.selectFarmAudits(status, keyword, query.getCategoryId(), provincePrefix,
                query.getSubmitType(), offset, size);
        LocalDateTime now = BeijingTime.now();
        for (FarmAuditItemVO row : rows) {
            row.setOwnerPhone(Masks.phone(row.getOwnerPhone()));
            row.setStatusText(AuditTexts.statusText(row.getStatus()));
            AuditWaitCalculator.Wait wait = AuditWaitCalculator.of(row.getSubmittedAt(), now);
            row.setWaitMinutes(wait.minutes());
            row.setWaitText(wait.text());
            row.setOverdue(wait.overdue());
        }
        FarmAuditPageVO result = new FarmAuditPageVO(rows, total, page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        for (String code : List.of("PENDING", "APPROVED", "REJECTED")) {
            tabs.put(code, (int) queryMapper.countFarmAudits(code, keyword, query.getCategoryId(),
                    provincePrefix, query.getSubmitType()));
        }
        result.setTabCounts(tabs);
        LocalDateTime overdueBefore = now.minusHours(48);
        result.setOverdueCount((int) queryMapper.countOverdueFarmAudits(keyword, query.getCategoryId(),
                provincePrefix, query.getSubmitType(), overdueBefore));
        result.setOverdueFarmNames(queryMapper.selectOverdueFarmNames(keyword, query.getCategoryId(),
                provincePrefix, query.getSubmitType(), overdueBefore, 5));
        return result;
    }

    @Override
    public FarmAuditDetailVO detail(Long auditId) {
        FarmAudit audit = auditId == null ? null : queryMapper.selectFarmAudit(auditId);
        if (audit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        Farm farm = queryMapper.selectFarmIncludingDeleted(audit.getFarmId());
        if (farm == null || Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        User owner = queryMapper.selectUser(farm.getOwnerUserId());
        FarmSnapshot snapshot = snapshot(audit, farm);
        CategoryInfo category = snapshot.mainCategoryId() == null ? null : baseData.getCategory(snapshot.mainCategoryId());
        String categoryName = category == null ? null : category.name();
        String countUnitText = category == null ? null : category.countUnitText();
        List<FarmAudit> all = queryMapper.selectFarmAuditsOfFarm(farm.getId());
        FarmAudit previous = previousAudit(all, audit);
        FarmAudit previousReject = previousReject(all, audit);
        Set<Long> previousFileIds = snapshot(previous, farm).fileIds() == null
                ? Set.of() : new LinkedHashSet<>(snapshot(previous, farm).fileIds());
        Set<Long> needReshootIds = previousReject == null ? Set.of()
                : new LinkedHashSet<>(JsonLists.readLongs(previousReject.getReshootFileIds()));

        FarmAuditDetailVO.Owner ownerVO = FarmAuditDetailVO.Owner.builder()
                .userId(owner == null ? null : owner.getId())
                .name(owner == null ? null : owner.getName())
                .phone(owner == null ? null : Masks.phone(owner.getPhone()))
                .registeredAt(owner == null ? null : owner.getCreatedAt())
                .otherFarms(otherFarms(farm, categoryName))
                .build();
        FarmAuditDetailVO.Info info = FarmAuditDetailVO.Info.builder()
                .name(snapshot.name())
                .categoryName(categoryName)
                .scale(snapshot.scale())
                .countUnitText(countUnitText)
                .contactName(snapshot.contactName())
                .contactPhone(Masks.phone(snapshot.contactPhone()))
                .address(snapshot.address())
                .regionText(regionText(snapshot.province(), snapshot.district()))
                .intro(farm.getIntro())
                .build();
        FarmAuditDetailVO.LastReject lastReject = null;
        if (previousReject != null) {
            lastReject = FarmAuditDetailVO.LastReject.builder()
                    .reason(previousReject.getRejectReason())
                    .auditedAt(previousReject.getAuditedAt())
                    .auditorName(adminName(previousReject.getAuditedBy()))
                    .build();
        }
        return FarmAuditDetailVO.builder()
                .auditId(audit.getId())
                .farmId(farm.getId())
                .submitNo(audit.getSubmitNo())
                .status(audit.getStatus())
                .statusText(AuditTexts.statusText(audit.getStatus()))
                .version(audit.getVersion())
                .submittedAt(audit.getSubmittedAt())
                .owner(ownerVO)
                .info(info)
                .lastReject(lastReject)
                .changeSummary(audit.getChangeSummary())
                .images(images(snapshot, previousFileIds, needReshootIds, owner))
                .location(location(snapshot, owner == null ? null : owner.getId(), farm.getId()))
                .history(history(all, farm, owner))
                .approveTemplate(AuditReasonTemplates.FARM_AUDIT_APPROVE)
                .rejectTemplates(AuditReasonTemplates.farmAuditRejects())
                .review(review(audit))
                .build();
    }

    @Override
    public AuditResultVO approve(Long auditId, AuditApproveRequest request) {
        String opinion = AuditSupport.requireOpinion(request.getAuditOpinion(), "请填写审核意见");
        Target target = target(auditId);
        farmAuditAdminService.approve(auditId, AdminContext.requireAdminId(), opinion,
                request.getVersion());
        return AuditResultVO.builder()
                .targetName(target.farm().getName())
                .notifiedNames(target.ownerName() == null ? List.of() : List.of(target.ownerName()))
                .build();
    }

    @Override
    public AuditResultVO reject(Long auditId, FarmAuditRejectRequest request) {
        String reason = AuditSupport.requireReason(request.getRejectReason(), "请填写驳回原因");
        Target target = target(auditId);
        List<Long> reshootIds = request.getReshootFileIds() == null ? List.of()
                : request.getReshootFileIds().stream().filter(Objects::nonNull).distinct().toList();
        if (!reshootIds.isEmpty()) {
            Set<Long> allowed = new HashSet<>(snapshot(target.audit(), target.farm()).fileIds());
            if (!allowed.containsAll(reshootIds)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "需重拍的资质图不属于本次提交，请刷新后重试");
            }
        }
        farmAuditAdminService.reject(auditId, AdminContext.requireAdminId(), reason, reshootIds,
                request.getVersion());
        return AuditResultVO.builder()
                .targetName(target.farm().getName())
                .notifiedNames(target.ownerName() == null ? List.of() : List.of(target.ownerName()))
                .build();
    }

    @Override
    public NoticeResendVO resendNotice(Long auditId, SmsResendRequest request) {
        FarmAudit audit = auditId == null ? null : queryMapper.selectFarmAudit(auditId);
        if (audit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        boolean approved = "APPROVED".equals(audit.getStatus());
        if (!approved && !"REJECTED".equals(audit.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条申请还未处理，不能重发审核结果通知");
        }
        Farm farm = queryMapper.selectFarmIncludingDeleted(audit.getFarmId());
        if (farm == null || Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        User owner = queryMapper.selectUser(farm.getOwnerUserId());
        if (owner == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "找不到这个养殖场的所有者");
        }
        String reason = request == null ? null : trimToNull(request.getReason());
        if (reason != null && reason.length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原因不能超过 200 字");
        }
        String logReason = reason == null ? "重新发送审核结果通知" : reason;
        String ownerName = owner.getName() == null || owner.getName().isBlank() ? "所有者" : owner.getName();
        boolean sent;
        try {
            // 不在事务内调用：AfterCommit 立即执行，消息服务异常在这里如实反馈
            farmNotify.auditResult(farm, audit.getId(), approved,
                    approved ? audit.getAuditOpinion() : audit.getRejectReason());
            sent = true;
        } catch (RuntimeException e) {
            log.warn("重发注册审核结果通知失败 auditId={}", audit.getId(), e);
            sent = false;
        }
        LocalDateTime now = BeijingTime.now();
        String note = sent
                ? "已把审核结果重新发到" + ownerName + "的消息中心（站内消息，不发短信）"
                : "审核结果通知发送失败，请稍后重试";
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("FARM_AUDIT")
                .opAction("RESEND_NOTICE")
                .bizType("FARM_AUDIT")
                .bizId(audit.getId())
                .farmId(farm.getId())
                .targetName(farm.getName() + " · 所有者 " + Masks.phone(owner.getPhone()))
                .searchText(farm.getName())
                .reason(logReason)
                .extraText(sent ? "审核结果通知已重新发到消息中心" : "审核结果通知发送失败")
                .notifyText(sent ? "已发到" + ownerName + "的消息中心" : null)
                .before(Map.of("audit_status", audit.getStatus()))
                .after(Map.of("resend", "MESSAGE_CENTER", "notify_status", sent ? "SENT" : "FAILED"))
                .result(sent ? "SUCCESS" : "FAIL")
                .build());
        return NoticeResendVO.builder()
                .id(audit.getId())
                .phone(Masks.phone(owner.getPhone()))
                .receiverName(ownerName)
                .notifyStatus(sent ? "SENT" : "FAILED")
                .notifyStatusText(sent ? "已发到消息中心" : "发送失败")
                .sentAt(sent ? now : null)
                .note(note)
                .build();
    }

    private Target target(Long auditId) {
        FarmAudit audit = auditId == null ? null : queryMapper.selectFarmAudit(auditId);
        if (audit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        Farm farm = queryMapper.selectFarmIncludingDeleted(audit.getFarmId());
        if (farm == null || Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        User owner = queryMapper.selectUser(farm.getOwnerUserId());
        return new Target(audit, farm, owner == null ? null : owner.getName());
    }

    private FarmSnapshot snapshot(FarmAudit audit, Farm farm) {
        if (audit != null && audit.getSnapshotJson() != null && !audit.getSnapshotJson().isBlank()) {
            FarmSnapshot snapshot = FarmSnapshotJson.read(audit.getSnapshotJson());
            if (snapshot != null) {
                return snapshot;
            }
        }
        return new FarmSnapshot(farm.getName(), farm.getMainCategoryId(), farm.getScale(),
                farm.getContactName(), farm.getContactPhone(), farm.getAddress(), farm.getProvince(),
                farm.getCity(), farm.getDistrict(), farm.getTownship(), farm.getAdcode(),
                farm.getLongitude(), farm.getLatitude(), farm.getLocAccuracy(), List.of());
    }

    private List<AdminImageVO> images(FarmSnapshot snapshot, Set<Long> previousFileIds,
                                      Set<Long> needReshootIds, User owner) {
        List<Long> fileIds = snapshot.fileIds();
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<FileEntity> files = queryMapper.selectFiles(fileIds);
        Map<Long, FileEntity> byId = new LinkedHashMap<>();
        for (FileEntity file : files) {
            byId.put(file.getId(), file);
        }
        List<AdminImageVO> images = new ArrayList<>();
        int sort = 1;
        for (Long fileId : fileIds) {
            FileEntity file = byId.get(fileId);
            if (file == null) {
                continue;
            }
            var vo = fileVoAssembler.toFileVo(file);
            images.add(AdminImageVO.builder()
                    .fileId(fileId)
                    .url(vo == null ? null : vo.getUrl())
                    .thumbUrl(vo == null ? null : vo.getThumbUrl())
                    .sortNo(sort++)
                    .reshoot(!previousFileIds.isEmpty() && !previousFileIds.contains(fileId))
                    .sameAsLast(previousFileIds.contains(fileId))
                    .needReshoot(needReshootIds.contains(fileId))
                    .fileSize(file.getFileSize())
                    .uploadedAt(file.getCreatedAt())
                    .uploaderName(owner == null ? null : owner.getName())
                    .build());
        }
        return images;
    }

    private FarmAuditDetailVO.Location location(FarmSnapshot snapshot, Long ownerUserId, Long currentFarmId) {
        String nearestName = null;
        BigDecimal nearestDistance = null;
        for (FarmBriefRow row : queryMapper.selectOwnerFarms(ownerUserId, currentFarmId)) {
            BigDecimal distance = GeoDistanceUtil.distanceKm(snapshot.latitude(), snapshot.longitude(),
                    row.getLatitude(), row.getLongitude());
            if (distance != null && (nearestDistance == null || distance.compareTo(nearestDistance) < 0)) {
                nearestDistance = distance;
                nearestName = row.getName();
            }
        }
        return FarmAuditDetailVO.Location.builder()
                .longitude(snapshot.longitude())
                .latitude(snapshot.latitude())
                .locAccuracy(snapshot.locAccuracy())
                .nearestFarmName(nearestName)
                .distanceKm(nearestDistance)
                .build();
    }

    private List<FarmAuditDetailVO.OtherFarm> otherFarms(Farm farm, String categoryName) {
        List<FarmAuditDetailVO.OtherFarm> farms = new ArrayList<>();
        farms.add(FarmAuditDetailVO.OtherFarm.builder()
                .farmId(farm.getId())
                .name(farm.getName())
                .auditStatus(farm.getAuditStatus())
                .auditStatusText(AuditTexts.farmStatusText(farm.getAuditStatus(), farm.getDisabled(), farm.getFrozen()))
                .categoryName(categoryName)
                .current(true)
                .build());
        for (FarmBriefRow row : queryMapper.selectOwnerFarms(farm.getOwnerUserId(), farm.getId())) {
            farms.add(FarmAuditDetailVO.OtherFarm.builder()
                    .farmId(row.getFarmId())
                    .name(row.getName())
                    .auditStatus(row.getAuditStatus())
                    .auditStatusText(AuditTexts.statusText(row.getAuditStatus()))
                    .categoryName(row.getCategoryName())
                    .current(false)
                    .build());
        }
        return farms;
    }

    private List<FarmAuditDetailVO.History> history(List<FarmAudit> audits, Farm farm, User owner) {
        List<FarmAuditDetailVO.History> history = new ArrayList<>();
        String ownerName = owner == null ? farm.getContactName() : owner.getName();
        for (FarmAudit audit : audits) {
            if (!"PENDING".equals(audit.getStatus()) && audit.getAuditedAt() != null) {
                String actor = adminName(audit.getAuditedBy());
                boolean approved = "APPROVED".equals(audit.getStatus());
                history.add(FarmAuditDetailVO.History.builder()
                        // 操作人放在 actor，页面拼成“驳回 · 周敏”，这里不再重复拼
                        .event(approved ? "通过" : "驳回")
                        .time(audit.getAuditedAt())
                        .actor(actor)
                        .desc(approved ? audit.getAuditOpinion() : audit.getRejectReason())
                        .tag(AuditTexts.statusText(audit.getStatus()))
                        .build());
            }
            FarmSnapshot snapshot = snapshot(audit, farm);
            history.add(FarmAuditDetailVO.History.builder()
                    .event("第 " + (audit.getSubmitNo() == null ? 1 : audit.getSubmitNo()) + " 次提交")
                    .time(audit.getSubmittedAt())
                    .actor(ownerName)
                    .desc(audit.getChangeSummary() != null ? audit.getChangeSummary()
                            : "提交注册信息，资质图 " + snapshot.fileIds().size() + " 张")
                    .tag(AuditTexts.statusText(audit.getStatus()))
                    .build());
        }
        history.sort(Comparator.comparing(FarmAuditDetailVO.History::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return history;
    }

    private ReviewVO review(FarmAudit audit) {
        if (audit == null || "PENDING".equals(audit.getStatus()) || audit.getAuditedAt() == null) {
            return null;
        }
        return ReviewVO.builder()
                .result(audit.getStatus())
                .resultText(AuditTexts.statusText(audit.getStatus()))
                .reviewerName(adminName(audit.getAuditedBy()))
                .reviewedAt(audit.getAuditedAt())
                .opinion(audit.getAuditOpinion())
                .rejectReason(audit.getRejectReason())
                .build();
    }

    private FarmAudit previousAudit(List<FarmAudit> all, FarmAudit current) {
        FarmAudit previous = null;
        for (FarmAudit candidate : all) {
            if (Objects.equals(candidate.getId(), current.getId())) {
                continue;
            }
            int currentNo = current.getSubmitNo() == null ? 0 : current.getSubmitNo();
            int candidateNo = candidate.getSubmitNo() == null ? 0 : candidate.getSubmitNo();
            if (candidateNo >= currentNo) {
                continue;
            }
            if (previous == null || candidateNo > (previous.getSubmitNo() == null ? 0 : previous.getSubmitNo())) {
                previous = candidate;
            }
        }
        return previous;
    }

    private FarmAudit previousReject(List<FarmAudit> all, FarmAudit current) {
        FarmAudit previous = null;
        for (FarmAudit candidate : all) {
            if (Objects.equals(candidate.getId(), current.getId())
                    || !"REJECTED".equals(candidate.getStatus())) {
                continue;
            }
            int currentNo = current.getSubmitNo() == null ? 0 : current.getSubmitNo();
            int candidateNo = candidate.getSubmitNo() == null ? 0 : candidate.getSubmitNo();
            if (candidateNo >= currentNo) {
                continue;
            }
            if (previous == null || candidateNo > (previous.getSubmitNo() == null ? 0 : previous.getSubmitNo())) {
                previous = candidate;
            }
        }
        return previous;
    }

    private String adminName(Long adminId) {
        return adminId == null ? null : queryMapper.selectAdminName(adminId);
    }

    private static String regionText(String province, String district) {
        if (province == null && district == null) {
            return null;
        }
        return (province == null ? "" : province) + " · " + (district == null ? "" : district);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Target(FarmAudit audit, Farm farm, String ownerName) {
    }
}
