package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.readonly.AdminOperationLogQueryMapper;
import com.zhimu.server.admin.system.query.OperationLogQuery;
import com.zhimu.server.admin.system.vo.OperationLogDetailVO;
import com.zhimu.server.admin.system.vo.OperationLogItemVO;
import com.zhimu.server.biz.common.entity.OperationLog;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogAdminServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private AdminOperationLogQueryMapper queryMapper;
    private AdminMapper adminMapper;
    private OperationLogAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        queryMapper = mock(AdminOperationLogQueryMapper.class);
        adminMapper = mock(AdminMapper.class);
        service = new OperationLogAdminServiceImpl(queryMapper, adminMapper);
        AdminContext.set(AdminTestFixtures.login(2L, "吴昊", "AUDITOR",
                AdminTestFixtures.codes("WA-S06:VIEW", "WA-R01:VIEW", "WA-R01:HANDLE", "WA-S02:VIEW")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    private static OperationLog log(long id, Long operatorId, String opType) {
        OperationLog log = new OperationLog();
        log.setId(id);
        log.setOperatorType("ADMIN");
        log.setOperatorId(operatorId);
        log.setOperatorName("吴昊");
        log.setOperatorRole("AUDITOR");
        log.setOpType(opType);
        log.setOpAction("APPROVE");
        log.setResult("SUCCESS");
        log.setTargetName("北湾牛场");
        log.setReason("资料齐全");
        log.setClientType("ADMIN_WEB");
        log.setUserAgent("Mozilla/5.0 Chrome/130");
        log.setIpRegion("内蒙古赤峰");
        log.setCreatedAt(LocalDateTime.of(2026, 10, 19, 10, 0));
        return log;
    }

    @Test
    void 列表默认近7天_按权限传入可见类型() {
        when(queryMapper.countAdminLogs(anyLong(), eq(false), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(queryMapper.selectAdminLogs(anyLong(), eq(false), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of(log(1L, 3L, "FARM_AUDIT")));

        PageVO<OperationLogItemVO> page = service.list(new OperationLogQuery());

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).getOpTypeText()).isEqualTo("养殖场注册审核");
        assertThat(page.getList().get(0).getOperatorRoleText()).isEqualTo("审核员");
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(queryMapper).countAdminLogs(eq(2L), eq(false), any(), from.capture(), to.capture(), isNull(),
                isNull(), isNull());
        assertThat(from.getValue()).isEqualTo(TODAY.minusDays(6));
        assertThat(to.getValue()).isEqualTo(TODAY);
    }

    @Test
    void 审核员只看到审核类与本人日志() {
        when(queryMapper.countAdminLogs(anyLong(), eq(false), any(), any(), any(), any(), any(), any()))
                .thenReturn(0L);

        service.list(new OperationLogQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(queryMapper).countAdminLogs(eq(2L), eq(false), captor.capture(), any(), any(), any(), any(), any());
        assertThat(captor.getValue()).contains("FARM_AUDIT", "EXPORT");
    }

    @Test
    void 超管看到全部日志() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes()));
        when(queryMapper.countAdminLogs(anyLong(), eq(true), any(), any(), any(), any(), any(), any()))
                .thenReturn(0L);

        service.list(new OperationLogQuery());

        verify(queryMapper).countAdminLogs(eq(1L), eq(true), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 日期跨度超过366天返回40001() {
        OperationLogQuery query = new OperationLogQuery();
        query.setDateFrom(LocalDate.of(2024, 1, 1));
        query.setDateTo(LocalDate.of(2026, 1, 2));

        assertThatThrownBy(() -> service.list(query))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("日期范围不能超过 1 年");
                });
        verify(queryMapper, never()).countAdminLogs(any(), any(Boolean.class), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void 开始日期晚于结束日期返回40001() {
        OperationLogQuery query = new OperationLogQuery();
        query.setDateFrom(LocalDate.of(2026, 10, 19));
        query.setDateTo(LocalDate.of(2026, 10, 18));

        assertThatThrownBy(() -> service.list(query))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    @Test
    void 详情_本人日志始终可见_不可见日志按不存在处理() {
        when(queryMapper.selectLogById(1L)).thenReturn(log(1L, 2L, "LOGIN"));
        when(adminMapper.selectById(2L)).thenReturn(
                AdminTestFixtures.admin(2L, "wuhao", "吴昊", 2L, "NORMAL"));
        OperationLogDetailVO detail = service.detail(1L);
        assertThat(detail.getOperator().getUsername()).isEqualTo("wuhao");
        assertThat(detail.getOperator().getRoleText()).isEqualTo("审核员");
        assertThat(detail.getClient().getClientTypeText()).isEqualTo("网页版");
        assertThat(detail.getClient().getBrowser()).isEqualTo("Chrome");
        assertThat(detail.getLink().getExists()).isFalse();

        when(queryMapper.selectLogById(2L)).thenReturn(log(2L, 9L, "FARM_FREEZE"));
        assertThatThrownBy(() -> service.detail(2L))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40401);
                    assertThat(e.getMessage()).isEqualTo("该日志不存在");
                });
    }

    @Test
    void 详情_用户端日志不进入管理端详情() {
        OperationLog userLog = log(3L, 10001L, "RECORD_EDIT");
        userLog.setOperatorType("USER");
        when(queryMapper.selectLogById(3L)).thenReturn(userLog);

        assertThatThrownBy(() -> service.detail(3L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40401));
    }

    @Test
    void 详情_直接写编码的变更与动作显示中文() {
        AdminContext.set(AdminTestFixtures.login(2L, "吴昊", "AUDITOR",
                AdminTestFixtures.codes("WA-S06:VIEW", "WA-C03:HANDLE")));
        OperationLog row = log(1L, 9L, "COURSE");
        row.setOpAction("TOPIC_OFF_SHELF");
        row.setBeforeJson("{\"status\":\"ON_SHELF\",\"version_no\":2}");
        row.setAfterJson("{\"status\":\"OFF_SHELF\",\"version_no\":3}");
        when(queryMapper.selectLogById(1L)).thenReturn(row);
        when(adminMapper.selectById(9L)).thenReturn(
                AdminTestFixtures.admin(9L, "sunwei", "孙伟", 2L, "NORMAL"));

        OperationLogDetailVO detail = service.detail(1L);

        assertThat(detail.getOpActionText()).isEqualTo("下架专题");
        assertThat(detail.getChanges()).anySatisfy(change -> {
            assertThat(change.getFieldName()).isEqualTo("状态");
            assertThat(change.getBefore()).isEqualTo("已上架");
            assertThat(change.getAfter()).isEqualTo("已下架");
        });
        assertThat(detail.getChanges()).anySatisfy(change -> {
            assertThat(change.getFieldName()).isEqualTo("版本号");
            assertThat(change.getAfter()).isEqualTo("3");
        });
    }

    @Test
    void 详情_旧的整份快照只列前后不同的字段() {
        AdminContext.set(AdminTestFixtures.login(2L, "吴昊", "AUDITOR",
                AdminTestFixtures.codes("WA-S06:VIEW", "WA-C03:HANDLE")));
        OperationLog row = log(1L, 9L, "COURSE");
        row.setOpAction("TOPIC_UPDATE");
        row.setBeforeJson("{\"name\":\"RTW测试专题\",\"status\":\"OFF_SHELF\",\"sort_no\":3,\"category_id\":5101}");
        row.setAfterJson("{\"name\":\"RTW测试专题\",\"status\":\"OFF_SHELF\",\"sort_no\":4,\"category_id\":5101}");
        when(queryMapper.selectLogById(1L)).thenReturn(row);
        when(adminMapper.selectById(9L)).thenReturn(
                AdminTestFixtures.admin(9L, "sunwei", "孙伟", 2L, "NORMAL"));

        OperationLogDetailVO detail = service.detail(1L);

        assertThat(detail.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("排序");
            assertThat(change.getBefore()).isEqualTo("3");
            assertThat(change.getAfter()).isEqualTo("4");
        });
    }

    @Test
    void 详情_解析变更对比与相关记录() {
        AdminContext.set(AdminTestFixtures.login(2L, "吴昊", "AUDITOR",
                AdminTestFixtures.codes("WA-S06:VIEW", "WA-S05:HANDLE")));
        OperationLog row = log(1L, 9L, "ADMIN_RBAC");
        row.setBizType("ADMIN");
        row.setBizId(5L);
        row.setBeforeJson("{\"status\":{\"name\":\"状态\",\"display\":\"正常\"}}");
        row.setAfterJson("{\"status\":{\"name\":\"状态\",\"display\":\"已停用\"}}");
        when(queryMapper.selectLogById(1L)).thenReturn(row);
        when(adminMapper.selectById(9L)).thenReturn(
                AdminTestFixtures.admin(9L, "sunwei", "孙伟", 2L, "NORMAL"));
        OperationLog related = log(2L, 2L, "LOGIN");
        related.setOperatorType("USER");
        when(queryMapper.selectRelated("ADMIN", 5L, 1L, 10)).thenReturn(List.of(related));

        OperationLogDetailVO detail = service.detail(1L);

        assertThat(detail.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("状态");
            assertThat(change.getBefore()).isEqualTo("正常");
            assertThat(change.getAfter()).isEqualTo("已停用");
        });
        assertThat(detail.getRelated()).singleElement().satisfies(item -> {
            assertThat(item.getSource()).isEqualTo("USER");
            assertThat(item.getSourceText()).isEqualTo("用户");
        });
        assertThat(detail.getLink().getPageCode()).isEqualTo("WA-S05");
        assertThat(detail.getLink().getExists()).isTrue();
    }
}
