package com.zhimu.server.biz.farm.controller;

import com.zhimu.server.biz.farm.query.InviteQuery;
import com.zhimu.server.biz.farm.request.InviteMemberRequest;
import com.zhimu.server.biz.farm.service.FarmInviteService;
import com.zhimu.server.biz.farm.vo.InviteAcceptVO;
import com.zhimu.server.biz.farm.vo.InviteItemVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** API-F19–F23：邀请列表、发送、同意、拒绝、撤销。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FarmInviteController {

    private final FarmInviteService inviteService;

    /** F19 邀请列表（场长只读，不返回完整手机号） */
    @GetMapping("/farms/{farmId:\\d+}/invites")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<InviteItemVO>> invites(@PathVariable("farmId") Long farmId,
                                                     InviteQuery query) {
        return ApiResponse.ok(inviteService.list(farmId, UserContext.requireUserId(), query));
    }

    /** F20 发送邀请 / 重新邀请 */
    @PostMapping("/farms/{farmId:\\d+}/invites")
    @FarmRoles(FarmRole.OWNER)
    @Idempotent(required = true)
    public ApiResponse<InviteItemVO> send(@PathVariable("farmId") Long farmId,
                                          @RequestBody InviteMemberRequest request) {
        InviteItemVO vo = inviteService.send(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已发送邀请，对方同意后加入", vo);
    }

    /** F23 撤销尚未接受的邀请（所有者/场长） */
    @PostMapping("/farms/{farmId:\\d+}/invites/{inviteId:\\d+}/cancel")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<Object> cancel(@PathVariable("farmId") Long farmId,
                                      @PathVariable("inviteId") Long inviteId,
                                      @RequestBody(required = false) Map<String, String> body) {
        inviteService.cancel(farmId, inviteId, UserContext.requireUserId(),
                body == null ? null : body.get("reason"));
        return new ApiResponse<>(0, "已撤销邀请", null);
    }

    /** F21 同意邀请（被邀请人本人） */
    @PostMapping("/farm-invites/{inviteId:\\d+}/accept")
    public ApiResponse<InviteAcceptVO> accept(@PathVariable("inviteId") Long inviteId) {
        InviteAcceptVO vo = inviteService.accept(inviteId, UserContext.requireUserId());
        return new ApiResponse<>(0, "已加入" + vo.getFarmName(), vo);
    }

    /** F22 拒绝邀请（被邀请人本人，冻结、停用也可拒绝） */
    @PostMapping("/farm-invites/{inviteId:\\d+}/reject")
    public ApiResponse<Object> reject(@PathVariable("inviteId") Long inviteId) {
        inviteService.reject(inviteId, UserContext.requireUserId());
        return new ApiResponse<>(0, "已拒绝邀请", null);
    }
}
