package com.zhimu.server.biz.today.controller;

import com.zhimu.server.biz.today.query.MessageQuery;
import com.zhimu.server.biz.today.query.UnreadCountQuery;
import com.zhimu.server.biz.today.request.MessageOpenRequest;
import com.zhimu.server.biz.today.request.MessageReadAllRequest;
import com.zhimu.server.biz.today.service.MessageQueryService;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDeleteResult;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.MessagePageVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.biz.today.vo.ReadAllResult;
import com.zhimu.server.biz.today.vo.UnreadCountVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-T12–T18 消息中心（本人，不需要养殖场上下文）。 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageQueryService messageQueryService;

    /** T12 消息列表 */
    @GetMapping
    public ApiResponse<MessagePageVO> list(@Valid MessageQuery query) {
        return ApiResponse.ok(messageQueryService.list(query, UserContext.requireUserId()));
    }

    /** T13 未读数 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> unreadCount(@Valid UnreadCountQuery query) {
        return ApiResponse.ok(messageQueryService.unreadCount(query, UserContext.requireUserId()));
    }

    /** T14 全部标为已读 */
    @PostMapping("/read-all")
    public ApiResponse<ReadAllResult> readAll(@RequestBody(required = false) MessageReadAllRequest request) {
        return ApiResponse.ok(messageQueryService.readAll(request, UserContext.requireUserId()));
    }

    /** T15 打开消息或推送 */
    @PostMapping("/{id:\\d+}/open")
    public ApiResponse<OpenResultVO> open(@PathVariable("id") Long id,
                                          @RequestBody(required = false) MessageOpenRequest request) {
        return ApiResponse.ok(messageQueryService.open(id, request, UserContext.requireUserId()));
    }

    /** T16 消息详情（打开即标为已读） */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<MessageDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(messageQueryService.detail(id, UserContext.requireUserId()));
    }

    /** T17 删除消息 */
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<MessageDeleteResult> delete(@PathVariable("id") Long id) {
        return ApiResponse.ok(messageQueryService.delete(id, UserContext.requireUserId()));
    }

    /** T18 撤销删除（60 秒内） */
    @PostMapping("/{id:\\d+}/restore")
    public ApiResponse<MessageBriefVO> restore(@PathVariable("id") Long id) {
        return ApiResponse.ok(messageQueryService.restore(id, UserContext.requireUserId()));
    }
}
