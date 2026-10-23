package com.zhimu.server.biz.breeding.service.impl;

import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.breeding.BreedingTableInfo;
import com.zhimu.server.biz.breeding.convert.BreedingRecordConvert;
import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.entity.MatingRecord;
import com.zhimu.server.biz.breeding.mapper.BreedingAnimalMapper;
import com.zhimu.server.biz.breeding.mapper.BreedingCycleMapper;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.breeding.mapper.MatingRecordMapper;
import com.zhimu.server.biz.breeding.mapper.PregnancyCheckMapper;
import com.zhimu.server.biz.breeding.mapper.WeaningRecordMapper;
import com.zhimu.server.biz.breeding.query.BreedingDueQuery;
import com.zhimu.server.biz.breeding.support.BreedingSupport;
import com.zhimu.server.biz.breeding.vo.BreedingOverviewVO;
import com.zhimu.server.biz.breeding.vo.FarrowDueItemVO;
import com.zhimu.server.biz.breeding.vo.HatchDueItemVO;
import com.zhimu.server.biz.breeding.vo.PendingCheckVO;
import com.zhimu.server.biz.breeding.vo.SubmissionVO;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** API-B01、B14、B22、B23、B32 的规则：分段计数、待产/待出雏分组、员工裁剪与提交结果。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreedingDashboardServiceImplTest {

    @Mock private BreedingAnimalMapper animalMapper;
    @Mock private BreedingCycleMapper cycleMapper;
    @Mock private MatingRecordMapper matingMapper;
    @Mock private PregnancyCheckMapper checkMapper;
    @Mock private FarrowingRecordMapper farrowingMapper;
    @Mock private WeaningRecordMapper weaningMapper;
    @Mock private IncubationMapper incubationMapper;
    @Mock private BreedingSupport support;
    @Mock private PenQueryService penQueryService;
    @Mock private FarmQueryService farmQueryService;
    @Mock private AccountQueryService accountQueryService;
    @Mock private CategoryQueryService categoryQueryService;

    private BreedingDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        BreedingTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), BeijingTime.ZONE));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 1, "jti"));
        service = new BreedingDashboardServiceImpl(animalMapper, cycleMapper, matingMapper, checkMapper,
                farrowingMapper, weaningMapper, incubationMapper, support, new BreedingRecordConvert(),
                penQueryService, farmQueryService, accountQueryService);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void overviewOwnerReturnsCountsDueSoonPenOptionsAndSwitchFarms() {
        FarmContext.FarmMember owner = member(FarmRole.OWNER, Set.of());
        when(support.viewer()).thenReturn(owner);
        when(animalMapper.selectList(any())).thenReturn(List.of(
                animal(4131L, "0131", 2007L, "PREGNANT", 5131L),
                animal(4109L, "0109", 2007L, "LACTATING", 5109L),
                animal(4155L, "0155", 2006L, "MATED", 5155L),
                animal(4161L, "0161", 2006L, "EMPTY", null),
                animal(4166L, "0166", 2006L, "MATED", 5166L),
                animal(4102L, "0102", 2006L, "CULLED", null)));
        BreedingCycle due = cycle(5131L, 4131L, "PREGNANT", LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 19));
        BreedingCycle lactating = cycle(5109L, 4109L, "FARROWED", LocalDate.of(2026, 5, 30),
                LocalDate.of(2026, 9, 21));
        BreedingCycle checkOne = cycle(5155L, 4155L, "MATED", LocalDate.of(2026, 9, 24),
                LocalDate.of(2027, 1, 16));
        BreedingCycle checkTwo = cycle(5166L, 4166L, "MATED", LocalDate.of(2026, 10, 8),
                LocalDate.of(2027, 1, 30));
        when(cycleMapper.selectList(any())).thenReturn(List.of(due, lactating, checkOne, checkTwo));
        when(cycleMapper.selectBatchIds(any())).thenReturn(List.of(due, lactating, checkOne, checkTwo));
        when(incubationMapper.selectList(any())).thenReturn(List.of(), List.of());
        when(support.categoryOfBreed(1201L)).thenReturn(
                new CategoryInfo(102L, "种猪", "PIG", "猪", "HEAD", "头", true, true));
        when(penQueryService.listPens(1001L, false)).thenReturn(List.of(
                pen(2006L, "ZC-01", "母猪舍", "SOW", 60),
                pen(2007L, "ZC-02", "产房", "FARROWING", 40),
                pen(2003L, "YF-03", "3 号育肥舍", "FATTENING", 220)));
        when(farmQueryService.listFarmsOfUser(10001L)).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").status("NORMAL").build(),
                FarmBriefVO.builder().farmId(1002L).name("西岭鸡场").status("NORMAL").build()));

        BreedingOverviewVO vo = service.overview();

        assertThat(vo.getFarmKind()).isEqualTo("LIVESTOCK");
        assertThat(vo.getAnimalLabel()).isEqualTo("母猪");
        assertThat(vo.getCountUnitText()).isEqualTo("头");
        assertThat(vo.getCounts().getAll()).isEqualTo(5);
        assertThat(vo.getCounts().getPregnant()).isEqualTo(1);
        assertThat(vo.getCounts().getMated()).isEqualTo(2);
        assertThat(vo.getCounts().getEmpty()).isEqualTo(1);
        assertThat(vo.getCounts().getLactating()).isEqualTo(1);
        assertThat(vo.getCounts().getExited()).isEqualTo(1);
        assertThat(vo.getFarrowDueCount()).isEqualTo(1);
        assertThat(vo.getDueSoon()).hasSize(1);
        assertThat(vo.getDueSoon().get(0).getAnimalId()).isEqualTo(4131L);
        assertThat(vo.getDueSoon().get(0).getDaysToDue()).isZero();
        assertThat(vo.getPenOptions()).extracting("penId").containsExactly(2007L, 2006L);
        assertThat(vo.getPenOptions().get(0).getPenTypeText()).isEqualTo("产房");
        assertThat(vo.isHasIncubation()).isFalse();
        assertThat(vo.getSwitchFarms()).isEmpty();
    }

    @Test
    void overviewOwnerAddsSwitchFarmWhenOtherFarmHasHatchDue() {
        when(support.viewer()).thenReturn(member(FarmRole.OWNER, Set.of()));
        when(animalMapper.selectList(any())).thenReturn(List.of());
        when(cycleMapper.selectList(any())).thenReturn(List.of());
        Incubation other = incubation(6001L, 1002L, 3211L, 2301L, "CANDLED",
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 19));
        when(incubationMapper.selectList(any())).thenReturn(List.of(), List.of(other));
        when(farmQueryService.listFarmsOfUser(10001L)).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").status("NORMAL").build(),
                FarmBriefVO.builder().farmId(1002L).name("西岭鸡场").status("NORMAL").build()));
        when(penQueryService.listPens(1001L, false)).thenReturn(List.of());

        BreedingOverviewVO vo = service.overview();

        assertThat(vo.getSwitchFarms()).hasSize(1);
        assertThat(vo.getSwitchFarms().get(0).getFarmId()).isEqualTo(1002L);
        assertThat(vo.getSwitchFarms().get(0).getFarmName()).isEqualTo("西岭鸡场");
        assertThat(vo.getSwitchFarms().get(0).getHatchDueCount()).isEqualTo(1);
    }

    @Test
    void overviewStaffOmitsManagerFieldsAndScopesToPens() {
        FarmContext.FarmMember staff = member(FarmRole.STAFF, Set.of(2007L));
        when(support.viewer()).thenReturn(staff);
        when(animalMapper.selectList(any())).thenReturn(List.of(
                animal(4131L, "0131", 2007L, "PREGNANT", 5131L),
                animal(4142L, "0142", 2006L, "PREGNANT", 5142L)));
        when(cycleMapper.selectList(any())).thenReturn(List.of(
                cycle(5131L, 4131L, "PREGNANT", LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)),
                cycle(5142L, 4142L, "PREGNANT", LocalDate.of(2026, 7, 4), LocalDate.of(2026, 10, 26))));
        when(incubationMapper.selectList(any())).thenReturn(List.of(
                incubation(6001L, 1001L, 3211L, 2301L, "CANDLED", LocalDate.of(2026, 9, 28),
                        LocalDate.of(2026, 10, 19))));
        when(support.categoryOfBreed(anyLong())).thenReturn(
                new CategoryInfo(102L, "种猪", "PIG", "猪", "HEAD", "头", true, true));

        BreedingOverviewVO vo = service.overview();

        assertThat(vo.getCounts()).isNull();
        assertThat(vo.getDueSoon()).isNull();
        assertThat(vo.getPenOptions()).isNull();
        assertThat(vo.getSwitchFarms()).isEmpty();
        assertThat(vo.getFarrowDueCount()).isEqualTo(1);
        assertThat(vo.getHatchDueCount()).isZero();
    }

    @Test
    void pendingChecksOnlyDueMatedOrderedByDays() {
        FarmContext.FarmMember owner = member(FarmRole.OWNER, Set.of());
        when(support.viewer()).thenReturn(owner);
        when(animalMapper.selectList(any())).thenReturn(List.of(
                animal(4155L, "0155", 2006L, "MATED", 5155L),
                animal(4149L, "0149", 2006L, "MATED", 5149L),
                animal(4166L, "0166", 2006L, "MATED", 5166L)));
        when(cycleMapper.selectList(any())).thenReturn(List.of(
                cycle(5155L, 4155L, "MATED", LocalDate.of(2026, 9, 24), LocalDate.of(2027, 1, 16)),
                cycle(5149L, 4149L, "MATED", LocalDate.of(2026, 9, 28), LocalDate.of(2027, 1, 20)),
                cycle(5166L, 4166L, "MATED", LocalDate.of(2026, 10, 8), LocalDate.of(2027, 1, 30))));
        when(support.breeds(any())).thenReturn(Map.of(1201L,
                new BreedQueryService.BreedInfo(1201L, 102L, "长大二元", 114, null, 21, 1101L, true)));

        var page = service.pendingChecks(new PageQuery());

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getList()).extracting(PendingCheckVO::getAnimalId)
                .containsExactly(4155L, 4149L);
        assertThat(page.getList().get(0).getDaysSinceMating()).isEqualTo(25);
        assertThat(page.getList().get(0).getEarTag()).isEqualTo("0155");
    }

    @Test
    void farrowDueGroupsAndManagerCanRegisterOnlyWhenDue() {
        FarmContext.FarmMember owner = member(FarmRole.OWNER, Set.of());
        when(support.viewer()).thenReturn(owner);
        when(animalMapper.selectList(any())).thenReturn(List.of(
                animal(4131L, "0131", 2007L, "PREGNANT", 5131L),
                animal(4136L, "0136", 2007L, "PREGNANT", 5136L),
                animal(4142L, "0142", 2006L, "PREGNANT", 5142L)));
        when(cycleMapper.selectList(any())).thenReturn(List.of(
                cycle(5131L, 4131L, "PREGNANT", LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)),
                cycle(5136L, 4136L, "PREGNANT", LocalDate.of(2026, 6, 30), LocalDate.of(2026, 10, 22)),
                cycle(5142L, 4142L, "PREGNANT", LocalDate.of(2026, 7, 4), LocalDate.of(2026, 10, 26))));
        when(support.breeds(any())).thenReturn(Map.of(1201L,
                new BreedQueryService.BreedInfo(1201L, 102L, "长大二元", 114, null, 21, 1101L, true)));
        when(support.pens(any())).thenReturn(Map.of(
                2007L, pen(2007L, "ZC-02", "产房", "FARROWING", 40),
                2006L, pen(2006L, "ZC-01", "母猪舍", "SOW", 60)));

        BreedingDueQuery query = new BreedingDueQuery();
        query.setPage(1);
        query.setSize(20);
        var page = service.farrowDue(query);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getList()).extracting(FarrowDueItemVO::getAnimalId)
                .containsExactly(4131L, 4136L, 4142L);
        assertThat(page.getList().get(0).getGroup()).isEqualTo("TODAY");
        assertThat(page.getList().get(0).getGroupText()).isEqualTo("今天");
        assertThat(page.getList().get(0).isCanRegister()).isTrue();
        assertThat(page.getList().get(0).getExpectedDueDate()).isEqualTo("2026-10-19");
        assertThat(page.getList().get(1).getGroup()).isEqualTo("WITHIN_7");
        assertThat(page.getList().get(1).isCanRegister()).isFalse();
        assertThat(page.getList().get(2).getGroup()).isEqualTo("WITHIN_7");
        assertThat(page.getTabCounts()).containsEntry("TODAY", 1).containsEntry("WITHIN_7", 2)
                .containsEntry("LATER", 0);
        assertThat(page.getList().get(0).getReminderText()).isEqualTo("10月16日提醒");
    }

    @Test
    void hatchDueGroupsByExpectedDateAndOnlyCandledHasFertileCount() {
        FarmContext.FarmMember owner = member(FarmRole.OWNER, Set.of());
        when(support.viewer()).thenReturn(owner);
        Incubation first = incubation(6001L, 1001L, 3211L, 2301L, "CANDLED",
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 19));
        first.setFertileCount(9120);
        Incubation second = incubation(6002L, 1001L, 3212L, 2302L, "SET",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 26));
        when(incubationMapper.selectList(any())).thenReturn(List.of(first, second));
        when(support.pens(any())).thenReturn(Map.of(
                2301L, pen(2301L, "ZJ-02", "1 号种鸡舍", "BREEDER", 5000),
                2302L, pen(2302L, "ZJ-03", "2 号种鸡舍", "BREEDER", 5000)));
        when(support.batchOrNull(3211L)).thenReturn(batch(3211L, "ZM2603-015"));
        when(support.batchOrNull(3212L)).thenReturn(batch(3212L, "ZM2603-016"));

        BreedingDueQuery query = new BreedingDueQuery();
        query.setPage(1);
        query.setSize(20);
        var page = service.hatchDue(query);

        assertThat(page.getList()).extracting(HatchDueItemVO::getIncubationId).containsExactly(6001L, 6002L);
        assertThat(page.getList().get(0).getGroup()).isEqualTo("TODAY");
        assertThat(page.getList().get(0).getFlockText()).isEqualTo("1 号种鸡舍 · ZM2603-015");
        assertThat(page.getList().get(0).getFertileCount()).isEqualTo(9120);
        assertThat(page.getList().get(0).isCanRegister()).isTrue();
        assertThat(page.getList().get(1).getGroup()).isEqualTo("WITHIN_7");
        assertThat(page.getList().get(1).getFertileCount()).isNull();
        assertThat(page.getList().get(1).isCanRegister()).isFalse();
        assertThat(page.getTabCounts()).containsEntry("TODAY", 1).containsEntry("WITHIN_7", 1)
                .containsEntry("LATER", 0);
    }

    @Test
    void submissionFindsSavedRecordByClientRequestId() {
        when(support.viewer()).thenReturn(member(FarmRole.OWNER, Set.of()));
        MatingRecord record = new MatingRecord();
        record.setId(72001L);
        record.setAnimalId(4131L);
        record.setCycleId(5131L);
        record.setIsFirst(true);
        record.setCreatedBy(10001L);
        when(matingMapper.selectOne(any())).thenReturn(record);
        when(support.requireAnimal(4131L)).thenReturn(animal(4131L, "0131", 2007L, "MATED", 5131L));
        when(support.requireCycle(5131L)).thenReturn(
                cycle(5131L, 4131L, "MATED", LocalDate.of(2026, 6, 27), LocalDate.of(2026, 10, 19)));

        SubmissionVO vo = service.submission("req-1", "MATING");

        assertThat(vo.isSaved()).isTrue();
        assertThat(vo.getType()).isEqualTo("MATING");
        assertThat(vo.getRecordId()).isEqualTo(72001L);
        assertThat(vo.getResult().getRecordType()).isEqualTo("MATING");
        assertThat(vo.getResult().getIsFirst()).isTrue();
        assertThat(vo.getResult().getExpectedDueDate()).isEqualTo("2026-10-19");
    }

    @Test
    void submissionReturnsSavedFalseWhenNotFound() {
        when(support.viewer()).thenReturn(member(FarmRole.OWNER, Set.of()));
        when(weaningMapper.selectOne(any())).thenReturn(null);

        SubmissionVO vo = service.submission("missing", "WEANING");

        assertThat(vo.isSaved()).isFalse();
        assertThat(vo.getType()).isEqualTo("WEANING");
        assertThat(vo.getRecordId()).isNull();
        assertThat(vo.getResult()).isNull();
    }

    private static FarmContext.FarmMember member(FarmRole role, Set<Long> pens) {
        return new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, role, pens, FarmStatus.NORMAL);
    }

    private static BreedingAnimal animal(Long id, String earTag, Long penId, String status, Long cycleId) {
        BreedingAnimal animal = new BreedingAnimal();
        animal.setId(id);
        animal.setFarmId(1001L);
        animal.setEarTag(earTag);
        animal.setBreedId(1201L);
        animal.setPenId(penId);
        animal.setStatus(status);
        animal.setCurrentCycleId(cycleId);
        animal.setParity(3);
        animal.setVersion(8);
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
        return cycle;
    }

    private static Incubation incubation(Long id, Long farmId, Long batchId, Long penId, String status,
                                         LocalDate setDate, LocalDate expected) {
        Incubation incubation = new Incubation();
        incubation.setId(id);
        incubation.setFarmId(farmId);
        incubation.setBreedingBatchId(batchId);
        incubation.setPenId(penId);
        incubation.setBreedId(2101L);
        incubation.setIncubationDays(21);
        incubation.setSetDate(setDate);
        incubation.setSetCount(9600);
        incubation.setExpectedHatchDate(expected);
        incubation.setStatus(status);
        return incubation;
    }

    private static PenInfo pen(Long id, String code, String name, String type, int capacity) {
        String typeText = switch (type) {
            case "FARROWING" -> "产房";
            case "SOW" -> "母猪舍";
            case "BREEDER" -> "种鸡舍";
            default -> "育肥舍";
        };
        return new PenInfo(id, 1001L, code, name, type, typeText, capacity, "ENABLED");
    }

    private static BatchInfo batch(Long id, String no) {
        return new BatchInfo(1001L, id, no, 2101L, 201L, LocalDate.of(2026, 3, 12), 3200, "ACTIVE",
                List.of(2301L), 150, 3300, 100, 0, 2301L);
    }
}
