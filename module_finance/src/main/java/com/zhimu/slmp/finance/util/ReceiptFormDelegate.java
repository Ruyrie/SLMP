package com.zhimu.slmp.finance.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SaleReceiptRequest;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登记收款表单（E07、E09 共用，技术准备文档 2.6.2）：金额默认未收金额、日期默认今天、方式默认 E15 的 last_payment_method；
 * 校验收款金额 > 0、2 位小数、≤ 未收金额，日期 ≤ 今天、≥ 出栏日期。提交由所在页 ViewModel 调用 API-E22。
 */
public class ReceiptFormDelegate {

    public static final String F_AMOUNT = "amount";
    public static final String F_DATE = "receipt_date";
    public static final String F_METHOD = "payment_method";

    /** 面板对应的出栏单 */
    public static final class Target {
        public final long saleOrderId;
        public final String batchNo;
        public final BigDecimal saleAmount;
        public final BigDecimal receivedAmount;
        public final BigDecimal unpaidAmount;
        public final String saleDate;

        public Target(long saleOrderId, @NonNull String batchNo, @NonNull BigDecimal saleAmount,
                      @NonNull BigDecimal receivedAmount, @NonNull BigDecimal unpaidAmount, @NonNull String saleDate) {
            this.saleOrderId = saleOrderId;
            this.batchNo = batchNo;
            this.saleAmount = saleAmount;
            this.receivedAmount = receivedAmount;
            this.unpaidAmount = unpaidAmount;
            this.saleDate = saleDate;
        }

        @NonNull
        public static Target of(@NonNull SaleOrderItemVO o) {
            return new Target(o.getId(), o.getBatchNo(), nz(o.getSaleAmount()), nz(o.getReceivedAmount()),
                    nz(o.getUnpaidAmount()), o.getSaleDate());
        }

        private static BigDecimal nz(@Nullable BigDecimal v) {
            return v == null ? BigDecimal.ZERO : v;
        }
    }

    /** 双向绑定 */
    public final MutableLiveData<String> amountText = new MutableLiveData<>("");
    public final MutableLiveData<String> receiptDate = new MutableLiveData<>("");
    public final MutableLiveData<String> paymentMethod = new MutableLiveData<>(FinanceConst.PAY_BANK);

    private final MutableLiveData<List<DictItemVO>> paymentMethods = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Map<String, String>> errors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Target> target = new MutableLiveData<>();
    /** 打开面板（金额框聚焦）/ 关闭面板 */
    private final SingleLiveEvent<Boolean> showSheet = new SingleLiveEvent<>();
    private final FinanceIdemKeeper idem = new FinanceIdemKeeper("receipt");

    public void attach(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
    }

    public LiveData<List<DictItemVO>> getPaymentMethods() {
        return paymentMethods;
    }

    public LiveData<Map<String, String>> getErrors() {
        return errors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Target> getTarget() {
        return target;
    }

    public SingleLiveEvent<Boolean> getShowSheet() {
        return showSheet;
    }

    @NonNull
    public FinanceIdemKeeper idem() {
        return idem;
    }

    /** U10 字典（PAYMENT_METHOD）；加载失败或为空时用四种默认方式 */
    public void setPaymentMethods(@Nullable List<DictItemVO> list) {
        paymentMethods.setValue(list == null || list.isEmpty() ? Collections.emptyList() : list);
    }

    /** 可选的收款方式 code → 名称（字典为空时用默认四种） */
    @NonNull
    public Map<String, String> methodOptions() {
        Map<String, String> map = new LinkedHashMap<>();
        List<DictItemVO> list = paymentMethods.getValue();
        if (list != null) {
            for (DictItemVO d : list) {
                map.put(d.getCode(), d.getName());
            }
        }
        if (map.isEmpty()) {
            for (String c : new String[]{FinanceConst.PAY_BANK, FinanceConst.PAY_WECHAT, FinanceConst.PAY_ALIPAY,
                    FinanceConst.PAY_CASH}) {
                map.put(c, FinanceConst.paymentMethodText(c));
            }
        }
        return map;
    }

    /** 打开面板：默认未收金额、今天、上次使用的收款方式 */
    public void open(@NonNull Target t, @Nullable String lastPaymentMethod) {
        if (!java.util.Objects.equals(target.getValue() == null ? null : target.getValue().saleOrderId,
                t.saleOrderId)) {
            idem.clear();
        }
        target.setValue(t);
        amountText.setValue(t.unpaidAmount.stripTrailingZeros().toPlainString());
        receiptDate.setValue(DateFormatter.todayString());
        Map<String, String> options = methodOptions();
        paymentMethod.setValue(lastPaymentMethod != null && options.containsKey(lastPaymentMethod)
                ? lastPaymentMethod : options.keySet().iterator().next());
        errors.setValue(Collections.emptyMap());
        submitting.setValue(false);
        showSheet.setValue(true);
    }

    public void setLastPaymentMethod(@Nullable String code) {
        if (code != null && methodOptions().containsKey(code)) {
            paymentMethod.setValue(code);
        }
    }

    /** 摘要“ZM2609-012 · 销售金额 188,160 元 · 已收 100,000 元” */
    @NonNull
    public String summaryText() {
        Target t = target.getValue();
        if (t == null) {
            return "";
        }
        return t.batchNo + " · 销售金额 " + FinanceFormat.yuan(t.saleAmount) + " · 已收 "
                + FinanceFormat.yuan(t.receivedAmount);
    }

    /** 默认辅助文字“最多 88,160 元，收款合计不能超过销售金额 188,160 元” */
    @NonNull
    public String helperText() {
        Target t = target.getValue();
        if (t == null) {
            return "";
        }
        return "最多 " + FinanceFormat.yuan(t.unpaidAmount) + "，收款合计不能超过销售金额 "
                + FinanceFormat.yuan(t.saleAmount);
    }

    /** 校验；通过时返回请求，失败时写 errors 并返回 null */
    @Nullable
    public SaleReceiptRequest validate() {
        Target t = target.getValue();
        if (t == null) {
            return null;
        }
        Map<String, String> e = new LinkedHashMap<>();
        String text = amountText.getValue() == null ? "" : amountText.getValue().trim();
        BigDecimal amount = SaleAmountCalculator.parseDecimal(text);
        if (text.isEmpty()) {
            e.put(F_AMOUNT, "请输入收款金额");
        } else if (amount == null || amount.signum() <= 0) {
            e.put(F_AMOUNT, "收款金额必须大于 0");
        } else if (amount.stripTrailingZeros().scale() > 2) {
            e.put(F_AMOUNT, "金额最多保留 2 位小数");
        } else if (amount.compareTo(t.unpaidAmount) > 0) {
            e.put(F_AMOUNT, "不能超过未收金额 " + FinanceFormat.yuan(t.unpaidAmount));
        }
        LocalDate date = DateFormatter.parseDate(receiptDate.getValue());
        LocalDate saleDate = DateFormatter.parseDate(t.saleDate);
        if (date == null) {
            e.put(F_DATE, "请选择收款日期");
        } else if (date.isAfter(DateFormatter.today())) {
            e.put(F_DATE, "收款日期不能晚于今天");
        } else if (saleDate != null && date.isBefore(saleDate)) {
            e.put(F_DATE, "收款日期不能早于出栏日期 " + DateFormatter.monthDay(saleDate));
        }
        String method = paymentMethod.getValue();
        if (method == null || method.isEmpty()) {
            e.put(F_METHOD, "请选择收款方式");
        }
        errors.setValue(e);
        if (!e.isEmpty()) {
            return null;
        }
        return new SaleReceiptRequest(amount, receiptDate.getValue(), method);
    }

    public void setSubmitting(boolean b) {
        submitting.setValue(b);
    }

    public void setErrors(@NonNull Map<String, String> map) {
        errors.setValue(new LinkedHashMap<>(map));
    }

    /** 成功或 40903 后关闭面板 */
    public void close() {
        submitting.setValue(false);
        idem.clear();
        showSheet.setValue(false);
    }

    /** 成功提示：部分收款“已登记收款 40,000 元，还有 48,160 元未收”；收齐“已登记收款 88,160 元，出栏单已收齐” */
    @NonNull
    public static String successText(@NonNull BigDecimal amount, @Nullable SaleOrderDetailVO result) {
        SaleOrderDetailVO.ReceiptResult rr = result == null ? null : result.getReceiptResult();
        boolean paidOff = rr != null ? rr.isPaidOff()
                : result != null && FinanceConst.PAY_PAID.equals(result.getPaymentStatus());
        BigDecimal unpaid = rr != null ? rr.getUnpaidAmount() : result == null ? null : result.getUnpaidAmount();
        if (paidOff || unpaid == null || unpaid.signum() <= 0) {
            return "已登记收款 " + FinanceFormat.yuan(amount) + "，出栏单已收齐";
        }
        return "已登记收款 " + FinanceFormat.yuan(amount) + "，还有 " + FinanceFormat.yuan(unpaid) + "未收";
    }

    /** 当前表单金额（提交成功后拼提示） */
    @Nullable
    public BigDecimal amount() {
        return SaleAmountCalculator.parseDecimal(amountText.getValue());
    }

    @NonNull
    public List<String> methodCodes() {
        return new ArrayList<>(methodOptions().keySet());
    }
}
