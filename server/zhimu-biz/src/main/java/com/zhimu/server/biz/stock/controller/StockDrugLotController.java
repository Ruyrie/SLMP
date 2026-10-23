package com.zhimu.server.biz.stock.controller;

import com.zhimu.server.biz.stock.service.StockDrugLotService;
import com.zhimu.server.biz.stock.vo.DrugLotDetailVO;
import com.zhimu.server.biz.stock.vo.DrugLotItemVO;
import com.zhimu.server.biz.stock.vo.DrugLotOptionsVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** API-M25–M27 批号列表、详情与选择批号（M27 全部角色共用，均价由 @ManagerView 裁剪）。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockDrugLotController {

    private final StockDrugLotService lotService;

    /** M25 批号列表 */
    @GetMapping("/drug-lots")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<PageVO<DrugLotItemVO>> page(
            @RequestParam(value = "group", defaultValue = "ALL") String group,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(lotService.page(group, category, keyword, page, size));
    }

    /** M27 选择批号：purpose USE / RETURN / VACCINATE / MEDICATE / DISINFECT */
    @GetMapping("/drug-lots/options")
    @RequireFarm
    public ApiResponse<DrugLotOptionsVO> options(
            @RequestParam("drug_id") Long drugId,
            @RequestParam(value = "purpose", defaultValue = "USE") String purpose,
            @RequestParam(value = "need_qty", required = false) BigDecimal needQty) {
        return ApiResponse.ok(lotService.options(drugId, purpose, needQty));
    }

    /** M26 批号详情 */
    @GetMapping("/drug-lots/{id:\\d+}")
    @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
    public ApiResponse<DrugLotDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(lotService.detail(id));
    }
}
