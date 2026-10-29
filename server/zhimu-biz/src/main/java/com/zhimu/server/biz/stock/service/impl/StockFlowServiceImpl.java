package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.convert.StockFlowAssembler;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.biz.stock.service.StockFlowService;
import com.zhimu.server.biz.stock.vo.StockFlowItemVO;
import com.zhimu.server.common.api.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流水查询实现：按业务日期倒序、同日按发生时间倒序（同一时刻按 id 倒序）；不含被更正替换（CORRECTED）的流水，
 * 来源已作废的流水照常返回并带“已作废”。
 */
@Service
@RequiredArgsConstructor
public class StockFlowServiceImpl implements StockFlowService {

    private final StockFlowMapper flowMapper;
    private final StockFlowAssembler assembler;

    @Override
    public PageVO<StockFlowItemVO> feedFlows(Long feedId, String flowGroup, int page, int size) {
        LambdaQueryWrapper<StockFlow> wrapper = base(StockCodes.MATERIAL_FEED);
        wrapper.eq(feedId != null, StockFlow::getFeedId, feedId);
        applyGroup(wrapper, flowGroup, true);
        return query(wrapper, page, size);
    }

    @Override
    public PageVO<StockFlowItemVO> drugFlows(Long drugLotId, Long drugId, String flowGroup, int page, int size) {
        LambdaQueryWrapper<StockFlow> wrapper = base(StockCodes.MATERIAL_DRUG);
        wrapper.eq(drugLotId != null, StockFlow::getDrugLotId, drugLotId);
        wrapper.eq(drugId != null, StockFlow::getDrugId, drugId);
        applyGroup(wrapper, flowGroup, false);
        return query(wrapper, page, size);
    }

    private PageVO<StockFlowItemVO> query(LambdaQueryWrapper<StockFlow> wrapper, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size <= 0 ? 20 : Math.min(size, 100);
        Long total = flowMapper.selectCount(wrapper);
        // 按业务日期倒序、同日按发生时间倒序（客户端按业务日期分组，补登的流水落在所属日期里）
        wrapper.orderByDesc(StockFlow::getBizDate).orderByDesc(StockFlow::getOccurAt).orderByDesc(StockFlow::getId)
                .last("LIMIT " + pageSize + " OFFSET " + (long) (pageNo - 1) * pageSize);
        List<StockFlow> flows = flowMapper.selectList(wrapper);
        return PageVO.of(assembler.toVos(flows), total == null ? 0 : total, pageNo, pageSize);
    }

    private static LambdaQueryWrapper<StockFlow> base(String materialType) {
        return Wrappers.<StockFlow>lambdaQuery()
                .eq(StockFlow::getMaterialType, materialType)
                .and(w -> w.isNull(StockFlow::getVoidReason).or()
                        .ne(StockFlow::getVoidReason, StockCodes.VOID_CORRECTED));
    }

    private static void applyGroup(LambdaQueryWrapper<StockFlow> wrapper, String flowGroup, boolean feed) {
        String group = StringUtils.hasText(flowGroup) ? flowGroup.trim().toUpperCase() : StockCodes.GROUP_ALL;
        switch (group) {
            case StockCodes.GROUP_INBOUND -> wrapper.eq(StockFlow::getFlowType, StockCodes.FLOW_INBOUND);
            case StockCodes.GROUP_FEED_USE -> wrapper.eq(StockFlow::getFlowType, StockCodes.FLOW_FEED_USE);
            case StockCodes.GROUP_OTHER_OUT -> wrapper.eq(StockFlow::getFlowType, StockCodes.FLOW_OTHER_OUT);
            case StockCodes.GROUP_LOSS -> wrapper.eq(StockFlow::getFlowType, StockCodes.FLOW_LOSS);
            case StockCodes.GROUP_STOCKTAKE -> wrapper.in(StockFlow::getFlowType,
                    StockCodes.FLOW_STOCKTAKE_GAIN, StockCodes.FLOW_STOCKTAKE_LOSS);
            case StockCodes.GROUP_USE -> wrapper.in(StockFlow::getFlowType,
                    StockCodes.FLOW_VACCINE_USE, StockCodes.FLOW_MEDICATION_USE,
                    StockCodes.FLOW_DISINFECT_USE, StockCodes.FLOW_MANUAL_USE);
            case StockCodes.GROUP_RETURN -> wrapper.eq(StockFlow::getFlowType, StockCodes.FLOW_RETURN);
            case StockCodes.GROUP_SCRAP -> wrapper.eq(StockFlow::getFlowType, StockCodes.FLOW_SCRAP);
            default -> {
                // ALL：不加流水类型条件
            }
        }
    }
}
