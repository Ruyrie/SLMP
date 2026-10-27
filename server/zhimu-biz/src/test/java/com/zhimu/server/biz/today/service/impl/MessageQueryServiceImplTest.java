package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.today.TodayTableInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.today.convert.MessageConvert;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.mapper.MessageMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.query.MessageQuery;
import com.zhimu.server.biz.today.request.MessageReadAllRequest;
import com.zhimu.server.biz.today.support.TodoLinks;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.MessagePageVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.biz.today.vo.ReadAllResult;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** API-T12–T18：分页与 date_group、全部已读、打开不跳转、40401 与 40903。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageQueryServiceImplTest {

    @Mock private MessageMapper messageMapper;
    @Mock private TodoMapper todoMapper;
    @Mock private FarmInviteMapper inviteMapper;
    @Mock private FarmTransferMapper transferMapper;
    @Mock private FarmQueryService farmQueryService;
    @Mock private FarmMemberLoader memberLoader;
    @Mock private TodoScope todoScope;
    @Mock private TodoLinks todoLinks;
    @Mock private com.zhimu.server.admin.content.service.NoticeMessageQueryService noticeContent;

    private MessageQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        TodayTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZoneId.of("Asia/Shanghai")));
        service = new MessageQueryServiceImpl(messageMapper, todoMapper,
                new MessageConvert(todoMapper, inviteMapper, transferMapper),
                farmQueryService, memberLoader, todoScope, todoLinks, noticeContent);
        when(farmQueryService.listFarmsOfUser(10001L)).thenReturn(List.of(
                FarmBriefVO.builder().farmId(1001L).name("东山猪场").status("NORMAL")
                        .statusText("正常").role("OWNER").build()));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void listReturnsPageWithDateGroupAndFilteredUnread() {
        Message message = message(501L, LocalDateTime.of(2026, 10, 19, 9, 21));
        IPage<Message> page = new Page<>(1, 20);
        page.setRecords(List.of(message));
        page.setTotal(22);
        when(messageMapper.selectPage(any(), any())).thenReturn(page);
        when(messageMapper.selectCount(any())).thenReturn(3L);

        MessageQuery query = new MessageQuery();
        query.setPage(1);
        query.setSize(20);
        query.setUnreadOnly(false);
        MessagePageVO result = service.list(query, 10001L);

        assertThat(result.getTotal()).isEqualTo(22);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getFilteredUnread()).isEqualTo(3);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().getFirst().getDateGroup()).isEqualTo("TODAY");
        assertThat(result.getList().getFirst().getFarm().getName()).isEqualTo("东山猪场");
    }

    @Test
    void listMarksAlreadyReadNoticeRevokedFromCurrentNoticeState() {
        Message notice = message(507L, LocalDateTime.of(2026, 10, 19, 9, 21));
        notice.setMsgType("NOTICE");
        notice.setCategory("NOTICE");
        notice.setFarmId(null);
        notice.setBizId(81L);
        notice.setReadAt(LocalDateTime.of(2026, 10, 19, 9, 22));
        notice.setExtraJson("{\"revoked\":false}");
        IPage<Message> page = new Page<>(1, 20);
        page.setRecords(List.of(notice));
        page.setTotal(1);
        when(messageMapper.selectPage(any(), any())).thenReturn(page);
        when(noticeContent.revokedIds(List.of(81L))).thenReturn(Set.of(81L));

        var result = service.list(new MessageQuery(), 10001L).getList().getFirst();

        assertThat(result.getRead()).isTrue();
        assertThat(result.getRevoked()).isTrue();
        assertThat(result.getSummary()).isEqualTo("该通知已撤回");
    }

    @Test
    void acceptedInvitationShowsHandledInListAndDetailWithoutRewritingTheMessage() {
        Message message = message(220016L, LocalDateTime.of(2026, 10, 19, 9, 21));
        message.setMsgType("FARM_INVITE");
        message.setCategory("MEMBER");
        message.setBizId(6012L);
        message.setExtraJson("{\"invite_id\":6012,\"status\":\"PENDING\"}");
        FarmInvite invite = new FarmInvite();
        invite.setId(6012L);
        invite.setFarmId(message.getFarmId());
        invite.setInviteeUserId(message.getUserId());
        invite.setRole("MANAGER");
        invite.setStatus("ACCEPTED");
        invite.setExpiresAt(BeijingTime.now().plusDays(7));
        when(inviteMapper.selectById(6012L)).thenReturn(invite);
        IPage<Message> page = new Page<>(1, 20);
        page.setRecords(List.of(message));
        page.setTotal(1);
        when(messageMapper.selectPage(any(), any())).thenReturn(page);
        when(messageMapper.selectIncludingDeleted(220016L, 10001L)).thenReturn(message);

        var listed = service.list(new MessageQuery(), 10001L).getList().getFirst();
        var detail = service.detail(220016L, 10001L);

        for (var result : List.of(listed, detail)) {
            assertThat(result.getAction().getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getAction().getStatusText()).isEqualTo("已同意");
            assertThat(result.getAction().getRoleText()).isEqualTo("场长");
            assertThat(result.getAction().getCanAccept()).isFalse();
            assertThat(result.getIconState()).isEqualTo("HANDLED");
        }
        assertThat(message.getExtraJson()).contains("PENDING");
    }

    @Test
    void detailReplacesAlreadyReadNoticeSnapshotWithCurrentRevocation() {
        Message notice = message(507L, LocalDateTime.of(2026, 10, 18, 16, 0));
        notice.setFarmId(null);
        notice.setMsgType("NOTICE");
        notice.setCategory("NOTICE");
        notice.setBizId(81L);
        notice.setReadAt(LocalDateTime.of(2026, 10, 18, 17, 0));
        notice.setExtraJson("{\"revoked\":false,\"content\":\"旧正文\",\"image\":{\"url\":\"expired\"}}");
        when(messageMapper.selectIncludingDeleted(507L, 10001L)).thenReturn(notice);
        when(noticeContent.payload(81L)).thenReturn(Map.of("notice_id", 81L, "revoked", true));

        var result = service.detail(507L, 10001L);

        assertThat(result.getRevoked()).isTrue();
        assertThat(result.getPayload()).containsEntry("notice_id", 81L).doesNotContainKeys("content", "image");
    }

    @Test
    void readAllReturnsUpdatedCountAndLatestUnread() {
        when(messageMapper.update(any(), any())).thenReturn(3);
        when(messageMapper.selectCount(any())).thenReturn(0L);

        ReadAllResult result = service.readAll(new MessageReadAllRequest(), 10001L);

        assertThat(result.getUpdatedCount()).isEqualTo(3);
        assertThat(result.getTotalUnread()).isZero();
    }

    @Test
    void readAllForSelectedFarmIncludesPlatformMessagesAndStaysRestrictedToTheRecipient() {
        MessageReadAllRequest request = new MessageReadAllRequest();
        request.setFarmId(1001L);
        request.setCategory("NOTICE");

        service.readAll(request, 10001L);

        ArgumentCaptor<UpdateWrapper<Message>> captor = updateCaptor();
        verify(messageMapper).update(isNull(), captor.capture());
        UpdateWrapper<Message> update = captor.getValue();
        // 同一括号内的 OR 只扩展到平台通知；用户与品类限制仍位于括号外。
        assertThat(update.getSqlSegment()).contains("user_id =", "category =", "read_at IS NULL")
                .containsPattern("AND \\(farm_id = #\\{[^}]+} OR farm_id IS NULL\\)");
        assertThat(update.getParamNameValuePairs().values()).contains(10001L, 1001L, "NOTICE").doesNotContain(1002L);
    }

    @Test
    void readAllWithoutSelectedFarmIncludesAllFarmsButOnlyTheRecipient() {
        service.readAll(new MessageReadAllRequest(), 10001L);

        ArgumentCaptor<UpdateWrapper<Message>> captor = updateCaptor();
        verify(messageMapper).update(isNull(), captor.capture());
        UpdateWrapper<Message> update = captor.getValue();
        assertThat(update.getSqlSegment()).contains("user_id =", "read_at IS NULL").doesNotContain("farm_id");
        assertThat(update.getParamNameValuePairs().values()).contains(10001L).doesNotContain(1001L, 1002L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<UpdateWrapper<Message>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
    }

    @Test
    void openInviteReturnsNoJump() {
        Message invite = message(504L, LocalDateTime.of(2026, 10, 19, 7, 40));
        invite.setCategory("MEMBER");
        invite.setMsgType("FARM_INVITE");
        invite.setFarmId(1004L);
        when(messageMapper.selectIncludingDeleted(504L, 10001L)).thenReturn(invite);
        when(messageMapper.update(any(), any())).thenReturn(1);
        when(messageMapper.selectCount(any())).thenReturn(2L);

        OpenResultVO result = service.open(504L, null, 10001L);

        assertThat(result.getTargetStatus()).isEqualTo("NO_JUMP");
        assertThat(result.getTotalUnread()).isEqualTo(2);
        assertThat(invite.getReadAt()).isNotNull();
    }

    @Test
    void openNoticeReturnsOkWithoutLink() {
        Message notice = message(507L, LocalDateTime.of(2026, 10, 18, 16, 0));
        notice.setFarmId(null);
        notice.setCategory("NOTICE");
        notice.setMsgType("NOTICE");
        when(messageMapper.selectIncludingDeleted(507L, 10001L)).thenReturn(notice);
        when(messageMapper.update(any(), any())).thenReturn(1);
        when(messageMapper.selectCount(any())).thenReturn(3L);

        OpenResultVO result = service.open(507L, null, 10001L);

        assertThat(result.getTargetStatus()).isEqualTo("OK");
        assertThat(result.getTargetStatusText()).isEqualTo("可以打开");
        assertThat(result.getLink()).isNull();
    }

    @Test
    void openRevokedNoticeReturnsOkWithoutLink() {
        Message notice = message(512L, LocalDateTime.of(2026, 10, 17, 10, 0));
        notice.setFarmId(null);
        notice.setCategory("NOTICE");
        notice.setMsgType("NOTICE");
        notice.setHidden(true);
        notice.setHiddenReason("NOTICE_REVOKED");
        when(messageMapper.selectIncludingDeleted(512L, 10001L)).thenReturn(notice);
        when(messageMapper.update(any(), any())).thenReturn(1);
        when(messageMapper.selectCount(any())).thenReturn(3L);

        OpenResultVO result = service.open(512L, null, 10001L);

        assertThat(result.getTargetStatus()).isEqualTo("OK");
        assertThat(result.getLink()).isNull();
    }

    @Test
    void openOwnerTransferReturnsOkWithoutLink() {
        Message transfer = message(513L, LocalDateTime.of(2026, 10, 16, 18, 30));
        transfer.setFarmId(1002L);
        transfer.setCategory("MEMBER");
        transfer.setMsgType("OWNER_TRANSFER");
        when(messageMapper.selectIncludingDeleted(513L, 10001L)).thenReturn(transfer);
        when(messageMapper.update(any(), any())).thenReturn(1);
        when(messageMapper.selectCount(any())).thenReturn(3L);
        when(memberLoader.load(1002L, 10001L)).thenReturn(new com.zhimu.server.common.context.FarmContext.FarmMember(
                1002L, "西岭鸡场", 10001L, 5002L, com.zhimu.server.common.enums.FarmRole.MANAGER,
                java.util.Set.of(), com.zhimu.server.common.enums.FarmStatus.NORMAL));

        OpenResultVO result = service.open(513L, null, 10001L);

        assertThat(result.getTargetStatus()).isEqualTo("OK");
        assertThat(result.getLink()).isNull();
    }

    @Test
    void openMissingMessageReturns40401() {
        when(messageMapper.selectIncludingDeleted(999L, 10001L)).thenReturn(null);

        assertThatThrownBy(() -> service.open(999L, null, 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void restoreAfterDeadlineReturns40903() {
        Message message = message(503L, LocalDateTime.of(2026, 10, 19, 8, 0));
        message.setDeleted(true);
        message.setDeletedAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        when(messageMapper.selectIncludingDeleted(503L, 10001L)).thenReturn(message);

        assertThatThrownBy(() -> service.restore(503L, 10001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.STATE_CHANGED.getCode());
    }

    @Test
    void restoreWithoutDeleteReturnsBrief() {
        Message message = message(503L, LocalDateTime.of(2026, 10, 19, 8, 0));
        message.setDeleted(false);
        when(messageMapper.selectIncludingDeleted(503L, 10001L)).thenReturn(message);

        assertThat(service.restore(503L, 10001L).getMessageId()).isEqualTo(503L);
    }

    private static Message message(long id, LocalDateTime sortTime) {
        Message message = new Message();
        message.setId(id);
        message.setUserId(10001L);
        message.setFarmId(1001L);
        message.setReceiverRole("OWNER");
        message.setCategory("TODO");
        message.setMsgType("TODO_REMIND");
        message.setLevel("NORMAL");
        message.setTitle("3 号育肥舍 · 猪瘟免疫");
        message.setSummary("18:00 截止");
        message.setMergeCount(1);
        message.setSortTime(sortTime);
        message.setCreatedAt(sortTime);
        message.setHidden(false);
        message.setDeleted(false);
        return message;
    }
}
