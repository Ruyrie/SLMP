package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.common.api.AccountFileCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FarmCancellationDataCleanerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountFileCleanupService files = mock(AccountFileCleanupService.class);
    private final FarmCancellationDataCleaner cleaner = new FarmCancellationDataCleaner(jdbc, new AccountProperties(), files);
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 4, 1, 14, 30);

    @Test
    void ineligibleOrTransferredFarmCannotPerformAnyDeleteOrQueueFiles() {
        when(jdbc.queryForList(startsWith("SELECT f.id"), eq(Long.class), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> cleaner.purge(1007L, 10007L, NOW)).hasMessageContaining("not eligible");

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(files);
    }

    @Test
    void dueCleanupKeepsOtherFarmsAndGlobalDataAndQueuesDetachedFilesOnce() {
        when(jdbc.queryForList(startsWith("SELECT f.id"), eq(Long.class), any(), any(), any())).thenReturn(List.of(1007L));
        when(jdbc.queryForList(startsWith("SELECT DISTINCT file_id"), eq(Long.class), eq(1007L), eq(1007L))).thenReturn(List.of(41L, 42L));
        when(jdbc.queryForList(startsWith("SELECT file_id FROM t_biz_report_export"), eq(Long.class), eq(1007L))).thenReturn(List.of(43L));
        when(jdbc.queryForList(startsWith("SELECT id FROM t_file"), eq(Long.class), eq(1007L))).thenReturn(List.of(41L, 44L));
        when(jdbc.update("DELETE FROM t_farm WHERE id = ? AND owner_user_id = ? AND delete_type = 'ACCOUNT_CANCEL'", 1007L, 10007L)).thenReturn(1);

        cleaner.purge(1007L, 10007L, NOW);

        verify(files).queueFarmFiles(eq(10007L), eq(1007L), argThat(ids -> ids.size() == 4 && ids.containsAll(List.of(41L, 42L, 43L, 44L))));
        var order = inOrder(jdbc, files);
        order.verify(jdbc).update("DELETE FROM t_biz_media WHERE farm_id = ? OR (biz_type = 'FARM' AND biz_id = ?)", 1007L, 1007L);
        order.verify(files).queueFarmFiles(eq(10007L), eq(1007L), any());
        order.verify(jdbc).update("DELETE FROM t_farm WHERE id = ? AND owner_user_id = ? AND delete_type = 'ACCOUNT_CANCEL'", 1007L, 10007L);
        for (String table : List.of("t_batch", "t_sale_order", "t_finance_record", "t_feed_record", "t_farm_member")) {
            verify(jdbc).update("DELETE FROM " + table + " WHERE farm_id = ?", 1007L);
        }
        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(statements.capture(), any(Object[].class));
        assertThat(statements.getAllValues()).allSatisfy(sql -> {
            assertThat(sql).doesNotContain("DELETE FROM t_user ", "DELETE FROM t_course ", "DELETE FROM t_ai_knowledge ");
            assertThat(sql).contains("WHERE");
        });
    }

    @Test
    void unapprovedFarmWithAnExistingBatchIsNotConsideredEmpty() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM t_batch WHERE farm_id = ?", Long.class, 1007L)).thenReturn(1L);
        assertThat(cleaner.hasOperationalData(1007L)).isTrue();
        verifyNoInteractions(files);
    }
}
