package com.zhimu.server.biz.inspect;

import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.query.InspectTemplateHeadRow;
import com.zhimu.server.biz.inspect.query.InspectTemplateItemRow;
import com.zhimu.server.biz.inspect.support.InspectTemplateBundle;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.context.UserContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 模块07 单测公共夹具。 */
public final class InspectFixtures {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T01:12:00Z"), ZONE);

    private InspectFixtures() {
    }

    public static void loginOwner() {
        login(10001L, 5001L, FarmRole.OWNER, Set.of());
    }

    public static void loginManager() {
        login(10002L, 5002L, FarmRole.MANAGER, Set.of());
    }

    public static void loginStaff(Long userId, Set<Long> penIds) {
        login(userId, 5003L, FarmRole.STAFF, penIds);
    }

    public static void login(Long userId, Long memberId, FarmRole role, Set<Long> penIds) {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "test-jti"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", userId, memberId, role, penIds,
                FarmStatus.NORMAL));
    }

    public static void logout() {
        UserContext.clear();
        FarmContext.clear();
    }

    public static PenInfo pen(long penId, String code, String name, int stock) {
        return new PenInfo(penId, 1001L, code, name, "FATTENING", "育肥舍", stock + 20, "ENABLED");
    }

    public static BatchInfo batch(long batchId, String batchNo, long categoryId, long breedId,
                                  LocalDate entryDate, int entryAgeDays, int count, Long... penIds) {
        List<Long> pens = new ArrayList<>(List.of(penIds));
        return new BatchInfo(1001L, batchId, batchNo, breedId, categoryId, entryDate, count, "ACTIVE", pens,
                entryAgeDays, count + 5, 5, 0, pens.isEmpty() ? null : pens.get(0));
    }

    public static FarmMemberRow member(long userId, String name, String role) {
        FarmMemberRow row = new FarmMemberRow();
        row.setUserId(userId);
        row.setName(name);
        row.setRole(role);
        return row;
    }

    public static Inspection inspection(long id, long penId, Long batchId, LocalDate date, String status,
                                        boolean abnormal, Long registrantId, Long reportId) {
        Inspection inspection = new Inspection();
        inspection.setId(id);
        inspection.setFarmId(1001L);
        inspection.setPenId(penId);
        inspection.setBatchId(batchId);
        inspection.setInspectDate(date);
        inspection.setInspectStatus(status);
        inspection.setTemplateId(11L);
        inspection.setTemplateVersion(2);
        inspection.setStockSnapshot(200);
        inspection.setRegistrantId(registrantId);
        inspection.setExecutorId(registrantId);
        inspection.setRegisteredAt(date.atTime(9, 12));
        inspection.setDeadlineTime(LocalTime.of(18, 0));
        inspection.setIsOverdue(false);
        inspection.setIsBackfill(false);
        inspection.setIsAbnormal(abnormal);
        inspection.setAbnormalSource(abnormal ? "CHECK" : null);
        inspection.setAbnormalSummary(abnormal ? "粪便 水样" : null);
        inspection.setIsManualMarked(false);
        inspection.setLocationStatus("ON_SITE");
        inspection.setDistanceM(120);
        inspection.setReportId(reportId);
        inspection.setVersion(1);
        inspection.setDeleted(false);
        inspection.setCreatedAt(date.atTime(9, 12));
        inspection.setUpdatedAt(date.atTime(9, 12));
        return inspection;
    }

    public static InspectionItemResult itemResult(long inspectionId, long itemId, String itemName,
                                                  long optionId, String optionName, boolean abnormal, int sortNo) {
        InspectionItemResult result = new InspectionItemResult();
        result.setFarmId(1001L);
        result.setInspectionId(inspectionId);
        result.setItemId(itemId);
        result.setItemName(itemName);
        result.setOptionId(optionId);
        result.setOptionName(optionName);
        result.setOptionAbnormal(abnormal);
        result.setSortNo(sortNo);
        return result;
    }

    public static AbnormalReport report(long id, String status, String aiState, long reporterId,
                                        long penId, long batchId, Long inspectionId) {
        AbnormalReport report = new AbnormalReport();
        report.setId(id);
        report.setFarmId(1001L);
        report.setPenId(penId);
        report.setBatchId(batchId);
        report.setInspectionId(inspectionId);
        report.setSource(inspectionId == null ? "DIRECT" : "INSPECTION");
        report.setClientRequestId("req-" + id);
        report.setReporterId(reporterId);
        report.setReportedAt(LocalDateTime.of(2026, 10, 19, 9, 20));
        report.setReportDate(LocalDate.of(2026, 10, 19));
        report.setSickCount(12);
        report.setStockSnapshot(200);
        report.setSickRate(new BigDecimal("0.0600"));
        report.setDescription("12 头排水样稀粪，部分呕吐。");
        report.setSameDaySeq(1);
        report.setStatus(status);
        report.setAiState(aiState);
        report.setAutoRetryUsed(false);
        report.setIsMajorEpidemic(false);
        report.setVersion(1);
        report.setDeleted(false);
        return report;
    }

    public static AiDiagnosis diagnosis(long id, long reportId, String status) {
        AiDiagnosis diagnosis = new AiDiagnosis();
        diagnosis.setId(id);
        diagnosis.setFarmId(1001L);
        diagnosis.setReportId(reportId);
        diagnosis.setAttemptNo(1);
        diagnosis.setTriggerType("SUBMIT");
        diagnosis.setStatus(status);
        diagnosis.setRequestedAt(LocalDateTime.of(2026, 10, 19, 9, 20, 5));
        diagnosis.setFinishedAt(LocalDateTime.of(2026, 10, 19, 9, 21));
        diagnosis.setModelName("qwen3.8-max");
        diagnosis.setPromptVersion("DIAG_V1");
        diagnosis.setUrgency("ATTENTION");
        diagnosis.setUrgencyBasis("SICK_RATE");
        diagnosis.setConclusion("发病 12 头，达到存栏的 5% 以上，紧急程度至少为“关注”。");
        diagnosis.setIsMajorEpidemic(false);
        diagnosis.setSuggestVet(true);
        return diagnosis;
    }

    /** 与 V2_08 一致的 3 项目模板。 */
    public static InspectTemplateBundle template() {
        InspectTemplateHeadRow head = new InspectTemplateHeadRow();
        head.setTemplateId(11L);
        head.setVersionNo(2);
        head.setVersionId(1101L);
        List<InspectTemplateItemRow> rows = new ArrayList<>();
        rows.add(templateRow(101L, "精神状态", 1, 1011L, "正常", false, 1));
        rows.add(templateRow(101L, "精神状态", 1, 1012L, "沉郁", true, 2));
        rows.add(templateRow(102L, "采食", 2, 1021L, "正常", false, 1));
        rows.add(templateRow(102L, "采食", 2, 1022L, "减少", true, 2));
        rows.add(templateRow(103L, "粪便", 3, 1031L, "正常", false, 1));
        rows.add(templateRow(103L, "粪便", 3, 1033L, "水样", true, 3));
        return InspectTemplateBundle.build(head, rows);
    }

    private static InspectTemplateItemRow templateRow(long itemId, String itemName, int itemSort, long optionId,
                                                      String optionName, boolean abnormal, int optionSort) {
        InspectTemplateItemRow row = new InspectTemplateItemRow();
        row.setItemId(itemId);
        row.setItemName(itemName);
        row.setItemSort(itemSort);
        row.setOptionId(optionId);
        row.setOptionName(optionName);
        row.setOptionAbnormal(abnormal);
        row.setOptionSort(optionSort);
        return row;
    }
}
