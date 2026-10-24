package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.query.BannedDrugQuery;
import com.zhimu.server.admin.base.query.DrugCatalogQuery;
import com.zhimu.server.admin.base.request.BannedDrugCreateRequest;
import com.zhimu.server.admin.base.request.BannedDrugUpdateRequest;
import com.zhimu.server.admin.base.request.DrugCatalogSaveRequest;
import com.zhimu.server.admin.base.request.DrugStatusRequest;
import com.zhimu.server.admin.base.request.LotRecallRequest;
import com.zhimu.server.admin.base.service.BannedDrugAdminService;
import com.zhimu.server.admin.base.service.DrugCatalogAdminService;
import com.zhimu.server.admin.base.service.DrugCatalogImportService;
import com.zhimu.server.admin.base.service.DrugLotRecallAdminService;
import com.zhimu.server.admin.base.vo.BannedDrugVO;
import com.zhimu.server.admin.base.vo.BannedHitRowVO;
import com.zhimu.server.admin.base.vo.BannedImpactVO;
import com.zhimu.server.admin.base.vo.DrugCatalogDetailVO;
import com.zhimu.server.admin.base.vo.DrugCatalogRowVO;
import com.zhimu.server.admin.base.vo.DrugImportResultVO;
import com.zhimu.server.admin.base.vo.DrugLotRecallVO;
import com.zhimu.server.admin.base.vo.RecallFarmVO;
import com.zhimu.server.admin.base.vo.RecallPreviewVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
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

/** 管理端-03 WA-D03：ADM-D24–D38 兽药疫苗目录、禁用药、批号停用。 */
@RestController
@RequestMapping("/admin/v1")
@RequiredArgsConstructor
@AdminPermission("WA-D03:VIEW")
public class DrugCatalogAdminController {

    private final DrugCatalogAdminService catalogService;
    private final DrugCatalogImportService catalogImportService;
    private final BannedDrugAdminService bannedService;
    private final DrugLotRecallAdminService recallService;

    /** ADM-D29 目录导入模板（xlsx）。 */
    @GetMapping("/drug-catalog/import-template")
    @AdminPermission("WA-D03:HANDLE")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] data = catalogImportService.template();
        String fileName = "兽药疫苗目录导入模板.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    /** ADM-D29 目录导入：整文件事务，任一行失败回滚并按行返回错误。 */
    @PostMapping("/drug-catalog/import")
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<DrugImportResultVO> importCatalog(@RequestParam("file") MultipartFile file) {
        DrugImportResultVO result = catalogImportService.importXlsx(file);
        return new ApiResponse<>(0, "已导入 " + result.getSuccess() + " 条目录", result);
    }

    /** ADM-D24 目录列表（总表页签计数在 tab_counts） */
    @GetMapping("/drug-catalog")
    public ApiResponse<PageVO<DrugCatalogRowVO>> list(@Valid DrugCatalogQuery query) {
        return ApiResponse.ok(catalogService.list(query));
    }

    /** ADM-D25 药品详情与影响 */
    @GetMapping("/drug-catalog/{id:\\d+}")
    public ApiResponse<DrugCatalogDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(catalogService.detail(id));
    }

    /** ADM-D26 新增药品 */
    @PostMapping("/drug-catalog")
    @Idempotent(required = true)
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<DrugCatalogDetailVO> create(@Valid @RequestBody DrugCatalogSaveRequest request) {
        DrugCatalogDetailVO vo = catalogService.create(request);
        return new ApiResponse<>(0, "已保存“" + vo.getName() + "”", vo);
    }

    /** ADM-D27 编辑药品；休药期改动需 change_reason */
    @PutMapping("/drug-catalog/{id:\\d+}")
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<DrugCatalogDetailVO> update(@PathVariable("id") Long id,
                                                   @Valid @RequestBody DrugCatalogSaveRequest request) {
        DrugCatalogAdminService.UpdateResult result = catalogService.update(id, request);
        return new ApiResponse<>(0, result.message(), result.vo());
    }

    /** ADM-D28 停用 / 启用药品 */
    @PostMapping("/drug-catalog/{id:\\d+}/status")
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<DrugCatalogDetailVO> changeStatus(@PathVariable("id") Long id,
                                                         @Valid @RequestBody DrugStatusRequest request) {
        DrugCatalogDetailVO vo = catalogService.changeStatus(id, request);
        String message = "DISABLED".equals(vo.getStatus())
                ? "已停用" + vo.getName() : "已启用" + vo.getName();
        return new ApiResponse<>(0, message, vo);
    }

    /** ADM-D30 禁用药清单 */
    @GetMapping("/banned-drugs")
    public ApiResponse<PageVO<BannedDrugVO>> bannedList(@Valid BannedDrugQuery query) {
        return ApiResponse.ok(bannedService.list(query));
    }

    /** ADM-D31 列入前影响 */
    @GetMapping("/banned-drugs/impact")
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<BannedImpactVO> bannedImpact(@RequestParam("catalog_id") Long catalogId) {
        return ApiResponse.ok(bannedService.impact(catalogId));
    }

    /** ADM-D32 列入禁用药 */
    @PostMapping("/banned-drugs")
    @Idempotent(required = true)
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<BannedDrugVO> ban(@Valid @RequestBody BannedDrugCreateRequest request) {
        return new ApiResponse<>(0, "已列入禁用药，用户端不能再选择", bannedService.create(request));
    }

    /** ADM-D33 编辑禁用药别名与类别 */
    @PutMapping("/banned-drugs/{id:\\d+}")
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<BannedDrugVO> updateBanned(@PathVariable("id") Long id,
                                                  @Valid @RequestBody BannedDrugUpdateRequest request) {
        return new ApiResponse<>(0, "已保存", bannedService.update(id, request));
    }

    /** ADM-D34 拦截记录 */
    @GetMapping("/banned-drugs/{id:\\d+}/hits")
    public ApiResponse<PageVO<BannedHitRowVO>> hits(@PathVariable("id") Long id,
                                                    @Valid PageQuery query) {
        return ApiResponse.ok(bannedService.hits(id, query));
    }

    /** ADM-D35 停用批号列表 */
    @GetMapping("/drug-lot-recalls")
    public ApiResponse<PageVO<DrugLotRecallVO>> recalls(@Valid PageQuery query) {
        return ApiResponse.ok(recallService.list(query));
    }

    /** ADM-D36 停用前带出有效期与库存 */
    @GetMapping("/drug-lot-recalls/preview")
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<RecallPreviewVO> preview(@RequestParam("catalog_id") Long catalogId,
                                                 @RequestParam("lot_no") String lotNo) {
        return ApiResponse.ok(recallService.preview(catalogId, lotNo));
    }

    /** ADM-D37 停用批号 */
    @PostMapping("/drug-lot-recalls")
    @Idempotent(required = true)
    @AdminPermission("WA-D03:HANDLE")
    public ApiResponse<DrugLotRecallVO> recall(@Valid @RequestBody LotRecallRequest request) {
        DrugLotRecallVO vo = recallService.recall(request);
        return new ApiResponse<>(0, "已停用批号 " + vo.getLotNo() + "，已通知 "
                + vo.getNotifiedFarmCount() + " 个养殖场", vo);
    }

    /** ADM-D38 处理进度 */
    @GetMapping("/drug-lot-recalls/{id:\\d+}/farms")
    public ApiResponse<PageVO<RecallFarmVO>> recallFarms(@PathVariable("id") Long id,
                                                         @Valid PageQuery query) {
        return ApiResponse.ok(recallService.farms(id, query));
    }
}
