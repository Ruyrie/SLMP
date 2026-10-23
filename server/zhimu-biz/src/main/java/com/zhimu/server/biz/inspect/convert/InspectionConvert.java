package com.zhimu.server.biz.inspect.convert;

import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.query.PenResponsibleRow;
import com.zhimu.server.biz.inspect.support.InspectRules;
import com.zhimu.server.biz.inspect.vo.InspectionDetailVO;
import com.zhimu.server.biz.inspect.vo.InspectionListItemVO;
import com.zhimu.server.biz.inspect.vo.InspectionSubmitResultVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionRowVO;
import com.zhimu.server.biz.inspect.vo.RecentIssueVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.UserBriefVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 巡检记录到 App VO 的转换。 */
public final class InspectionConvert {

    private InspectionConvert() {
    }

    public static String roleText(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "OWNER" -> "所有者";
            case "MANAGER" -> "场长";
            case "STAFF" -> "员工";
            default -> role;
        };
    }

    public static UserBriefVO userBrief(Long userId, Map<Long, FarmMemberRow> members) {
        if (userId == null) {
            return null;
        }
        FarmMemberRow member = members == null ? null : members.get(userId);
        UserBriefVO vo = new UserBriefVO();
        vo.setUserId(userId);
        vo.setName(member == null ? null : member.getName());
        vo.setRole(member == null ? "STAFF" : member.getRole());
        vo.setRoleText(roleText(member == null ? "STAFF" : member.getRole()));
        return vo;
    }

    public static InspectionDetailVO detail(Inspection inspection, String penName, List<String> batchNos,
                                            List<InspectionItemResult> results, List<MediaItemVO> media,
                                            Map<Long, FarmMemberRow> members, String reportStatus) {
        InspectionDetailVO vo = new InspectionDetailVO();
        vo.setInspectionId(inspection.getId());
        vo.setPenId(inspection.getPenId());
        vo.setPenName(penName);
        vo.setBatchNos(batchNos);
        vo.setInspectDate(BeijingTime.format(inspection.getInspectDate()));
        vo.setInspectStatus(inspection.getInspectStatus());
        vo.setInspectStatusText(InspectRules.statusText(inspection.getInspectStatus()));
        List<InspectionDetailVO.ItemResult> items = new ArrayList<>();
        if (results != null) {
            for (InspectionItemResult result : results) {
                InspectionDetailVO.ItemResult item = new InspectionDetailVO.ItemResult();
                item.setItemId(result.getItemId());
                item.setItemName(result.getItemName());
                item.setOptionId(result.getOptionId());
                item.setOptionName(result.getOptionName());
                item.setOptionAbnormal(result.getOptionAbnormal());
                item.setSortNo(result.getSortNo() == null ? 0 : result.getSortNo());
                items.add(item);
            }
        }
        vo.setItemResults(items);
        vo.setIsAbnormal(inspection.getIsAbnormal());
        vo.setAbnormalSource(inspection.getAbnormalSource());
        vo.setAbnormalSummary(inspection.getAbnormalSummary());
        vo.setIsManualMarked(inspection.getIsManualMarked());
        vo.setManualReason(inspection.getManualReason());
        FarmMemberRow marker = inspection.getManualMarkedBy() == null || members == null ? null
                : members.get(inspection.getManualMarkedBy());
        vo.setManualMarkedByName(marker == null ? null : marker.getName());
        vo.setManualMarkedAt(BeijingTime.format(inspection.getManualMarkedAt()));
        vo.setRemark(inspection.getRemark());
        vo.setMedia(media == null ? List.of() : media);
        vo.setRegistrant(userBrief(inspection.getRegistrantId(), members));
        vo.setExecutor(userBrief(inspection.getExecutorId(), members));
        vo.setIsProxy(inspection.getRegistrantId() != null && inspection.getExecutorId() != null
                && !inspection.getRegistrantId().equals(inspection.getExecutorId()));
        vo.setRegisteredAt(BeijingTime.format(inspection.getRegisteredAt()));
        vo.setLocationStatus(inspection.getLocationStatus());
        vo.setLocationStatusText(InspectRules.locationText(inspection.getLocationStatus()));
        vo.setDistanceM(inspection.getDistanceM());
        vo.setIsOverdue(inspection.getIsOverdue());
        vo.setIsBackfill(inspection.getIsBackfill());
        vo.setTemplateVersion(inspection.getTemplateVersion());
        vo.setReportId(inspection.getReportId());
        vo.setReportStatus(reportStatus);
        vo.setVersion(inspection.getVersion() == null ? 0 : inspection.getVersion());
        return vo;
    }

    public static InspectionDetailVO withEditMode(InspectionDetailVO vo, String editMode, boolean canEdit,
                                                  boolean hasRevisions, Long pendingRequestId) {
        vo.setEditMode(editMode);
        vo.setHasRevisions(hasRevisions);
        vo.setChangeState(ChangeRequestStateVO.builder()
                .canEdit(canEdit)
                .editDeadline(vo.getInspectDate() == null ? null
                        : BeijingTime.editDeadline(java.time.LocalDate.parse(vo.getInspectDate()), 3))
                .pendingRequestId(pendingRequestId)
                .build());
        return vo;
    }

    public static InspectionListItemVO listItem(Inspection inspection, long penId, String penCode, String penName,
                                                Long batchId, List<String> batchNos, int stockCount,
                                                List<String> responsibleNames, String displayStatus,
                                                String registrantName, String reportStatus, boolean canModify,
                                                boolean canReport, boolean deadlinePassed) {
        InspectionListItemVO vo = new InspectionListItemVO();
        vo.setInspectionId(inspection == null ? 0L : inspection.getId());
        vo.setPenId(penId);
        vo.setPenCode(penCode);
        vo.setPenName(penName);
        vo.setBatchId(batchId);
        vo.setBatchNos(batchNos == null ? List.of() : batchNos);
        vo.setStockCount(stockCount);
        vo.setResponsibleNames(responsibleNames == null ? List.of() : responsibleNames);
        vo.setDisplayStatus(displayStatus);
        vo.setDisplayStatusText(InspectRules.displayStatusText(displayStatus));
        vo.setIsOverdue(false);
        vo.setIsBackfill(false);
        if (inspection != null) {
            vo.setAbnormalSource(inspection.getAbnormalSource());
            vo.setAbnormalSummary(inspection.getAbnormalSummary());
            vo.setManualReason(inspection.getManualReason());
            vo.setRegistrantName(registrantName);
            vo.setRegisteredAt(BeijingTime.format(inspection.getRegisteredAt()));
            vo.setLocationStatus(inspection.getLocationStatus());
            vo.setDistanceM(inspection.getDistanceM());
            vo.setIsOverdue(inspection.getIsOverdue());
            vo.setIsBackfill(inspection.getIsBackfill());
            vo.setReportId(inspection.getReportId());
            vo.setVersion(inspection.getVersion() == null ? 0 : inspection.getVersion());
        }
        vo.setDeadlinePassed(deadlinePassed);
        vo.setReportStatus(reportStatus);
        boolean done = inspection != null && InspectRules.DONE.equals(inspection.getInspectStatus());
        vo.setCanRegister("PENDING".equals(displayStatus));
        vo.setCanModify(done && canModify);
        vo.setCanReport(done && canReport);
        vo.setCanViewDiagnosis(inspection != null && inspection.getReportId() != null);
        return vo;
    }

    public static InspectionSubmitResultVO submitResult(Inspection inspection, String penName) {
        InspectionSubmitResultVO vo = new InspectionSubmitResultVO();
        vo.setInspectionId(inspection.getId());
        vo.setVersion(inspection.getVersion() == null ? 0 : inspection.getVersion());
        vo.setPenName(penName);
        vo.setInspectDate(BeijingTime.format(inspection.getInspectDate()));
        vo.setIsAbnormal(inspection.getIsAbnormal());
        vo.setIsOverdue(inspection.getIsOverdue());
        vo.setIsBackfill(inspection.getIsBackfill());
        vo.setTodoCompleted(true);
        vo.setLocationStatus(inspection.getLocationStatus());
        vo.setDistanceM(inspection.getDistanceM());
        return vo;
    }

    public static PenInspectionRowVO penRow(Inspection inspection, boolean today, String registrantName,
                                            boolean canModify, boolean canChangeRequest, boolean canBackfill,
                                            String resultSummary) {
        PenInspectionRowVO vo = new PenInspectionRowVO();
        vo.setInspectionId(inspection.getId());
        vo.setInspectDate(BeijingTime.format(inspection.getInspectDate()));
        vo.setIsToday(today);
        vo.setInspectStatus(inspection.getInspectStatus());
        List<String> tags = new ArrayList<>();
        if (Boolean.TRUE.equals(inspection.getIsAbnormal())) {
            tags.add("ABNORMAL");
        }
        if (Boolean.TRUE.equals(inspection.getIsOverdue())) {
            tags.add("OVERDUE");
        }
        if (Boolean.TRUE.equals(inspection.getIsBackfill())) {
            tags.add("BACKFILL");
        }
        if ("ON_SITE".equals(inspection.getLocationStatus())) {
            tags.add("ON_SITE");
        } else if ("OFF_SITE".equals(inspection.getLocationStatus())) {
            tags.add("OFF_SITE");
        } else if (InspectRules.DONE.equals(inspection.getInspectStatus())) {
            tags.add("NO_LOCATION");
        }
        if (InspectRules.MISSED.equals(inspection.getInspectStatus())) {
            tags.add("MISSED");
        }
        vo.setTags(tags);
        vo.setAbnormalSource(inspection.getAbnormalSource());
        vo.setAbnormalSummary(inspection.getAbnormalSummary());
        vo.setManualReason(inspection.getManualReason());
        vo.setRegistrantName(registrantName);
        vo.setRegisteredAt(BeijingTime.format(inspection.getRegisteredAt()));
        vo.setCanModify(canModify);
        vo.setModifyTarget(inspection.getInspectDate() != null
                && inspection.getInspectDate().equals(BeijingTime.today()) ? "REGISTER" : "BACKFILL");
        vo.setCanChangeRequest(canChangeRequest);
        vo.setHasRevisions(false);
        vo.setCanBackfill(canBackfill);
        vo.setBackfillExpired(false);
        if (InspectRules.MISSED.equals(inspection.getInspectStatus()) && inspection.getInspectDate() != null) {
            vo.setBackfillUntil(BeijingTime.format(InspectRules.backfillUntil(inspection.getInspectDate())));
            vo.setBackfillExpired(InspectRules.backfillExpired(inspection.getInspectDate()));
        }
        vo.setResultSummary(resultSummary);
        return vo;
    }

    public static RecentIssueVO recentIssue(Inspection inspection, String penName, String registrantName,
                                            String issueType) {
        RecentIssueVO vo = new RecentIssueVO();
        vo.setInspectionId(inspection.getId());
        vo.setPenId(inspection.getPenId());
        vo.setPenName(penName);
        vo.setInspectDate(BeijingTime.format(inspection.getInspectDate()));
        vo.setIssueType(issueType);
        vo.setIssueTypeText(InspectRules.issueTypeText(issueType));
        vo.setRegistrantName(registrantName);
        vo.setRegisteredAt(BeijingTime.format(inspection.getRegisteredAt()));
        if (InspectRules.MISSED.equals(inspection.getInspectStatus())) {
            vo.setBackfillUntil(BeijingTime.format(InspectRules.backfillUntil(inspection.getInspectDate())));
            vo.setCanBackfill(InspectRules.inBackfillWindow(inspection.getInspectDate()));
        }
        return vo;
    }

    public static List<String> names(List<PenResponsibleRow> rows, long penId) {
        List<String> names = new ArrayList<>();
        for (PenResponsibleRow row : rows) {
            if (row.getPenId() != null && row.getPenId() == penId && row.getName() != null) {
                names.add(row.getName());
            }
        }
        return names;
    }
}
