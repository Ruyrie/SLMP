package com.zhimu.server.biz.farm.controller;

import com.zhimu.server.biz.farm.query.MemberQuery;
import com.zhimu.server.biz.farm.query.PenOptionQuery;
import com.zhimu.server.biz.farm.request.UpdateMemberPensRequest;
import com.zhimu.server.biz.farm.request.UpdateMemberRoleRequest;
import com.zhimu.server.biz.farm.service.FarmMemberService;
import com.zhimu.server.biz.farm.vo.MemberChangeResultVO;
import com.zhimu.server.biz.farm.vo.MemberDetailVO;
import com.zhimu.server.biz.farm.vo.MemberItemVO;
import com.zhimu.server.biz.farm.vo.MemberRemoveResultVO;
import com.zhimu.server.biz.farm.vo.MemberSummaryVO;
import com.zhimu.server.biz.farm.vo.PenOptionVO;
import com.zhimu.server.biz.farm.vo.RemovalImpactVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-F15–F18、F23–F27：成员管理与主动退出。 */
@RestController
@RequestMapping("/api/v1/farms")
@RequiredArgsConstructor
public class FarmMemberController {

    private final FarmMemberService memberService;

    /** F15 成员概况 */
    @GetMapping("/{farmId:\\d+}/member-summary")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<MemberSummaryVO> summary(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(memberService.summary(farmId, UserContext.requireUserId()));
    }

    /** F16 成员列表 */
    @GetMapping("/{farmId:\\d+}/members")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<MemberItemVO>> members(@PathVariable("farmId") Long farmId,
                                                     MemberQuery query) {
        return ApiResponse.ok(memberService.list(farmId, UserContext.requireUserId(), query));
    }

    /** F17 成员详情 */
    @GetMapping("/{farmId:\\d+}/members/{memberId:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<MemberDetailVO> member(@PathVariable("farmId") Long farmId,
                                              @PathVariable("memberId") Long memberId) {
        return ApiResponse.ok(memberService.detail(farmId, memberId, UserContext.requireUserId()));
    }

    /** F18 可分配栏舍（不传 member_id 为邀请场景） */
    @GetMapping("/{farmId:\\d+}/pen-options")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<List<PenOptionVO>> penOptions(@PathVariable("farmId") Long farmId,
                                                     PenOptionQuery query) {
        return ApiResponse.ok(memberService.penOptions(farmId, query.getMemberId(),
                UserContext.requireUserId()));
    }

    /** F23 修改角色 */
    @PutMapping("/{farmId:\\d+}/members/{memberId:\\d+}/role")
    @FarmRoles(FarmRole.OWNER)
    public ApiResponse<MemberChangeResultVO> updateRole(@PathVariable("farmId") Long farmId,
                                                        @PathVariable("memberId") Long memberId,
                                                        @RequestBody UpdateMemberRoleRequest request) {
        MemberChangeResultVO vo = memberService.updateRole(farmId, memberId,
                UserContext.requireUserId(), request);
        String roleText = "MANAGER".equals(vo.getMember().getRole()) ? "场长" : "员工";
        return new ApiResponse<>(0, "已把" + vo.getMember().getName() + "改为" + roleText, vo);
    }

    /** F24 调整负责栏舍 */
    @PutMapping("/{farmId:\\d+}/members/{memberId:\\d+}/pens")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<MemberChangeResultVO> updatePens(@PathVariable("farmId") Long farmId,
                                                        @PathVariable("memberId") Long memberId,
                                                        @RequestBody UpdateMemberPensRequest request) {
        MemberChangeResultVO vo = memberService.updatePens(farmId, memberId,
                UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已更新" + vo.getMember().getName() + "的负责栏舍", vo);
    }

    /** F25 移出影响 */
    @GetMapping("/{farmId:\\d+}/members/{memberId:\\d+}/removal-impact")
    @FarmRoles(FarmRole.OWNER)
    public ApiResponse<RemovalImpactVO> removalImpact(@PathVariable("farmId") Long farmId,
                                                      @PathVariable("memberId") Long memberId) {
        return ApiResponse.ok(memberService.removalImpact(farmId, memberId, UserContext.requireUserId()));
    }

    /** F26 移出成员 */
    @DeleteMapping("/{farmId:\\d+}/members/{memberId:\\d+}")
    @FarmRoles(FarmRole.OWNER)
    @RequireFarm(allowStatus = {FarmStatus.NORMAL, FarmStatus.DISABLED})
    public ApiResponse<MemberRemoveResultVO> remove(@PathVariable("farmId") Long farmId,
                                                    @PathVariable("memberId") Long memberId,
                                                    @RequestParam("version") int version) {
        MemberRemoveResultVO vo = memberService.remove(farmId, memberId,
                UserContext.requireUserId(), version);
        return new ApiResponse<>(0, "已把该成员移出养殖场", vo);
    }

    /** F27 主动退出 */
    @PostMapping("/{farmId:\\d+}/quit")
    @FarmRoles({FarmRole.MANAGER, FarmRole.STAFF})
    @RequireFarm(allowStatus = {FarmStatus.NORMAL, FarmStatus.DISABLED, FarmStatus.FROZEN})
    public ApiResponse<Object> quit(@PathVariable("farmId") Long farmId) {
        String name = memberService.quit(farmId, UserContext.requireUserId());
        return new ApiResponse<>(0, "你已退出" + name, null);
    }
}
