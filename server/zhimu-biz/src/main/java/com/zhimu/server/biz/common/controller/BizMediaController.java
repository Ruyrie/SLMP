package com.zhimu.server.biz.common.controller;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.service.impl.RecordAccessGuard;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.biz.common.vo.BizMediaListVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.CodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

/**
 * API-U08 某条记录的照片和视频列表与来源说明（I09、记录详情；App 不调用，但按管理端-04 2.5.10 定义）。
 */
@RestController
@RequestMapping("/api/v1/biz-media")
@RequiredArgsConstructor
public class BizMediaController {

    private final BizMediaService bizMediaService;
    private final RecordAccessGuard recordAccessGuard;

    @GetMapping
    public ApiResponse<BizMediaListVO> list(@RequestParam("biz_type") String bizType,
                                            @RequestParam("biz_id") Long bizId) {
        BizType type = CodeEnum.of(BizType.class, bizType);
        if (type == null) {
            throw CommonErrors.fieldError("biz_type", "记录类型不正确");
        }
        if (bizId == null || bizId <= 0) {
            throw CommonErrors.fieldError("biz_id", "记录 ID 不正确");
        }
        recordAccessGuard.checkMediaAccess(type, bizId);
        BizMediaListVO vo = BizMediaListVO.builder()
                .bizType(type.name())
                .bizTypeText(ChangeBizTypes.textOf(type))
                .recordDesc(ChangeBizTypes.textOf(type) + " · " + bizId)
                .items(new ArrayList<>(bizMediaService.list(type, bizId)))
                .build();
        return ApiResponse.ok(vo);
    }
}
