package com.zhimu.server.biz.breeding.service.impl;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.biz.breeding.BreedingTableInfo;
import com.zhimu.server.biz.breeding.convert.BreedingRecordConvert;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.entity.MatingRecord;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.MatingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.PregnancyCheckMapper;
import com.zhimu.server.biz.breeding.mapper.WeaningRecordMapper;
import com.zhimu.server.biz.breeding.request.FarrowingRequest;
import com.zhimu.server.biz.breeding.request.MatingRequest;
import com.zhimu.server.biz.breeding.request.WeaningRequest;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.vo.CapacityConfirmVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** API-B12–B21 的核心规则：状态机、数量校验、修改限制与超容量。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreedingRecordServiceImplTest {

    @Mock private BreedingAnimalMapper animalMapper;
    @Mock private BreedingCycleMapper cycleMapper;
    @Mock private MatingRecordMapper matingMapper;
    @Mock private PregnancyCheckMapper checkMapper;
    @Mock private FarrowingRecordMapper farrowingMapper;
    @Mock private WeaningRecordMapper weaningMapper;
    @Mock private BreedingSupport support;
    @Mock private OperationLogRecorder operationLogRecorder;

    private BreedingRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        BreedingTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), BeijingTime.ZONE));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 1, "jti"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        service = new BreedingRecordServiceImpl(animalMapper, cycleMapper, matingMapper, checkMapper,
                farrowingMapper, weaningMapper, support, new BreedingRecordConvert(), operationLogRecorder);
        when(support.farmId()).thenReturn(1001L);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void createMatingRejectsExitedAnimal() {
        BreedingAnimal animal = animal(4161L, "0161", BreedingCodes.STATUS_CULLED, null);
        when(support.requireAnimal(4161L)).thenReturn(animal);

        assertThatThrownBy(() -> service.createMating(matingRequest(), "mating-1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(ex.getMessage()).isEqualTo("0161 已淘汰，不能登记配种");
                });
    }

    @Test
    void createMatingOnEmptyAnimalOpensCycleAndReturnsDueDate() {
        BreedingAnimal animal = animal(4161L, "0161", BreedingCodes.STATUS_EMPTY, null);
        animal.setPenId(2006L);
        animal.setLastCycleEndDate(LocalDate.of(2026, 10, 12));
        animal.setBirthDate(LocalDate.of(2024, 6, 8));
        when(support.requireAnimal(4161L)).thenReturn(animal);
        when(support.breed(1201L)).thenReturn(
                new BreedQueryService.BreedInfo(1201L, 102L, "长大二元", 114, null, 21, 1101L, true));
        doAnswer(inv -> {
            ((BreedingCycle) inv.getArgument(0)).setId(6001L);
            return 1;
        }).when(cycleMapper).insert(any(BreedingCycle.class));
        doAnswer(inv -> {
            ((MatingRecord) inv.getArgument(0)).setId(72099L);
            return 1;
        }).when(matingMapper).insert(any(MatingRecord.class));
        when(animalMapper.updateById(any(BreedingAnimal.class))).thenReturn(1);

        RegisterResultVO result = service.createMating(matingRequest(), "mating-1");

        assertThat(result.getRecordType()).isEqualTo("MATING");
        assertThat(result.getRecordId()).isEqualTo(72099L);
        assertThat(result.getIsFirst()).isTrue();
        assertThat(result.getAnimalStatus()).isEqualTo("MATED");
        assertThat(result.getExpectedDueDate()).isEqualTo("2027-02-10");
        assertThat(animal.getStatus()).isEqualTo("MATED");
        assertThat(animal.getCurrentCycleId()).isEqualTo(6001L);
    }

    @Test
    void createCheckRejectsSecondCheckInSameCycle() {
        BreedingAnimal animal = animal(4155L, "0155", BreedingCodes.STATUS_MATED, 5155L);
        when(support.requireAnimal(4155L)).thenReturn(animal);
        when(support.requireCycle(5155L)).thenReturn(cycle(5155L, 4155L, "MATED",
                LocalDate.of(2026, 9, 24), LocalDate.of(2027, 1, 16)));
        when(checkMapper.selectCount(any())).thenReturn(1L);

        var request = new com.zhimu.server.biz.breeding.request.PregnancyCheckRequest();
        request.setAnimalId(4155L);
        request.setCycleId(5155L);
        request.setCheckDate("2026-10-19");
        request.setResult("POSITIVE");

        assertThatThrownBy(() -> service.createCheck(request, "check-1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(ex.getMessage()).isEqualTo("本次配种已登记妊检");
                });
    }

    @Test
    void createFarrowingRejectsDuplicateForSameCycle() {
        BreedingAnimal animal = animal(4131L, "0131", BreedingCodes.STATUS_PREGNANT, 5131L);
        when(support.requireAnimal(4131L)).thenReturn(animal);
        when(support.requireCycle(5131L)).thenReturn(cycle(5131L, 4131L, "PREGNANT",
                LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)));
        when(farrowingMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createFarrowing(farrowingRequest(13, 12, 1), "farrow-1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(ex.getMessage()).contains("本胎已登记产仔");
                });
    }

    @Test
    void createFarrowingCountMismatchReturns40001() {
        BreedingAnimal animal = animal(4131L, "0131", BreedingCodes.STATUS_PREGNANT, 5131L);
        when(support.requireAnimal(4131L)).thenReturn(animal);
        when(support.requireCycle(5131L)).thenReturn(cycle(5131L, 4131L, "PREGNANT",
                LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)));
        when(farrowingMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.createFarrowing(farrowingRequest(13, 11, 1), "farrow-1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                    assertThat(ex.getMessage()).contains("与总产仔 13 头不一致");
                });
    }

    @Test
    void createFarrowingOverCapacityReturns42206WithConfirmData() {
        BreedingAnimal animal = animal(4131L, "0131", BreedingCodes.STATUS_PREGNANT, 5131L);
        animal.setPenId(2007L);
        when(support.requireAnimal(4131L)).thenReturn(animal);
        when(support.requireCycle(5131L)).thenReturn(cycle(5131L, 4131L, "PREGNANT",
                LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)));
        when(farrowingMapper.selectCount(any())).thenReturn(0L);
        when(support.unitOfBreed(1101L)).thenReturn("头");
        when(support.breed(1201L)).thenReturn(
                new BreedQueryService.BreedInfo(1201L, 102L, "长大二元", 114, null, 21, 1101L, true));
        when(support.breed(1101L)).thenReturn(
                new BreedQueryService.BreedInfo(1101L, 101L, "杜长大", null, null, null, null, true));
        when(support.pen(2007L)).thenReturn(new PenInfo(2007L, 1001L, "ZC-02", "产房", "FARROWING",
                "产房", 40, "ENABLED"));
        CapacityConfirmVO data = new CapacityConfirmVO();
        data.setNeedConfirm(true);
        data.setPenId(2007L);
        doThrow(new BizException(ErrorCode.CAPACITY_CONFIRM, "入栏后超过栏舍容量。", data))
                .when(support).checkCapacity(eq(2007L), any(), eq("头"), eq(45), eq(false));

        assertThatThrownBy(() -> service.createFarrowing(farrowingRequest(45, 45, 0), "farrow-2"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPACITY_CONFIRM.getCode());
                    assertThat(ex.getData()).isSameAs(data);
                });
    }

    @Test
    void updateFarrowingWithBatchRequiresChangeRequest() {
        FarrowingRecord record = new FarrowingRecord();
        record.setId(8161L);
        record.setCycleId(5061L);
        record.setAnimalId(4161L);
        record.setBatchId(3052L);
        record.setVersion(3);
        when(farrowingMapper.selectById(8161L)).thenReturn(record);

        var request = new com.zhimu.server.biz.breeding.request.FarrowingUpdateRequest();
        request.setVersion(3);

        assertThatThrownBy(() -> service.updateFarrowing(8161L, request))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.CHANGE_REQUEST_REQUIRED.getCode());
                    assertThat(ex.getMessage()).isEqualTo("已转为新批次的产仔记录需提交修改申请");
                });
    }

    @Test
    void createWeaningRejectsAnimalNotLactating() {
        BreedingAnimal animal = animal(4161L, "0161", BreedingCodes.STATUS_EMPTY, null);
        when(support.requireAnimal(4161L)).thenReturn(animal);

        WeaningRequest request = new WeaningRequest();
        request.setAnimalId(4161L);
        request.setWeanDate("2026-10-19");
        request.setWeanedCount(10);

        assertThatThrownBy(() -> service.createWeaning(request, "wean-1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(ex.getMessage()).isEqualTo("0161 的状态已变化，请刷新后再登记");
                });
    }

    @Test
    void createMatingRequiresIdempotencyKey() {
        assertThatThrownBy(() -> service.createMating(matingRequest(), " "))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }

    private static MatingRequest matingRequest() {
        MatingRequest request = new MatingRequest();
        request.setAnimalId(4161L);
        request.setAnimalVersion(3);
        request.setMatingDate("2026-10-19");
        request.setMatingMethod("ARTIFICIAL");
        return request;
    }

    private static FarrowingRequest farrowingRequest(int total, int live, int still) {
        FarrowingRequest request = new FarrowingRequest();
        request.setAnimalId(4131L);
        request.setAnimalVersion(8);
        request.setFarrowDate("2026-10-19");
        request.setTotalBorn(total);
        request.setLiveBorn(live);
        request.setStillborn(still);
        return request;
    }

    private static BreedingAnimal animal(Long id, String earTag, String status, Long cycleId) {
        BreedingAnimal animal = new BreedingAnimal();
        animal.setId(id);
        animal.setFarmId(1001L);
        animal.setEarTag(earTag);
        animal.setBreedId(1201L);
        animal.setPenId(2006L);
        animal.setStatus(status);
        animal.setCurrentCycleId(cycleId);
        animal.setParity(3);
        animal.setVersion(3);
        return animal;
    }

    private static BreedingCycle cycle(Long id, Long animalId, String status, LocalDate firstMating,
                                       LocalDate expectedDue) {
        BreedingCycle cycle = new BreedingCycle();
        cycle.setId(id);
        cycle.setFarmId(1001L);
        cycle.setAnimalId(animalId);
        cycle.setStatus(status);
        cycle.setFirstMatingDate(firstMating);
        cycle.setLastMatingDate(firstMating);
        cycle.setMatingCount(1);
        cycle.setGestationDays(114);
        cycle.setPregnancyCheckDays(21);
        cycle.setCheckDueDate(firstMating.plusDays(21));
        cycle.setExpectedDueDate(expectedDue);
        cycle.setVersion(2);
        return cycle;
    }
}
