package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.mapper.ExportFileMapper;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容运营导出（ADM-C10、C65）：同步生成 xlsx、写 t_file（scene = EXPORT、7 天过期）并返回 10 分钟有效的下载地址。
 * 行数上限 5 万；正式环境应与管理端-04 的异步导出任务统一，当前实现保证接口可用。
 */
@Service("adminContentExportService")
@RequiredArgsConstructor
public class ContentExportService {

    public static final int MAX_ROWS = 50_000;

    private final ObjectStorage objectStorage;
    private final ExportFileMapper fileMapper;

    public record ExportResult(Long fileId, String fileName, String status, String url, Integer rowCount) {
    }

    public ExportResult export(String fileName, String sheetName, List<String> headers, List<List<Object>> rows) {
        if (rows != null && rows.size() > MAX_ROWS) {
            throw com.zhimu.server.admin.content.support.ContentErrors.field("page",
                    "导出超过 5 万行，请缩小筛选范围");
        }
        byte[] bytes = workbook(sheetName, headers, rows);
        String objectKey = objectStorage.buildObjectKey(FileScene.EXPORT, "xlsx");
        String bucket = objectStorage.bucketOf(FileScene.EXPORT);
        String fullName = fileName + ".xlsx";
        objectStorage.put(bucket, objectKey, new ByteArrayInputStream(bytes), bytes.length,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        LocalDateTime now = BeijingTime.now();
        FileEntity file = new FileEntity();
        file.setBucket(bucket);
        file.setObjectKey(objectKey);
        file.setOriginalName(fullName);
        file.setFileKind("DOCUMENT");
        file.setScene(FileScene.EXPORT.name());
        file.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        file.setFileExt("xlsx");
        file.setFileSize((long) bytes.length);
        file.setUploadStatus(UploadStatus.READY.name());
        file.setTranscodeStatus(TranscodeStatus.NONE.name());
        file.setUploaderType("ADMIN");
        file.setClientType("ADMIN_WEB");
        file.setBound(1);
        file.setExpireAt(now.plusDays(7));
        file.setCreatedBy(AdminContext.requireAdminId());
        fileMapper.insert(file);
        String url = objectStorage.presignedGetUrl(bucket, objectKey, Duration.ofMinutes(10), fullName);
        return new ExportResult(file.getId(), fullName, "READY", url, rows == null ? 0 : rows.size());
    }

    private static byte[] workbook(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName == null ? "导出数据" : sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.min(50, Math.max(12, headers.get(i).length() * 2 + 4)) * 256);
            }
            int rowNo = 1;
            if (rows != null) {
                for (List<Object> values : rows) {
                    Row row = sheet.createRow(rowNo++);
                    for (int i = 0; i < values.size() && i < headers.size(); i++) {
                        Cell cell = row.createCell(i);
                        Object value = values.get(i);
                        if (value == null) {
                            cell.setBlank();
                        } else if (value instanceof Number number) {
                            cell.setCellValue(number.doubleValue());
                        } else if (value instanceof Boolean bool) {
                            cell.setCellValue(bool);
                        } else {
                            cell.setCellValue(String.valueOf(value));
                        }
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成导出文件失败", e);
        }
    }
}
