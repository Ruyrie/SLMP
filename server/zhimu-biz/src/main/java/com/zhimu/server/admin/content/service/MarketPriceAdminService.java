package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.entity.MarketPriceCorrection;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.LastPriceRow;
import com.zhimu.server.admin.content.mapper.MarketPriceAdminMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceConfigAdminMapper;
import com.zhimu.server.admin.content.mapper.MarketPriceCorrectionMapper;
import com.zhimu.server.admin.content.mapper.NameRow;
import com.zhimu.server.admin.content.query.ContentQueries;
import com.zhimu.server.admin.content.request.ContentRequests;
import com.zhimu.server.admin.content.support.ContentErrors;
import com.zhimu.server.admin.content.support.ContentTexts;
import com.zhimu.server.admin.content.vo.MarketPriceAdminVos;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.entity.MarketPriceConfig;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** WA-C02 市场行情发布（ADM-C11–C21，外加当天撤销的 C22）。 */
@Service
@RequiredArgsConstructor
public class MarketPriceAdminService {

    private static final Set<String> PRICE_TYPES = Set.of("SLAUGHTER", "SEEDLING");
    private static final Set<String> ROW_STATUSES = Set.of("PENDING_REVIEW", "PUBLISHED", "UNPUBLISHED");
    private static final List<String> BRAND = List.of("PUBLISHED", "PENDING_REVIEW", "RETURNED", "OFFLINE");

    private final MarketPriceAdminMapper priceMapper;
    private final MarketPriceConfigAdminMapper configMapper;
    private final MarketPriceCorrectionMapper correctionMapper;
    private final ContentLookupMapper lookupMapper;
    private final MarketPriceCalculator calculator;
    private final OperationLogService operationLogService;
    private final AdminMessageService adminMessageService;

    private record Combo(String provinceCode, Long categoryId, Long breedId, String priceType) {
    }

    // ===================== 查询 =====================

    public PageVO<MarketPriceAdminVos.RowVO> list(ContentQueries.MarketPriceQuery query) {
        LocalDate date = query.getDate() == null ? BeijingTime.today() : query.getDate();
        calculator.requireViewableDate(date);
        List<MarketPriceConfig> configs = enabledConfigs(query.getProvinceCode(), query.getCategoryId());
        Map<Long, MarketPrice> prices = pricesByConfig(date, configs);
        Map<Long, MarketPriceCorrection> pending = pendingCorrections(prices.values().stream()
                .map(MarketPrice::getId).toList());
        Map<Long, String> categoryNames = categoryNames(configs);
        Map<Long, String> breedNames = breedNames(configs);
        Map<Long, String> adminNames = adminNames(collectAdminIds(prices.values(), pending.values()));
        Long adminId = AdminContext.adminId();

        List<RowBuild> rows = new ArrayList<>();
        for (MarketPriceConfig config : configs) {
            MarketPrice price = prices.get(config.getId());
            MarketPriceCorrection correction = price == null ? null : pending.get(price.getId());
            MarketPriceAdminVos.RowVO row = buildRow(config, price, correction, categoryNames, breedNames,
                    adminNames, adminId, date);
            rows.add(new RowBuild(config, row));
        }
        rows.sort(Comparator.comparingInt((RowBuild build) -> rowOrder(build.row.getRowStatus()))
                .thenComparingInt(build -> build.config.getSortNo() == null ? 0 : build.config.getSortNo())
                .thenComparing(build -> build.config.getId()));

        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", rows.size());
        for (String status : List.of("PENDING_REVIEW", "PUBLISHED", "UNPUBLISHED")) {
            tabs.put(status, (int) rows.stream().filter(r -> status.equals(r.row.getRowStatus())).count());
        }
        String filter = StringUtils.hasText(query.getStatus()) ? query.getStatus().trim().toUpperCase() : "ALL";
        if (!"ALL".equals(filter) && !ROW_STATUSES.contains(filter)) {
            throw ContentErrors.field("status", "状态不正确");
        }
        List<MarketPriceAdminVos.RowVO> filtered = rows.stream()
                .filter(r -> "ALL".equals(filter) || filter.equals(r.row.getRowStatus()))
                .map(RowBuild::row).toList();
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return PageVO.of(new ArrayList<>(filtered.subList(from, to)), filtered.size(), page, size)
                .withTabCounts(tabs);
    }

    public MarketPriceAdminVos.ProgressVO progress(ContentQueries.MarketDateQuery query) {
        LocalDate date = query.getDate() == null ? BeijingTime.today() : query.getDate();
        calculator.requireViewableDate(date);
        List<MarketPriceConfig> configs = enabledConfigs(null, null);
        Map<Long, MarketPrice> prices = pricesByConfig(date, configs);
        Map<Long, String> categoryNames = categoryNames(configs);
        Map<Long, String> breedNames = breedNames(configs);
        Map<String, MarketPriceAdminVos.ProvinceProgress> provinces = new LinkedHashMap<>();
        List<MarketPriceAdminVos.UnpublishedItem> unpublished = new ArrayList<>();
        for (MarketPriceConfig config : configs) {
            MarketPriceAdminVos.ProvinceProgress progress = provinces.computeIfAbsent(config.getProvinceCode(),
                    code -> {
                        MarketPriceAdminVos.ProvinceProgress p = new MarketPriceAdminVos.ProvinceProgress();
                        p.setProvinceCode(code);
                        p.setProvinceName(config.getProvinceName());
                        return p;
                    });
            progress.setTotal(progress.getTotal() + 1);
            MarketPrice price = prices.get(config.getId());
            if (price != null && "PUBLISHED".equals(price.getStatus())) {
                progress.setPublished(progress.getPublished() + 1);
                continue;
            }
            if (price != null && "PENDING_REVIEW".equals(price.getStatus())) {
                continue;
            }
            MarketPriceAdminVos.UnpublishedItem item = new MarketPriceAdminVos.UnpublishedItem();
            item.setConfigId(config.getId());
            item.setProvinceName(config.getProvinceName());
            item.setCategoryName(categoryNames.getOrDefault(config.getCategoryId(), "品类"));
            item.setBreedName(breedName(config, breedNames));
            item.setPriceType(config.getPriceType());
            item.setPriceTypeText(ContentTexts.priceType(config.getPriceType()));
            MarketPrice last = lastPublished(config, date);
            if (last != null) {
                item.setLastPrice(last.getPrice());
                item.setLastUnit(last.getPriceUnitText());
                item.setLastDate(last.getPriceDate());
            }
            unpublished.add(item);
        }
        MarketPriceAdminVos.ProgressVO vo = new MarketPriceAdminVos.ProgressVO();
        vo.setPriceDate(date);
        vo.setUnpublishedCount(unpublished.size());
        vo.setUnpublished(unpublished);
        vo.setProvinces(new ArrayList<>(provinces.values()));
        return vo;
    }

    public MarketPriceAdminVos.EntryFormVO entryForm(ContentQueries.EntryFormQuery query) {
        LocalDate date = query.getDate() == null ? BeijingTime.today() : query.getDate();
        calculator.requirePublishableDate(date);
        if (!StringUtils.hasText(query.getProvinceCode())) {
            throw ContentErrors.field("province_code", "请选择地区");
        }
        List<MarketPriceConfig> configs = enabledConfigs(query.getProvinceCode(), null);
        if (configs.isEmpty()) {
            throw ContentErrors.field("province_code", "该地区没有启用的发布配置");
        }
        Map<Long, MarketPrice> prices = pricesByConfig(date, configs);
        Map<Long, String> categoryNames = categoryNames(configs);
        Map<Long, String> breedNames = breedNames(configs);
        MarketPriceAdminVos.EntryFormVO vo = new MarketPriceAdminVos.EntryFormVO();
        vo.setPriceDate(date);
        vo.setProvinceCode(configs.getFirst().getProvinceCode());
        vo.setProvinceName(configs.getFirst().getProvinceName());
        for (MarketPriceConfig config : configs) {
            MarketPriceAdminVos.EntryRow row = new MarketPriceAdminVos.EntryRow();
            row.setConfigId(config.getId());
            row.setCategoryName(categoryNames.getOrDefault(config.getCategoryId(), "品类"));
            row.setBreedName(breedName(config, breedNames));
            row.setPriceType(config.getPriceType());
            row.setPriceTypeText(ContentTexts.priceType(config.getPriceType()));
            MarketPrice last = lastPublished(config, date);
            if (last != null) {
                row.setLastPrice(last.getPrice());
                row.setLastUnit(last.getPriceUnit());
                row.setLastUnitText(last.getPriceUnitText());
                row.setLastDate(last.getPriceDate());
            }
            row.setDefaultUnit(defaultUnit(config));
            row.setDefaultUnitText(ContentTexts.priceUnit(defaultUnit(config)));
            MarketPrice today = prices.get(config.getId());
            row.setTodayStatus(today == null ? "NONE" : today.getStatus());
            row.setTodayPrice(today == null ? null : today.getPrice());
            row.setIsReadonly(today != null && ("PUBLISHED".equals(today.getStatus())
                    || "PENDING_REVIEW".equals(today.getStatus())));
            if (today != null && "RETURNED".equals(today.getStatus())) {
                row.setReturnNote(today.getReviewNote());
            }
            vo.getRows().add(row);
        }
        return vo;
    }

    public MarketPriceAdminVos.DetailVO detail(Long id) {
        MarketPrice price = requirePrice(id);
        Long configId = priceConfigId(price);
        MarketPriceConfig config = configId == null ? null : configMapper.selectById(configId);
        if (config == null) {
            config = syntheticConfig(price);
        }
        Map<Long, String> categoryNames = categoryNames(List.of(config));
        Map<Long, String> breedNames = breedNames(List.of(config));
        List<MarketPriceCorrection> corrections = correctionMapper.selectByPriceId(id);
        List<Long> adminIds = new ArrayList<>();
        if (price.getEnteredBy() != null) {
            adminIds.add(price.getEnteredBy());
        }
        if (price.getReviewerId() != null) {
            adminIds.add(price.getReviewerId());
        }
        for (MarketPriceCorrection correction : corrections) {
            adminIds.add(correction.getCorrectedBy());
            adminIds.add(correction.getReviewerId());
        }
        Map<Long, String> adminNames = adminNames(adminIds);
        MarketPriceCorrection pending = corrections.stream()
                .filter(c -> Boolean.TRUE.equals(c.getPendingFlag())).findFirst().orElse(null);
        MarketPriceAdminVos.RowVO row = buildRow(config, price, pending, categoryNames, breedNames,
                adminNames, AdminContext.adminId(), price.getPriceDate());
        MarketPriceAdminVos.DetailVO vo = new MarketPriceAdminVos.DetailVO();
        org.springframework.beans.BeanUtils.copyProperties(row, vo);
        vo.setStatus(price.getStatus());
        vo.setStatusText(ContentTexts.priceStatus(price.getStatus()));
        List<MarketPrice> near7 = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, "PUBLISHED")
                .eq(MarketPrice::getProvinceCode, price.getProvinceCode())
                .eq(MarketPrice::getCategoryId, price.getCategoryId())
                .eq(MarketPrice::getBreedId, price.getBreedId())
                .eq(MarketPrice::getPriceType, price.getPriceType())
                .between(MarketPrice::getPriceDate, price.getPriceDate().minusDays(6), price.getPriceDate()));
        if (!near7.isEmpty()) {
            vo.setNear7Min(near7.stream().map(MarketPrice::getPrice).min(BigDecimal::compareTo).orElse(null));
            vo.setNear7Max(near7.stream().map(MarketPrice::getPrice).max(BigDecimal::compareTo).orElse(null));
        }
        vo.setDataSource(price.getSource());
        vo.setRemark(price.getRemark());
        if (price.getReviewerId() != null) {
            vo.setReviewerName(adminNames.get(price.getReviewerId()));
        }
        vo.setReviewedAt(price.getReviewedAt());
        vo.setReviewResult(price.getReviewResult());
        vo.setReviewResultText(ContentTexts.reviewResult(price.getReviewResult()));
        vo.setReviewNote(price.getReviewNote());
        vo.setIsCorrected(Boolean.TRUE.equals(price.getCorrected()));
        vo.setCorrectedAt(price.getCorrectedAt());
        vo.setCorrections(corrections.stream().map(c -> correctionVO(c, adminNames)).toList());
        vo.setPendingCorrection(pending == null ? null : correctionVO(pending, adminNames));
        vo.setVersion(price.getVersion() == null ? 0 : price.getVersion());
        return vo;
    }

    public List<MarketPriceAdminVos.HistoryItemVO> history(ContentQueries.MarketHistoryQuery query) {
        if (!StringUtils.hasText(query.getProvinceCode()) || query.getCategoryId() == null
                || query.getBreedId() == null || !StringUtils.hasText(query.getPriceType())) {
            throw ContentErrors.param("请选择地区、品类、品种与价格类型");
        }
        int days = query.getDays() == null ? 90 : Math.max(1, Math.min(90, query.getDays()));
        LocalDate from = BeijingTime.today().minusDays(days - 1L);
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getProvinceCode, query.getProvinceCode())
                .eq(MarketPrice::getCategoryId, query.getCategoryId())
                .eq(MarketPrice::getBreedId, query.getBreedId())
                .eq(MarketPrice::getPriceType, query.getPriceType())
                .ge(MarketPrice::getPriceDate, from)
                .orderByDesc(MarketPrice::getPriceDate));
        if (prices.isEmpty()) {
            return List.of();
        }
        List<Long> priceIds = prices.stream().map(MarketPrice::getId).toList();
        Map<Long, List<MarketPriceCorrection>> corrections = new HashMap<>();
        for (MarketPriceCorrection correction : correctionMapper.selectList(Wrappers.<MarketPriceCorrection>lambdaQuery()
                .in(MarketPriceCorrection::getPriceId, priceIds)
                .orderByDesc(MarketPriceCorrection::getId))) {
            corrections.computeIfAbsent(correction.getPriceId(), key -> new ArrayList<>()).add(correction);
        }
        List<Long> adminIds = new ArrayList<>();
        prices.forEach(p -> adminIds.add(p.getEnteredBy()));
        corrections.values().forEach(list -> list.forEach(c -> {
            adminIds.add(c.getCorrectedBy());
            adminIds.add(c.getReviewerId());
        }));
        Map<Long, String> adminNames = adminNames(adminIds);
        List<MarketPriceAdminVos.HistoryItemVO> result = new ArrayList<>();
        for (MarketPrice price : prices) {
            MarketPriceAdminVos.HistoryItemVO item = new MarketPriceAdminVos.HistoryItemVO();
            item.setPriceDate(price.getPriceDate());
            item.setPriceId(price.getId());
            item.setPrice(price.getPrice());
            item.setPriceUnit(price.getPriceUnit());
            item.setPriceUnitText(price.getPriceUnitText());
            item.setChangeRate(price.getChangeRate());
            item.setStatus(price.getStatus());
            item.setStatusText(ContentTexts.priceStatus(price.getStatus()));
            item.setEnteredByName(adminNames.get(price.getEnteredBy()));
            item.setEnteredAt(price.getEnteredAt());
            item.setIsCorrected(Boolean.TRUE.equals(price.getCorrected()));
            item.setCorrections(corrections.getOrDefault(price.getId(), List.of()).stream()
                    .map(c -> correctionVO(c, adminNames)).toList());
            result.add(item);
        }
        return result;
    }

    public List<MarketPriceAdminVos.ConfigVO> configs(String provinceCode) {
        List<MarketPriceConfig> configs = configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                .eq(StringUtils.hasText(provinceCode), MarketPriceConfig::getProvinceCode, provinceCode)
                .orderByAsc(MarketPriceConfig::getProvinceCode)
                .orderByAsc(MarketPriceConfig::getSortNo)
                .orderByAsc(MarketPriceConfig::getId));
        Map<Long, String> categoryNames = categoryNames(configs);
        Map<Long, String> breedNames = breedNames(configs);
        List<MarketPriceAdminVos.ConfigVO> result = new ArrayList<>();
        for (MarketPriceConfig config : configs) {
            MarketPriceAdminVos.ConfigVO vo = new MarketPriceAdminVos.ConfigVO();
            vo.setConfigId(config.getId());
            vo.setProvinceCode(config.getProvinceCode());
            vo.setProvinceName(config.getProvinceName());
            vo.setCategoryId(config.getCategoryId());
            vo.setCategoryName(categoryNames.getOrDefault(config.getCategoryId(), "品类"));
            vo.setBreedId(config.getBreedId() == null ? 0L : config.getBreedId());
            vo.setBreedName(breedName(config, breedNames));
            vo.setPriceType(config.getPriceType());
            vo.setPriceTypeText(ContentTexts.priceType(config.getPriceType()));
            vo.setDefaultUnit(defaultUnit(config));
            vo.setDefaultUnitText(ContentTexts.priceUnit(defaultUnit(config)));
            vo.setSortNo(config.getSortNo());
            vo.setIsEnabled("ENABLED".equals(config.getStatus()));
            result.add(vo);
        }
        return result;
    }

    // ===================== 写操作 =====================

    @Transactional
    public MarketPriceAdminVos.BatchPublishVO batchPublish(ContentRequests.BatchPublishRequest request) {
        if (request == null) {
            throw ContentErrors.param("请填写发布内容");
        }
        calculator.requirePublishableDate(request.getPriceDate());
        if (!StringUtils.hasText(request.getProvinceCode())) {
            throw ContentErrors.field("province_code", "请选择地区");
        }
        String dataSource = trim(request.getDataSource());
        if (dataSource == null || dataSource.codePointCount(0, dataSource.length()) > 50) {
            throw ContentErrors.field("data_source", "请填写数据来源，最多 50 字");
        }
        String remark = trim(request.getRemark());
        if (remark != null && remark.codePointCount(0, remark.length()) > 100) {
            throw ContentErrors.field("remark", "备注不能超过 100 字");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw ContentErrors.field("items", "请填写至少一项价格");
        }
        if (request.getItems().size() > 50) {
            throw ContentErrors.field("items", "一次最多发布 50 项");
        }
        List<MarketPriceConfig> provinceConfigs = enabledConfigs(request.getProvinceCode(), null);
        if (provinceConfigs.isEmpty()) {
            throw ContentErrors.field("province_code", "该地区没有启用的发布配置");
        }
        Map<Long, MarketPriceConfig> configById = new LinkedHashMap<>();
        provinceConfigs.forEach(c -> configById.put(c.getId(), c));
        List<FieldErrorVO> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            ContentRequests.BatchPublishRequest.Item item = request.getItems().get(i);
            String prefix = "items[" + i + "]";
            MarketPriceConfig config = item == null || item.getConfigId() == null ? null : configById.get(item.getConfigId());
            if (config == null) {
                errors.add(new FieldErrorVO(prefix + ".config_id", "发布配置已变化，请刷新后重试"));
                continue;
            }
            if (!seen.add(config.getId())) {
                errors.add(new FieldErrorVO(prefix + ".config_id", "同一项价格只能填写一次"));
            }
            if (!calculator.validPrice(item.getPrice())) {
                errors.add(new FieldErrorVO(prefix + ".price", item.getPrice() == null ? "请填写价格" : "价格必须大于 0，最多保留 2 位小数"));
            }
            if (StringUtils.hasText(item.getPriceUnit()) && !MarketPriceCalculator.PRICE_UNITS.contains(item.getPriceUnit())) {
                errors.add(new FieldErrorVO(prefix + ".price_unit", "请选择计价单位"));
            }
        }
        if (!errors.isEmpty()) {
            throw ContentErrors.fields(errors);
        }
        for (ContentRequests.BatchPublishRequest.Item item : request.getItems()) {
            MarketPriceConfig config = configById.get(item.getConfigId());
            MarketPrice existing = findPrice(config, request.getPriceDate());
            if (existing != null && ("PUBLISHED".equals(existing.getStatus()) || "PENDING_REVIEW".equals(existing.getStatus()))) {
                throw ContentErrors.stateChanged(configLabel(config) + "今天已发布，列表已刷新");
            }
        }
        MarketPriceAdminVos.BatchPublishVO result = new MarketPriceAdminVos.BatchPublishVO();
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        for (ContentRequests.BatchPublishRequest.Item item : request.getItems()) {
            MarketPriceConfig config = configById.get(item.getConfigId());
            MarketPrice existing = findPrice(config, request.getPriceDate());
            MarketPrice last = lastPublished(config, request.getPriceDate());
            BigDecimal rate = last == null ? null : calculator.rate(item.getPrice(), last.getPrice());
            boolean needReview = calculator.needReview(rate);
            MarketPrice price = existing == null ? new MarketPrice() : existing;
            if (existing == null) {
                price.setProvinceCode(config.getProvinceCode());
                price.setProvinceName(config.getProvinceName());
                price.setCategoryId(config.getCategoryId());
                price.setBreedId(config.getBreedId() == null ? 0L : config.getBreedId());
                price.setPriceType(config.getPriceType());
                price.setPriceDate(request.getPriceDate());
                price.setCorrected(false);
                price.setVersion(0);
            }
            price.setPrice(item.getPrice());
            price.setPriceUnit(StringUtils.hasText(item.getPriceUnit()) ? item.getPriceUnit() : defaultUnit(config));
            price.setPriceUnitText(ContentTexts.priceUnit(price.getPriceUnit()));
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
            price.setSource(dataSource);
            price.setRemark(remark);
            price.setCorrected(false);
            price.setCorrectedAt(null);
            try {
                if (existing == null) {
                    priceMapper.insert(price);
                } else {
                    int version = existing.getVersion() == null ? 0 : existing.getVersion();
                    int rows = priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                            .eq(MarketPrice::getId, existing.getId())
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
                            .set(MarketPrice::getSource, dataSource)
                            .set(MarketPrice::getRemark, remark)
                            .set(MarketPrice::getUpdatedAt, now)
                            .setSql("version = version + 1"));
                    if (rows == 0) {
                        throw ContentErrors.stateChanged(configLabel(config) + "今天已发布，列表已刷新");
                    }
                    price.setId(existing.getId());
                    price.setVersion(version + 1);
                }
            } catch (DuplicateKeyException e) {
                throw ContentErrors.stateChanged(configLabel(config) + "今天已发布，列表已刷新");
            }
            recalculateAfter(config, request.getPriceDate(), price.getId());
            result.getItems().add(batchItem(config.getId(), price.getId(), price.getStatus(), rate));
            if (needReview) {
                result.setReviewCount(result.getReviewCount() + 1);
            } else {
                result.setPublishedCount(result.getPublishedCount() + 1);
            }
            log(price, needReview ? "SUBMIT_REVIEW" : "PUBLISH", adminId,
                    "发布 " + configLabel(config) + " " + BeijingTime.format(request.getPriceDate()), null,
                    price.getStatus());
        }
        return result;
    }

    @Transactional
    public MarketPriceAdminVos.DetailVO review(Long id, ContentRequests.PriceReviewRequest request) {
        MarketPrice price = requirePrice(id);
        if (request == null || !StringUtils.hasText(request.getResult())) {
            throw ContentErrors.field("result", "请选择复核结果");
        }
        String result = request.getResult().trim().toUpperCase();
        if (!Set.of("PASS", "RETURN").contains(result)) {
            throw ContentErrors.field("result", "复核结果不正确");
        }
        String note = trim(request.getReviewNote());
        if (note == null) {
            throw ContentErrors.reasonEmpty("请填写复核说明");
        }
        if (note.codePointCount(0, note.length()) > 100) {
            throw ContentErrors.field("review_note", "复核说明不能超过 100 字");
        }
        if (!"PENDING_REVIEW".equals(price.getStatus()) && request.getCorrectionId() == null) {
            throw ContentErrors.stateChanged("这条价格已由其他管理员复核，列表已刷新");
        }
        int version = request.getVersion() == null ? -1 : request.getVersion();
        if (version != (price.getVersion() == null ? 0 : price.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        if (request.getCorrectionId() != null) {
            return reviewCorrection(price, request.getCorrectionId(), result, note, adminId, now);
        }
        String status = "PASS".equals(result) ? "PUBLISHED" : "RETURNED";
        int rows = priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                .eq(MarketPrice::getId, id)
                .eq(MarketPrice::getStatus, "PENDING_REVIEW")
                .eq(MarketPrice::getVersion, version)
                .set(MarketPrice::getStatus, status)
                .set(MarketPrice::getReviewerId, adminId)
                .set(MarketPrice::getReviewedAt, now)
                .set(MarketPrice::getReviewResult, result)
                .set(MarketPrice::getReviewNote, note)
                .set(MarketPrice::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw ContentErrors.stateChanged("这条价格已由其他管理员复核，列表已刷新");
        }
        if ("PASS".equals(result)) {
            recalculateAfterConfig(price, price.getPriceDate());
        } else {
            notifyReturned(price, note, now);
        }
        log(price, "PASS".equals(result) ? "REVIEW_PASS" : "REVIEW_RETURN", adminId,
                "复核 " + configLabel(price), note, status);
        return detail(id);
    }

    private MarketPriceAdminVos.DetailVO reviewCorrection(MarketPrice price, Long correctionId, String result,
                                                          String note, Long adminId, LocalDateTime now) {
        MarketPriceCorrection correction = correctionMapper.selectById(correctionId);
        if (correction == null || !"PENDING_REVIEW".equals(correction.getStatus())
                || !price.getId().equals(correction.getPriceId())) {
            throw ContentErrors.stateChanged("这条更正已处理，列表已刷新");
        }
        if ("PASS".equals(result)) {
            int version = price.getVersion() == null ? 0 : price.getVersion();
            int rows = priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                    .eq(MarketPrice::getId, price.getId())
                    .eq(MarketPrice::getVersion, version)
                    .set(MarketPrice::getPrice, correction.getNewPrice())
                    .set(MarketPrice::getChangeRate, correction.getChangeRateAfter())
                    .set(MarketPrice::getCorrected, true)
                    .set(MarketPrice::getCorrectedAt, now)
                    .set(MarketPrice::getUpdatedAt, now)
                    .setSql("version = version + 1"));
            if (rows == 0) {
                throw ContentErrors.versionConflict();
            }
            correction.setStatus("APPLIED");
            correction.setPendingFlag(null);
            correction.setReviewerId(adminId);
            correction.setReviewedAt(now);
            correction.setReviewNote(note);
            correctionMapper.updateById(correction);
            clearCorrectionPending(correction.getId());
            recalculateAfterConfig(price, price.getPriceDate());
        } else {
            correction.setStatus("RETURNED");
            correction.setPendingFlag(null);
            correction.setReviewerId(adminId);
            correction.setReviewedAt(now);
            correction.setReviewNote(note);
            correctionMapper.updateById(correction);
            clearCorrectionPending(correction.getId());
            notifyReturned(price, note, now);
        }
        log(price, "PASS".equals(result) ? "REVIEW_PASS" : "REVIEW_RETURN", adminId,
                "复核更正 " + configLabel(price), note, correction.getStatus());
        return detail(price.getId());
    }

    /** updateById 会跳过 null，待复核标记要显式清空，否则同一条价格再更正会撞唯一键 */
    private void clearCorrectionPending(Long correctionId) {
        correctionMapper.update(null, Wrappers.<MarketPriceCorrection>lambdaUpdate()
                .eq(MarketPriceCorrection::getId, correctionId)
                .set(MarketPriceCorrection::getPendingFlag, null));
    }

    @Transactional
    public MarketPriceAdminVos.CorrectionResultVO correct(Long id, ContentRequests.PriceCorrectionRequest request) {
        MarketPrice price = requirePrice(id);
        if (!"PUBLISHED".equals(price.getStatus())) {
            throw ContentErrors.stateChanged("只有已发布的价格可以更正");
        }
        if (correctionMapper.selectPending(id) != null) {
            throw ContentErrors.stateChanged("这条价格已有待复核的更正");
        }
        if (!calculator.validPrice(request == null ? null : request.getNewPrice())) {
            throw ContentErrors.field("new_price", "价格必须大于 0，最多保留 2 位小数");
        }
        if (request.getNewPrice().compareTo(price.getPrice()) == 0) {
            throw ContentErrors.field("new_price", "更正后的价格不能与原价相同");
        }
        String reason = trim(request.getReason());
        if (reason == null) {
            throw ContentErrors.reasonEmpty("请填写更正原因");
        }
        if (reason.codePointCount(0, reason.length()) > 100) {
            throw ContentErrors.field("reason", "更正原因不能超过 100 字");
        }
        int version = request.getVersion() == null ? -1 : request.getVersion();
        if (version != (price.getVersion() == null ? 0 : price.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        BigDecimal rateAfter = calculator.rate(request.getNewPrice(), price.getPrevPrice());
        boolean needReview = calculator.needReview(rateAfter);
        MarketPriceCorrection correction = new MarketPriceCorrection();
        correction.setPriceId(id);
        correction.setPriceDate(price.getPriceDate());
        correction.setOldPrice(price.getPrice());
        correction.setNewPrice(request.getNewPrice());
        correction.setChangeRateAfter(rateAfter);
        correction.setReason(reason);
        correction.setCorrectedBy(adminId);
        correction.setCorrectedAt(now);
        if (needReview) {
            correction.setStatus("PENDING_REVIEW");
            correction.setPendingFlag(true);
            try {
                correctionMapper.insert(correction);
            } catch (DuplicateKeyException e) {
                throw ContentErrors.stateChanged("这条价格已有待复核的更正");
            }
        } else {
            correction.setStatus("APPLIED");
            correction.setPendingFlag(null);
            int rows = priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                    .eq(MarketPrice::getId, id)
                    .eq(MarketPrice::getVersion, version)
                    .eq(MarketPrice::getStatus, "PUBLISHED")
                    .set(MarketPrice::getPrice, request.getNewPrice())
                    .set(MarketPrice::getChangeRate, rateAfter)
                    .set(MarketPrice::getCorrected, true)
                    .set(MarketPrice::getCorrectedAt, now)
                    .set(MarketPrice::getUpdatedAt, now)
                    .setSql("version = version + 1"));
            if (rows == 0) {
                throw ContentErrors.versionConflict();
            }
            correctionMapper.insert(correction);
            price.setPrice(request.getNewPrice());
            recalculateAfterConfig(price, price.getPriceDate());
        }
        log(price, "CORRECT", adminId, "更正 " + configLabel(price), reason, correction.getStatus());
        MarketPriceAdminVos.CorrectionResultVO vo = new MarketPriceAdminVos.CorrectionResultVO();
        vo.setCorrectionId(correction.getId());
        vo.setPriceDate(price.getPriceDate());
        vo.setStatus(correction.getStatus());
        vo.setStatusText(ContentTexts.correctionStatus(correction.getStatus()));
        if (!needReview) {
            vo.setRecalculated(recalculated(price, price.getPriceDate()));
        }
        return vo;
    }

    @Transactional
    public MarketPriceAdminVos.DetailVO revoke(Long id, ContentRequests.MarketRevokeRequest request) {
        MarketPrice price = requirePrice(id);
        if (!"PUBLISHED".equals(price.getStatus())) {
            throw ContentErrors.stateChanged("只有已发布的价格可以撤销");
        }
        if (!BeijingTime.today().equals(price.getPriceDate())) {
            throw ContentErrors.stateChanged("只能撤销当天的价格");
        }
        int version = request == null || request.getVersion() == null ? -1 : request.getVersion();
        if (version != (price.getVersion() == null ? 0 : price.getVersion())) {
            throw ContentErrors.versionConflict();
        }
        String reason = trim(request == null ? null : request.getReason());
        if (reason != null && reason.codePointCount(0, reason.length()) > 100) {
            throw ContentErrors.field("reason", "原因不能超过 100 字");
        }
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        int rows = priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                .eq(MarketPrice::getId, id)
                .eq(MarketPrice::getStatus, "PUBLISHED")
                .eq(MarketPrice::getVersion, version)
                .set(MarketPrice::getStatus, "OFFLINE")
                .set(MarketPrice::getRemark, reason)
                .set(MarketPrice::getReviewNote, reason)
                .set(MarketPrice::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw ContentErrors.stateChanged("这条价格已撤销，列表已刷新");
        }
        log(price, "WITHDRAW", adminId, "撤销 " + configLabel(price), reason, "OFFLINE");
        return detail(id);
    }

    @Transactional
    public List<MarketPriceAdminVos.ConfigVO> saveConfigs(ContentRequests.MarketConfigSaveRequest request) {
        AdminContext.LoginAdmin admin = AdminContext.get();
        if (admin == null || !admin.isSuperAdmin()) {
            throw new BizException(ErrorCode.ADMIN_PERMISSION_DENIED, "只有超级管理员可以修改发布配置");
        }
        if (request == null || !StringUtils.hasText(request.getProvinceCode())) {
            throw ContentErrors.field("province_code", "请选择地区");
        }
        String provinceCode = request.getProvinceCode().trim();
        if (!provinceCode.matches("\\d{6}")) {
            throw ContentErrors.field("province_code", "地区编码不正确");
        }
        String provinceName = ContentTexts.provinceName(provinceCode);
        List<ContentRequests.MarketConfigSaveRequest.ConfigItem> items =
                request.getConfigs() == null ? List.of() : request.getConfigs();
        List<FieldErrorVO> errors = new ArrayList<>();
        Set<String> combos = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            ContentRequests.MarketConfigSaveRequest.ConfigItem item = items.get(i);
            String prefix = "configs[" + i + "]";
            if (item.getCategoryId() == null) {
                errors.add(new FieldErrorVO(prefix + ".category_id", "请选择品类"));
            }
            if (!StringUtils.hasText(item.getPriceType()) || !PRICE_TYPES.contains(item.getPriceType())) {
                errors.add(new FieldErrorVO(prefix + ".price_type", "请选择价格类型"));
            }
            long breedId = item.getBreedId() == null ? 0L : item.getBreedId();
            if (breedId != 0) {
                Long categoryId = lookupMapper.selectBreedCategoryId(breedId);
                if (categoryId == null || !categoryId.equals(item.getCategoryId())) {
                    errors.add(new FieldErrorVO(prefix + ".breed_id", "品种不属于所选品类"));
                }
            }
            String key = item.getCategoryId() + ":" + breedId + ":" + item.getPriceType();
            if (!combos.add(key)) {
                errors.add(new FieldErrorVO(prefix + ".category_id", "同一地区、品类、品种和价格类型的配置重复"));
            }
            if (StringUtils.hasText(item.getDefaultUnit())
                    && !MarketPriceCalculator.PRICE_UNITS.contains(item.getDefaultUnit())) {
                errors.add(new FieldErrorVO(prefix + ".default_unit", "计价单位不正确"));
            }
        }
        if (!errors.isEmpty()) {
            throw ContentErrors.fields(errors);
        }
        List<MarketPriceConfig> existing = configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                .eq(MarketPriceConfig::getProvinceCode, provinceCode));
        Map<Long, MarketPriceConfig> byId = new LinkedHashMap<>();
        Map<String, MarketPriceConfig> byCombo = new HashMap<>();
        int maxSort = 0;
        for (MarketPriceConfig config : existing) {
            byId.put(config.getId(), config);
            byCombo.put(configKey(config), config);
            maxSort = Math.max(maxSort, config.getSortNo() == null ? 0 : config.getSortNo());
        }
        Set<Long> touched = new HashSet<>();
        for (ContentRequests.MarketConfigSaveRequest.ConfigItem item : items) {
            long breedId = item.getBreedId() == null ? 0L : item.getBreedId();
            String key = item.getCategoryId() + ":" + breedId + ":" + item.getPriceType();
            MarketPriceConfig config = item.getConfigId() == null ? byCombo.get(key) : byId.get(item.getConfigId());
            boolean creating = config == null;
            if (creating) {
                config = new MarketPriceConfig();
                config.setProvinceCode(provinceCode);
                config.setProvinceName(provinceName);
            }
            config.setCategoryId(item.getCategoryId());
            config.setBreedId(breedId);
            config.setPriceType(item.getPriceType());
            config.setDefaultUnit(StringUtils.hasText(item.getDefaultUnit()) ? item.getDefaultUnit()
                    : ("SEEDLING".equals(item.getPriceType()) ? "YUAN_PER_HEAD" : "YUAN_PER_KG"));
            config.setSortNo(item.getSortNo() != null ? item.getSortNo() : (config.getSortNo() != null
                    ? config.getSortNo() : maxSort + 1));
            config.setStatus(Boolean.FALSE.equals(item.getIsEnabled()) ? "DISABLED" : "ENABLED");
            if (creating) {
                configMapper.insert(config);
                maxSort = Math.max(maxSort, config.getSortNo());
            } else {
                configMapper.updateById(config);
            }
            touched.add(config.getId());
        }
        for (MarketPriceConfig config : existing) {
            if (!touched.contains(config.getId()) && !"DISABLED".equals(config.getStatus())) {
                config.setStatus("DISABLED");
                configMapper.updateById(config);
            }
        }
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("MARKET_PRICE")
                .opAction("CONFIG_SAVE")
                .bizType(null)
                .targetName(provinceName + " 行情发布配置")
                .reason(trim(request.getReason()))
                .result("SUCCESS")
                .build());
        return configs(provinceCode);
    }

    // ===================== 内部 =====================

    private record RowBuild(MarketPriceConfig config, MarketPriceAdminVos.RowVO row) {
    }

    private MarketPriceAdminVos.RowVO buildRow(MarketPriceConfig config, MarketPrice price,
                                               MarketPriceCorrection pending, Map<Long, String> categoryNames,
                                               Map<Long, String> breedNames, Map<Long, String> adminNames,
                                               Long adminId, LocalDate date) {
        MarketPriceAdminVos.RowVO row = new MarketPriceAdminVos.RowVO();
        row.setConfigId(config.getId());
        row.setPriceId(price == null ? null : price.getId());
        row.setProvinceCode(config.getProvinceCode());
        row.setProvinceName(config.getProvinceName());
        row.setCategoryId(config.getCategoryId());
        row.setCategoryName(categoryNames.getOrDefault(config.getCategoryId(), "品类"));
        row.setBreedId(config.getBreedId() == null ? 0L : config.getBreedId());
        row.setBreedName(breedName(config, breedNames));
        row.setPriceType(config.getPriceType());
        row.setPriceTypeText(ContentTexts.priceType(config.getPriceType()));
        if (price != null) {
            row.setPrice(price.getPrice());
            row.setPriceUnit(price.getPriceUnit());
            row.setPriceUnitText(price.getPriceUnitText());
            row.setChangeRate(price.getChangeRate());
            row.setPrevPrice(price.getPrevPrice());
            row.setPrevPriceDate(price.getPrevPriceDate());
            row.setEnteredByName(adminNames.get(price.getEnteredBy()));
            row.setEnteredAt(price.getEnteredAt());
        }
        boolean pendingCorrection = pending != null;
        if (price != null && "PUBLISHED".equals(price.getStatus()) && pendingCorrection) {
            row.setRowStatus("PENDING_REVIEW");
            row.setReviewKind("CORRECTION");
        } else if (price != null && "PENDING_REVIEW".equals(price.getStatus())) {
            row.setRowStatus("PENDING_REVIEW");
            row.setReviewKind("NEW_PRICE");
        } else if (price != null && "PUBLISHED".equals(price.getStatus())) {
            row.setRowStatus("PUBLISHED");
        } else {
            row.setRowStatus("UNPUBLISHED");
        }
        row.setRowStatusText(ContentTexts.rowStatus(row.getRowStatus()));
        row.setReviewKindText("CORRECTION".equals(row.getReviewKind()) ? "更正"
                : "NEW_PRICE".equals(row.getReviewKind()) ? "新价" : null);
        row.setIsOverThreshold(row.getChangeRate() != null
                && row.getChangeRate().abs().compareTo(MarketPriceCalculator.REVIEW_THRESHOLD) > 0);
        row.setIsPrevCorrected(isPrevCorrected(config, price));
        if ("PENDING_REVIEW".equals(row.getRowStatus())) {
            row.setCanReview(adminId != null);
        }
        row.setCanCorrect("PUBLISHED".equals(row.getRowStatus()) && !pendingCorrection);
        if ("UNPUBLISHED".equals(row.getRowStatus()) && date.isBefore(BeijingTime.today())) {
            row.setUnpublishedText("当天未发布");
        }
        return row;
    }

    private boolean isPrevCorrected(MarketPriceConfig config, MarketPrice price) {
        if (price == null || price.getPrevPriceDate() == null) {
            return false;
        }
        List<MarketPrice> previous = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getProvinceCode, config.getProvinceCode())
                .eq(MarketPrice::getCategoryId, config.getCategoryId())
                .eq(MarketPrice::getBreedId, config.getBreedId() == null ? 0L : config.getBreedId())
                .eq(MarketPrice::getPriceType, config.getPriceType())
                .eq(MarketPrice::getPriceDate, price.getPrevPriceDate())
                .eq(MarketPrice::getStatus, "PUBLISHED")
                .last("LIMIT 1"));
        return !previous.isEmpty() && Boolean.TRUE.equals(previous.getFirst().getCorrected());
    }

    private List<MarketPriceConfig> enabledConfigs(String provinceCode, Long categoryId) {
        return configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                .eq(MarketPriceConfig::getStatus, "ENABLED")
                .eq(StringUtils.hasText(provinceCode), MarketPriceConfig::getProvinceCode, provinceCode)
                .eq(categoryId != null, MarketPriceConfig::getCategoryId, categoryId)
                .orderByAsc(MarketPriceConfig::getSortNo)
                .orderByAsc(MarketPriceConfig::getId));
    }

    private Map<Long, MarketPrice> pricesByConfig(LocalDate date, List<MarketPriceConfig> configs) {
        if (configs.isEmpty()) {
            return Map.of();
        }
        List<Long> categoryIds = configs.stream().map(MarketPriceConfig::getCategoryId).distinct().toList();
        List<String> provinces = configs.stream().map(MarketPriceConfig::getProvinceCode).distinct().toList();
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getPriceDate, date)
                .in(MarketPrice::getProvinceCode, provinces)
                .in(MarketPrice::getCategoryId, categoryIds));
        Map<String, MarketPrice> byCombo = new HashMap<>();
        for (MarketPrice price : prices) {
            byCombo.put(comboKey(price.getProvinceCode(), price.getCategoryId(), price.getBreedId(),
                    price.getPriceType()), price);
        }
        Map<Long, MarketPrice> result = new HashMap<>();
        for (MarketPriceConfig config : configs) {
            MarketPrice price = byCombo.get(comboKey(config.getProvinceCode(), config.getCategoryId(),
                    config.getBreedId(), config.getPriceType()));
            if (price != null) {
                result.put(config.getId(), price);
            }
        }
        return result;
    }

    private Map<Long, MarketPriceCorrection> pendingCorrections(List<Long> priceIds) {
        if (priceIds == null || priceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MarketPriceCorrection> result = new HashMap<>();
        for (MarketPriceCorrection correction : correctionMapper.selectList(Wrappers.<MarketPriceCorrection>lambdaQuery()
                .in(MarketPriceCorrection::getPriceId, priceIds)
                .eq(MarketPriceCorrection::getPendingFlag, true))) {
            result.put(correction.getPriceId(), correction);
        }
        return result;
    }

    private MarketPrice findPrice(MarketPriceConfig config, LocalDate date) {
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getPriceDate, date)
                .eq(MarketPrice::getProvinceCode, config.getProvinceCode())
                .eq(MarketPrice::getCategoryId, config.getCategoryId())
                .eq(MarketPrice::getBreedId, config.getBreedId() == null ? 0L : config.getBreedId())
                .eq(MarketPrice::getPriceType, config.getPriceType())
                .last("LIMIT 1"));
        return prices.isEmpty() ? null : prices.getFirst();
    }

    private MarketPrice lastPublished(MarketPriceConfig config, LocalDate before) {
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, "PUBLISHED")
                .eq(MarketPrice::getProvinceCode, config.getProvinceCode())
                .eq(MarketPrice::getCategoryId, config.getCategoryId())
                .eq(MarketPrice::getBreedId, config.getBreedId() == null ? 0L : config.getBreedId())
                .eq(MarketPrice::getPriceType, config.getPriceType())
                .lt(MarketPrice::getPriceDate, before)
                .orderByDesc(MarketPrice::getPriceDate)
                .last("LIMIT 1"));
        return prices.isEmpty() ? null : prices.getFirst();
    }

    /** 补录/更正后重算后续已发布日期的 prev_price、change_rate（只更新显示，不触发复核） */
    private void recalculateAfter(MarketPriceConfig config, LocalDate date, Long currentPriceId) {
        List<MarketPrice> rows = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getProvinceCode, config.getProvinceCode())
                .eq(MarketPrice::getCategoryId, config.getCategoryId())
                .eq(MarketPrice::getBreedId, config.getBreedId() == null ? 0L : config.getBreedId())
                .eq(MarketPrice::getPriceType, config.getPriceType())
                .eq(MarketPrice::getStatus, "PUBLISHED")
                .gt(MarketPrice::getPriceDate, date)
                .orderByAsc(MarketPrice::getPriceDate));
        if (rows.isEmpty()) {
            return;
        }
        MarketPrice previous = findPrice(config, date);
        if (previous == null || !"PUBLISHED".equals(previous.getStatus())) {
            previous = lastPublished(config, date);
        }
        for (MarketPrice row : rows) {
            BigDecimal rate = calculator.rate(row.getPrice(), previous == null ? null : previous.getPrice());
            priceMapper.update(null, Wrappers.<MarketPrice>lambdaUpdate()
                    .eq(MarketPrice::getId, row.getId())
                    .set(MarketPrice::getPrevPrice, previous == null ? null : previous.getPrice())
                    .set(MarketPrice::getPrevPriceDate, previous == null ? null : previous.getPriceDate())
                    .set(MarketPrice::getChangeRate, rate));
            previous = row;
        }
    }

    private void recalculateAfterConfig(MarketPrice price, LocalDate date) {
        MarketPriceConfig config = syntheticConfig(price);
        recalculateAfter(config, date, price.getId());
    }

    /**
     * ADM-C15 导入后重算后续已发布日期的 prev_price / change_rate。
     * 与补录、更正使用同一套口径；只更新显示，不触发复核。
     */
    public void recalculateAfterImport(MarketPriceConfig config, LocalDate date, Long currentPriceId) {
        recalculateAfter(config, date, currentPriceId);
    }

    private List<MarketPriceAdminVos.RecalculatedItem> recalculated(MarketPrice price, LocalDate date) {
        List<MarketPrice> rows = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getProvinceCode, price.getProvinceCode())
                .eq(MarketPrice::getCategoryId, price.getCategoryId())
                .eq(MarketPrice::getBreedId, price.getBreedId())
                .eq(MarketPrice::getPriceType, price.getPriceType())
                .gt(MarketPrice::getPriceDate, date)
                .orderByAsc(MarketPrice::getPriceDate));
        List<MarketPriceAdminVos.RecalculatedItem> result = new ArrayList<>();
        for (MarketPrice row : rows) {
            MarketPriceAdminVos.RecalculatedItem item = new MarketPriceAdminVos.RecalculatedItem();
            item.setPriceDate(row.getPriceDate());
            item.setOldRate(row.getChangeRate());
            item.setNewRate(calculator.rate(row.getPrice(), price.getPrice()));
            result.add(item);
        }
        return result;
    }

    private void notifyReturned(MarketPrice price, String note, LocalDateTime now) {
        Long receiver = price.getEnteredBy();
        if (receiver == null) {
            return;
        }
        AdminMessageCommand command = AdminMessageCommand.builder()
                .adminId(receiver)
                .msgType("MARKET_PRICE_RETURNED")
                .level("NORMAL")
                .title("你录入的行情被退回")
                .summary(configLabel(price) + "：" + note)
                .bizType("MARKET_PRICE")
                .bizId(price.getId())
                .routePage("WA-C02")
                .build();
        adminMessageService.send(command);
    }

    private MarketPriceAdminVos.CorrectionVO correctionVO(MarketPriceCorrection correction, Map<Long, String> names) {
        MarketPriceAdminVos.CorrectionVO vo = new MarketPriceAdminVos.CorrectionVO();
        vo.setCorrectionId(correction.getId());
        vo.setPriceDate(correction.getPriceDate());
        vo.setOldPrice(correction.getOldPrice());
        vo.setNewPrice(correction.getNewPrice());
        vo.setChangeRateAfter(correction.getChangeRateAfter());
        vo.setReason(correction.getReason());
        vo.setStatus(correction.getStatus());
        vo.setStatusText(ContentTexts.correctionStatus(correction.getStatus()));
        vo.setCorrectedByName(names.get(correction.getCorrectedBy()));
        vo.setCorrectedAt(correction.getCorrectedAt());
        vo.setReviewerName(names.get(correction.getReviewerId()));
        vo.setReviewedAt(correction.getReviewedAt());
        vo.setReviewNote(correction.getReviewNote());
        return vo;
    }

    private MarketPriceAdminVos.BatchItem batchItem(Long configId, Long priceId, String status, BigDecimal rate) {
        MarketPriceAdminVos.BatchItem item = new MarketPriceAdminVos.BatchItem();
        item.setConfigId(configId);
        item.setPriceId(priceId);
        item.setStatus(status);
        item.setChangeRate(rate);
        return item;
    }

    private MarketPrice requirePrice(Long id) {
        MarketPrice price = id == null ? null : priceMapper.selectById(id);
        if (price == null) {
            throw ContentErrors.notFound();
        }
        return price;
    }

    private Long priceConfigId(MarketPrice price) {
        List<MarketPriceConfig> configs = configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                .eq(MarketPriceConfig::getProvinceCode, price.getProvinceCode())
                .eq(MarketPriceConfig::getCategoryId, price.getCategoryId())
                .eq(MarketPriceConfig::getBreedId, price.getBreedId() == null ? 0L : price.getBreedId())
                .eq(MarketPriceConfig::getPriceType, price.getPriceType())
                .last("LIMIT 1"));
        return configs.isEmpty() ? null : configs.getFirst().getId();
    }

    private MarketPriceConfig syntheticConfig(MarketPrice price) {
        MarketPriceConfig config = new MarketPriceConfig();
        config.setId(priceConfigId(price));
        config.setProvinceCode(price.getProvinceCode());
        config.setProvinceName(price.getProvinceName());
        config.setCategoryId(price.getCategoryId());
        config.setBreedId(price.getBreedId());
        config.setPriceType(price.getPriceType());
        config.setSortNo(0);
        return config;
    }

    private Map<Long, String> categoryNames(List<MarketPriceConfig> configs) {
        List<Long> ids = configs.stream().map(MarketPriceConfig::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (NameRow row : lookupMapper.selectCategoryNames(ids)) {
            result.put(row.getId(), row.getName());
        }
        return result;
    }

    private Map<Long, String> breedNames(List<MarketPriceConfig> configs) {
        List<Long> ids = configs.stream().map(MarketPriceConfig::getBreedId)
                .filter(id -> id != null && id != 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (NameRow row : lookupMapper.selectBreedNames(ids)) {
            result.put(row.getId(), row.getName());
        }
        return result;
    }

    private Map<Long, String> adminNames(List<Long> ids) {
        List<Long> safe = ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (safe.isEmpty()) {
            // 示例行情没有录入人：调用方会用 null 键取名字，Map.of() 的 get(null) 会抛空指针
            return new HashMap<>();
        }
        Map<Long, String> result = new HashMap<>();
        for (NameRow row : lookupMapper.selectAdminNames(safe)) {
            result.put(row.getId(), row.getName());
        }
        return result;
    }

    private List<Long> collectAdminIds(Iterable<MarketPrice> prices, Iterable<MarketPriceCorrection> corrections) {
        List<Long> ids = new ArrayList<>();
        for (MarketPrice price : prices) {
            ids.add(price.getEnteredBy());
            ids.add(price.getReviewerId());
        }
        for (MarketPriceCorrection correction : corrections) {
            ids.add(correction.getCorrectedBy());
            ids.add(correction.getReviewerId());
        }
        return ids;
    }

    private void log(MarketPrice price, String action, Long adminId, String target, String reason, String status) {
        operationLogService.writeAdminLog(AdminLogCommand.builder()
                .opType("MARKET_PRICE")
                .opAction(action)
                .bizType("MARKET_PRICE")
                .bizId(price.getId())
                .targetName(target)
                .reason(reason)
                .extraText("status=" + status)
                .result("SUCCESS")
                .build());
    }

    private static String configLabel(MarketPriceConfig config) {
        return config.getProvinceName() + " · " + ContentTexts.priceType(config.getPriceType());
    }

    private static String configLabel(MarketPrice price) {
        return price.getProvinceName() + " · " + ContentTexts.priceType(price.getPriceType()) + " · "
                + BeijingTime.format(price.getPriceDate());
    }

    private static String breedName(MarketPriceConfig config, Map<Long, String> names) {
        long breedId = config.getBreedId() == null ? 0L : config.getBreedId();
        return breedId == 0 ? "全部品种" : names.getOrDefault(breedId, "品种");
    }

    private static String defaultUnit(MarketPriceConfig config) {
        if (StringUtils.hasText(config.getDefaultUnit())) {
            return config.getDefaultUnit();
        }
        return "SEEDLING".equals(config.getPriceType()) ? "YUAN_PER_HEAD" : "YUAN_PER_KG";
    }

    private static int rowOrder(String status) {
        return switch (status) {
            case "PENDING_REVIEW" -> 0;
            case "PUBLISHED" -> 1;
            default -> 2;
        };
    }

    private static String comboKey(String provinceCode, Long categoryId, Long breedId, String priceType) {
        return provinceCode + ":" + categoryId + ":" + (breedId == null ? 0 : breedId) + ":" + priceType;
    }

    private static String configKey(MarketPriceConfig config) {
        return config.getCategoryId() + ":" + (config.getBreedId() == null ? 0 : config.getBreedId())
                + ":" + config.getPriceType();
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
