package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.api.ExportService;
import com.zhimu.server.admin.system.mapper.readonly.AdminUserQueryMapper;
import com.zhimu.server.admin.system.query.AdminUserListRow;
import com.zhimu.server.admin.system.query.AdminUserQuery;
import com.zhimu.server.admin.system.request.UserStatusRequest;
import com.zhimu.server.admin.system.vo.AdminUserItemVO;
import com.zhimu.server.biz.account.api.AccountAdminService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAdminServiceImplTest {

    private final AdminUserQueryMapper mapper = mock(AdminUserQueryMapper.class);
    private final AccountAdminService accountAdminService = mock(AccountAdminService.class);
    private final UserAdminServiceImpl service = new UserAdminServiceImpl(mapper, accountAdminService,
            mock(ExportService.class));

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"),
                Set.of("WA-S03:VIEW", "WA-S03:HANDLE"), 0, "tok"));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    @Test
    void 列表打码手机号并生成标记与操作() {
        AdminUserListRow row = new AdminUserListRow();
        row.setUserId(10001L);
        row.setName("王建国");
        row.setPhone("13800006620");
        row.setFarmNamesText("东山猪场、西岭鸡场");
        row.setFarmCount(2);
        row.setOwnerCount(1);
        row.setManagerCount(1);
        row.setStaffCount(0);
        row.setStatus("NORMAL");
        row.setVersion(2);
        row.setRegisteredAt(LocalDateTime.of(2026, 3, 1, 8, 0));
        row.setFarmPending(1);
        row.setRecoveryPending(1);
        row.setRecoveredAt(LocalDateTime.of(2026, 10, 18, 9, 0));
        when(mapper.countUsers(any(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.selectUsers(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(row));
        when(mapper.countUsers(eq("ALL"), any(), any(), any(), any())).thenReturn(5L);
        when(mapper.countByStatus(eq("NORMAL"), any(), any(), any(), any())).thenReturn(4L);
        when(mapper.countByStatus(eq("DISABLED"), any(), any(), any(), any())).thenReturn(1L);

        PageVO<AdminUserItemVO> page = service.list(new AdminUserQuery());

        assertThat(page.getTabCounts()).containsEntry("all", 5).containsEntry("normal", 4)
                .containsEntry("disabled", 1);
        assertThat(page.getList()).singleElement().satisfies(item -> {
            assertThat(item.getPhone()).isEqualTo("138****6620");
            assertThat(item.getRoleCounts().getOwner()).isEqualTo(1);
            assertThat(item.getRoleCounts().getManager()).isEqualTo(1);
            assertThat(item.getFlags()).extracting(AdminUserItemVO.Flag::getCode)
                    .containsExactly("FARM_PENDING", "RECOVERY_PENDING", "RECOVERED");
            assertThat(item.getActions()).containsExactly("DETAIL", "DISABLE");
            assertThat(item.getStatusText()).isEqualTo("正常");
        });
    }

    @Test
    void 注册时间范围超过一年返回40001() {
        AdminUserQuery query = new AdminUserQuery();
        query.setRegisteredFrom(LocalDate.of(2024, 1, 1));
        query.setRegisteredTo(LocalDate.of(2026, 1, 2));
        assertThatThrownBy(() -> service.list(query))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    @Test
    void 停用原因为空返回40004() {
        UserStatusRequest request = new UserStatusRequest();
        request.setReason(" ");
        request.setVersion(1);
        assertThatThrownBy(() -> service.disable(10001L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40004));
    }
}
