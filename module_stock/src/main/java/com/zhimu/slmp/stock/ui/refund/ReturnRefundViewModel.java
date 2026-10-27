package com.zhimu.slmp.stock.ui.refund;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.request.ReturnRefundRequest;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.SubmitGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;

/** 两个退货详情入口共用的到账表单，状态及待确认幂等键由 SavedStateHandle 保留。 */
public class ReturnRefundViewModel extends BaseViewModel {
    private final StockRepository repository;
    private final FarmContext farm;
    private final SubmitGuard guard = new SubmitGuard();
    public MutableLiveData<String> amountText = new MutableLiveData<>("");
    public MutableLiveData<String> refundDate = new MutableLiveData<>("");
    public MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<StockOutboundVO> record = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> errors = new MutableLiveData<>(Collections.emptyMap());
    private final SingleLiveEvent<Void> saved = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> refreshRecord = new SingleLiveEvent<>();
    @Nullable private ReturnRefundRequest uncertainRequest;
    private long uncertainOutboundId;

    @Inject public ReturnRefundViewModel(StockRepository repository, FarmContext farm) {
        this.repository = repository;
        this.farm = farm;
    }

    @Override protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        guard.attach(handle);
        amountText = handle.getLiveData("refund_amount_text", "");
        refundDate = handle.getLiveData("refund_date", "");
        remark = handle.getLiveData("refund_remark", "");
        Long pendingId = handle.get("refund_outbound_id");
        if (pendingId != null && guard.current() != null && guard.snapshot() != null) {
            try {
                uncertainRequest = com.zhimu.slmp.network.GsonFactory.get().fromJson(guard.snapshot(), ReturnRefundRequest.class);
                uncertainOutboundId = pendingId;
            } catch (RuntimeException ignored) {
                // 无法恢复的旧表单仍由服务端一单一次约束兜底，用户可重新填写。
            }
        }
    }

    public static boolean isReturn(@Nullable StockOutboundVO value) {
        return value != null && "DRUG".equals(value.materialType) && "RETURN".equals(value.outType);
    }

    public static boolean isRefunded(@Nullable StockOutboundVO value) {
        return value != null && "REFUNDED".equals(value.refundStatus);
    }

    public static boolean canRegister(@Nullable StockOutboundVO value) {
        return isReturn(value) && !isRefunded(value) && value.actions != null && Boolean.TRUE.equals(value.actions.canRefund);
    }

    public static boolean canCorrect(@Nullable StockOutboundVO value) {
        return isReturn(value) && isRefunded(value) && value.actions != null && Boolean.TRUE.equals(value.actions.canEditRefund);
    }

    public void bind(@Nullable StockOutboundVO value) {
        record.setValue(value);
        if (value != null && value.outboundId == uncertainOutboundId && uncertainRequest != null && matches(value, uncertainRequest)) {
            finish(value);
        }
    }

    public boolean isEditing() { return Boolean.TRUE.equals(getSavedState().get("refund_editing")); }

    public boolean open(boolean edit) {
        StockOutboundVO value = record.getValue();
        if (Boolean.TRUE.equals(submitting.getValue()) || (edit ? !canCorrect(value) : !canRegister(value))) return false;
        Long oldId = getSavedState().get("refund_outbound_id");
        boolean newForm = oldId == null || oldId != value.outboundId || edit != isEditing();
        if (newForm) {
            guard.clear();
            amountText.setValue(edit && value.refundAmount != null ? value.refundAmount.toPlainString() : "");
            refundDate.setValue(edit ? value.refundDate : DateFormatter.todayString());
            remark.setValue(edit && value.refundRemark != null ? value.refundRemark : "");
        }
        getSavedState().set("refund_outbound_id", value.outboundId);
        getSavedState().set("refund_farm_id", farm.currentFarmId());
        getSavedState().set("refund_editing", edit);
        errors.setValue(Collections.emptyMap());
        return true;
    }

    public String summaryText() {
        StockOutboundVO value = record.getValue();
        return value == null ? "" : value.outboundNo + " · " + value.materialName + "\n库存金额 "
                + StockCalc.money(value.amount) + " 元（仅供核对，请填写实际到账金额）";
    }

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) return;
        StockOutboundVO value = record.getValue();
        if (value == null || value.version == null || (isEditing() ? !canCorrect(value) : !canRegister(value))) {
            errors.setValue(Collections.singletonMap("submit", value != null && value.refundBlockReason != null
                    ? value.refundBlockReason : "当前退货单不能登记或更正退款，请刷新后查看"));
            return;
        }
        if (!Objects.equals(getSavedState().get("refund_farm_id"), farm.currentFarmId())) {
            errors.setValue(Collections.singletonMap("submit", "养殖场已切换，请返回当前养殖场重新打开退货单"));
            return;
        }
        ReturnRefundRequest request = validate(value);
        if (request == null) return;
        submitting.setValue(true);
        uncertainRequest = null;
        String key = guard.keyFor(request);
        launch(isEditing() ? repository.updateReturnRefund(value.outboundId, request, key)
                : repository.registerReturnRefund(value.outboundId, request, key), this::finish, error -> {
            submitting.setValue(false);
            if (error.hasFieldErrors()) errors.setValue(new LinkedHashMap<>(error.getFieldErrors()));
            else errors.setValue(Collections.singletonMap("submit", error.getMessage() + "；填写内容已保留，可重试"));
            // 查询原单核对超时/版本冲突结果；同一请求重试继续使用原幂等键。
            uncertainRequest = request;
            uncertainOutboundId = value.outboundId;
            refreshRecord.setValue(null);
        });
    }

    @Nullable private ReturnRefundRequest validate(StockOutboundVO value) {
        Map<String, String> problems = new LinkedHashMap<>();
        BigDecimal amount = null;
        try {
            String raw = text(amountText.getValue());
            if (!raw.matches("[0-9]+(?:\\.[0-9]+)?")) throw new NumberFormatException();
            amount = new BigDecimal(raw);
            if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999999.99")) > 0)
                problems.put("amount", "到账金额须大于 0 且不超过 9,999,999,999.99 元");
            else if (amount.stripTrailingZeros().scale() > 2) problems.put("amount", "金额最多保留 2 位小数");
        } catch (NumberFormatException e) { problems.put("amount", "请输入实际到账金额"); }
        LocalDate date = DateFormatter.parseDate(refundDate.getValue());
        LocalDate outDate = DateFormatter.parseDate(value.outDate);
        if (date == null) problems.put("refund_date", "请选择到账日期");
        else if (date.isAfter(DateFormatter.today())) problems.put("refund_date", "到账日期不能晚于今天");
        else if (outDate != null && date.isBefore(outDate)) problems.put("refund_date", "到账日期不能早于退货日期");
        String note = text(remark.getValue());
        if (note.length() > 200) problems.put("remark", "说明最多 200 字");
        errors.setValue(problems);
        if (!problems.isEmpty()) return null;
        ReturnRefundRequest request = new ReturnRefundRequest();
        request.amount = amount.setScale(2);
        request.refundDate = refundDate.getValue();
        request.remark = note.isEmpty() ? null : note;
        request.version = value.version;
        return request;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static boolean matches(StockOutboundVO value, ReturnRefundRequest request) {
        return isRefunded(value) && value.refundAmount != null && value.refundAmount.compareTo(request.amount) == 0
                && Objects.equals(value.refundDate, request.refundDate) && text(value.refundRemark).equals(text(request.remark));
    }
    private void finish(StockOutboundVO value) {
        uncertainRequest = null;
        submitting.setValue(false);
        errors.setValue(Collections.emptyMap());
        guard.clear();
        record.setValue(value);
        getSavedState().remove("refund_outbound_id");
        saved.setValue(null);
        showMessage(isEditing() ? "退款已更正，关联收入已同步更新" : "退款已登记，已自动生成关联收入");
    }
    public LiveData<StockOutboundVO> getRecord() { return record; }
    public LiveData<Boolean> getSubmitting() { return submitting; }
    public LiveData<Map<String, String>> getErrors() { return errors; }
    public SingleLiveEvent<Void> getSaved() { return saved; }
    public SingleLiveEvent<Void> getRefreshRecord() { return refreshRecord; }
}
