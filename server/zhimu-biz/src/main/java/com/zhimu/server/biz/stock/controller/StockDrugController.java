package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.request.DrugSaveRequest;
import com.zhimu.server.biz.stock.request.StockSettingRequest;
import com.zhimu.server.biz.stock.service.StockDrugService;
import com.zhimu.server.biz.stock.service.StockRequestService;
import com.zhimu.server.biz.stock.service.StockSettingService;
import com.zhimu.server.biz.stock.vo.DrugArchiveListResult;
import com.zhimu.server.biz.stock.vo.DrugDetailVO;
import com.zhimu.server.biz.stock.vo.DrugOptionListResult;
import com.zhimu.server.biz.stock.vo.DrugStockOverviewVO;
import com.zhimu.server.biz.stock.vo.RequestStatusVO;
import com.zhimu.server.biz.stock.vo.StockSettingVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-M24 药苗概览、M34–M38 药品档案、M40/M41 临期天数、M42 提交结果（所有者、场长）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockDrugController {

    private final StockDrugService drugService;
    private final StockSettingService settingService;
    private final StockRequestService requestService;

    /** M24 兽药疫苗概览 */
    @GetMapping("/drug-stock/overview")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugStockOverviewVO> overview() {
        return ApiResponse.ok(drugService.overview());
    }

    /** M34 药品档案列表（不分页） */
    @GetMapping("/drugs")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugArchiveListResult> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category) {
        return ApiResponse.ok(drugService.list(keyword, category));
    }

    /** M35 药品下拉 */
    @GetMapping("/drugs/options")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugOptionListResult> options(
            @RequestParam(value = "purpose", defaultValue = "INBOUND") String purpose,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(drugService.options(purpose, keyword));
    }

    /** M36 药品档案详情 */
    @GetMapping("/drugs/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(drugService.detail(id));
    }

    /** M37 新增药品档案 */
    @PostMapping("/drugs")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugDetailVO> create(@Valid @RequestBody DrugSaveRequest request) {
        DrugDetailVO vo = drugService.create(request);
        return new ApiResponse<>(0, "已新增药品档案：" + vo.getName(), vo);
    }

    /** M38 修改药品档案 */
    @PutMapping("/drugs/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugDetailVO> update(@PathVariable("id") Long id,
                                            @Valid @RequestBody DrugSaveRequest request) {
        DrugDetailVO vo = drugService.update(id, request);
        return new ApiResponse<>(0, "已保存" + vo.getName() + "的档案", vo);
    }

    /** M40 读取临期天数 */
    @GetMapping("/stock-settings")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockSettingVO> getSetting() {
        return ApiResponse.ok(settingService.get());
    }

    /** M41 保存临期天数 */
    @PutMapping("/stock-settings")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<StockSettingVO> saveSetting(@Valid @RequestBody StockSettingRequest request) {
        return new ApiResponse<>(0, "已保存临期天数设置", settingService.save(request));
    }

    /** M42 查询提交结果（request_id 即提交时的幂等键） */
    @GetMapping("/stock-requests/{request_id}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<RequestStatusVO> requestStatus(@PathVariable("request_id") String requestId) {
        return ApiResponse.ok(requestService.status(requestId));
    }
}
