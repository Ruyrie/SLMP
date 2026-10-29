package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.Stocktake;
import com.zhimu.server.biz.stock.entity.StocktakeItem;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeItemMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeMapper;
import com.zhimu.server.biz.stock.request.StocktakeConfirmRequest;
import com.zhimu.server.biz.stock.service.StockFeedService;
import com.zhimu.server.biz.stock.service.StocktakeService;
import com.zhimu.server.biz.stock.support.FeedStats;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockNumbers;
import com.zhimu.server.biz.stock.support.StockQty;
import com.zhimu.server.biz.stock.vo.StocktakeSheetVO;
import com.zhimu.server.biz.stock.vo.StocktakeVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 盘点实现（模块09 2.6 链 4）：逐项比对账面结存与 stock_version，任一项不一致整单不保存并返回最新账面；
 * 盘盈按当前均价增加库存金额（均价不变），盘亏按均价减少，实盘为 0 时金额清零；相符只写明细。
 */
@Service
@RequiredArgsConstructor
public class StocktakeServiceImpl implements StocktakeService {

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper itemMapper;
    private final FeedMapper feedMapper;
    private final StockFeedService feedService;
    private final StockLedger ledger;
    private final StockFlowWriter flowWriter;
    private final StockNumbers numbers;
    private final StockLookups lookups;

    @Override
    public StocktakeSheetVO sheet() {
        StocktakeSheetVO vo = new StocktakeSheetVO();
        vo.setStocktakeDate(BeijingTime.format(BeijingTime.today()));
        vo.setOperator(lookups.currentUser());
        for (Feed feed : feedService.listActiveFeeds()) {
            vo.getItems().add(sheetItem(feed));
        }
        return vo;
    }

    @Override
    @Transactional
    public StocktakeVO confirm(StocktakeConfirmRequest request, String requestId) {
        Long farmId = lookups.farmId();
        Long userId = lookups.userId();
        List<Long> feedIds = new ArrayList<>();
        Map<Long, BigDecimal> actualByFeed = new LinkedHashMap<>();
        for (StocktakeConfirmRequest.Item item : request.getItems()) {
            if (item.getFeedId() == null) {
                throw StockErrors.field("items.feed_id", "请选择饲料");
            }
            if (feedIds.contains(item.getFeedId())) {
                throw StockErrors.field("items", "同一种饲料只能录入一次");
            }
            feedIds.add(item.getFeedId());
            actualByFeed.put(item.getFeedId(), item.getActualQty());
        }
        Map<Long, Feed> feeds = new HashMap<>();
        List<StocktakeSheetVO.Item> latest = new ArrayList<>();
        for (StocktakeConfirmRequest.Item item : request.getItems()) {
            Feed feed = lookups.requireFeed(item.getFeedId());
            feeds.put(feed.getId(), feed);
            boolean bookChanged = item.getBookQty() == null
                    || StockQty.zeroIfNull(feed.getStockQty()).compareTo(StockQty.qty(item.getBookQty())) != 0;
            boolean versionChanged = item.getStockVersion() == null
                    || !item.getStockVersion().equals(feed.getVersion());
            if (bookChanged || versionChanged) {
                latest.add(sheetItem(feed));
            }
        }
        if (!latest.isEmpty()) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "账面结存已变化，请刷新后重新核对",
                    Map.of("items", latest));
        }

        Stocktake stocktake = new Stocktake();
        stocktake.setFarmId(farmId);
        stocktake.setStocktakeNo(numbers.nextStocktakeNo(BeijingTime.today()));
        stocktake.setStocktakeDate(BeijingTime.today());
        stocktake.setItemCount(0);
        stocktake.setGainCount(0);
        stocktake.setLossCount(0);
        stocktake.setGainAmount(BigDecimal.ZERO);
        stocktake.setLossAmount(BigDecimal.ZERO);
        stocktake.setClientRequestId(requestId);
        stocktake.setVersion(0);
        stocktake.setCreatedBy(userId);
        stocktake.setUpdatedBy(userId);
        stocktakeMapper.insert(stocktake);

        int gainCount = 0;
        int lossCount = 0;
        BigDecimal gainAmount = BigDecimal.ZERO;
        BigDecimal lossAmount = BigDecimal.ZERO;
        for (StocktakeConfirmRequest.Item item : request.getItems()) {
            Feed feed = feeds.get(item.getFeedId());
            BigDecimal bookQty = StockQty.zeroIfNull(feed.getStockQty());
            BigDecimal actualQty = StockQty.qty(item.getActualQty());
            BigDecimal diff = actualQty.subtract(bookQty);
            BigDecimal avg = StockQty.zeroIfNull(feed.getAvgPrice());
            BigDecimal diffAmount = StockQty.amount(diff.multiply(avg));
            String result = diff.signum() > 0 ? StockCodes.RESULT_GAIN
                    : diff.signum() < 0 ? StockCodes.RESULT_LOSS : StockCodes.RESULT_MATCH;

            StocktakeItem detail = new StocktakeItem();
            detail.setStocktakeId(stocktake.getId());
            detail.setFarmId(farmId);
            detail.setFeedId(feed.getId());
            detail.setBookQty(bookQty);
            detail.setActualQty(actualQty);
            detail.setDiffQty(diff);
            detail.setResult(result);
            detail.setAvgPrice(StockQty.price(avg));
            detail.setDiffAmount(diffAmount);
            if (diff.signum() > 0) {
                StockLedger.Move move = ledger.inFeed(feed, diff, diffAmount.abs());
                StockFlow flow = flow(stocktake, feed.getId(), StockCodes.FLOW_STOCKTAKE_GAIN,
                        StockCodes.DIRECTION_IN, diff, avg, diffAmount.abs(), move);
                detail.setFlowId(flow.getId());
                gainCount++;
                gainAmount = gainAmount.add(diffAmount.abs());
            } else if (diff.signum() < 0) {
                StockLedger.Move move = ledger.outFeed(feed, diff.abs());
                StockFlow flow = flow(stocktake, feed.getId(), StockCodes.FLOW_STOCKTAKE_LOSS,
                        StockCodes.DIRECTION_OUT, diff.abs(), move.unitPrice(), move.amount(), move);
                detail.setFlowId(flow.getId());
                lossCount++;
                lossAmount = lossAmount.add(move.amount());
            }
            itemMapper.insert(detail);
        }
        stocktake.setItemCount(request.getItems().size());
        stocktake.setGainCount(gainCount);
        stocktake.setLossCount(lossCount);
        stocktake.setGainAmount(StockQty.amount(gainAmount));
        stocktake.setLossAmount(StockQty.amount(lossAmount));
        stocktakeMapper.updateById(stocktake);
        lookups.log("CREATE", "STOCKTAKE_CONFIRM", StockCodes.BIZ_STOCKTAKE, stocktake.getId(),
                stocktake.getStocktakeNo(), null, Map.of("gain", gainAmount, "loss", lossAmount));
        return get(stocktake.getId());
    }

    @Override
    public PageVO<StocktakeVO> page(int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size <= 0 ? 20 : Math.min(size, 100);
        Long total = stocktakeMapper.selectCount(Wrappers.<Stocktake>lambdaQuery());
        List<Stocktake> rows = stocktakeMapper.selectList(Wrappers.<Stocktake>lambdaQuery()
                .orderByDesc(Stocktake::getStocktakeDate)
                .orderByDesc(Stocktake::getId)
                .last("LIMIT " + pageSize + " OFFSET " + (long) (pageNo - 1) * pageSize));
        List<StocktakeVO> items = new ArrayList<>(rows.size());
        for (Stocktake row : rows) {
            items.add(toVo(row, false));
        }
        return PageVO.of(items, total == null ? 0 : total, pageNo, pageSize);
    }

    @Override
    public StocktakeVO get(Long id) {
        Stocktake stocktake = lookups.requireStocktake(id);
        return toVo(stocktake, true);
    }

    private StocktakeVO toVo(Stocktake stocktake, boolean withItems) {
        StocktakeVO vo = new StocktakeVO();
        vo.setStocktakeId(stocktake.getId());
        vo.setStocktakeNo(stocktake.getStocktakeNo());
        vo.setStocktakeDate(BeijingTime.format(stocktake.getStocktakeDate()));
        vo.setOperator(lookups.userBrief(stocktake.getCreatedBy()));
        vo.setItemCount(stocktake.getItemCount() == null ? 0 : stocktake.getItemCount());
        vo.setGainCount(stocktake.getGainCount() == null ? 0 : stocktake.getGainCount());
        vo.setLossCount(stocktake.getLossCount() == null ? 0 : stocktake.getLossCount());
        vo.setGainAmount(StockQty.amount(stocktake.getGainAmount()));
        vo.setLossAmount(StockQty.amount(stocktake.getLossAmount()));
        vo.setCreatedAt(BeijingTime.format(stocktake.getCreatedAt()));
        vo.setVersion(stocktake.getVersion());
        // 已确认的盘点只能提交修改申请
        vo.setActions(StockActions.requestOnly(true));
        if (withItems) {
            List<StocktakeItem> details = itemMapper.selectList(Wrappers.<StocktakeItem>lambdaQuery()
                    .eq(StocktakeItem::getStocktakeId, stocktake.getId())
                    .orderByAsc(StocktakeItem::getId));
            List<StocktakeVO.Item> items = new ArrayList<>(details.size());
            for (StocktakeItem detail : details) {
                StocktakeVO.Item item = new StocktakeVO.Item();
                item.setItemId(detail.getId() == null ? 0 : detail.getId());
                item.setFeedId(detail.getFeedId());
                Feed feed = feedMapper.selectById(detail.getFeedId());
                item.setFeedName(feed == null ? null : feed.getName());
                item.setUnitText(StockCodes.unitText(StockCodes.UNIT_KG));
                item.setBookQty(StockQty.qty(detail.getBookQty()));
                item.setActualQty(StockQty.qty(detail.getActualQty()));
                item.setDiffQty(StockQty.qty(detail.getDiffQty()));
                item.setResult(detail.getResult());
                item.setResultText(StockCodes.stocktakeResultText(detail.getResult()));
                item.setAvgPrice(StockQty.price(detail.getAvgPrice()));
                item.setDiffAmount(StockQty.amount(detail.getDiffAmount()));
                items.add(item);
            }
            vo.setItems(items);
        }
        return vo;
    }

    private StocktakeSheetVO.Item sheetItem(Feed feed) {
        StocktakeSheetVO.Item item = new StocktakeSheetVO.Item();
        item.setFeedId(feed.getId());
        item.setFeedName(feed.getName());
        item.setUnitText(StockCodes.unitText(feed.getUnit()));
        item.setBookQty(StockQty.qty(feed.getStockQty()));
        item.setStockVersion(feed.getVersion());
        item.setLowStock(FeedStats.of(feed).lowStock());
        item.setAvgPrice(StockQty.price(feed.getAvgPrice()));
        return item;
    }

    private StockFlow flow(Stocktake stocktake, Long feedId, String flowType, String direction, BigDecimal qty,
                           BigDecimal unitPrice, BigDecimal amount, StockLedger.Move move) {
        StockFlow flow = new StockFlow();
        flow.setFarmId(stocktake.getFarmId());
        flow.setMaterialType(StockCodes.MATERIAL_FEED);
        flow.setFeedId(feedId);
        flow.setFlowType(flowType);
        flow.setDirection(direction);
        flow.setQty(StockQty.qty(qty));
        flow.setUnitPrice(StockQty.price(unitPrice));
        flow.setAmount(StockQty.amount(amount));
        flow.setStockQtyAfter(StockQty.qty(move.qtyAfter()));
        flow.setStockAmountAfter(StockQty.amount(move.amountAfter()));
        flow.setBizType(StockCodes.BIZ_STOCKTAKE);
        flow.setBizId(stocktake.getId());
        // 经办人：盘点人
        flow.setOperatorId(stocktake.getCreatedBy());
        flow.setOccurAt(BeijingTime.now());
        flow.setBizDate(stocktake.getStocktakeDate());
        flow.setIsBatchCost(false);
        flow.setIsFeedUsage(false);
        return flowWriter.record(flow);
    }
}
