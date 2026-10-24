package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.mapper.ImmuneRecordMapper;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.query.FarmMemberRow;
import com.zhimu.server.biz.health.query.PenResponsibleRow;
import com.zhimu.server.biz.health.request.ImmuneRecordCreateRequest;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.health.vo.ImmuneRecordVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.QtyVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登记接种（API-D14）的库存链路：DrugStockPort 用 Mockito mock，验证 IMMUNE_RECORD 扣减、计划状态回写与员工裁剪口径。
 * 时间固定为 2026-10-19 10:00（北京时间，周一）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImmuneRecordServiceImplTest {

    private static final Long FARM_ID = 1001L;
    private static final Long BATCH_ID = 3001L;
    private static final Long PEN_ID = 2003L;
    private static final Long PLAN_ID = 8117L;
    private static final Long DRUG_ID = 5001L;
    private static final Long LOT_ID = 6001L;

    @Mock private ImmuneRecordMapper recordMapper;
    @Mock private ImmunePlanMapper planMapper;
    @Mock private HealthReferenceMapper referenceMapper;
    @Mock private BatchQueryService batchQueryService;
    @Mock private PenQueryService penQueryService;
    @Mock private DictQueryService dictQueryService;
    @Mock private CategoryQueryService categoryQueryService;
    @Mock private DrugStockPort drugStockPort;
    @Mock private DrugCatalogQueryService drugCatalogQueryService;
    @Mock private HealthAudit audit;
    @Mock private HealthTodoSource todoSource;

    private ImmuneRecordServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        HealthTableInfo.ensure();
    }

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), BeijingTime.ZONE));
        FarmContext.set(new FarmContext.FarmMember(FARM_ID, "东山猪场", 10001L, 5001L,
                FarmRole.OWNER, Set.of(), FarmStatus.NORMAL));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 1, "jti"));
        RequestContext.set(new RequestContext.Info(ClientType.APP_ANDROID, "1.0.0", "127.0.0.1", "test",
                "req-immune-1"));
        HealthSupport support = new HealthSupport(referenceMapper, batchQueryService, penQueryService,
                dictQueryService, categoryQueryService, drugStockPort);
        service = new ImmuneRecordServiceImpl(recordMapper, planMapper, referenceMapper, support, audit,
                drugStockPort, drugCatalogQueryService, todoSource);

        when(planMapper.selectById(PLAN_ID)).thenReturn(plan());
        when(planMapper.update(isNull(), any())).thenReturn(1);
        when(recordMapper.insert(any(ImmuneRecord.class))).thenAnswer(invocation -> {
            ImmuneRecord record = invocation.getArgument(0);
            record.setId(8801L);
            record.setVersion(0);
            return 1;
        });
        when(recordMapper.update(isNull(), any())).thenReturn(1);
        when(referenceMapper.selectDrugs(FARM_ID)).thenReturn(List.of(drug()));
        when(referenceMapper.selectMembers(FARM_ID)).thenReturn(List.of(owner()));
        when(referenceMapper.selectPenResponsibles(FARM_ID)).thenReturn(List.of(penResponsible()));
        when(referenceMapper.selectUserName(anyLong())).thenReturn("李明");
        when(referenceMapper.selectBreedName(anyLong())).thenReturn("杜长大");
        when(batchQueryService.requireBatch(BATCH_ID)).thenReturn(batch());
        when(penQueryService.getPens(any())).thenReturn(Map.of(PEN_ID, pen()));
        when(dictQueryService.nameOf("VACCINATE_METHOD", "IM")).thenReturn("肌肉注射");
        when(categoryQueryService.info(101L)).thenReturn(new CategoryInfo(101L, "育肥猪", "PIG", "猪",
                "HEAD", "头", false, true));
        when(drugStockPort.lotsFor(eq(FARM_ID), eq(DRUG_ID), eq("VACCINATE"), any()))
                .thenReturn(List.of(lotOption()));
        when(drugStockPort.deduct(any())).thenReturn(new StockConsumeResult(90001L, new BigDecimal("2.35"),
                new BigDecimal("470.00"), new BigDecimal("200"), "2025081", List.of()));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        FarmContext.clear();
        UserContext.clear();
        RequestContext.clear();
    }

    @Test
    void createDeductsVaccineStockWithImmuneBizType() {
        ImmuneRecordVO vo = service.create(request(200));

        assertThat(vo.getId()).isEqualTo(8801L);
        assertThat(vo.getQuantity()).isEqualTo(200);
        assertThat(vo.getDoseUnit()).isEqualTo("头份 / 头");
        assertThat(vo.getMethodName()).isEqualTo("肌肉注射");
        assertThat(vo.getTodoCompleted()).isTrue();
        assertThat(vo.getOverdueDone()).isFalse();

        ArgumentCaptor<DrugStockCommand> captor = ArgumentCaptor.forClass(DrugStockCommand.class);
        verify(drugStockPort).deduct(captor.capture());
        verify(todoSource).completeImmune(any(ImmunePlan.class), eq(8801L), eq(10003L));
        DrugStockCommand command = captor.getValue();
        assertThat(command.bizType()).isEqualTo(DrugStockCommand.BIZ_IMMUNE_RECORD);
        assertThat(command.flowType()).isEqualTo(DrugStockCommand.FLOW_VACCINE_USE);
        assertThat(command.bizId()).isEqualTo(8801L);
        assertThat(command.batchId()).isEqualTo(BATCH_ID);
        assertThat(command.penId()).isEqualTo(PEN_ID);
        assertThat(command.drugLotId()).isEqualTo(LOT_ID);
        assertThat(command.qty()).isEqualByComparingTo("200.000");
        assertThat(command.bizDate()).isEqualTo(LocalDate.of(2026, 10, 19));

        verify(planMapper).update(isNull(), any());
        verify(audit).record(any(), eq("IMMUNE_RECORD"), eq(8801L), eq("UPDATE"), any(), any(), isNull(), any());
    }

    @Test
    void createRejectsQuantityAboveCurrentCount() {
        assertThatThrownBy(() -> service.create(request(201)))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode())
                        .isEqualTo(ErrorCode.COUNT_EXCEEDED.getCode()))
                .hasMessageContaining("不能超过存栏 200 头");
    }

    // ===== fixtures =====

    private ImmuneRecordCreateRequest request(int quantity) {
        ImmuneRecordCreateRequest request = new ImmuneRecordCreateRequest();
        request.setPlanId(PLAN_ID);
        request.setDrugId(DRUG_ID);
        request.setDrugLotId(LOT_ID);
        request.setDose(BigDecimal.ONE);
        request.setQuantity(quantity);
        request.setMethodCode("IM");
        request.setVaccinateDate(LocalDate.of(2026, 10, 19));
        return request;
    }

    private ImmunePlan plan() {
        ImmunePlan plan = new ImmunePlan();
        plan.setId(PLAN_ID);
        plan.setFarmId(FARM_ID);
        plan.setTargetType("BATCH");
        plan.setBatchId(BATCH_ID);
        plan.setCatalogId(7001L);
        plan.setDrugId(DRUG_ID);
        plan.setVaccineName("猪瘟活疫苗");
        plan.setImmuneRound("加强免疫");
        plan.setPlanMode("BY_DAY_AGE");
        plan.setPlanDate(LocalDate.of(2026, 10, 19));
        plan.setDueTime(LocalTime.of(18, 0));
        plan.setDose(BigDecimal.ONE);
        plan.setDoseUnit("DOSE_PER_HEAD");
        plan.setMethodCode("IM");
        plan.setStatus("PENDING");
        plan.setVersion(2);
        return plan;
    }

    private BatchInfo batch() {
        return new BatchInfo(FARM_ID, BATCH_ID, "ZM2610-031", 1101L, 101L,
                LocalDate.of(2026, 10, 1), 200, "ACTIVE", List.of(PEN_ID), 78, 205, 5, 0, PEN_ID);
    }

    private PenInfo pen() {
        return new PenInfo(PEN_ID, FARM_ID, "P03", "3 号育肥舍", "FATTEN", "育肥舍", 200, "ENABLED");
    }

    private FarmDrugRow drug() {
        FarmDrugRow row = new FarmDrugRow();
        row.setId(DRUG_ID);
        row.setCatalogId(7001L);
        row.setCategory("VACCINE");
        row.setName("猪瘟活疫苗");
        row.setUnit("DOSE");
        row.setStatus("ENABLED");
        return row;
    }

    private FarmMemberRow owner() {
        FarmMemberRow row = new FarmMemberRow();
        row.setUserId(10001L);
        row.setName("王建国");
        row.setRole("OWNER");
        return row;
    }

    private PenResponsibleRow penResponsible() {
        PenResponsibleRow row = new PenResponsibleRow();
        row.setPenId(PEN_ID);
        row.setUserId(10003L);
        row.setName("李明");
        return row;
    }

    private DrugLotOption lotOption() {
        QtyVO stock = QtyVO.builder().value(new BigDecimal("400")).unit("DOSE").unitText("头份").build();
        return new DrugLotOption(LOT_ID, "2025081", LocalDate.of(2026, 12, 30), 72, "NORMAL", "正常",
                stock, true, false, false, new BigDecimal("2.35"), 1);
    }
}
