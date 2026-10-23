package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.FeedSaveRequest;
import com.zhimu.server.biz.stock.vo.FeedDetailVO;
import com.zhimu.server.biz.stock.vo.FeedOptionVO;
import com.zhimu.server.biz.stock.vo.FeedStockItemVO;
import com.zhimu.server.biz.stock.vo.FeedStockOverviewVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 饲料库存：概览、结存列表、下拉、档案详情与保存（API-M01–M06）。 */
public interface StockFeedService {

    /** API-M01 饲料库存概览（预警横幅、近 7 天领用、最近出入库） */
    FeedStockOverviewVO overview();

    /** API-M02 饲料结存列表：预警在前，再按名称 */
    PageVO<FeedStockItemVO> page(int page, int size);

    /** API-M03 饲料下拉：purpose INBOUND 全部 / OTHER_OUT 结存为 0 的不可选；keyword 选填 */
    List<FeedOptionVO> options(String purpose, String keyword);

    /** API-M04 饲料档案详情；不存在抛 40401 */
    FeedDetailVO detail(Long id);

    /** API-M05 新增饲料；重名抛 40905 */
    FeedDetailVO create(FeedSaveRequest request);

    /** API-M06 修改饲料档案；edit_version 不一致抛 40902 */
    FeedDetailVO update(Long id, FeedSaveRequest request);

    /** API-M14 盘点表用的“需要盘点的饲料”（有结存或流水的本场饲料） */
    List<com.zhimu.server.biz.stock.entity.Feed> listActiveFeeds();
}
