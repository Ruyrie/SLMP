package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceAdminMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceConfigAdminMapper;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.MarketPriceAdminVos;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.entity.MarketPriceConfig;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ADM-C15 行情导入：生成 xlsx 模板、解析并整文件事务入库。
 *
 * <p>模板列固定为：日期、省份、城市、品类、品种、规格、价格、单位、涨跌、来源。
 * “涨跌”由系统按上一个有数据日期的已发布价重算，导入时忽略该列；“城市”“规格”随备注保存，
 * 现有 t_market_price 没有城市/规格列，同一天同地区同品类同品种同价格类型只能有一行。</p>
 */
@Service
@RequiredArgsConstructor
public class MarketPriceImportService {

    public static final List<String> TEMPLATE_HEADERS = List.of(
            "日期", "省份", "城市", "品类", "品种", "规格", "价格", "单位", "涨跌", "来源");

    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 200;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DataFormatter CELL_FORMAT = new DataFormatter(Locale.CHINA);
    private final MarketPriceAdminMapper priceMapper;
    private final MarketPriceConfigAdminMapper configMapper;
    private final ContentLookupMapper lookupMapper;
    private final MarketPriceAdminService marketPriceAdminService;
    private final MarketPriceCalculator calculator;
    private final OperationLogService operationLogService;

    // ===================== 模板 =====================

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("行情导入");
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, switch (i) {
                    case 0 -> 14 * 256;
                    case 1, 3 -> 12 * 256;
                    case 2, 4, 5, 7, 8 -> 16 * 256;
                    case 9 -> 22 * 256;
                    default -> 14 * 256;
                });
            }
            sheet.createFreezePane(0, 1);

            Sheet help = workbook.createSheet("填写说明");
            String[] lines = {
                    "1. 第 1 行为表头，请勿修改列名；从第 2 行开始逐行填写，一次最多 200 行。",
                    "2. 日期：yyyy-MM-dd，只允许今天和前 3 天；同一文件可以包含多天。",
                    "3. 省份：填写省份名称（如 内蒙古、山东）或 6 位行政区划代码；城市选填。",
                    "4. 品类、品种：填写平台启用的品类、品种名称；品种不填或写“全部品种”表示全部品种。",
                    "5. 规格：选填。可填写“出栏价/种苗价”帮助区分同一品类下的两条发布配置；其他规格文字会随备注保存。",
                    "6. 价格：大于 0，最多 2 位小数；单位：元/千克、元/头、元/只，留空时取发布配置的默认单位。",
                    "7. 涨跌：由系统按上一个有数据日期的已发布价自动计算，填写内容会被忽略。",
                    "8. 来源：选填，最多 30 字，留空记为“IMPORT”。",
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
    public MarketPriceAdminVos.ImportResultVO importXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ContentErrors.fileInvalid("请选择要导入的 xlsx 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw ContentErrors.fileInvalid("只支持不超过 2MB 的 xlsx 文件");
        }
        Long adminId = AdminContext.requireAdminId();
        List<MarketPriceAdminVos.ImportError> errors = new ArrayList<>();
        List<ParsedRow> rows = new ArrayList<>();
        int total = 0;

        try (InputStream in = file.getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw ContentErrors.fileInvalid("只支持按模板填写的 xlsx 文件");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            HeaderIndex index = headerIndex(header);
            Map<String, List<MarketPriceConfig>> configCache = new HashMap<>();
            Map<Long, String> categoryNameCache = new HashMap<>();
            Set<String> fileKeys = new HashSet<>();
            Set<String> comboKeys = new HashSet<>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlankRow(row)) {
                    continue;
                }
                total++;
                if (total > MAX_ROWS) {
                    throw ContentErrors.fileInvalid("每次最多导入 200 行");
                }
                int rowNo = r + 1;
                List<String> rowErrors = new ArrayList<>();
                ParsedRow parsed = parseRow(row, index, rowNo, rowErrors, configCache, categoryNameCache);
                if (!rowErrors.isEmpty()) {
                    for (String message : rowErrors) {
                        errors.add(error(rowNo, message));
                    }
                    continue;
                }
                String key = parsed.fileKey();
                if (!fileKeys.add(key)) {
                    errors.add(error(rowNo, "同一天同地区同品类同规格的价格在文件中重复"));
                    continue;
                }
                if (!comboKeys.add(parsed.comboKey())) {
                    errors.add(error(rowNo, "同一天同地区同品类同品种同价格类型在文件中只能有一行"
                            + "（现有价格表不支持同品类多规格）"));
                    continue;
                }
                MarketPrice existing = findPrice(parsed);
                if (existing != null && ("PUBLISHED".equals(existing.getStatus())
                        || "PENDING_REVIEW".equals(existing.getStatus()))) {
                    errors.add(error(rowNo, "同一天同地区同品类同规格的价格已存在（"
                            + ContentTexts.priceStatus(existing.getStatus()) + "），不能重复导入"));
                    continue;
                }
                parsed.existing = existing;
                rows.add(parsed);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw ContentErrors.fileInvalid("只支持按模板填写的 xlsx 文件，不超过 2MB");
        }

        if (total == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入文件没有数据行",
                    importData(0, 0, List.of()));
        }
        if (!errors.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, importMessage(errors),
                    importData(total, failedRows(errors), errors));
        }

        LocalDateTime now = BeijingTime.now();
        for (ParsedRow parsed : rows) {
            MarketPrice price = parsed.existing == null ? new MarketPrice() : parsed.existing;
            if (parsed.existing == null) {
                price.setProvinceCode(parsed.config.getProvinceCode());
                price.setProvinceName(parsed.config.getProvinceName());
                price.setCategoryId(parsed.config.getCategoryId());
                price.setBreedId(parsed.config.getBreedId() == null ? 0L : parsed.config.getBreedId());
                price.setPriceType(parsed.config.getPriceType());
                price.setPriceDate(parsed.date);
                price.setVersion(0);
                price.setCorrected(false);
            }
            MarketPrice last = lastPublished(parsed.config, parsed.date);
            BigDecimal rate = last == null ? null : calculator.rate(parsed.price, last.getPrice());
            boolean needReview = calculator.needReview(rate);
            price.setPrice(parsed.price);
            price.setPriceUnit(parsed.priceUnit);
            price.setPriceUnitText(ContentTexts.priceUnit(parsed.priceUnit));
            price.setPrevPrice(last == null ? null : last.getPrice());
            price.setPrevPriceDate(last == null ? null : last.getPriceDate());
            price.setChangeRate(rate);
            price.setIsNeedReview(needReview);
            price.setStatus(needReview ? "PENDING_REVIEW" : "PUBLISHED");
            price.setReviewerId(null);
            price.setReviewedAt(null);
            price.setReviewResult(null);
            price.setReviewNote(null);
            price.setEnteredBy(adminId);
            price.setEnteredAt(now);
            price.setSource(parsed.source);
            price.setRemark(parsed.remark);
            price.setCorrected(false);
            price.setCorrectedAt(null);
            price.setUpdatedAt(now);
            try {
                if (parsed.existing == null) {
                    priceMapper.insert(price);
                } else {
                    int version = parsed.existing.getVersion() == null ? 0 : parsed.existing.getVersion();
                    int updated = priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                            .eq(MarketPrice::getId, parsed.existing.getId())
                            .eq(MarketPrice::getVersion, version)
                            .set(MarketPrice::getPrice, price.getPrice())
                            .set(MarketPrice::getPriceUnit, price.getPriceUnit())
                            .set(MarketPrice::getPriceUnitText, price.getPriceUnitText())
                            .set(MarketPrice::getPrevPrice, price.getPrevPrice())
                            .set(MarketPrice::getPrevPriceDate, price.getPrevPriceDate())
                            .set(MarketPrice::getChangeRate, price.getChangeRate())
                            .set(MarketPrice::getIsNeedReview, price.getIsNeedReview())
                            .set(MarketPrice::getStatus, price.getStatus())
                            .set(MarketPrice::getReviewerId, null)
                            .set(MarketPrice::getReviewedAt, null)
                            .set(MarketPrice::getReviewResult, null)
                            .set(MarketPrice::getReviewNote, null)
                            .set(MarketPrice::getCorrected, false)
                            .set(MarketPrice::getCorrectedAt, null)
                            .set(MarketPrice::getEnteredBy, adminId)
                            .set(MarketPrice::getEnteredAt, now)
                            .set(MarketPrice::getSource, price.getSource())
                            .set(MarketPrice::getRemark, price.getRemark())
                            .set(MarketPrice::getUpdatedAt, now)
                            .setSql("version = version + 1"));
                    if (updated == 0) {
                        throw ContentErrors.stateChanged("第 " + parsed.rowNo + " 行价格已被其他管理员修改，请刷新后重试");
                    }
                    price.setId(parsed.existing.getId());
                }
            } catch (DuplicateKeyException e) {
                throw ContentErrors.stateChanged("第 " + parsed.rowNo
                        + " 行同一天同地区同品类同规格的价格已存在，整个文件已回滚");
            }
            marketPriceAdminService.recalculateAfterImport(parsed.config, parsed.date, price.getId());
        }

        MarketPriceAdminVos.ImportResultVO result = new MarketPriceAdminVos.ImportResultVO();
        result.setTotal(total);
        result.setSuccess(total);
        result.setFailed(0);
        result.setFileName(file.getOriginalFilename());
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("MARKET_PRICE")
                .opAction("IMPORT")
                .bizType("MARKET_PRICE")
                .targetName("行情导入")
                .searchText(file.getOriginalFilename())
                .reason("导入 " + total + " 行行情")
                .extraText("成功 " + total + " 行")
                .after(Map.of("total", total, "success", total))
                .result("SUCCESS")
                .build());
        return result;
    }

    // ===================== 解析 =====================

    private ParsedRow parseRow(Row row, HeaderIndex index, int rowNo, List<String> errors,
                               Map<String, List<MarketPriceConfig>> configCache,
                               Map<Long, String> categoryNameCache) {
        LocalDate date = dateCell(row, index.date, errors);
        if (date != null) {
            try {
                calculator.requirePublishableDate(date);
            } catch (BizException e) {
                errors.add(e.getMessage());
            }
        }
        String provinceRaw = text(row, index.province);
        if (!StringUtils.hasText(provinceRaw)) {
            errors.add("省份不能为空");
        }
        String city = text(row, index.city);
        if (city != null && city.codePointCount(0, city.length()) > 20) {
            errors.add("城市不能超过 20 字");
        }
        String categoryRaw = text(row, index.category);
        if (!StringUtils.hasText(categoryRaw)) {
            errors.add("品类不能为空");
        }
        String breedRaw = text(row, index.breed);
        String spec = text(row, index.spec);
        if (spec != null && spec.codePointCount(0, spec.length()) > 30) {
            errors.add("规格不能超过 30 字");
        }
        BigDecimal price = priceCell(row, index.price, errors);
        String unitRaw = text(row, index.unit);
        String source = text(row, index.source);
        if (source == null) {
            source = "IMPORT";
        } else if (source.codePointCount(0, source.length()) > 30) {
            errors.add("来源不能超过 30 字");
        }

        MarketPriceConfig config = null;
        String priceUnit = null;
        Long categoryId = null;
        Long breedId = 0L;
        String categoryName = null;
        if (StringUtils.hasText(provinceRaw) && StringUtils.hasText(categoryRaw)) {
            String provinceCode = provinceCode(provinceRaw);
            if (provinceCode == null) {
                errors.add("找不到该省份");
            } else {
                List<MarketPriceConfig> configs = configCache.computeIfAbsent(provinceCode,
                        code -> configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                                .eq(MarketPriceConfig::getProvinceCode, code)
                                .eq(MarketPriceConfig::getStatus, "ENABLED")));
                if (configs.isEmpty()) {
                    errors.add("找不到该地区的发布配置");
                }
                categoryId = lookupMapper.selectCategoryIdByName(categoryRaw);
                if (categoryId == null) {
                    errors.add("找不到品类“" + categoryRaw + "”");
                } else {
                    categoryName = categoryNameCache.computeIfAbsent(categoryId, id -> categoryRaw);
                    if (StringUtils.hasText(breedRaw) && !"全部品种".equals(breedRaw) && !"全部".equals(breedRaw)) {
                        breedId = lookupMapper.selectEnabledBreedIdByName(categoryId, breedRaw);
                        if (breedId == null) {
                            errors.add("找不到品种“" + breedRaw + "”");
                            breedId = 0L;
                        }
                    }
                    if (errors.isEmpty()) {
                        config = matchConfig(configs, categoryId, breedId, spec, unitRaw, errors);
                    }
                }
            }
        }
        if (config != null) {
            priceUnit = resolveUnit(unitRaw, config);
            if (priceUnit == null) {
                errors.add("单位不正确，请填写 元/千克、元/头、元/只 或对应代码");
            }
        }
        if (!errors.isEmpty()) {
            return null;
        }
        String remark = remark(city, spec);
        return new ParsedRow(rowNo, date, config, price, priceUnit, source, remark, categoryName,
                provinceRaw, categoryRaw, breedRaw, spec);
    }

    private MarketPriceConfig matchConfig(List<MarketPriceConfig> configs, Long categoryId, Long breedId,
                                          String spec, String unitRaw, List<String> errors) {
        List<MarketPriceConfig> candidates = configs.stream()
                .filter(c -> categoryId.equals(c.getCategoryId()))
                .filter(c -> (c.getBreedId() == null ? 0L : c.getBreedId()) == breedId)
                .toList();
        if (candidates.isEmpty()) {
            errors.add("找不到该地区的发布配置");
            return null;
        }
        String hintedType = priceTypeHint(spec);
        if (hintedType != null) {
            List<MarketPriceConfig> hinted = candidates.stream()
                    .filter(c -> hintedType.equals(c.getPriceType())).toList();
            if (hinted.isEmpty()) {
                errors.add("规格中的价格类型与该地区的发布配置不一致");
                return null;
            }
            candidates = hinted;
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        String unit = normalizeUnit(unitRaw);
        if (unit != null) {
            List<MarketPriceConfig> byUnit = candidates.stream()
                    .filter(c -> unit.equals(c.getDefaultUnit())).toList();
            if (byUnit.size() == 1) {
                return byUnit.getFirst();
            }
        }
        errors.add("同一地区、品类、品种有多条发布配置，请在“规格”列填写“出栏价”或“种苗价”");
        return null;
    }

    private String resolveUnit(String raw, MarketPriceConfig config) {
        String unit = normalizeUnit(raw);
        if (unit == null) {
            unit = StringUtils.hasText(config.getDefaultUnit()) ? config.getDefaultUnit() : "YUAN_PER_KG";
        }
        return MarketPriceCalculator.PRICE_UNITS.contains(unit) ? unit : null;
    }

    private static String priceTypeHint(String spec) {
        if (!StringUtils.hasText(spec)) {
            return null;
        }
        String value = spec.trim();
        if (value.contains("种苗")) {
            return "SEEDLING";
        }
        if (value.contains("出栏") || value.contains("毛猪") || value.contains("活猪")) {
            return "SLAUGHTER";
        }
        return null;
    }

    private static String normalizeUnit(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "元/千克", "元/公斤", "元/KG", "YUAN_PER_KG" -> "YUAN_PER_KG";
            case "元/头", "YUAN_PER_HEAD" -> "YUAN_PER_HEAD";
            case "元/只", "元/羽", "YUAN_PER_BIRD" -> "YUAN_PER_BIRD";
            default -> null;
        };
    }

    private static String provinceCode(String raw) {
        String value = raw.trim();
        if (value.matches("\\d{6}")) {
            return value;
        }
        return ContentTexts.provinceCode(value);
    }

    private MarketPrice findPrice(ParsedRow row) {
        return priceMapper.selectOne(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getPriceDate, row.date)
                .eq(MarketPrice::getProvinceCode, row.config.getProvinceCode())
                .eq(MarketPrice::getCategoryId, row.config.getCategoryId())
                .eq(MarketPrice::getBreedId, row.config.getBreedId() == null ? 0L : row.config.getBreedId())
                .eq(MarketPrice::getPriceType, row.config.getPriceType())
                .last("LIMIT 1"));
    }

    private MarketPrice lastPublished(MarketPriceConfig config, LocalDate before) {
        List<MarketPrice> rows = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, "PUBLISHED")
                .eq(MarketPrice::getProvinceCode, config.getProvinceCode())
                .eq(MarketPrice::getCategoryId, config.getCategoryId())
                .eq(MarketPrice::getBreedId, config.getBreedId() == null ? 0L : config.getBreedId())
                .eq(MarketPrice::getPriceType, config.getPriceType())
                .lt(MarketPrice::getPriceDate, before)
                .orderByDesc(MarketPrice::getPriceDate)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String remark(String city, String spec) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(city)) {
            parts.add("城市：" + city);
        }
        if (StringUtils.hasText(spec)) {
            parts.add("规格：" + spec);
        }
        if (parts.isEmpty()) {
            return null;
        }
        String value = String.join("；", parts);
        return value.codePointCount(0, value.length()) > 100
                ? value.substring(0, 100) : value;
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
        Integer date = firstIndex(map, "日期", "价格日期");
        Integer province = firstIndex(map, "省份", "地区", "省");
        Integer category = firstIndex(map, "品类");
        Integer price = firstIndex(map, "价格", "今日价", "今日价格");
        List<String> missing = new ArrayList<>();
        if (date == null) {
            missing.add("日期");
        }
        if (province == null) {
            missing.add("省份");
        }
        if (category == null) {
            missing.add("品类");
        }
        if (price == null) {
            missing.add("价格");
        }
        if (!missing.isEmpty()) {
            throw ContentErrors.fileInvalid("模板不正确，缺少列：" + String.join("、", missing));
        }
        return new HeaderIndex(date, province,
                firstIndex(map, "城市"), category,
                firstIndex(map, "品种"), firstIndex(map, "规格"),
                price, firstIndex(map, "单位", "计价单位"),
                firstIndex(map, "涨跌"), firstIndex(map, "来源", "数据来源"));
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

    private LocalDate dateCell(Row row, Integer index, List<String> errors) {
        if (index == null) {
            errors.add("日期不能为空");
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            errors.add("日期不能为空");
            return null;
        }
        try {
            CellType type = cell.getCellType() == CellType.FORMULA
                    ? cell.getCachedFormulaResultType() : cell.getCellType();
            if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            String value = CELL_FORMAT.formatCellValue(cell).trim();
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException | IllegalStateException e) {
            errors.add("日期格式不正确，请填写 yyyy-MM-dd");
            return null;
        }
    }

    private BigDecimal priceCell(Row row, Integer index, List<String> errors) {
        if (index == null) {
            errors.add("价格不能为空");
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            errors.add("价格不能为空");
            return null;
        }
        BigDecimal price = null;
        try {
            CellType type = cell.getCellType() == CellType.FORMULA
                    ? cell.getCachedFormulaResultType() : cell.getCellType();
            if (type == CellType.NUMERIC) {
                price = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
            } else {
                String value = CELL_FORMAT.formatCellValue(cell).trim().replace(",", "");
                if (!value.isEmpty()) {
                    price = new BigDecimal(value);
                }
            }
        } catch (NumberFormatException e) {
            errors.add("价格必须是数字");
            return null;
        }
        if (price == null) {
            errors.add("价格不能为空");
            return null;
        }
        if (!calculator.validPrice(price)) {
            errors.add("价格必须大于 0，最多保留 2 位小数");
            return null;
        }
        return price;
    }

    private static boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !CELL_FORMAT.formatCellValue(cell).trim().isEmpty()) {
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

    private static MarketPriceAdminVos.ImportError error(int row, String message) {
        MarketPriceAdminVos.ImportError error = new MarketPriceAdminVos.ImportError();
        error.setRow(row);
        error.setMessage(message);
        return error;
    }

    private static Map<String, Object> importData(int total, int failed, List<MarketPriceAdminVos.ImportError> errors) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("success", 0);
        data.put("failed", failed);
        data.put("errors", errors);
        return data;
    }

    private static String importMessage(List<MarketPriceAdminVos.ImportError> errors) {
        MarketPriceAdminVos.ImportError first = errors.getFirst();
        String message = "第 " + first.getRow() + " 行：" + first.getMessage();
        return errors.size() == 1 ? message : message + "（共 " + errors.size() + " 处错误，整个文件已回滚）";
    }

    private static int failedRows(List<MarketPriceAdminVos.ImportError> errors) {
        return (int) errors.stream().map(MarketPriceAdminVos.ImportError::getRow).distinct().count();
    }

    private record HeaderIndex(Integer date, Integer province, Integer city, Integer category,
                               Integer breed, Integer spec, Integer price, Integer unit,
                               Integer changeRate, Integer source) {
    }

    private static final class ParsedRow {
        private final int rowNo;
        private final LocalDate date;
        private final MarketPriceConfig config;
        private final BigDecimal price;
        private final String priceUnit;
        private final String source;
        private final String remark;
        @SuppressWarnings("unused")
        private final String categoryName;
        @SuppressWarnings("unused")
        private final String provinceRaw;
        @SuppressWarnings("unused")
        private final String categoryRaw;
        @SuppressWarnings("unused")
        private final String breedRaw;
        private final String spec;
        private MarketPrice existing;

        private ParsedRow(int rowNo, LocalDate date, MarketPriceConfig config, BigDecimal price,
                          String priceUnit, String source, String remark, String categoryName,
                          String provinceRaw, String categoryRaw, String breedRaw, String spec) {
            this.rowNo = rowNo;
            this.date = date;
            this.config = config;
            this.price = price;
            this.priceUnit = priceUnit;
            this.source = source;
            this.remark = remark;
            this.categoryName = categoryName;
            this.provinceRaw = provinceRaw;
            this.categoryRaw = categoryRaw;
            this.breedRaw = breedRaw;
            this.spec = spec;
        }

        private String fileKey() {
            return comboKey() + "|" + (spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT));
        }

        private String comboKey() {
            return date + "|" + config.getProvinceCode() + "|" + config.getCategoryId() + "|"
                    + (config.getBreedId() == null ? 0L : config.getBreedId()) + "|"
                    + config.getPriceType();
        }
    }
}
