package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.controller.ChangeRequestController;
import com.zhimu.server.biz.common.controller.RecordLogController;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.enums.FarmRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U01–U04 + 审核回写 + U14 的完整链路（MockMvc standalone + 内存 Mapper + 测试 handler）。
 */
class ChangeRequestFlowTest {

    private CommonFlowFixture fixture;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        fixture = new CommonFlowFixture();
        mvc = TestMvc.builder(
                new ChangeRequestController(fixture.applicationService),
                new RecordLogController(fixture.logQueryService)).build();
        CommonFlowFixture.login(10001L);
        fixture.grant(1001L, 10001L, FarmRole.OWNER);
    }

    @AfterEach
    void tearDown() {
        CommonFlowFixture.logout();
    }

    @Test
    void u01ReturnsCompleteFormContext() throws Exception {
        mvc.perform(get("/api/v1/change-requests/form")
                        .param("biz_type", "FEED_RECORD").param("biz_id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.biz_type").value("FEED_RECORD"))
                .andExpect(jsonPath("$.data.biz_type_text").value("投喂记录"))
                .andExpect(jsonPath("$.data.biz_id").value(7001))
                .andExpect(jsonPath("$.data.record_locator").value("3 号育肥舍 · ZM2610-031 · 10月18日 晚餐"))
                .andExpect(jsonPath("$.data.record_title").value("投喂记录 · 3 号育肥舍"))
                .andExpect(jsonPath("$.data.record_subtitle").value("ZM2610-031 · 10月18日 晚餐 · 李明登记"))
                .andExpect(jsonPath("$.data.record_version").value("3"))
                .andExpect(jsonPath("$.data.void_allowed").value(true))
                .andExpect(jsonPath("$.data.void_notice").isNotEmpty())
                .andExpect(jsonPath("$.data.void_recalc_tags.length()").value(3))
                .andExpect(jsonPath("$.data.record_card.length()").value(3))
                .andExpect(jsonPath("$.data.record_card[0].label").value("饲料"))
                .andExpect(jsonPath("$.data.record_card[0].value").value("育肥全价料"))
                .andExpect(jsonPath("$.data.fields[1].field_code").value("actual_qty"))
                .andExpect(jsonPath("$.data.fields[1].field_name").value("实际投喂量"))
                .andExpect(jsonPath("$.data.fields[1].value").value("140"))
                .andExpect(jsonPath("$.data.fields[1].value_unit").value("千克"))
                .andExpect(jsonPath("$.data.fields[1].input_type").value("NUMBER"))
                .andExpect(jsonPath("$.data.fields[1].min").value(0))
                .andExpect(jsonPath("$.data.fields[1].max").value(1000))
                .andExpect(jsonPath("$.data.fields[0].input_type").value("SELECT"))
                .andExpect(jsonPath("$.data.fields[0].options.length()").value(2))
                .andExpect(jsonPath("$.data.field_hints.actual_qty").value("计划 160 千克"))
                .andExpect(jsonPath("$.data.field_hints.feed_id").value("可选：育肥全价料、保育料"))
                .andExpect(jsonPath("$.data.recalc_tags.actual_qty.length()").value(4))
                .andExpect(jsonPath("$.data.linked_rules[0]").value("修改实际投喂量会同步调整饲料库存出库数量"))
                .andExpect(jsonPath("$.data.review_tip").isNotEmpty());
    }

    @Test
    void u01WithoutHandlerReturns42207() throws Exception {
        mvc.perform(get("/api/v1/change-requests/form")
                        .param("biz_type", "SALE_ORDER").param("biz_id", "6101"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42207));
    }

    @Test
    void u01RecordNotFoundReturns40401() throws Exception {
        mvc.perform(get("/api/v1/change-requests/form")
                        .param("biz_type", "FEED_RECORD").param("biz_id", "9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void u01StaffReturns40303() throws Exception {
        fixture.grant(1001L, 10001L, FarmRole.STAFF);
        mvc.perform(get("/api/v1/change-requests/form")
                        .param("biz_type", "FEED_RECORD").param("biz_id", "7001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303))
                .andExpect(jsonPath("$.message").value("员工不能提交或查看修改申请，如需修改请联系所有者或场长。"));
    }

    @Test
    void u02SubmitUpdateCreatesPendingRequest() throws Exception {
        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "160", "3",
                "登记时把实际投喂量误填为 140 千克，当餐实际投喂 160 千克。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.request_no").value(org.hamcrest.Matchers.startsWith("CR20261019")))
                .andExpect(jsonPath("$.data.biz_type_text").value("投喂记录"))
                .andExpect(jsonPath("$.data.biz_id").value(7001))
                .andExpect(jsonPath("$.data.farm_id").value(1001))
                .andExpect(jsonPath("$.data.farm_name").value("东山猪场"))
                .andExpect(jsonPath("$.data.change_type").value("UPDATE"))
                .andExpect(jsonPath("$.data.field_code").value("actual_qty"))
                .andExpect(jsonPath("$.data.field_name").value("实际投喂量"))
                .andExpect(jsonPath("$.data.before_display").value("140 千克"))
                .andExpect(jsonPath("$.data.after_display").value("160 千克"))
                .andExpect(jsonPath("$.data.after_value").value("160"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.status_text").value("待审核"))
                .andExpect(jsonPath("$.data.impacts[0]").value("库存"))
                .andExpect(jsonPath("$.data.recalc_tags.length()").value(4))
                .andExpect(jsonPath("$.data.batch_id").value(3001));

        ChangeRequest saved = fixture.lastRequest();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getPendingFlag()).isEqualTo(1);
        assertThat(saved.getSourceModule()).isEqualTo("FEEDING");
        assertThat(saved.getSubmitterRole()).isEqualTo("OWNER");
        assertThat(saved.getFarmId()).isEqualTo(1001L);
        assertThat(saved.getRecordVersion()).isEqualTo("3");
        assertThat(saved.getImpactInventory()).isEqualTo(1);
        assertThat(saved.getImpactCost()).isEqualTo(1);
        assertThat(saved.getRecordLocator()).isEqualTo("3 号育肥舍 · ZM2610-031 · 10月18日 晚餐");

        JsonNode before = TestMvc.json(saved.getBeforeJson());
        assertThat(before.has("record_card")).isTrue();
        assertThat(before.has("fields")).isTrue();
        assertThat(before.has("compare_keys")).isTrue();
        assertThat(before.path("before").path("actual_qty").path("display").asString()).isEqualTo("140 千克");
        assertThat(before.path("after").isEmpty()).isTrue();
        JsonNode after = TestMvc.json(saved.getAfterJson());
        assertThat(after.path("before").path("actual_qty").path("value").asString()).isEqualTo("140");
        assertThat(after.path("after").path("actual_qty").path("value").asString()).isEqualTo("160");
        assertThat(after.path("after").path("actual_qty").path("display").asString()).isEqualTo("160 千克");
        assertThat(after.path("fields").size()).isEqualTo(2);
    }

    @Test
    void u02VoidDoesNotRequireField() throws Exception {
        String body = body("FEED_RECORD", 7001, "VOID", null, null, "3", "当餐实际没有投喂。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.change_type").value("VOID"))
                .andExpect(jsonPath("$.data.field_code").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(fixture.lastRequest().getFieldCode()).isNull();
        assertThat(fixture.lastRequest().getRecalcTags()).contains("库存（退回饲料）");
    }

    @Test
    void u02DuplicatePendingReturns40906() throws Exception {
        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "160", "3", "填错了，改为 160 千克。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));
    }

    @Test
    void u02VersionMismatchReturns40902() throws Exception {
        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "160", "2", "填错了。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("内容已更新，请刷新后再修改"));
    }

    @Test
    void u02SameValueReturns40001WithOnlyAllowedKeys() throws Exception {
        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "140", "3", "填错了。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.field_errors[0].field").value("new_value"))
                .andExpect(jsonPath("$.data.field_errors[0].message").value("新值与原值相同，不需要提交"));
    }

    @Test
    void u02EmptyReasonReturns40001Reason() throws Exception {
        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "160", "3", "   ", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.field_errors[0].field").value("reason"));
    }

    @Test
    void u03ListsMineWithTabCountsAndFarmName() throws Exception {
        submitUpdate();
        ChangeRequest canceled = new ChangeRequest();
        canceled.setId(9999L);
        canceled.setBizType("FEED_RECORD");
        canceled.setBizId(7002L);
        canceled.setFarmId(1001L);
        canceled.setSubmittedBy(10001L);
        canceled.setStatus("CANCELED");
        canceled.setSubmittedAt(java.time.LocalDateTime.now());
        fixture.requests.add(canceled);

        mvc.perform(get("/api/v1/change-requests").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.list[0].status_text").value("待审核"))
                .andExpect(jsonPath("$.data.list[0].farm_id").value(1001))
                .andExpect(jsonPath("$.data.list[0].farm_name").value("东山猪场"))
                .andExpect(jsonPath("$.data.list[0].field_code").value("actual_qty"))
                .andExpect(jsonPath("$.data.list[0].after_value").value("160"))
                .andExpect(jsonPath("$.data.list[0].biz_type_text").value("投喂记录"))
                .andExpect(jsonPath("$.data.tab_counts.ALL").value(1))
                .andExpect(jsonPath("$.data.tab_counts.PENDING").value(1))
                .andExpect(jsonPath("$.data.tab_counts.APPROVED").value(0))
                .andExpect(jsonPath("$.data.tab_counts.REJECTED").value(0));

        mvc.perform(get("/api/v1/change-requests").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.tab_counts.ALL").value(1))
                .andExpect(jsonPath("$.data.tab_counts.PENDING").value(1));
    }

    @Test
    void u03StaffWithNoRequestsReturns40303() throws Exception {
        fixture.enterFarm(1001L, 10001L, FarmRole.STAFF);
        mvc.perform(get("/api/v1/change-requests"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
    }

    @Test
    void u04DetailVisibleToSubmitterAndOwner() throws Exception {
        submitUpdate();
        long id = fixture.lastRequest().getId();

        mvc.perform(get("/api/v1/change-requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.reason").value("登记时把实际投喂量误填为 140 千克，当餐实际投喂 160 千克。"))
                .andExpect(jsonPath("$.data.after_value").value("160"))
                .andExpect(jsonPath("$.data.status_text").value("待审核"));

        // 同场所有者可看
        CommonFlowFixture.login(10003L);
        fixture.grant(1001L, 10003L, FarmRole.OWNER);
        mvc.perform(get("/api/v1/change-requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        // 非成员 40401
        CommonFlowFixture.login(10002L);
        mvc.perform(get("/api/v1/change-requests/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void adminApproveAppliesChangeAndU14ShowsModification() throws Exception {
        submitUpdate();
        ChangeRequest request = fixture.lastRequest();
        long id = request.getId();
        String requestNo = request.getRequestNo();

        fixture.adminService.approve(id, 999L, "同意修改", 0);

        assertThat(request.getStatus()).isEqualTo("APPROVED");
        assertThat(request.getPendingFlag()).isNull();
        assertThat(request.getVersion()).isEqualTo(1);
        assertThat(request.getRecalcSummary()).isEqualTo("已重新计算批次收益");
        assertThat(request.getRecalcCount()).isEqualTo(4);
        assertThat(request.getImpactJson()).contains("recalc_tags");
        assertThat(fixture.handler.actualQty()).isEqualTo("160");
        assertThat(fixture.handler.version()).isEqualTo("4");
        assertThat(fixture.logs).anyMatch(log -> "ADMIN".equals(log.getOperatorType())
                && "CHANGE_REQUEST".equals(log.getOpType()) && "APPROVE".equals(log.getOpAction())
                && id == log.getChangeRequestId());

        mvc.perform(get("/api/v1/record-logs")
                        .param("biz_type", "FEED_RECORD").param("biz_id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].op_action").value("UPDATE"))
                .andExpect(jsonPath("$.data[0].op_action_text").value("修改"))
                .andExpect(jsonPath("$.data[0].operator.name").value("王建国"))
                .andExpect(jsonPath("$.data[0].operator.role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].operator.role_text").value("所有者"))
                .andExpect(jsonPath("$.data[0].changes[0].field_name").value("实际投喂量"))
                .andExpect(jsonPath("$.data[0].changes[0].before").value("140 千克"))
                .andExpect(jsonPath("$.data[0].changes[0].after").value("160 千克"))
                .andExpect(jsonPath("$.data[0].change_request_no").value(requestNo))
                .andExpect(jsonPath("$.data[0].reason").value("登记时把实际投喂量误填为 140 千克，当餐实际投喂 160 千克。"));
    }

    @Test
    void adminRejectMarksRequestAndKeepsU14Empty() throws Exception {
        submitUpdate();
        ChangeRequest request = fixture.lastRequest();

        fixture.adminService.reject(request.getId(), 999L, "与单据不一致", 0);

        assertThat(request.getStatus()).isEqualTo("REJECTED");
        assertThat(request.getPendingFlag()).isNull();
        assertThat(request.getRejectReason()).isEqualTo("与单据不一致");
        verifyStatusBadge();

        mvc.perform(get("/api/v1/record-logs")
                        .param("biz_type", "FEED_RECORD").param("biz_id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void u02ResubmitRejectedRequestCreatesNewPending() throws Exception {
        submitUpdate();
        ChangeRequest rejected = fixture.lastRequest();
        fixture.adminService.reject(rejected.getId(), 999L, "与单据不一致", 0);

        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "160", "3", "已核对单据，改为 160 千克。",
                rejected.getId());
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.not(rejected.getId().intValue())));
        assertThat(fixture.requests).hasSize(2);
        assertThat(fixture.lastRequest().getResubmitOfId()).isEqualTo(rejected.getId());

        // 同一条被驳回的申请不能再重新提交一次
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    @Test
    void u02DeleteNotAllowedReturns42207() throws Exception {
        String body = body("FEED_RECORD", 7001, "DELETE", null, null, "3", "这条记录不需要了。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42207));
    }

    private void verifyStatusBadge() {
        assertThat(fixture.logs).anyMatch(log -> "ADMIN".equals(log.getOperatorType())
                && "REJECT".equals(log.getOpAction()) && log.getChangeRequestId() != null);
    }

    private void submitUpdate() throws Exception {
        String body = body("FEED_RECORD", 7001, "UPDATE", "actual_qty", "160", "3",
                "登记时把实际投喂量误填为 140 千克，当餐实际投喂 160 千克。", null);
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private static String body(String bizType, long bizId, String changeType, String fieldCode,
                               String newValue, String recordVersion, String reason, Long resubmitOfId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("biz_type", bizType);
        map.put("biz_id", bizId);
        map.put("change_type", changeType);
        if (fieldCode != null) {
            map.put("field_code", fieldCode);
        }
        if (newValue != null) {
            map.put("new_value", newValue);
        }
        map.put("record_version", recordVersion);
        map.put("reason", reason);
        if (resubmitOfId != null) {
            map.put("resubmit_of_id", resubmitOfId);
        }
        return TestMvc.MAPPER.writeValueAsString(map);
    }
}
