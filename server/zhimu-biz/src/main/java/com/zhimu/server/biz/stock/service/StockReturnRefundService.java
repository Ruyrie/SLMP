package com.zhimu.server.biz.stock.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.common.mapper.ChangeRequestMapper;
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.request.StockReturnRefundRequest;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockReturnRefunds;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** 登记现金到账与收入在一个事务内完成，锁原单以串行化退款、改单和作废。 */
@Service
@RequiredArgsConstructor
public class StockReturnRefundService {
    private final StockOutboundMapper outboundMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final FinanceSourceWriteService financeWriter;
    private final StockLookups lookups;

    @Transactional
    public void register(Long id, StockReturnRefundRequest request, String idempotencyKey) {
        StockOutbound outbound = locked(id);
        requireSupported(outbound);
        String key = requireKey(idempotencyKey);
        String remark = validate(request, outbound);
        if (StockReturnRefunds.refunded(outbound)) {
            if (outbound.getFinanceRecordId() != null && key.equals(outbound.getRefundRequestId())
                    && sameValues(outbound, request, remark)) return;
            throw new BizException(ErrorCode.STATE_CHANGED, "这张退货单已登记退款，请刷新查看或使用更正退款");
        }
        requireVersion(outbound, request);
        if (changeRequestMapper.selectPending(StockCodes.BIZ_OUTBOUND, id) != null) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张退货单有待审核修改申请，请处理后再登记退款");
        }
        if (outboundMapper.selectOne(Wrappers.<StockOutbound>lambdaQuery()
                .eq(StockOutbound::getFarmId, outbound.getFarmId())
                .eq(StockOutbound::getRefundRequestId, key)) != null) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该退款请求已用于另一张退货单，请刷新后重试");
        }
        Long actor = lookups.userId();
        Long financeId = financeWriter.syncReturnRefundIncome(source(outbound, request, remark, actor));
        if (financeId == null) throw new BizException(ErrorCode.SERVER_ERROR, "退款收入登记失败");
        outbound.setFinanceRecordId(financeId);
        outbound.setRefundAmount(request.getAmount().setScale(2));
        outbound.setRefundDate(request.getRefundDate());
        outbound.setRefundRemark(remark);
        outbound.setRefundRegisteredAt(BeijingTime.now());
        outbound.setRefundRegisteredBy(actor);
        outbound.setRefundRequestId(key);
        outbound.setUpdatedBy(actor);
        if (outboundMapper.updateById(outbound) == 0) throw StockErrors.versionConflict();
        lookups.log("CREATE", "RETURN_REFUND_REGISTER", StockCodes.BIZ_OUTBOUND, id,
                outbound.getOutboundNo(), Map.of(), snapshot(outbound));
    }

    @Transactional
    public void correct(Long id, StockReturnRefundRequest request, String idempotencyKey) {
        StockOutbound outbound = locked(id);
        requireSupported(outbound);
        requireKey(idempotencyKey);
        String remark = validate(request, outbound);
        if (!StockReturnRefunds.refunded(outbound) || outbound.getFinanceRecordId() == null) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张退货单尚未登记退款");
        }
        if (!StockActions.registeredToday(outbound.getRefundRegisteredAt())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "退款仅支持登记当天更正，跨日后暂不支持更正");
        }
        // 网络丢失成功响应后，原版本重试同内容也安全返回，不再次记收入或审计。
        if (sameValues(outbound, request, remark)) return;
        requireVersion(outbound, request);
        Map<String, Object> before = snapshot(outbound);
        Long actor = lookups.userId();
        Long financeId = financeWriter.syncReturnRefundIncome(source(outbound, request, remark, actor));
        if (!Objects.equals(financeId, outbound.getFinanceRecordId())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "关联退款收入已变化，请刷新后重试");
        }
        outbound.setRefundAmount(request.getAmount().setScale(2));
        outbound.setRefundDate(request.getRefundDate());
        outbound.setRefundRemark(remark);
        outbound.setUpdatedBy(actor);
        // 显式清空说明，防止 MyBatis NOT_NULL 策略保留旧文本。
        int changed = outboundMapper.update(null, Wrappers.<StockOutbound>lambdaUpdate()
                .eq(StockOutbound::getId, id).eq(StockOutbound::getFarmId, outbound.getFarmId())
                .eq(StockOutbound::getVersion, request.getVersion())
                .set(StockOutbound::getRefundAmount, outbound.getRefundAmount())
                .set(StockOutbound::getRefundDate, outbound.getRefundDate())
                .set(StockOutbound::getRefundRemark, remark)
                .set(StockOutbound::getUpdatedBy, actor)
                .set(StockOutbound::getUpdatedAt, BeijingTime.now())
                .set(StockOutbound::getVersion, request.getVersion() + 1));
        if (changed == 0) throw StockErrors.versionConflict();
        lookups.log("UPDATE", "RETURN_REFUND_CORRECT", StockCodes.BIZ_OUTBOUND, id,
                outbound.getOutboundNo(), before, snapshot(outbound));
    }

    public void populate(StockOutbound outbound, StockOutboundVO vo) {
        vo.getActions().setCanRefund(false);
        vo.getActions().setCanEditRefund(false);
        if (!StockReturnRefunds.supported(outbound)) return;
        boolean refunded = StockReturnRefunds.refunded(outbound);
        vo.setRefundStatus(refunded ? "REFUNDED" : "UNREFUNDED");
        vo.setRefundAmount(outbound.getRefundAmount());
        vo.setRefundDate(BeijingTime.format(outbound.getRefundDate()));
        vo.setRefundRemark(outbound.getRefundRemark());
        vo.setRefundRegisteredAt(BeijingTime.format(outbound.getRefundRegisteredAt()));
        vo.setRefundRegisteredBy(lookups.userBrief(outbound.getRefundRegisteredBy()));
        vo.setFinanceRecordId(outbound.getFinanceRecordId());
        boolean manager = FarmContext.get() != null && FarmContext.get().isManagerView();
        boolean writable = manager && FarmContext.get().farmStatus() == FarmStatus.NORMAL;
        if (refunded) {
            vo.setActions(StockActions.requestOnly(false));
            vo.getActions().setCanRefund(false);
            boolean canCorrect = writable && StockCodes.STATUS_NORMAL.equals(outbound.getStatus())
                    && StockActions.registeredToday(outbound.getRefundRegisteredAt());
            vo.getActions().setCanEditRefund(canCorrect);
            vo.setRefundBlockReason(StockReturnRefunds.LOCKED_MESSAGE);
            if (outbound.getRefundRegisteredAt() != null) {
                vo.setRefundEditDeadline(BeijingTime.format(outbound.getRefundRegisteredAt().toLocalDate().atTime(23, 59, 59)));
            }
        } else if (!StockCodes.STATUS_NORMAL.equals(outbound.getStatus())) {
            vo.setRefundBlockReason("这张退货单已作废，不能登记退款");
        } else if (!writable) {
            vo.setRefundBlockReason("仅正常养殖场的所有者或场长可以登记退款");
        } else if (changeRequestMapper.selectPending(StockCodes.BIZ_OUTBOUND, outbound.getId()) != null) {
            vo.setRefundBlockReason("这张退货单有待审核修改申请，请处理后再登记退款");
        } else {
            vo.getActions().setCanRefund(true);
        }
    }

    private StockOutbound locked(Long id) {
        var member = FarmContext.require();
        if (!member.isManagerView()) throw new BizException(ErrorCode.ROLE_FORBIDDEN);
        if (member.farmStatus() != FarmStatus.NORMAL) throw new BizException(ErrorCode.FARM_STATUS_FORBIDDEN);
        StockOutbound outbound = id == null ? null : outboundMapper.selectForUpdate(id, member.farmId());
        if (outbound == null || !member.farmId().equals(outbound.getFarmId())) throw StockErrors.notFound();
        return outbound;
    }

    private static void requireSupported(StockOutbound outbound) {
        if (!StockReturnRefunds.supported(outbound)) {
            throw new BizException(ErrorCode.STATE_CHANGED, "仅药苗退货单可以登记退款，领用和其他出库不支持");
        }
        if (!StockCodes.STATUS_NORMAL.equals(outbound.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张退货单已作废，不能登记或更正退款");
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 64) {
            throw StockErrors.field("idempotency_key", "请刷新后重试退款登记");
        }
        return key.trim();
    }

    private static String validate(StockReturnRefundRequest request, StockOutbound outbound) {
        if (request == null || request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw StockErrors.field("amount", "请输入实际到账的退款金额，必须大于 0");
        }
        if (request.getAmount().stripTrailingZeros().scale() > 2
                || request.getAmount().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw StockErrors.field("amount", "退款金额最多保留 2 位小数且不能超过 9,999,999,999.99");
        }
        if (request.getRefundDate() == null || request.getRefundDate().isAfter(BeijingTime.today())
                || outbound.getOutDate() == null || request.getRefundDate().isBefore(outbound.getOutDate())) {
            throw StockErrors.field("refund_date", "到账日期应在退货日期至今天之间");
        }
        if (request.getVersion() == null || request.getVersion() < 0) throw StockErrors.versionConflict();
        String remark = request.getRemark() == null ? null : request.getRemark().trim();
        if (remark != null && remark.codePointCount(0, remark.length()) > 200) {
            throw StockErrors.field("remark", "退款说明最多 200 字");
        }
        return remark == null || remark.isEmpty() ? null : remark;
    }

    private static void requireVersion(StockOutbound outbound, StockReturnRefundRequest request) {
        if (!Objects.equals(request.getVersion(), outbound.getVersion())) throw StockErrors.versionConflict();
    }

    private static boolean sameValues(StockOutbound outbound, StockReturnRefundRequest request, String remark) {
        return outbound.getRefundAmount() != null && outbound.getRefundAmount().compareTo(request.getAmount()) == 0
                && Objects.equals(outbound.getRefundDate(), request.getRefundDate())
                && Objects.equals(outbound.getRefundRemark(), remark);
    }

    private static FinanceSourceWriteService.ReturnRefundIncome source(StockOutbound outbound,
            StockReturnRefundRequest request, String remark, Long actor) {
        String detail = "退货单 " + outbound.getOutboundNo() + (remark == null ? "" : " · " + remark);
        return new FinanceSourceWriteService.ReturnRefundIncome(outbound.getFarmId(), outbound.getId(),
                outbound.getOutboundNo(), request.getAmount(), request.getRefundDate(), detail, actor,
                outbound.getFinanceRecordId());
    }

    private static Map<String, Object> snapshot(StockOutbound outbound) {
        return Map.of("refund_amount", Map.of("name", "退款金额", "display", outbound.getRefundAmount().toPlainString() + " 元"),
                "refund_date", Map.of("name", "到账日期", "display", outbound.getRefundDate().toString()),
                "refund_remark", Map.of("name", "退款说明", "display", Objects.toString(outbound.getRefundRemark(), "无")));
    }
}
