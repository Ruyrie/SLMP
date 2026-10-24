package com.zhimu.server.admin.content.support;

import com.zhimu.server.admin.content.vo.ContentExportVO;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.common.enums.FileKind;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.TranscodeStatus;
import com.zhimu.server.common.enums.UploadStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 内容运营导出：生成 xlsx，写 t_file（scene = EXPORT、7 天有效），返回 10 分钟下载地址。
 */
@Component("adminContentSupportExportService")
@RequiredArgsConstructor
public class ContentExportService {

    private final FileMapper fileMapper;
    private final ObjectStorage objectStorage;

    public ContentExportVO export(String fileName, String sheetName, List<String> headers,
                                  List<List<Object>> rows, String bizType, Long bizId, String filterText) {
        byte[] data = xlsx(sheetName, headers, rows);
        String name = fileName.endsWith(".xlsx") ? fileName : fileName + ".xlsx";
        String objectKey = "export/" + BeijingTime.today().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + ".xlsx";
        objectStorage.put(objectStorage.privateBucket(), objectKey, new ByteArrayInputStream(data), data.length,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        FileEntity file = new FileEntity();
        file.setBucket(objectStorage.privateBucket());
        file.setObjectKey(objectKey);
        file.setOriginalName(name);
        file.setFileKind(FileKind.DOCUMENT.name());
        file.setScene(FileScene.EXPORT.name());
        file.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        file.setFileExt("xlsx");
        file.setFileSize((long) data.length);
        file.setUploadStatus(UploadStatus.READY.name());
        file.setTranscodeStatus(TranscodeStatus.NONE.name());
        file.setUploaderType("ADMIN");
        file.setClientType("ADMIN_WEB");
        file.setBound(1);
        file.setExpireAt(BeijingTime.now().plusDays(7));
        fileMapper.insert(file);

        ContentExportVO vo = new ContentExportVO();
        vo.setFileId(file.getId());
        vo.setFileName(name);
        vo.setStatus("READY");
        vo.setRowCount(rows == null ? 0 : rows.size());
        vo.setUrl(objectStorage.presignedGetUrl(file.getBucket(), objectKey, Duration.ofMinutes(10), name));
        vo.setExpireAt(BeijingTime.now().plusMinutes(10));
        return vo;
    }

    private static byte[] xlsx(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "导出数据" : sheetName);
            int rowIndex = 0;
            if (headers != null && !headers.isEmpty()) {
                Row header = sheet.createRow(rowIndex++);
                for (int i = 0; i < headers.size(); i++) {
                    header.createCell(i).setCellValue(headers.get(i));
                    sheet.setColumnWidth(i, Math.min(60, Math.max(12, headers.get(i).length() * 2 + 4)) * 256);
                }
            }
            if (rows != null) {
                for (List<Object> values : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int i = 0; i < values.size(); i++) {
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
        } catch (Exception e) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.SERVER_ERROR, "导出失败，请重试");
        }
    }
}
