package com.zhimu.server.biz.breeding.service.impl;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.breeding.BreedingTableInfo;
import com.zhimu.server.biz.breeding.convert.BreedingAnimalConvert;
import com.zhimu.server.biz.breeding.convert.BreedingRecordConvert;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.MatingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.PregnancyCheckMapper;
import com.zhimu.server.biz.breeding.mapper.WeaningRecordMapper;
import com.zhimu.server.biz.breeding.query.BreedingAnimalQuery;
import com.zhimu.server.biz.breeding.request.AnimalExitRequest;
import com.zhimu.server.biz.breeding.request.BreedingAnimalRequest;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalItemVO;
import com.zhimu.server.biz.breeding.vo.RegisterContextVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** API-B02、B04–B09、B11：列表分段与排序、耳号唯一、状态机、登记表单带入。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreedingAnimalServiceImplTest {

    @Mock private BreedingAnimalMapper animalMapper;
    @Mock private BreedingCycleMapper cycleMapper;
    @Mock private MatingRecordMapper matingMapper;
    @Mock private PregnancyCheckMapper checkMapper;
    @Mock private FarrowingRecordMapper farrowingMapper;
    @Mock private WeaningRecordMapper weaningMapper;
    @Mock private BreedingSupport support;
    @Mock private PenQueryService penQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    private BreedingAnimalServiceImpl service;

    @BeforeEach
    void setUp() {
        BreedingTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), BeijingTime.ZONE));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 1, "jti"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        service = new BreedingAnimalServiceImpl(animalMapper, cycleMapper, matingMapper, checkMapper,
                farrowingMapper, weaningMapper, support, new BreedingAnimalConvert(), new BreedingRecordConvert(),
                penQueryService, operationLogRecorder);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void listReturnsTabCountsAndKeyFieldsForTab() {
        BreedingAnimal pregnant = animal(4131L, "0131", 2007L, "PREGNANT", 5131L, 3, 8);
        BreedingAnimal lactating = animal(4109L, "0109", 2007L, "LACTATING", 5109L, 3, 7);
        BreedingAnimal exited = animal(4102L, "0102", 2006L, "CULLED", null, 6, 12);
        exited.setExitType("CULL");
        exited.setExitDate(LocalDate.of(2026, 9, 15));
        when(animalMapper.selectList(any())).thenReturn(List.of(pregnant, lactating, exited));
        BreedingCycle due = cycle(5131L, 4131L, "PREGNANT", LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 10, 19));
        BreedingCycle nursing = cycle(5109L, 4109L, "FARROWED", LocalDate.of(2026, 5, 30),
                LocalDate.of(2026, 9, 21));
        when(cycleMapper.selectBatchIds(any())).thenReturn(List.of(due, nursing));
        FarrowingRecord farrow = new FarrowingRecord();
        farrow.setId(8109L);
        farrow.setAnimalId(4109L);
        farrow.setCycleId(5109L);
        farrow.setFarrowDate(LocalDate.of(2026, 9, 21));
        farrow.setLiveBorn(12);
        when(farrowingMapper.selectList(any())).thenReturn(List.of(farrow));
        when(weaningMapper.selectList(any())).thenReturn(List.of());
        when(support.pens(any())).thenReturn(Map.of(
                2007L, new PenInfo(2007L, 1001L, "ZC-02", "产房", "FARROWING", "产房", 40, "ENABLED"),
                2006L, new PenInfo(2006L, 1001L, "ZC-01", "母猪舍", "SOW", "母猪舍", 60, "ENABLED")));
        when(support.breeds(any())).thenReturn(Map.of(1201L,
                new BreedQueryService.BreedInfo(1201L, 102L, "长大二元", 114, null, 21, 1101L, true)));

        BreedingAnimalQuery query = new BreedingAnimalQuery();
        query.setStatus("ALL");
        var page = service.list(query);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getTabCounts()).containsEntry("ALL", 2).containsEntry("PREGNANT", 1)
                .containsEntry("LACTATING", 1).containsEntry("EXITED", 1);
        assertThat(page.getList()).extracting(BreedingAnimalItemVO::getEarTag).containsExactly("0131", "0109");
        BreedingAnimalItemVO first = page.getList().get(0);
        assertThat(first.getStatusText()).isEqualTo("妊娠");
        assertThat(first.getExpectedDueDate()).isEqualTo("2026-10-19");
        assertThat(first.getDaysToDue()).isZero();
        assertThat(first.getNextAction()).isEqualTo("FARROWING");
        assertThat(first.getNextActionText()).isEqualTo("登记产仔");
        BreedingAnimalItemVO second = page.getList().get(1);
        assertThat(second.getNursingDays()).isEqualTo(28);
        assertThat(second.getLiveBorn()).isEqualTo(12);
        assertThat(second.getNextAction()).isEqualTo("WEANING");
    }

    @Test
    void createRejectsDuplicateEarTagWith40905() {
        when(animalMapper.selectCount(any())).thenReturn(1L);
        BreedingAnimalRequest request = new BreedingAnimalRequest();
        request.setEarTag("0161");
        request.setBreedId(1201L);
        request.setSource("PURCHASED");
        request.setPenId(2006L);

        assertThatThrownBy(() -> service.create(request, "create-1"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.NAME_DUPLICATE.getCode());
                    assertThat(ex.getMessage()).isEqualTo("本场已有耳号 0161 的母猪");
                });
    }

    @Test
    void updateRejectsExitedAnimalWith40903() {
        BreedingAnimal animal = animal(4102L, "0102", 2006L, "CULLED", null, 6, 12);
        when(support.requireAnimal(4102L)).thenReturn(animal);
        BreedingAnimalRequest request = new BreedingAnimalRequest();
        request.setVersion(12);

        assertThatThrownBy(() -> service.update(4102L, request))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(ex.getMessage()).isEqualTo("0102 已淘汰，档案只能查看");
                });
    }

    @Test
    void exitRejectsStaleVersionWith40902() {
        BreedingAnimal animal = animal(4161L, "0161", 2006L, "PREGNANT", 5061L, 2, 3);
        when(support.requireAnimal(4161L)).thenReturn(animal);
        AnimalExitRequest request = new AnimalExitRequest();
        request.setExitType("DEATH");
        request.setExitDate("2026-10-19");
        request.setReason("难产");
        request.setVersion(2);

        assertThatThrownBy(() -> service.exit(4161L, request, "exit-1"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT.getCode()));
    }

    @Test
    void farrowingContextReturnsDefaultPenAndOffspringBreed() {
        when(support.farmId()).thenReturn(1001L);
        BreedingAnimal animal = animal(4131L, "0131", 2007L, "PREGNANT", 5131L, 3, 8);
        when(support.requireAnimal(4131L)).thenReturn(animal);
        when(support.currentCycle(animal)).thenReturn(cycle(5131L, 4131L, "PREGNANT",
                LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)));
        when(support.breed(1201L)).thenReturn(
                new BreedQueryService.BreedInfo(1201L, 102L, "长大二元", 114, null, 21, 1101L, true));
        when(support.breedName(1101L)).thenReturn("杜长大");
        when(support.pen(2007L)).thenReturn(new PenInfo(2007L, 1001L, "ZC-02", "产房", "FARROWING",
                "产房", 40, "ENABLED"));
        when(support.breed(1101L)).thenReturn(new BreedQueryService.BreedInfo(1101L,101L,"杜长大",null,null,null,null,true));
        when(penQueryService.listPensForCategory(1001L,101L)).thenReturn(List.of(new PenInfo(2002L,1001L,"BY-02","2号保育舍","NURSERY","保育舍",400,"ENABLED")));
        when(support.penCurrentCount(2002L)).thenReturn(0);
        when(support.unitOfBreed(1101L)).thenReturn("头");
        when(farrowingMapper.selectList(any())).thenReturn(List.of());

        RegisterContextVO context = service.registerContext(4131L, "FARROWING");

        assertThat(context.getType()).isEqualTo("FARROWING");
        assertThat(context.getCycleId()).isEqualTo(5131L);
        assertThat(context.getExpectedDueDate()).isEqualTo("2026-10-19");
        assertThat(context.getDaysToDue()).isZero();
        assertThat(context.getDefaultPen().getPenId()).isEqualTo(2002L);
        assertThat(context.getDefaultPen().getPenName()).isEqualTo("2号保育舍");
        assertThat(context.getDefaultPen().getCapacity()).isEqualTo(400);
        assertThat(context.getDefaultPen().getUnit()).isEqualTo("头");
        assertThat(context.getOffspringBreed().getBreedId()).isEqualTo(1101L);
        assertThat(context.getOffspringBreed().getBreedName()).isEqualTo("杜长大");
        assertThat(context.getAnimal().getVersion()).isEqualTo(8);
    }

    @Test
    void registerContextRejectsWrongStatusWith40903() {
        BreedingAnimal animal = animal(4161L, "0161", 2006L, "EMPTY", null, 2, 3);
        when(support.requireAnimal(4161L)).thenReturn(animal);

        assertThatThrownBy(() -> service.registerContext(4161L, "FARROWING"))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode());
                    assertThat(ex.getMessage()).isEqualTo("0161 的状态已变化，请刷新后再登记");
                });
    }

    private static BreedingAnimal animal(Long id, String earTag, Long penId, String status, Long cycleId,
                                         int parity, int version) {
        BreedingAnimal animal = new BreedingAnimal();
        animal.setId(id);
        animal.setFarmId(1001L);
        animal.setEarTag(earTag);
        animal.setBreedId(1201L);
        animal.setPenId(penId);
        animal.setStatus(status);
        animal.setCurrentCycleId(cycleId);
        animal.setParity(parity);
        animal.setVersion(version);
        animal.setInitialParity(1);
        animal.setFilingDate(LocalDate.of(2025, 3, 24));
        animal.setBirthDate(LocalDate.of(2024, 12, 24));
        animal.setSource("PURCHASED");
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
