package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.FarmChangeAdminQuery;
import com.zhimu.server.admin.audit.query.FarmChangeStatsRow;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.AuditRejectRequest;
import com.zhimu.server.admin.audit.service.FarmChangeReviewService;
import com.zhimu.server.admin.audit.support.AuditReasonTemplates;
import com.zhimu.server.admin.audit.support.AuditSupport;
import com.zhimu.server.admin.audit.support.AuditTexts;
import com.zhimu.server.admin.audit.support.AuditWaitCalculator;
import com.zhimu.server.admin.audit.support.FarmSnapshotJson;
import com.zhimu.server.admin.audit.support.GeoDistanceUtil;
import com.zhimu.server.admin.audit.vo.AdminImageVO;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.CompareRowVO;
import com.zhimu.server.admin.audit.vo.FarmChangeDetailVO;
import com.zhimu.server.admin.audit.vo.FarmChangeItemVO;
import com.zhimu.server.admin.audit.vo.FarmChangePageVO;
import com.zhimu.server.admin.audit.vo.ReviewVO;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.api.FarmChangeAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmChange;
import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.common.vo.MediaItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-R06–R09：资料变更列表/对比详情；通过/驳回复用模块02 的 FarmChangeAdminService。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmChangeReviewServiceImpl implements FarmChangeReviewService {

    private static final String MSG_NOT_FOUND = "资料变更申请不存在，列表已刷新";
    private static final String MSG_DELETED = "该养殖场已被删除";

    private final AuditAdminQueryMapper queryMapper;
    private final FarmChangeAdminService farmChangeAdminService;
    private final BaseDataQueryService baseData;
    private final BizMediaService bizMediaService;

    @Override
    public FarmChangePageVO list(FarmChangeAdminQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        long total = queryMapper.countFarmChanges(status, keyword, query.getChangeItem());
        List<FarmChangeItemVO> rows = total == 0 ? List.of()
                : queryMapper.selectFarmChanges(status, keyword, query.getChangeItem(), offset, size);
        LocalDateTime now = BeijingTime.now();
        for (FarmChangeItemVO row : rows) {
            row.setOwnerPhone(Masks.phone(row.getOwnerPhone()));
            row.setStatusText(AuditTexts.statusText(row.getStatus()));
            List<String> codes = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            addChange(codes, texts, "NAME", "名称", row.getNameChanged());
            addChange(codes, texts, "ADDRESS", "地址", row.getAddressChanged());
            addChange(codes, texts, "QUALIFICATION", "资质图", row.getQualChanged());
            row.setChangeItems(codes);
            row.setChangeItemsText(texts);
            AuditWaitCalculator.Wait wait = AuditWaitCalculator.of(row.getSubmittedAt(), now);
            row.setWaitMinutes(wait.minutes());
            row.setWaitText(wait.text());
            row.setOverdue(wait.overdue());
        }
        FarmChangePageVO result = new FarmChangePageVO(rows, total, page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        for (String code : List.of("PENDING", "APPROVED", "REJECTED")) {
            tabs.put(code, (int) queryMapper.countFarmChanges(code, keyword, query.getChangeItem()));
        }
        result.setTabCounts(tabs);
        FarmChangeStatsRow stats = queryMapper.selectFarmChangeStats(keyword, query.getChangeItem(),
                BeijingTime.today().minusDays(29).atStartOfDay(), now);
        FarmChangePageVO.Stats statsVO = new FarmChangePageVO.Stats();
        if (stats != null) {
            statsVO.setPendingCount(intValue(stats.getPendingCount()));
            statsVO.setEarliestAt(stats.getEarliestAt());
            statsVO.setApproved30d(intValue(stats.getApproved30d()));
            Map<String, Integer> byItem = new LinkedHashMap<>();
            byItem.put("NAME", intValue(stats.getApprovedByName()));
            byItem.put("ADDRESS", intValue(stats.getApprovedByAddress()));
            byItem.put("QUALIFICATION", intValue(stats.getApprovedByQualification()));
            statsVO.setApproved30dByItem(byItem);
            statsVO.setRejected30d(intValue(stats.getRejected30d()));
        }
        result.setStats(statsVO);
        return result;
    }

    @Override
    public FarmChangeDetailVO detail(Long changeId) {
        FarmChange change = changeId == null ? null : queryMapper.selectFarmChange(changeId);
        if (change == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        Farm farm = queryMapper.selectFarmIncludingDeleted(change.getFarmId());
        if (farm == null || Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        User owner = queryMapper.selectUser(farm.getOwnerUserId());
        CategoryInfo category = baseData.getCategory(farm.getMainCategoryId());
        String categoryName = category == null ? null : category.name();
        String countUnitText = category == null ? null : category.countUnitText();
        FarmSnapshot before = changeSnapshot(change, farm);
        List<MediaItemVO> currentMedia = bizMediaService.list(BizType.FARM, farm.getId());
        List<MediaItemVO> appliedMedia = change.getQualChanged() == null || change.getQualChanged()
                ? bizMediaService.list(BizType.FARM_CHANGE, change.getId())
                : currentMedia;
        Set<Long> currentIds = ids(currentMedia);
        Set<Long> appliedIds = ids(appliedMedia);
        List<AdminImageVO> imagesCurrent = images(currentMedia, currentIds, appliedIds, false);
        List<AdminImageVO> imagesAfter = images(appliedMedia, currentIds, appliedIds, true);
        List<CompareRowVO> compare = List.of(
                CompareRowVO.of("养殖场名称", before.name(), change.getNewName()),
                CompareRowVO.of("地址", before.address(), change.getNewAddress()),
                CompareRowVO.of("资质图", currentIds.size() + " 张", appliedIds.size() + " 张")
        );
        FarmChangeDetailVO.Locations locations = locations(farm, change, before);
        long stock = queryMapper.countFarmStock(farm.getId());
        long members = queryMapper.countFarmActiveMembers(farm.getId());
        return FarmChangeDetailVO.builder()
                .changeId(change.getId())
                .farmId(farm.getId())
                .farmName(farm.getName())
                .changeNo(change.getChangeNo())
                .status(change.getStatus())
                .statusText(AuditTexts.statusText(change.getStatus()))
                .version(change.getVersion())
                .submittedAt(change.getCreatedAt())
                .ownerName(owner == null ? farm.getContactName() : owner.getName())
                .ownerPhone(Masks.phone(owner == null ? farm.getContactPhone() : owner.getPhone()))
                .remark(change.getRemark())
                .compare(compare)
                .locations(locations)
                .imagesCurrent(imagesCurrent)
                .imagesAfter(imagesAfter)
                .farmOverview(FarmChangeDetailVO.FarmOverview.builder()
                        .approvedAt(farm.getApprovedAt())
                        .categoryName(categoryName)
                        .currentCount((int) stock)
                        .countUnitText(countUnitText)
                        .memberCount((int) members)
                        .farmStatusText(AuditTexts.farmStatusText(farm.getAuditStatus(), farm.getDisabled(),
                                farm.getFrozen()))
                        .build())
                .approveTemplate(AuditReasonTemplates.FARM_CHANGE_APPROVE)
                .rejectTemplates(AuditReasonTemplates.farmChangeRejects())
                .review(review(change))
                .build();
    }

    @Override
    public AuditResultVO approve(Long changeId, AuditApproveRequest request) {
        String opinion = AuditSupport.requireOpinion(request.getAuditOpinion(), "请填写审核意见");
        Target target = target(changeId);
        FarmChange change = target.change();
        if (Boolean.TRUE.equals(change.getNameChanged())
                && queryMapper.countOwnerFarmName(target.farm().getOwnerUserId(), target.farm().getId(),
                change.getNewName()) > 0) {
            String ownerName = target.ownerName() == null ? "所有者" : target.ownerName();
            throw new BizException(ErrorCode.NAME_DUPLICATE,
                    ownerName + "名下已有同名养殖场，不能通过，请驳回并说明原因");
        }
        farmChangeAdminService.approve(changeId, AdminContext.requireAdminId(), opinion,
                request.getVersion());
        return AuditResultVO.builder()
                .targetName(change.getNewName())
                .notifiedNames(target.ownerName() == null ? List.of() : List.of(target.ownerName()))
                .build();
    }

    @Override
    public AuditResultVO reject(Long changeId, AuditRejectRequest request) {
        String reason = AuditSupport.requireReason(request.getRejectReason(), "请填写驳回原因");
        Target target = target(changeId);
        farmChangeAdminService.reject(changeId, AdminContext.requireAdminId(), reason,
                request.getVersion());
        return AuditResultVO.builder()
                .targetName(target.farm().getName())
                .notifiedNames(target.ownerName() == null ? List.of() : List.of(target.ownerName()))
                .build();
    }

    private Target target(Long changeId) {
        FarmChange change = changeId == null ? null : queryMapper.selectFarmChange(changeId);
        if (change == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        Farm farm = queryMapper.selectFarmIncludingDeleted(change.getFarmId());
        if (farm == null || Boolean.TRUE.equals(farm.getDeleted())) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_DELETED);
        }
        User owner = queryMapper.selectUser(farm.getOwnerUserId());
        return new Target(change, farm, owner == null ? null : owner.getName());
    }

    private FarmSnapshot changeSnapshot(FarmChange change, Farm farm) {
        if (change.getBeforeJson() != null && !change.getBeforeJson().isBlank()) {
            FarmSnapshot snapshot = FarmSnapshotJson.read(change.getBeforeJson());
            if (snapshot != null) {
                return snapshot;
            }
        }
        return new FarmSnapshot(farm.getName(), farm.getMainCategoryId(), farm.getScale(),
                farm.getContactName(), farm.getContactPhone(), farm.getAddress(), farm.getProvince(),
                farm.getCity(), farm.getDistrict(), farm.getTownship(), farm.getAdcode(),
                farm.getLongitude(), farm.getLatitude(), farm.getLocAccuracy(), List.of());
    }

    private FarmChangeDetailVO.Locations locations(Farm farm, FarmChange change, FarmSnapshot before) {
        FarmChangeDetailVO.Point current = FarmChangeDetailVO.Point.builder()
                .longitude(farm.getLongitude())
                .latitude(farm.getLatitude())
                .address(farm.getAddress())
                .locAccuracy(farm.getLocAccuracy())
                .sourceText("当前位置")
                .build();
        if (!Boolean.TRUE.equals(change.getAddressChanged())) {
            return FarmChangeDetailVO.Locations.builder().current(current).build();
        }
        FarmChangeDetailVO.Point applied = FarmChangeDetailVO.Point.builder()
                .longitude(change.getNewLongitude())
                .latitude(change.getNewLatitude())
                .address(change.getNewAddress())
                .locAccuracy(change.getNewLocAccuracy())
                .sourceText("地图选点")
                .build();
        return FarmChangeDetailVO.Locations.builder()
                .current(current)
                .applied(applied)
                .distanceKm(GeoDistanceUtil.distanceKm(farm.getLatitude(), farm.getLongitude(),
                        change.getNewLatitude(), change.getNewLongitude()))
                .build();
    }

    private List<AdminImageVO> images(List<MediaItemVO> media, Set<Long> currentIds,
                                      Set<Long> appliedIds, boolean after) {
        List<AdminImageVO> images = new ArrayList<>();
        int index = 1;
        for (MediaItemVO item : media) {
            boolean inCurrent = currentIds.contains(item.getFileId());
            boolean inApplied = appliedIds.contains(item.getFileId());
            String flag = after ? (inCurrent ? "KEEP" : "NEW") : (inApplied ? "KEEP" : "REMOVED");
            images.add(AdminImageVO.builder()
                    .fileId(item.getFileId())
                    .url(item.getUrl())
                    .thumbUrl(item.getThumbUrl())
                    .sortNo(item.getSortNo() == null ? index : item.getSortNo())
                    .flag(flag)
                    .fileSize(item.getSize())
                    .uploadedAt(item.getShotAt())
                    .uploaderName(item.getShotBy())
                    .build());
            index++;
        }
        return images;
    }

    private ReviewVO review(FarmChange change) {
        if (change == null || "PENDING".equals(change.getStatus()) || change.getAuditedAt() == null) {
            return null;
        }
        return ReviewVO.builder()
                .result(change.getStatus())
                .resultText(AuditTexts.statusText(change.getStatus()))
                .reviewerName(adminName(change.getAuditedBy()))
                .reviewedAt(change.getAuditedAt())
                .opinion(change.getAuditOpinion())
                .rejectReason(change.getRejectReason())
                .build();
    }

    private String adminName(Long adminId) {
        return adminId == null ? null : queryMapper.selectAdminName(adminId);
    }

    private static Set<Long> ids(List<MediaItemVO> media) {
        Set<Long> ids = new HashSet<>();
        for (MediaItemVO item : media) {
            if (item.getFileId() != null) {
                ids.add(item.getFileId());
            }
        }
        return ids;
    }

    private static void addChange(List<String> codes, List<String> texts,
                                  String code, String text, Boolean changed) {
        if (Boolean.TRUE.equals(changed)) {
            codes.add(code);
            texts.add(text);
        }
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Target(FarmChange change, Farm farm, String ownerName) {
    }
}
