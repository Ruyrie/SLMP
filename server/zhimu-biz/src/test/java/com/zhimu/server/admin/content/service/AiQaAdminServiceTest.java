package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.entity.AiCorrectionRow;
import com.zhimu.server.admin.content.mapper.AiCorrectionAdminMapper;
import com.zhimu.server.admin.content.mapper.AiQaAdminMapper;
import com.zhimu.server.admin.content.mapper.AiQaReviewMapper;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.QaDetailRow;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.vo.AiQaVos;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQaAdminServiceTest {

    private AiQaAdminMapper qaMapper;
    private AiCorrectionAdminMapper correctionMapper;
    private AiQaReviewMapper reviewMapper;
    private OperationLogService operationLogService;
    private AiQaAdminService service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        AdminContext.set(new AdminContext.LoginAdmin(2L, "周敏", Set.of("SUPER_ADMIN"),
                Set.of("WA-C05:VIEW", "WA-C05:HANDLE", "WA-C04:HANDLE"), 0, "token"));
        qaMapper = mock(AiQaAdminMapper.class);
        correctionMapper = mock(AiCorrectionAdminMapper.class);
        reviewMapper = mock(AiQaReviewMapper.class);
        operationLogService = mock(OperationLogService.class);
        service = new AiQaAdminService(qaMapper, correctionMapper, reviewMapper, mock(ContentLookupMapper.class),
                mock(FileService.class), operationLogService, mock(AdminMessageService.class),
                mock(AdminPermissionService.class), mock(ContentExportService.class));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    @Test
    void publishCorrectionWritesPublishedRowAndChangesHandleStatus() {
        QaDetailRow row = answered();
        when(qaMapper.selectDetail(55L)).thenReturn(row);
        when(correctionMapper.selectPublished(55L)).thenReturn(null);
        when(correctionMapper.insert(any(AiCorrectionRow.class))).thenAnswer(invocation -> {
            AiCorrectionRow correction = invocation.getArgument(0);
            correction.setId(99L);
            return 1;
        });
        AiCorrectionRow saved = correctionRow("同一批次用过多种兽药时按休药期最晚结束的一种计算。", "PUBLISHED", 0);
        when(correctionMapper.selectByIdRow(99L)).thenReturn(saved);
        when(qaMapper.updateHandleStatus(55L, "PENDING", "CORRECTED")).thenReturn(1);
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(com.zhimu.server.admin.content.entity.AiQaReview.class))).thenReturn(1);

        ContentRequests.CorrectionPublishRequest request = new ContentRequests.CorrectionPublishRequest();
        request.setContent("同一批次用过多种兽药时按休药期最晚结束的一种计算。");
        request.setCreateTask(false);
        AiQaVos.CorrectionVO vo = service.publishCorrection(55L, request);

        assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
        assertThat(vo.getCorrectionId()).isEqualTo(99L);
        verify(qaMapper).updateHandleStatus(55L, "PENDING", "CORRECTED");
        verify(qaMapper).recomputeSessionCorrection(10L);
        verify(operationLogService).writeAdminLog(any(com.zhimu.server.admin.system.api.AdminLogCommand.class));
    }

    @Test
    void publishCorrectionRejectsExistingCorrection() {
        when(qaMapper.selectDetail(55L)).thenReturn(answered());
        AiCorrectionRow published = correctionRow("已有更正", "PUBLISHED", 0);
        when(correctionMapper.selectPublished(55L)).thenReturn(published);

        ContentRequests.CorrectionPublishRequest request = new ContentRequests.CorrectionPublishRequest();
        request.setContent("新的更正");
        assertThatThrownBy(() -> service.publishCorrection(55L, request))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode()));
        verify(correctionMapper, never()).insert(any(AiCorrectionRow.class));
    }

    @Test
    void withdrawWithStaleVersionReturns40902() {
        AiCorrectionRow published = correctionRow("已有更正", "PUBLISHED", 3);
        when(correctionMapper.selectPublished(55L)).thenReturn(published);

        ContentRequests.CorrectionWithdrawRequest request = new ContentRequests.CorrectionWithdrawRequest();
        request.setWithdrawReason("说明不准确");
        request.setVersion(2);
        assertThatThrownBy(() -> service.withdrawCorrection(55L, request))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT.getCode()));
        verify(correctionMapper, never()).update(any(), any());
    }

    private static QaDetailRow answered() {
        QaDetailRow row = new QaDetailRow();
        row.setMessageId(55L);
        row.setSessionId(10L);
        row.setFarmId(1001L);
        row.setUserId(10001L);
        row.setQuestionText("氟苯尼考粉用于猪，多久可以出栏？");
        row.setStatus("ANSWERED");
        row.setHandleStatus("PENDING");
        row.setAnswerType("NORMAL");
        row.setRoundNo(1);
        return row;
    }

    private static AiCorrectionRow correctionRow(String content, String status, int version) {
        AiCorrectionRow row = new AiCorrectionRow();
        row.setId(99L);
        row.setMessageId(55L);
        row.setSessionId(10L);
        row.setFarmId(1001L);
        row.setUserId(10001L);
        row.setContent(content);
        row.setStatus(status);
        row.setVersion(version);
        row.setPublishedFlag(true);
        return row;
    }
}
