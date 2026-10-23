package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.herd.query.BatchListQuery;
import com.zhimu.server.biz.herd.query.BatchLookupQuery;
import com.zhimu.server.biz.herd.query.TimelineQuery;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.vo.BatchDetailVO;
import com.zhimu.server.biz.herd.vo.BatchListItemVO;
import com.zhimu.server.biz.herd.vo.BatchLookupVO;
import com.zhimu.server.biz.herd.vo.PenStockVO;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API-L06、L14–L17 批次查询。写接口（L18/L20/L28）在后续提交补入。 */
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    /** L06 批次列表 / 下拉 */
    @GetMapping
    @RequireFarm
    public ApiResponse<PageVO<BatchListItemVO>> list(BatchListQuery query) {
        return ApiResponse.ok(batchService.list(query));
    }

    /** L14 扫码 / 批次号解析（不要求 X-Farm-Id，传了作为当前养殖场比较） */
    @GetMapping("/lookup")
    public ApiResponse<BatchLookupVO> lookup(BatchLookupQuery query,
                                             @RequestHeader(value = "X-Farm-Id", required = false) String farmIdHeader) {
        Long currentFarmId = parseId(farmIdHeader);
        if (currentFarmId == null) {
            currentFarmId = FarmContext.farmId();
        }
        return ApiResponse.ok(batchService.lookup(query, currentFarmId));
    }

    /** L15 批次档案 */
    @GetMapping("/{id:\\d+}")
    @RequireFarm(allowStatus = {com.zhimu.server.common.enums.FarmStatus.NORMAL,
            com.zhimu.server.common.enums.FarmStatus.DISABLED})
    public ApiResponse<BatchDetailVO> detail(@PathVariable("id") Long batchId) {
        return ApiResponse.ok(batchService.detail(batchId));
    }

    /** L16 生产记录时间线 */
    @GetMapping("/{id:\\d+}/timeline")
    @RequireFarm
    public ApiResponse<PageVO<TimelineItemVO>> timeline(@PathVariable("id") Long batchId, TimelineQuery query) {
        return ApiResponse.ok(batchService.timeline(batchId, query));
    }

    /** L17 批次各栏舍在栏数 */
    @GetMapping("/{id:\\d+}/pen-stocks")
    @RequireFarm
    public ApiResponse<List<PenStockVO>> penStocks(@PathVariable("id") Long batchId) {
        return ApiResponse.ok(batchService.penStocks(batchId));
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
