package com.zhimu.server.biz.finance.support.change;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.entity.SaleReceipt;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.mapper.SaleReceiptMapper;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.service.BatchSettlementService;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 出栏单（SALE_ORDER）的修改申请适配：已有收款且未作废的出栏单，修改单价、数量或作废都要走修改申请。
 * 数量 new_value 可能是 App E08 提交的 JSON（{"quantity":98,"total_weight":11760}，见任务书第 9 条），
 * 也可能是纯数字；审核通过后重算销售金额、未收金额并把差额回写批次存栏。表单与 App mock 一致。
 */
@Component
@RequiredArgsConstructor
public class SaleOrderChangeHandler implements ChangeRequestHandler {

    public static final String FIELD_UNIT_PRICE = "unit_price";
    public static final String FIELD_QUANTITY = "quantity";
    private static final Pattern JSON_QUANTITY = Pattern.compile("\"quantity\"\\s*:\\s*(\\d+)");
    private static final Pattern JSON_WEIGHT = Pattern.compile("\"total_weight\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999.99");
    private static final List<String> RECALC_TAGS = List.of("销售金额", "未收金额", "批次盈亏", "待收款", "经营报表");
    private static final List<String> VOID_RECALC_TAGS = List.of("销售金额", "批次盈亏", "存栏");

    private final SaleOrderMapper orderMapper;
    private final SaleReceiptMapper receiptMapper;
    private final FinanceRecordMapper financeRecordMapper;
    private final BatchQueryService batchQueryService;
    private final BatchSalePort batchSalePort;
    private final AccountQueryService accountQueryService;
    private final OperationLogRecorder operationLogRecorder;
    private final BatchSettlementService batchSettlementService;
    private final com.zhimu.server.biz.finance.support.ReceivableTodos receivableTodos;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.SALE_ORDER);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.SALE_ORDER);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        SaleOrder order = load(bizId);
        if (order == null) {
            return null;
        }
        String batchNo = batchNo(order.getBatchId());
        String date = monthDay(order.getSaleDate());
        String locator = (batchNo == null ? "批次" : batchNo) + " · " + date + "出栏";
        String title = "出栏单 " + (batchNo == null ? "" : batchNo);
        String subtitle = amountText(order.getTotalWeight()) + " 千克 · 已收 "
                + amountText(order.getReceivedAmount()) + " 元";
        boolean voided = FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus());
        return new ChangeRecord(BizType.SALE_ORDER, order.getId(), order.getFarmId(), order.getBatchId(),
                order.getPenId(), locator, title, subtitle,
                order.getContentVersion() == null ? "0" : String.valueOf(order.getContentVersion()),
                false, voided);
    }

    /** 已有收款且未作废的出栏单不能直接改 */
    @Override
    public boolean requiresRequest(ChangeRecord record) {
        SaleOrder order = load(record.bizId());
        if (order == null || FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            return false;
        }
        return FinanceSupport.nz(order.getReceivedAmount()).signum() > 0;
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        SaleOrder order = load(record.bizId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        int stock = currentStock(order.getBatchId());
        int maxQuantity = (order.getQuantity() == null ? 0 : order.getQuantity()) + stock;
        List<ChangeField> fields = List.of(
                new ChangeField(FIELD_UNIT_PRICE, "单价", priceText(order.getUnitPrice()), "元/千克",
                        ChangeField.NUMBER, List.of(), BigDecimal.ZERO, MAX_PRICE, RECALC_TAGS, null),
                new ChangeField(FIELD_QUANTITY, "数量",
                        order.getQuantity() == null ? null : String.valueOf(order.getQuantity()), "头",
                        ChangeField.NUMBER, List.of(), BigDecimal.ONE, BigDecimal.valueOf(maxQuantity),
                        RECALC_TAGS, null));
        return new ChangeForm(
                List.of(new ChangeCardItem("销售金额", amountText(order.getSaleAmount()) + " 元"),
                        new ChangeCardItem("已收", amountText(order.getReceivedAmount()) + " 元")),
                fields,
                List.of("修改单价或数量会重新计算销售金额、未收金额和批次盈亏；若低于已收金额，同步纠正收款和对应收入记录"),
                "这张出栏单已有收款，修改需要管理员审核。审核后若销售金额低于已收金额，将同步纠正收款和对应收入记录，保留修改留痕。审核期间原单照常有效。",
                false, true,
                "审核通过后出栏单作废，已收款生成的收入同步作废",
                VOID_RECALC_TAGS);
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType == ChangeType.DELETE) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "出栏单只能申请作废，不能删除");
        }
        if (changeType == ChangeType.VOID) {
            return ChangeValidation.withoutField(List.of(ChangeImpact.INCOME, ChangeImpact.COST,
                    ChangeImpact.STATS, ChangeImpact.STOCK), VOID_RECALC_TAGS);
        }
        ChangeRequestJson.requireField(form, fieldCode);
        SaleOrder order = load(record.bizId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "记录不存在或无权查看");
        }
        ChangeField field = form.fieldOf(fieldCode);
        BigDecimal received = FinanceSupport.nz(order.getReceivedAmount());
        if (FIELD_UNIT_PRICE.equals(fieldCode)) {
            BigDecimal price = parseDecimal(newValue);
            if (price == null) {
                throw FinanceErrors.field("new_value", "请填写数字");
            }
            if (price.signum() <= 0) {
                throw FinanceErrors.field("new_value", "单价必须大于 0");
            }
            if (price.stripTrailingZeros().scale() > 2) {
                throw FinanceErrors.field("new_value", "单价最多保留 2 位小数");
            }
            if (price.compareTo(MAX_PRICE) > 0) {
                throw FinanceErrors.field("new_value", "单价不能超过 99,999.99");
            }
            if (price.compareTo(FinanceSupport.nz(order.getUnitPrice())) == 0) {
                throw FinanceErrors.field("new_value", "新单价与原单价相同，无需修改");
            }
            BigDecimal newAmount = saleAmount(order.getPricingMethod(), order.getTotalWeight(), price,
                    order.getQuantity());
            validateSaleAmount(newAmount, received);
            String normalized = priceText(price);
            return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(),
                    field.value(), ChangeRequestJson.display(field.value(), field.valueUnit()),
                    normalized, ChangeRequestJson.display(normalized, field.valueUnit()),
                    List.of(ChangeImpact.INCOME, ChangeImpact.COST, ChangeImpact.STATS), RECALC_TAGS);
        }
        if (FIELD_QUANTITY.equals(fieldCode)) {
            QuantityChange change = quantityChange(order, newValue);
            int max = (order.getQuantity() == null ? 0 : order.getQuantity()) + currentStock(order.getBatchId());
            if (change.quantity() > max) {
                throw FinanceErrors.field("new_value", "不能超过 " + max + " 头（原出栏 "
                        + order.getQuantity() + " 头加批次当前存栏 " + currentStock(order.getBatchId()) + " 头）");
            }
            BigDecimal newAmount = saleAmount(order.getPricingMethod(), change.totalWeight(),
                    order.getUnitPrice(), change.quantity());
            validateSaleAmount(newAmount, received);
            // App E08 的数量是 JSON（含 total_weight）时原样保存，审核回写才能沿用 App 填写的总重
            String text = newValue == null ? "" : newValue.trim();
            String normalized = text.startsWith("{") ? text : String.valueOf(change.quantity());
            return ChangeValidation.of(fieldCode, field.fieldName(), field.valueUnit(),
                    field.value(), ChangeRequestJson.display(field.value(), field.valueUnit()),
                    normalized, ChangeRequestJson.display(String.valueOf(change.quantity()), field.valueUnit()),
                    List.of(ChangeImpact.INCOME, ChangeImpact.COST, ChangeImpact.STATS, ChangeImpact.STOCK),
                    RECALC_TAGS);
        }
        throw FinanceErrors.field("field_code", "请选择要修改的项目");
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        SaleOrder order = load(record.bizId());
        if (order == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        if (FinanceConst.PAY_VOIDED.equals(order.getPaymentStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这张出栏单已作废");
        }
        BigDecimal received = FinanceSupport.nz(order.getReceivedAmount());
        if (FIELD_QUANTITY.equals(request.getFieldCode())) {
            String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_QUANTITY);
            QuantityChange change = quantityChange(order, raw);
            int max = (order.getQuantity() == null ? 0 : order.getQuantity()) + currentStock(order.getBatchId());
            if (change.quantity() > max) {
                throw new BizException(ErrorCode.COUNT_EXCEEDED,
                        "不能超过 " + max + " 头（原出栏 " + order.getQuantity() + " 头加批次当前存栏 "
                                + currentStock(order.getBatchId()) + " 头）");
            }
            validateSaleAmount(saleAmount(order.getPricingMethod(), change.totalWeight(),
                    order.getUnitPrice(), change.quantity()), received);
        } else if (FIELD_UNIT_PRICE.equals(request.getFieldCode())) {
            String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_UNIT_PRICE);
            BigDecimal price = parseDecimal(raw);
            if (price == null || price.signum() <= 0) {
                throw FinanceErrors.field("new_value", "单价必须大于 0");
            }
            validateSaleAmount(saleAmount(order.getPricingMethod(), order.getTotalWeight(), price,
                    order.getQuantity()), received);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        SaleOrder order = load(record.bizId());
        if (order == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        ChangeType changeType = changeTypeOf(request.getChangeType());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        if (changeType == ChangeType.UPDATE) {
            if (FIELD_UNIT_PRICE.equals(request.getFieldCode())) {
                BigDecimal price = parseDecimal(ChangeRequestJson.afterValue(request.getAfterJson(),
                        FIELD_UNIT_PRICE));
                if (price == null || price.signum() <= 0) {
                    throw FinanceErrors.field("new_value", "单价必须大于 0");
                }
                BigDecimal oldPrice = FinanceSupport.nz(order.getUnitPrice());
                BigDecimal oldAmount = FinanceSupport.nz(order.getSaleAmount());
                order.setUnitPrice(FinanceSupport.scale2(price));
                order.setSaleAmount(saleAmount(order.getPricingMethod(), order.getTotalWeight(), price,
                        order.getQuantity()));
                correctReceipts(order, adminId, before, after);
                refreshPayment(order);
                order.setContentVersion((order.getContentVersion() == null ? 0 : order.getContentVersion()) + 1);
                order.setUpdatedBy(adminId);
                if (orderMapper.updateById(order) == 0) throw new BizException(ErrorCode.VERSION_CONFLICT);
                before.put(FIELD_UNIT_PRICE, Map.of("name", "单价",
                        "display", priceText(oldPrice) + " 元/千克"));
                before.put("sale_amount", Map.of("name", "销售金额", "display", amountText(oldAmount) + " 元"));
                after.put(FIELD_UNIT_PRICE, Map.of("name", "单价",
                        "display", priceText(price) + " 元/千克"));
                after.put("sale_amount", Map.of("name", "销售金额",
                        "display", amountText(order.getSaleAmount()) + " 元"));
            } else if (FIELD_QUANTITY.equals(request.getFieldCode())) {
                String raw = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_QUANTITY);
                QuantityChange change = quantityChange(order, raw);
                int oldQuantity = order.getQuantity() == null ? 0 : order.getQuantity();
                List<SaleOrderVos.PenAllocation> allocations = allocations(order);
                adjustAllocations(allocations, change.quantity() - oldQuantity);
                order.setPenAllocations(FinanceSupport.json(allocations));
                order.setQuantity(change.quantity());
                order.setTotalWeight(FinanceSupport.scale2(change.totalWeight()));
                order.setAvgWeight(change.totalWeight().divide(BigDecimal.valueOf(change.quantity()), 1,
                        RoundingMode.HALF_UP));
                order.setSaleAmount(saleAmount(order.getPricingMethod(), change.totalWeight(),
                        order.getUnitPrice(), change.quantity()));
                correctReceipts(order, adminId, before, after);
                refreshPayment(order);
                order.setContentVersion((order.getContentVersion() == null ? 0 : order.getContentVersion()) + 1);
                order.setUpdatedBy(adminId);
                if (orderMapper.updateById(order) == 0) throw new BizException(ErrorCode.VERSION_CONFLICT);
                Map<Long, Integer> delta = new LinkedHashMap<>();
                delta.put(primaryPen(order, allocations), change.quantity() - oldQuantity);
                batchSalePort.adjustSale(order.getFarmId(), order.getBatchId(), order.getSaleDate(), delta,
                        order.getId());
                before.put(FIELD_QUANTITY, Map.of("name", "数量", "display", oldQuantity + " 头"));
                after.put(FIELD_QUANTITY, Map.of("name", "数量", "display", change.quantity() + " 头"));
            } else {
                throw FinanceErrors.field("field_code", "请选择要修改的项目");
            }
        } else if (changeType == ChangeType.VOID) {
            voidOrder(order, request, adminId);
            before.put("record_status", Map.of("name", "记录状态", "display", "有效"));
            after.put("record_status", Map.of("name", "记录状态", "display", "已作废"));
            after.put("receipts", Map.of("name", "作废收款", "display", "已同步作废收入"));
        } else {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "出栏单只能申请作废，不能删除");
        }
        batchSettlementService.recalculateForSale(order.getFarmId(), order.getBatchId());
        // 改价 / 改数量 / 作废后同步待收款待办（收齐完成、重新欠款恢复、作废忽略）
        receivableTodos.sync(order);
        record(request, record, changeType, before, after);
        return new ChangeApplyResult(changeType == ChangeType.VOID ? 4 : 3,
                changeType == ChangeType.VOID ? "已作废出栏单并重新计算批次盈亏" : "已同步纠正销售金额、收款与收入记录，并重新计算批次盈亏");
    }

    @Override
    public void onSubmitted(ChangeRequest request) {
        SaleOrder order = load(request.getBizId());
        if (order == null) {
            return;
        }
        order.setPendingChangeRequestId(request.getId());
        orderMapper.updateById(order);
    }

    @Override
    public void onClosed(ChangeRequest request) {
        SaleOrder order = load(request.getBizId());
        if (order == null || order.getPendingChangeRequestId() == null
                || !order.getPendingChangeRequestId().equals(request.getId())) {
            return;
        }
        order.setPendingChangeRequestId(null);
        orderMapper.updateById(order);
    }

    /** 按审批后的销售额冲减最近登记的收款，保留原始金额的审核留痕。 */
    private void correctReceipts(SaleOrder order, Long adminId, Map<String, Object> before, Map<String, Object> after) {
        BigDecimal original = FinanceSupport.nz(order.getReceivedAmount());
        BigDecimal excess = original.subtract(order.getSaleAmount());
        if (excess.signum() <= 0) return;
        List<SaleReceipt> receipts = new ArrayList<>(TenantSkip.call(() -> receiptMapper.selectNormalByOrderId(order.getId())));
        receipts.sort(java.util.Comparator.comparing(SaleReceipt::getId).reversed());
        BigDecimal actual = receipts.stream().map(SaleReceipt::getAmount).map(FinanceSupport::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (actual.compareTo(original) != 0) throw new BizException(ErrorCode.STATE_CHANGED, "收款记录已变化，请刷新后重新审核");
        int count = receipts.size();
        for (SaleReceipt receipt : receipts) {
            if (excess.signum() <= 0) break;
            BigDecimal oldAmount = FinanceSupport.nz(receipt.getAmount());
            BigDecimal reduction = oldAmount.min(excess);
            BigDecimal corrected = oldAmount.subtract(reduction);
            FinanceRecord income = TenantSkip.call(() -> financeRecordMapper.selectById(receipt.getFinanceRecordId()));
            if (income == null || !Objects.equals(income.getFarmId(), order.getFarmId())) {
                throw new BizException(ErrorCode.STATE_CHANGED, "对应收入记录不存在，无法同步纠正收款");
            }
            if (corrected.signum() == 0) {
                receipt.setStatus("VOID"); receipt.setVoidedAt(BeijingTime.now());
                income.setStatus(FinanceConst.RECORD_VOID); income.setVoidSource("CHANGE_REQUEST");
                income.setVoidedAt(BeijingTime.now()); income.setVoidedBy(adminId);
                count--;
            } else {
                receipt.setAmount(FinanceSupport.scale2(corrected));
                income.setAmount(FinanceSupport.scale2(corrected));
            }
            income.setUpdatedBy(adminId);
            if (TenantSkip.call(() -> receiptMapper.updateById(receipt)) == 0
                    || TenantSkip.call(() -> financeRecordMapper.updateById(income)) == 0) {
                throw new BizException(ErrorCode.VERSION_CONFLICT);
            }
            String field = "receipt_" + receipt.getId();
            before.put(field, Map.of("name", "收款及收入 #" + receipt.getId(), "display", amountText(oldAmount) + " 元"));
            after.put(field, Map.of("name", "收款及收入 #" + receipt.getId(), "display", corrected.signum() == 0 ? "已作废" : amountText(corrected) + " 元"));
            excess = excess.subtract(reduction);
        }
        order.setReceivedAmount(order.getSaleAmount());
        order.setReceiptCount(count);
        before.put("received_amount", Map.of("name", "已收金额", "display", amountText(original) + " 元"));
        after.put("received_amount", Map.of("name", "已收金额", "display", amountText(order.getReceivedAmount()) + " 元"));
    }

    private void refreshPayment(SaleOrder order) {
        BigDecimal received = FinanceSupport.nz(order.getReceivedAmount());
        BigDecimal unpaid = order.getSaleAmount().subtract(received).max(BigDecimal.ZERO);
        order.setUnpaidAmount(FinanceSupport.scale2(unpaid));
        order.setPaymentStatus(unpaid.signum() == 0 ? FinanceConst.PAY_PAID
                : received.signum() > 0 ? FinanceConst.PAY_PARTIAL : FinanceConst.PAY_UNPAID);
        if (unpaid.signum() > 0) order.setPaidOffDate(null);
        else if (order.getPaidOffDate() == null) order.setPaidOffDate(BeijingTime.today());
    }

    private void voidOrder(SaleOrder order, ChangeRequest request, Long adminId) {
        List<SaleReceipt> receipts = receiptMapper.selectNormalByOrderId(order.getId());
        for (SaleReceipt receipt : receipts == null ? List.<SaleReceipt>of() : receipts) {
            receipt.setStatus("VOID");
            receipt.setVoidedAt(BeijingTime.now());
            receiptMapper.updateById(receipt);
            if (receipt.getFinanceRecordId() != null) {
                FinanceRecord record = financeRecordMapper.selectById(receipt.getFinanceRecordId());
                if (record != null) {
                    record.setStatus(FinanceConst.RECORD_VOID);
                    record.setUpdatedBy(adminId);
                    financeRecordMapper.updateById(record);
                }
            }
        }
        order.setPaymentStatus(FinanceConst.PAY_VOIDED);
        order.setUnpaidAmount(BigDecimal.ZERO);
        order.setVoidReason(request.getReason());
        order.setVoidSource("CHANGE_REQUEST");
        order.setVoidedAt(BeijingTime.now());
        order.setVoidedBy(adminId);
        order.setContentVersion((order.getContentVersion() == null ? 0 : order.getContentVersion()) + 1);
        order.setUpdatedBy(adminId);
        orderMapper.updateById(order);
        Map<Long, Integer> restore = new LinkedHashMap<>();
        for (SaleOrderVos.PenAllocation allocation : allocations(order)) {
            restore.put(allocation.getPenId(), allocation.getQuantity() == null ? 0 : allocation.getQuantity());
        }
        batchSalePort.restoreSale(order.getFarmId(), order.getBatchId(), order.getSaleDate(), restore, order.getId());
    }

    private List<SaleOrderVos.PenAllocation> allocations(SaleOrder order) {
        SaleOrderVos.PenAllocation[] rows = FinanceSupport.parse(order.getPenAllocations(),
                SaleOrderVos.PenAllocation[].class);
        List<SaleOrderVos.PenAllocation> result = new ArrayList<>();
        if (rows != null) {
            for (SaleOrderVos.PenAllocation row : rows) {
                if (row != null) {
                    result.add(row);
                }
            }
        }
        return result;
    }

    private void adjustAllocations(List<SaleOrderVos.PenAllocation> allocations, int delta) {
        if (allocations.isEmpty()) {
            return;
        }
        SaleOrderVos.PenAllocation first = allocations.get(0);
        int quantity = first.getQuantity() == null ? 0 : first.getQuantity();
        first.setQuantity(Math.max(0, quantity + delta));
    }

    private Long primaryPen(SaleOrder order, List<SaleOrderVos.PenAllocation> allocations) {
        if (order.getPenId() != null) {
            return order.getPenId();
        }
        return allocations.isEmpty() ? null : allocations.get(0).getPenId();
    }

    private void validateSaleAmount(BigDecimal amount, BigDecimal received) {
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999999")) > 0) {
            throw FinanceErrors.field("new_value", "销售金额须大于 0 且不能超过 9,999,999,999 元");
        }
    }

    /** 数量新值：纯数字或 App E08 的 JSON（含 quantity 与 total_weight） */
    private QuantityChange quantityChange(SaleOrder order, String newValue) {
        String text = newValue == null ? "" : newValue.trim();
        if (text.isEmpty()) {
            throw FinanceErrors.field("new_value", "请填写数量");
        }
        Integer quantity;
        BigDecimal totalWeight = null;
        if (text.startsWith("{")) {
            Matcher q = JSON_QUANTITY.matcher(text);
            if (!q.find()) {
                throw FinanceErrors.field("new_value", "请填写数量");
            }
            quantity = Integer.valueOf(q.group(1));
            Matcher w = JSON_WEIGHT.matcher(text);
            if (w.find()) {
                totalWeight = new BigDecimal(w.group(1));
            }
        } else {
            try {
                quantity = Integer.valueOf(text);
            } catch (NumberFormatException e) {
                throw FinanceErrors.field("new_value", "请填写整数");
            }
        }
        if (quantity <= 0) {
            throw FinanceErrors.field("new_value", "数量必须大于 0");
        }
        int oldQuantity = order.getQuantity() == null ? 0 : order.getQuantity();
        if (quantity == oldQuantity) {
            throw FinanceErrors.field("new_value", "新数量与原数量相同，无需修改");
        }
        if (totalWeight == null) {
            totalWeight = defaultWeight(order);
            if (oldQuantity > 0 && quantity != oldQuantity) {
                totalWeight = totalWeight.multiply(BigDecimal.valueOf(quantity))
                        .divide(BigDecimal.valueOf(oldQuantity), 2, RoundingMode.HALF_UP);
            }
        }
        if (totalWeight.signum() <= 0) {
            throw FinanceErrors.field("new_value", "总重必须大于 0");
        }
        if (totalWeight.compareTo(new BigDecimal("99999999.99")) > 0 || totalWeight.stripTrailingZeros().scale() > 2) {
            throw FinanceErrors.field("new_value", "总重不能超过 99,999,999.99 千克，最多保留 2 位小数");
        }
        return new QuantityChange(quantity, totalWeight);
    }

    private BigDecimal defaultWeight(SaleOrder order) {
        if (order.getTotalWeight() != null && order.getTotalWeight().signum() > 0) {
            return order.getTotalWeight();
        }
        BigDecimal avg = FinanceSupport.nz(order.getAvgWeight());
        return avg.multiply(BigDecimal.valueOf(order.getQuantity() == null ? 0 : order.getQuantity()));
    }

    private BigDecimal saleAmount(String method, BigDecimal totalWeight, BigDecimal unitPrice, Integer quantity) {
        BigDecimal price = FinanceSupport.nz(unitPrice);
        BigDecimal amount = FinanceConst.PRICE_HEAD.equals(method)
                ? price.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity))
                : FinanceSupport.nz(totalWeight).multiply(price);
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private int currentStock(Long batchId) {
        if (batchId == null) {
            return 0;
        }
        try {
            BatchInfo batch = batchQueryService.requireBatch(batchId);
            return batch.currentCount() == null ? 0 : batch.currentCount();
        } catch (BizException e) {
            return 0;
        }
    }

    private String batchNo(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batchQueryService.requireBatch(batchId).batchNo();
        } catch (BizException e) {
            return null;
        }
    }

    private SaleOrder load(Long bizId) {
        return bizId == null ? null : TenantSkip.call(() -> orderMapper.selectById(bizId));
    }

    private void record(ChangeRequest request, ChangeRecord record, ChangeType changeType,
                        Map<String, Object> before, Map<String, Object> after) {
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(request.getSubmittedBy())
                .operatorRole(request.getSubmitterRole())
                .opType("RECORD_EDIT")
                .opAction(changeType.name())
                .result("SUCCESS")
                .bizType(BizType.SALE_ORDER.name())
                .bizId(request.getBizId())
                .targetName(record.recordTitle())
                .farmId(request.getFarmId())
                .reason(request.getReason())
                .before(before)
                .after(after)
                .changeRequestId(request.getId())
                .build());
    }

    private record QuantityChange(int quantity, BigDecimal totalWeight) {
    }

    private static BigDecimal parseDecimal(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String monthDay(LocalDate date) {
        return date == null ? "" : date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private static String priceText(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 千分位金额 / 重量：188,160 / 11,760.5 */
    private static String amountText(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##",
                DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(value);
    }

    private static String money(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.##",
                DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(FinanceSupport.nz(value));
    }

    private static ChangeType changeTypeOf(String code) {
        ChangeType type = ChangeRequestJson.parseChangeType(code);
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改类型不正确");
        }
        return type;
    }
}
