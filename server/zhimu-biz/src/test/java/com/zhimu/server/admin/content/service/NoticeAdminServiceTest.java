package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.ContentTableInfo;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.mapper.NoticeRecipientMapper;
import com.zhimu.server.admin.content.mapper.NoticeTargetMapper;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.vo.NoticeVos;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.common.service.FileService;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeAdminServiceTest {

    private NoticeMapper noticeMapper;
    private NoticeTargetMapper targetMapper;
    private NoticeRecipientMapper recipientMapper;
    private NoticeAudienceService audienceService;
    private NoticeStatService statService;
    private FileService fileService;
    private OperationLogService operationLogService;
    private MessageService messageService;
    private NoticeAdminService service;

    @BeforeEach
    void setUp() {
        ContentTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        AdminContext.set(new AdminContext.LoginAdmin(2L, "周敏", Set.of("SUPER_ADMIN"),
                Set.of("WA-C01:VIEW", "WA-C01:HANDLE"), 0, "token"));
        noticeMapper = mock(NoticeMapper.class);
        targetMapper = mock(NoticeTargetMapper.class);
        recipientMapper = mock(NoticeRecipientMapper.class);
        audienceService = mock(NoticeAudienceService.class);
        statService = mock(NoticeStatService.class);
        fileService = mock(FileService.class);
        operationLogService = mock(OperationLogService.class);
        messageService = mock(MessageService.class);
        service = new NoticeAdminService(noticeMapper, targetMapper, recipientMapper, audienceService,
                statService, fileService, mock(com.zhimu.server.admin.content.mapper.ContentLookupMapper.class),
                operationLogService, messageService, mock(ContentExportService.class),
                mock(com.zhimu.server.biz.common.api.BizMediaService.class));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    @Test
    void cancelScheduledNotice() {
        Notice scheduled = notice(1L, "SCHEDULED", 2);
        when(noticeMapper.selectAnyById(1L)).thenReturn(scheduled);
        when(noticeMapper.update(any(), any())).thenReturn(1);
        Notice canceled = notice(1L, "CANCELED", 3);
        when(noticeMapper.selectById(1L)).thenReturn(canceled);

        ContentRequests.NoticeReasonRequest request = new ContentRequests.NoticeReasonRequest();
        request.setReason("时间调整");
        request.setVersion(2);
        NoticeVos.NoticeVO vo = service.cancel(1L, request);

        assertThat(vo.getStatus()).isEqualTo("CANCELED");
        assertThat(vo.getVersion()).isEqualTo(3);
        verify(operationLogService).writeAdminLog(any(com.zhimu.server.admin.system.api.AdminLogCommand.class));
    }

    @Test
    void cancelWithStaleVersionReturns40902() {
        Notice scheduled = notice(1L, "SCHEDULED", 3);
        when(noticeMapper.selectAnyById(1L)).thenReturn(scheduled);
        when(noticeMapper.update(any(), any())).thenReturn(0);

        ContentRequests.NoticeReasonRequest request = new ContentRequests.NoticeReasonRequest();
        request.setReason("时间调整");
        request.setVersion(2);
        assertThatThrownBy(() -> service.cancel(1L, request))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT.getCode()));
    }

    @Test
    void revokeKeepsReadCountAndHidesUnreadMessages() {
        Notice sent = notice(1L, "SENT", 1);
        sent.setReadCount(618);
        sent.setDeliveredCount(1524);
        when(noticeMapper.selectAnyById(1L)).thenReturn(sent);
        when(noticeMapper.update(any(), any())).thenReturn(1);
        Notice revoked = notice(1L, "REVOKED", 2);
        revoked.setReadCount(618);
        revoked.setReadBeforeRevoke(618);
        when(noticeMapper.selectById(1L)).thenReturn(revoked);
        when(messageService.hideUnreadByBiz(any(), any(), anyString())).thenReturn(906);

        ContentRequests.NoticeReasonRequest request = new ContentRequests.NoticeReasonRequest();
        request.setReason("内容有误");
        request.setVersion(1);
        NoticeVos.NoticeVO vo = service.revoke(1L, request);

        assertThat(vo.getStatus()).isEqualTo("REVOKED");
        assertThat(vo.getReadBeforeRevoke()).isEqualTo(618);
        verify(messageService).hideUnreadByBiz(any(), eq(1L), eq("NOTICE_REVOKED"));
        verify(recipientMapper).markUnreadHidden(1L);
    }

    @Test
    void revokeDraftReturns40903() {
        Notice draft = notice(1L, "DRAFT", 0);
        when(noticeMapper.selectAnyById(1L)).thenReturn(draft);
        ContentRequests.NoticeReasonRequest request = new ContentRequests.NoticeReasonRequest();
        request.setReason("原因");
        request.setVersion(0);
        assertThatThrownBy(() -> service.revoke(1L, request))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.STATE_CHANGED.getCode()));
        verify(messageService, never()).hideUnreadByBiz(any(), any(), anyString());
    }

    @Test
    void scheduleMustBeAtLeastTenMinutesLater() {
        when(audienceService.resolve(anyString(), any(), any(), any(), any(), eq(true)))
                .thenReturn(new NoticeAudienceService.Result(
                        List.of(new NoticeAudienceService.Recipient(1001L, "王建国", "OWNER", null)), 1));
        ContentRequests.NoticeSaveRequest request = new ContentRequests.NoticeSaveRequest();
        request.setSubmitAction("SCHEDULE");
        request.setTitle("寒潮提醒");
        request.setContent("<p>请做好防寒</p>");
        request.setScopeType("ALL");
        request.setScheduledAt(BeijingTime.now().plusMinutes(5));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                    assertThat(e.getMessage()).contains("10 分钟");
                });
        verify(noticeMapper, never()).insert(any(Notice.class));
    }

    @Test
    void draftOnlyRequiresTitleAndCanSkipIncompleteAudience() {
        when(audienceService.resolve(anyString(), any(), any(), any(), any(), eq(false)))
                .thenReturn(new NoticeAudienceService.Result(List.of(), 0));
        ContentRequests.NoticeSaveRequest request = new ContentRequests.NoticeSaveRequest();
        request.setSubmitAction("DRAFT");
        request.setTitle("系统维护通知");
        request.setScopeType("CONDITION");
        NoticeVos.NoticeVO vo = service.create(request);
        assertThat(vo.getStatus()).isEqualTo("DRAFT");
        verify(noticeMapper).insert(any(Notice.class));
    }

    private static Notice notice(Long id, String status, int version) {
        Notice notice = new Notice();
        notice.setId(id);
        notice.setTitle("通知");
        notice.setStatus(status);
        notice.setVersion(version);
        notice.setLevel("NORMAL");
        notice.setScopeType("ALL");
        notice.setEstAccountCount(10);
        notice.setDeliveredCount(0);
        notice.setReadCount(0);
        return notice;
    }
}
