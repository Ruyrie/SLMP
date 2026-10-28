package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.request.AdminCreateRequest;
import com.zhimu.server.admin.system.request.RolePermissionSaveRequest;
import com.zhimu.server.admin.system.service.AdminAccountService;
import com.zhimu.server.admin.system.service.AdminRoleService;
import com.zhimu.server.admin.system.vo.AdminCreatedVO;
import com.zhimu.server.admin.system.vo.AdminItemVO;
import com.zhimu.server.admin.system.vo.PermissionSavedVO;
import com.zhimu.server.admin.system.vo.RoleItemVO;
import com.zhimu.server.admin.system.vo.RolePermissionVO;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AdminRbacControllerMvcTest {

    private final AdminAccountService accountService = mock(AdminAccountService.class);
    private final AdminRoleService roleService = mock(AdminRoleService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new AdminRbacController(accountService, roleService)).build();
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void S20管理员列表分页字段() throws Exception {
        AdminItemVO item = AdminItemVO.builder().adminId(1L).name("周敏").isSelf(true)
                .username("zhoumin").roleId(1L).roleCode("SUPER_ADMIN").roleName("超级管理员")
                .phone("138****0001").status("NORMAL").statusText("正常").locked(false)
                .version(0).actions(List.of("EDIT")).build();
        when(accountService.list(any())).thenReturn(PageVO.of(List.of(item), 1, 1, 20));

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/admins"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("total").asInt()).isEqualTo(1);
        assertThat(data.get("list").get(0).get("is_self").asBoolean()).isTrue();
        assertThat(data.get("list").get(0).get("actions").get(0).asString()).isEqualTo("EDIT");
    }

    @Test
    void S21账号查重返回available() throws Exception {
        when(accountService.usernameAvailable("lisi")).thenReturn(true);

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/admins/username-check")
                        .param("username", "lisi"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("available").asBoolean()).isTrue();
    }

    @Test
    void S22新增管理员_任何环境都返回一次性temp_password且文案提示线下告知() throws Exception {
        when(accountService.create(any(AdminCreateRequest.class)))
                .thenReturn(new AdminAccountService.AdminCreateResult(10L, "李四", "139****1185", "Ab3K9mN2xQ",
                        java.time.LocalDateTime.of(2026, 10, 29, 9, 0)));

        var result = mvc.perform(post("/admin/v1/admins").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\",\"username\":\"lisi\",\"phone\":\"13900001185\","
                                + "\"role_id\":2,\"reason\":\"新同事\"}"))
                .andReturn().getResponse();
        JsonNode json = TestMvc.json(result.getContentAsString());

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(json.get("message").asString()).isEqualTo("已创建李四的管理员账号，请把临时密码线下告知对方");
        assertThat(json.get("data").get("admin_id").asLong()).isEqualTo(10L);
        assertThat(json.get("data").get("temp_password").asString()).isEqualTo("Ab3K9mN2xQ");
        assertThat(json.get("data").has("temp_password_expire_at")).isTrue();
    }

    @Test
    void S22原因为空返回40004() throws Exception {
        when(accountService.create(any())).thenThrow(new BizException(ErrorCode.REASON_EMPTY, "请填写新增原因"));

        var response = mvc.perform(post("/admin/v1/admins").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\",\"username\":\"lisi\",\"phone\":\"13900001185\",\"role_id\":2}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(TestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40004);
    }

    @Test
    void S24重置密码_返回一次性临时密码与文案() throws Exception {
        when(accountService.resetPassword(eq(2L), any())).thenReturn(
                new AdminAccountService.AdminResetResult("吴昊", "139****5874", "Zx8Kp2Qm5N",
                        java.time.LocalDateTime.of(2026, 10, 29, 9, 0)));

        JsonNode json = TestMvc.json(mvc.perform(post("/admin/v1/admins/2/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"忘记密码\"}"))
                .andReturn().getResponse().getContentAsString());

        assertThat(json.get("data").get("temp_password").asString()).isEqualTo("Zx8Kp2Qm5N");
        assertThat(json.get("message").asString()).isEqualTo("已重置吴昊的密码，请把临时密码线下告知对方");
    }

    @Test
    void S25停用返回成功文案() throws Exception {
        when(accountService.disable(eq(2L), any())).thenReturn("孙伟");

        JsonNode json = TestMvc.json(mvc.perform(post("/admin/v1/admins/2/disable")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"离职\",\"version\":0}"))
                .andReturn().getResponse().getContentAsString());

        assertThat(json.get("message").asString()).isEqualTo("已停用孙伟的管理员账号");
    }

    @Test
    void S26启用返回成功文案() throws Exception {
        when(accountService.enable(eq(2L), any())).thenReturn("孙伟");

        JsonNode json = TestMvc.json(mvc.perform(post("/admin/v1/admins/2/enable")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"恢复\",\"version\":0}"))
                .andReturn().getResponse().getContentAsString());

        assertThat(json.get("message").asString()).isEqualTo("已启用孙伟的管理员账号");
    }

    @Test
    void S27角色列表() throws Exception {
        when(roleService.list()).thenReturn(List.of(RoleItemVO.builder().roleId(2L).roleCode("AUDITOR")
                .roleName("审核员").description("审核").memberCount(3L).editable(true).build()));

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/admin-roles"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get(0).get("role_code").asString()).isEqualTo("AUDITOR");
        assertThat(data.get(0).get("member_count").asLong()).isEqualTo(3L);
    }

    @Test
    void S28角色权限表() throws Exception {
        RolePermissionVO vo = RolePermissionVO.builder().role(RoleItemVO.builder().roleId(2L)
                        .roleCode("AUDITOR").roleName("审核员").editable(true).build())
                .editable(true).version(0).rows(List.of()).defaultCodes(List.of("WA-S02:VIEW")).build();
        when(roleService.permissions(2L)).thenReturn(vo);

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/admin-roles/2/permissions"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("role").get("role_code").asString()).isEqualTo("AUDITOR");
        assertThat(data.get("default_codes").get(0).asString()).isEqualTo("WA-S02:VIEW");
    }

    @Test
    void S29保存权限返回人数与成功文案() throws Exception {
        when(roleService.savePermissions(eq(2L), any(RolePermissionSaveRequest.class)))
                .thenReturn(new AdminRoleService.SavedResult("审核员", 3));

        JsonNode json = TestMvc.json(mvc.perform(put("/admin/v1/admin-roles/2/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perm_codes\":[\"WA-R01:VIEW\",\"WA-R01:HANDLE\"],"
                                + "\"reason\":\"调整\",\"version\":0}"))
                .andReturn().getResponse().getContentAsString());

        assertThat(json.get("message").asString()).isEqualTo("已保存审核员的权限，3 名管理员立即生效");
        assertThat(json.get("data").get("affected_admin_count").asLong()).isEqualTo(3L);
        verify(roleService).savePermissions(eq(2L), any());
    }

    @Test
    void S29超管权限不可修改返回40303() throws Exception {
        when(roleService.savePermissions(eq(1L), any()))
                .thenThrow(new BizException(ErrorCode.ROLE_FORBIDDEN, "超级管理员的权限不能修改"));

        var response = mvc.perform(put("/admin/v1/admin-roles/1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perm_codes\":[],\"reason\":\"调整\",\"version\":0}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(TestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40303);
    }
}
