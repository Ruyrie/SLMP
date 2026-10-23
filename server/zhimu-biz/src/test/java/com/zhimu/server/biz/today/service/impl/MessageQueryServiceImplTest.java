package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.biz.farm.api.FarmQueryService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** API-T12–T18：分页与 date_group、全部已读、打开不跳转、40401 与 40903。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageQueryServiceImplTest {

    @Mock private MessageMapper messageMapper;
    @Mock private TodoMapper todoMapper;
    @Mock private FarmQueryService farmQueryService;
    @Mock private FarmMemberLoader memberLoader;
    @Mock private TodoScope todoScope;
    @Mock private TodoLinks todoLinks;

    private MessageQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), ZoneId.of("Asia/Shanghai")));
        service = new MessageQueryServiceImpl(messageMapper, todoMapper, new MessageConvert(todoMapper),
                farmQueryService, memberLoader, todoScope, todoLinks);
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
    void readAllReturnsUpdatedCountAndLatestUnread() {
        when(messageMapper.update(any(), any())).thenReturn(3);
        when(messageMapper.selectCount(any())).thenReturn(0L);

        ReadAllResult result = service.readAll(new MessageReadAllRequest(), 10001L);

        assertThat(result.getUpdatedCount()).isEqualTo(3);
        assertThat(result.getTotalUnread()).isZero();
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
