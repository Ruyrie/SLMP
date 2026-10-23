package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.vo.FeedRecordVO;

import java.math.BigDecimal;

/** 模块06 VO 转换小工具。 */
public final class FeedConvert {

    private FeedConvert() {
    }

    public static FeedRecordVO.OptionBrief option(FeedStockPort.FeedBrief feed) {
        if (feed == null) {
            return null;
        }
        FeedRecordVO.OptionBrief vo = new FeedRecordVO.OptionBrief();
        vo.setFeedId(feed.feedId());
        vo.setFeedName(feed.feedName());
        vo.setUnit(FeedCalc.unit(feed.unit()));
        vo.setStockQty(feed.stockQty());
        vo.setRemainingDays(feed.remainingDays());
        vo.setLowStock(feed.lowStock());
        vo.setSelectable(feed.selectable());
        vo.setDisabledReason(feed.disabledReason());
        return vo;
    }

    public static BigDecimal zero2() {
        return BigDecimal.ZERO.setScale(2);
    }

    public static BigDecimal zero4() {
        return BigDecimal.ZERO.setScale(4);
    }
}
