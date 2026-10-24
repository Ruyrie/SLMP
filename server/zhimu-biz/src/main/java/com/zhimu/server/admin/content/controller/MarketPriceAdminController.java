package com.zhimu.server.admin.content.controller;

import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.service.MarketPriceAdminService;
import com.zhimu.server.admin.content.service.MarketPriceImportService;
import com.zhimu.server.admin.content.vo.MarketPriceAdminVos;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 管理端-02 WA-C02 市场行情发布（ADM-C11–C22）。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
@AdminPermission("WA-C02:VIEW")
public class MarketPriceAdminController {

    private final MarketPriceAdminService service;
    private final MarketPriceImportService importService;

    /** ADM-C15 行情导入模板（xlsx）。 */
    @GetMapping("/market-prices/import-template")
    @AdminPermission("WA-C02:HANDLE")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] data = importService.template();
        String fileName = "行情导入模板.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    /** ADM-C15 行情导入：整文件事务，任一行失败回滚并按行返回错误。 */
    @PostMapping("/market-prices/import")
    @Idempotent
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<MarketPriceAdminVos.ImportResultVO> importPrices(
            @RequestParam("file") MultipartFile file) {
        MarketPriceAdminVos.ImportResultVO result = importService.importXlsx(file);
        return new ApiResponse<>(0, "已导入 " + result.getSuccess() + " 行行情", result);
    }

    /** ADM-C11 某日价格表与分段计数 */
    @GetMapping("/market-prices")
    public ApiResponse<PageVO<MarketPriceAdminVos.RowVO>> list(@Valid ContentQueries.MarketPriceQuery query) {
        return ApiResponse.ok(service.list(query));
    }

    /** ADM-C12 未发布清单与地区进度 */
    @GetMapping("/market-prices/progress")
    public ApiResponse<MarketPriceAdminVos.ProgressVO> progress(@Valid ContentQueries.MarketDateQuery query) {
        return ApiResponse.ok(service.progress(query));
    }

    /** ADM-C13 按地区录入表 */
    @GetMapping("/market-prices/entry-form")
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<MarketPriceAdminVos.EntryFormVO> entryForm(@Valid ContentQueries.EntryFormQuery query) {
        return ApiResponse.ok(service.entryForm(query));
    }

    /** ADM-C14 批量发布与提交复核 */
    @PostMapping("/market-prices/batch-publish")
    @Idempotent(required = true)
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<MarketPriceAdminVos.BatchPublishVO> batchPublish(
            @RequestBody @Valid ContentRequests.BatchPublishRequest request) {
        MarketPriceAdminVos.BatchPublishVO vo = service.batchPublish(request);
        String message = vo.getReviewCount() > 0
                ? "已发布 " + vo.getPublishedCount() + " 项，" + vo.getReviewCount() + " 项等待复核"
                : "已发布 " + vo.getPublishedCount() + " 项";
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C16 价格详情与复核信息 */
    @GetMapping("/market-prices/{id:\\d+}")
    public ApiResponse<MarketPriceAdminVos.DetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** ADM-C17 复核新价或更正 */
    @PostMapping("/market-prices/{id:\\d+}/review")
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<MarketPriceAdminVos.DetailVO> review(@PathVariable("id") Long id,
                                                            @RequestBody ContentRequests.PriceReviewRequest request) {
        MarketPriceAdminVos.DetailVO vo = service.review(id, request);
        String message;
        if ("RETURN".equals(request == null ? null : request.getResult())) {
            message = "已退回" + (vo.getEnteredByName() == null ? "录入人" : vo.getEnteredByName()) + "修改";
        } else {
            message = "已复核通过并发布";
        }
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-C18 更正历史价格 */
    @PostMapping("/market-prices/{id:\\d+}/corrections")
    @Idempotent(required = true)
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<MarketPriceAdminVos.CorrectionResultVO> correct(
            @PathVariable("id") Long id,
            @RequestBody ContentRequests.PriceCorrectionRequest request) {
        MarketPriceAdminVos.CorrectionResultVO vo = service.correct(id, request);
        String message = "PENDING_REVIEW".equals(vo.getStatus())
                ? "更正后涨跌超过 20%，已提交复核"
                : "已更正 " + DateTimeFormatter.ofPattern("M月d日").format(vo.getPriceDate()) + "的价格";
        return new ApiResponse<>(0, message, vo);
    }

    /** 当天撤销已发布价格（管理端补充操作，ADM-C22） */
    @PostMapping("/market-prices/{id:\\d+}/revoke")
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<MarketPriceAdminVos.DetailVO> revoke(@PathVariable("id") Long id,
                                                            @RequestBody(required = false) ContentRequests.MarketRevokeRequest request) {
        return new ApiResponse<>(0, "已撤销当天的价格", service.revoke(id, request));
    }

    /** ADM-C19 组合近 90 天价格 */
    @GetMapping("/market-prices/history")
    public ApiResponse<List<MarketPriceAdminVos.HistoryItemVO>> history(
            @Valid ContentQueries.MarketHistoryQuery query) {
        return ApiResponse.ok(service.history(query));
    }

    /** ADM-C20 发布配置 */
    @GetMapping("/market-price-configs")
    public ApiResponse<List<MarketPriceAdminVos.ConfigVO>> configs(
            @Valid ContentQueries.MarketConfigQuery query) {
        return ApiResponse.ok(service.configs(query.getProvinceCode()));
    }

    /** ADM-C21 保存发布配置（超级管理员） */
    @PutMapping("/market-price-configs")
    @AdminPermission("WA-C02:HANDLE")
    public ApiResponse<List<MarketPriceAdminVos.ConfigVO>> saveConfigs(
            @RequestBody ContentRequests.MarketConfigSaveRequest request) {
        return new ApiResponse<>(0, "已保存发布配置", service.saveConfigs(request));
    }

    /** 供前端判断是否显示“修改发布配置” */
    @GetMapping("/market-price-configs/permission")
    public ApiResponse<Boolean> configPermission() {
        AdminContext.LoginAdmin admin = AdminContext.get();
        return ApiResponse.ok(admin != null && admin.isSuperAdmin());
    }
}
