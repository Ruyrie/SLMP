package com.zhimu.server.framework.oplog;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpLogAspectTest {

    @Getter
    @AllArgsConstructor
    public static class RejectRequest {
        private String farmName;
        private String reason;
    }

    @Getter
    @AllArgsConstructor
    public static class AuditResult {
        private Long auditId;
        private Long farmId;
    }

    public static class DemoService {

        @OpLog(opType = "FARM_AUDIT", opAction = "REJECT", bizType = "FARM_AUDIT", bizId = "#result.auditId",
                targetName = "#request.farmName", reason = "#request.reason", farmId = "#result.farmId")
        public AuditResult reject(Long id, RejectRequest request) {
            return new AuditResult(id + 1, 1001L);
        }

        @OpLog(opType = "FARM_MEMBER", opAction = "REMOVE", bizType = "FARM_MEMBER", bizId = "#memberId")
        public void removeMember(Long memberId) {
        }

        @OpLog(opType = "RECORD_EDIT", opAction = "DELETE", bizType = "FEED_RECORD", bizId = "#id",
                targetName = "#result.name")
        public Object deleteRecord(Long id) {
            throw new BizException(ErrorCode.STATE_CHANGED);
        }

        @OpLog(opType = "RECORD_EDIT", opAction = "UPDATE", bizId = "#notExists.id", targetName = "'固定文字'")
        public String badExpression(Long id) {
            return "ok";
        }
    }

    private OperationLogRecorder recorder;
    private ObjectProvider<OperationLogRecorder> provider;
    private DemoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        recorder = mock(OperationLogRecorder.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        service = proxy(provider);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
        UserContext.clear();
        FarmContext.clear();
        RequestContext.clear();
    }

    @Test
    void resolvesSpelFromArgumentsAndResultForAdmin() {
        AdminContext.set(new AdminContext.LoginAdmin(9L, "吴昊", Set.of("SUPER_ADMIN", "AUDITOR"), Set.of(), 1, "s"));

        AuditResult result = service.reject(41L, new RejectRequest("东山猪场", "资质照片不清晰"));

        assertThat(result.getAuditId()).isEqualTo(42L);
        OperationLogEntry entry = captured();
        assertThat(entry.getOpType()).isEqualTo("FARM_AUDIT");
        assertThat(entry.getOpAction()).isEqualTo("REJECT");
        assertThat(entry.getResult()).isEqualTo("SUCCESS");
        assertThat(entry.getBizType()).isEqualTo("FARM_AUDIT");
        assertThat(entry.getBizId()).isEqualTo(42L);
        assertThat(entry.getTargetName()).isEqualTo("东山猪场");
        assertThat(entry.getReason()).isEqualTo("资质照片不清晰");
        assertThat(entry.getFarmId()).isEqualTo(1001L);
        assertThat(entry.getOperatorType()).isEqualTo("ADMIN");
        assertThat(entry.getOperatorId()).isEqualTo(9L);
        assertThat(entry.getOperatorName()).isEqualTo("吴昊");
        assertThat(entry.getOperatorRole()).isEqualTo("AUDITOR,SUPER_ADMIN");
    }

    @Test
    void fillsUserOperatorWithFarmRole() {
        UserContext.set(new UserContext.LoginUser(7L, ClientType.APP_ANDROID, 1, "jti"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 7L, 70L, FarmRole.MANAGER, Set.of(),
                FarmStatus.NORMAL));

        service.removeMember(88L);

        OperationLogEntry entry = captured();
        assertThat(entry.getBizId()).isEqualTo(88L);
        assertThat(entry.getOperatorType()).isEqualTo("USER");
        assertThat(entry.getOperatorId()).isEqualTo(7L);
        assertThat(entry.getOperatorRole()).isEqualTo("MANAGER");
        assertThat(entry.getOperatorName()).isNull();
        assertThat(entry.getFarmId()).isEqualTo(1001L);
    }

    @Test
    void systemOperatorWithoutContext() {
        service.removeMember(88L);

        OperationLogEntry entry = captured();
        assertThat(entry.getOperatorType()).isEqualTo("SYSTEM");
        assertThat(entry.getOperatorId()).isNull();
        assertThat(entry.getFarmId()).isNull();
    }

    @Test
    void recordsFailAndRethrowsOriginalException() {
        assertThatThrownBy(() -> service.deleteRecord(5L))
                .isInstanceOf(BizException.class)
                .hasMessage(ErrorCode.STATE_CHANGED.getDefaultMessage());

        OperationLogEntry entry = captured();
        assertThat(entry.getResult()).isEqualTo("FAIL");
        assertThat(entry.getBizId()).isEqualTo(5L);
        assertThat(entry.getTargetName()).isNull();
        assertThat(entry.getExtraText()).startsWith("40903");
    }

    @Test
    void badExpressionDoesNotBreakBusiness() {
        assertThat(service.badExpression(1L)).isEqualTo("ok");

        OperationLogEntry entry = captured();
        assertThat(entry.getBizId()).isNull();
        assertThat(entry.getTargetName()).isEqualTo("固定文字");
    }

    @Test
    void recorderFailureDoesNotBreakBusiness() {
        doThrow(new IllegalStateException("db down")).when(recorder).record(any());

        assertThat(service.badExpression(1L)).isEqualTo("ok");
    }

    @Test
    void writesJsonToOplogLoggerWithoutRecorder() {
        when(provider.getIfAvailable()).thenReturn(null);
        RequestContext.set(new RequestContext.Info(ClientType.ADMIN_WEB, null, "10.0.0.8", "Mozilla/5.0", null));
        AdminContext.set(new AdminContext.LoginAdmin(9L, "吴昊", Set.of("AUDITOR"), Set.of(), 1, "s"));
        Logger logger = (Logger) LoggerFactory.getLogger(OperationLogSupport.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.reject(41L, new RejectRequest("东山猪场", "资质照片不清晰"));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .contains("\"operator_type\":\"ADMIN\"")
                .contains("\"op_type\":\"FARM_AUDIT\"")
                .contains("\"biz_id\":42")
                .contains("\"client_type\":\"ADMIN_WEB\"")
                .contains("\"ip\":\"10.0.0.8\"")
                .contains("\"user_agent\":\"Mozilla/5.0\"");
    }

    private OperationLogEntry captured() {
        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(recorder).record(captor.capture());
        return captor.getValue();
    }

    private static DemoService proxy(ObjectProvider<OperationLogRecorder> provider) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new DemoService());
        factory.setProxyTargetClass(true);
        factory.addAspect(new OpLogAspect(provider));
        return factory.getProxy();
    }
}
