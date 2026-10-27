package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.service.BatchSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 修复旧版本未生成的出栏核算；每批次独立事务，已有锁定快照不改写。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zhimu.finance.repair-missing-settlements", havingValue = "true", matchIfMissing = true)
public class SaleSettlementInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final BatchSettlementService settlements;

    @Override
    public void run(ApplicationArguments args) {
        var batches = jdbc.queryForList("""
                SELECT DISTINCT o.farm_id, o.batch_id FROM t_sale_order o
                JOIN t_batch b ON b.id = o.batch_id AND b.deleted = 0
                LEFT JOIN t_batch_settlement s ON s.batch_id = o.batch_id
                WHERE s.id IS NULL OR s.status = 'RUNNING'
                """);
        int repaired = 0;
        for (var row : batches) {
            long farm = ((Number) row.get("farm_id")).longValue();
            long batch = ((Number) row.get("batch_id")).longValue();
            try {
                settlements.recalculateForSale(farm, batch);
                repaired++;
            } catch (RuntimeException error) {
                log.error("出栏核算补齐失败 farm={} batch={}", farm, batch, error);
            }
        }
        log.info("出栏核算补齐完成：成功 {} / {}", repaired, batches.size());
    }
}
