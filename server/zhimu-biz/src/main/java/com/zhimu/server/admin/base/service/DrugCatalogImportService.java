package com.zhimu.server.admin.base.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.DrugAdminSupport;
import com.zhimu.server.admin.base.vo.DrugImportResultVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADM-D29 兽药疫苗目录导入：生成 xlsx 模板、逐行校验并整文件事务写入 t_drug_catalog。
 *
 * <p>模板列：类别、药品名称、规格、单位、生产厂家、批准文号、包装规格、备注。
 * 现有表没有生产厂家/批准文号/备注列，导入时写入 last_change_note（“导入：…”）；
 * 包装规格解析为 pack_unit + pack_size；剂型模板未提供，按类别写默认值，可在 D27 再改。</p>
 */
@Service
@RequiredArgsConstructor
public class DrugCatalogImportService {

    public static final List<String> TEMPLATE_HEADERS = List.of(
            "类别", "药品名称", "规格", "单位", "生产厂家", "批准文号", "包装规格", "备注");

    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 500;
    private static final DataFormatter CELL_FORMAT = new DataFormatter(Locale.CHINA);
    private static final Pattern PACK_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]{1,3})?)\\s*(?:[^/／]*?)\\s*[/／]\\s*([^/／]{1,4})$");
    private static final Pattern NUMBER_PACK_PATTERN = Pattern.compile("^([0-9]+(?:\\.[0-9]{1,3})?)$");

    private final DrugCatalogMapper catalogMapper;
    private final DrugAdminSupport drugSupport;
    private final BaseLogs logs;

    // ===================== 模板 =====================

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("兽药疫苗目录");
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, switch (i) {
                    case 0 -> 16 * 256;
                    case 1, 4, 5 -> 22 * 256;
                    default -> 16 * 256;
                });
            }
            sheet.createFreezePane(0, 1);

            Sheet help = workbook.createSheet("填写说明");
            String[] lines = {
                    "1. 第 1 行为表头，请勿修改列名；从第 2 行开始填写，一次最多 500 行。",
                    "2. 类别：VET_DRUG / VACCINE / DISINFECTANT，也可以填 兽药 / 疫苗 / 消毒剂。",
                    "3. 药品名称、规格、单位必填；同类别同名同规格不能重复，同类别名称也不能重复。",
                    "4. 单位：G / ML / L / DOSE / KG，也可以填 克 / 毫升 / 升 / 头份 / 千克；疫苗必须为头份或羽份。",
                    "5. 包装规格：例如 100 克/袋、10 头份/瓶、10 瓶/箱；只填包装单位时按每包装 1 个记账单位处理。",
                    "6. 生产厂家、批准文号、备注会合并写入目录的“最近修改说明”，不新增表字段。",
                    "7. 剂型模板未提供：兽药记为“未填写”，疫苗记为“疫苗”，消毒剂记为“消毒剂”，导入后可在目录编辑中修改。",
                    "8. 导入不写各畜种用量与休药期；导入后请到目录编辑中补齐后再让用户端选择。",
                    "9. 任一行校验失败，整个文件回滚，接口按行返回错误；修正后重新导入。"
            };
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 110 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVER_ERROR, "模板生成失败，请重试");
        }
    }

    // ===================== 导入 =====================

    @Transactional(rollbackFor = Exception.class)
    public DrugImportResultVO importXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_INVALID, "请选择要导入的 xlsx 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持不超过 2MB 的 xlsx 文件");
        }
        Long adminId = AdminContext.requireAdminId();
        List<DrugImportResultVO.ImportError> errors = new ArrayList<>();
        List<ParsedRow> rows = new ArrayList<>();
        int total = 0;
        int nextSort = nextSortNo();

        try (InputStream in = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BizException(ErrorCode.FILE_INVALID, "只支持按模板填写的 xlsx 文件");
            }
            Sheet sheet = workbook.getSheetAt(0);
            HeaderIndex index = headerIndex(sheet.getRow(0));
            Set<String> fileKeys = new HashSet<>();
            Set<String> fileNames = new HashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlankRow(row)) {
                    continue;
                }
                total++;
                if (total > MAX_ROWS) {
                    throw new BizException(ErrorCode.FILE_INVALID, "每次最多导入 500 行");
                }
                int rowNo = r + 1;
                List<String> rowErrors = new ArrayList<>();
                ParsedRow parsed = parseRow(row, index, rowNo, nextSort, rowErrors);
                if (!rowErrors.isEmpty()) {
                    for (String message : rowErrors) {
                        errors.add(new DrugImportResultVO.ImportError(rowNo, message));
                    }
                    continue;
                }
                String nameKey = parsed.category + "|" + parsed.name;
                if (!fileNames.add(nameKey)) {
                    errors.add(new DrugImportResultVO.ImportError(rowNo,
                            "同类别名称在文件中重复（名称：" + parsed.name + "）"));
                    continue;
                }
                String key = nameKey + "|" + parsed.spec;
                if (!fileKeys.add(key)) {
                    errors.add(new DrugImportResultVO.ImportError(rowNo, "同类别同名同规格的药品在文件中重复"));
                    continue;
                }
                DrugCatalog existing = catalogMapper.selectOne(Wrappers.<DrugCatalog>lambdaQuery()
                        .eq(DrugCatalog::getDrugCategory, parsed.category)
                        .eq(DrugCatalog::getName, parsed.name)
                        .last("LIMIT 1"));
                if (existing != null) {
                    errors.add(new DrugImportResultVO.ImportError(rowNo,
                            parsed.spec.equals(existing.getSpec())
                                    ? "同类别同名同规格的药品已存在"
                                    : "同类别已有同名药品“" + existing.getName() + "”（现有规格："
                                    + existing.getSpec() + "），同类别名称不能重复"));
                    continue;
                }
                rows.add(parsed);
                nextSort++;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持按模板填写的 xlsx 文件，不超过 2MB");
        }

        if (total == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入文件没有数据行",
                    importData(0, 0, List.of()));
        }
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, importMessage(errors),
                    importData(total, failedRows(errors), errors));
        }

        for (ParsedRow parsed : rows) {
            DrugCatalog catalog = new DrugCatalog();
            catalog.setDrugCategory(parsed.category);
            catalog.setName(parsed.name);
            catalog.setDosageForm(parsed.dosageForm);
            catalog.setSpec(parsed.spec);
            catalog.setMeasureUnit(parsed.measureUnit);
            catalog.setPackUnit(parsed.packUnit);
            catalog.setPackSize(parsed.packSize);
            catalog.setVaccineDoseUnit(parsed.vaccineDoseUnit);
            catalog.setDefaultVaccinateMethodCode(null);
            catalog.setConcentration(null);
            catalog.setDisinfectMethodCodes(null);
            catalog.setStatus("ENABLED");
            catalog.setBanned(false);
            catalog.setSortNo(parsed.sortNo);
            catalog.setLastChangeNote(parsed.note);
            catalog.setUpdatedBy(adminId);
            catalog.setVersion(0);
            try {
                catalogMapper.insert(catalog);
            } catch (DuplicateKeyException e) {
                throw BaseErrors.stateChanged("第 " + parsed.rowNo
                        + " 行药品已存在，整个文件已回滚");
            }
        }

        DrugImportResultVO result = new DrugImportResultVO();
        result.setTotal(total);
        result.setSuccess(total);
        result.setFailed(0);
        result.setFileName(file.getOriginalFilename());
        logs.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("BASE_DRUG_CATALOG")
                .opAction("IMPORT")
                .targetName("兽药疫苗目录导入")
                .searchText(file.getOriginalFilename())
                .reason("导入 " + total + " 条目录")
                .extraText("成功 " + total + " 条")
                .after(Map.of("total", total, "success", total))
                .build());
        return result;
    }

    // ===================== 解析 =====================

    private ParsedRow parseRow(Row row, HeaderIndex index, int rowNo, int sortNo,
                               List<String> errors) {
        String category = category(text(row, index.category), errors);
        String name = text(row, index.name);
        if (!StringUtils.hasText(name)) {
            errors.add("药品名称不能为空");
        } else if (name.codePointCount(0, name.length()) > 40) {
            errors.add("药品名称不能超过 40 字");
        } else {
            try {
                // 与 D26 同口径：目录名称不能与禁用药名称或别名相同
                drugSupport.assertNameNotBanned(name);
            } catch (BizException e) {
                errors.add(e.getMessage());
            }
        }
        String spec = text(row, index.spec);
        if (!StringUtils.hasText(spec)) {
            errors.add("规格不能为空");
        } else if (spec.codePointCount(0, spec.length()) > 20) {
            errors.add("规格不能超过 20 字");
        }
        Unit unit = unit(text(row, index.unit), category, errors);
        Pack pack = pack(text(row, index.pack), errors);
        String manufacturer = text(row, index.manufacturer);
        if (manufacturer != null && manufacturer.codePointCount(0, manufacturer.length()) > 100) {
            errors.add("生产厂家不能超过 100 字");
        }
        String approvalNo = text(row, index.approvalNo);
        if (approvalNo != null && approvalNo.codePointCount(0, approvalNo.length()) > 50) {
            errors.add("批准文号不能超过 50 字");
        }
        String remark = text(row, index.remark);
        if (remark != null && remark.codePointCount(0, remark.length()) > 100) {
            errors.add("备注不能超过 100 字");
        }
        if (!errors.isEmpty()) {
            return null;
        }
        String dosageForm = switch (category) {
            case "VACCINE" -> "疫苗";
            case "DISINFECTANT" -> "消毒剂";
            default -> "未填写";
        };
        String vaccineDoseUnit = "VACCINE".equals(category) ? vaccineDoseUnit(text(row, index.unit)) : null;
        String note = note(manufacturer, approvalNo, pack.raw, remark);
        return new ParsedRow(rowNo, category, name, spec, unit.code, pack.unit, pack.size,
                vaccineDoseUnit, dosageForm, note, sortNo);
    }

    private static String category(String raw, List<String> errors) {
        if (!StringUtils.hasText(raw)) {
            errors.add("类别不能为空");
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "VET_DRUG", "兽药" -> "VET_DRUG";
            case "VACCINE", "疫苗" -> "VACCINE";
            case "DISINFECTANT", "消毒剂" -> "DISINFECTANT";
            default -> {
                errors.add("类别只能是 VET_DRUG / VACCINE / DISINFECTANT（或兽药 / 疫苗 / 消毒剂）");
                yield null;
            }
        };
    }

    private static Unit unit(String raw, String category, List<String> errors) {
        if (!StringUtils.hasText(raw)) {
            errors.add("单位不能为空");
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        String code = switch (value) {
            case "G", "克", "公克" -> "G";
            case "KG", "千克", "公斤" -> "KG";
            case "ML", "毫升" -> "ML";
            case "L", "升" -> "L";
            case "DOSE", "头份", "羽份", "剂", "头份/瓶", "羽份/瓶" -> "DOSE";
            default -> null;
        };
        if (code == null) {
            errors.add("单位不正确，请填写 G / ML / L / DOSE / KG（或 克 / 毫升 / 升 / 头份 / 千克）");
            return null;
        }
        if ("VACCINE".equals(category) && !"DOSE".equals(code)) {
            errors.add("疫苗记账单位须为头份或羽份");
            return null;
        }
        return new Unit(code);
    }

    private static String vaccineDoseUnit(String rawUnit) {
        String value = rawUnit == null ? "" : rawUnit.trim();
        if (value.contains("羽份") || "DOSE_PER_BIRD".equalsIgnoreCase(value)) {
            return "DOSE_PER_BIRD";
        }
        if (value.contains("毫升") || "ML".equalsIgnoreCase(value)) {
            return "ML_PER_HEAD";
        }
        return "DOSE_PER_HEAD";
    }

    private static Pack pack(String raw, List<String> errors) {
        if (!StringUtils.hasText(raw)) {
            return new Pack("袋", BigDecimal.ONE, null);
        }
        String value = raw.trim();
        Matcher matcher = PACK_PATTERN.matcher(value);
        if (matcher.matches()) {
            BigDecimal size = new BigDecimal(matcher.group(1));
            String unit = matcher.group(2).trim();
            if (!validPack(size, unit, errors)) {
                return null;
            }
            return new Pack(unit, size, value);
        }
        Matcher number = NUMBER_PACK_PATTERN.matcher(value);
        if (number.matches()) {
            BigDecimal size = new BigDecimal(number.group(1));
            if (!validPack(size, "袋", errors)) {
                return null;
            }
            return new Pack("袋", size, value);
        }
        if (value.codePointCount(0, value.length()) <= 4) {
            return new Pack(value, BigDecimal.ONE, value);
        }
        errors.add("包装规格格式不正确，例如 100 克/袋、10 头份/瓶");
        return null;
    }

    private static boolean validPack(BigDecimal size, String unit, List<String> errors) {
        if (size.signum() <= 0) {
            errors.add("每个包装折合数量必须大于 0");
            return false;
        }
        if (size.stripTrailingZeros().scale() > 3) {
            errors.add("每个包装折合数量最多 3 位小数");
            return false;
        }
        if (unit.isEmpty() || unit.codePointCount(0, unit.length()) > 4) {
            errors.add("包装单位需要 1–4 个字");
            return false;
        }
        return true;
    }

    private static String note(String manufacturer, String approvalNo, String packRaw, String remark) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(manufacturer)) {
            parts.add("厂家 " + manufacturer.trim());
        }
        if (StringUtils.hasText(approvalNo)) {
            parts.add("批准文号 " + approvalNo.trim());
        }
        if (StringUtils.hasText(packRaw)) {
            parts.add("包装 " + packRaw.trim());
        }
        if (StringUtils.hasText(remark)) {
            parts.add("备注 " + remark.trim());
        }
        if (parts.isEmpty()) {
            return "导入目录";
        }
        String value = "导入：" + String.join("；", parts);
        return value.codePointCount(0, value.length()) > 100 ? value.substring(0, 100) : value;
    }

    private int nextSortNo() {
        DrugCatalog latest = catalogMapper.selectOne(Wrappers.<DrugCatalog>lambdaQuery()
                .orderByDesc(DrugCatalog::getSortNo)
                .orderByDesc(DrugCatalog::getId)
                .last("LIMIT 1"));
        return latest == null || latest.getSortNo() == null ? 1 : latest.getSortNo() + 1;
    }

    // ===================== Excel 单元格 =====================

    private static HeaderIndex headerIndex(Row header) {
        Map<String, Integer> map = new HashMap<>();
        if (header != null) {
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String name = CELL_FORMAT.formatCellValue(header.getCell(i)).trim();
                if (!name.isEmpty()) {
                    map.putIfAbsent(name, i);
                }
            }
        }
        Integer category = firstIndex(map, "类别", "药品类别", "类型");
        Integer name = firstIndex(map, "药品名称", "通用名称", "名称");
        Integer spec = firstIndex(map, "规格");
        Integer unit = firstIndex(map, "单位", "记账单位", "库存单位");
        List<String> missing = new ArrayList<>();
        if (category == null) {
            missing.add("类别");
        }
        if (name == null) {
            missing.add("药品名称");
        }
        if (spec == null) {
            missing.add("规格");
        }
        if (unit == null) {
            missing.add("单位");
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.FILE_INVALID,
                    "模板不正确，缺少列：" + String.join("、", missing));
        }
        return new HeaderIndex(category, name, spec, unit,
                firstIndex(map, "生产厂家", "厂家", "生产企业"),
                firstIndex(map, "批准文号", "批准号"),
                firstIndex(map, "包装规格", "包装"),
                firstIndex(map, "备注"));
    }

    private static Integer firstIndex(Map<String, Integer> map, String... names) {
        for (String name : names) {
            Integer index = map.get(name);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private static String text(Row row, Integer index) {
        if (row == null || index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = CELL_FORMAT.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !CELL_FORMAT.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static Map<String, Object> importData(int total, int failed,
                                                  List<DrugImportResultVO.ImportError> errors) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("success", 0);
        data.put("failed", failed);
        data.put("errors", errors);
        return data;
    }

    private static String importMessage(List<DrugImportResultVO.ImportError> errors) {
        DrugImportResultVO.ImportError first = errors.getFirst();
        String message = "第 " + first.getRow() + " 行：" + first.getMessage();
        return errors.size() == 1 ? message : message + "（共 " + errors.size() + " 处错误，整个文件已回滚）";
    }

    private static int failedRows(List<DrugImportResultVO.ImportError> errors) {
        return (int) errors.stream().map(DrugImportResultVO.ImportError::getRow).distinct().count();
    }

    private record HeaderIndex(Integer category, Integer name, Integer spec, Integer unit,
                               Integer manufacturer, Integer approvalNo, Integer pack, Integer remark) {
    }

    private record Unit(String code) {
    }

    private record Pack(String unit, BigDecimal size, String raw) {
    }

    private record ParsedRow(int rowNo, String category, String name, String spec, String measureUnit,
                             String packUnit, BigDecimal packSize, String vaccineDoseUnit,
                             String dosageForm, String note, int sortNo) {
    }
}
