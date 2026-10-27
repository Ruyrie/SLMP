package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.support.AccountPersonalDataGuard;
import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.mapper.*;
import com.zhimu.server.biz.ai.service.impl.AiRoundService;
import com.zhimu.server.biz.ai.support.AiRound;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.convert.ReminderSettingConvert;
import com.zhimu.server.biz.today.mapper.*;
import com.zhimu.server.biz.today.service.impl.*;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import com.zhimu.server.integration.push.PushClient;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalWriteCancellationTest {
    private final UserMapper users = mock(UserMapper.class);
    private final AccountPersonalDataGuard guard = new AccountPersonalDataGuard(users);

    private void cancelled() {
        User user = new User(); user.setStatus("CANCELLED");
        when(users.selectByIdForUpdate(100L)).thenReturn(user);
    }

    @Test void lateAiResponseCannotRestoreMessagesOrCitations() {
        cancelled();
        AiSessionMapper sessions = mock(AiSessionMapper.class);
        AiMessageMapper messages = mock(AiMessageMapper.class);
        AiCitationMapper citations = mock(AiCitationMapper.class);
        AiRoundService service = new AiRoundService(guard, sessions, messages, citations, mock(BizMediaService.class));
        AiRound round = new AiRound(1L,100L,2L,3L,1,false,"NONE","question",List.of(),null,null,null,null,null);
        service.finish(round,"GENERAL","answer",List.of(new AiCitation()),"model",1,1,20,"OTHER");
        service.fail(round,"ERROR",500,"failure","partial");
        verifyNoInteractions(sessions, messages, citations);
        verify(users, times(2)).selectByIdForUpdate(100L);
    }

    @Test void lateReminderGetCannotRecreateDefaults() {
        cancelled();
        ReminderSettingMapper settings = mock(ReminderSettingMapper.class);
        ReminderSettingServiceImpl service = new ReminderSettingServiceImpl(guard, settings, new ReminderSettingConvert());
        assertThatThrownBy(() -> service.get(100L)).isInstanceOf(BizException.class);
        verifyNoInteractions(settings);
    }

    @Test void notificationsSkipCancelledRecipientsWithoutAbortingCaller() {
        cancelled();
        MessageMapper messages = mock(MessageMapper.class);
        PushRecordMapper records = mock(PushRecordMapper.class);
        ReminderSettingMapper settings = mock(ReminderSettingMapper.class);
        PushClient push = mock(PushClient.class);
        DefaultMessageService service = new DefaultMessageService(guard,messages,records,settings,
                mock(FarmQueryService.class),mock(FarmMemberLoader.class),push);
        MessageCommand command = MessageCommand.builder().receiverUserId(100L).build();
        service.send(command);
        verifyNoInteractions(messages, records, settings, push);
    }
}
