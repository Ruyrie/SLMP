package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.inspect.InspectFixtures;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.job.InspectionTodoSource;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.query.BatchPenStockRow;
import com.zhimu.server.biz.inspect.query.InspectionFormQuery;
import com.zhimu.server.biz.inspect.query.PenResponsibleRow;
import com.zhimu.server.biz.inspect.request.InspectionSubmitRequest;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionOverviewVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionServiceImplTest {

    @Mock private InspectionMapper inspectionMapper;
    @Mock private InspectionItemResultMapper itemResultMapper;
    @Mock private InspectSupport support;
    @Mock private BizMediaService bizMediaService;
    @Mock private InspectionTodoSource inspectionTodos;

    private InspectionServiceImpl service;
    private BatchInfo batch;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(InspectFixtures.CLOCK);
        InspectFixtures.loginOwner();
        service = new InspectionServiceImpl(inspectionMapper, itemResultMapper, support, bizMediaService, inspectionTodos);
        batch = InspectFixtures.batch(3001L, "ZM2610-031", 101L, 1101L,
                LocalDate.of(2026, 10, 1), 78, 200, 2003L);
        lenient().when(support.member()).thenAnswer(invocation -> FarmContext.require());
        lenient().when(support.farmId()).thenReturn(1001L);
        lenient().when(support.farmName()).thenReturn("东山猪场");
        lenient().when(support.deadline()).thenReturn(LocalTime.of(18, 0));
        lenient().when(support.pens()).thenReturn(List.of(
                InspectFixtures.pen(2003L, "YF-03", "3 号育肥舍", 200)));
        lenient().when(support.requirePen(anyLong()))
                .thenAnswer(invocation -> InspectFixtures.pen(invocation.getArgument(0), "YF-03",
                        "3 号育肥舍", 200));
        lenient().when(support.activeBatches()).thenReturn(List.of(batch));
        lenient().when(support.batchesOfPen(anyLong())).thenReturn(List.of(batch));
        lenient().when(support.requireActiveBatch(anyLong())).thenReturn(batch);
        lenient().when(support.batchPenStock(anyLong(), anyLong())).thenReturn(200);
        lenient().when(support.templateForCategory(anyLong())).thenReturn(InspectFixtures.template());
        lenient().when(support.templateForVersion(anyLong(), any())).thenReturn(InspectFixtures.template());
        lenient().when(support.memberMap()).thenReturn(Map.of(
                10001L, InspectFixtures.member(10001L, "王建国", "OWNER"),
                10002L, InspectFixtures.member(10002L, "刘芳", "MANAGER"),
                10003L, InspectFixtures.member(10003L, "李明", "STAFF")));
        lenient().when(support.memberList()).thenReturn(List.of(
                InspectFixtures.member(10001L, "王建国", "OWNER"),
                InspectFixtures.member(10002L, "刘芳", "MANAGER"),
                InspectFixtures.member(10003L, "李明", "STAFF")));
        lenient().when(support.userName(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return id == 10001L ? "王建国" : id == 10002L ? "刘芳" : "李明";
        });
        lenient().when(support.penName(anyLong())).thenReturn("3 号育肥舍");
        lenient().when(support.reportStatus(anyLong())).thenReturn("DIAGNOSED");
        lenient().when(support.media(any(), anyLong())).thenReturn(List.of());
        PenResponsibleRow responsible = new PenResponsibleRow();
        responsible.setPenId(2003L);
        responsible.setUserId(10003L);
        responsible.setName("李明");
        lenient().when(support.penResponsibles()).thenReturn(List.of(responsible));
        BatchPenStockRow stock = new BatchPenStockRow();
        stock.setBatchId(3001L);
        stock.setPenId(2003L);
        stock.setStockCount(200);
        lenient().when(support.activeBatchPens()).thenReturn(List.of(stock));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        InspectFixtures.logout();
    }

    @Test
    void summaryComputesCountsAndCompletionRate() {
        Inspection inspection = InspectFixtures.inspection(50121L, 2003L, 3001L,
                LocalDate.of(2026, 10, 19), "DONE", true, 10003L, 6001L);
        when(inspectionMapper.selectDay(1001L, LocalDate.of(2026, 10, 19))).thenReturn(List.of(inspection));

        InspectionSummaryVO vo = service.summary("2026-10-19");

        assertThat(vo.getFarmName()).isEqualTo("东山猪场");
        assertThat(vo.getShouldCount()).isEqualTo(1);
        assertThat(vo.getDoneCount()).isEqualTo(1);
        assertThat(vo.getAbnormalCount()).isEqualTo(1);
        assertThat(vo.getPendingCount()).isZero();
        assertThat(vo.getCompletionRate()).isEqualByComparingTo("1.0000");
        assertThat(vo.getFirstAbnormalPenName()).isEqualTo("3 号育肥舍");
        assertThat(vo.isAllDone()).isTrue();
        assertThat(vo.isCanEditDeadline()).isTrue();
    }

    @Test
    void futureDateReturns40001() {
        assertThatThrownBy(() -> service.summary("2026-10-20"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void staffManualMarkReturns40303() {
        InspectFixtures.loginStaff(10003L, Set.of(2003L));
        when(inspectionMapper.selectByClientRequestId(1001L, "req-1")).thenReturn(null);
        when(inspectionMapper.selectByPenDate(1001L, 2003L, LocalDate.of(2026, 10, 19))).thenReturn(null);

        InspectionSubmitRequest request = request(2003L, "req-1", true);
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.ROLE_FORBIDDEN.getCode());
    }

    @Test
    void registerWhenAlreadyRegisteredWithoutVersionReturns40903WithInspectionId() {
        when(inspectionMapper.selectByClientRequestId(1001L, "req-1")).thenReturn(null);
        Inspection existing = InspectFixtures.inspection(50121L, 2003L, 3001L,
                LocalDate.of(2026, 10, 19), "DONE", true, 10003L, null);
        when(inspectionMapper.selectByPenDate(1001L, 2003L, LocalDate.of(2026, 10, 19))).thenReturn(existing);

        InspectionSubmitRequest request = request(2003L, "req-1", false);
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(exception.getMessage()).contains("李明").contains("09:12");
                    assertThat(exception.getData()).isEqualTo(Map.of("inspection_id", 50121L));
                });
    }

    @Test
    void registerPersistsTheRemarkAlongsideTheSelectedInspectionItems() {
        org.mockito.Mockito.doAnswer(call -> {
            call.<Inspection>getArgument(0).setId(50132L);
            return 1;
        }).when(inspectionMapper).insert(any(Inspection.class));
        InspectionSubmitRequest request = request(2003L, "remark-register", false);
        request.setRemark("  已清理料槽并观察采食  ");
        service.register(request);
        org.mockito.ArgumentCaptor<Inspection> saved = org.mockito.ArgumentCaptor.forClass(Inspection.class);
        org.mockito.Mockito.verify(inspectionMapper).insert(saved.capture());
        assertThat(saved.getValue().getRemark()).isEqualTo("已清理料槽并观察采食");
    }

    @Test
    void backfillAlsoPersistsTheRemark() {
        org.mockito.Mockito.doAnswer(call -> {
            call.<Inspection>getArgument(0).setId(50133L);
            return 1;
        }).when(inspectionMapper).insert(any(Inspection.class));
        com.zhimu.server.biz.inspect.request.InspectionBackfillRequest request =
                new com.zhimu.server.biz.inspect.request.InspectionBackfillRequest();
        request.setPenId(2003L);
        request.setInspectDate("2026-10-18");
        request.setExecutorId(10003L);
        request.setClientRequestId("remark-backfill");
        request.setItemResults(List.of(item(101L, 1011L), item(102L, 1021L), item(103L, 1033L)));
        request.setIsManualMarked(false);
        request.setRemark("  补充当天观察记录  ");
        service.backfill(request);
        org.mockito.ArgumentCaptor<Inspection> saved = org.mockito.ArgumentCaptor.forClass(Inspection.class);
        org.mockito.Mockito.verify(inspectionMapper).insert(saved.capture());
        assertThat(saved.getValue().getRemark()).isEqualTo("补充当天观察记录");
    }

    @Test
    void staffBackfillReturns40303() {
        InspectFixtures.loginStaff(10003L, Set.of(2003L));
        doThrow(new BizException(ErrorCode.ROLE_FORBIDDEN, "只有所有者和场长可以补录"))
                .when(support).requireManager(anyString());

        com.zhimu.server.biz.inspect.request.InspectionBackfillRequest request =
                new com.zhimu.server.biz.inspect.request.InspectionBackfillRequest();
        request.setPenId(2003L);
        request.setInspectDate("2026-10-18");
        request.setExecutorId(10003L);
        request.setClientRequestId("req-b");
        assertThatThrownBy(() -> service.backfill(request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.ROLE_FORBIDDEN.getCode());
    }

    @Test
    void staffModifyOtherRecordInFormContextReturns40303() {
        InspectFixtures.loginStaff(10003L, Set.of(2003L));
        Inspection inspection = InspectFixtures.inspection(50121L, 2003L, 3001L,
                LocalDate.of(2026, 10, 19), "DONE", true, 10004L, null);
        when(inspectionMapper.selectById(50121L)).thenReturn(inspection);
        InspectionFormQuery query = new InspectionFormQuery();
        query.setPenId(2003L);
        query.setMode("MODIFY");
        query.setInspectionId(50121L);

        assertThatThrownBy(() -> service.formContext(query))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.ROLE_FORBIDDEN.getCode());
    }

    @Test
    void detailOfOtherPenForStaffReturns40304() {
        InspectFixtures.loginStaff(10003L, Set.of(2004L));
        Inspection inspection = InspectFixtures.inspection(50121L, 2003L, 3001L,
                LocalDate.of(2026, 10, 19), "DONE", true, 10003L, null);
        when(inspectionMapper.selectById(50121L)).thenReturn(inspection);

        assertThatThrownBy(() -> service.detail(50121L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.PEN_FORBIDDEN.getCode());
    }

    @Test
    void findByRequestMissingReturns40401() {
        when(inspectionMapper.selectByClientRequestId(1001L, "missing")).thenReturn(null);
        assertThatThrownBy(() -> service.findByRequest("missing"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void penOverviewTagsAbnormalAndCountsDoneDays() {
        Inspection inspection = InspectFixtures.inspection(50121L, 2003L, 3001L,
                LocalDate.of(2026, 10, 19), "DONE", true, 10003L, null);
        when(inspectionMapper.selectRange(1001L, 2003L, LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 19))).thenReturn(List.of(inspection));

        PenInspectionOverviewVO vo = service.penOverview(2003L, "2026-10-19");

        assertThat(vo.getDays()).hasSize(7);
        assertThat(vo.getDays().get(6).getDayTag()).isEqualTo("ABNORMAL");
        assertThat(vo.getRecent7DoneDays()).isEqualTo(1);
    }

    @Test
    void settingUpdateRejectsOutOfRangeTime() {
        com.zhimu.server.biz.inspect.entity.InspectionSetting setting =
                new com.zhimu.server.biz.inspect.entity.InspectionSetting();
        setting.setFarmId(1001L);
        setting.setDeadlineTime(LocalTime.of(18, 0));
        setting.setRemindBeforeMin(60);
        when(support.setting()).thenReturn(setting);
        com.zhimu.server.biz.inspect.service.impl.InspectionSettingServiceImpl settingService =
                new com.zhimu.server.biz.inspect.service.impl.InspectionSettingServiceImpl(null, support);

        com.zhimu.server.biz.inspect.request.InspectionSettingRequest request =
                new com.zhimu.server.biz.inspect.request.InspectionSettingRequest();
        request.setDeadlineTime("05:00");
        assertThatThrownBy(() -> settingService.update(request))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    private static InspectionSubmitRequest request(long penId, String clientRequestId, boolean manual) {
        InspectionSubmitRequest request = new InspectionSubmitRequest();
        request.setPenId(penId);
        request.setClientRequestId(clientRequestId);
        request.setItemResults(List.of(
                item(101L, 1011L), item(102L, 1021L), item(103L, 1033L)));
        request.setIsManualMarked(manual);
        request.setManualReason(manual ? "个别猪只咳嗽" : null);
        request.setLocation(location());
        return request;
    }

    private static InspectionSubmitRequest.ItemResultRequest item(long itemId, long optionId) {
        InspectionSubmitRequest.ItemResultRequest item = new InspectionSubmitRequest.ItemResultRequest();
        item.setItemId(itemId);
        item.setOptionId(optionId);
        return item;
    }

    private static InspectionSubmitRequest.LocationRequest location() {
        InspectionSubmitRequest.LocationRequest location = new InspectionSubmitRequest.LocationRequest();
        location.setLatitude(42.3025);
        location.setLongitude(118.8516);
        location.setAccuracyM(20.0);
        location.setClientStatus("OK");
        return location;
    }
}
