package com.zhimu.server.biz.stock.support;

import com.zhimu.server.admin.base.api.CategoryQueryService;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.herd.api.PenQueryService;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.entity.StockLoss;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.entity.Stocktake;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
import com.zhimu.server.biz.stock.mapper.StockLossMapper;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeMapper;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 本模块共用的读取与展示辅助：本场记录加载（40401）、用户 / 栏舍 / 品类 / 字典名称、操作留痕。
 * 跨模块只调用对方 api 包的 Service（模块01 账号、模块04 栏舍、管理端-03 品类与字典）。
 */
@Component
@RequiredArgsConstructor
public class StockLookups {

    private final FeedMapper feedMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final StockInboundMapper inboundMapper;
    private final StockOutboundMapper outboundMapper;
    private final StockLossMapper lossMapper;
    private final StocktakeMapper stocktakeMapper;
    private final AccountQueryService accountQueryService;
    private final PenQueryService penQueryService;
    private final CategoryQueryService categoryQueryService;
    private final DictQueryService dictQueryService;
    private final OperationLogRecorder operationLogRecorder;

    public Long farmId() {
        return FarmContext.requireFarmId();
    }

    public Long userId() {
        return UserContext.requireUserId();
    }

    /** 已停用的养殖场：前端只读（本模块的写接口由 @RequireFarm 拦住） */
    public boolean farmReadonly() {
        FarmContext.FarmMember member = FarmContext.get();
        return member != null && member.farmStatus() == FarmStatus.DISABLED;
    }

    public Feed requireFeed(Long id) {
        Feed feed = id == null ? null : feedMapper.selectById(id);
        if (feed == null || !inFarm(feed.getFarmId())) {
            throw StockErrors.notFound();
        }
        return feed;
    }

    public Drug requireDrug(Long id) {
        Drug drug = id == null ? null : drugMapper.selectById(id);
        if (drug == null || !inFarm(drug.getFarmId())) {
            throw StockErrors.notFound();
        }
        return drug;
    }

    public DrugLot requireLot(Long id) {
        DrugLot lot = id == null ? null : drugLotMapper.selectById(id);
        if (lot == null || !inFarm(lot.getFarmId())) {
            throw StockErrors.notFound();
        }
        return lot;
    }

    public StockInbound requireInbound(Long id) {
        StockInbound inbound = id == null ? null : inboundMapper.selectById(id);
        if (inbound == null || !inFarm(inbound.getFarmId())) {
            throw StockErrors.notFound();
        }
        return inbound;
    }

    public StockOutbound requireOutbound(Long id) {
        StockOutbound outbound = id == null ? null : outboundMapper.selectById(id);
        if (outbound == null || !inFarm(outbound.getFarmId())) {
            throw StockErrors.notFound();
        }
        return outbound;
    }

    public StockLoss requireLoss(Long id) {
        StockLoss loss = id == null ? null : lossMapper.selectById(id);
        if (loss == null || !inFarm(loss.getFarmId())) {
            throw StockErrors.notFound();
        }
        return loss;
    }

    public Stocktake requireStocktake(Long id) {
        Stocktake stocktake = id == null ? null : stocktakeMapper.selectById(id);
        if (stocktake == null || !inFarm(stocktake.getFarmId())) {
            throw StockErrors.notFound();
        }
        return stocktake;
    }

    /** 当前养殖场 ID 为空（定时任务 / 测试）时不校验，交给多租户插件 */
    private boolean inFarm(Long recordFarmId) {
        Long farmId = FarmContext.farmId();
        return farmId == null || farmId.equals(recordFarmId);
    }

    public UserBriefVO userBrief(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQueryService.getBrief(userId);
        FarmContext.FarmMember member = FarmContext.get();
        String role = member != null && userId.equals(member.userId()) ? member.role().name() : null;
        return UserBriefVO.builder()
                .userId(userId)
                .name(brief == null ? null : brief.name())
                .role(role)
                .roleText(StockCodes.roleText(role))
                .build();
    }

    public Map<Long, AccountBrief> userBriefs(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return accountQueryService.listBriefs(userIds);
    }

    public UserBriefVO currentUser() {
        return userBrief(UserContext.requireUserId());
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AccountBrief brief = accountQueryService.getBrief(userId);
        return brief == null ? null : brief.name();
    }

    public PenInfo pen(Long penId) {
        if (penId == null) {
            return null;
        }
        Map<Long, PenInfo> pens = penQueryService.getPens(java.util.List.of(penId));
        return pens == null ? null : pens.get(penId);
    }

    public String penName(Long penId) {
        PenInfo pen = pen(penId);
        return pen == null ? null : pen.penName();
    }

    public PenBriefVO penBrief(Long penId) {
        PenInfo pen = pen(penId);
        if (pen == null) {
            return null;
        }
        return PenBriefVO.builder()
                .penId(pen.penId())
                .name(pen.penName())
                .penTypeText(pen.penTypeText())
                .build();
    }

    public String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        var info = categoryQueryService.info(categoryId);
        return info == null ? null : info.name();
    }

    public String feedTypeName(String code) {
        return code == null ? null : dictQueryService.nameOf("FEED_TYPE", code);
    }

    public String scrapReasonName(String code) {
        return code == null ? null : dictQueryService.nameOf("SCRAP_REASON", code);
    }

    /** 养殖场主要品类（药品临期天数按场主要品类推断畜种时用；本模块只用到品类 ID 不需要畜种） */
    public boolean isManagerView() {
        FarmContext.FarmMember member = FarmContext.get();
        return member == null || member.isManagerView();
    }

    public String currentRoleCode() {
        FarmContext.FarmMember member = FarmContext.get();
        return member == null ? null : member.role().name();
    }

    public boolean currentRole(FarmRole role) {
        FarmContext.FarmMember member = FarmContext.get();
        return member != null && member.role() == role;
    }

    /** 写 t_operation_log（与业务同事务） */
    public void log(String opType, String opAction, String bizType, Long bizId, String targetName,
                    Object before, Object after) {
        Long farmId = FarmContext.farmId();
        if (farmId == null) {
            return;
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .opType(opType)
                .opAction(opAction)
                .bizType(bizType)
                .bizId(bizId)
                .targetName(targetName)
                .farmId(farmId)
                .before(before)
                .after(after)
                .build());
    }
}
