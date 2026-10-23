package com.zhimu.server.biz.farm.controller;

import com.zhimu.server.biz.farm.request.CreateTransferRequest;
import com.zhimu.server.biz.farm.service.FarmTransferService;
import com.zhimu.server.biz.farm.vo.TransferAcceptVO;
import com.zhimu.server.biz.farm.vo.TransferPageVO;
import com.zhimu.server.biz.farm.vo.TransferVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-F28–F32：转让页、发起、接受、拒绝、撤回。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FarmTransferController {

    private final FarmTransferService transferService;

    /** F28 转让页数据 */
    @GetMapping("/farms/{farmId:\\d+}/transfer")
    @FarmRoles(FarmRole.OWNER)
    public ApiResponse<TransferPageVO> page(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(transferService.page(farmId, UserContext.requireUserId()));
    }

    /** F29 发起转让 */
    @PostMapping("/farms/{farmId:\\d+}/transfers")
    @FarmRoles(FarmRole.OWNER)
    @RequireFarm(allowStatus = {FarmStatus.NORMAL, FarmStatus.DISABLED})
    @Idempotent(required = true)
    public ApiResponse<TransferVO> create(@PathVariable("farmId") Long farmId,
                                          @RequestBody CreateTransferRequest request) {
        TransferVO vo = transferService.create(farmId, UserContext.requireUserId(), request);
        return new ApiResponse<>(0, "已向" + vo.getToName() + "发送转让请求，对方确认后生效", vo);
    }

    /** F30 接受转让（受让场长本人） */
    @PostMapping("/farm-transfers/{transferId:\\d+}/accept")
    public ApiResponse<TransferAcceptVO> accept(@PathVariable("transferId") Long transferId) {
        TransferAcceptVO vo = transferService.accept(transferId, UserContext.requireUserId());
        return new ApiResponse<>(0, "你已成为" + vo.getFarmName() + "的所有者", vo);
    }

    /** F31 拒绝转让（受让场长本人，冻结也可拒绝） */
    @PostMapping("/farm-transfers/{transferId:\\d+}/reject")
    public ApiResponse<Object> reject(@PathVariable("transferId") Long transferId) {
        transferService.reject(transferId, UserContext.requireUserId());
        return new ApiResponse<>(0, "已拒绝接收该养殖场的所有权", null);
    }

    /** F32 撤回转让 */
    @PostMapping("/farm-transfers/{transferId:\\d+}/cancel")
    public ApiResponse<TransferVO> cancel(@PathVariable("transferId") Long transferId) {
        TransferVO vo = transferService.cancel(transferId, UserContext.requireUserId());
        return new ApiResponse<>(0, "已撤回转让请求", vo);
    }
}
