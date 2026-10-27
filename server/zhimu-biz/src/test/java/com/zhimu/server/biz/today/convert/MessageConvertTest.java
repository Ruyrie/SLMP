package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 消息 date_group、合并摘要、邀请动作与详情 payload。 */
class MessageConvertTest {

    private final TodoMapper todoMapper = mock(TodoMapper.class);
    private final FarmInviteMapper inviteMapper = mock(FarmInviteMapper.class);
    private final FarmTransferMapper transferMapper = mock(FarmTransferMapper.class);
    private final MessageConvert convert = new MessageConvert(todoMapper, inviteMapper, transferMapper);
    private final LocalDate today = LocalDate.of(2026, 10, 19);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 30);
    private final FarmBriefVO farm = FarmBriefVO.builder().farmId(1001L).name("东山猪场")
            .status("NORMAL").statusText("正常").role("OWNER").build();

    @Test
    void dateGroupCoversTodayYesterdayEarlier() {
        assertThat(brief(message(501L, LocalDateTime.of(2026, 10, 19, 9, 21), null, null))
                .getDateGroup()).isEqualTo("TODAY");
        assertThat(brief(message(505L, LocalDateTime.of(2026, 10, 18, 17, 0), null, null))
                .getDateGroup()).isEqualTo("YESTERDAY");
        assertThat(brief(message(522L, LocalDateTime.of(2026, 9, 20, 10, 0), null, null))
                .getDateGroup()).isEqualTo("EARLIER");
    }

    @Test
    void mergedTodoReminderUsesMergeSummary() {
        Message message = message(503L, LocalDateTime.of(2026, 10, 19, 8, 0), null, null);
        message.setMergeCount(2);
        message.setSummary("18:00 截止");
        MessageBriefVO vo = brief(message);
        assertThat(vo.getSummary()).isEqualTo("已合并 2 次提醒");
        assertThat(vo.getMergeCount()).isEqualTo(2);
        assertThat(vo.getRead()).isFalse();
        assertThat(vo.getDateGroup()).isEqualTo("TODAY");
    }

    @Test
    void inviteActionExposesCanAccept() {
        Message invite = message(504L, LocalDateTime.of(2026, 10, 19, 7, 40), null, null);
        invite.setCategory("MEMBER");
        invite.setMsgType("FARM_INVITE");
        invite.setExtraJson("{\"invite_id\":7001,\"role_text\":\"员工\",\"status\":\"PENDING\","
                + "\"expire_at\":\"2026-10-26 07:40:00\",\"farm_name\":\"红山牛场\"}");
        when(inviteMapper.selectById(7001L)).thenAnswer(ignored -> {
            assertThat(TenantSkip.isSkipped()).isTrue();
            return currentInvite("PENDING", LocalDateTime.of(2026, 10, 26, 7, 40));
        });
        MessageBriefVO vo = convert.toBrief(invite, FarmBriefVO.builder().farmId(1004L).name("红山牛场").build(),
                today, now);

        assertThat(vo.getAction()).isNotNull();
        assertThat(vo.getAction().getActionType()).isEqualTo("INVITE");
        assertThat(vo.getAction().getActionId()).isEqualTo(7001L);
        assertThat(vo.getAction().getStatus()).isEqualTo("PENDING");
        assertThat(vo.getAction().getCanAccept()).isTrue();
        assertThat(vo.getAction().getRoleText()).isEqualTo("员工");
        // App 列表副文字：“员工 · 7 天内有效”
        assertThat(vo.getAction().getStatusText()).isEqualTo("7 天内有效");
        assertThat(TenantSkip.isSkipped()).isFalse();
    }

    @Test
    void transferActionPendingUsesConfirmText() {
        Message transfer = message(513L, LocalDateTime.of(2026, 10, 16, 18, 30), null, null);
        transfer.setCategory("MEMBER");
        transfer.setMsgType("OWNER_TRANSFER");
        transfer.setExtraJson("{\"status\":\"PENDING\",\"role_text\":\"所有者\","
                + "\"expire_at\":\"2026-10-19 18:30:00\"}");
        transfer.setBizId(8001L);
        FarmTransfer current = new FarmTransfer();
        current.setId(8001L);
        current.setFarmId(1001L);
        current.setToUserId(10001L);
        current.setStatus("PENDING");
        current.setExpiresAt(LocalDateTime.of(2026, 10, 19, 18, 30));
        when(transferMapper.selectById(8001L)).thenReturn(current);
        MessageBriefVO vo = brief(transfer);

        assertThat(vo.getAction().getActionType()).isEqualTo("TRANSFER");
        assertThat(vo.getAction().getStatusText()).isEqualTo("待你确认");
        assertThat(vo.getAction().getCanAccept()).isTrue();

        current.setStatus("ACCEPTED");
        MessageBriefVO handled = brief(transfer);
        assertThat(handled.getAction().getStatus()).isEqualTo("ACCEPTED");
        assertThat(handled.getAction().getCanAccept()).isFalse();
        assertThat(handled.getIconState()).isEqualTo("HANDLED");
    }

    @Test
    void revokedNoticeIsMarkedAndKeepsDefaultIcon() {
        Message notice = message(512L, LocalDateTime.of(2026, 10, 17, 10, 0), null, null);
        notice.setCategory("NOTICE");
        notice.setMsgType("NOTICE");
        notice.setHidden(true);
        notice.setHiddenReason("NOTICE_REVOKED");
        notice.setExtraJson("{\"revoked\":true}");
        MessageBriefVO vo = brief(notice);

        assertThat(vo.getRevoked()).isTrue();
        assertThat(vo.getIconState()).isEqualTo("DEFAULT");
    }

    @Test
    void expiredInviteCannotAccept() {
        Message invite = message(504L, LocalDateTime.of(2026, 10, 19, 7, 40), null, null);
        invite.setCategory("MEMBER");
        invite.setMsgType("FARM_INVITE");
        invite.setBizId(7001L);
        invite.setExtraJson("{\"status\":\"PENDING\",\"expire_at\":\"2026-10-18 07:40:00\"}");
        when(inviteMapper.selectById(7001L)).thenReturn(currentInvite("PENDING", now.minusDays(1)));
        MessageBriefVO vo = brief(invite);
        assertThat(vo.getAction().getCanAccept()).isFalse();
        assertThat(vo.getAction().getStatus()).isEqualTo("EXPIRED");
        assertThat(vo.getIconState()).isEqualTo("HANDLED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "REJECTED", "CANCELED", "EXPIRED"})
    void currentInviteStateOverridesPendingMessageSnapshot(String status) {
        Message message = message(504L, now, null, "{\"status\":\"PENDING\"}");
        message.setMsgType("FARM_INVITE");
        message.setBizId(7001L);
        when(inviteMapper.selectById(7001L)).thenReturn(currentInvite(status, now.plusDays(7)));

        MessageBriefVO result = brief(message);

        assertThat(result.getAction().getStatus()).isEqualTo(status);
        assertThat(result.getAction().getCanAccept()).isFalse();
        assertThat(result.getIconState()).isEqualTo("HANDLED");
    }

    @Test
    void missingOrWrongRecipientInviteCannotAdvertiseAnAcceptAction() {
        Message message = message(504L, now, null, "{\"status\":\"PENDING\"}");
        message.setMsgType("FARM_INVITE");
        message.setBizId(7001L);
        assertThat(brief(message).getAction().getCanAccept()).isFalse();

        FarmInvite anotherUsersInvite = currentInvite("PENDING", now.plusDays(7));
        anotherUsersInvite.setInviteeUserId(10991L);
        when(inviteMapper.selectById(7001L)).thenReturn(anotherUsersInvite);
        assertThat(brief(message).getAction().getCanAccept()).isFalse();

        anotherUsersInvite.setInviteeUserId(message.getUserId());
        anotherUsersInvite.setFarmId(10991L);
        assertThat(brief(message).getAction().getCanAccept()).isFalse();
    }

    private static FarmInvite currentInvite(String status, LocalDateTime expiresAt) {
        FarmInvite invite = new FarmInvite();
        invite.setId(7001L);
        invite.setFarmId(1001L);
        invite.setInviteeUserId(10001L);
        invite.setRole("STAFF");
        invite.setStatus(status);
        invite.setExpiresAt(expiresAt);
        return invite;
    }

    @Test
    void detailCarriesPayloadAndOpenResult() {
        Message message = message(507L, LocalDateTime.of(2026, 10, 18, 16, 0), null, null);
        message.setCategory("NOTICE");
        message.setMsgType("NOTICE");
        message.setLevel("IMPORTANT");
        message.setExtraJson("{\"title\":\"秋季重大动物疫病防控提醒\",\"sender_name\":\"智牧通平台\","
                + "\"revoked\":false}");
        MessageBriefVO brief = brief(message);
        OpenResultVO open = new OpenResultVO();
        open.setTargetStatus("OK");
        open.setTargetStatusText("可以打开");
        open.setTotalUnread(3);
        MessageDetailVO detail = convert.toDetail(brief, message, open);

        assertThat(detail.getPayload()).containsEntry("sender_name", "智牧通平台");
        assertThat(detail.getTargetStatus()).isEqualTo("OK");
        assertThat(detail.getTotalUnread()).isEqualTo(3);
        assertThat(detail.getLevelText()).isEqualTo("重要");
        assertThat(detail.getCategoryText()).isEqualTo("平台通知");
    }

    private MessageBriefVO brief(Message message) {
        return convert.toBrief(message, farm, today, now);
    }

    private static Message message(long id, LocalDateTime sortTime, Long todoId, String extra) {
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
        message.setTodoId(todoId);
        message.setExtraJson(extra);
        message.setMergeCount(1);
        message.setSortTime(sortTime);
        message.setCreatedAt(sortTime);
        message.setHidden(false);
        message.setDeleted(false);
        return message;
    }
}
