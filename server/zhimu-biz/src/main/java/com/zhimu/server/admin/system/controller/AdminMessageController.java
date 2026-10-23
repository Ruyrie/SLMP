package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.query.AdminMessageQuery;
import com.zhimu.server.admin.system.request.MessageReadRequest;
import com.zhimu.server.admin.system.vo.AdminMessageVO;
import com.zhimu.server.admin.system.vo.MessageReadVO;
import com.zhimu.server.admin.system.vo.UnreadCountVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.framework.security.annotation.NoSessionRenew;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端站内消息（ADM-S40–S42）：只看本人消息；未读数轮询不续期会话。 */
@RestController
@RequestMapping("/admin/v1/messages")
@RequiredArgsConstructor
public class AdminMessageController {

    private final AdminMessageService adminMessageService;

    /** ADM-S40 消息列表：read = ALL（默认）/ UNREAD，tab_counts：all、unread */
    @GetMapping
    public ApiResponse<PageVO<AdminMessageVO>> list(@Valid AdminMessageQuery query) {
        return ApiResponse.ok(adminMessageService.page(query));
    }

    /** ADM-S41 未读数：每 60 秒轮询，@NoSessionRenew 不延长 30 分钟会话 */
    @NoSessionRenew
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> unreadCount() {
        return ApiResponse.ok(new UnreadCountVO(adminMessageService.unreadCount()));
    }

    /** ADM-S42 标记已读：message_ids 或 all 二选一 */
    @PostMapping("/read")
    public ApiResponse<MessageReadVO> markRead(@RequestBody(required = false) MessageReadRequest request) {
        return ApiResponse.ok(new MessageReadVO(adminMessageService.markRead(request)));
    }
}
