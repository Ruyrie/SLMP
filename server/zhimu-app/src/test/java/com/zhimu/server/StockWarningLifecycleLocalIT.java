package com.zhimu.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.biz.account.support.Secrets;
import org.junit.jupiter.api.Test;
import com.zhimu.server.biz.stock.service.StockInboundService;
import com.zhimu.server.biz.stock.request.FeedInboundRequest;
import com.zhimu.server.biz.stock.request.DrugInboundRequest;
import com.zhimu.server.biz.stock.request.DrugScrapRequest;
import com.zhimu.server.biz.stock.service.StockLossService;
import com.zhimu.server.biz.stock.vo.StockLossVO;
import com.zhimu.server.biz.common.api.ChangeRequestAdminService;
import com.zhimu.server.biz.common.service.impl.ChangeRequestApplicationService;
import com.zhimu.server.biz.common.request.ChangeRequestSubmitRequest;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.biz.today.service.HomeService;
import com.zhimu.server.biz.today.service.TodoService;
import com.zhimu.server.biz.today.query.TodayTodoQuery;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in integration evidence, never part of ordinary tests. Uses an isolated local schema,
 * Redis DB 12 and new fixture farms. Inbound calls commit their actual transactions;
 * assertions check persisted recovery before requesting home and warning lists.
 * Run only through the accompanying local runner, which creates/verifies the schema marker.
 */
@EnabledIfEnvironmentVariable(named = "ZMT_RUN_STOCK_WARNING_IT", matches = "LOCAL_FIXTURES_ONLY")
@SpringBootTest(classes = {ZhimuServerApplication.class, StockWarningLifecycleLocalIT.Isolation.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class StockWarningLifecycleLocalIT {
    private static final String DATABASE = "zhimu_stock_warning_qa_20261027";
    private static final String MARKER = "stock-warning-local-fixtures-20261027";
    private static final SecureRandom RANDOM = new SecureRandom();

    @DynamicPropertySource
    static void localOnly(DynamicPropertyRegistry properties) {
        if (!"LOCAL_FIXTURES_ONLY".equals(System.getenv("ZMT_RUN_STOCK_WARNING_IT"))) {
            throw new IllegalStateException("Explicit local fixture opt-in is required");
        }
        // Guard before Spring creates Flyway/data-access beans, not merely after migration.
        try (var connection = DriverManager.getConnection(jdbcUrl(), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT marker_value FROM qa_fixture_marker WHERE marker_key='purpose'");
             var rows = statement.executeQuery()) {
            if (!rows.next() || !MARKER.equals(rows.getString(1))) throw new IllegalStateException("Wrong fixture database marker");
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("Local fixture database marker is unavailable; use the guarded runner");
        }
        properties.add("spring.datasource.url", StockWarningLifecycleLocalIT::jdbcUrl);
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> "6379");
        properties.add("spring.data.redis.database", () -> "12");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.flyway.clean-disabled", () -> "true");
        // The runner creates one safety marker table before Flyway's first migration.
        properties.add("spring.flyway.baseline-on-migrate", () -> "true");
        properties.add("spring.flyway.baseline-version", () -> "0");
        properties.add("zhimu.integration.minio.endpoint", () -> "http://127.0.0.1:9000");
        properties.add("zhimu.integration.minio.public-endpoint", () -> "http://127.0.0.1:9000");
        properties.add("zhimu.integration.sms.mode", () -> "mock");
        properties.add("zhimu.integration.push.app-key", () -> "");
        properties.add("zhimu.integration.push.master-secret", () -> "");
        properties.add("zhimu.clock.fixed-date", () -> "");
        properties.add("zhimu.demo.shift.enabled", () -> "false");
        properties.add("logging.level.com.zhimu.server", () -> "INFO");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Isolation {
        @Bean static BeanFactoryPostProcessor disableBackgroundWorkForThisTest() {
            return factory -> {
                BeanDefinitionRegistry definitions = (BeanDefinitionRegistry) factory;
                for (String name : List.of("org.springframework.context.annotation.internalScheduledAnnotationProcessor",
                        "accountCancelledListener", "storageBucketInitializer")) {
                    if (definitions.containsBeanDefinition(name)) definitions.removeBeanDefinition(name);
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired StockInboundService inbound;
    @Autowired StockLossService losses;
    @Autowired com.zhimu.server.biz.stock.service.StockOutboundService outbounds;
    @Autowired com.zhimu.server.biz.stock.service.StockReturnRefundService refunds;
    @Autowired ChangeRequestApplicationService changes;
    @Autowired ChangeRequestAdminService reviews;
    @Autowired HomeService homes;
    @Autowired TodoService todos;
    private final String runId = UUID.randomUUID().toString();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> farmIds = new ArrayList<>();
    private LocalDateTime now;
    private long category;

    @Test
    void returnRefundCommitsExactlyOneIncomeAndCorrectionNeverMovesStock() throws Exception {
        now = BeijingTime.now();
        category = insert("t_category", row("name", "退款验收" + runId.substring(0, 6), "species", "PIG",
                "count_unit", "HEAD", "sort_no", 9999, "status", "ENABLED", "created_at", now, "updated_at", now));
        long uid = user("退款本地验收", null);
        long farm = farm(uid, "退款隔离场", "APPROVED", null, false);
        login(uid);
        var scope = new FarmContext.FarmMember(farm, "退款隔离场", uid, 1L,
                com.zhimu.server.common.enums.FarmRole.OWNER, Set.of(), com.zhimu.server.common.enums.FarmStatus.NORMAL);
        FarmContext.set(scope);
        var checks = new ArrayList<String>();
        try {
            long catalog = insert("t_drug_catalog", row("drug_category", "VACCINE", "name", "退款疫苗" + runId.substring(0, 6),
                    "dosage_form", "注射剂", "spec", "10头份/瓶", "measure_unit", "DOSE", "pack_unit", "瓶", "pack_size", 10,
                    "status", "ENABLED", "created_at", now, "updated_at", now));
            long drug = insert("t_drug", row("farm_id", farm, "category", "VACCINE", "catalog_id", catalog,
                    "name", "退款验收疫苗", "unit", "DOSE", "pack_unit", "瓶", "pack_size", 10, "created_at", now, "updated_at", now));
            DrugInboundRequest purchase = new DrugInboundRequest();
            purchase.setDrugId(drug); purchase.setLotNo("REFUND-QA"); purchase.setInputQty(new BigDecimal("20"));
            purchase.setInputUnit("MEASURE"); purchase.setUnitPrice(new BigDecimal("5"));
            purchase.setInboundDate(now.toLocalDate().minusDays(3)); purchase.setExpireDate(now.toLocalDate().plusDays(90));
            purchase.setSyncFinance(false);
            inbound.createDrugInbound(purchase, UUID.randomUUID().toString());
            long lot = jdbc.queryForObject("SELECT id FROM t_drug_lot WHERE drug_id=? AND farm_id=?", Long.class, drug, farm);
            var returned = new com.zhimu.server.biz.stock.request.DrugOutboundRequest();
            returned.setOutType("RETURN"); returned.setDrugId(drug); returned.setDrugLotId(lot);
            returned.setInputQty(new BigDecimal("5")); returned.setInputUnit("MEASURE");
            returned.setReturnTo("QA supplier"); returned.setReturnReason("QUALITY"); returned.setOutDate(now.toLocalDate().minusDays(2));
            returned.setStockVersion(jdbc.queryForObject("SELECT version FROM t_drug_lot WHERE id=?", Integer.class, lot));
            var outbound = outbounds.createDrugOutbound(returned, UUID.randomUUID().toString());
            long id = outbound.getOutboundId();
            assertThat(outbound.getAmount()).isEqualByComparingTo("25");
            assertThat(count("SELECT COUNT(*) FROM t_finance_record WHERE farm_id=?", farm)).isZero();
            String stockBefore = refundStockSnapshot(farm, lot);
            assertThatThrownBy(() -> refunds.register(id, refundRequest("0", outbound.getVersion()), UUID.randomUUID().toString()))
                    .isInstanceOf(BizException.class);
            assertThat(count("SELECT COUNT(*) FROM t_finance_record WHERE farm_id=?", farm)).isZero();
            checks.add("return_only_moves_inventory_and_invalid_refund_leaves_no_income");

            var start = new java.util.concurrent.CountDownLatch(1);
            var winner = new java.util.concurrent.atomic.AtomicReference<String>();
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var futures = new ArrayList<java.util.concurrent.Future<String>>();
                for (int i = 0; i < 2; i++) futures.add(pool.submit(() -> {
                    login(uid); FarmContext.set(scope);
                    String key = UUID.randomUUID().toString();
                    try {
                        start.await();
                        refunds.register(id, refundRequest("22.50", outbound.getVersion()), key);
                        winner.set(key); return "REGISTERED";
                    } catch (BizException expectedDuplicate) { return "REJECTED"; }
                    finally { clearContexts(); }
                }));
                start.countDown();
                assertThat(List.of(futures.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                        futures.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder("REGISTERED", "REJECTED");
            } finally { pool.shutdownNow(); }
            refunds.register(id, refundRequest("22.50", outbound.getVersion()), winner.get());
            var paid = outbounds.get(id);
            long finance = paid.getFinanceRecordId();
            assertThat(paid.getRefundAmount()).isEqualByComparingTo("22.50");
            assertThat(count("SELECT COUNT(*) FROM t_finance_record WHERE farm_id=? AND source_type='DRUG_RETURN_REFUND' AND source_id=?", farm, id)).isEqualTo(1);
            assertThat(text("SELECT record_type FROM t_finance_record WHERE id=?", finance)).isEqualTo("INCOME");
            assertThat(jdbc.queryForObject("SELECT amount FROM t_finance_record WHERE id=?", BigDecimal.class, finance)).isEqualByComparingTo("22.50");
            assertThat(refundStockSnapshot(farm, lot)).isEqualTo(stockBefore);
            checks.add("concurrent_different_keys_and_same_key_replay_produce_one_actual_amount_income");

            String registeredAt = text("SELECT CAST(refund_registered_at AS CHAR) FROM t_stock_outbound WHERE id=?", id);
            var correction = refundRequest("21.25", paid.getVersion());
            correction.setRemark("Correction QA");
            String correctionKey = UUID.randomUUID().toString();
            refunds.correct(id, correction, correctionKey);
            refunds.correct(id, correction, correctionKey);
            var corrected = outbounds.get(id);
            assertThat(corrected.getFinanceRecordId()).isEqualTo(finance);
            assertThat(jdbc.queryForObject("SELECT amount FROM t_finance_record WHERE id=?", BigDecimal.class, finance)).isEqualByComparingTo("21.25");
            assertThat(text("SELECT CAST(refund_registered_at AS CHAR) FROM t_stock_outbound WHERE id=?", id)).isEqualTo(registeredAt);
            assertThat(count("SELECT COUNT(*) FROM t_finance_record WHERE farm_id=?", farm)).isEqualTo(1);
            assertThat(refundStockSnapshot(farm, lot)).isEqualTo(stockBefore);
            checks.add("same_day_correction_updates_same_income_and_keeps_registration_time_and_stock");

            var voidRequest = new com.zhimu.server.biz.stock.request.VoidRequest();
            voidRequest.setVersion(corrected.getVersion());
            assertThatThrownBy(() -> outbounds.voidOutbound(id, voidRequest)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> changes.form("OUTBOUND", id)).isInstanceOf(BizException.class);
            FarmContext.set(new FarmContext.FarmMember(farm, "退款隔离场", uid, 1L,
                    com.zhimu.server.common.enums.FarmRole.STAFF, Set.of(), com.zhimu.server.common.enums.FarmStatus.NORMAL));
            assertThatThrownBy(() -> refunds.correct(id, refundRequest("20", corrected.getVersion()), UUID.randomUUID().toString()))
                    .isInstanceOf(BizException.class);
            FarmContext.set(scope);
            long otherFarm = farm(uid, "退款对照场", "APPROVED", null, false);
            FarmContext.set(new FarmContext.FarmMember(otherFarm, "退款对照场", uid, 2L,
                    com.zhimu.server.common.enums.FarmRole.OWNER, Set.of(), com.zhimu.server.common.enums.FarmStatus.NORMAL));
            assertThatThrownBy(() -> refunds.correct(id, refundRequest("20", corrected.getVersion()), UUID.randomUUID().toString()))
                    .isInstanceOf(BizException.class);
            FarmContext.set(scope);
            assertThat(refundStockSnapshot(farm, lot)).isEqualTo(stockBefore);
            jdbc.update("UPDATE t_stock_outbound SET refund_registered_at=? WHERE id=?", now.minusDays(1), id);
            assertThatThrownBy(() -> refunds.correct(id, refundRequest("20", corrected.getVersion()), UUID.randomUUID().toString()))
                    .isInstanceOf(BizException.class);
            assertThat(jdbc.queryForObject("SELECT amount FROM t_finance_record WHERE id=?", BigDecimal.class, finance)).isEqualByComparingTo("21.25");
            checks.add("registered_return_void_and_amendment_staff_cross_farm_and_late_correction_blocked");
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                    Path.of(System.getenv("ZMT_STOCK_WARNING_IT_REPORT")).resolveSibling("refund-evidence.json").toFile(),
                    Map.of("status", "PASS", "database", DATABASE, "farm_id", farm, "outbound_id", id, "finance_id", finance, "checks", checks));
        } finally { clearContexts(); }
    }

    private com.zhimu.server.biz.stock.request.StockReturnRefundRequest refundRequest(String amount, int version) {
        var request = new com.zhimu.server.biz.stock.request.StockReturnRefundRequest();
        request.setAmount(new BigDecimal(amount)); request.setRefundDate(now.toLocalDate()); request.setVersion(version);
        return request;
    }

    private String refundStockSnapshot(long farm, long lot) {
        return text("SELECT JSON_OBJECT('qty',stock_qty,'amount',stock_amount,'version',version,"
                + "'flows',(SELECT COUNT(*) FROM t_stock_flow WHERE farm_id=?)) FROM t_drug_lot WHERE id=?", farm, lot);
    }

    @Test
    void replenishmentCommitsWarningRecoveryAndReadRepairAgainstRealMysql() throws Exception {
        now = BeijingTime.now();
        category = insert("t_category", row("name", "库存验收"+runId.substring(0,6), "species", "PIG",
                "count_unit", "HEAD", "sort_no", 9999, "status", "ENABLED", "created_at", now,"updated_at",now));
        long uid = user("库存预警验收", null);
        long farm = farm(uid,"库存预警隔离场","APPROVED",null,false);
        login(uid);
        FarmContext.set(new FarmContext.FarmMember(farm,"库存预警隔离场",uid,1L,
                com.zhimu.server.common.enums.FarmRole.OWNER,Set.of(),com.zhimu.server.common.enums.FarmStatus.NORMAL));
        List<String> checks = new ArrayList<>();
        try {
            var primaryScope = FarmContext.require();
            long secondFarm = farm(uid,"单号对照场","APPROVED",null,false);
            long secondFeed = insert("t_feed", row("farm_id",secondFarm,"name","对照饲料","feed_type_code","COMPLETE",
                    "created_at",now,"updated_at",now));
            FarmContext.set(new FarmContext.FarmMember(secondFarm,"单号对照场",uid,2L,
                    com.zhimu.server.common.enums.FarmRole.OWNER,Set.of(),com.zhimu.server.common.enums.FarmStatus.NORMAL));
            feedInbound(secondFeed,1);
            FarmContext.set(primaryScope);
            checks.add("inbound_numbers_are_unique_across_farms");
            long feed = insert("t_feed", row("farm_id",farm,"name","验收饲料","feed_type_code","COMPLETE",
                    "min_stock",10,"stock_qty",5,"stock_amount",5,"avg_price",1,"daily_use_qty",2,
                    "warn_days",7,"remaining_basis","USAGE_7D","created_at",now,"updated_at",now));
            assertWarnings(uid,1);
            long warning = jdbc.queryForObject("SELECT id FROM t_todo WHERE farm_id=? AND todo_type='FEED_LOW_STOCK' AND status='PENDING'",Long.class,farm);
            jdbc.update("UPDATE t_todo SET due_date=?,sort_time=? WHERE id=?",now.toLocalDate().minusDays(2),now.minusDays(2),warning);
            assertWarnings(uid,1);
            checks.add("old_unresolved_warning_visible_in_warning_collection");
            feedInbound(feed,3);
            assertThat(text("SELECT status FROM t_todo WHERE id=?",warning)).isEqualTo("PENDING");
            assertWarnings(uid,1);
            checks.add("partial_replenishment_below_min_keeps_warning");
            feedInbound(feed,2);
            assertWarnings(uid,1);
            assertThat(text("SELECT low_stock_reason FROM t_feed WHERE id=?",feed)).isEqualTo("BELOW_DAYS");
            checks.add("minimum_met_but_days_insufficient_keeps_warning");
            feedInbound(feed,4);
            assertThat(text("SELECT status FROM t_todo WHERE id=?",warning)).isEqualTo("DONE");
            assertThat(text("SELECT complete_way FROM t_todo WHERE id=?",warning)).isEqualTo("AUTO");
            assertWarnings(uid,0);
            assertWarnings(uid,0);
            checks.add("recovery_committed_before_read_and_home_collection_both_zero");
            jdbc.update("UPDATE t_todo SET status='PENDING',completed_at=NULL,complete_way=NULL WHERE id=?",warning);
            assertWarnings(uid,0);
            checks.add("stale_warning_reconciled_on_read_history_retained");

            long catalog=insert("t_drug_catalog",row("drug_category","VET_DRUG","name","验收药品"+runId.substring(0,6),
                    "dosage_form","粉剂","spec","1克/袋","measure_unit","G","pack_unit","袋","pack_size",1,
                    "status","ENABLED","created_at",now,"updated_at",now));
            long drug=insert("t_drug",row("farm_id",farm,"category","VET_DRUG","catalog_id",catalog,"name","验收药品",
                    "unit","G","pack_unit","袋","pack_size",1,"min_stock",5,"created_at",now,"updated_at",now));
            insert("t_drug_lot",row("farm_id",farm,"drug_id",drug,"catalog_id",catalog,"category","VET_DRUG","first_inbound_date",now.toLocalDate().minusDays(10),"lot_no","EXPIRED-QA",
                    "expire_date",now.toLocalDate().minusDays(1),"stock_qty",100,"stock_amount",100,"avg_price",1,"created_at",now,"updated_at",now));
            insert("t_drug_lot",row("farm_id",farm,"drug_id",drug,"catalog_id",catalog,"category","VET_DRUG","first_inbound_date",now.toLocalDate().minusDays(10),"lot_no","RECALLED-QA",
                    "expire_date",now.toLocalDate().plusDays(90),"recalled_at",now,"stock_qty",100,"stock_amount",100,"avg_price",1,"created_at",now,"updated_at",now));
            assertWarnings(uid,1);
            drugInbound(drug,2);
            assertWarnings(uid,1);
            checks.add("expired_recalled_lots_excluded_partial_usable_stock_keeps_warning");
            drugInbound(drug,3);
            assertThat(count("SELECT COUNT(*) FROM t_todo WHERE farm_id=? AND status='PENDING'",farm)).isZero();
            assertWarnings(uid,0);
            checks.add("drug_usable_stock_recovery_commits_and_removes_warning");
            verifyScrapAndChangeApproval(farm, checks);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(System.getenv("ZMT_STOCK_WARNING_IT_REPORT")).toFile(),
                    Map.of("status","PASS","database",DATABASE,"farm_id",farm,"feed_id",feed,"drug_id",drug,"checks",checks));
        } finally {clearContexts();}
    }

    private void verifyScrapAndChangeApproval(long farm, List<String> checks) {
        long catalog = insert("t_drug_catalog", row("name", "报废验收" + runId.substring(0, 6),
                "drug_category", "VET_DRUG", "measure_unit", "G", "pack_unit", "袋", "pack_size", 500,
                "dosage_form", "粉剂", "spec", "500 克/袋", "status", "ENABLED",
                "created_at", now, "updated_at", now));
        long drug = insert("t_drug", row("farm_id", farm, "category", "VET_DRUG", "catalog_id", catalog,
                "name", "报废单位验收药品", "unit", "G", "pack_unit", "袋", "pack_size", 500,
                "created_at", now, "updated_at", now));
        long lot = scrapLot(drug, "SCRAP-PACK-QA");
        StockLossVO full = scrap(lot, "6", "PACK");
        assertThat(full.getQty().getValue()).isEqualByComparingTo("3000");
        assertThat(full.getRemaining().getValue()).isZero();
        assertThat(text("SELECT input_unit FROM t_stock_loss WHERE id=?", full.getLossId())).isEqualTo("PACK");
        assertLotCleared(lot);
        checks.add("six_pack_scrap_persists_3000_grams_and_clears_stock_and_amount");

        var form = changes.form("LOSS", full.getLossId());
        assertThat(form.getFields().get(0).getValueUnit()).isEqualTo("袋");
        assertThat(form.getFields().get(0).getMax()).isEqualByComparingTo("6");
        long requestsBefore = count("SELECT COUNT(*) FROM t_change_request WHERE farm_id=?", farm);
        assertThatThrownBy(() -> changes.submit(changeQty(full.getLossId(), "6", form.getRecordVersion())))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
        assertThat(count("SELECT COUNT(*) FROM t_change_request WHERE farm_id=?", farm)).isEqualTo(requestsBefore);
        var submitted = changes.submit(changeQty(full.getLossId(), "3", form.getRecordVersion()));
        assertThat(submitted.getStatus()).isEqualTo("PENDING");
        assertLotCleared(lot);
        reviews.approve(submitted.getId(), 900000001L, "隔离测试确认", 0);
        assertThat(text("SELECT status FROM t_change_request WHERE id=?", submitted.getId())).isEqualTo("APPROVED");
        assertThat(decimal("SELECT stock_qty FROM t_drug_lot WHERE id=?", lot)).isEqualByComparingTo("1500");
        assertThat(decimal("SELECT stock_amount FROM t_drug_lot WHERE id=?", lot)).isEqualByComparingTo("45");
        checks.add("unchanged_request_rejected_and_pending_request_does_not_change_stock");
        checks.add("approved_pack_change_returns_exact_1500_grams_and_45_yuan");

        var nextForm = changes.form("LOSS", full.getLossId());
        var next = changes.submit(changeQty(full.getLossId(), "6", nextForm.getRecordVersion()));
        reviews.approve(next.getId(), 900000001L, "隔离测试再次确认", 0);
        assertLotCleared(lot);
        checks.add("approved_full_pack_change_clears_stock_and_amount_again");

        long remainderLot = scrapLot(drug, "SCRAP-REMAINDER-QA");
        // Reproduce the quantities previously sent as grams by the broken service.
        scrap(remainderLot, "6", "MEASURE");
        scrap(remainderLot, "5.98", "MEASURE");
        StockLossVO beforeFinal = scrap(remainderLot, "5.97", "MEASURE");
        assertThat(beforeFinal.getRemaining().getValue()).isEqualByComparingTo("2982.05");
        StockLossVO cleared = scrap(remainderLot, "2982.05", "MEASURE");
        assertThat(cleared.getAmount()).isEqualByComparingTo("89.46");
        assertLotCleared(remainderLot);
        assertThatThrownBy(() -> scrap(remainderLot, "1", "MEASURE"))
                .isInstanceOf(BizException.class).hasMessageContaining("结存已为 0");
        checks.add("historical_fractional_remainder_clears_exactly_and_repeat_scrap_is_rejected");
    }

    private long scrapLot(long drugId, String lotNo) {
        DrugInboundRequest request = new DrugInboundRequest();
        request.setDrugId(drugId); request.setLotNo(lotNo); request.setInputQty(new BigDecimal("6"));
        request.setInputUnit("PACK"); request.setUnitPrice(new BigDecimal("15"));
        request.setInboundDate(BeijingTime.today().minusDays(365));
        request.setExpireDate(BeijingTime.today().minusDays(1)); request.setSyncFinance(false);
        inbound.createDrugInbound(request, UUID.randomUUID().toString());
        return jdbc.queryForObject("SELECT id FROM t_drug_lot WHERE drug_id=? AND lot_no=?", Long.class, drugId, lotNo);
    }

    private StockLossVO scrap(long lotId, String qty, String inputUnit) {
        DrugScrapRequest request = new DrugScrapRequest();
        request.setDrugLotId(lotId); request.setInputQty(new BigDecimal(qty)); request.setInputUnit(inputUnit);
        request.setReason("EXPIRED"); request.setScrapDate(BeijingTime.today());
        request.setStockVersion(jdbc.queryForObject("SELECT version FROM t_drug_lot WHERE id=?", Integer.class, lotId));
        return losses.createDrugScrap(request, UUID.randomUUID().toString());
    }

    private void assertLotCleared(long lotId) {
        assertThat(decimal("SELECT stock_qty FROM t_drug_lot WHERE id=?", lotId)).isZero();
        assertThat(decimal("SELECT stock_amount FROM t_drug_lot WHERE id=?", lotId)).isZero();
    }

    private static ChangeRequestSubmitRequest changeQty(long lossId, String qty, String version) {
        ChangeRequestSubmitRequest request = new ChangeRequestSubmitRequest();
        request.setBizType("LOSS"); request.setBizId(lossId); request.setChangeType("UPDATE");
        request.setFieldCode("input_qty"); request.setNewValue(qty); request.setRecordVersion(version);
        request.setReason("本地隔离验收报废单位换算");
        return request;
    }

    private BigDecimal decimal(String sql, Object... values) { return jdbc.queryForObject(sql, BigDecimal.class, values); }
    private void assertWarnings(long user,int count) {
        assertThat(homes.getHome(user).getOwnerMetrics().getWarningCount()).isEqualTo(count);
        TodayTodoQuery q=new TodayTodoQuery();q.setFilter("WARNING");q.setPage(1);q.setSize(20);
        assertThat(todos.today(q,user).getTotal()).isEqualTo(count);
    }
    private void feedInbound(long id,int qty) {
        FeedInboundRequest r=new FeedInboundRequest();r.setFeedId(id);r.setInputQty(BigDecimal.valueOf(qty));
        r.setInputUnit("MEASURE");r.setUnitPrice(BigDecimal.ONE);r.setSupplierName("隔离验收供应商");
        r.setInboundDate(BeijingTime.today());r.setSyncFinance(false);inbound.createFeedInbound(r,UUID.randomUUID().toString());
    }
    private void drugInbound(long id,int qty) {
        DrugInboundRequest r=new DrugInboundRequest();r.setDrugId(id);r.setLotNo("USABLE-QA");
        r.setInputQty(BigDecimal.valueOf(qty));r.setInputUnit("MEASURE");r.setUnitPrice(BigDecimal.ONE);
        r.setInboundDate(BeijingTime.today());r.setExpireDate(BeijingTime.today().plusDays(90));r.setSyncFinance(false);
        inbound.createDrugInbound(r,UUID.randomUUID().toString());
    }
    private long user(String name, LocalDateTime cancelledAt) {
        String phone;
        do { phone = "13997" + String.format("%06d", RANDOM.nextInt(1_000_000)); }
        while (count("SELECT COUNT(*) FROM t_user WHERE phone=?", phone) != 0);
        long id = insert("t_user", row("phone", phone, "name", name, "status", cancelledAt == null ? "NORMAL" : "CANCELLED",
                "register_client", "WEB", "agreement_version", "fixture", "agreed_at", now,
                "cancelled_at", cancelledAt, "created_at", now.minusDays(35), "updated_at", now));
        userIds.add(id);
        if (cancelledAt != null) {
            insert("t_account_cancel", row("user_id", id, "phone_masked", "139****0000", "phone_hash",
                    Secrets.sha256Hex("zhimu-account-cancel:" + phone),
                    "phone_release_at", cancelledAt.plusDays(30), "cleanup_status", "PENDING", "client_type", "WEB",
                    "created_at", cancelledAt, "updated_at", cancelledAt));
        }
        return id;
    }

    private long farm(long owner, String name, String audit, LocalDateTime disabledAt, boolean cancellationMarker) {
        long id = insert("t_farm", row("name", name + runId.substring(0, 4), "owner_user_id", owner,
                "main_category_id", category, "scale", 100, "contact_name", "本地验收", "contact_phone", "13997000000",
                "address", "本地隔离验收场", "province", "江苏省", "city", "南京市", "district", "建邺区",
                "adcode", "320105", "longitude", 118.73, "latitude", 32.01, "audit_status", audit,
                "disabled", disabledAt == null ? 0 : 1, "disabled_at", disabledAt,
                "delete_type", cancellationMarker ? "ACCOUNT_CANCEL" : null, "created_at", now.minusDays(200), "updated_at", now));
        farmIds.add(id);
        member(id, owner, "OWNER");
        if ("CANCELLED".equals(text("SELECT status FROM t_user WHERE id=?", owner))) {
            jdbc.update("UPDATE t_farm_member SET status='ACCOUNT_CANCELED',active_flag=NULL,left_at=? WHERE farm_id=? AND user_id=?",
                    now.minusDays(31), id, owner);
        }
        return id;
    }

    private void member(long farm, long user, String role) {
        insert("t_farm_member", row("farm_id", farm, "user_id", user, "role", role, "status", "ACTIVE", "active_flag", 1,
                "join_type", "OWNER".equals(role) ? "CREATE" : "INVITE", "joined_at", now.minusDays(200),
                "created_at", now.minusDays(200), "updated_at", now));
    }

    private long insert(String table, Map<String, Object> values) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(values.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put((String) values[i], values[i + 1]);
        return map;
    }

    private long count(String sql, Object... values) { return jdbc.queryForObject(sql, Long.class, values); }
    private String text(String sql, Object... values) { return jdbc.queryForObject(sql, String.class, values); }
    private static String jdbcUrl() { return "jdbc:mysql://127.0.0.1:3306/" + DATABASE
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"; }
    private static void login(long id) { UserContext.set(new UserContext.LoginUser(id, ClientType.WEB, 0, "stock-warning-local-it")); }
    private static void clearContexts() { UserContext.clear(); FarmContext.clear(); }
}
