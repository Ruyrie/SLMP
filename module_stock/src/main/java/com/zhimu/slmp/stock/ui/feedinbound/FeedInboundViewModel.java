package com.zhimu.slmp.stock.ui.feedinbound;

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
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.FeedInboundRequest;
import com.zhimu.slmp.stock.model.request.InboundUpdateRequest;
import com.zhimu.slmp.stock.model.request.VoidRequest;
import com.zhimu.slmp.stock.model.vo.FeedOptionVO;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockInboundVO;
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
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * M03 饲料入库（技术准备文档 2.4.4）：
 * <ul>
 *     <li>NEW：API-M03（INBOUND）下拉，feed_id 预选；数量 × 单价实时算入库金额；确认入库 API-M08（幂等键，超时查 API-M42）</li>
 *     <li>VIEW：API-M09 只读，菜单按 actions；EDIT：带出原值，API-M10（带 version）；40307 转 U01</li>
 *     <li>作废 API-M11（只带 version）；最近供应商 API-M12（300 ms）；草稿 page_code M03</li>
 * </ul>
 */
public class FeedInboundViewModel extends StockFormViewModel {

    public static final String PAGE_CODE = "M03";
    public static final String F_FEED = "feed_id";
    public static final String F_QTY = "input_qty";
    public static final String F_PRICE = "unit_price";
    public static final String F_PRODUCTION = "production_date";
    public static final String F_SHELF = "shelf_life_date";
    public static final String F_SUPPLIER = "supplier_name";
    public static final String F_DATE = "inbound_date";
    public static final String F_SYNC = "sync_finance";
    public static final String PICKER_FEED = "stock_m03_feed";

    private final FarmContext farmContext;

    private final MutableLiveData<String> mode = new MutableLiveData<>(FormMode.NEW);
    private final MutableLiveData<List<FeedOptionVO>> feedOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<FeedOptionVO> selectedFeed = new MutableLiveData<>();
    private final MutableLiveData<String> inputQty = new MutableLiveData<>("");
    private final MutableLiveData<String> unitPrice = new MutableLiveData<>("");
    private final MutableLiveData<String> supplierName = new MutableLiveData<>("");
    private final MutableLiveData<String> productionDate = new MutableLiveData<>();
    private final MutableLiveData<String> shelfLifeDate = new MutableLiveData<>();
    private final MutableLiveData<String> inboundDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<Boolean> syncFinance = new MutableLiveData<>(true);
    private final MediatorLiveData<String> amountText = new MediatorLiveData<>();
    private final MutableLiveData<String> syncText = new MutableLiveData<>("");
    private final MutableLiveData<String> feedHint = new MutableLiveData<>();
    private final MutableLiveData<String> feedName = new MutableLiveData<>("");
    private final MutableLiveData<String> unitText = new MutableLiveData<>("千克");
    private final MutableLiveData<List<String>> supplierSuggest = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<StockInboundVO> record = new MutableLiveData<>();
    private final MutableLiveData<List<String[]>> readonlyRows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PickerData> feedPicker = new MutableLiveData<>();

    private final SingleLiveEvent<UiMessage> submitted = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> confirmVoid = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> voided = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> needRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();

    private final PublishSubject<String> supplierQuery = PublishSubject.create();

    @Nullable
    private Long presetFeedId;
    @Nullable
    private Long recordId;
    @Nullable
    private Long todoId;

    @Inject
    public FeedInboundViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                                @NonNull FarmContext farmContext) {
        super(repository, drafts);
        this.farmContext = farmContext;
        amountText.setValue("—");
        amountText.addSource(inputQty, v -> onFieldChanged(F_QTY));
        amountText.addSource(unitPrice, v -> onFieldChanged(F_PRICE));
        amountText.addSource(supplierName, v -> clearFieldError(F_SUPPLIER));
        amountText.addSource(selectedFeed, v -> onFeedChanged());
        disposables.add(supplierQuery
                .debounce(debounceMs, TimeUnit.MILLISECONDS, Schedulers.computation())
                .switchMapSingle(k -> repository.recentPartners(StockEnums.PARTNER_SUPPLIER, k.isEmpty() ? null : k))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(supplierSuggest::setValue, t -> {
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        todoId = StockExtras.readId(handle.get(RouterPath.EXTRA_TODO_ID));
        init(StockExtras.readId(handle.get(RouterPath.EXTRA_FEED_ID)),
                StockExtras.readId(handle.get(RouterPath.EXTRA_RECORD_ID)));
    }

    /** feedId 预选饲料；recordId 有值为只读 */
    public void init(@Nullable Long feedId, @Nullable Long recordId) {
        this.presetFeedId = feedId;
        this.recordId = recordId;
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            load(repository.getInbound(recordId), this::applyRecord);
        } else {
            mode.setValue(FormMode.NEW);
            load(repository.listFeedOptions(StockEnums.PURPOSE_INBOUND, null), list -> {
                applyOptions(list);
                markClean();
                checkDraft();
            });
        }
    }

    @Override
    public void retry() {
        init(presetFeedId, recordId);
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    /** 从 M02 新增饲料返回后重取下拉（保留已填内容） */
    public void reloadOptions() {
        launch(repository.listFeedOptions(StockEnums.PURPOSE_INBOUND, null), this::applyOptions);
    }

    private void applyOptions(@NonNull List<FeedOptionVO> list) {
        feedOptions.setValue(list);
        FeedOptionVO current = selectedFeed.getValue();
        Long want = current != null ? Long.valueOf(current.feedId) : presetFeedId;
        if (want != null) {
            for (FeedOptionVO o : list) {
                if (o.feedId == want) {
                    selectedFeed.setValue(o);
                }
            }
        }
        rebuildPicker();
    }

    private void applyRecord(@NonNull StockInboundVO r) {
        record.setValue(r);
        mode.setValue(FormMode.VIEW);
        feedName.setValue(StockTexts.nz(r.materialName));
        unitText.setValue(r.qty == null ? "千克" : StockQtyFormatter.unitText(r.qty));
        List<String[]> rows = new ArrayList<>();
        String unit = unitText.getValue();
        rows.add(new String[]{"饲料", r.materialName});
        rows.add(new String[]{"数量", r.qty != null ? StockQtyFormatter.format(r.qty) : StockQtyFormatter.qty(r.inputQty, unit)});
        rows.add(new String[]{"单价", r.unitPrice == null ? null : StockCalc.money(r.unitPrice) + " 元/" + unit});
        rows.add(new String[]{"入库金额", r.amount == null ? null : StockCalc.money(r.amount) + " 元"});
        rows.add(new String[]{"生产日期", r.productionDate});
        rows.add(new String[]{"保质期至", r.shelfLifeDate});
        rows.add(new String[]{"供应商", r.supplierName});
        rows.add(new String[]{"入库日期", DateFormatter.formatDate(r.inboundDate)});
        rows.add(new String[]{"登记人", StockTexts.join(StockTexts.DOT,
                r.createdBy == null ? null : r.createdBy.getName(),
                r.createdAt == null ? null : DateFormatter.formatDateTime(r.createdAt))});
        readonlyRows.setValue(rows);
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
            }
        }
        rebuildPicker();
    }

    private void onFeedChanged() {
        FeedOptionVO f = selectedFeed.getValue();
        clearFieldError(F_FEED);
        if (f == null) {
            feedHint.setValue(null);
            return;
        }
        feedName.setValue(StockTexts.nz(f.name));
        unitText.setValue(f.stock == null ? "千克" : StockQtyFormatter.unitText(f.stock));
        String remain = f.remainingDays == null ? "暂无消耗" : "剩余可用 " + f.remainingDays + " 天";
        feedHint.setValue("当前结存 " + StockQtyFormatter.format(f.stock) + "，" + remain);
    }

    private void onFieldChanged(@NonNull String field) {
        clearFieldError(field);
        BigDecimal amount = StockCalc.amount(StockCalc.parse(inputQty.getValue()), StockCalc.parse(unitPrice.getValue()));
        amountText.setValue(StockCalc.money(amount));
        syncText.setValue("确认后在收支流水中生成一条支出：饲料采购 " + (amount == null ? "—"
                : NumberFormatter.formatAmountSummary(amount)) + "，来源标为“饲料入库单”。入库单作废时，这条支出同步作废。");
    }

    /** 日期选择器回填后立即校验日期 */
    public void onDatePicked(@NonNull String field, @Nullable String date) {
        switch (field) {
            case F_PRODUCTION:
                productionDate.setValue(date);
                break;
            case F_SHELF:
                shelfLifeDate.setValue(date);
                break;
            default:
                inboundDate.setValue(date);
                break;
        }
        Map<String, String> all = validate();
        for (String f : new String[]{F_PRODUCTION, F_SHELF, F_DATE}) {
            String msg = all.get(f);
            if (msg != null) {
                putFieldError(f, msg);
            } else {
                clearFieldError(f);
            }
        }
    }

    public void toggleSync() {
        syncFinance.setValue(!Boolean.TRUE.equals(syncFinance.getValue()));
    }

    /** 供应商框获得焦点或输入：取最近 5 个（300 ms 防抖） */
    public void querySuppliers(@Nullable String keyword) {
        supplierQuery.onNext(keyword == null ? "" : keyword.trim());
    }

    private void rebuildPicker() {
        List<PickerItem> items = new ArrayList<>();
        List<FeedOptionVO> list = feedOptions.getValue();
        FeedOptionVO sel = selectedFeed.getValue();
        if (list != null) {
            for (FeedOptionVO o : list) {
                items.add(PickerItem.item(String.valueOf(o.feedId), StockTexts.nz(o.name))
                        .subtitle(o.selectable ? "结存 " + StockQtyFormatter.format(o.stock)
                                : StockTexts.nz(o.disabledReason))
                        .tag(o.lowStock ? "库存不足" : null, com.zhimu.slmp.base.widget.StatusTagView.WARNING)
                        .icon(com.zhimu.slmp.base.R.drawable.ic_inventory_2, o.lowStock
                                ? com.zhimu.slmp.base.widget.ListRowView.TONE_WARNING
                                : com.zhimu.slmp.base.widget.ListRowView.TONE_SECONDARY)
                        .disabled(!o.selectable)
                        .selected(sel != null && sel.feedId == o.feedId));
            }
        }
        feedPicker.setValue(PickerData.of(items, "还没有饲料档案。先新增一种饲料，再登记入库。"));
    }

    // ===================== 校验与提交 =====================

    @VisibleForTesting
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (FormMode.NEW.equals(mode.getValue()) && selectedFeed.getValue() == null) {
            e.put(F_FEED, "请选择饲料");
        }
        String q = StockValidators.positive(inputQty.getValue(), StockValidators.MAX_QTY);
        if (q != null) {
            e.put(F_QTY, q);
        }
        String p = StockValidators.positive(unitPrice.getValue(), StockValidators.MAX_PRICE);
        if (p != null) {
            e.put(F_PRICE, p);
        }
        String in = inboundDate.getValue();
        String prod = productionDate.getValue();
        String shelf = shelfLifeDate.getValue();
        if (prod != null && StockValidators.isAfter(prod, in)) {
            e.put(F_PRODUCTION, "生产日期不能晚于入库日期");
        }
        if (shelf != null) {
            if (prod != null && !StockValidators.isAfter(shelf, prod)) {
                e.put(F_SHELF, "必须晚于生产日期");
            } else if (StockValidators.isBefore(shelf, in)) {
                e.put(F_SHELF, "已过保质期，不能入库");
            }
        }
        String s = StockValidators.text(supplierName.getValue(), 30, "请填写供应商");
        if (s != null) {
            e.put(F_SUPPLIER, s);
        }
        String d = StockValidators.notAfterToday(in, "入库日期");
        if (d != null) {
            e.put(F_DATE, d);
        }
        return e;
    }

    @VisibleForTesting
    @Nullable
    FeedInboundRequest buildRequest() {
        FeedOptionVO f = selectedFeed.getValue();
        if (f == null) {
            return null;
        }
        FeedInboundRequest r = new FeedInboundRequest();
        r.feedId = f.feedId;
        r.inputQty = StockCalc.parse(inputQty.getValue());
        r.inputUnit = StockEnums.INPUT_MEASURE;
        r.unitPrice = StockCalc.parse(unitPrice.getValue());
        r.productionDate = productionDate.getValue();
        r.shelfLifeDate = shelfLifeDate.getValue();
        r.supplierName = trim(supplierName.getValue());
        r.inboundDate = inboundDate.getValue();
        r.syncFinance = Boolean.TRUE.equals(syncFinance.getValue());
        return r;
    }

    /** 主按钮：新增“确认入库”/ 修改“保存修改” */
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
        FeedInboundRequest req = buildRequest();
        if (req == null) {
            return;
        }
        submitGuarded(req, key -> repository.createFeedInbound(req, key),
                st -> repository.getInbound(st.bizId == null ? 0 : st.bizId),
                vo -> onCreated(vo, req), this::onBizError);
    }

    private void onCreated(@NonNull StockInboundVO vo, @NonNull FeedInboundRequest req) {
        onSubmitted(todoId, StockEnums.BIZ_INBOUND, vo.inboundId, farmContext.farmIdOrZero());
        submitted.setValue(successMessage(req.inputQty, unitText.getValue(), feedName.getValue(),
                Boolean.TRUE.equals(req.syncFinance)));
    }

    /** “已入库 1,000 千克育肥全价料，已同步记一笔支出”+“查看”（E04） */
    @NonNull
    public static UiMessage successMessage(@Nullable BigDecimal qty, @Nullable String unit, @Nullable String name,
                                    boolean sync) {
        String text = "已入库 " + StockQtyFormatter.qty(qty, unit) + StockTexts.nz(name);
        if (sync) {
            return UiMessage.withPage(text + "，已同步记一笔支出", "查看", RouterPath.E04);
        }
        return UiMessage.of(text);
    }

    private void onBizError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
            setErrors(e.getFieldErrors());
            return;
        }
        handleError(e);
    }

    // ===================== 只读、修改与作废 =====================

    /** 菜单“修改”：带出原值进入 EDIT */
    public void startEdit() {
        StockInboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        inputQty.setValue(StockCalc.plain(r.inputQty != null ? r.inputQty : r.qty == null ? null : r.qty.value));
        unitPrice.setValue(StockCalc.plain(r.unitPrice));
        productionDate.setValue(r.productionDate);
        shelfLifeDate.setValue(r.shelfLifeDate);
        supplierName.setValue(StockTexts.nz(r.supplierName));
        inboundDate.setValue(r.inboundDate);
        syncFinance.setValue(r.syncFinance);
        fieldErrors.setValue(Collections.emptyMap());
        mode.setValue(FormMode.EDIT);
        markClean();
        checkDraft();
    }

    /** 修改状态返回：回到只读 */
    public boolean cancelEdit() {
        if (FormMode.EDIT.equals(mode.getValue())) {
            mode.setValue(FormMode.VIEW);
            return true;
        }
        return false;
    }

    private void saveEdit() {
        StockInboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        InboundUpdateRequest req = new InboundUpdateRequest();
        req.inputQty = StockCalc.parse(inputQty.getValue());
        req.inputUnit = StockEnums.INPUT_MEASURE;
        req.unitPrice = StockCalc.parse(unitPrice.getValue());
        req.inboundDate = inboundDate.getValue();
        req.syncFinance = Boolean.TRUE.equals(syncFinance.getValue());
        req.productionDate = productionDate.getValue();
        req.shelfLifeDate = shelfLifeDate.getValue();
        req.supplierName = trim(supplierName.getValue());
        req.version = r.version;
        submitting.setValue(true);
        launch(repository.updateInbound(r.inboundId, req), vo -> {
            submitting.setValue(false);
            deleteDraft();
            applyRecord(vo);
            showMessage("已保存入库单修改");
        }, this::onEditError);
    }

    private void onEditError(@NonNull ApiException e) {
        submitting.setValue(false);
        switch (e.getCode()) {
            case ErrorCodes.CHANGE_REQUEST_REQUIRED:
                needRequest.setValue(e.getMessage());
                return;
            case ErrorCodes.STATE_CHANGED:
            case ErrorCodes.PENDING_EXISTS:
                showMessage(e.getMessage());
                reloadRecord();
                return;
            case ErrorCodes.VERSION_CONFLICT:
                showMessage(UiMessage.withAction(MSG_VERSION, "刷新", ACTION_REFRESH));
                return;
            default:
                onBizError(e);
        }
    }

    /** 重取只读状态与 actions */
    public void reloadRecord() {
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            launch(repository.getInbound(recordId), this::applyRecord);
        }
    }

    /** 菜单“作废”：对话框正文写明影响 */
    public void onVoidMenu() {
        StockInboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        String qty = r.qty != null ? StockQtyFormatter.format(r.qty) : StockQtyFormatter.qty(r.inputQty, unitText.getValue());
        String text = "作废后结存减少 " + qty;
        text += r.syncFinance ? "，同步记账生成的饲料采购 " + NumberFormatter.formatAmountSummary(r.amount) + "同时作废。" : "。";
        confirmVoid.setValue(text);
    }

    public void voidRecord() {
        StockInboundVO r = record.getValue();
        if (r == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        VoidRequest req = new VoidRequest();
        req.version = r.version;
        submitting.setValue(true);
        launch(repository.voidInbound(r.inboundId, req), () -> {
            submitting.setValue(false);
            finished = true;
            voided.setValue(r.syncFinance ? "已作废入库单，同步记账生成的饲料采购 "
                    + NumberFormatter.formatAmountSummary(r.amount) + "同时作废" : "已作废入库单");
        }, e -> {
            submitting.setValue(false);
            onEditError(e);
        });
    }

    public void onRequestChange() {
        StockInboundVO r = record.getValue();
        if (r != null) {
            openChangeRequest.setValue(r.inboundId);
        }
    }

    public void onViewRequest() {
        StockInboundVO r = record.getValue();
        openRequest.setValue(r == null || r.actions == null ? null : r.actions.pendingRequestId);
    }

    public void onChangeLog() {
        StockInboundVO r = record.getValue();
        if (r != null) {
            openChangeLog.setValue(r.inboundId);
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
        m.put(F_PRICE, unitPrice.getValue());
        m.put(F_PRODUCTION, productionDate.getValue());
        m.put(F_SHELF, shelfLifeDate.getValue());
        m.put(F_SUPPLIER, supplierName.getValue());
        m.put(F_DATE, inboundDate.getValue());
        m.put(F_SYNC, String.valueOf(Boolean.TRUE.equals(syncFinance.getValue())));
        return m;
    }

    @Override
    protected void applyDraft(@NonNull Map<String, String> d) {
        Long feed = StockExtras.readId(d.get(F_FEED));
        if (feed != null && FormMode.NEW.equals(mode.getValue())) {
            onFeedPicked(feed);
        }
        inputQty.setValue(nz(d.get(F_QTY)));
        unitPrice.setValue(nz(d.get(F_PRICE)));
        productionDate.setValue(d.get(F_PRODUCTION));
        shelfLifeDate.setValue(d.get(F_SHELF));
        supplierName.setValue(nz(d.get(F_SUPPLIER)));
        if (d.get(F_DATE) != null) {
            inboundDate.setValue(d.get(F_DATE));
        }
        syncFinance.setValue(!"false".equals(d.get(F_SYNC)));
    }

    // ===================== 对外 =====================

    public LiveData<String> getMode() {
        return mode;
    }

    public LiveData<List<FeedOptionVO>> getFeedOptions() {
        return feedOptions;
    }

    public LiveData<FeedOptionVO> getSelectedFeed() {
        return selectedFeed;
    }

    public MutableLiveData<String> getInputQty() {
        return inputQty;
    }

    public MutableLiveData<String> getUnitPrice() {
        return unitPrice;
    }

    public MutableLiveData<String> getSupplierName() {
        return supplierName;
    }

    public LiveData<String> getProductionDate() {
        return productionDate;
    }

    public LiveData<String> getShelfLifeDate() {
        return shelfLifeDate;
    }

    public LiveData<String> getInboundDate() {
        return inboundDate;
    }

    public LiveData<Boolean> getSyncFinance() {
        return syncFinance;
    }

    public LiveData<String> getAmountText() {
        return amountText;
    }

    public LiveData<String> getSyncText() {
        return syncText;
    }

    public LiveData<String> getFeedHint() {
        return feedHint;
    }

    public LiveData<String> getFeedName() {
        return feedName;
    }

    public LiveData<String> getUnitText() {
        return unitText;
    }

    public LiveData<List<String>> getSupplierSuggest() {
        return supplierSuggest;
    }

    public LiveData<StockInboundVO> getRecord() {
        return record;
    }

    public LiveData<List<String[]>> getReadonlyRows() {
        return readonlyRows;
    }

    public LiveData<PickerData> getFeedPicker() {
        return feedPicker;
    }

    @Nullable
    public StockActionsVO actions() {
        StockInboundVO r = record.getValue();
        return r == null ? null : r.actions;
    }

    public SingleLiveEvent<UiMessage> getSubmitted() {
        return submitted;
    }

    public SingleLiveEvent<String> getConfirmVoid() {
        return confirmVoid;
    }

    public SingleLiveEvent<String> getVoided() {
        return voided;
    }

    public SingleLiveEvent<String> getNeedRequest() {
        return needRequest;
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
}
