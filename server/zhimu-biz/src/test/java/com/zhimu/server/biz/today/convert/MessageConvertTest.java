package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.common.vo.FarmBriefVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 消息 date_group、合并摘要、邀请动作与详情 payload。 */
class MessageConvertTest {

    private final TodoMapper todoMapper = mock(TodoMapper.class);
    private final MessageConvert convert = new MessageConvert(todoMapper);
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
        MessageBriefVO vo = convert.toBrief(invite, FarmBriefVO.builder().farmId(1004L).name("红山牛场").build(),
                today, now);

        assertThat(vo.getAction()).isNotNull();
        assertThat(vo.getAction().getActionType()).isEqualTo("INVITE");
        assertThat(vo.getAction().getActionId()).isEqualTo(7001L);
        assertThat(vo.getAction().getStatus()).isEqualTo("PENDING");
        assertThat(vo.getAction().getCanAccept()).isTrue();
        assertThat(vo.getAction().getRoleText()).isEqualTo("员工");
    }

    @Test
    void expiredInviteCannotAccept() {
        Message invite = message(504L, LocalDateTime.of(2026, 10, 19, 7, 40), null, null);
        invite.setCategory("MEMBER");
        invite.setMsgType("FARM_INVITE");
        invite.setExtraJson("{\"status\":\"PENDING\",\"expire_at\":\"2026-10-18 07:40:00\"}");
        MessageBriefVO vo = brief(invite);
        assertThat(vo.getAction().getCanAccept()).isFalse();
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
