package com.zhimu.slmp.finance.ui.saledetail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SaleOrderVoidRequest;
import com.zhimu.slmp.finance.model.request.SaleReceiptRequest;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.ui.common.ReceiptHost;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.finance.util.ReceiptFormDelegate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * E07 出栏单详情：销售金额、收款进度、约定收款、收款记录、出栏信息；“更多”菜单（修改 / 作废 / 提交修改申请 / 查看修改记录）；
 * 登记收款底部面板（API-E19–E22）。
 */
public class SaleOrderDetailViewModel extends FinanceViewModel implements ReceiptHost {

    public static final int MENU_APPLY_CHANGE = 1;
    public static final int MENU_LOG = 2;
    public static final int MENU_EDIT = 3;
    public static final int MENU_VOID = 4;
    public static final int BANNER_NONE = 0;
    public static final int BANNER_HAS_RECEIPT = 1;
    public static final int BANNER_PENDING = 2;
    static final int REASON_MAX = 200;

    /** 菜单项（页面按 id 取文字与图标） */
    public static final class MenuSpec {
        public final int id;
        public final boolean enabled;
        @Nullable
        public final String note;

        MenuSpec(int id, boolean enabled, @Nullable String note) {
            this.id = id;
            this.enabled = enabled;
            this.note = note;
        }
    }

    private final SaleOrderRepository repository;
    private final BaseDataRepository baseData;
    private final ReceiptFormDelegate receipt = new ReceiptFormDelegate();

    private final MutableLiveData<SaleOrderDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<List<MenuSpec>> menuItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> bannerMode = new MutableLiveData<>(BANNER_NONE);
    private final MutableLiveData<Boolean> voiding = new MutableLiveData<>(false);
    private final SingleLiveEvent<String> showReasonDialog = new SingleLiveEvent<>();
    /** 打开修改记录面板（U14），值为出栏单 ID */
    private final SingleLiveEvent<Long> showLogSheet = new SingleLiveEvent<>();
    private long orderId;
    private boolean changed;

    @Inject
    public SaleOrderDetailViewModel(@NonNull SaleOrderRepository repository, @NonNull BaseDataRepository baseData,
                                    @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
        this.baseData = baseData;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        receipt.attach(handle);
        Long id = handle.get(RouterPath.EXTRA_SALE_ORDER_ID);
        if (id == null) {
            id = handle.get(RouterPath.EXTRA_BIZ_ID);
        }
        orderId = id == null ? 0 : id;
        load();
        launch(baseData.getDicts(FinanceConst.DICT_PAYMENT_METHOD, null), receipt::setPaymentMethods, e -> {
        });
    }

    public void load() {
        if (denyStaff()) {
            return;
        }
        load(repository.getOrderDetail(orderId), this::apply);
    }

    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    // ===== 对外 =====

    public LiveData<SaleOrderDetailVO> getDetail() {
        return detail;
    }

    public LiveData<List<MenuSpec>> getMenuItems() {
        return menuItems;
    }

    public LiveData<Integer> getBannerMode() {
        return bannerMode;
    }

    public LiveData<Boolean> getVoiding() {
        return voiding;
    }

    public SingleLiveEvent<String> getShowReasonDialog() {
        return showReasonDialog;
    }

    public SingleLiveEvent<Long> getShowLogSheet() {
        return showLogSheet;
    }

    @NonNull
    @Override
    public ReceiptFormDelegate getReceipt() {
        return receipt;
    }

    /** 返回来源页时是否需要刷新（收款、作废、修改后） */
    public boolean isChanged() {
        return changed;
    }

    void apply(@NonNull SaleOrderDetailVO d) {
        detail.setValue(d);
        menuItems.setValue(buildMenu(d));
        bannerMode.setValue(bannerOf(d));
    }

    @NonNull
    static List<MenuSpec> buildMenu(@NonNull SaleOrderDetailVO d) {
        List<MenuSpec> list = new ArrayList<>();
        SaleOrderDetailVO.Actions a = d.getActions();
        boolean voided = FinanceFormat.isVoided(d);
        if (!voided && a != null && a.isCanApplyChange()) {
            boolean review = a.isChangeInReview() || d.getPendingChange() != null;
            list.add(new MenuSpec(MENU_APPLY_CHANGE, !review, review ? "审核中" : "需审核"));
            list.add(new MenuSpec(MENU_LOG, true, null));
        } else if (!voided && a != null && (a.isCanEdit() || a.isCanVoid())) {
            if (a.isCanEdit()) {
                list.add(new MenuSpec(MENU_EDIT, true, null));
            }
            list.add(new MenuSpec(MENU_LOG, true, null));
            if (a.isCanVoid()) {
                list.add(new MenuSpec(MENU_VOID, true, null));
            }
        } else {
            list.add(new MenuSpec(MENU_LOG, true, null));
        }
        return list;
    }

    static int bannerOf(@NonNull SaleOrderDetailVO d) {
        if (FinanceFormat.isVoided(d)) {
            return BANNER_NONE;
        }
        if (d.getPendingChange() != null) {
            return BANNER_PENDING;
        }
        return d.getReceivedAmount() != null && d.getReceivedAmount().signum() > 0 ? BANNER_HAS_RECEIPT : BANNER_NONE;
    }

    /** 待审核横幅“修改申请待审核：单价每千克 16.00 元改为 15.50 元，审核期间原单照常有效。” */
    @NonNull
    public static String pendingText(@NonNull SaleOrderDetailVO d) {
        SaleOrderDetailVO.PendingChange p = d.getPendingChange();
        return "修改申请待审核：" + (p == null ? "" : p.getSummaryText()) + "，审核期间原单照常有效。";
    }

    /** 金额说明“98 头 · 11,760 千克 · 每千克 16.00 元”/“98 头 · 每头 1,920.00 元” */
    @NonNull
    public static String amountNote(@NonNull SaleOrderDetailVO d) {
        String qty = FinanceFormat.count(d.getQuantity(), d.getCountUnitText());
        if (FinanceConst.BY_HEAD.equals(d.getPricingMethod())) {
            return qty + " · 每" + d.getCountUnitText() + " " + FinanceFormat.price(d.getUnitPrice()) + " 元";
        }
        return qty + " · " + FinanceFormat.kg(d.getTotalWeight()) + " · 每千克 " + FinanceFormat.price(d.getUnitPrice())
                + " 元";
    }

    /** 约定收款行文字 */
    @NonNull
    public static String dueText(@NonNull SaleOrderDetailVO d) {
        if (FinanceConst.PAY_PAID.equals(d.getPaymentStatus()) && d.getPaidOffDate() != null) {
            return FinanceFormat.monthDay(d.getPaidOffDate()) + "已收齐";
        }
        if (d.getDueDate() == null) {
            return "约定收款 未填写 · 按 " + FinanceFormat.monthDay(d.getEffectiveDueDate()) + "提醒";
        }
        return "约定收款 " + FinanceFormat.dateWeek(d.getDueDate());
    }

    // ===== 菜单 =====

    public void onMenuSelected(int itemId) {
        SaleOrderDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        switch (itemId) {
            case MENU_APPLY_CHANGE:
                open(NavTarget.of(RouterPath.E08).put(RouterPath.EXTRA_SALE_ORDER_ID, d.getId()).forResult());
                break;
            case MENU_LOG:
                showLogSheet.setValue(d.getId());
                break;
            case MENU_EDIT:
                open(NavTarget.of(RouterPath.E06).put(RouterPath.EXTRA_SALE_ORDER_ID, d.getId()).forResult());
                break;
            case MENU_VOID:
                showReasonDialog.setValue("作废后 " + d.getBatchNo() + " 存栏恢复 "
                        + FinanceFormat.count(d.getQuantity(), d.getCountUnitText()) + "，批次回到在养状态。");
                break;
            default:
                break;
        }
    }

    /** 作废未收款出栏单（API-E21），原因 1–200 字 */
    public void voidOrder(@Nullable String rawReason) {
        SaleOrderDetailVO d = detail.getValue();
        String reason = rawReason == null ? "" : rawReason.trim();
        if (d == null || reason.isEmpty() || Boolean.TRUE.equals(voiding.getValue())) {
            return;
        }
        if (reason.length() > REASON_MAX) {
            showMessage("作废原因不能超过 200 字");
            return;
        }
        voiding.setValue(true);
        launch(repository.voidOrder(d.getId(), new SaleOrderVoidRequest(reason, d.getVersion())), r -> {
            voiding.setValue(false);
            changed = true;
            apply(r);
            notifyTodoChanged(FinanceConst.BIZ_SALE_ORDER, d.getId());
            showMessage("已作废出栏单，存栏已恢复");
        }, e -> {
            voiding.setValue(false);
            handleError(e);
        });
    }

    public void openSettlement() {
        SaleOrderDetailVO d = detail.getValue();
        if (d != null) {
            open(NavTarget.of(RouterPath.E10).put(RouterPath.EXTRA_BATCH_ID, d.getBatchId()));
        }
    }

    /** 收款记录“已生成收入”：进入 E04 并定位那条收入 */
    public void openIncome(@NonNull SaleOrderDetailVO.Receipt r) {
        if (r.getFinanceRecordId() == null) {
            return;
        }
        open(NavTarget.of(RouterPath.E04).put(RouterPath.EXTRA_HIGHLIGHT_ID, r.getFinanceRecordId())
                .put(RouterPath.EXTRA_MONTH, FinanceFormat.monthOf(r.getReceiptDate())));
    }

    /** 待审核横幅“查看”→ U02 我的修改申请 */
    public void openPendingRequest() {
        open(NavTarget.of(RouterPath.U02));
    }

    // ===== 收款 =====

    /** “登记收款”：默认未收金额、今天、E15 的上次收款方式 */
    public void openReceipt() {
        SaleOrderDetailVO d = detail.getValue();
        if (d == null || d.getActions() == null || !d.getActions().isCanReceive()) {
            return;
        }
        receipt.open(ReceiptFormDelegate.Target.of(d), null);
        launch(repository.getPrefill(null, null), p -> receipt.setLastPaymentMethod(p.getLastPaymentMethod()),
                e -> {
                });
    }

    @Override
    public void confirmReceipt() {
        ReceiptFormDelegate.Target t = receipt.getTarget().getValue();
        if (t == null || Boolean.TRUE.equals(receipt.getSubmitting().getValue())) {
            return;
        }
        SaleReceiptRequest req = receipt.validate();
        if (req == null) {
            return;
        }
        String key = receipt.idem().keyFor(req);
        receipt.setSubmitting(true);
        launch(repository.addReceipt(t.saleOrderId, req, key), r -> onReceived(req.getAmount(), r),
                e -> onReceiptError(e, key, req.getAmount()));
    }

    private void onReceived(@NonNull BigDecimal amount, @Nullable SaleOrderDetailVO r) {
        receipt.close();
        changed = true;
        if (r != null) {
            apply(r);
        } else {
            load();
        }
        notifyTodoChanged(FinanceConst.BIZ_SALE_ORDER, orderId);
        showMessage(ReceiptFormDelegate.successText(amount, r));
    }

    private void onReceiptError(@NonNull ApiException e, @NonNull String key, @NonNull BigDecimal amount) {
        if (FinanceErrors.isTimeout(e)) {
            launch(repository.getSubmission(key), s -> {
                if (s.isSaved()) {
                    onReceived(amount, null);
                } else {
                    receipt.setSubmitting(false);
                    showMessage(FinanceErrors.MSG_TIMEOUT);
                }
            }, e2 -> {
                receipt.setSubmitting(false);
                showMessage(FinanceErrors.MSG_TIMEOUT);
            });
            return;
        }
        receipt.setSubmitting(false);
        if (e.isNetwork()) {
            showMessage(FinanceErrors.MSG_NETWORK_KEPT);
            return;
        }
        receipt.idem().clear();
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            // 已在其他设备收齐或已作废：关闭面板并刷新
            receipt.close();
            showMessage(e.getMessage());
            load();
            return;
        }
        if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
            receipt.setErrors(e.getFieldErrors());
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            receipt.close();
        }
        handleError(e);
    }

    /** 从 E06 编辑、E08 修改申请返回 */
    public void onChildResult(@Nullable String text) {
        changed = true;
        load();
        if (text != null) {
            showMessage(text);
        }
    }
}
