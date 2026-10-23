package com.zhimu.server.biz.breeding.controller;

import com.zhimu.server.biz.breeding.BreedingTestMvc;
import com.zhimu.server.biz.breeding.service.BreedingAnimalService;
import com.zhimu.server.biz.breeding.service.BreedingDashboardService;
import com.zhimu.server.biz.breeding.service.BreedingRecordService;
import com.zhimu.server.biz.breeding.service.IncubationService;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalDetailVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalItemVO;
import com.zhimu.server.biz.breeding.vo.BreedingAnimalOptionVO;
import com.zhimu.server.biz.breeding.vo.BreedingOverviewVO;
import com.zhimu.server.biz.breeding.vo.BreedingRecordVO;
import com.zhimu.server.biz.breeding.vo.CapacityConfirmVO;
import com.zhimu.server.biz.breeding.vo.FarrowDueItemVO;
import com.zhimu.server.biz.breeding.vo.FarrowingDetailVO;
import com.zhimu.server.biz.breeding.vo.HatchDueItemVO;
import com.zhimu.server.biz.breeding.vo.IncubationDetailVO;
import com.zhimu.server.biz.breeding.vo.PendingCheckVO;
import com.zhimu.server.biz.breeding.vo.RegisterContextVO;
import com.zhimu.server.biz.breeding.vo.RegisterResultVO;
import com.zhimu.server.biz.breeding.vo.SubmissionVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 模块05 的 27 个 App 映射：路径、方法、关键字段与状态错误码。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreedingControllersMockMvcTest {

    @Mock private BreedingAnimalService animalService;
    @Mock private BreedingRecordService recordService;
    @Mock private IncubationService incubationService;
    @Mock private BreedingDashboardService dashboardService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = BreedingTestMvc.builder(
                new BreedingAnimalController(animalService),
                new BreedingDashboardController(dashboardService),
                new BreedingRecordController(recordService),
                new IncubationController(incubationService)).build();
    }

    // ===== 种畜档案（8 个映射） =====

    @Test
    void b01Overview() throws Exception {
        BreedingOverviewVO vo = new BreedingOverviewVO();
        vo.setFarmKind("LIVESTOCK");
        vo.setFarmKindText("畜类");
        vo.setFarrowDueCount(11);
        BreedingOverviewVO.Counts counts = new BreedingOverviewVO.Counts();
        counts.setAll(24);
        counts.setPregnant(11);
        vo.setCounts(counts);
        when(dashboardService.overview()).thenReturn(vo);

        mvc.perform(get("/api/v1/breeding/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.farm_kind").value("LIVESTOCK"))
                .andExpect(jsonPath("$.data.farrow_due_count").value(11))
                .andExpect(jsonPath("$.data.counts.all").value(24))
                .andExpect(jsonPath("$.data.counts.pregnant").value(11))
                .andExpect(jsonPath("$.data.hatch_due_count").value(0));
    }

    @Test
    void b02Animals() throws Exception {
        BreedingAnimalItemVO item = new BreedingAnimalItemVO();
        item.setAnimalId(4131L);
        item.setEarTag("0131");
        item.setStatus("PREGNANT");
        item.setStatusText("妊娠");
        item.setDaysSinceMating(114);
        when(animalService.list(any())).thenReturn(new PageVO<>(List.of(item), 1, 1, 20));

        mvc.perform(get("/api/v1/breeding-animals").param("status", "PREGNANT")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].animal_id").value(4131))
                .andExpect(jsonPath("$.data.list[0].ear_tag").value("0131"))
                .andExpect(jsonPath("$.data.list[0].status_text").value("妊娠"))
                .andExpect(jsonPath("$.data.list[0].days_since_mating").value(114))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    @Test
    void b04Options() throws Exception {
        BreedingAnimalOptionVO option = new BreedingAnimalOptionVO();
        option.setAnimalId(4161L);
        option.setEarTag("0161");
        option.setGestationDays(114);
        when(animalService.options(any())).thenReturn(new PageVO<>(List.of(option), 1, 1, 50));

        mvc.perform(get("/api/v1/breeding-animals/options").param("purpose", "MATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].animal_id").value(4161))
                .andExpect(jsonPath("$.data.list[0].gestation_days").value(114))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    void b05AnimalDetail() throws Exception {
        BreedingAnimalDetailVO vo = new BreedingAnimalDetailVO();
        vo.setAnimalId(4131L);
        vo.setEarTag("0131");
        vo.setAnimalLabel("母猪");
        vo.setCanEdit(true);
        when(animalService.detail(4131L)).thenReturn(vo);

        mvc.perform(get("/api/v1/breeding-animals/4131"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.animal_id").value(4131))
                .andExpect(jsonPath("$.data.ear_tag").value("0131"))
                .andExpect(jsonPath("$.data.animal_label").value("母猪"))
                .andExpect(jsonPath("$.data.can_edit").value(true));
    }

    @Test
    void b06Records() throws Exception {
        BreedingRecordVO record = new BreedingRecordVO();
        record.setRecordType("MATING");
        record.setRecordTypeText("配种");
        record.setRecordId(71002L);
        record.setRecordDate("2026-06-27");
        when(animalService.records(eq(4131L), anyInt(), anyInt()))
                .thenReturn(new PageVO<>(List.of(record), 1, 1, 20));

        mvc.perform(get("/api/v1/breeding-animals/4131/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].record_type").value("MATING"))
                .andExpect(jsonPath("$.data.list[0].record_id").value(71002))
                .andExpect(jsonPath("$.data.list[0].record_date").value("2026-06-27"));
    }

    @Test
    void b11RegisterContext() throws Exception {
        RegisterContextVO vo = new RegisterContextVO();
        vo.setType("MATING");
        vo.setGestationDays(114);
        vo.setMinMatingDate("2026-10-12");
        when(animalService.registerContext(4161L, "MATING")).thenReturn(vo);

        mvc.perform(get("/api/v1/breeding-animals/4161/register-context").param("type", "MATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MATING"))
                .andExpect(jsonPath("$.data.gestation_days").value(114))
                .andExpect(jsonPath("$.data.min_mating_date").value("2026-10-12"));
    }

    @Test
    void b07CreateAnimal() throws Exception {
        BreedingAnimalDetailVO vo = new BreedingAnimalDetailVO();
        vo.setAnimalId(4185L);
        vo.setEarTag("0185");
        when(animalService.create(any(), anyString())).thenReturn(vo);

        mvc.perform(post("/api/v1/breeding-animals")
                        .header("Idempotency-Key", "create-animal-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ear_tag":"0185","breed_id":1201,"source":"PURCHASED","pen_id":2006}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.animal_id").value(4185))
                .andExpect(jsonPath("$.data.ear_tag").value("0185"));
    }

    @Test
    void b08UpdateAnimal() throws Exception {
        BreedingAnimalDetailVO vo = new BreedingAnimalDetailVO();
        vo.setAnimalId(4131L);
        vo.setVersion(9);
        when(animalService.update(eq(4131L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/breeding-animals/4131")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ear_tag":"0131","breed_id":1201,"source":"PURCHASED","pen_id":2007,"version":8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.animal_id").value(4131))
                .andExpect(jsonPath("$.data.version").value(9));
    }

    @Test
    void b09ExitAnimal() throws Exception {
        BreedingAnimalDetailVO vo = new BreedingAnimalDetailVO();
        vo.setAnimalId(4161L);
        vo.setStatus("DEAD");
        vo.setStatusText("已死亡");
        when(animalService.exit(eq(4161L), any(), anyString())).thenReturn(vo);

        mvc.perform(post("/api/v1/breeding-animals/4161/exit")
                        .header("Idempotency-Key", "exit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exit_type":"DEATH","exit_date":"2026-10-19","reason":"难产","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEAD"))
                .andExpect(jsonPath("$.data.status_text").value("已死亡"));
    }

    // ===== 繁育概况待办（4 个映射 + 提交查询） =====

    @Test
    void b14PendingChecks() throws Exception {
        PendingCheckVO vo = new PendingCheckVO();
        vo.setAnimalId(4155L);
        vo.setCycleId(5155L);
        vo.setDaysSinceMating(25);
        when(dashboardService.pendingChecks(any())).thenReturn(new PageVO<>(List.of(vo), 1, 1, 20));

        mvc.perform(get("/api/v1/breeding/pending-checks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].animal_id").value(4155))
                .andExpect(jsonPath("$.data.list[0].cycle_id").value(5155))
                .andExpect(jsonPath("$.data.list[0].days_since_mating").value(25));
    }

    @Test
    void b22FarrowDue() throws Exception {
        FarrowDueItemVO vo = new FarrowDueItemVO();
        vo.setGroup("TODAY");
        vo.setGroupText("今天");
        vo.setAnimalId(4131L);
        vo.setDaysToDue(0);
        vo.setCanRegister(true);
        when(dashboardService.farrowDue(any())).thenReturn(new PageVO<>(List.of(vo), 1, 1, 20)
                .withTabCounts(java.util.Map.of("TODAY", 1, "WITHIN_7", 0, "LATER", 0)));

        mvc.perform(get("/api/v1/breeding/farrow-due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].group").value("TODAY"))
                .andExpect(jsonPath("$.data.list[0].days_to_due").value(0))
                .andExpect(jsonPath("$.data.list[0].can_register").value(true))
                .andExpect(jsonPath("$.data.tab_counts.TODAY").value(1));
    }

    @Test
    void b23HatchDue() throws Exception {
        HatchDueItemVO vo = new HatchDueItemVO();
        vo.setGroup("TODAY");
        vo.setIncubationId(6001L);
        vo.setDaysToHatch(0);
        vo.setFlockText("1 号种鸡舍 · ZM2603-015");
        when(dashboardService.hatchDue(any())).thenReturn(new PageVO<>(List.of(vo), 1, 1, 20)
                .withTabCounts(java.util.Map.of("TODAY", 1, "WITHIN_7", 0, "LATER", 0)));

        mvc.perform(get("/api/v1/breeding/hatch-due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].incubation_id").value(6001))
                .andExpect(jsonPath("$.data.list[0].days_to_hatch").value(0))
                .andExpect(jsonPath("$.data.list[0].flock_text").value("1 号种鸡舍 · ZM2603-015"))
                .andExpect(jsonPath("$.data.tab_counts.TODAY").value(1));
    }

    @Test
    void b32Submission() throws Exception {
        SubmissionVO vo = new SubmissionVO();
        vo.setSaved(false);
        vo.setType("MATING");
        when(dashboardService.submission("req-1", "MATING")).thenReturn(vo);

        mvc.perform(get("/api/v1/breeding/submissions/req-1").param("type", "MATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false))
                .andExpect(jsonPath("$.data.type").value("MATING"));
    }

    // ===== 猪的登记与修改（9 个映射） =====

    @Test
    void b12CreateMating() throws Exception {
        RegisterResultVO result = registerResult("MATING", 71002L);
        result.setAnimalId(4161L);
        result.setAnimalStatus("MATED");
        result.setIsFirst(true);
        result.setExpectedDueDate("2027-02-10");
        when(recordService.createMating(any(), anyString())).thenReturn(result);

        mvc.perform(post("/api/v1/matings").header("Idempotency-Key", "mating-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animal_id":4161,"animal_version":3,"mating_date":"2026-10-19",
                                 "mating_method":"ARTIFICIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("MATING"))
                .andExpect(jsonPath("$.data.record_id").value(71002))
                .andExpect(jsonPath("$.data.is_first").value(true))
                .andExpect(jsonPath("$.data.animal_status").value("MATED"))
                .andExpect(jsonPath("$.data.expected_due_date").value("2027-02-10"));
    }

    @Test
    void b13UpdateMating() throws Exception {
        BreedingRecordVO vo = new BreedingRecordVO();
        vo.setRecordType("MATING");
        vo.setRecordId(71002L);
        vo.setVersion(2);
        when(recordService.updateMating(eq(71002L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/matings/71002").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mating_date":"2026-06-27","mating_method":"ARTIFICIAL","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("MATING"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void b15CreateCheck() throws Exception {
        RegisterResultVO result = registerResult("PREG_CHECK", 71001L);
        result.setAnimalStatus("PREGNANT");
        when(recordService.createCheck(any(), anyString())).thenReturn(result);

        mvc.perform(post("/api/v1/pregnancy-checks").header("Idempotency-Key", "check-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animal_id":4155,"animal_version":4,"cycle_id":5155,
                                 "check_date":"2026-10-19","result":"POSITIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("PREG_CHECK"))
                .andExpect(jsonPath("$.data.animal_status").value("PREGNANT"));
    }

    @Test
    void b16UpdateCheck() throws Exception {
        BreedingRecordVO vo = new BreedingRecordVO();
        vo.setRecordType("PREG_CHECK");
        vo.setRecordId(71001L);
        vo.setCheckResult("POSITIVE");
        vo.setCheckResultText("阳性");
        when(recordService.updateCheck(eq(71001L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/pregnancy-checks/71001").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"check_date":"2026-07-22","result":"POSITIVE","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.check_result").value("POSITIVE"))
                .andExpect(jsonPath("$.data.check_result_text").value("阳性"));
    }

    @Test
    void b17CreateFarrowing() throws Exception {
        RegisterResultVO result = registerResult("FARROWING", 8118L);
        result.setAnimalStatus("LACTATING");
        result.setTodoCompleted(true);
        when(recordService.createFarrowing(any(), anyString())).thenReturn(result);

        mvc.perform(post("/api/v1/farrowings").header("Idempotency-Key", "farrow-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animal_id":4131,"animal_version":8,"farrow_date":"2026-10-19",
                                 "total_born":13,"live_born":12,"stillborn":1,"transfer_to_batch":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("FARROWING"))
                .andExpect(jsonPath("$.data.animal_status").value("LACTATING"))
                .andExpect(jsonPath("$.data.todo_completed").value(true));
    }

    @Test
    void b19FarrowingDetail() throws Exception {
        FarrowingDetailVO vo = new FarrowingDetailVO();
        vo.setFarrowingId(8161L);
        vo.setEarTag("0161");
        vo.setTotalBorn(12);
        vo.setLiveBorn(11);
        vo.setTransferToBatch(true);
        when(recordService.farrowing(8161L)).thenReturn(vo);

        mvc.perform(get("/api/v1/farrowings/8161"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farrowing_id").value(8161))
                .andExpect(jsonPath("$.data.ear_tag").value("0161"))
                .andExpect(jsonPath("$.data.live_born").value(11))
                .andExpect(jsonPath("$.data.transfer_to_batch").value(true));
    }

    @Test
    void b18UpdateFarrowing() throws Exception {
        BreedingRecordVO vo = new BreedingRecordVO();
        vo.setRecordType("FARROWING");
        vo.setRecordId(8118L);
        vo.setLiveBorn(10);
        when(recordService.updateFarrowing(eq(8118L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/farrowings/8118").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"farrow_date":"2026-10-08","total_born":12,"live_born":10,"stillborn":2,"version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("FARROWING"))
                .andExpect(jsonPath("$.data.live_born").value(10));
    }

    @Test
    void b20CreateWeaning() throws Exception {
        RegisterResultVO result = registerResult("WEANING", 71155L);
        result.setAnimalStatus("EMPTY");
        when(recordService.createWeaning(any(), anyString())).thenReturn(result);

        mvc.perform(post("/api/v1/weanings").header("Idempotency-Key", "wean-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animal_id":4109,"animal_version":7,"wean_date":"2026-10-19","weaned_count":12}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("WEANING"))
                .andExpect(jsonPath("$.data.animal_status").value("EMPTY"));
    }

    @Test
    void b21UpdateWeaning() throws Exception {
        BreedingRecordVO vo = new BreedingRecordVO();
        vo.setRecordType("WEANING");
        vo.setRecordId(71155L);
        vo.setWeanedCount(10);
        when(recordService.updateWeaning(eq(71155L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/weanings/71155").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"wean_date":"2026-10-12","weaned_count":10,"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("WEANING"))
                .andExpect(jsonPath("$.data.weaned_count").value(10));
    }

    // ===== 孵化（5 个映射） =====

    @Test
    void b26CreateIncubation() throws Exception {
        RegisterResultVO result = registerResult("INCUBATION", 6003L);
        result.setIncubationId(6003L);
        result.setExpectedHatchDate("2026-11-09");
        when(incubationService.create(any(), anyString())).thenReturn(result);

        mvc.perform(post("/api/v1/incubations").header("Idempotency-Key", "incubation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"breeding_batch_id":3212,"set_date":"2026-10-19","set_count":9200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("INCUBATION"))
                .andExpect(jsonPath("$.data.incubation_id").value(6003))
                .andExpect(jsonPath("$.data.expected_hatch_date").value("2026-11-09"));
    }

    @Test
    void b28IncubationDetail() throws Exception {
        IncubationDetailVO vo = new IncubationDetailVO();
        vo.setIncubationId(6001L);
        vo.setStatus("CANDLED");
        vo.setCurrentStep(3);
        vo.setSetCount(9600);
        vo.setFertileCount(9120);
        when(incubationService.detail(6001L)).thenReturn(vo);

        mvc.perform(get("/api/v1/incubations/6001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incubation_id").value(6001))
                .andExpect(jsonPath("$.data.status").value("CANDLED"))
                .andExpect(jsonPath("$.data.current_step").value(3))
                .andExpect(jsonPath("$.data.fertile_count").value(9120));
    }

    @Test
    void b29UpdateIncubation() throws Exception {
        IncubationDetailVO vo = new IncubationDetailVO();
        vo.setIncubationId(6001L);
        vo.setVersion(3);
        when(incubationService.update(eq(6001L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/incubations/6001").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"set_count":9600,"version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incubation_id").value(6001))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void b30Candling() throws Exception {
        IncubationDetailVO vo = new IncubationDetailVO();
        vo.setIncubationId(6003L);
        vo.setStatus("CANDLED");
        vo.setFertileCount(8740);
        when(incubationService.candling(eq(6003L), any(), anyString())).thenReturn(vo);

        mvc.perform(post("/api/v1/incubations/6003/candling").header("Idempotency-Key", "candle-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"candle_date":"2026-10-19","fertile_count":8740,"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANDLED"))
                .andExpect(jsonPath("$.data.fertile_count").value(8740));
    }

    @Test
    void b31Hatch() throws Exception {
        RegisterResultVO result = registerResult("INCUBATION", 6001L);
        result.setIncubationId(6001L);
        result.setTodoCompleted(true);
        when(incubationService.hatch(eq(6001L), any(), anyString())).thenReturn(result);

        mvc.perform(post("/api/v1/incubations/6001/hatch").header("Idempotency-Key", "hatch-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hatch_date":"2026-10-19","hatched_count":8650,"healthy_count":8480,
                                 "transfer_to_batch":false,"version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type").value("INCUBATION"))
                .andExpect(jsonPath("$.data.todo_completed").value(true));
    }

    // ===== 错误码 =====

    @Test
    void stateChangedReturns40903() throws Exception {
        when(recordService.createMating(any(), anyString())).thenThrow(
                new BizException(ErrorCode.STATE_CHANGED, "0161 的状态已变化，请刷新后再登记"));

        mvc.perform(post("/api/v1/matings").header("Idempotency-Key", "mating-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animal_id":4161,"animal_version":3,"mating_date":"2026-10-19",
                                 "mating_method":"ARTIFICIAL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value("0161 的状态已变化，请刷新后再登记"));
    }

    @Test
    void capacityConfirmReturns42206WithData() throws Exception {
        CapacityConfirmVO data = new CapacityConfirmVO();
        data.setNeedConfirm(true);
        data.setPenId(2007L);
        data.setPenName("产房");
        data.setUnit("头");
        data.setCapacity(40);
        data.setCurrentCount(0);
        data.setAfterCount(45);
        data.setOverCount(5);
        when(recordService.createFarrowing(any(), anyString())).thenThrow(
                new BizException(ErrorCode.CAPACITY_CONFIRM,
                        "入栏后超过栏舍容量。产房容量 40 头，现有 0 头，入栏后共 45 头，超出 5 头。", data));

        mvc.perform(post("/api/v1/farrowings").header("Idempotency-Key", "farrow-over")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animal_id":4131,"animal_version":8,"farrow_date":"2026-10-19",
                                 "total_born":45,"live_born":45,"stillborn":0}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42206))
                .andExpect(jsonPath("$.data.need_confirm").value(true))
                .andExpect(jsonPath("$.data.after_count").value(45))
                .andExpect(jsonPath("$.data.over_count").value(5));
    }

    private static RegisterResultVO registerResult(String recordType, long recordId) {
        RegisterResultVO result = new RegisterResultVO();
        result.setRecordType(recordType);
        result.setRecordId(recordId);
        return result;
    }
}
