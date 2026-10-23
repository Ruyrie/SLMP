package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 库存记账（模块09 2.7 节）：移动加权平均、出库不超过结存、乐观锁重试。
 * <p>
 * 每次更新都是“读当前值 → 算新值 → 带 version 条件 UPDATE”；UPDATE 影响 0 行表示版本被推进，
 * 重新读取重算，最多重试 3 次。任何出库都不会把结存扣成负数。
 * <ul>
 *     <li>入库：均价 =（原库存金额 + 入库金额）÷（原结存 + 入库数量），4 位小数</li>
 *     <li>出库：金额 = 数量 × 当时均价，保留到分；把结存扣到 0 时金额取剩余库存金额，均价保留最后值</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StockLedger {

    private static final int MAX_RETRY = 3;

    private final FeedMapper feedMapper;
    private final DrugLotMapper drugLotMapper;

    /**
     * 一次记账后的快照。
     *
     * @param qtyAfter    记账后的结存
     * @param amountAfter 记账后的库存金额
     * @param avgPrice    记账后的均价
     * @param unitPrice   本次的单位成本（入库为折合记账单位单价，出库为当时均价）
     * @param amount      本次金额
     * @param version     记账后的结存版本
     */
    public record Move(BigDecimal qtyAfter, BigDecimal amountAfter, BigDecimal avgPrice,
                       BigDecimal unitPrice, BigDecimal amount, Integer version) {
    }

    /** 饲料入库 */
    public Move inFeed(Feed feed, BigDecimal qty, BigDecimal amount) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Feed current = attempt == 0 ? feed : feedMapper.selectById(feed.getId());
            if (current == null) {
                throw StockErrors.notFound();
            }
            BigDecimal oldQty = StockQty.zeroIfNull(current.getStockQty());
            BigDecimal oldAmount = StockQty.zeroIfNull(current.getStockAmount());
            BigDecimal newQty = oldQty.add(qty);
            BigDecimal newAmount = oldAmount.add(amount);
            BigDecimal avg = newQty.signum() == 0 ? StockQty.zeroIfNull(current.getAvgPrice())
                    : newAmount.divide(newQty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP);
            int rows = feedMapper.updateStock(current.getId(), current.getVersion(), newQty, newAmount, avg,
                    BeijingTime.now());
            if (rows > 0) {
                return new Move(newQty, newAmount, avg,
                        qty.signum() == 0 ? avg : amount.divide(qty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP),
                        amount, current.getVersion() + 1);
            }
        }
        throw StockErrors.versionConflict();
    }

    /** 饲料出库：结存不足抛 42201 */
    public Move outFeed(Feed feed, BigDecimal qty) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Feed current = attempt == 0 ? feed : feedMapper.selectById(feed.getId());
            if (current == null) {
                throw StockErrors.notFound();
            }
            BigDecimal oldQty = StockQty.zeroIfNull(current.getStockQty());
            if (oldQty.compareTo(qty) < 0) {
                throw StockErrors.notEnoughOut(oldQty, "千克");
            }
            BigDecimal oldAmount = StockQty.zeroIfNull(current.getStockAmount());
            BigDecimal avg = StockQty.zeroIfNull(current.getAvgPrice());
            BigDecimal newQty = oldQty.subtract(qty);
            BigDecimal amount = newQty.signum() == 0
                    ? oldAmount
                    : StockQty.amount(qty.multiply(avg));
            BigDecimal newAmount = newQty.signum() == 0 ? BigDecimal.ZERO
                    : oldAmount.subtract(amount).max(BigDecimal.ZERO);
            int rows = feedMapper.updateStock(current.getId(), current.getVersion(), newQty, newAmount, avg,
                    BeijingTime.now());
            if (rows > 0) {
                return new Move(newQty, newAmount, avg, avg, amount, current.getVersion() + 1);
            }
        }
        throw StockErrors.versionConflict();
    }

    /**
     * 饲料结存按增量调整：入库单修改 / 作废时用（deltaQty、deltaAmount 可正可负）。
     * 调整后结存不得为负，均价按新金额重算。
     */
    public Move adjustFeed(Feed feed, BigDecimal deltaQty, BigDecimal deltaAmount) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Feed current = attempt == 0 ? feed : feedMapper.selectById(feed.getId());
            if (current == null) {
                throw StockErrors.notFound();
            }
            BigDecimal oldQty = StockQty.zeroIfNull(current.getStockQty());
            BigDecimal newQty = oldQty.add(deltaQty);
            if (newQty.signum() < 0) {
                throw StockErrors.notEnoughOut(oldQty, "千克");
            }
            BigDecimal newAmount = StockQty.zeroIfNull(current.getStockAmount()).add(deltaAmount).max(BigDecimal.ZERO);
            BigDecimal avg = newQty.signum() == 0 ? StockQty.zeroIfNull(current.getAvgPrice())
                    : newAmount.divide(newQty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP);
            int rows = feedMapper.updateStock(current.getId(), current.getVersion(), newQty, newAmount, avg,
                    BeijingTime.now());
            if (rows > 0) {
                return new Move(newQty, newAmount, avg, avg, deltaAmount, current.getVersion() + 1);
            }
        }
        throw StockErrors.versionConflict();
    }

    /** 药品批号入库 */
    public Move inLot(DrugLot lot, BigDecimal qty, BigDecimal amount) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            DrugLot current = attempt == 0 ? lot : drugLotMapper.selectById(lot.getId());
            if (current == null) {
                throw StockErrors.notFound();
            }
            BigDecimal oldQty = StockQty.zeroIfNull(current.getStockQty());
            BigDecimal oldAmount = StockQty.zeroIfNull(current.getStockAmount());
            BigDecimal newQty = oldQty.add(qty);
            BigDecimal newAmount = oldAmount.add(amount);
            BigDecimal avg = newQty.signum() == 0 ? StockQty.zeroIfNull(current.getAvgPrice())
                    : newAmount.divide(newQty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP);
            int rows = drugLotMapper.updateStock(current.getId(), current.getVersion(), newQty, newAmount, avg,
                    BeijingTime.now());
            if (rows > 0) {
                return new Move(newQty, newAmount, avg,
                        qty.signum() == 0 ? avg : amount.divide(qty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP),
                        amount, current.getVersion() + 1);
            }
        }
        throw StockErrors.versionConflict();
    }

    /** 药品批号出库：结存不足抛 42201（带记账单位文字） */
    public Move outLot(DrugLot lot, BigDecimal qty, String unitText) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            DrugLot current = attempt == 0 ? lot : drugLotMapper.selectById(lot.getId());
            if (current == null) {
                throw StockErrors.notFound();
            }
            BigDecimal oldQty = StockQty.zeroIfNull(current.getStockQty());
            if (oldQty.compareTo(qty) < 0) {
                throw StockErrors.notEnoughOut(oldQty, unitText);
            }
            BigDecimal oldAmount = StockQty.zeroIfNull(current.getStockAmount());
            BigDecimal avg = StockQty.zeroIfNull(current.getAvgPrice());
            BigDecimal newQty = oldQty.subtract(qty);
            BigDecimal amount = newQty.signum() == 0
                    ? oldAmount
                    : StockQty.amount(qty.multiply(avg));
            BigDecimal newAmount = newQty.signum() == 0 ? BigDecimal.ZERO
                    : oldAmount.subtract(amount).max(BigDecimal.ZERO);
            int rows = drugLotMapper.updateStock(current.getId(), current.getVersion(), newQty, newAmount, avg,
                    BeijingTime.now());
            if (rows > 0) {
                return new Move(newQty, newAmount, avg, avg, amount, current.getVersion() + 1);
            }
        }
        throw StockErrors.versionConflict();
    }

    /** 批号结存按增量调整（入库单修改 / 作废、报废回滚用） */
    public Move adjustLot(DrugLot lot, BigDecimal deltaQty, BigDecimal deltaAmount, String unitText) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            DrugLot current = attempt == 0 ? lot : drugLotMapper.selectById(lot.getId());
            if (current == null) {
                throw StockErrors.notFound();
            }
            BigDecimal oldQty = StockQty.zeroIfNull(current.getStockQty());
            BigDecimal newQty = oldQty.add(deltaQty);
            if (newQty.signum() < 0) {
                throw StockErrors.notEnoughOut(oldQty, unitText);
            }
            BigDecimal newAmount = StockQty.zeroIfNull(current.getStockAmount()).add(deltaAmount).max(BigDecimal.ZERO);
            BigDecimal avg = newQty.signum() == 0 ? StockQty.zeroIfNull(current.getAvgPrice())
                    : newAmount.divide(newQty, StockQty.PRICE_SCALE, RoundingMode.HALF_UP);
            int rows = drugLotMapper.updateStock(current.getId(), current.getVersion(), newQty, newAmount, avg,
                    BeijingTime.now());
            if (rows > 0) {
                return new Move(newQty, newAmount, avg, avg, deltaAmount, current.getVersion() + 1);
            }
        }
        throw StockErrors.versionConflict();
    }

    /** 供上层在重试耗尽时统一抛 40902 */
    public static BizException conflict() {
        return StockErrors.versionConflict();
    }

    @SuppressWarnings("unused")
    private static LocalDateTime now() {
        return BeijingTime.now();
    }
}
