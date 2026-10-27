package com.zhimu.server.biz.common;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.entity.OperationLog;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import com.zhimu.server.biz.common.mapper.OperationLogMapper;
import com.zhimu.server.biz.common.query.RecordLogRow;
import com.zhimu.server.biz.common.service.impl.OperationLogRecorderImpl;
import com.zhimu.server.biz.common.service.impl.OperationLogServiceImpl;
import com.zhimu.server.biz.common.vo.RecordLogVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperationLogRecorder 写 t_operation_log 与 U14 修改记录查询。
 */
class OperationLogTest {

    private final OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
    private final AccountQueryService accountQueryService = mock(AccountQueryService.class);
    private final IpRegionResolver ipRegionResolver = mock(IpRegionResolver.class);
    private final FarmMemberCache farmMemberCache = mock(FarmMemberCache.class);
    private OperationLogRecorderImpl recorder;
    private OperationLogServiceImpl queryService;

    @BeforeEach
    void setUp() {
        when(accountQueryService.getBrief(any())).thenReturn(
                new AccountBrief(10001L, "王建国", "13800006620", "NORMAL", false));
        when(ipRegionResolver.resolve(anyString())).thenReturn("广东省 深圳市");
        recorder = new OperationLogRecorderImpl(operationLogMapper,
                CommonFlowFixture.provider(accountQueryService), CommonFlowFixture.provider(ipRegionResolver));
        queryService = new OperationLogServiceImpl(operationLogMapper,
                CommonFlowFixture.provider(accountQueryService), farmMemberCache);
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        com.zhimu.server.common.context.RequestContext.set(new com.zhimu.server.common.context.RequestContext.Info(
                ClientType.APP_ANDROID, "1.0.0", "113.88.0.1", "JUnit", "test-key"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        com.zhimu.server.common.context.RequestContext.clear();
    }

    @Test
    void recorderFillsOperatorRegionAndSnakeCaseJson() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("actual_qty", Map.of("name", "实际投喂量", "display", "140 千克"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("actual_qty", Map.of("name", "实际投喂量", "display", "160 千克"));
        OperationLogEntry entry = OperationLogEntry.builder()
                .operatorType("USER")
                .opType("RECORD_EDIT")
                .opAction("UPDATE")
                .bizType("FEED_RECORD")
                .bizId(7001L)
                .farmId(1001L)
                .reason("填错了")
                .before(before)
                .after(after)
                .changeRequestId(9008L)
                .build();

        recorder.record(entry);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog log = captor.getValue();
        assertThat(log.getOperatorType()).isEqualTo("USER");
        assertThat(log.getOperatorId()).isEqualTo(10001L);
        assertThat(log.getOperatorName()).isEqualTo("王建国");
        assertThat(log.getOpType()).isEqualTo("RECORD_EDIT");
        assertThat(log.getOpAction()).isEqualTo("UPDATE");
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getBizType()).isEqualTo("FEED_RECORD");
        assertThat(log.getBizId()).isEqualTo(7001L);
        assertThat(log.getFarmId()).isEqualTo(1001L);
        assertThat(log.getChangeRequestId()).isEqualTo(9008L);
        assertThat(log.getIpRegion()).isEqualTo("广东省 深圳市");
        assertThat(log.getClientType()).isEqualTo("APP_ANDROID");
        assertThat(log.getBeforeJson()).contains("\"name\":\"实际投喂量\"").contains("\"display\":\"140 千克\"");
        assertThat(log.getAfterJson()).contains("\"display\":\"160 千克\"");
    }

    @Test
    void recorderDoesNotDoubleEncodeJsonTextAndTruncatesLongText() {
        String json = "{\"actual_qty\":{\"name\":\"实际投喂量\",\"display\":\"140 千克\"}}";
        OperationLogEntry entry = OperationLogEntry.builder()
                .operatorType("SYSTEM")
                .opType("RECORD_EDIT")
                .opAction("UPDATE")
                .reason("x".repeat(600))
                .before(json)
                .build();
        recorder.record(entry);
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getBeforeJson()).isEqualTo(json);
        assertThat(captor.getValue().getReason()).hasSize(500);
        assertThat(captor.getValue().getOperatorType()).isEqualTo("SYSTEM");
    }

    @Test
    void u14BuildsChangesWithChineseNameAndDisplayText() {
        RecordLogRow row = new RecordLogRow();
        row.setId(1L);
        row.setOperatorId(10001L);
        row.setOperatorName("王建国");
        row.setOperatorRole("OWNER");
        row.setOpAction("UPDATE");
        row.setReason("填错了");
        row.setBeforeJson("{\"actual_qty\":{\"name\":\"实际投喂量\",\"display\":\"140 千克\"}}");
        row.setAfterJson("{\"actual_qty\":{\"name\":\"实际投喂量\",\"display\":\"160 千克\"}}");
        row.setFarmId(1001L);
        row.setChangeRequestId(9008L);
        row.setChangeRequestNo("CR202610190008");
        row.setCreatedAt(LocalDateTime.of(2026, 10, 19, 9, 28));
        when(operationLogMapper.selectUserLogs(eq("FEED_RECORD"), eq(7001L), anyInt())).thenReturn(List.of(row));
        grant(1001L, 10001L);

        List<RecordLogVO> logs = queryService.listByBiz(BizType.FEED_RECORD, 7001L, 500);

        verify(operationLogMapper).selectUserLogs("FEED_RECORD", 7001L, 200);
        assertThat(logs).hasSize(1);
        RecordLogVO vo = logs.get(0);
        assertThat(vo.getOpAction()).isEqualTo("UPDATE");
        assertThat(vo.getOpActionText()).isEqualTo("修改");
        assertThat(vo.getOperator().getName()).isEqualTo("王建国");
        assertThat(vo.getOperator().getRoleText()).isEqualTo("所有者");
        assertThat(vo.getChangeRequestNo()).isEqualTo("CR202610190008");
        assertThat(vo.getChanges()).hasSize(1);
        assertThat(vo.getChanges().get(0).getFieldName()).isEqualTo("实际投喂量");
        assertThat(vo.getChanges().get(0).getBefore()).isEqualTo("140 千克");
        assertThat(vo.getChanges().get(0).getAfter()).isEqualTo("160 千克");
    }

    @Test
    void u14ReturnsEmptyArrayWhenNoLogs() {
        when(operationLogMapper.selectUserLogs(any(), any(), anyInt())).thenReturn(List.of());
        List<RecordLogVO> logs = queryService.listByBiz(BizType.FEED_RECORD, 7001L, 20);
        assertThat(logs).isNotNull().isEmpty();
    }

    @Test
    void u14ChecksFarmAccess() {
        RecordLogRow row = new RecordLogRow();
        row.setId(1L);
        row.setOperatorId(10001L);
        row.setOpAction("VOID");
        row.setFarmId(1002L);
        when(operationLogMapper.selectUserLogs(any(), any(), anyInt())).thenReturn(List.of(row));

        assertThatThrownBy(() -> queryService.listByBiz(BizType.FEED_RECORD, 7001L, 20))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void u14VoidLogUsesVoidText() {
        RecordLogRow row = new RecordLogRow();
        row.setId(2L);
        row.setOperatorId(10001L);
        row.setOperatorName("王建国");
        row.setOpAction("VOID");
        row.setBeforeJson("{\"record_status\":{\"name\":\"记录状态\",\"display\":\"有效\"}}");
        row.setAfterJson("{\"record_status\":{\"name\":\"记录状态\",\"display\":\"已作废\"}}");
        when(operationLogMapper.selectUserLogs(any(), any(), anyInt())).thenReturn(List.of(row));
        grant(1001L, 10001L);

        List<RecordLogVO> logs = queryService.listByBiz(BizType.FEED_RECORD, 7001L, 20);
        assertThat(logs.get(0).getOpActionText()).isEqualTo("作废");
        assertThat(logs.get(0).getChanges().get(0).getBefore()).isEqualTo("有效");
        assertThat(logs.get(0).getChanges().get(0).getAfter()).isEqualTo("已作废");
    }

    @Test
    void penLegacyPartialSnapshotShowsOnlyTheActualCapacityChange() {
        List<RecordLogVO> logs = logs(BizType.PEN, "UPDATE",
                "{\"pen_name\":\"1 号保育舍\",\"pen_type\":\"NURSERY\",\"capacity\":320}",
                "{\"pen_name\":\"1 号保育舍\",\"capacity\":360}");

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("容量");
            assertThat(change.getBefore()).isEqualTo("320");
            assertThat(change.getAfter()).isEqualTo("360");
        });
    }

    @Test
    void penLegacyUnchangedSnapshotDoesNotInventTypeRemovalOrShowEmptyModification() {
        assertThat(logs(BizType.PEN, "UPDATE",
                "{\"pen_name\":\"1 号保育舍\",\"pen_type\":\"NURSERY\",\"capacity\":360}",
                "{\"pen_name\":\"1 号保育舍\",\"capacity\":360}")).isEmpty();
    }

    @Test
    void penLegacyTypeAndStatusCodesHaveChineseDisplay() {
        List<RecordLogVO> typeLogs = logs(BizType.PEN, "UPDATE",
                "{\"pen_type\":\"NURSERY\"}", "{\"pen_type\":\"FATTENING\"}");
        assertThat(typeLogs.get(0).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("栏舍类型");
            assertThat(change.getBefore()).isEqualTo("保育舍");
            assertThat(change.getAfter()).isEqualTo("育肥舍");
        });
        List<RecordLogVO> statusLogs = logs(BizType.PEN, "DISABLE",
                "{\"status\":\"ENABLED\"}", "{\"status\":\"DISABLED\"}");
        assertThat(statusLogs.get(0).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("栏舍状态");
            assertThat(change.getBefore()).isEqualTo("启用");
            assertThat(change.getAfter()).isEqualTo("停用");
        });
    }

    @Test
    void explicitNullStillMeansAFieldWasCleared() {
        List<RecordLogVO> logs = logs(BizType.PEN, "UPDATE",
                "{\"pen_type\":\"NURSERY\"}", "{\"pen_type\":null}");
        assertThat(logs.get(0).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getBefore()).isEqualTo("保育舍");
            assertThat(change.getAfter()).isNull();
        });
    }

    @Test
    void structuredPenSnapshotKeepsUnitsAndDropsUnchangedFields() {
        List<RecordLogVO> logs = logs(BizType.PEN, "UPDATE",
                "{\"pen_type\":{\"name\":\"栏舍类型\",\"display\":\"保育舍\"},"
                        + "\"capacity\":{\"name\":\"容量\",\"display\":\"320 头\"}}",
                "{\"pen_type\":{\"name\":\"栏舍类型\",\"display\":\"保育舍\"},"
                        + "\"capacity\":{\"name\":\"容量\",\"display\":\"360 头\"}}");
        assertThat(logs.get(0).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("容量");
            assertThat(change.getBefore()).isEqualTo("320 头");
            assertThat(change.getAfter()).isEqualTo("360 头");
        });
    }

    @Test
    void otherRecordDeletionRetainsTheBeforeOnlyValue() {
        List<RecordLogVO> logs = logs(BizType.FEED_RECORD, "DELETE",
                "{\"actual_qty\":{\"name\":\"实际投喂量\",\"display\":\"140 千克\"}}", null);
        assertThat(logs.get(0).getOpActionText()).isEqualTo("删除");
        assertThat(logs.get(0).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getBefore()).isEqualTo("140 千克");
            assertThat(change.getAfter()).isNull();
        });
    }

    @Test
    void medicationLegacyCreateUsesChineseLabelsAndAction() {
        RecordLogVO log = logs(BizType.MEDICATION_RECORD, "CREATE", null,
                "{\"used_qty\":555,\"first_date\":\"2026-10-27\"}").get(0);
        assertThat(log.getOpActionText()).isEqualTo("登记用药");
        assertThat(log.getChanges()).extracting(RecordLogVO.Change::getFieldName)
                .containsExactly("实际用量", "首次用药日期");
        assertThat(log.getChanges().get(0).getAfter()).isEqualTo("555");
    }

    @Test
    void medicationLegacyMissingBeforeValueIsUnknownInsteadOfEmpty() {
        RecordLogVO log = logs(BizType.MEDICATION_RECORD, "UPDATE",
                "{\"last_date\":\"2026-10-31\"}",
                "{\"last_date\":\"2026-10-31\",\"used_qty\":555}").get(0);
        assertThat(log.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("实际用量");
            assertThat(change.getBefore()).isEqualTo("未记录");
            assertThat(change.getAfter()).isEqualTo("555");
        });
    }

    @Test
    void medicationStructuredSnapshotsKeepUnitsAndRealDifferencesOnly() {
        RecordLogVO log = logs(BizType.MEDICATION_RECORD, "UPDATE",
                "{\"used_qty\":{\"name\":\"实际用量\",\"display\":\"500 克\"},"
                        + "\"reason\":{\"name\":\"用药原因\",\"display\":\"原说明\"}}",
                "{\"used_qty\":{\"name\":\"实际用量\",\"display\":\"555 克\"},"
                        + "\"reason\":{\"name\":\"用药原因\",\"display\":null}}").get(0);
        assertThat(log.getChanges()).hasSize(2);
        assertThat(log.getChanges().get(0).getBefore()).isEqualTo("500 克");
        assertThat(log.getChanges().get(0).getAfter()).isEqualTo("555 克");
        assertThat(log.getChanges().get(1).getAfter()).isNull();
    }

    @Test
    void medicationLegacyDeleteUsesReadableState() {
        RecordLogVO log = logs(BizType.MEDICATION_RECORD, "DELETE",
                "{\"deleted\":false}", "{\"deleted\":true}").get(0);
        assertThat(log.getOpActionText()).isEqualTo("删除用药记录");
        assertThat(log.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("记录状态");
            assertThat(change.getBefore()).isEqualTo("有效");
            assertThat(change.getAfter()).isEqualTo("已删除");
        });
    }

    private List<RecordLogVO> logs(BizType type, String action, String before, String after) {
        RecordLogRow row = new RecordLogRow();
        row.setId(1L);
        row.setOperatorId(10001L);
        row.setOperatorName("王建国");
        row.setOpAction(action);
        row.setBeforeJson(before);
        row.setAfterJson(after);
        row.setFarmId(1001L);
        when(operationLogMapper.selectUserLogs(type.name(), 2001L, 20)).thenReturn(List.of(row));
        grant(1001L, 10001L);
        return queryService.listByBiz(type, 2001L, 20);
    }

    private void grant(Long farmId, Long userId) {
        when(farmMemberCache.get(farmId, userId)).thenReturn(new com.zhimu.server.common.context.FarmContext.FarmMember(
                farmId, "东山猪场", userId, 5001L, FarmRole.OWNER, java.util.Set.of(), FarmStatus.NORMAL));
    }

    @Test
    void financeModificationHistoryKeepsTheSameManagerOnlyAccessAsFinanceRecords() {
        RecordLogRow row = new RecordLogRow();
        row.setId(1L);
        row.setFarmId(1001L);
        row.setBeforeJson("{\"amount\":{\"name\":\"金额\",\"display\":\"18.00 元\"}}");
        row.setAfterJson("{\"amount\":{\"name\":\"金额\",\"display\":\"25.50 元\"}}");
        when(operationLogMapper.selectUserLogs("FINANCE_RECORD", 98001L, 20)).thenReturn(List.of(row));
        when(farmMemberCache.get(1001L, 10001L)).thenReturn(new com.zhimu.server.common.context.FarmContext.FarmMember(
                1001L, "东山猪场", 10001L, 5001L, FarmRole.STAFF, java.util.Set.of(), FarmStatus.NORMAL));

        assertThatThrownBy(() -> queryService.listByBiz(BizType.FINANCE_RECORD, 98001L, 20))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode()).isEqualTo(ErrorCode.ROLE_FORBIDDEN.getCode()));
    }
}
