package com.zhimu.slmp.stock.ui.druginbound;

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
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.DrugInboundRequest;
import com.zhimu.slmp.stock.model.request.InboundUpdateRequest;
import com.zhimu.slmp.stock.model.request.VoidRequest;
import com.zhimu.slmp.stock.model.vo.DrugOptionListResult;
import com.zhimu.slmp.stock.model.vo.DrugOptionVO;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockInboundVO;
import com.zhimu.slmp.stock.model.vo.StockSettingVO;
import com.zhimu.slmp.stock.ui.common.FormMode;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerItem;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockValidators;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * M08 兽药与疫苗入库（技术准备文档 2.4.10）：
 * <ul>
 *     <li>NEW：API-M35（INBOUND，300 ms 搜索）药品面板，命中禁用药时顶部 error 行；面板“新建药品档案”进入 M09，返回 drug_id 后重取并选中</li>
 *     <li>生产批号、入库日期、有效期至、数量（PACK）、单价、同步记账；有效期在临期范围内时 warning 辅助文字（API-M40 按类别）</li>
 *     <li>确认入库 API-M30（幂等键）；42203 写药品框、42204 写批号框</li>
 *     <li>VIEW / EDIT 同 M03（API-M09 / M10 / M11），批号不可改；从 M06“修改入库信息”带 mode = EDIT 直接进入修改</li>
 * </ul>
 */
public class DrugInboundViewModel extends StockFormViewModel {

    public static final String PAGE_CODE = "M08";
    public static final String F_DRUG = "drug_id";
    public static final String F_LOT = "lot_no";
    public static final String F_DATE = "inbound_date";
    public static final String F_EXPIRE = "expire_date";
    public static final String F_QTY = "input_qty";
    public static final String F_PRICE = "unit_price";
    public static final String F_SYNC = "sync_finance";
    public static final String PICKER_DRUG = "stock_m08_drug";
    private static final Pattern LOT_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,30}$");

    private final FarmContext farmContext;

    private final MutableLiveData<String> mode = new MutableLiveData<>(FormMode.NEW);
    private final MutableLiveData<List<DrugOptionVO>> drugOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> bannedHint = new MutableLiveData<>();
    private final MutableLiveData<DrugOptionVO> selectedDrug = new MutableLiveData<>();
    private final MutableLiveData<String> drugName = new MutableLiveData<>("");
    private final MutableLiveData<String> categoryText = new MutableLiveData<>();
    private final MutableLiveData<String> lotNo = new MutableLiveData<>("");
    private final MutableLiveData<String> inputQty = new MutableLiveData<>("");
    private final MutableLiveData<String> unitPrice = new MutableLiveData<>("");
    private final MutableLiveData<String> expireDate = new MutableLiveData<>();
    private final MutableLiveData<String> inboundDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<Boolean> syncFinance = new MutableLiveData<>(true);
    private final MutableLiveData<StockSettingVO> setting = new MutableLiveData<>();
    private final MutableLiveData<String> expireHint = new MutableLiveData<>();
    private final MediatorLiveData<String> amountText = new MediatorLiveData<>();
    private final MutableLiveData<String> syncText = new MutableLiveData<>("");
    private final MutableLiveData<String> packUnit = new MutableLiveData<>("");
    private final MutableLiveData<StockInboundVO> record = new MutableLiveData<>();
    private final MutableLiveData<List<String[]>> readonlyRows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<PickerData> drugPicker = new MutableLiveData<>();

    private final SingleLiveEvent<String> openArchiveNew = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> submitted = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> confirmVoid = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> voided = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> needRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();

    @Nullable
    private Long presetDrugId;
    @Nullable
    private Long recordId;
    @Nullable
    private Long todoId;
    private boolean startInEdit;
    @NonNull
    private String keyword = "";

    @Inject
    public DrugInboundViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                                @NonNull FarmContext farmContext) {
        super(repository, drafts);
        this.farmContext = farmContext;
        amountText.setValue("—");
        amountText.addSource(inputQty, v -> onAmountChanged(F_QTY));
        amountText.addSource(unitPrice, v -> onAmountChanged(F_PRICE));
        amountText.addSource(lotNo, v -> clearFieldError(F_LOT));
        amountText.addSource(selectedDrug, v -> onDrugChanged());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        todoId = StockExtras.readId(handle.get(RouterPath.EXTRA_TODO_ID));
        startInEdit = FormMode.EDIT.equals(handle.get(RouterPath.EXTRA_MODE));
        init(StockExtras.readId(handle.get(RouterPath.EXTRA_DRUG_ID)),
                StockExtras.readId(handle.get(RouterPath.EXTRA_RECORD_ID)));
    }

    public void init(@Nullable Long drugId, @Nullable Long recordId) {
        presetDrugId = drugId;
        this.recordId = recordId;
        Single<StockSettingVO> st = repository.getStockSetting().onErrorReturnItem(new StockSettingVO());
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            load(Single.zip(repository.getInbound(recordId), st, (r, s) -> {
                setting.postValue(s);
                return r;
            }), r -> {
                applyRecord(r);
                if (startInEdit) {
                    startInEdit = false;
                    startEdit();
                }
            });
        } else {
            mode.setValue(FormMode.NEW);
            load(Single.zip(repository.listDrugOptions(StockEnums.PURPOSE_INBOUND, null), st, (o, s) -> {
                setting.postValue(s);
                return o;
            }), o -> {
                applyOptions(o);
                markClean();
                checkDraft();
            });
        }
    }

    @Override
    public void retry() {
        init(presetDrugId, recordId);
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    private void applyOptions(@NonNull DrugOptionListResult r) {
        List<DrugOptionVO> list = r.list == null ? new ArrayList<>() : r.list;
        drugOptions.setValue(list);
        bannedHint.setValue(r.bannedHint);
        Long want = presetDrugId;
        if (want != null) {
            for (DrugOptionVO o : list) {
                if (o.drugId == want) {
                    selectedDrug.setValue(o);
                    presetDrugId = null;
                }
            }
        }
        rebuildPicker();
    }

    private void applyRecord(@NonNull StockInboundVO r) {
        record.setValue(r);
        mode.setValue(FormMode.VIEW);
        drugName.setValue(StockTexts.nz(r.materialName));
        categoryText.setValue(r.categoryText != null ? r.categoryText : StockEnums.categoryText(r.category));
        String unit = r.qty != null && r.qty.packUnit != null ? r.qty.packUnit : "";
        packUnit.setValue(unit);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"药品", r.materialName});
        rows.add(new String[]{"类型", categoryText.getValue()});
        rows.add(new String[]{"生产批号", r.lotNo});
        rows.add(new String[]{"数量", r.qty != null ? StockQtyFormatter.format(r.qty)
                : StockQtyFormatter.qty(r.inputQty, unit)});
        rows.add(new String[]{"单价", r.unitPrice == null ? null : StockCalc.money(r.unitPrice) + " 元/" + unit});
        rows.add(new String[]{"入库金额", r.amount == null ? null : StockCalc.money(r.amount) + " 元"});
        rows.add(new String[]{"入库日期", DateFormatter.formatDate(r.inboundDate)});
        rows.add(new String[]{"有效期至", r.expireDate});
        rows.add(new String[]{"登记人", StockTexts.join(StockTexts.DOT,
                r.createdBy == null ? null : r.createdBy.getName(),
                r.createdAt == null ? null : DateFormatter.formatDateTime(r.createdAt))});
        readonlyRows.setValue(rows);
    }

    // ===================== 药品面板 =====================

    /** 面板搜索（已防抖 300 ms） */
    public void searchDrugs(@NonNull String kw) {
        keyword = kw;
        launch(repository.listDrugOptions(StockEnums.PURPOSE_INBOUND, kw.isEmpty() ? null : kw), this::applyOptions);
    }

    public void onDrugPicked(long drugId) {
        List<DrugOptionVO> list = drugOptions.getValue();
        if (list != null) {
            for (DrugOptionVO o : list) {
                if (o.drugId == drugId) {
                    selectedDrug.setValue(o);
                }
            }
        }
        rebuildPicker();
    }

    /** 面板“新建药品档案”：带搜索词进入 M09 */
    public void onArchiveNew() {
        openArchiveNew.setValue(keyword);
    }

    /** M09 保存后返回 drug_id：重取下拉并选中，已填内容不变 */
    public void onArchiveCreated(long drugId) {
        presetDrugId = drugId;
        keyword = "";
        launch(repository.listDrugOptions(StockEnums.PURPOSE_INBOUND, null), r -> {
            applyOptions(r);
            if (presetDrugId != null) {
                // 下拉中还没有新档案（服务端列表延迟）：按档案详情补一项并选中
                launch(repository.getDrug(drugId), d -> {
                    DrugOptionVO o = new DrugOptionVO();
                    o.drugId = d.drugId;
                    o.name = d.name;
                    o.category = d.category;
                    o.spec = d.spec;
                    o.unit = d.unit;
                    o.packUnit = d.packUnit;
                    o.packSize = d.packSize;
                    o.usable = d.usable;
                    List<DrugOptionVO> list = new ArrayList<>(drugOptions.getValue() == null
                            ? Collections.emptyList() : drugOptions.getValue());
                    list.add(o);
                    drugOptions.setValue(list);
                    presetDrugId = null;
                    selectedDrug.setValue(o);
                    rebuildPicker();
                }, e -> presetDrugId = null);
            }
        });
    }

    private void rebuildPicker() {
        List<PickerItem> items = new ArrayList<>();
        DrugOptionVO sel = selectedDrug.getValue();
        List<DrugOptionVO> list = drugOptions.getValue();
        if (list != null) {
            for (DrugOptionVO o : list) {
                items.add(PickerItem.item(String.valueOf(o.drugId), StockTexts.nz(o.name))
                        .subtitle(o.spec)
                        .icon(StockTexts.categoryIcon(o.category), com.zhimu.slmp.base.widget.ListRowView.TONE_SECONDARY)
                        .tag(StockEnums.categoryText(o.category), StatusTagView.NEUTRAL)
                        .selected(sel != null && sel.drugId == o.drugId));
            }
        }
        String empty = keyword.isEmpty() ? "还没有药品档案。新增后才能登记入库。" : "没有找到“" + keyword + "”相关的药品";
        drugPicker.setValue(new PickerData(items, bannedHint.getValue(), empty, null));
    }

    private void onDrugChanged() {
        clearFieldError(F_DRUG);
        DrugOptionVO d = selectedDrug.getValue();
        if (d == null) {
            return;
        }
        drugName.setValue(StockTexts.nz(d.name));
        categoryText.setValue(StockEnums.categoryText(d.category));
        packUnit.setValue(d.packUnit != null ? d.packUnit : StockEnums.unitText(d.unit));
        updateExpireHint();
    }

    private void onAmountChanged(@NonNull String field) {
        clearFieldError(field);
        BigDecimal amount = StockCalc.amount(StockCalc.parse(inputQty.getValue()), StockCalc.parse(unitPrice.getValue()));
        amountText.setValue(StockCalc.money(amount));
        syncText.setValue("确认后在收支流水中生成一条支出：兽药疫苗 " + (amount == null ? "—"
                : NumberFormatter.formatAmountSummary(amount)) + "，来源标为“兽药疫苗入库单”。入库单作废时，这条支出同步作废。");
    }

    public void onDatePicked(@NonNull String field, @Nullable String date) {
        if (F_EXPIRE.equals(field)) {
            expireDate.setValue(date);
        } else {
            inboundDate.setValue(date);
        }
        Map<String, String> all = validate();
        for (String f : new String[]{F_DATE, F_EXPIRE}) {
            String msg = all.get(f);
            if (msg != null) {
                putFieldError(f, msg);
            } else {
                clearFieldError(f);
            }
        }
        updateExpireHint();
    }

    /** 有效期至 − 今天 ≤ 临期天数：“有效期剩 20 天，入库后即为临期”（不拦截） */
    private void updateExpireHint() {
        LocalDate exp = DateFormatter.parseDate(expireDate.getValue());
        if (exp == null) {
            expireHint.setValue(null);
            return;
        }
        long days = ChronoUnit.DAYS.between(DateFormatter.today(), exp);
        int near = nearDaysOf(categoryOf());
        expireHint.setValue(days >= 0 && days <= near ? "有效期剩 " + days + " 天，入库后即为临期" : null);
    }

    @Nullable
    private String categoryOf() {
        DrugOptionVO d = selectedDrug.getValue();
        if (d != null) {
            return d.category;
        }
        StockInboundVO r = record.getValue();
        return r == null ? null : r.category;
    }

    private int nearDaysOf(@Nullable String category) {
        StockSettingVO s = setting.getValue();
        if (s == null || s.version == 0 && s.nearDaysVet == 0) {
            return 30;
        }
        if (StockEnums.CAT_VACCINE.equals(category)) {
            return s.nearDaysVaccine;
        }
        if (StockEnums.CAT_DISINFECTANT.equals(category)) {
            return s.nearDaysDisinfectant;
        }
        return s.nearDaysVet;
    }

    public void toggleSync() {
        syncFinance.setValue(!Boolean.TRUE.equals(syncFinance.getValue()));
    }

    // ===================== 校验与提交 =====================

    @VisibleForTesting
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        boolean isNew = FormMode.NEW.equals(mode.getValue());
        if (isNew && selectedDrug.getValue() == null) {
            e.put(F_DRUG, "请选择药品");
        }
        if (isNew) {
            String lot = trim(lotNo.getValue());
            if (lot.isEmpty()) {
                e.put(F_LOT, "请填写生产批号");
            } else if (!LOT_PATTERN.matcher(lot).matches()) {
                e.put(F_LOT, "只能输入字母、数字和短横线，最多 30 个字符");
            }
        }
        String in = inboundDate.getValue();
        String d = StockValidators.notAfterToday(in, "入库日期");
        if (d != null) {
            e.put(F_DATE, d);
        }
        String exp = expireDate.getValue();
        if (exp == null) {
            e.put(F_EXPIRE, "请选择有效期");
        } else if (StockValidators.isBefore(exp, in)) {
            e.put(F_EXPIRE, "早于入库日期，不能入库");
        }
        String q = StockValidators.positive(inputQty.getValue(), StockValidators.MAX_QTY);
        if (q != null) {
            e.put(F_QTY, q);
        } else if (StockEnums.isVaccine(categoryOf())) {
            BigDecimal packs = StockCalc.parse(inputQty.getValue());
            BigDecimal size = packSize();
            String dose = packs == null ? null : StockValidators.integerDose(StockCalc.toMeasure(packs, size));
            if (dose != null) {
                e.put(F_QTY, dose);
            }
        }
        String p = StockValidators.positive(unitPrice.getValue(), StockValidators.MAX_PRICE);
        if (p != null) {
            e.put(F_PRICE, p);
        }
        return e;
    }

    @Nullable
    private BigDecimal packSize() {
        DrugOptionVO d = selectedDrug.getValue();
        if (d != null) {
            return d.packSize;
        }
        StockInboundVO r = record.getValue();
        return r == null || r.qty == null ? null : r.qty.packSize;
    }

    @VisibleForTesting
    @Nullable
    DrugInboundRequest buildRequest() {
        DrugOptionVO d = selectedDrug.getValue();
        if (d == null) {
            return null;
        }
        DrugInboundRequest r = new DrugInboundRequest();
        r.drugId = d.drugId;
        r.lotNo = trim(lotNo.getValue());
        r.inboundDate = inboundDate.getValue();
        r.expireDate = expireDate.getValue();
        r.inputQty = StockCalc.parse(inputQty.getValue());
        r.inputUnit = StockEnums.INPUT_PACK;
        r.unitPrice = StockCalc.parse(unitPrice.getValue());
        r.syncFinance = Boolean.TRUE.equals(syncFinance.getValue());
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
        DrugInboundRequest req = buildRequest();
        if (req == null) {
            return;
        }
        String unit = packUnit.getValue();
        String name = drugName.getValue();
        submitGuarded(req, key -> repository.createDrugInbound(req, key),
                st -> repository.getInbound(st.bizId == null ? 0 : st.bizId),
                vo -> {
                    onSubmitted(todoId, StockEnums.BIZ_INBOUND, vo.inboundId, farmContext.farmIdOrZero());
                    submitted.setValue(successMessage(req.inputQty, unit, name, Boolean.TRUE.equals(req.syncFinance)));
                }, this::onBizError);
    }

    /** “已入库 50 瓶口蹄疫灭活疫苗，已同步记一笔支出”+“查看”（E04） */
    @NonNull
    public static UiMessage successMessage(@Nullable BigDecimal qty, @Nullable String unit, @Nullable String name,
                                    boolean sync) {
        String text = "已入库 " + StockQtyFormatter.qty(qty, unit) + StockTexts.nz(name);
        return sync ? UiMessage.withPage(text + "，已同步记一笔支出", "查看", RouterPath.E04) : UiMessage.of(text);
    }

    private void onBizError(@NonNull ApiException e) {
        switch (e.getCode()) {
            case ErrorCodes.BANNED_DRUG:
                putFieldError(F_DRUG, e.getMessage());
                return;
            case ErrorCodes.LOT_UNAVAILABLE:
                putFieldError(F_LOT, e.getMessage());
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

    public void startEdit() {
        StockInboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        lotNo.setValue(StockTexts.nz(r.lotNo));
        inputQty.setValue(StockCalc.plain(r.inputQty));
        unitPrice.setValue(StockCalc.plain(r.unitPrice));
        inboundDate.setValue(r.inboundDate);
        expireDate.setValue(r.expireDate);
        syncFinance.setValue(r.syncFinance);
        fieldErrors.setValue(Collections.emptyMap());
        mode.setValue(FormMode.EDIT);
        updateExpireHint();
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
        StockInboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        InboundUpdateRequest req = new InboundUpdateRequest();
        req.inputQty = StockCalc.parse(inputQty.getValue());
        req.inputUnit = r.inputUnit != null ? r.inputUnit : StockEnums.INPUT_PACK;
        req.unitPrice = StockCalc.parse(unitPrice.getValue());
        req.inboundDate = inboundDate.getValue();
        req.syncFinance = Boolean.TRUE.equals(syncFinance.getValue());
        req.expireDate = expireDate.getValue();
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

    public void reloadRecord() {
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            launch(repository.getInbound(recordId), this::applyRecord);
        }
    }

    public void onVoidMenu() {
        StockInboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        String text = "作废后结存减少 " + StockQtyFormatter.qty(r.inputQty, packUnit.getValue());
        text += r.syncFinance ? "，同步记账生成的兽药疫苗 " + NumberFormatter.formatAmountSummary(r.amount) + "同时作废。" : "。";
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
            voided.setValue(r.syncFinance ? "已作废入库单，同步记账生成的兽药疫苗 "
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
        DrugOptionVO d = selectedDrug.getValue();
        m.put(F_DRUG, d == null ? null : String.valueOf(d.drugId));
        m.put(F_LOT, lotNo.getValue());
        m.put(F_DATE, inboundDate.getValue());
        m.put(F_EXPIRE, expireDate.getValue());
        m.put(F_QTY, inputQty.getValue());
        m.put(F_PRICE, unitPrice.getValue());
        m.put(F_SYNC, String.valueOf(Boolean.TRUE.equals(syncFinance.getValue())));
        return m;
    }

    @Override
    protected void applyDraft(@NonNull Map<String, String> d) {
        Long drug = StockExtras.readId(d.get(F_DRUG));
        if (drug != null && FormMode.NEW.equals(mode.getValue())) {
            onDrugPicked(drug);
        }
        lotNo.setValue(nz(d.get(F_LOT)));
        if (d.get(F_DATE) != null) {
            inboundDate.setValue(d.get(F_DATE));
        }
        expireDate.setValue(d.get(F_EXPIRE));
        inputQty.setValue(nz(d.get(F_QTY)));
        unitPrice.setValue(nz(d.get(F_PRICE)));
        syncFinance.setValue(!"false".equals(d.get(F_SYNC)));
        updateExpireHint();
    }

    // ===================== 对外 =====================

    public LiveData<String> getMode() {
        return mode;
    }

    public LiveData<List<DrugOptionVO>> getDrugOptions() {
        return drugOptions;
    }

    public LiveData<String> getBannedHint() {
        return bannedHint;
    }

    public LiveData<DrugOptionVO> getSelectedDrug() {
        return selectedDrug;
    }

    public LiveData<String> getDrugName() {
        return drugName;
    }

    public LiveData<String> getCategoryText() {
        return categoryText;
    }

    public MutableLiveData<String> getLotNo() {
        return lotNo;
    }

    public MutableLiveData<String> getInputQty() {
        return inputQty;
    }

    public MutableLiveData<String> getUnitPrice() {
        return unitPrice;
    }

    public LiveData<String> getExpireDate() {
        return expireDate;
    }

    public LiveData<String> getInboundDate() {
        return inboundDate;
    }

    public LiveData<Boolean> getSyncFinance() {
        return syncFinance;
    }

    public LiveData<String> getExpireHint() {
        return expireHint;
    }

    public LiveData<String> getAmountText() {
        return amountText;
    }

    public LiveData<String> getSyncText() {
        return syncText;
    }

    public LiveData<String> getPackUnit() {
        return packUnit;
    }

    public LiveData<StockInboundVO> getRecord() {
        return record;
    }

    public LiveData<List<String[]>> getReadonlyRows() {
        return readonlyRows;
    }

    public LiveData<PickerData> getDrugPicker() {
        return drugPicker;
    }

    @Nullable
    public StockActionsVO actions() {
        StockInboundVO r = record.getValue();
        return r == null ? null : r.actions;
    }

    @NonNull
    public String keyword() {
        return keyword;
    }

    public SingleLiveEvent<String> getOpenArchiveNew() {
        return openArchiveNew;
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
