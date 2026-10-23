package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.service.OperationLogAdminService;
import com.zhimu.server.admin.system.vo.OperationLogDetailVO;
import com.zhimu.server.admin.system.vo.OperationLogItemVO;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class OperationLogAdminControllerMvcTest {

    private final OperationLogAdminService logService = mock(OperationLogAdminService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new OperationLogAdminController(logService)).build();
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void S30日志列表分页() throws Exception {
        OperationLogItemVO item = OperationLogItemVO.builder().logId(1L)
                .createdAt(LocalDateTime.of(2026, 10, 19, 10, 0)).operatorName("吴昊")
                .operatorRole("AUDITOR").operatorRoleText("审核员")
                .opType("FARM_AUDIT").opTypeText("养殖场注册审核")
                .opAction("APPROVE").opActionText("通过").result("SUCCESS")
                .targetName("北湾牛场").reason("资料齐全").build();
        when(logService.list(any())).thenReturn(PageVO.of(List.of(item), 1, 1, 20));

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/operation-logs")
                        .param("date_from", "2026-10-13").param("date_to", "2026-10-19"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("list").get(0).get("op_type_text").asString()).isEqualTo("养殖场注册审核");
        assertThat(data.get("list").get(0).get("operator_role_text").asString()).isEqualTo("审核员");
    }

    @Test
    void S31日志详情包含变更与相关记录() throws Exception {
        OperationLogDetailVO detail = OperationLogDetailVO.builder()
                .operator(OperationLogDetailVO.Operator.builder().name("孙伟").username("sunwei")
                        .roleText("审核员").build())
                .opType("ADMIN_RBAC").opTypeText("管理员与权限")
                .opAction("DISABLE").opActionText("停用").result("SUCCESS").resultText("成功")
                .targetName("孙伟").reason("离职")
                .client(OperationLogDetailVO.Client.builder().clientTypeText("网页版").browser("Chrome")
                        .ipRegion("内蒙古赤峰").build())
                .changes(List.of(OperationLogDetailVO.Change.builder().fieldName("状态")
                        .before("正常").after("已停用").build()))
                .related(List.of(OperationLogDetailVO.Related.builder().source("USER").sourceText("用户")
                        .desc("登录 · 登录").build()))
                .link(OperationLogDetailVO.Link.builder().pageCode("WA-S05").bizType("ADMIN")
                        .bizId(2L).exists(true).build())
                .build();
        when(logService.detail(1L)).thenReturn(detail);

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/operation-logs/1"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("operator").get("username").asString()).isEqualTo("sunwei");
        assertThat(data.get("changes").get(0).get("field_name").asString()).isEqualTo("状态");
        assertThat(data.get("link").get("page_code").asString()).isEqualTo("WA-S05");
        assertThat(data.get("client").get("browser").asString()).isEqualTo("Chrome");
    }

    @Test
    void S31不可见日志返回404() throws Exception {
        when(logService.detail(9L)).thenThrow(new BizException(ErrorCode.NOT_FOUND, "该日志不存在"));

        var response = mvc.perform(get("/admin/v1/operation-logs/9")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(TestMvc.json(response.getContentAsString()).get("message").asString())
                .isEqualTo("该日志不存在");
    }
}
