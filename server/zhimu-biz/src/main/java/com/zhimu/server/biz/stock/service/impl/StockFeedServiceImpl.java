package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.convert.StockFlowAssembler;
import com.zhimu.server.biz.stock.request.FeedSaveRequest;
import com.zhimu.server.biz.stock.service.StockFeedService;
import com.zhimu.server.biz.stock.support.FeedStats;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.FeedDetailVO;
import com.zhimu.server.biz.stock.vo.FeedOptionVO;
import com.zhimu.server.biz.stock.vo.FeedStockItemVO;
import com.zhimu.server.biz.stock.vo.FeedStockOverviewVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 饲料库存实现（API-M01–M06）。
 * <p>
 * “需要盘点的饲料”= 有结存、有均价、有近 7 天领用或有预警标记的饲料；从未入过库的饲料只出现在入库下拉里，
 * 不进结存列表与盘点表（与 App mock 一致：5103 教槽料只在 options 中、结存 0 时不可出库）。
 * 近 7 天领用取 t_feed 的统计缓存（StockDailyJob / 流水写入时重算），窗口为今天之前的 7 个完整自然日。
 */
@Service
@RequiredArgsConstructor
public class StockFeedServiceImpl implements StockFeedService {

    private final FeedMapper feedMapper;
    private final StockFlowMapper flowMapper;
    private final StockFlowAssembler flowAssembler;
    private final StockLookups lookups;

    @Override
    public FeedStockOverviewVO overview() {
        List<Feed> active = listActiveFeeds();
        FeedStockOverviewVO vo = new FeedStockOverviewVO();
        vo.setFeedCount(active.size());
        vo.setHasFeed(!active.isEmpty());
        vo.setFarmReadonly(lookups.farmReadonly());
        vo.setStockAmount(StockQty.amount(active.stream()
                .map(feed -> StockQty.zeroIfNull(feed.getStockAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)));

        List<Feed> low = active.stream().filter(feed -> FeedStats.of(feed).lowStock()).toList();
        vo.setWarnCount(low.size());
        vo.setBanner(banner(low));

        BigDecimal usageTotal = BigDecimal.ZERO;
        List<FeedStockOverviewVO.UsageItem> usageItems = new ArrayList<>();
        for (Feed feed : active) {
            BigDecimal usage = StockQty.zeroIfNull(feed.getUsage7dQty());
            if (usage.signum() <= 0) {
                continue;
            }
            FeedStockOverviewVO.UsageItem item = new FeedStockOverviewVO.UsageItem();
            item.setFeedId(feed.getId());
            item.setFeedName(feed.getName());
            item.setQty(StockQty.qty(usage));
            usageItems.add(item);
            usageTotal = usageTotal.add(usage);
        }
        vo.setUsage7dTotal(StockQty.qty(usageTotal));
        vo.setUsage7dItems(usageItems);

        List<StockFlow> recent = flowMapper.selectList(Wrappers.<StockFlow>lambdaQuery()
                .eq(StockFlow::getMaterialType, StockCodes.MATERIAL_FEED)
                .and(w -> w.isNull(StockFlow::getVoidReason).or()
                        .ne(StockFlow::getVoidReason, StockCodes.VOID_CORRECTED))
                .orderByDesc(StockFlow::getBizDate)
                .orderByDesc(StockFlow::getOccurAt)
                .orderByDesc(StockFlow::getId)
                .last("LIMIT 5"));
        vo.setRecentFlows(flowAssembler.toVos(recent));
        vo.setCanOtherOut(active.stream()
                .anyMatch(feed -> StockQty.zeroIfNull(feed.getStockQty()).signum() > 0));
        return vo;
    }

    @Override
    public PageVO<FeedStockItemVO> page(int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size <= 0 ? 20 : Math.min(size, 100);
        List<Feed> active = new ArrayList<>(listActiveFeeds());
        active.sort(Comparator.comparing((Feed feed) -> FeedStats.of(feed).lowStock()).reversed()
                .thenComparing(feed -> feed.getName() == null ? "" : feed.getName()));
        long total = active.size();
        int from = (pageNo - 1) * pageSize;
        List<FeedStockItemVO> items = new ArrayList<>();
        for (int i = from; i < Math.min(from + pageSize, active.size()); i++) {
            items.add(item(active.get(i)));
        }
        return PageVO.of(items, total, pageNo, pageSize);
    }

    @Override
    public List<FeedOptionVO> options(String purpose, String keyword) {
        boolean otherOut = StockCodes.PURPOSE_OTHER_OUT.equalsIgnoreCase(purpose);
        List<Feed> feeds = feedMapper.selectList(Wrappers.<Feed>lambdaQuery()
                .like(StringUtils.hasText(keyword), Feed::getName,
                        keyword == null ? null : keyword.trim())
                .orderByAsc(Feed::getName)
                .orderByAsc(Feed::getId));
        List<FeedOptionVO> result = new ArrayList<>(feeds.size());
        for (Feed feed : feeds) {
            FeedStats.View view = FeedStats.of(feed);
            FeedOptionVO vo = new FeedOptionVO();
            vo.setFeedId(feed.getId());
            vo.setName(feed.getName());
            vo.setStock(StockQty.feed(feed.getStockQty()));
            vo.setRemainingDays(view.remainingDays());
            vo.setLowStock(view.lowStock());
            boolean hasStock = StockQty.zeroIfNull(feed.getStockQty()).signum() > 0;
            vo.setSelectable(!otherOut || hasStock);
            if (otherOut && !hasStock) {
                vo.setDisabledReason("结存 0 千克，不能出库");
            }
            vo.setAvgPrice(StockQty.price(feed.getAvgPrice()));
            vo.setStockVersion(feed.getVersion());
            result.add(vo);
        }
        return result;
    }

    @Override
    public FeedDetailVO detail(Long id) {
        return detailOf(lookups.requireFeed(id));
    }

    @Override
    @Transactional
    public FeedDetailVO create(FeedSaveRequest request) {
        Long farmId = lookups.farmId();
        checkPack(request);
        String name = request.getName().trim();
        if (existsName(name, null)) {
            throw StockErrors.duplicateName(name);
        }
        Feed feed = new Feed();
        feed.setFarmId(farmId);
        feed.setName(name);
        feed.setFeedTypeCode(request.getFeedTypeCode().trim());
        feed.setSpec(trimToNull(request.getSpec()));
        feed.setUnit(StockCodes.UNIT_KG);
        feed.setPackUnit(trimToNull(request.getPackUnit()));
        feed.setPackSize(request.getPackSize() == null ? null
                : request.getPackSize().setScale(3, java.math.RoundingMode.HALF_UP));
        feed.setCategoryId(request.getCategoryId());
        feed.setMinStock(StockQty.amount(request.getMinStock()));
        feed.setWarnDays(request.getWarnDays() == null ? 7 : request.getWarnDays());
        feed.setStockQty(BigDecimal.ZERO);
        feed.setStockAmount(BigDecimal.ZERO);
        feed.setAvgPrice(BigDecimal.ZERO);
        feed.setUsage7dQty(BigDecimal.ZERO);
        feed.setUsageDays(0);
        feed.setRemainingBasis(StockCodes.BASIS_NONE);
        feed.setIsLowStock(false);
        feed.setIsUnitLocked(false);
        feed.setVersion(0);
        feed.setEditVersion(0);
        feed.setDeleted(0);
        feed.setCreatedBy(lookups.userId());
        feed.setUpdatedBy(lookups.userId());
        feedMapper.insert(feed);
        lookups.log("CREATE", "FEED_CREATE", StockCodes.BIZ_FEED, feed.getId(), feed.getName(), null,
                feed.getName());
        return detailOf(feed);
    }

    @Override
    @Transactional
    public FeedDetailVO update(Long id, FeedSaveRequest request) {
        Feed feed = lookups.requireFeed(id);
        if (request.getEditVersion() == null) {
            throw StockErrors.field("edit_version", "数据已被他人修改，请刷新后重试");
        }
        if (!request.getEditVersion().equals(feed.getEditVersion())) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        checkPack(request);
        String name = request.getName().trim();
        if (existsName(name, id)) {
            throw StockErrors.duplicateName(name);
        }
        int rows = feedMapper.update(null, Wrappers.<Feed>lambdaUpdate()
                .eq(Feed::getId, id)
                .eq(Feed::getEditVersion, request.getEditVersion())
                .set(Feed::getName, name)
                .set(Feed::getFeedTypeCode, request.getFeedTypeCode().trim())
                .set(Feed::getSpec, trimToNull(request.getSpec()))
                .set(Feed::getPackUnit, trimToNull(request.getPackUnit()))
                .set(Feed::getPackSize, request.getPackSize() == null ? null
                        : request.getPackSize().setScale(3, java.math.RoundingMode.HALF_UP))
                .set(Feed::getCategoryId, request.getCategoryId())
                .set(Feed::getMinStock, StockQty.amount(request.getMinStock()))
                .set(Feed::getWarnDays, request.getWarnDays() == null ? 7 : request.getWarnDays())
                .set(Feed::getEditVersion, request.getEditVersion() + 1)
                .set(Feed::getUpdatedBy, lookups.userId()));
        if (rows == 0) {
            throw new com.zhimu.server.common.error.BizException(
                    com.zhimu.server.common.error.ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        lookups.log("UPDATE", "FEED_UPDATE", StockCodes.BIZ_FEED, id, feed.getName(), feed.getName(), name);
        return detailOf(lookups.requireFeed(id));
    }

    @Override
    public List<Feed> listActiveFeeds() {
        List<Feed> feeds = feedMapper.selectList(Wrappers.<Feed>lambdaQuery()
                .orderByAsc(Feed::getName).orderByAsc(Feed::getId));
        List<Feed> active = new ArrayList<>();
        for (Feed feed : feeds) {
            if (active(feed)) {
                active.add(feed);
            }
        }
        return active;
    }

    private static boolean active(Feed feed) {
        return StockQty.zeroIfNull(feed.getStockQty()).signum() > 0
                || StockQty.zeroIfNull(feed.getAvgPrice()).signum() > 0
                || StockQty.zeroIfNull(feed.getUsage7dQty()).signum() > 0
                || feed.getLowStockAt() != null
                || feed.getStatDate() != null;
    }

    private FeedStockItemVO item(Feed feed) {
        FeedStats.View view = FeedStats.of(feed);
        FeedStockItemVO vo = new FeedStockItemVO();
        vo.setFeedId(feed.getId());
        vo.setName(feed.getName());
        vo.setSpec(feed.getSpec());
        vo.setFeedTypeCode(feed.getFeedTypeCode());
        vo.setFeedTypeText(lookups.feedTypeName(feed.getFeedTypeCode()));
        vo.setCategoryText(lookups.categoryName(feed.getCategoryId()));
        vo.setStock(StockQty.feed(feed.getStockQty()));
        vo.setRemainingDays(view.remainingDays());
        vo.setRemainingBasis(view.basis());
        vo.setDailyUseQty(view.dailyUseQty());
        vo.setUsageQty(StockQty.qty(feed.getUsage7dQty()));
        vo.setUsageDays(feed.getUsageDays());
        vo.setAvgPrice(StockQty.price(feed.getAvgPrice()));
        vo.setStockAmount(StockQty.amount(feed.getStockAmount()));
        vo.setMinStock(StockQty.amount(feed.getMinStock()));
        vo.setWarnDays(feed.getWarnDays());
        vo.setLowStock(view.lowStock());
        vo.setLowStockReason(view.reason());
        vo.setLowStockReasonText(StockCodes.lowStockReasonText(view.reason()));
        return vo;
    }

    private FeedDetailVO detailOf(Feed feed) {
        FeedDetailVO vo = new FeedDetailVO();
        vo.setFeedId(feed.getId());
        vo.setName(feed.getName());
        vo.setFeedTypeCode(feed.getFeedTypeCode());
        vo.setFeedTypeText(lookups.feedTypeName(feed.getFeedTypeCode()));
        vo.setSpec(feed.getSpec());
        vo.setUnit(feed.getUnit());
        vo.setUnitText(StockCodes.unitText(feed.getUnit()));
        vo.setPackUnit(feed.getPackUnit());
        vo.setPackSize(feed.getPackSize());
        vo.setCategoryId(feed.getCategoryId());
        vo.setCategoryText(lookups.categoryName(feed.getCategoryId()));
        vo.setMinStock(StockQty.amount(feed.getMinStock()));
        vo.setWarnDays(feed.getWarnDays());
        vo.setUnitLocked(Boolean.TRUE.equals(feed.getIsUnitLocked()));
        vo.setEditVersion(feed.getEditVersion());
        vo.setUpdatedAt(BeijingTime.format(feed.getUpdatedAt()));
        vo.setUpdatedByName(feed.getUpdatedBy() == null ? null : lookups.userName(feed.getUpdatedBy()));
        return vo;
    }

    private FeedStockOverviewVO.Banner banner(List<Feed> low) {
        if (low.isEmpty()) {
            return null;
        }
        FeedStockOverviewVO.Banner banner = new FeedStockOverviewVO.Banner();
        if (low.size() == 1) {
            Feed feed = low.get(0);
            FeedStats.View view = FeedStats.of(feed);
            banner.setKind(FeedStats.bannerKind(view.reason()));
            banner.setFeedId(feed.getId());
            banner.setFeedName(feed.getName());
            banner.setRemainingDays(view.remainingDays());
            banner.setWarnDays(feed.getWarnDays());
            banner.setStock(StockQty.feed(feed.getStockQty()));
            banner.setMinStock(StockQty.amount(feed.getMinStock()));
            banner.setFeedNames(List.of(feed.getName()));
            banner.setPreselectFeedId(feed.getId());
            return banner;
        }
        banner.setKind(StockCodes.BANNER_MULTI);
        banner.setFeedNames(low.stream().map(Feed::getName).toList());
        banner.setPreselectFeedId(low.get(0).getId());
        return banner;
    }

    private boolean existsName(String name, Long excludeId) {
        Long count = feedMapper.selectCount(Wrappers.<Feed>lambdaQuery()
                .eq(Feed::getName, name)
                .ne(excludeId != null, Feed::getId, excludeId));
        return count != null && count > 0;
    }

    private static void checkPack(FeedSaveRequest request) {
        boolean hasUnit = StringUtils.hasText(request.getPackUnit());
        boolean hasSize = request.getPackSize() != null;
        if (hasUnit != hasSize) {
            throw StockErrors.field("pack_size", hasUnit ? "请填写每袋千克数" : "请填写包装单位");
        }
    }

    private static String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
