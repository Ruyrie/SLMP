package com.zhimu.server.admin.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminMessage;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminMessageMapper;
import com.zhimu.server.admin.system.query.AdminMessageQuery;
import com.zhimu.server.admin.system.request.MessageReadRequest;
import com.zhimu.server.admin.system.support.AdminPermissionCache;
import com.zhimu.server.admin.system.vo.AdminMessageVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMessageServiceImplTest {

    private AdminMessageMapper messageMapper;
    private AdminMapper adminMapper;
    private AdminPermissionCache permissionCache;
    private AdminMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        messageMapper = mock(AdminMessageMapper.class);
        adminMapper = mock(AdminMapper.class);
        permissionCache = mock(AdminPermissionCache.class);
        service = new AdminMessageServiceImpl(messageMapper, adminMapper, permissionCache);
        AdminContext.set(AdminTestFixtures.login(2L, "吴昊", "AUDITOR", AdminTestFixtures.codes("WA-R01:HANDLE")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    private static AdminMessageCommand command(Long adminId) {
        return AdminMessageCommand.builder()
                .adminId(adminId)
                .msgType("AUDIT_OVERDUE")
                .level("IMPORTANT")
                .title("有 3 条审核已超时")
                .summary("养殖场注册审核")
                .bizType("FARM_AUDIT")
                .bizId(1207L)
                .routePage("WA-R01")
                .routeParams(Map.of("audit_id", 1207, "tab", "OVERDUE"))
                .mergeKey("AUDIT_OVERDUE:FARM_AUDIT:1207")
                .build();
    }

    @Test
    void send写入消息并序列化route_params() {
        service.send(command(2L));

        ArgumentCaptor<AdminMessage> captor = ArgumentCaptor.forClass(AdminMessage.class);
        verify(messageMapper).insert(captor.capture());
        AdminMessage message = captor.getValue();
        assertThat(message.getAdminId()).isEqualTo(2L);
        assertThat(message.getMsgType()).isEqualTo("AUDIT_OVERDUE");
        assertThat(message.getLevel()).isEqualTo("IMPORTANT");
        assertThat(message.getRouteParams()).contains("\"audit_id\":1207");
        assertThat(message.getMergeKey()).isEqualTo("AUDIT_OVERDUE:FARM_AUDIT:1207");
        assertThat(message.getReadAt()).isNull();
    }

    @Test
    void send_merge_key已存在时更新并重新置为未读() {
        AdminMessage existing = new AdminMessage();
        existing.setId(9L);
        existing.setAdminId(2L);
        existing.setReadAt(LocalDateTime.of(2026, 10, 18, 9, 0));
        when(messageMapper.selectByMergeKey(2L, "AUDIT_OVERDUE:FARM_AUDIT:1207")).thenReturn(existing);

        service.send(command(2L));

        verify(messageMapper).updateById(existing);
        verify(messageMapper, never()).insert(any(AdminMessage.class));
        assertThat(existing.getReadAt()).isNull();
        assertThat(existing.getTitle()).isEqualTo("有 3 条审核已超时");
    }

    @Test
    void send没有接收人或命令为空时不写库() {
        service.send(null);
        service.send(AdminMessageCommand.builder().title("x").build());
        verify(messageMapper, never()).insert(any(AdminMessage.class));
    }

    @Test
    void sendToPermission只发给拥有该权限的正常管理员() {
        Admin superAdmin = AdminTestFixtures.admin(1L, "zhoumin", "周敏", 1L, "NORMAL");
        Admin auditor = AdminTestFixtures.admin(2L, "wuhao", "吴昊", 2L, "NORMAL");
        when(adminMapper.selectAllNormal()).thenReturn(List.of(superAdmin, auditor));
        when(permissionCache.permissionsOfRole(1L)).thenReturn(AdminTestFixtures.codes("WA-C02:HANDLE"));
        when(permissionCache.permissionsOfRole(2L)).thenReturn(AdminTestFixtures.codes("WA-R01:HANDLE"));

        service.sendToPermission("WA-C02:HANDLE", command(null));

        ArgumentCaptor<AdminMessage> captor = ArgumentCaptor.forClass(AdminMessage.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getAdminId()).isEqualTo(1L);
    }

    @Test
    void unreadCount_当前管理员() {
        when(messageMapper.countUnread(2L)).thenReturn(4L);
        assertThat(service.unreadCount()).isEqualTo(4);
        assertThat(service.unreadCount(3L)).isZero();
    }

    @Test
    void page只看本人消息_带all与unread计数() {
        AdminMessage message = new AdminMessage();
        message.setId(1L);
        message.setAdminId(2L);
        message.setMsgType("SMS_FAILED");
        message.setLevel("NORMAL");
        message.setTitle("短信发送失败，请重新重置密码");
        message.setRoutePage("WA-S05");
        message.setRouteParams("{\"admin_id\":7}");
        message.setReadAt(null);
        message.setCreatedAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        Page<AdminMessage> page = new Page<>(1, 20);
        page.setRecords(List.of(message));
        page.setTotal(1);
        when(messageMapper.selectPage(any(), any())).thenReturn(page);
        when(messageMapper.countAll(2L)).thenReturn(5L);
        when(messageMapper.countUnread(2L)).thenReturn(3L);

        AdminMessageQuery query = new AdminMessageQuery();
        PageVO<AdminMessageVO> result = service.page(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getTabCounts()).containsEntry("all", 5).containsEntry("unread", 3);
        AdminMessageVO vo = result.getList().get(0);
        assertThat(vo.getRead()).isFalse();
        assertThat(vo.getMsgTypeText()).isEqualTo("短信发送失败");
        assertThat(vo.getRouteParams()).containsEntry("admin_id", 7);
    }

    @Test
    void markRead_all标记全部未读() {
        when(messageMapper.updateReadAll(eq(2L), any())).thenReturn(6);

        int updated = service.markRead(new MessageReadRequest(null, true));

        assertThat(updated).isEqualTo(6);
        verify(messageMapper).updateReadAll(eq(2L), any());
        verify(messageMapper, never()).updateReadByIds(any(), any(), any());
    }

    @Test
    void markRead_按ID标记已读() {
        when(messageMapper.updateReadByIds(eq(2L), eq(List.of(1L, 2L)), any())).thenReturn(2);

        assertThat(service.markRead(new MessageReadRequest(List.of(1L, 2L), false))).isEqualTo(2);
    }

    @Test
    void markRead_既没有ID也没有all返回40001() {
        assertThatThrownBy(() -> service.markRead(new MessageReadRequest(null, false)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("请选择要标记的消息");
                });
        assertThatThrownBy(() -> service.markRead(null))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
        verify(messageMapper, never()).updateReadAll(any(), any());
        verify(messageMapper, never()).updateReadByIds(any(), any(), any());
    }
}
