package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.vo.AdminMessageVO;
import com.zhimu.server.admin.system.vo.MessageReadVO;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AdminMessageControllerMvcTest {

    private final AdminMessageService messageService = mock(AdminMessageService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new AdminMessageController(messageService)).build();
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void S40消息列表带tab_counts与route_params() throws Exception {
        AdminMessageVO message = AdminMessageVO.builder()
                .messageId(1L).msgType("KNOWLEDGE_TASK_ASSIGNED").msgTypeText("知识补充任务")
                .level("NORMAL").title("你有一个知识补充任务").summary("猪舍熏蒸消毒")
                .routePage("WA-C04").routeParams(Map.of("task_id", 7))
                .read(false).createdAt(LocalDateTime.of(2026, 10, 19, 9, 0)).build();
        PageVO<AdminMessageVO> page = PageVO.of(List.of(message), 1, 1, 20)
                .withTabCounts(Map.of("all", 5, "unread", 3));
        when(messageService.page(any())).thenReturn(page);

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/messages").param("read", "UNREAD"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("tab_counts").get("unread").asInt()).isEqualTo(3);
        assertThat(data.get("list").get(0).get("msg_type_text").asString()).isEqualTo("知识补充任务");
        assertThat(data.get("list").get(0).get("route_params").get("task_id").asInt()).isEqualTo(7);
        assertThat(data.get("list").get(0).get("read").asBoolean()).isFalse();
    }

    @Test
    void S41未读数返回unread_count() throws Exception {
        when(messageService.unreadCount()).thenReturn(12);

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/messages/unread-count"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("unread_count").asInt()).isEqualTo(12);
    }

    @Test
    void S42标记已读返回updated_count() throws Exception {
        when(messageService.markRead(any())).thenReturn(2);

        JsonNode data = TestMvc.json(mvc.perform(post("/admin/v1/messages/read")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message_ids\":[1,2]}"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("updated_count").asInt()).isEqualTo(2);
        verify(messageService).markRead(any());
    }

    @Test
    void S42全部标记已读() throws Exception {
        when(messageService.markRead(any())).thenReturn(5);

        JsonNode data = TestMvc.json(mvc.perform(post("/admin/v1/messages/read")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"all\":true}"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("updated_count").asInt()).isEqualTo(5);
    }
}
