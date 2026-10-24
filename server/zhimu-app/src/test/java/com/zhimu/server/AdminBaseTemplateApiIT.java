package com.zhimu.server;

import com.zhimu.server.admin.base.query.BannedDrugQuery;
import com.zhimu.server.admin.base.query.DrugCatalogQuery;
import com.zhimu.server.admin.base.query.ImmuneTemplateQuery;
import com.zhimu.server.admin.base.request.DrugCatalogSaveRequest;
import com.zhimu.server.admin.base.request.DrugStatusRequest;
import com.zhimu.server.admin.base.request.ImmuneItemSaveRequest;
import com.zhimu.server.admin.base.request.InspectItemSaveRequest;
import com.zhimu.server.admin.base.request.LotRecallRequest;
import com.zhimu.server.admin.base.service.BannedDrugAdminService;
import com.zhimu.server.admin.base.service.DrugCatalogAdminService;
import com.zhimu.server.admin.base.service.DrugLotRecallAdminService;
import com.zhimu.server.admin.base.service.ImmuneTemplateAdminService;
import com.zhimu.server.admin.base.service.InspectTemplateAdminService;
import com.zhimu.server.admin.base.vo.DrugCatalogDetailVO;
import com.zhimu.server.admin.base.vo.DrugLotRecallVO;
import com.zhimu.server.admin.base.vo.ImmuneItemVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateGroupVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateVO;
import com.zhimu.server.admin.base.vo.InspectItemVO;
import com.zhimu.server.admin.base.vo.InspectTemplateListVO;
import com.zhimu.server.admin.base.vo.InspectTemplateVO;
import com.zhimu.server.admin.base.vo.RecallFarmVO;
import com.zhimu.server.admin.base.vo.RecallPreviewVO;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础数据补口运行态冒烟：Flyway（含 V1_23_2）在测试库执行 + 目录/模板/批号停用真实 SQL。
 * 每次 clean 后重新迁移，只跑结构脚本与 V2 初始化数据。
 */
@SpringBootTest(classes = ZhimuServerApplication.class)
@ActiveProfiles("it")
class AdminBaseTemplateApiIT {

    @TestConfiguration
    static class CleanMigrateConfig {
        @Bean
        FlywayMigrationStrategy cleanMigrateStrategy() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired private ImmuneTemplateAdminService immuneService;
    @Autowired private InspectTemplateAdminService inspectService;
    @Autowired private DrugCatalogAdminService catalogService;
    @Autowired private BannedDrugAdminService bannedService;
    @Autowired private DrugLotRecallAdminService recallService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of("WA-D02:VIEW",
                "WA-D02:HANDLE", "WA-D03:VIEW", "WA-D03:HANDLE", "WA-D04:VIEW", "WA-D04:HANDLE"), 0, "t"));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void seedReadEndpointsWork() {
        List<ImmuneTemplateGroupVO> groups = immuneService.list(new ImmuneTemplateQuery());
        assertThat(groups).isNotEmpty();
        assertThat(groups.stream().flatMap(group -> group.getItems().stream())
                .anyMatch(item -> Long.valueOf(1101L).equals(item.getBreedId())
                        && Long.valueOf(21L).equals(item.getTemplateId()))).isTrue();

        ImmuneTemplateVO immune = immuneService.detail(21L);
        assertThat(immune.getItems()).hasSize(9);
        assertThat(immune.getVersion()).isEqualTo(1);

        DrugCatalogQuery query = new DrugCatalogQuery();
        query.setDrugCategory("VET_DRUG");
        PageVO<com.zhimu.server.admin.base.vo.DrugCatalogRowVO> catalogs = catalogService.list(query);
        assertThat(catalogs.getTotal()).isEqualTo(6);
        assertThat(catalogs.getTabCounts()).containsKeys("VET_DRUG", "VACCINE", "DISINFECTANT", "BANNED",
                "RECALL");
        assertThat(catalogs.getTabCounts().get("BANNED")).isEqualTo(5);

        DrugCatalogDetailVO detail = catalogService.detail(7202L);
        assertThat(detail.getUsages()).hasSize(4);
        assertThat(detail.getMeasureUnitText()).isEqualTo("克");

        BannedDrugQuery bannedQuery = new BannedDrugQuery();
        assertThat(bannedService.list(bannedQuery).getTotal()).isEqualTo(5);

        List<InspectTemplateListVO> templates = inspectService.list();
        assertThat(templates.stream().anyMatch(row -> Long.valueOf(11L).equals(row.getTemplateId()))).isTrue();
        InspectTemplateVO inspect = inspectService.detail(11L);
        assertThat(inspect.getPreviewItems()).hasSize(3);
    }

    @Test
    @Transactional
    @Rollback
    void immuneDraftAddAndDiscard() {
        ImmuneTemplateVO before = immuneService.detail(21L);
        ImmuneItemSaveRequest request = new ImmuneItemSaveRequest();
        request.setVaccineId(7001L);
        request.setDoseRound("测试免次");
        request.setScheduleType("BY_DAY_AGE");
        request.setDayAge(130);
        request.setDose(new BigDecimal("1.00"));
        request.setDoseUnit("DOSE_PER_HEAD");
        request.setVaccinateMethodCode("IM");
        request.setVersion(before.getVersion());

        ImmuneItemVO item = immuneService.addItem(21L, request);
        assertThat(item.getChangeType()).isEqualTo("ADDED");
        assertThat(item.getVaccineName()).isEqualTo("猪瘟活疫苗");

        ImmuneTemplateVO draft = immuneService.detail(21L);
        assertThat(draft.getHasDraft()).isTrue();
        assertThat(draft.getItems()).hasSize(10);
        assertThat(draft.getVersion()).isGreaterThan(before.getVersion());

        immuneService.discard(21L, draft.getVersion());
        ImmuneTemplateVO discarded = immuneService.detail(21L);
        assertThat(discarded.getHasDraft()).isFalse();
        assertThat(discarded.getItems()).hasSize(9);
    }

    @Test
    @Transactional
    @Rollback
    void inspectDraftAddAndDiscard() {
        InspectTemplateVO before = inspectService.detail(11L);
        InspectItemSaveRequest request = new InspectItemSaveRequest();
        request.setItemName("呼吸");
        request.setCheckNote("听咳嗽、看腹式呼吸");
        request.setEnabled(true);
        request.setVersion(before.getVersion());
        InspectItemSaveRequest.Option normal = new InspectItemSaveRequest.Option();
        normal.setOptionName("正常");
        normal.setIsAbnormal(false);
        InspectItemSaveRequest.Option cough = new InspectItemSaveRequest.Option();
        cough.setOptionName("咳嗽");
        cough.setIsAbnormal(true);
        request.setOptions(List.of(normal, cough));

        InspectItemVO item = inspectService.addItem(11L, request);
        assertThat(item.getChangeType()).isEqualTo("ADDED");
        assertThat(item.getOptions()).hasSize(2);

        InspectTemplateVO draft = inspectService.detail(11L);
        assertThat(draft.getHasDraft()).isTrue();
        assertThat(draft.getItems()).hasSize(4);
        assertThat(draft.getPreviewItems()).hasSize(3);

        inspectService.discard(11L, draft.getVersion());
        assertThat(inspectService.detail(11L).getHasDraft()).isFalse();
    }

    @Test
    @Transactional
    @Rollback
    void catalogCreateAndDisable() {
        DrugCatalogSaveRequest request = new DrugCatalogSaveRequest();
        request.setDrugCategory("VET_DRUG");
        request.setName("测试新兽药");
        request.setDosageForm("粉剂");
        request.setSpec("100 克/袋");
        request.setMeasureUnit("G");
        request.setPackUnit("袋");
        request.setPackSize(new BigDecimal("100"));
        request.setUsages(List.of(
                usage("PIG", true, "MIX_FEED", "100", "G_PER_T_FEED", 5, 20),
                usage("POULTRY", false, null, null, null, null, 5),
                usage("CATTLE", false, null, null, null, null, 28),
                usage("SHEEP", false, null, null, null, null, 14)));

        DrugCatalogDetailVO created = catalogService.create(request);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo("ENABLED");
        assertThat(created.getUsages()).hasSize(4);

        DrugStatusRequest status = new DrugStatusRequest();
        status.setStatus("DISABLED");
        status.setReason("测试停用");
        status.setVersion(created.getVersion());
        DrugCatalogDetailVO disabled = catalogService.changeStatus(created.getId(), status);
        assertThat(disabled.getStatus()).isEqualTo("DISABLED");
        assertThat(disabled.getDisableReason()).isEqualTo("测试停用");
    }

    @Test
    @Transactional
    @Rollback
    void recallPreviewAndMarkLot() {
        jdbc.update("INSERT INTO t_drug (id, farm_id, category, catalog_id, name, spec, unit, pack_unit, "
                        + "pack_size, version, deleted, created_at, updated_at) VALUES (8801, 1001, 'VACCINE', 7001, "
                        + "'猪瘟活疫苗', '10 头份/瓶', 'DOSE', '瓶', 10.000, 0, 0, NOW(), NOW())");
        jdbc.update("INSERT INTO t_drug_lot (id, farm_id, drug_id, catalog_id, category, lot_no, expire_date, "
                        + "first_inbound_date, inbound_count, stock_qty, stock_amount, avg_price, version, "
                        + "created_at, updated_at) VALUES (8802, 1001, 8801, 7001, 'VACCINE', '2025097', "
                        + "'2026-11-30', '2026-01-01', 1, 200.000, 0, 0, 0, NOW(), NOW())");

        RecallPreviewVO preview = recallService.preview(7001L, " 2025097 ");
        assertThat(preview.getHasStockRecord()).isTrue();
        assertThat(preview.getFarmCount()).isEqualTo(1);
        assertThat(preview.getTotalQty()).isEqualByComparingTo("200.000");
        assertThat(preview.getExpireDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 30));

        LotRecallRequest request = new LotRecallRequest();
        request.setCatalogId(7001L);
        request.setLotNo("2025097");
        request.setReason("测试停用");
        DrugLotRecallVO recall = recallService.recall(request);
        assertThat(recall.getFarmCount()).isEqualTo(1);
        assertThat(recall.getNotifiedFarmCount()).isEqualTo(1);

        Long recallId = jdbc.queryForObject("SELECT recall_id FROM t_drug_lot WHERE id = 8802", Long.class);
        assertThat(recallId).isEqualTo(recall.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_drug_lot_recall_farm WHERE recall_id = ?",
                Integer.class, recall.getId())).isEqualTo(1);

        PageVO<RecallFarmVO> farms = recallService.farms(recall.getId(), new PageQuery());
        assertThat(farms.getTotal()).isEqualTo(1);
    }

    private static DrugCatalogSaveRequest.Usage usage(String species, boolean applicable, String route,
                                                      String dose, String unit, Integer course, Integer withdrawal) {
        DrugCatalogSaveRequest.Usage usage = new DrugCatalogSaveRequest.Usage();
        usage.setSpecies(species);
        usage.setApplicable(applicable);
        usage.setAdminRoute(route);
        usage.setRecommendedDose(dose == null ? null : new BigDecimal(dose));
        usage.setDoseUnit(unit);
        usage.setCourseDays(course);
        usage.setWithdrawalDays(withdrawal);
        return usage;
    }
}
