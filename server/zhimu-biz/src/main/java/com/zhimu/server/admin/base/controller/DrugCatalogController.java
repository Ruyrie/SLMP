package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.vo.DrugCatalogResultVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-U11 兽药疫苗目录搜索（App module_stock StockApi.searchCatalog / M09 名称面板）。
 * 路径、参数、响应字段与 module_stock 的 CatalogResultVO 和 mock catalog_*.json 一致：
 * GET /api/v1/base/drug-catalog?drug_category=VET_DRUG&keyword=阿莫&size=20。
 * 需要养殖场上下文（X-Farm-Id）；员工与所有者/场长都可调用（3B 任务口径）。
 * U13 巡检项查询 App 未调用，本批不提供 Controller（见联调报告）。
 */
@RestController
@RequestMapping("/api/v1/base")
@RequiredArgsConstructor
public class DrugCatalogController {

    private final DrugCatalogQueryService drugCatalogQueryService;

    /** API-U11 目录搜索；结果不含停用与禁用药，关键词命中禁用药时 banned_hit 有值 */
    @GetMapping("/drug-catalog")
    @RequireFarm
    public ApiResponse<DrugCatalogResultVO> search(
            @RequestParam(value = "drug_category", required = false) String drugCategory,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "species", required = false) String species,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(drugCatalogQueryService.search(keyword, species, drugCategory, page, size));
    }
}
