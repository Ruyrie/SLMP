package com.zhimu.server;

import com.zhimu.server.biz.breeding.service.BreedingRecordService;
import com.zhimu.server.biz.breeding.service.BreedingAnimalService;
import com.zhimu.server.biz.breeding.request.FarrowingRequest;
import com.zhimu.server.biz.breeding.support.BreedingCodes;
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
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real SQL/transactions; explicitly guarded local fixture schema, never production. */
@EnabledIfEnvironmentVariable(named="ZMT_RUN_SALE_ORDER_IT", matches="LOCAL_FIXTURES_ONLY")
@SpringBootTest(classes={ZhimuServerApplication.class, SaleOrderCreateLocalIT.Isolation.class}, webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class BreedingFarrowingLocalIT {
    @DynamicPropertySource static void local(DynamicPropertyRegistry r) { SaleOrderCreateLocalIT.localOnly(r); }
    @Autowired JdbcTemplate jdbc;
    @Autowired BreedingRecordService records;
    @Autowired BreedingAnimalService animals;
    long farm, motherCategory, childCategory, motherBreed, childBreed, motherPen, childPen, sow, cycle;
    final String tag = UUID.randomUUID().toString().substring(0,8);

    long insert(String table, Object... fields) {
        Map<String,Object> values = new LinkedHashMap<>();
        for (int i=0;i<fields.length;i+=2) values.put((String)fields[i],fields[i+1]);
        values.put("created_at",BeijingTime.now()); values.put("updated_at",BeijingTime.now());
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(values.keySet().toArray(String[]::new))
            .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    }
    void seed() {
        motherCategory=insert("t_category","name","M"+tag,"species","PIG","count_unit","HEAD","sort_no",999,"status","ENABLED","is_breeding",true);
        childCategory=insert("t_category","name","C"+tag,"species","PIG","count_unit","HEAD","sort_no",999,"status","ENABLED","is_breeding",false);
        childBreed=insert("t_breed","category_id",childCategory,"name","C"+tag,"sort_no",1,"status","ENABLED");
        motherBreed=insert("t_breed","category_id",motherCategory,"name","M"+tag,"sort_no",1,"status","ENABLED","gestation_days",114,"offspring_breed_id",childBreed);
        farm=insert("t_farm","name","QAF"+tag,"owner_user_id",99001,"main_category_id",childCategory,"scale",400,"contact_name","fixture","contact_phone","13800000000","address","fixture","province","内蒙古自治区","city","赤峰市","district","松山区","adcode","150404","longitude",118,"latitude",42,"audit_status","APPROVED");
        motherPen=insert("t_pen","farm_id",farm,"pen_code","QA-M","pen_name","QA产房","pen_type","FARROWING","category_id",motherCategory,"capacity",100,"stock_count",0,"status","ENABLED");
        childPen=insert("t_pen","farm_id",farm,"pen_code","QA-C","pen_name","QA保育舍","pen_type","NURSERY","category_id",childCategory,"capacity",400,"stock_count",0,"status","ENABLED");
        UserContext.set(new UserContext.LoginUser(99001L,ClientType.APP_ANDROID,0,"farrowing-fixture"));
        FarmContext.set(new FarmContext.FarmMember(farm,"fixture",99001L,99001L,FarmRole.OWNER,Set.of(),FarmStatus.NORMAL));
        sow=sow("QA1");
    }
    long sow(String tag) {
        long id=insert("t_breeding_animal","farm_id",farm,"ear_tag",tag,"breed_id",motherBreed,"source","PURCHASED","pen_id",motherPen,"filing_date",BeijingTime.today().minusDays(200),"status","PREGNANT");
        cycle=insert("t_breeding_cycle","farm_id",farm,"animal_id",id,"first_mating_date",BeijingTime.today().minusDays(116),"last_mating_date",BeijingTime.today().minusDays(116),"gestation_days",114,"pregnancy_check_days",21,"check_due_date",BeijingTime.today().minusDays(95),"expected_due_date",BeijingTime.today().minusDays(2),"status","PREGNANT");
        jdbc.update("UPDATE t_breeding_animal SET current_cycle_id=? WHERE id=?",cycle,id);
        return id;
    }
    FarrowingRequest request(long animal) {
        var r=new FarrowingRequest();r.setAnimalId(animal);r.setAnimalVersion(0);r.setFarrowDate(BeijingTime.today().toString());
        r.setTotalBorn(40);r.setLiveBorn(40);r.setStillborn(0);r.setTransferToBatch(true);r.setTargetPenId(childPen);r.setOffspringBreedId(childBreed);
        return r;
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE farm_id=?",Integer.class,farm); }
    void assertUnchanged(long animal,int farrowings,int batches,int stock) {
        assertThat(count("t_farrowing_record")).isEqualTo(farrowings);
        assertThat(count("t_batch")).isEqualTo(batches);
        assertThat(jdbc.queryForObject("SELECT stock_count FROM t_pen WHERE id=?",Integer.class,childPen)).isEqualTo(stock);
        assertThat(jdbc.queryForObject("SELECT status FROM t_breeding_animal WHERE id=?",String.class,animal)).isEqualTo("PREGNANT");
    }
    @Test void configuredOffspringCreatesAtomicStockAndPreventsDuplicateOrInvalidWrites() throws Exception {
        var originalClock=BeijingTime.clock();
        // Other fixture tests deliberately use huge October sequence numbers. Use an independent fixed month.
        BeijingTime.setClock(java.time.Clock.fixed(java.time.Instant.parse("2027-01-15T02:00:00Z"),BeijingTime.ZONE));
        try {
            seed();
            var ctx=animals.registerContext(sow,"FARROWING");
            assertThat(ctx.getOffspringBreed().getCategoryId()).isEqualTo(childCategory);
            assertThat(ctx.getDefaultPen().getPenId()).isEqualTo(childPen);
            assertThat(BreedingCodes.dueGroup(-2)).isEqualTo("OVERDUE");
            var r=request(sow);var result=records.createFarrowing(r,"qa-farrow-"+tag);
            assertThat(result.getBatchId()).isNotNull(); assertThat(result.getBatchNo()).isNotBlank();
            var batch=jdbc.queryForMap("SELECT * FROM t_batch WHERE id=?",result.getBatchId());
            assertThat(((Number)batch.get("breed_id")).longValue()).isEqualTo(childBreed);
            assertThat(((Number)batch.get("category_id")).longValue()).isEqualTo(childCategory);
            assertThat(((Number)batch.get("entry_age_days")).intValue()).isZero();
            assertThat(((Number)batch.get("current_count")).intValue()).isEqualTo(40);
            assertThat(batch.get("source")).isEqualTo("SELF_BRED");
            assertThat(batch.get("source_record_type")).isEqualTo("FARROWING");
            assertThat(((Number)batch.get("source_record_id")).longValue()).isEqualTo(result.getRecordId());
            assertThat(jdbc.queryForObject("SELECT batch_id FROM t_farrowing_record WHERE id=?",Long.class,result.getRecordId())).isEqualTo(result.getBatchId());
            assertThat(jdbc.queryForObject("SELECT stock_count FROM t_pen WHERE id=?",Integer.class,childPen)).isEqualTo(40);
            assertThat(jdbc.queryForObject("SELECT stock_count FROM t_batch_pen WHERE batch_id=?",Integer.class,result.getBatchId())).isEqualTo(40);
            assertThatThrownBy(()->records.createFarrowing(r,"qa-farrow-"+tag));
            assertThat(count("t_farrowing_record")).isEqualTo(1);assertThat(count("t_batch")).isEqualTo(1);
            long next=sow("QA2");var invalid=request(next);invalid.setOffspringBreedId(-1L);
            assertThatThrownBy(()->records.createFarrowing(invalid,"qa-invalid-"+tag));
            assertUnchanged(next,1,1,40);
            jdbc.update("UPDATE t_pen SET capacity=40 WHERE id=?",childPen);
            assertThatThrownBy(()->records.createFarrowing(request(next),"qa-capacity-"+tag));
            assertUnchanged(next,1,1,40);
            jdbc.update("UPDATE t_pen SET capacity=400 WHERE id=?",childPen);
            // Fails inside standard batch entry, after the farrowing INSERT; its enclosing transaction must roll back.
            jdbc.update("UPDATE t_batch SET category_id=? WHERE id=?",motherCategory,result.getBatchId());
            assertThatThrownBy(()->records.createFarrowing(request(next),"qa-conflict-"+tag));
            assertUnchanged(next,1,1,40);
            jdbc.update("UPDATE t_batch SET category_id=? WHERE id=?",childCategory,result.getBatchId());
            // A compatible occupied pen accepts a second automatic batch.
            var second=records.createFarrowing(request(next),"qa-second-"+tag);
            assertThat(second.getBatchId()).isNotEqualTo(result.getBatchId());
            assertThat(count("t_farrowing_record")).isEqualTo(2);assertThat(count("t_batch")).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT stock_count FROM t_pen WHERE id=?",Integer.class,childPen)).isEqualTo(80);
            long empty=sow("QA3");var noLive=request(empty);noLive.setTotalBorn(2);noLive.setLiveBorn(0);noLive.setStillborn(2);noLive.setTransferToBatch(false);noLive.setOffspringBreedId(-1L);
            var noBatch=records.createFarrowing(noLive,"qa-no-live-"+tag);
            assertThat(noBatch.getBatchId()).isNull();assertThat(noBatch.getAnimalStatus()).isEqualTo("EMPTY");
            assertThat(count("t_batch")).isEqualTo(2);
            String report=System.getenv("ZMT_SALE_ORDER_IT_REPORT");
            if(report!=null) Files.writeString(Path.of(report),"{\"status\":\"PASS\",\"checks\":[\"configured_cross_category_offspring\",\"linked_self_bred_batch_and_stock\",\"zero_day_age\",\"duplicate_protection\",\"invalid_breed_no_writes\",\"capacity_no_writes\",\"inner_batch_conflict_rolls_back_farrowing\",\"same_category_occupied_pen_accepts_batch\"]}");
        } finally {
            if(farm>0) {
                for(String table:jdbc.queryForList("SELECT table_name FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='farm_id'",String.class))
                    jdbc.update("DELETE FROM `"+table+"` WHERE farm_id=?",farm);
                jdbc.update("DELETE FROM t_farm WHERE id=?",farm);
            }
            jdbc.update("DELETE FROM t_breed WHERE id IN (?,?)",motherBreed,childBreed);
            jdbc.update("DELETE FROM t_category WHERE id IN (?,?)",motherCategory,childCategory);
            FarmContext.clear();UserContext.clear();BeijingTime.setClock(originalClock);
        }
    }
}
