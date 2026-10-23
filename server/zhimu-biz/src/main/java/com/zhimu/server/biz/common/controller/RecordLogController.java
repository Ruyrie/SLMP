package com.zhimu.server.biz.common.controller;

import com.zhimu.server.biz.common.api.OperationLogQueryService;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.biz.common.vo.RecordLogVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.CodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * API-U14 某条记录的修改记录（各记录的“查看修改记录”）。
 * 不分页，data 直接是数组，最多返回最近 200 条；没有记录时返回 []，不返回 null。
 */
@RestController
@RequestMapping("/api/v1/record-logs")
@RequiredArgsConstructor
public class RecordLogController {

    public static final int MAX_LIMIT = 200;

    private final OperationLogQueryService operationLogQueryService;

    @GetMapping
    public ApiResponse<List<RecordLogVO>> list(@RequestParam("biz_type") String bizType,
                                               @RequestParam("biz_id") Long bizId,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size) {
        BizType type = CodeEnum.of(BizType.class, bizType);
        if (type == null) {
            throw CommonErrors.fieldError("biz_type", "记录类型不正确");
        }
        if (bizId == null || bizId <= 0) {
            throw CommonErrors.fieldError("biz_id", "记录 ID 不正确");
        }
        List<RecordLogVO> logs = operationLogQueryService.listByBiz(type, bizId, MAX_LIMIT);
        return ApiResponse.ok(logs == null ? new ArrayList<>() : logs);
    }
}
