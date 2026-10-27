package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.service.impl.AccountFileCleanupServiceImpl;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountFileCleanupServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AccountFileCleanupServiceImpl service = new AccountFileCleanupServiceImpl(jdbc,storage,transactions);

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            return sql.contains("JOIN t_account_cancel") || sql.contains("id=? AND deleted=1") ? 1L : 0L;
        });
        when(jdbc.queryForList(contains("FROM t_account_file_cleanup"),eq(100L))).thenReturn(List.of(
                Map.of("id",1L,"file_id",10L,"bucket","private","object_key","avatar/test.jpg")));
    }

    @Test void storageFailureRemainsRetryableAndLaterRetryCompletes() {
        doThrow(new IllegalStateException("provider secret details")).doNothing()
                .when(storage).remove("private","avatar/test.jpg");
        assertThatThrownBy(() -> service.purgeQueued(100L)).hasMessage("Account file cleanup pending");
        verify(jdbc).update(contains("retry_count=retry_count+1"),eq("IllegalStateException"),any(),eq(1L),eq(100L));
        verify(jdbc,never()).update(contains("completed_at"),any(Object[].class));
        service.purgeQueued(100L);
        verify(storage,times(2)).remove("private","avatar/test.jpg");
        verify(jdbc).update(contains("completed_at"),eq("DONE"),any(),any(),eq(1L),eq(100L));
    }

    @Test void activeAccountCannotProcessAnOldQueue() {
        when(jdbc.queryForObject(contains("JOIN t_account_cancel"),eq(Long.class),eq(100L))).thenReturn(0L);
        assertThatThrownBy(() -> service.purgeQueued(100L)).hasMessage("Cancellation account mismatch");
        verifyNoInteractions(storage);
    }

    @Test void sharedStorageObjectIsPreserved() {
        when(jdbc.queryForObject(contains("bucket=? AND object_key=? AND deleted=0"),eq(Long.class),eq("private"),eq("avatar/test.jpg"))).thenReturn(1L);
        service.purgeQueued(100L);
        verifyNoInteractions(storage);
        verify(jdbc).update(contains("completed_at"),eq("SHARED"),any(),any(),eq(1L),eq(100L));
    }
}
