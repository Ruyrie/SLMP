package com.zhimu.slmp.finance.ui.salechange;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.ui.change.ChangeRepository;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SaleChangePreviewRequest;
import com.zhimu.slmp.finance.model.vo.SaleChangePreviewVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.FinanceIdemKeeper;
import com.zhimu.slmp.finance.util.SaleAmountCalculator;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * E08 出栏单修改申请（只对已有收款的出栏单）：修改单价 / 修改数量 / 作废，审核通过后的预计结果（API-E23，新值变化防抖 300 毫秒），
 * 修改原因 1–200 字，经 U01 取表单、U02 提交（biz_type = SALE_ORDER，record_version = content_version）。
 */
public class SaleChangeRequestViewModel extends FinanceViewModel {

    public static final String F_PRICE = "new_unit_price";
    public static final String F_QUANTITY = "new_quantity";
    public static final String F_WEIGHT = "new_total_weight";
    public static final String F_REASON = "reason";
    static final int REASON_MAX = 200;
    static final long PREVIEW_DEBOUNCE_MS = 300;

    private final SaleOrderRepository repository;
    private final ChangeRepository changeRepository;
    private final FinanceIdemKeeper idem = new FinanceIdemKeeper("sale_change");

    /** 双向绑定 */
    public final MutableLiveData<String> changeMode = new MutableLiveData<>(FinanceConst.MODE_UNIT_PRICE);
    public final MutableLiveData<String> newUnitPriceText = new MutableLiveData<>("");
    public final MutableLiveData<String> newQuantityText = new MutableLiveData<>("");
    public final MutableLiveData<String> newTotalWeightText = new MutableLiveData<>("");
    public final MutableLiveData<String> reason = new MutableLiveData<>("");

    private final MutableLiveData<SaleOrderDetailVO> original = new MutableLiveData<>();
    private final MutableLiveData<ChangeRequestFormVO> formCtx = new MutableLiveData<>();
    private final MutableLiveData<SaleChangePreviewVO> preview = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final SingleLiveEvent<String> finishWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> finishWithMessage = new SingleLiveEvent<>();
    private final PublishSubject<Long> previewTrigger = PublishSubject.create();
    private long orderId;

    @Inject
    public SaleChangeRequestViewModel(@NonNull SaleOrderRepository repository,
                                      @NonNull ChangeRepository changeRepository, @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
        this.changeRepository = changeRepository;
        canSubmit.addSource(changeMode, v -> onValueChanged());
        canSubmit.addSource(newUnitPriceText, v -> onValueChanged());
        canSubmit.addSource(newQuantityText, v -> onValueChanged());
        canSubmit.addSource(newTotalWeightText, v -> onValueChanged());
        canSubmit.addSource(reason, v -> recompute());
        canSubmit.addSource(submitting, v -> recompute());
        canSubmit.addSource(original, v -> recompute());
        canSubmit.setValue(false);
        disposables.add(previewTrigger
                .debounce(PREVIEW_DEBOUNCE_MS, TimeUnit.MILLISECONDS, Schedulers.computation())
                .switchMapSingle(t -> buildPreview().subscribeOn(Schedulers.io())
                        .map(java.util.Optional::of)
                        .onErrorReturn(e -> {
                            ApiException ae = ApiException.from(e);
                            if (ae.getCode() == ErrorCodes.STATE_CHANGED) {
                                AndroidSchedulers.mainThread().scheduleDirect(() -> onPreviewStateChanged(ae));
                            }
                            return java.util.Optional.empty();
                        }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(o -> preview.setValue(o.orElse(null)), e -> {
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        Long id = handle.get(RouterPath.EXTRA_SALE_ORDER_ID);
        orderId = id == null ? 0 : id;
        init();
    }

    void init() {
        if (denyStaff()) {
            return;
        }
        load(repository.getOrderDetail(orderId).flatMap(d -> changeRepository
                .getForm(FinanceConst.BIZ_SALE_ORDER, orderId)
                .map(f -> new Object[]{d, f})
                .onErrorReturnItem(new Object[]{d, null})), arr -> {
            original.setValue((SaleOrderDetailVO) arr[0]);
            formCtx.setValue((ChangeRequestFormVO) arr[1]);
            onValueChanged();
        });
    }

    @Override
    public void retry() {
        init();
    }

    public LiveData<SaleOrderDetailVO> getOriginal() {
        return original;
    }

    public LiveData<ChangeRequestFormVO> getFormCtx() {
        return formCtx;
    }

    public LiveData<SaleChangePreviewVO> getPreview() {
        return preview;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<String> getFinishWithResult() {
        return finishWithResult;
    }

    /** E23 的 40903（还没有收款，可以直接修改）：提示后关闭 */
    public SingleLiveEvent<String> getFinishWithMessage() {
        return finishWithMessage;
    }

    public void setMode(@NonNull String mode) {
        if (!mode.equals(changeMode.getValue())) {
            changeMode.setValue(mode);
            fieldErrors.setValue(Collections.emptyMap());
        }
    }

    public boolean isByHead() {
        SaleOrderDetailVO d = original.getValue();
        return d != null && FinanceConst.BY_HEAD.equals(d.getPricingMethod());
    }

    private void onValueChanged() {
        recompute();
        idem.clear();
        if (original.getValue() != null) {
            previewTrigger.onNext(System.nanoTime());
        }
    }

    private void recompute() {
        boolean ok = original.getValue() != null && !Boolean.TRUE.equals(submitting.getValue());
        if (!Objects.equals(canSubmit.getValue(), ok)) {
            canSubmit.setValue(ok);
        }
    }

    @NonNull
    SaleChangePreviewRequest previewRequest() {
        String mode = changeMode.getValue();
        if (FinanceConst.MODE_VOID.equals(mode)) {
            return new SaleChangePreviewRequest(FinanceConst.CHANGE_VOID, null, null, null, null, null);
        }
        if (FinanceConst.MODE_QUANTITY.equals(mode)) {
            return new SaleChangePreviewRequest(FinanceConst.CHANGE_UPDATE, FinanceConst.FIELD_QUANTITY, null,
                    SaleAmountCalculator.parseInt(newQuantityText.getValue()),
                    isByHead() ? null : SaleAmountCalculator.parseDecimal(newTotalWeightText.getValue()), null);
        }
        return new SaleChangePreviewRequest(FinanceConst.CHANGE_UPDATE, FinanceConst.FIELD_UNIT_PRICE,
                SaleAmountCalculator.parseDecimal(newUnitPriceText.getValue()), null, null, null);
    }

    @NonNull
    private Single<SaleChangePreviewVO> buildPreview() {
        return repository.previewChange(orderId, previewRequest());
    }

    private void onPreviewStateChanged(@NonNull ApiException e) {
        finishWithMessage.setValue(e.getMessage());
    }

    /** 本地预估的新销售金额（E23 未返回时用于校验“不能低于已收”） */
    @Nullable
    BigDecimal estimateNewSaleAmount() {
        SaleOrderDetailVO d = original.getValue();
        if (d == null) {
            return null;
        }
        String mode = changeMode.getValue();
        if (FinanceConst.MODE_UNIT_PRICE.equals(mode)) {
            BigDecimal p = SaleAmountCalculator.parseDecimal(newUnitPriceText.getValue());
            return SaleAmountCalculator.saleAmount(d.getPricingMethod(), d.getQuantity(), d.getTotalWeight(), p);
        }
        if (FinanceConst.MODE_QUANTITY.equals(mode)) {
            Integer q = SaleAmountCalculator.parseInt(newQuantityText.getValue());
            BigDecimal w = isByHead() ? d.getTotalWeight() : SaleAmountCalculator.parseDecimal(newTotalWeightText.getValue());
            return SaleAmountCalculator.saleAmount(d.getPricingMethod(), q, w, d.getUnitPrice());
        }
        return null;
    }

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        SaleOrderDetailVO d = original.getValue();
        if (d == null) {
            return e;
        }
        String mode = changeMode.getValue();
        BigDecimal received = d.getReceivedAmount() == null ? BigDecimal.ZERO : d.getReceivedAmount();
        if (FinanceConst.MODE_UNIT_PRICE.equals(mode)) {
            BigDecimal p = SaleAmountCalculator.parseDecimal(newUnitPriceText.getValue());
            if (p == null) {
                e.put(F_PRICE, "请输入新单价");
            } else if (p.signum() <= 0) {
                e.put(F_PRICE, "单价必须大于 0");
            } else if (d.getUnitPrice() != null && p.compareTo(d.getUnitPrice()) == 0) {
                e.put(F_PRICE, "新单价与原单价相同，无需修改");
            } else if (belowReceived(received)) {
                e.put(F_PRICE, "修改后销售金额不能低于已收 " + FinanceFormat.yuan(received));
            }
        } else if (FinanceConst.MODE_QUANTITY.equals(mode)) {
            Integer q = SaleAmountCalculator.parseInt(newQuantityText.getValue());
            if (q == null) {
                e.put(F_QUANTITY, "请输入新数量");
            } else if (q <= 0) {
                e.put(F_QUANTITY, "数量必须大于 0");
            } else if (q == d.getQuantity()) {
                e.put(F_QUANTITY, "新数量与原数量相同，无需修改");
            }
            if (!isByHead()) {
                BigDecimal w = SaleAmountCalculator.parseDecimal(newTotalWeightText.getValue());
                if (w == null) {
                    e.put(F_WEIGHT, "请输入新总重");
                } else if (w.signum() <= 0) {
                    e.put(F_WEIGHT, "总重必须大于 0");
                }
            }
            if (!e.containsKey(F_QUANTITY) && !e.containsKey(F_WEIGHT) && belowReceived(received)) {
                e.put(F_QUANTITY, "修改后销售金额不能低于已收 " + FinanceFormat.yuan(received));
            }
        }
        String r = reason.getValue() == null ? "" : reason.getValue().trim();
        if (r.isEmpty()) {
            e.put(F_REASON, "请填写修改原因");
        } else if (r.length() > REASON_MAX) {
            e.put(F_REASON, "修改原因不能超过 200 字");
        }
        return e;
    }

    private boolean belowReceived(@NonNull BigDecimal received) {
        SaleChangePreviewVO p = preview.getValue();
        BigDecimal after = p != null && p.getSaleAmountAfter() != null ? p.getSaleAmountAfter()
                : estimateNewSaleAmount();
        return after != null && after.compareTo(received) < 0;
    }

    /** U02 的新值：改单价为单价；改数量为 JSON（数量与按重量计价时联动的总重，见交付说明） */
    @Nullable
    String newValue() {
        String mode = changeMode.getValue();
        if (FinanceConst.MODE_UNIT_PRICE.equals(mode)) {
            BigDecimal p = SaleAmountCalculator.parseDecimal(newUnitPriceText.getValue());
            return p == null ? null : p.stripTrailingZeros().toPlainString();
        }
        if (FinanceConst.MODE_QUANTITY.equals(mode)) {
            Integer q = SaleAmountCalculator.parseInt(newQuantityText.getValue());
            BigDecimal w = SaleAmountCalculator.parseDecimal(newTotalWeightText.getValue());
            if (isByHead() || w == null) {
                return q == null ? null : String.valueOf(q);
            }
            return "{\"quantity\":" + q + ",\"total_weight\":" + w.stripTrailingZeros().toPlainString() + "}";
        }
        return null;
    }

    public void submit() {
        SaleOrderDetailVO d = original.getValue();
        if (d == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        boolean isVoid = FinanceConst.MODE_VOID.equals(changeMode.getValue());
        String field = isVoid ? null : FinanceConst.MODE_QUANTITY.equals(changeMode.getValue())
                ? FinanceConst.FIELD_QUANTITY : FinanceConst.FIELD_UNIT_PRICE;
        ChangeRequestFormVO form = formCtx.getValue();
        String version = form != null && form.getRecordVersion() != null ? form.getRecordVersion()
                : String.valueOf(d.getContentVersion());
        ChangeRequestSubmitRequest req = new ChangeRequestSubmitRequest(FinanceConst.BIZ_SALE_ORDER, d.getId(),
                isVoid ? FinanceConst.CHANGE_VOID : FinanceConst.CHANGE_UPDATE, field, newValue(), version,
                reason.getValue().trim(), null);
        submitting.setValue(true);
        launch(changeRepository.submit(req, idem.keyFor(req)), r -> {
            submitting.setValue(false);
            idem.clear();
            notifyTodoChanged(FinanceConst.BIZ_SALE_ORDER, d.getId());
            finishWithResult.setValue("已提交修改申请，等待管理员审核");
        }, e -> {
            submitting.setValue(false);
            if (!e.isNetwork()) {
                idem.clear();
            }
            handleError(e);
        });
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    /** 40902 内容已更新：重新取原单；其余 40903 只提示 */
    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            init();
        }
    }

    public boolean isDirty() {
        return !isBlank(newUnitPriceText.getValue()) || !isBlank(newQuantityText.getValue())
                || !isBlank(newTotalWeightText.getValue()) || !isBlank(reason.getValue());
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
