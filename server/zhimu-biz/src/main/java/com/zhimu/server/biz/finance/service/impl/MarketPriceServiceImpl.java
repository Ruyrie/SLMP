package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.entity.MarketPriceConfig;
import com.zhimu.server.biz.finance.mapper.MarketPriceConfigMapper;
import com.zhimu.server.biz.finance.mapper.MarketPriceMapper;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.MarketVos;
import com.zhimu.server.biz.finance.vo.OverviewVos;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API-E30–E32 行情；价格只读 t_market_price 中 PUBLISHED 的行，不接外部数据源。 */
@Service
@RequiredArgsConstructor
public class MarketPriceServiceImpl implements MarketPriceService {

    private static final List<MarketVos.PriceTypeOption> PRICE_TYPES = List.of(
            priceType(FinanceConst.MARKET_SLAUGHTER, "出栏价"),
            priceType(FinanceConst.MARKET_SEEDLING, "种苗价"));

    private final MarketPriceConfigMapper configMapper;
    private final MarketPriceMapper priceMapper;
    private final BreedQueryService breedQueryService;
    private final BatchQueryService batchQueryService;
    private final FinanceSupport support;

    @Override
    public MarketVos.FilterVO filters() {
        Long farmId = support.farmId();
        List<MarketPriceConfig> configs = configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                .eq(MarketPriceConfig::getStatus, "ENABLED")
                .orderByAsc(MarketPriceConfig::getSortNo));
        MarketVos.FilterVO vo = new MarketVos.FilterVO();
        LinkedHashMap<String, String> provinces = new LinkedHashMap<>();
        LinkedHashSet<Long> categoryIds = new LinkedHashSet<>();
        for (MarketPriceConfig config : configs) {
            provinces.putIfAbsent(config.getProvinceCode(), config.getProvinceName());
            categoryIds.add(config.getCategoryId());
        }
        for (Map.Entry<String, String> entry : provinces.entrySet()) {
            MarketVos.Province province = new MarketVos.Province();
            province.setCode(entry.getKey());
            province.setName(entry.getValue());
            vo.getProvinces().add(province);
        }
        for (Long categoryId : categoryIds) {
            MarketVos.CategoryOption category = new MarketVos.CategoryOption();
            category.setId(categoryId);
            category.setName(support.categoryName(categoryId));
            vo.getCategories().add(category);

            MarketVos.BreedOption all = new MarketVos.BreedOption();
            all.setCategoryId(categoryId);
            all.setBreedId(0L);
            all.setName("全部品种");
            vo.getBreeds().add(all);
            for (BreedQueryService.BreedInfo breed : breedQueryService.listEnabled(categoryId)) {
                MarketVos.BreedOption option = new MarketVos.BreedOption();
                option.setCategoryId(categoryId);
                option.setBreedId(breed.id());
                option.setName(breed.name());
                vo.getBreeds().add(option);
            }
        }
        vo.getPriceTypes().addAll(PRICE_TYPES);
        MarketVos.MarketDefaults defaults = new MarketVos.MarketDefaults();
        defaults.setProvinceCode(defaultProvinceCode(farmId));
        defaults.setCategoryId(defaultCategoryId(farmId));
        defaults.setBreedId(0L);
        defaults.setPriceType(FinanceConst.MARKET_SLAUGHTER);
        vo.setDefaults(defaults);
        Farm farm = support.farm(farmId);
        vo.setDefaultDesc("按" + (farm == null ? "当前养殖场" : farm.getName()) + "所在地区和在养品类默认筛选");
        return vo;
    }

    @Override
    public MarketVos.TrendVO trend(FinanceQueries.MarketPriceQuery query) {
        validate(query);
        // 未传 range_days 时按 App 默认的近 30 天（之前直接拆箱会 500）
        int range = query.getRangeDays() == null ? 30 : query.getRangeDays();
        FinanceErrors.require(range == 7 || range == 30 || range == 90, "range_days", "请选择 7 / 30 / 90 天");
        LocalDate today = BeijingTime.today();
        LocalDate start = today.minusDays(range - 1L);
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, FinanceConst.MARKET_PUBLISHED)
                .eq(MarketPrice::getProvinceCode, query.getProvinceCode())
                .eq(MarketPrice::getCategoryId, query.getCategoryId())
                .eq(MarketPrice::getBreedId, query.getBreedId() == null ? 0L : query.getBreedId())
                .eq(MarketPrice::getPriceType, query.getPriceType())
                .between(MarketPrice::getPriceDate, start, today)
                .orderByAsc(MarketPrice::getPriceDate));
        MarketVos.TrendVO vo = new MarketVos.TrendVO();
        vo.setEmpty(prices.isEmpty());
        if (prices.isEmpty()) {
            vo.setSeries(new ArrayList<>());
            return vo;
        }
        Map<LocalDate, MarketPrice> byDate = new LinkedHashMap<>();
        prices.forEach(p -> byDate.put(p.getPriceDate(), p));
        List<MarketVos.PricePoint> series = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            MarketPrice price = byDate.get(date);
            MarketVos.PricePoint point = new MarketVos.PricePoint();
            point.setDate(FinanceSupport.format(date));
            point.setPrice(price == null ? null : price.getPrice());
            point.setCorrected(price != null && Boolean.TRUE.equals(price.getCorrected()));
            series.add(point);
        }
        vo.setSeries(series);
        MarketPrice latest = prices.get(prices.size() - 1);
        MarketVos.Latest latestVo = new MarketVos.Latest();
        latestVo.setPrice(latest.getPrice());
        latestVo.setPriceUnit(latest.getPriceUnit());
        latestVo.setPriceUnitText(latest.getPriceUnitText());
        latestVo.setPriceDate(FinanceSupport.format(latest.getPriceDate()));
        vo.setLatest(latestVo);
        if (prices.size() > 1) {
            MarketPrice previous = prices.get(prices.size() - 2);
            MarketVos.PriceChange change = new MarketVos.PriceChange();
            BigDecimal rate = FinanceSupport.rate(latest.getPrice(), previous.getPrice());
            change.setChangeRate(rate);
            change.setDirection(FinanceSupport.trendOf(rate));
            change.setCompareDate(FinanceSupport.format(previous.getPriceDate()));
            change.setComparePrice(previous.getPrice());
            vo.setChange(change);
        }
        vo.setStats(stats(prices));
        return vo;
    }

    @Override
    public PageVO<MarketVos.DailyPriceVO> daily(FinanceQueries.MarketPriceQuery query) {
        validate(query);
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, FinanceConst.MARKET_PUBLISHED)
                .eq(MarketPrice::getProvinceCode, query.getProvinceCode())
                .eq(MarketPrice::getCategoryId, query.getCategoryId())
                .eq(MarketPrice::getBreedId, query.getBreedId() == null ? 0L : query.getBreedId())
                .eq(MarketPrice::getPriceType, query.getPriceType())
                .orderByAsc(MarketPrice::getPriceDate));
        Map<LocalDate, MarketPrice> previous = new LinkedHashMap<>();
        MarketPrice last = null;
        for (MarketPrice price : prices) {
            if (last != null) {
                previous.put(price.getPriceDate(), last);
            }
            last = price;
        }
        List<MarketPrice> desc = new ArrayList<>(prices);
        desc.sort(Comparator.comparing(MarketPrice::getPriceDate).reversed());
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, desc.size());
        int to = Math.min(from + size, desc.size());
        List<MarketVos.DailyPriceVO> list = new ArrayList<>();
        for (MarketPrice price : desc.subList(from, to)) {
            MarketVos.DailyPriceVO vo = new MarketVos.DailyPriceVO();
            vo.setPriceDate(FinanceSupport.format(price.getPriceDate()));
            vo.setPrice(price.getPrice());
            vo.setPriceUnitText(price.getPriceUnitText());
            MarketPrice prev = previous.get(price.getPriceDate());
            BigDecimal rate = prev == null ? null : FinanceSupport.rate(price.getPrice(), prev.getPrice());
            vo.setChangeRate(rate);
            vo.setDirection(FinanceSupport.trendOf(rate));
            vo.setCorrected(Boolean.TRUE.equals(price.getCorrected()));
            vo.setCorrectedAt(FinanceSupport.format(price.getCorrectedAt()));
            list.add(vo);
        }
        PageVO<MarketVos.DailyPriceVO> vo = new PageVO<>(list, desc.size(), page, size);
        return vo;
    }

    @Override
    public String defaultProvinceCode(Long farmId) {
        List<MarketPriceConfig> configs = configMapper.selectList(Wrappers.<MarketPriceConfig>lambdaQuery()
                .eq(MarketPriceConfig::getStatus, "ENABLED")
                .orderByAsc(MarketPriceConfig::getSortNo));
        Farm farm = support.farm(farmId);
        if (farm != null && farm.getProvince() != null) {
            for (MarketPriceConfig config : configs) {
                if (farm.getProvince().contains(config.getProvinceName())
                        || config.getProvinceName().contains(farm.getProvince())) {
                    return config.getProvinceCode();
                }
            }
        }
        return configs.isEmpty() ? null : configs.get(0).getProvinceCode();
    }

    @Override
    public Long defaultCategoryId(Long farmId) {
        List<BatchInfo> active = batchQueryService.listActive(farmId);
        Map<Long, Integer> stock = new LinkedHashMap<>();
        for (BatchInfo batch : active) {
            stock.merge(batch.categoryId(), batch.currentCount() == null ? 0 : batch.currentCount(), Integer::sum);
        }
        Long best = stock.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (best != null) {
            return best;
        }
        Farm farm = support.farm(farmId);
        return farm == null ? null : farm.getMainCategoryId();
    }

    @Override
    public MarketPrice latest(Long farmId, Long categoryId, String priceType, LocalDate date) {
        String province = defaultProvinceCode(farmId);
        if (province == null || categoryId == null) {
            return null;
        }
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, FinanceConst.MARKET_PUBLISHED)
                .eq(MarketPrice::getProvinceCode, province)
                .eq(MarketPrice::getCategoryId, categoryId)
                .eq(MarketPrice::getBreedId, 0L)
                .eq(MarketPrice::getPriceType, priceType == null ? FinanceConst.MARKET_SLAUGHTER : priceType)
                .le(MarketPrice::getPriceDate, date == null ? BeijingTime.today() : date)
                .orderByDesc(MarketPrice::getPriceDate)
                .last("LIMIT 1"));
        return prices.isEmpty() ? null : prices.get(0);
    }

    @Override
    public OverviewVos.PriceCard priceCard(Long farmId) {
        Long categoryId = defaultCategoryId(farmId);
        MarketPrice latest = latest(farmId, categoryId, FinanceConst.MARKET_SLAUGHTER, BeijingTime.today());
        if (latest == null || categoryId == null) {
            return null;
        }
        MarketPrice previous = previous(latest);
        OverviewVos.PriceCard card = new OverviewVos.PriceCard();
        card.setCategoryName(support.categoryName(categoryId));
        card.setProvinceName(latest.getProvinceName());
        card.setPrice(latest.getPrice());
        card.setPriceUnit(latest.getPriceUnit());
        card.setPriceUnitText(latest.getPriceUnitText());
        card.setPriceDate(FinanceSupport.format(latest.getPriceDate()));
        if (previous != null) {
            BigDecimal rate = FinanceSupport.rate(latest.getPrice(), previous.getPrice());
            card.setChangeRate(rate);
            card.setDirection(FinanceSupport.trendOf(rate));
            card.setCompareDate(FinanceSupport.format(previous.getPriceDate()));
            card.setComparePrice(previous.getPrice());
        }
        return card;
    }

    @Override
    public SaleOrderVos.MarketPrice prefillPrice(Long farmId, Long categoryId, LocalDate date) {
        MarketPrice latest = latest(farmId, categoryId, FinanceConst.MARKET_SLAUGHTER, date);
        if (latest == null) {
            return null;
        }
        SaleOrderVos.MarketPrice price = new SaleOrderVos.MarketPrice();
        price.setProvinceName(latest.getProvinceName());
        price.setCategoryName(support.categoryName(latest.getCategoryId()));
        price.setPrice(latest.getPrice());
        price.setPriceUnitText(latest.getPriceUnitText());
        price.setPriceDate(FinanceSupport.format(latest.getPriceDate()));
        return price;
    }

    private MarketPrice previous(MarketPrice latest) {
        List<MarketPrice> prices = priceMapper.selectList(Wrappers.<MarketPrice>lambdaQuery()
                .eq(MarketPrice::getStatus, FinanceConst.MARKET_PUBLISHED)
                .eq(MarketPrice::getProvinceCode, latest.getProvinceCode())
                .eq(MarketPrice::getCategoryId, latest.getCategoryId())
                .eq(MarketPrice::getBreedId, latest.getBreedId())
                .eq(MarketPrice::getPriceType, latest.getPriceType())
                .lt(MarketPrice::getPriceDate, latest.getPriceDate())
                .orderByDesc(MarketPrice::getPriceDate)
                .last("LIMIT 1"));
        return prices.isEmpty() ? null : prices.get(0);
    }

    private static MarketVos.PriceStats stats(List<MarketPrice> prices) {
        MarketVos.PriceStats stats = new MarketVos.PriceStats();
        MarketPrice max = prices.stream().max(Comparator.comparing(MarketPrice::getPrice)).orElse(null);
        MarketPrice min = prices.stream().min(Comparator.comparing(MarketPrice::getPrice)).orElse(null);
        stats.setMaxPrice(max == null ? null : max.getPrice());
        stats.setMaxDate(max == null ? null : FinanceSupport.format(max.getPriceDate()));
        stats.setMinPrice(min == null ? null : min.getPrice());
        stats.setMinDate(min == null ? null : FinanceSupport.format(min.getPriceDate()));
        BigDecimal sum = prices.stream().map(MarketPrice::getPrice).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setAvgPrice(prices.isEmpty() ? null
                : sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP));
        return stats;
    }

    private static void validate(FinanceQueries.MarketPriceQuery query) {
        FinanceErrors.require(query.getProvinceCode() != null && !query.getProvinceCode().isBlank(),
                "province_code", "请选择省份");
        FinanceErrors.require(query.getCategoryId() != null, "category_id", "请选择品类");
        FinanceErrors.require(query.getPriceType() != null, "price_type", "请选择价格类型");
    }

    private static MarketVos.PriceTypeOption priceType(String code, String name) {
        MarketVos.PriceTypeOption option = new MarketVos.PriceTypeOption();
        option.setCode(code);
        option.setName(name);
        return option;
    }
}
