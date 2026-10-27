package com.zhimu.server;

import com.zhimu.server.biz.finance.service.*;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.support.change.SaleOrderChangeHandler;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.common.context.*;
import com.zhimu.server.common.enums.*;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real service/database lifecycle; opt-in guarded local fixture schema only. */
@EnabledIfEnvironmentVariable(named="ZMT_RUN_SALE_ORDER_IT", matches="LOCAL_FIXTURES_ONLY")
@SpringBootTest(classes={ZhimuServerApplication.class, SaleOrderCreateLocalIT.Isolation.class},
        webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class SaleSettlementLocalIT {
    @DynamicPropertySource static void local(DynamicPropertyRegistry registry) { SaleOrderCreateLocalIT.localOnly(registry); }
    @Autowired JdbcTemplate jdbc;
    @Autowired SaleOrderService sales;
    @Autowired BatchSettlementService settlements;
    @Autowired SaleOrderChangeHandler corrections;
    private long farm, batch;
    private final String tag = UUID.randomUUID().toString().substring(0,8);

    long insert(String table, Object... fields) {
        Map<String,Object> values = new LinkedHashMap<>();
        for(int i=0;i<fields.length;i+=2) values.put((String)fields[i], fields[i+1]);
        values.put("created_at", BeijingTime.now()); values.put("updated_at", BeijingTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(values.keySet().toArray(String[]::new)).usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    }
    void seed() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM t_finance_category WHERE code='SALE'", Integer.class) == 0)
            insert("t_finance_category", "direction", "INCOME", "code", "SALE", "name", "出栏销售", "sort_no", 1, "status", "ENABLED");
        long category=insert("t_category", "name","QA"+tag,"species","PIG","count_unit","HEAD","sort_no",999,"status","ENABLED");
        farm=insert("t_farm","name","QA"+tag,"owner_user_id",99001,"main_category_id",category,"scale",200,
                "contact_name","fixture","contact_phone","13800000000","address","fixture","province","内蒙古自治区",
                "city","赤峰市","district","松山区","adcode","150404","longitude",118,"latitude",42,"audit_status","APPROVED");
        long pen=insert("t_pen","farm_id",farm,"pen_code","QA","pen_name","QA","pen_type","FATTENING",
                "category_id",category,"capacity",300,"stock_count",200);
        batch=insert("t_batch","farm_id",farm,"batch_no","QA-"+tag,"no_month","2610","no_seq",(int)(System.nanoTime()&0x7fffffff),
                "qr_token",UUID.randomUUID().toString().replace("-","").substring(0,10),"category_id",category,"breed_id",1,"purpose","COMMERCIAL",
                "source","PURCHASED","entry_pen_id",pen,"entry_date",BeijingTime.today().minusDays(50),"entry_age_days",15,
                "entry_count",215,"entry_avg_weight",12,"intro_unit_price",430,"current_count",200,"death_count",15,"status","ACTIVE");
        insert("t_batch_pen","farm_id",farm,"batch_id",batch,"pen_id",pen,"stock_count",200,"first_in_date",BeijingTime.today().minusDays(50));
        insert("t_batch_material_cost","farm_id",farm,"batch_id",batch,"flow_id",batch,"source_type","FEED","source_id",batch,
                "cost_type","FEED","qty",14840,"ratio",1,"amount",10000,"biz_date",BeijingTime.today());
        insert("t_batch_material_cost","farm_id",farm,"batch_id",batch,"flow_id",batch+100000,"source_type","DRUG","source_id",batch,
                "cost_type","DRUG","qty",1,"ratio",1,"amount",500,"biz_date",BeijingTime.today());
        UserContext.set(new UserContext.LoginUser(99001L,ClientType.APP_ANDROID,0,"settlement-fixture"));
        FarmContext.set(new FarmContext.FarmMember(farm,"fixture",99001L,99001L,FarmRole.OWNER,Set.of(),FarmStatus.NORMAL));
    }
    long sale(int quantity) {
        var r=new FinanceRequests.SaleOrderRequest();r.setBatchId(batch);r.setSaleDate(BeijingTime.today().toString());
        r.setQuantity(quantity);r.setTotalWeight(BigDecimal.valueOf(quantity*50L));r.setPricingMethod("BY_WEIGHT");
        r.setUnitPrice(new BigDecimal("16.5"));r.setBuyerName("fixture");
        return sales.create(r,UUID.randomUUID().toString()).getId();
    }
    void receive(long id,String amount) {
        var r=new FinanceRequests.SaleReceiptRequest();r.setAmount(new BigDecimal(amount));
        r.setReceiptDate(BeijingTime.today().toString());r.setPaymentMethod("BANK_TRANSFER");
        sales.addReceipt(id,r,UUID.randomUUID().toString());
    }
    void approve(long id,String field,String value) {
        var request=new ChangeRequest();request.setId(99001L);request.setBizId(id);request.setBizType("SALE_ORDER");
        request.setFarmId(farm);request.setChangeType("UPDATE");request.setFieldCode(field);request.setReason("fixture correction");
        request.setAfterJson("{\"after\":{\""+field+"\":{\"value\":\""+value+"\"}}}");
        request.setSubmittedBy(99001L);request.setSubmitterRole("OWNER");
        var record=corrections.loadRecord(BizType.SALE_ORDER,id);
        corrections.validateForApprove(request,record);corrections.apply(request,record,99001L);
    }
    @Test void existingAndFutureSalesAlwaysHaveAccountingAndApprovedCorrectionsStayConsistent() throws Exception {
        seed();
        try {
            long first=sale(100);
            var partial=sales.detail(first).getSettlementBrief();
            assertThat(partial.getSaleIncome()).isEqualByComparingTo("82500");
            assertThat(partial.getCostTotal()).isEqualByComparingTo("102950");
            assertThat(partial.getStatus()).isEqualTo("RUNNING");
            receive(first,"40000");receive(first,"42500");
            long last=sale(100);receive(last,"82500");
            var full=sales.detail(last).getSettlementBrief();
            assertThat(full.getSaleIncome()).isEqualByComparingTo("165000");
            assertThat(full.getProfit()).isEqualByComparingTo("62050");
            assertThat(full.getCostPerHead()).isEqualByComparingTo("514.75");
            assertThat(full.getFcr()).isEqualByComparingTo("2.00");
            assertThat(full.getStatus()).isEqualTo("LOCKED");
            // Simulate a historical sale whose snapshot was never initialized.
            jdbc.update("DELETE FROM t_batch_settlement WHERE farm_id=? AND batch_id=?",farm,batch);
            new com.zhimu.server.biz.finance.support.SaleSettlementInitializer(jdbc, settlements).run(null);
            assertThat(sales.detail(first).getSettlementBrief().getSaleIncome()).isEqualByComparingTo("165000");
            approve(last,"quantity","99");
            var corrected=sales.detail(last);
            assertThat(corrected.getSaleAmount()).isEqualByComparingTo("81675");
            assertThat(corrected.getReceivedAmount()).isEqualByComparingTo("81675");
            assertThat(corrected.getUnpaidAmount()).isZero();
            assertThat(jdbc.queryForObject("SELECT current_count FROM t_batch WHERE id=?",Integer.class,batch)).isEqualTo(1);
            assertThat(corrected.getSettlementBrief().getSaleIncome()).isEqualByComparingTo("164175");
            assertThat(corrected.getSettlementBrief().getStatus()).isEqualTo("RUNNING");
            approve(first,"unit_price","7.5");
            var reduced=sales.detail(first);
            assertThat(reduced.getSaleAmount()).isEqualByComparingTo("37500");
            assertThat(reduced.getReceivedAmount()).isEqualByComparingTo("37500");
            assertThat(reduced.getReceipts()).filteredOn(r -> "NORMAL".equals(r.getStatus())).hasSize(1);
            assertThat(reduced.getReceipts()).filteredOn(r -> "VOID".equals(r.getStatus())).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM t_finance_record WHERE farm_id=? AND status='NORMAL' AND record_type='INCOME'",BigDecimal.class,farm)).isEqualByComparingTo("119175");
            assertThat(reduced.getSettlementBrief().getSaleIncome()).isEqualByComparingTo("119175");
            assertThat(reduced.getSettlementBrief().getProfit()).isEqualByComparingTo("16225");
            Files.writeString(Path.of(System.getenv("ZMT_SALE_ORDER_IT_REPORT")),"{\"status\":\"PASS\",\"checks\":[\"new_partial_and_full_sales_initialize_accounting\",\"historic_missing_snapshot_self_repairs\",\"entry_and_feed_costs_and_fcr\",\"approved_quantity_restores_stock\",\"receipts_and_income_corrected_together\",\"multiple_receipts_reconciled\",\"batch_profit_matches_corrected_income\"]}");
        } finally { FarmContext.clear();UserContext.clear(); }
    }
}
