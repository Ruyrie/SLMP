package com.zhimu.server;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.*;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.service.impl.SaleOrderServiceImpl;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.context.*;
import com.zhimu.server.common.enums.*;
import com.zhimu.server.common.util.BeijingTime;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Explicit local MySQL fixtures only; verifies real tenant SQL and concurrent inserts. */
@EnabledIfEnvironmentVariable(named = "ZMT_RUN_SALE_ORDER_IT", matches = "LOCAL_FIXTURES_ONLY")
@SpringBootTest(classes = {ZhimuServerApplication.class, SaleOrderCreateLocalIT.Isolation.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SaleOrderCreateLocalIT {
    private static final String DATABASE = "zhimu_sale_order_qa_20261027";
    private static final String MARKER = "sale-order-local-fixtures-20261027";
    private static String jdbcUrl() { return "jdbc:mysql://127.0.0.1:3306/" + DATABASE
            + "?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"; }
    @DynamicPropertySource
    static void localOnly(DynamicPropertyRegistry properties) {
        if (!"LOCAL_FIXTURES_ONLY".equals(System.getenv("ZMT_RUN_SALE_ORDER_IT"))) {
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
        properties.add("spring.datasource.url", SaleOrderCreateLocalIT::jdbcUrl);
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> "6379");
        properties.add("spring.data.redis.database", () -> "12");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("zhimu.finance.repair-missing-settlements", () -> "false");
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
    @Autowired SaleOrderMapper orders;
    @Autowired PlatformTransactionManager transactionManager;

    private void login(long farm) {
        UserContext.set(new UserContext.LoginUser(99001L, ClientType.APP_ANDROID, 0, "sale-qa"));
        FarmContext.set(new FarmContext.FarmMember(farm, "sale-qa", 99001L, 99001L,
                FarmRole.OWNER, Set.of(), FarmStatus.NORMAL));
    }

    private FinanceRequests.SaleOrderRequest request() {
        var r = new FinanceRequests.SaleOrderRequest();
        r.setBatchId(99001L); r.setSaleDate(BeijingTime.today().toString()); r.setQuantity(150);
        r.setTotalWeight(new BigDecimal("1000")); r.setPricingMethod("BY_WEIGHT");
        r.setUnitPrice(new BigDecimal("15.60")); r.setBuyerName("isolated-sale-qa");
        return r;
    }

    private SaleOrderServiceImpl service(MarketPriceService prices, BatchSalePort port) {
        FinanceSupport support = mock(FinanceSupport.class);
        when(support.farmId()).thenAnswer(call -> FarmContext.requireFarmId());
        when(support.requireBatch(anyLong(), anyLong())).thenAnswer(call -> new BatchInfo(
                call.getArgument(1), 99001L, "QA", 1L, 1L, BeijingTime.today().minusDays(10),
                200, "ACTIVE", List.of(99001L), 1, 200, 0, 0, 99001L));
        return new SaleOrderServiceImpl(orders, mock(SaleReceiptMapper.class), mock(FinanceRecordMapper.class),
                mock(FinanceCategoryMapper.class), mock(BatchSettlementMapper.class), mock(BatchSettlementSupplementMapper.class),
                mock(SaleAdviceMapper.class), port, mock(BatchQueryService.class), prices,
                mock(AccountQueryService.class), support, mock(TodoSourceWriteService.class), mock(com.zhimu.server.biz.finance.service.BatchSettlementService.class));
    }

    @Test void createsAcrossFarmsAndRetriesConcurrentUniqueNumbersInRealTransactions() throws Exception {
        // Fresh IDs per run; cleanup only this test's rows, even in the isolated database.
        long base = 800000000L + (System.nanoTime() & 0xfffffffL);
        var tx = new TransactionTemplate(transactionManager);
        BatchSalePort port = mock(BatchSalePort.class);
        when(port.saleOut(anyLong(), anyLong(), any(), any(), anyLong())).thenReturn(50);
        try {
            login(base);
            var first = tx.execute(status -> service(mock(MarketPriceService.class), port).create(request(), UUID.randomUUID().toString()));
            assertThat(first.getId()).isNotNull();
            String firstNumber = jdbc.queryForObject("SELECT sale_no FROM t_sale_order WHERE id=?", String.class, first.getId());
            String prefix = firstNumber.substring(0, 8);
            jdbc.update("UPDATE t_sale_order SET sale_no=? WHERE id=?", prefix + "1000", first.getId());
            FarmContext.clear(); UserContext.clear();

            CyclicBarrier readBarrier = new CyclicBarrier(2);
            MarketPriceService prices = mock(MarketPriceService.class);
            when(prices.latest(anyLong(), anyLong(), anyString(), any())).thenAnswer(call -> {
                readBarrier.await(20, TimeUnit.SECONDS); return null;
            });
            var concurrentService = service(prices, port);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Long>> futures = new ArrayList<>();
                for (long farm : List.of(base + 1, base + 2)) futures.add(executor.submit(() -> {
                    login(farm);
                    try { return tx.execute(status -> concurrentService.create(request(), UUID.randomUUID().toString()).getId()); }
                    finally { FarmContext.clear(); UserContext.clear(); }
                }));
                for (var future : futures) assertThat(future.get(45, TimeUnit.SECONDS)).isNotNull();
            }
            List<String> numbers = jdbc.queryForList("SELECT sale_no FROM t_sale_order WHERE farm_id IN (?,?) ORDER BY sale_no",
                    String.class, base + 1, base + 2);
            assertThat(numbers).containsExactly(prefix + "1001", prefix + "1002");
            verify(port, times(3)).saleOut(anyLong(), anyLong(), any(), any(), anyLong());
            login(base + 1);
            var visible = orders.selectList(Wrappers.<SaleOrder>lambdaQuery().likeRight(SaleOrder::getSaleNo, prefix));
            assertThat(visible).hasSize(1);
            assertThat(visible.getFirst().getFarmId()).isEqualTo(base + 1);
            Files.writeString(Path.of(System.getenv("ZMT_SALE_ORDER_IT_REPORT")),
                    "{\"status\":\"PASS\",\"checks\":[\"cross_farm_global_number\",\"sequence_after_999\",\"concurrent_unique_retry_commits\",\"ordinary_reads_stay_farm_scoped\",\"stock_deducted_once_per_order\"]}");
        } finally {
            FarmContext.clear(); UserContext.clear();
            jdbc.update("DELETE FROM t_sale_order WHERE farm_id IN (?,?,?) AND buyer_name='isolated-sale-qa'", base, base+1, base+2);
        }
    }
}
