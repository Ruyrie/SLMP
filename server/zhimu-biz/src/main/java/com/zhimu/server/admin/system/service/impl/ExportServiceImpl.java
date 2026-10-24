package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.ExportRequest;
import com.zhimu.server.admin.system.api.ExportService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.vo.ExportVO;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一导出（ADM-S14、S19、S32、R22）：SXSSF 流式写 xlsx，写 t_file（scene=EXPORT、7 天有效、bound=1），
 * 签发 10 分钟下载地址，并写一条 EXPORT 日志。调用方负责条数上限与表头/行数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final int WINDOW_SIZE = 100;
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final FileMapper fileMapper;
    private final ObjectStorage objectStorage;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public ExportVO export(ExportRequest request) {
        if (request == null || request.getHeaders() == null || request.getHeaders().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导出内容为空");
        }
        String fileName = fileName(request.getFileName());
        byte[] content = workbook(request);
        String bucket = objectStorage.privateBucket();
        String objectKey = objectStorage.buildObjectKey(FileScene.EXPORT, "xlsx");
        try {
            objectStorage.put(bucket, objectKey, new ByteArrayInputStream(content), content.length,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (RuntimeException e) {
            log.error("导出文件写入对象存储失败 object_key={}", objectKey, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "导出生成失败，请稍后重试");
        }
        LocalDateTime now = BeijingTime.now();
        FileEntity file = new FileEntity();
        file.setBucket(bucket);
        file.setObjectKey(objectKey);
        file.setOriginalName(fileName);
        file.setFileKind("DOCUMENT");
        file.setScene(FileScene.EXPORT.name());
        file.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        file.setFileExt("xlsx");
        file.setFileSize((long) content.length);
        file.setUploadStatus(UploadStatus.READY.name());
        file.setTranscodeStatus(TranscodeStatus.NONE.name());
        file.setUploaderType("ADMIN");
        file.setClientType("ADMIN_WEB");
        file.setBound(1);
        file.setExpireAt(now.plusDays(7));
        file.setDeleted(0);
        fileMapper.insert(file);
        int rowCount = request.getRowCount() == null
                ? (request.getRows() == null ? 0 : request.getRows().size()) : request.getRowCount();
        String url = objectStorage.presignedGetUrl(bucket, objectKey, DOWNLOAD_TTL, fileName);
        try {
            operationLogService.writeAdminLog(AdminLogCommand.builder()
                    .opType("EXPORT")
                    .opAction("EXPORT")
                    .bizType(request.getBizType())
                    .bizId(request.getBizId())
                    .targetName(fileName)
                    .extraText("导出 " + rowCount + " 条记录；筛选条件："
                            + (request.getFilters() == null ? "无" : request.getFilters()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("导出日志写入失败 file_id={}", file.getId(), e);
        }
        return ExportVO.builder()
                .fileId(file.getId())
                .fileName(fileName)
                .rowCount(rowCount)
                .url(url)
                .expireAt(file.getExpireAt())
                .build();
    }

    private static byte[] workbook(ExportRequest request) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(request.getSheetName() == null ? "导出" : request.getSheetName());
            Row header = sheet.createRow(0);
            List<String> headers = request.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(nullSafe(headers.get(i)));
            }
            if (request.getRows() != null) {
                int rowIndex = 1;
                for (List<Object> values : request.getRows()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int i = 0; i < headers.size(); i++) {
                        Cell cell = row.createCell(i);
                        Object value = values != null && i < values.size() ? values.get(i) : null;
                        cell.setCellValue(value == null ? "" : String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("导出 xlsx 生成失败", e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "导出生成失败，请稍后重试");
        }
    }

    private static String fileName(String base) {
        String name = base == null || base.isBlank() ? "导出_" + BeijingTime.today() : base.trim();
        if (name.contains("/") || name.contains("\\")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导出文件名不正确");
        }
        return name.endsWith(".xlsx") ? name : name + ".xlsx";
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
