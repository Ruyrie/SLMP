package com.zhimu.slmp.stock.ui.otherout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.FeedOtherOutRequest;
import com.zhimu.slmp.stock.model.request.OutboundUpdateRequest;
import com.zhimu.slmp.stock.model.request.VoidRequest;
import com.zhimu.slmp.stock.model.vo.FeedOptionVO;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.ui.common.FormMode;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerItem;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockValidators;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * M10 饲料其他出库（技术准备文档 2.4.12，按需求补充）：
 * <ul>
 *     <li>NEW：API-M03（OTHER_OUT，结存为 0 不可选）；去向必选，调往他场与其他时说明必填；按均价折合；确认出库 API-M20（幂等键）</li>
 *     <li>VIEW：API-M21 只读，菜单按 actions（登记当天修改、作废，次日起提交修改申请）；EDIT：上限为 data.max_qty，API-M22；
 *     40307 弹“这条记录已不是今天登记的”转 U01；作废 API-M23</li>
 * </ul>
 * 草稿 page_code M10。
 */
public class FeedOtherOutViewModel extends StockFormViewModel {

    public static final String PAGE_CODE = "M10";
    public static final String F_FEED = "feed_id";
    public static final String F_QTY = "input_qty";
    public static final String F_DEST = "destination";
    public static final String F_REMARK = "remark";
    public static final String F_DATE = "out_date";
    public static final String PICKER_FEED = "stock_m10_feed";

    /** 去向 Chip：退回供应商 / 调往他场 / 出售 / 其他 */
    public static final String[][] DESTINATIONS = {
            {StockEnums.DEST_RETURN_SUPPLIER, "退回供应商"}, {StockEnums.DEST_TRANSFER_FARM, "调往他场"},
            {StockEnums.DEST_SALE, "出售"}, {StockEnums.DEST_OTHER, "其他"}};

    private final FarmContext farmContext;

    private final MutableLiveData<String> mode = new MutableLiveData<>(FormMode.NEW);
    private final MutableLiveData<List<FeedOptionVO>> feedOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FeedOptionVO> selectedFeed = new MutableLiveData<>();
    private final MutableLiveData<String> feedName = new MutableLiveData<>("");
    private final MutableLiveData<String> feedHint = new MutableLiveData<>();
    private final MutableLiveData<String> inputQty = new MutableLiveData<>("");
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<String> outDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> destination = new MutableLiveData<>();
    private final MutableLiveData<BigDecimal> maxQty = new MutableLiveData<>();
    private final MutableLiveData<String> maxText = new MutableLiveData<>();
    private final MediatorLiveData<String> amountText = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> remarkRequired = new MutableLiveData<>(false);
    private final MutableLiveData<String> destHint = new MutableLiveData<>();
    private final MutableLiveData<String> unitText = new MutableLiveData<>("千克");
    private final MutableLiveData<StockOutboundVO> record = new MutableLiveData<>();
    private final MutableLiveData<List<String[]>> readonlyRows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> pending = new MutableLiveData<>(false);
    private final MutableLiveData<PickerData> feedPicker = new MutableLiveData<>();

    private final SingleLiveEvent<String> submitted = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> confirmVoid = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> voided = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> notToday = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> edited = new SingleLiveEvent<>();

    @Nullable
    private Long recordId;

    @Inject
    public FeedOtherOutViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                                 @NonNull FarmContext farmContext) {
        super(repository, drafts);
        this.farmContext = farmContext;
        amountText.setValue("—");
        amountText.addSource(inputQty, v -> {
            clearFieldError(F_QTY);
            recomputeAmount();
        });
        amountText.addSource(remark, v -> clearFieldError(F_REMARK));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        init(StockExtras.readId(handle.get(StockExtras.EXTRA_RECORD_ID)));
    }

    public void init(@Nullable Long recordId) {
        this.recordId = recordId;
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            load(repository.getOutbound(recordId), this::applyRecord);
        } else {
            mode.setValue(FormMode.NEW);
            load(repository.listFeedOptions(StockEnums.PURPOSE_OTHER_OUT, null), list -> {
                feedOptions.setValue(list);
                rebuildPicker();
                markClean();
                checkDraft();
            });
        }
    }

    @Override
    public void retry() {
        init(recordId);
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    private void applyRecord(@NonNull StockOutboundVO r) {
        record.setValue(r);
        mode.setValue(FormMode.VIEW);
        feedName.setValue(StockTexts.nz(r.materialName));
        String unit = r.qty == null ? "千克" : StockQtyFormatter.unitText(r.qty);
        unitText.setValue(unit);
        StockActionsVO a = r.actions;
        pending.setValue(a != null && (StockEnums.EDIT_PENDING.equals(a.editState) || a.pendingRequestId != null));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"饲料", r.materialName});
        rows.add(new String[]{"出库数量", r.qty != null ? StockQtyFormatter.format(r.qty) : StockQtyFormatter.qty(r.inputQty, unit)});
        rows.add(new String[]{"出库去向", r.destinationText != null ? r.destinationText : destText(r.destination)});
        rows.add(new String[]{"说明", r.remark});
        rows.add(new String[]{"出库日期", DateFormatter.formatDate(r.outDate)});
        rows.add(new String[]{"按均价折合", r.amount == null ? null : StockCalc.money(r.amount) + " 元"});
        rows.add(new String[]{"登记人", StockTexts.join(StockTexts.DOT,
                r.createdBy == null ? null : r.createdBy.getName(),
                r.createdAt == null ? null : DateFormatter.formatDateTime(r.createdAt))});
        readonlyRows.setValue(rows);
    }

    @Nullable
    static String destText(@Nullable String code) {
        for (String[] d : DESTINATIONS) {
            if (d[0].equals(code)) {
                return d[1];
            }
        }
        return null;
    }

    // ===================== 选择与输入 =====================

    public void onFeedPicked(long feedId) {
        List<FeedOptionVO> list = feedOptions.getValue();
        if (list == null) {
            return;
        }
        for (FeedOptionVO o : list) {
            if (o.feedId == feedId && o.selectable) {
                selectedFeed.setValue(o);
                feedName.setValue(StockTexts.nz(o.name));
                String unit = o.stock == null ? "千克" : StockQtyFormatter.unitText(o.stock);
                unitText.setValue(unit);
                feedHint.setValue("当前结存 " + StockQtyFormatter.format(o.stock) + StockTexts.DOT
                        + StockTexts.avgPrice(unit, o.avgPrice));
                BigDecimal max = o.stock == null ? null : o.stock.value;
                maxQty.setValue(max);
                maxText.setValue("最多 " + StockQtyFormatter.qty(max, unit) + "，出库后库存不能为负数");
                clearFieldError(F_FEED);
            }
        }
        rebuildPicker();
        recomputeAmount();
    }

    public void onDestinationPicked(@Nullable String dest) {
        destination.setValue(dest);
        clearFieldError(F_DEST);
        clearFieldError(F_REMARK);
        remarkRequired.setValue(StockEnums.DEST_TRANSFER_FARM.equals(dest) || StockEnums.DEST_OTHER.equals(dest));
        destHint.setValue(destHintOf(dest));
    }

    /** Chip 行下方提示，随去向变化 */
    @Nullable
    static String destHintOf(@Nullable String dest) {
        if (StockEnums.DEST_RETURN_SUPPLIER.equals(dest) || StockEnums.DEST_SALE.equals(dest)) {
            return "系统不会自动记账，收到退款或货款时请在 E03 记一笔中登记";
        }
        if (StockEnums.DEST_TRANSFER_FARM.equals(dest)) {
            return "对方养殖场不会自动入库，需要对方自行登记入库";
        }
        if (StockEnums.DEST_OTHER.equals(dest)) {
            return "请在说明中写明出库原因";
        }
        return null;
    }

    public void onDatePicked(@Nullable String date) {
        outDate.setValue(date);
        String e = StockValidators.notAfterToday(date, "出库日期");
        if (e != null) {
            putFieldError(F_DATE, e);
        } else {
            clearFieldError(F_DATE);
        }
    }

    private void recomputeAmount() {
        BigDecimal price = null;
        FeedOptionVO f = selectedFeed.getValue();
        if (f != null) {
            price = f.avgPrice;
        } else if (record.getValue() != null) {
            price = record.getValue().unitPrice;
        }
        BigDecimal amount = StockCalc.amount(StockCalc.parse(inputQty.getValue()), price);
        amountText.setValue(StockCalc.money(amount));
    }

    private void rebuildPicker() {
        List<PickerItem> items = new ArrayList<>();
        List<FeedOptionVO> list = feedOptions.getValue();
        FeedOptionVO sel = selectedFeed.getValue();
        boolean any = false;
        if (list != null) {
            for (FeedOptionVO o : list) {
                any |= o.selectable;
                String unit = o.stock == null ? "千克" : StockQtyFormatter.unitText(o.stock);
                items.add(PickerItem.item(String.valueOf(o.feedId), StockTexts.nz(o.name))
                        .subtitle(o.selectable ? "结存 " + StockQtyFormatter.format(o.stock)
                                : o.disabledReason != null ? o.disabledReason : "结存 0 " + unit + "，不能出库")
                        .tag(o.lowStock ? "库存不足" : null, StatusTagView.WARNING)
                        .icon(com.zhimu.slmp.base.R.drawable.ic_inventory_2, o.lowStock ? ListRowView.TONE_WARNING
                                : ListRowView.TONE_SECONDARY)
                        .disabled(!o.selectable)
                        .selected(sel != null && sel.feedId == o.feedId));
            }
        }
        feedPicker.setValue(new PickerData(items, null, null,
                any ? null : "没有可以出库的饲料。先登记入库，再登记出库。"));
    }

    // ===================== 校验与提交 =====================

    @VisibleForTesting
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (FormMode.NEW.equals(mode.getValue()) && selectedFeed.getValue() == null) {
            e.put(F_FEED, "请选择饲料");
        }
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        BigDecimal max = maxQty.getValue();
        if (q == null || q.signum() <= 0) {
            e.put(F_QTY, StockValidators.MSG_GT_ZERO);
        } else if (StockCalc.scaleOf(q) > 2) {
            e.put(F_QTY, StockValidators.MSG_SCALE);
        } else if (max != null && q.compareTo(max) > 0) {
            e.put(F_QTY, "超过当前结存 " + StockQtyFormatter.qty(max, unitText.getValue()) + "，出库后库存不能为负数");
        }
        String dest = destination.getValue();
        if (dest == null) {
            e.put(F_DEST, "请选择出库去向");
        }
        String r = trim(remark.getValue());
        if (r.isEmpty() && StockEnums.DEST_TRANSFER_FARM.equals(dest)) {
            e.put(F_REMARK, "请写明调往哪个养殖场");
        } else if (r.isEmpty() && StockEnums.DEST_OTHER.equals(dest)) {
            e.put(F_REMARK, "请写明出库原因");
        } else if (r.length() > 100) {
            e.put(F_REMARK, "最多 100 个字");
        }
        String d = StockValidators.notAfterToday(outDate.getValue(), "出库日期");
        if (d != null) {
            e.put(F_DATE, d);
        }
        return e;
    }

    @VisibleForTesting
    @Nullable
    FeedOtherOutRequest buildRequest() {
        FeedOptionVO f = selectedFeed.getValue();
        if (f == null) {
            return null;
        }
        FeedOtherOutRequest r = new FeedOtherOutRequest();
        r.feedId = f.feedId;
        r.inputQty = StockCalc.parse(inputQty.getValue());
        r.inputUnit = StockEnums.INPUT_MEASURE;
        r.destination = destination.getValue();
        r.remark = emptyToNull(remark.getValue());
        r.outDate = outDate.getValue();
        r.stockVersion = f.stockVersion;
        return r;
    }

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> e = validate();
        setErrors(e);
        if (!e.isEmpty()) {
            return;
        }
        if (FormMode.EDIT.equals(mode.getValue())) {
            saveEdit();
            return;
        }
        FeedOtherOutRequest req = buildRequest();
        if (req == null) {
            return;
        }
        String qty = StockQtyFormatter.qty(req.inputQty, unitText.getValue());
        String name = feedName.getValue();
        submitGuarded(req, key -> repository.createFeedOtherOut(req, key),
                st -> repository.getOutbound(st.bizId == null ? 0 : st.bizId),
                vo -> {
                    onSubmitted(null, StockEnums.BIZ_OUTBOUND, vo.outboundId, farmContext.farmIdOrZero());
                    submitted.setValue("已登记其他出库 " + qty + StockTexts.nz(name));
                }, this::onError);
    }

    private void onError(@NonNull ApiException e) {
        switch (e.getCode()) {
            case ErrorCodes.STOCK_NOT_ENOUGH:
                putFieldError(F_QTY, e.getMessage());
                return;
            case ErrorCodes.VERSION_CONFLICT: {
                String cur = e.dataString("current_qty");
                if (cur != null) {
                    putFieldError(F_QTY, "库存已变化，当前结存 " + StockQtyFormatter.qty(StockCalc.parse(cur),
                            unitText.getValue()) + "，请刷新后重试");
                    if (FormMode.NEW.equals(mode.getValue())) {
                        launch(repository.listFeedOptions(StockEnums.PURPOSE_OTHER_OUT, null), list -> {
                            feedOptions.setValue(list);
                            FeedOptionVO f = selectedFeed.getValue();
                            if (f != null) {
                                onFeedPicked(f.feedId);
                            }
                        });
                    }
                } else {
                    showMessage(UiMessage.withAction(MSG_VERSION, "刷新", ACTION_REFRESH));
                }
                return;
            }
            case ErrorCodes.CHANGE_REQUEST_REQUIRED:
                notToday.setValue(e.getMessage());
                return;
            case ErrorCodes.STATE_CHANGED:
            case ErrorCodes.PENDING_EXISTS:
                showMessage(e.getMessage());
                reloadRecord();
                return;
            default:
                if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
                    setErrors(e.getFieldErrors());
                    return;
                }
                handleError(e);
        }
    }

    // ===================== 只读、修改与作废 =====================

    /** 菜单“修改”：饲料只读，上限为 max_qty（当前结存 + 本条原出库数量） */
    public void startEdit() {
        StockOutboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        inputQty.setValue(StockCalc.plain(r.inputQty != null ? r.inputQty : r.qty == null ? null : r.qty.value));
        destination.setValue(r.destination);
        onDestinationPicked(r.destination);
        remark.setValue(StockTexts.nz(r.remark));
        outDate.setValue(r.outDate);
        maxQty.setValue(r.maxQty);
        maxText.setValue("最多 " + StockQtyFormatter.qty(r.maxQty, unitText.getValue()));
        feedHint.setValue("饲料不能修改，选错时请作废后重新登记");
        fieldErrors.setValue(Collections.emptyMap());
        mode.setValue(FormMode.EDIT);
        recomputeAmount();
        markClean();
        checkDraft();
    }

    public boolean cancelEdit() {
        if (FormMode.EDIT.equals(mode.getValue())) {
            mode.setValue(FormMode.VIEW);
            return true;
        }
        return false;
    }

    private void saveEdit() {
        StockOutboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        OutboundUpdateRequest req = new OutboundUpdateRequest();
        req.inputQty = StockCalc.parse(inputQty.getValue());
        req.inputUnit = StockEnums.INPUT_MEASURE;
        req.destination = destination.getValue();
        req.remark = emptyToNull(remark.getValue());
        req.outDate = outDate.getValue();
        req.version = r.version;
        submitting.setValue(true);
        launch(repository.updateOutbound(r.outboundId, req), vo -> {
            submitting.setValue(false);
            deleteDraft();
            applyRecord(vo);
            edited.call();
            showMessage("已修改，修改记录已保留");
        }, e -> {
            submitting.setValue(false);
            onError(e);
        });
    }

    public void reloadRecord() {
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            launch(repository.getOutbound(recordId), this::applyRecord);
        }
    }

    /** “作废后保育料结存增加 200 千克。这条出库没有计入批次成本，作废不影响批次核算。” */
    public void onVoidMenu() {
        StockOutboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        String qty = r.qty != null ? StockQtyFormatter.format(r.qty) : StockQtyFormatter.qty(r.inputQty, unitText.getValue());
        confirmVoid.setValue("作废后" + StockTexts.nz(r.materialName) + "结存增加 " + qty
                + "。这条出库没有计入批次成本，作废不影响批次核算。");
    }

    public void voidRecord() {
        StockOutboundVO r = record.getValue();
        if (r == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        VoidRequest req = new VoidRequest();
        req.version = r.version;
        submitting.setValue(true);
        launch(repository.voidOutbound(r.outboundId, req), () -> {
            submitting.setValue(false);
            finished = true;
            voided.setValue("已作废这条出库记录");
        }, e -> {
            submitting.setValue(false);
            onError(e);
        });
    }

    public void onRequestChange() {
        StockOutboundVO r = record.getValue();
        if (r != null) {
            openChangeRequest.setValue(r.outboundId);
        }
    }

    public void onViewRequest() {
        StockOutboundVO r = record.getValue();
        openRequest.setValue(r == null || r.actions == null ? null : r.actions.pendingRequestId);
    }

    public void onChangeLog() {
        StockOutboundVO r = record.getValue();
        if (r != null) {
            openChangeLog.setValue(r.outboundId);
        }
    }

    // ===================== 草稿 =====================

    @Nullable
    @Override
    protected String pageCode() {
        return PAGE_CODE;
    }

    @Nullable
    @Override
    protected String draftBizKey() {
        String m = mode.getValue();
        if (FormMode.NEW.equals(m)) {
            return "new";
        }
        return FormMode.EDIT.equals(m) && recordId != null ? String.valueOf(recordId) : null;
    }

    @Override
    protected boolean isEditing() {
        String m = mode.getValue();
        return FormMode.NEW.equals(m) || FormMode.EDIT.equals(m);
    }

    @NonNull
    @Override
    protected Map<String, String> draftFields() {
        Map<String, String> m = new LinkedHashMap<>();
        FeedOptionVO f = selectedFeed.getValue();
        m.put(F_FEED, f == null ? null : String.valueOf(f.feedId));
        m.put(F_QTY, inputQty.getValue());
        m.put(F_DEST, destination.getValue());
        m.put(F_REMARK, remark.getValue());
        m.put(F_DATE, outDate.getValue());
        return m;
    }

    @Override
    protected void applyDraft(@NonNull Map<String, String> d) {
        Long feed = StockExtras.readId(d.get(F_FEED));
        if (feed != null && FormMode.NEW.equals(mode.getValue())) {
            onFeedPicked(feed);
        }
        inputQty.setValue(nz(d.get(F_QTY)));
        onDestinationPicked(d.get(F_DEST));
        remark.setValue(nz(d.get(F_REMARK)));
        if (d.get(F_DATE) != null) {
            outDate.setValue(d.get(F_DATE));
        }
    }

    // ===================== 对外 =====================

    public LiveData<String> getMode() {
        return mode;
    }

    public LiveData<FeedOptionVO> getSelectedFeed() {
        return selectedFeed;
    }

    public LiveData<String> getFeedName() {
        return feedName;
    }

    public LiveData<String> getFeedHint() {
        return feedHint;
    }

    public MutableLiveData<String> getInputQty() {
        return inputQty;
    }

    public MutableLiveData<String> getRemark() {
        return remark;
    }

    public LiveData<String> getOutDate() {
        return outDate;
    }

    public LiveData<String> getDestination() {
        return destination;
    }

    public LiveData<BigDecimal> getMaxQty() {
        return maxQty;
    }

    public LiveData<String> getMaxText() {
        return maxText;
    }

    public LiveData<String> getAmountText() {
        return amountText;
    }

    public LiveData<Boolean> getRemarkRequired() {
        return remarkRequired;
    }

    public LiveData<String> getDestHint() {
        return destHint;
    }

    public LiveData<String> getUnitText() {
        return unitText;
    }

    public LiveData<StockOutboundVO> getRecord() {
        return record;
    }

    public LiveData<List<String[]>> getReadonlyRows() {
        return readonlyRows;
    }

    public LiveData<Boolean> getPending() {
        return pending;
    }

    public LiveData<PickerData> getFeedPicker() {
        return feedPicker;
    }

    @Nullable
    public StockActionsVO actions() {
        StockOutboundVO r = record.getValue();
        return r == null ? null : r.actions;
    }

    public SingleLiveEvent<String> getSubmitted() {
        return submitted;
    }

    public SingleLiveEvent<String> getConfirmVoid() {
        return confirmVoid;
    }

    public SingleLiveEvent<String> getVoided() {
        return voided;
    }

    public SingleLiveEvent<String> getNotToday() {
        return notToday;
    }

    public SingleLiveEvent<Long> getOpenChangeRequest() {
        return openChangeRequest;
    }

    public SingleLiveEvent<Long> getOpenRequest() {
        return openRequest;
    }

    public SingleLiveEvent<Long> getOpenChangeLog() {
        return openChangeLog;
    }

    public SingleLiveEvent<Void> getEdited() {
        return edited;
    }
}
