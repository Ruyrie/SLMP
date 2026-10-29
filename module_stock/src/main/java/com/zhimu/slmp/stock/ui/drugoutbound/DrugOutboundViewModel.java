package com.zhimu.slmp.stock.ui.drugoutbound;

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
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.DrugOutboundRequest;
import com.zhimu.slmp.stock.model.request.OutboundUpdateRequest;
import com.zhimu.slmp.stock.model.request.VoidRequest;
import com.zhimu.slmp.stock.model.vo.CostAllocationVO;
import com.zhimu.slmp.stock.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.stock.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.stock.model.vo.DrugOptionListResult;
import com.zhimu.slmp.stock.model.vo.DrugOptionVO;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockBatchOptionVO;
import com.zhimu.slmp.stock.model.vo.StockMemberVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.model.vo.StockPenOptionVO;
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
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * M11 兽药与疫苗出库（技术准备文档 2.4.13，按需求补充）：
 * <ul>
 *     <li>连接按钮组切换领用 / 退货；药品 API-M35（OUTBOUND），批号 API-M27（USE / RETURN）：领用用 default_lot_id 预选并标记“系统默认”，
 *     切到退货时只清空系统默认的批号；不可选批号显示“去报废”</li>
 *     <li>领用：关联批次 / 栏舍（API-L06 / L07，待定事项 2）、领用人（API-F16，默认当前用户），批号、数量、关联对象变化后 300 ms 取分摊预览 API-M33</li>
 *     <li>退货：退回对象（API-M12 最近 5 个）、退货原因、退货金额 = 数量 × 批号均价</li>
 *     <li>确认 API-M32（幂等键）；VIEW / EDIT 同 M10（API-M21 / M22 / M23），出库类型、药品与批号只读</li>
 * </ul>
 * 草稿 page_code M11。
 */
public class DrugOutboundViewModel extends StockFormViewModel {

    public static final String PAGE_CODE = "M11";
    public static final String F_DRUG = "drug_id";
    public static final String F_LOT = "drug_lot_id";
    public static final String F_QTY = "input_qty";
    public static final String F_BATCH = "batch_id";
    public static final String F_PEN = "pen_id";
    public static final String F_RECEIVER = "receiver_id";
    public static final String F_RETURN_TO = "return_to";
    public static final String F_REASON = "return_reason";
    public static final String F_REMARK = "remark";
    public static final String F_DATE = "out_date";
    public static final String PICKER_DRUG = "stock_m11_drug";
    public static final String PICKER_LOT = "stock_m11_lot";

    /** 退货原因 Chip */
    public static final String[][] RETURN_REASONS = {
            {StockEnums.RETURN_QUALITY, "质量问题"}, {StockEnums.RETURN_PACKAGE_DAMAGED, "包装破损"},
            {StockEnums.RETURN_OVER_PURCHASE, "购买过多"}, {StockEnums.RETURN_OTHER, "其他"}};

    private final FarmContext farmContext;
    private final SessionManager session;

    private final MutableLiveData<String> mode = new MutableLiveData<>(FormMode.NEW);
    private final MutableLiveData<String> outType = new MutableLiveData<>(StockEnums.OUT_USE);
    private final MutableLiveData<List<DrugOptionVO>> drugOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DrugOptionVO> selectedDrug = new MutableLiveData<>();
    private final MutableLiveData<DrugLotOptionsVO> lotOptions = new MutableLiveData<>();
    private final MutableLiveData<DrugLotOptionVO> selectedLot = new MutableLiveData<>();
    private final MutableLiveData<String> drugName = new MutableLiveData<>("");
    private final MutableLiveData<String> drugCategory = new MutableLiveData<>();
    private final MutableLiveData<String> drugCategoryText = new MutableLiveData<>();
    private final MutableLiveData<String> lotText = new MutableLiveData<>("");
    private final MutableLiveData<String> lotHelper = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showPriorityTag = new MutableLiveData<>(false);
    private final MutableLiveData<String> unitText = new MutableLiveData<>("");
    private final MutableLiveData<String> maxText = new MutableLiveData<>();
    private final MutableLiveData<String> inputQty = new MutableLiveData<>("");
    /** 数量真的改了才清除数量错误（重拉批号结存后的绑定回写不算） */
    private final TextChange qtyChange = new TextChange();
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<String> returnTo = new MutableLiveData<>("");
    private final MutableLiveData<String> outDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> targetType = new MutableLiveData<>(StockEnums.TARGET_BATCH);
    private final MutableLiveData<String> returnReason = new MutableLiveData<>();
    private final MutableLiveData<Long> batchId = new MutableLiveData<>();
    private final MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MutableLiveData<Long> receiverId = new MutableLiveData<>();
    private final MutableLiveData<String> targetText = new MutableLiveData<>("");
    private final MutableLiveData<String> targetHelper = new MutableLiveData<>();
    private final MutableLiveData<String> receiverText = new MutableLiveData<>("");
    private final MutableLiveData<String> recorderText = new MutableLiveData<>();
    private final MutableLiveData<List<StockPenOptionVO>> pens = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<StockBatchOptionVO>> batches = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<StockMemberVO>> members = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<CostAllocationVO> allocation = new MutableLiveData<>();
    private final MediatorLiveData<String> returnAmount = new MediatorLiveData<>();
    private final MutableLiveData<List<String>> returnToSuggest = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<StockOutboundVO> record = new MutableLiveData<>();
    private final MutableLiveData<List<String[]>> readonlyRows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> pending = new MutableLiveData<>(false);
    private final MutableLiveData<PickerData> drugPicker = new MutableLiveData<>();
    private final MutableLiveData<PickerData> lotPicker = new MutableLiveData<>();

    private final SingleLiveEvent<String> submitted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> confirmDiscard = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openScrap = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> confirmVoid = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> voided = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> notToday = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> edited = new SingleLiveEvent<>();

    private final PublishSubject<Object> previewTrigger = PublishSubject.create();
    private final PublishSubject<String> returnToQuery = PublishSubject.create();

    @Nullable
    private Long presetDrugId;
    @Nullable
    private Long presetLotId;
    @Nullable
    private Long recordId;
    /** 当前批号是系统按先到期先出默认选的（切到退货时清空） */
    private boolean lotIsDefault;
    @NonNull
    private String drugKeyword = "";

    @Inject
    public DrugOutboundViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                                 @NonNull FarmContext farmContext, @NonNull SessionManager session) {
        super(repository, drafts);
        this.farmContext = farmContext;
        this.session = session;
        returnAmount.setValue("—");
        returnAmount.addSource(inputQty, this::onQtyChanged);
        returnAmount.addSource(remark, v -> clearFieldError(F_REMARK));
        returnAmount.addSource(returnTo, v -> clearFieldError(F_RETURN_TO));
        disposables.add(previewTrigger
                .debounce(debounceMs, TimeUnit.MILLISECONDS, Schedulers.computation())
                .switchMap(t -> previewSource())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(allocation::setValue, e -> {
                }));
        disposables.add(returnToQuery
                .debounce(debounceMs, TimeUnit.MILLISECONDS, Schedulers.computation())
                .switchMapSingle(k -> repository.recentPartners(StockEnums.PARTNER_RETURN_TO, k.isEmpty() ? null : k))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(returnToSuggest::setValue, e -> {
                }));
        AccountUserVO me = session.currentUser();
        if (me != null) {
            receiverId.setValue(me.getUserId());
            receiverText.setValue(StockTexts.nz(me.getName()));
            recorderText.setValue("登记人：" + StockTexts.nz(me.getName()));
        }
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        init(StockExtras.readId(handle.get(RouterPath.EXTRA_DRUG_ID)),
                StockExtras.readId(handle.get(RouterPath.EXTRA_LOT_ID)),
                StockExtras.readId(handle.get(RouterPath.EXTRA_RECORD_ID)));
    }

    /** M06“出库”带药品与批号；M05 菜单不预选；record_id 有值为只读 */
    public void init(@Nullable Long drugId, @Nullable Long lotId, @Nullable Long recordId) {
        presetDrugId = drugId;
        presetLotId = lotId;
        this.recordId = recordId;
        Single<Options> options = Single.zip(
                repository.getPenOptions().onErrorReturnItem(Collections.emptyList()),
                repository.getActiveBatches().onErrorReturnItem(Collections.emptyList()),
                repository.getMembers().onErrorReturnItem(Collections.emptyList()), Options::new);
        if (recordId != null) {
            mode.setValue(FormMode.VIEW);
            load(Single.zip(repository.getOutbound(recordId), options, (r, o) -> {
                applyOptionsLater = o;
                return r;
            }), r -> {
                applyOptions(applyOptionsLater);
                applyRecord(r);
            });
        } else {
            mode.setValue(FormMode.NEW);
            load(Single.zip(repository.listDrugOptions(StockEnums.PURPOSE_OUTBOUND, null), options, (d, o) -> {
                applyOptionsLater = o;
                return d;
            }), d -> {
                applyOptions(applyOptionsLater);
                applyDrugs(d);
                markClean();
                checkDraft();
            });
        }
    }

    @Nullable
    private Options applyOptionsLater;

    @Override
    public void retry() {
        init(presetDrugId, presetLotId, recordId);
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data == null;
    }

    private void applyOptions(@Nullable Options o) {
        if (o == null) {
            return;
        }
        pens.setValue(o.pens);
        batches.setValue(o.batches);
        members.setValue(o.members);
        Long me = receiverId.getValue();
        if (me != null) {
            for (StockMemberVO m : o.members) {
                if (m.userId == me) {
                    receiverText.setValue(StockTexts.nz(m.name));
                }
            }
        }
    }

    private void applyDrugs(@NonNull DrugOptionListResult r) {
        List<DrugOptionVO> list = r.list == null ? new ArrayList<>() : r.list;
        drugOptions.setValue(list);
        if (presetDrugId != null) {
            for (DrugOptionVO d : list) {
                if (d.drugId == presetDrugId) {
                    selectDrug(d, true);
                }
            }
            presetDrugId = null;
        }
        rebuildDrugPicker(r.bannedHint);
    }

    // ===================== 出库类型 =====================

    /** 切换领用 / 退货：共有字段保留；领用切到退货时，系统默认选的批号改为未选 */
    public void setOutType(@NonNull String type) {
        if (type.equals(outType.getValue()) || !FormMode.NEW.equals(mode.getValue())) {
            return;
        }
        outType.setValue(type);
        if (StockEnums.OUT_RETURN.equals(type) && lotIsDefault) {
            selectedLot.setValue(null);
            lotIsDefault = false;
        }
        allocation.setValue(null);
        DrugOptionVO d = selectedDrug.getValue();
        if (d != null) {
            loadLots(d.drugId, false);
        }
        updateLotTexts();
        recomputeAmounts();
        trigger();
    }

    public boolean isUse() {
        return StockEnums.OUT_USE.equals(outType.getValue());
    }

    // ===================== 药品与批号 =====================

    public void searchDrugs(@NonNull String kw) {
        drugKeyword = kw;
        launch(repository.listDrugOptions(StockEnums.PURPOSE_OUTBOUND, kw.isEmpty() ? null : kw), this::applyDrugs);
    }

    public void onDrugPicked(long drugId) {
        List<DrugOptionVO> list = drugOptions.getValue();
        if (list == null) {
            return;
        }
        for (DrugOptionVO d : list) {
            if (d.drugId == drugId) {
                DrugOptionVO old = selectedDrug.getValue();
                boolean changed = old == null || old.drugId != drugId;
                selectDrug(d, changed);
            }
        }
        rebuildDrugPicker(null);
    }

    private void selectDrug(@NonNull DrugOptionVO d, boolean reloadLots) {
        selectedDrug.setValue(d);
        drugName.setValue(StockTexts.nz(d.name));
        drugCategory.setValue(d.category);
        drugCategoryText.setValue(StockEnums.categoryText(d.category));
        unitText.setValue(StockEnums.isVaccine(d.category) ? StockEnums.unitText(StockEnums.UNIT_DOSE)
                : d.packUnit != null ? d.packUnit : StockEnums.unitText(d.unit));
        clearFieldError(F_DRUG);
        if (reloadLots) {
            selectedLot.setValue(null);
            lotIsDefault = false;
            updateLotTexts();
            loadLots(d.drugId, true);
        }
    }

    private void loadLots(long drugId, boolean pickDefault) {
        String purpose = isUse() ? StockEnums.PURPOSE_USE : StockEnums.PURPOSE_RETURN;
        launch(repository.listDrugLotOptions(drugId, purpose, null), o -> applyLots(o, pickDefault));
    }

    @VisibleForTesting
    void applyLots(@NonNull DrugLotOptionsVO o, boolean pickDefault) {
        lotOptions.setValue(o);
        DrugLotOptionVO current = selectedLot.getValue();
        DrugLotOptionVO next = null;
        boolean isDefault = false;
        if (current != null) {
            next = findSelectable(o, current.lotId);
            isDefault = next != null && lotIsDefault;
        }
        if (next == null && presetLotId != null) {
            next = findSelectable(o, presetLotId);
            presetLotId = null;
        }
        if (next == null && pickDefault && isUse() && o.defaultLotId != null) {
            next = findSelectable(o, o.defaultLotId);
            isDefault = next != null;
        }
        selectedLot.setValue(next);
        lotIsDefault = isDefault;
        updateLotTexts();
        rebuildLotPicker();
        recomputeAmounts();
        trigger();
    }

    @Nullable
    private static DrugLotOptionVO findSelectable(@Nullable DrugLotOptionsVO o, long lotId) {
        if (o == null || o.selectable == null) {
            return null;
        }
        for (DrugLotOptionVO l : o.selectable) {
            if (l.lotId == lotId) {
                return l;
            }
        }
        return null;
    }

    public void onLotPicked(long lotId) {
        DrugLotOptionVO l = findSelectable(lotOptions.getValue(), lotId);
        if (l == null) {
            return;
        }
        selectedLot.setValue(l);
        lotIsDefault = false;
        clearFieldError(F_LOT);
        updateLotTexts();
        rebuildLotPicker();
        recomputeAmounts();
        trigger();
    }

    /** 批号面板“去报废”：有已填内容时先确认放弃 */
    public void onGoScrap(long lotId) {
        if (isDirty()) {
            confirmDiscard.setValue(lotId);
        } else {
            openScrap.setValue(lotId);
        }
    }

    public void onDiscardForScrap(long lotId) {
        onDiscardConfirmed();
        openScrap.setValue(lotId);
    }

    public boolean isLotDefault() {
        return lotIsDefault;
    }

    /** 批号辅助文字与“优先出库”标签、数量上限文字 */
    private void updateLotTexts() {
        DrugLotOptionVO l = selectedLot.getValue();
        if (l == null) {
            lotText.setValue("");
            lotHelper.setValue(null);
            showPriorityTag.setValue(false);
            maxText.setValue(null);
            return;
        }
        lotText.setValue(StockTexts.nz(l.lotNo));
        lotHelper.setValue(lotHelperOf(l, isUse(), earlierLot(l)));
        showPriorityTag.setValue(isUse() && l.priorityOut);
        BigDecimal max = maxOf(l);
        String m = "最多 " + StockQtyFormatter.qty(max, unitText.getValue());
        if (StockEnums.isVaccine(drugCategory.getValue()) && StockQtyFormatter.hasPack(l.stock)) {
            m += "（" + StockQtyFormatter.packText(l.stock) + "）";
        }
        maxText.setValue(isUse() ? m + "，按该批号结存计算" : m);
    }

    /** 领用改选较晚到期的批号时，返回更早到期的优先批号 */
    @Nullable
    private DrugLotOptionVO earlierLot(@NonNull DrugLotOptionVO selected) {
        DrugLotOptionsVO o = lotOptions.getValue();
        if (!isUse() || selected.priorityOut || o == null || o.selectable == null) {
            return null;
        }
        for (DrugLotOptionVO l : o.selectable) {
            if (l.priorityOut && l.lotId != selected.lotId) {
                return l;
            }
        }
        return null;
    }

    /** “有效期至 2026-11-08 · 临期 20 天 · 结存 8 桶”，领用改选较晚批号时追加“· 批号 2025033 更早到期，建议优先领用” */
    @NonNull
    static String lotHelperOf(@NonNull DrugLotOptionVO l, boolean use, @Nullable DrugLotOptionVO earlier) {
        String near = StockEnums.LOT_NEAR_EXPIRY.equals(l.lotStatus) && l.daysToExpire != null
                ? "临期 " + l.daysToExpire + " 天" : null;
        String s = StockTexts.join(StockTexts.DOT, "有效期至 " + StockTexts.nz(l.expireDate), use ? near : null,
                "结存 " + StockQtyFormatter.format(l.stock));
        if (earlier != null) {
            s += StockTexts.DOT + "批号 " + earlier.lotNo + " 更早到期，建议优先领用";
        }
        return s;
    }

    /** 出库上限：疫苗为头份，其他为包装数；修改时为 max_qty */
    @NonNull
    BigDecimal maxOf(@Nullable DrugLotOptionVO l) {
        StockOutboundVO r = record.getValue();
        if (FormMode.EDIT.equals(mode.getValue()) && r != null && r.maxQty != null) {
            return r.maxQty;
        }
        if (l == null || l.stock == null || l.stock.value == null) {
            return BigDecimal.ZERO;
        }
        if (StockEnums.isVaccine(drugCategory.getValue()) || !StockQtyFormatter.hasPack(l.stock)) {
            return l.stock.value;
        }
        return StockCalc.toPack(l.stock.value, l.stock.packSize);
    }

    private void rebuildDrugPicker(@Nullable String bannedHint) {
        List<PickerItem> items = new ArrayList<>();
        DrugOptionVO sel = selectedDrug.getValue();
        List<DrugOptionVO> list = drugOptions.getValue();
        if (list != null) {
            for (DrugOptionVO d : list) {
                items.add(PickerItem.item(String.valueOf(d.drugId), StockTexts.nz(d.name))
                        .subtitle(d.usable == null ? null : "可用结存 " + StockQtyFormatter.packText(d.usable))
                        .icon(StockTexts.categoryIcon(d.category), ListRowView.TONE_SECONDARY)
                        .tag(StockEnums.categoryText(d.category), StatusTagView.NEUTRAL)
                        .selected(sel != null && sel.drugId == d.drugId));
            }
        }
        String empty = drugKeyword.isEmpty() ? "没有可以出库的药品。先登记入库，再登记出库。"
                : "没有找到“" + drugKeyword + "”相关的药品";
        drugPicker.setValue(new PickerData(items, bannedHint, empty, null));
    }

    private void rebuildLotPicker() {
        DrugLotOptionsVO o = lotOptions.getValue();
        DrugLotOptionVO sel = selectedLot.getValue();
        List<PickerItem> items = new ArrayList<>();
        String packUnit = null;
        if (o != null && o.selectable != null) {
            for (DrugLotOptionVO l : o.selectable) {
                packUnit = l.stock == null ? null : l.stock.packUnit;
                String price = l.avgPrice == null ? null : "每" + (StockQtyFormatter.hasPack(l.stock) && !StockEnums.isVaccine(
                        drugCategory.getValue()) ? l.stock.packUnit : unitText.getValue()) + " "
                        + StockCalc.money(unitPrice(l)) + " 元";
                items.add(PickerItem.item(String.valueOf(l.lotId), StockTexts.nz(l.lotNo))
                        .subtitle(StockTexts.join(StockTexts.DOT, "有效期至 " + StockTexts.nz(l.expireDate),
                                "结存 " + StockQtyFormatter.format(l.stock), price))
                        .tag(l.priorityOut ? "优先出库" : null, StatusTagView.NEUTRAL)
                        .selected(sel != null && sel.lotId == l.lotId));
            }
        }
        if (o != null && o.unselectable != null && !o.unselectable.isEmpty()) {
            items.add(PickerItem.header("不可选择"));
            for (DrugLotOptionVO l : o.unselectable) {
                String tag = StockEnums.LOT_RECALLED.equals(l.lotStatus) ? "已停用"
                        : StockEnums.LOT_EXPIRED.equals(l.lotStatus) ? "已过期"
                        : l.lotStatusText != null ? l.lotStatusText : "不可选";
                items.add(PickerItem.item(String.valueOf(l.lotId), StockTexts.nz(l.lotNo))
                        .subtitle(StockTexts.join(StockTexts.DOT, "有效期至 " + StockTexts.nz(l.expireDate),
                                "结存 " + StockQtyFormatter.format(l.stock)))
                        .tag(tag, StatusTagView.ERROR)
                        .disabled(true)
                        .action(l.canGoScrap ? "去报废" : null));
            }
        }
        boolean noSelectable = o == null || o.selectable == null || o.selectable.isEmpty();
        lotPicker.setValue(new PickerData(items, null, noSelectable ? "没有可以出库的批号。" : null,
                "已过期和已停用的批号不能领用或退货，只能登记报废。"));
    }

    /** 每包装单价（疫苗为每头份）= 均价 × 每包装折合数 */
    @Nullable
    private BigDecimal unitPrice(@NonNull DrugLotOptionVO l) {
        if (l.avgPrice == null) {
            return null;
        }
        if (StockEnums.isVaccine(drugCategory.getValue()) || !StockQtyFormatter.hasPack(l.stock)) {
            return l.avgPrice;
        }
        return l.avgPrice.multiply(l.stock.packSize);
    }

    /** 批号面板副标题“聚维酮碘溶液 · 按先到期先出排序” */
    @NonNull
    public String lotPickerSubtitle() {
        return drugName.getValue() + StockTexts.DOT + "按先到期先出排序";
    }

    // ===================== 关联对象、领用人与数量 =====================

    /** 切换关联对象“批次 / 栏舍”：已选值清空，分摊重算 */
    public void setTargetType(@NonNull String t) {
        if (t.equals(targetType.getValue())) {
            return;
        }
        targetType.setValue(t);
        batchId.setValue(null);
        penId.setValue(null);
        targetText.setValue("");
        targetHelper.setValue(null);
        allocation.setValue(null);
        clearFieldError(F_BATCH);
        clearFieldError(F_PEN);
    }

    public void onBatchPicked(long id) {
        List<StockBatchOptionVO> list = batches.getValue();
        if (list == null) {
            return;
        }
        for (StockBatchOptionVO b : list) {
            if (b.batchId == id) {
                batchId.setValue(id);
                targetText.setValue(batchLabel(b));
                targetHelper.setValue("存栏 " + b.currentCount + " " + (b.unitText == null ? "头" : b.unitText)
                        + StockTexts.DOT + "成本全部计入该批次");
                clearFieldError(F_BATCH);
                trigger();
            }
        }
    }

    public void onPenPicked(long id) {
        List<StockPenOptionVO> list = pens.getValue();
        if (list == null) {
            return;
        }
        for (StockPenOptionVO p : list) {
            if (p.penId == id && p.stockCount > 0 && !p.disabled) {
                penId.setValue(id);
                targetText.setValue(StockTexts.nz(p.penName));
                int n = batchCountInPen(id);
                targetHelper.setValue((n > 0 ? "在栏 " + n + " 个批次" : "在栏 " + p.stockCount + " "
                        + (p.unitText == null ? "头" : p.unitText)) + "，成本按存栏量比例分摊");
                clearFieldError(F_PEN);
                trigger();
            }
        }
    }

    int batchCountInPen(long penId) {
        int n = 0;
        List<StockBatchOptionVO> list = batches.getValue();
        if (list != null) {
            for (StockBatchOptionVO b : list) {
                if (b.pens == null) {
                    continue;
                }
                for (StockBatchOptionVO.PenItem p : b.pens) {
                    if (p.penId == penId && p.stockCount > 0) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** 批次下拉文字“ZM2610-031 · 3 号育肥舍” */
    @NonNull
    static String batchLabel(@NonNull StockBatchOptionVO b) {
        String pen = b.pens == null || b.pens.isEmpty() ? null : b.pens.get(0).penName;
        return StockTexts.join(StockTexts.DOT, b.batchNo, pen);
    }

    public void onReceiverPicked(long userId) {
        List<StockMemberVO> list = members.getValue();
        if (list == null) {
            return;
        }
        for (StockMemberVO m : list) {
            if (m.userId == userId) {
                receiverId.setValue(userId);
                receiverText.setValue(StockTexts.nz(m.name));
                clearFieldError(F_RECEIVER);
            }
        }
    }

    public void onReturnReasonPicked(@Nullable String reason) {
        returnReason.setValue(reason);
        clearFieldError(F_REASON);
        clearFieldError(F_REMARK);
    }

    public void onDatePicked(@Nullable String date) {
        outDate.setValue(date);
        String e = StockValidators.notAfterToday(date, isUse() ? "领用日期" : "退货日期");
        if (e != null) {
            putFieldError(F_DATE, e);
        } else {
            clearFieldError(F_DATE);
        }
    }

    public void queryReturnTo(@Nullable String keyword) {
        returnToQuery.onNext(keyword == null ? "" : keyword.trim());
    }

    private void onQtyChanged(@Nullable String qty) {
        if (qtyChange.update(qty)) {
            clearFieldError(F_QTY);
        }
        recomputeAmounts();
        trigger();
    }

    private void recomputeAmounts() {
        returnAmount.setValue(StockCalc.money(localAmount()));
    }

    /** 本地估算：数量 × 批号均价（非疫苗先换算为记账数量） */
    @Nullable
    BigDecimal localAmount() {
        DrugLotOptionVO l = selectedLot.getValue();
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        if (l == null || q == null || l.avgPrice == null) {
            StockOutboundVO r = record.getValue();
            if (r != null && q != null && r.unitPrice != null) {
                return StockCalc.amount(q, r.unitPrice);
            }
            return null;
        }
        if (StockEnums.isVaccine(drugCategory.getValue()) || !StockQtyFormatter.hasPack(l.stock)) {
            return StockCalc.amount(q, l.avgPrice);
        }
        return StockCalc.amount(StockCalc.toMeasure(q, l.stock.packSize), l.avgPrice);
    }

    private void trigger() {
        previewTrigger.onNext(Boolean.TRUE);
    }

    /** 领用、批号、数量、关联对象齐全时取分摊预览；否则清空 */
    @NonNull
    private Observable<CostAllocationVO> previewSource() {
        DrugLotOptionVO l = selectedLot.getValue();
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        String t = targetType.getValue();
        Long batch = batchId.getValue();
        Long pen = penId.getValue();
        boolean ready = isUse() && l != null && q != null && q.signum() > 0 && t != null
                && (StockEnums.TARGET_BATCH.equals(t) ? batch != null : pen != null);
        if (!ready) {
            return Observable.just(new CostAllocationVO());
        }
        return repository.previewAllocation(l.lotId, q, inputUnit(), t, StockEnums.TARGET_BATCH.equals(t) ? batch : null,
                        StockEnums.TARGET_PEN.equals(t) ? pen : null)
                .onErrorReturnItem(new CostAllocationVO())
                .toObservable();
    }

    @NonNull
    String inputUnit() {
        DrugLotOptionVO l = selectedLot.getValue();
        boolean dose = StockEnums.isVaccine(drugCategory.getValue());
        return dose || l == null || !StockQtyFormatter.hasPack(l.stock) ? StockEnums.INPUT_MEASURE
                : StockEnums.INPUT_PACK;
    }

    /** 分摊卡行：“ZM2610-019 · 存栏 150 头 · 62.5%”“56.25 元”；关联批次时“ZM2610-031 · 全部计入” */
    @NonNull
    public static List<String[]> allocationRows(@Nullable CostAllocationVO a) {
        List<String[]> rows = new ArrayList<>();
        if (a == null || a.rows == null) {
            return rows;
        }
        for (CostAllocationVO.Row r : a.rows) {
            String left;
            if (a.rows.size() == 1 && (r.ratio == null || r.ratio.compareTo(BigDecimal.ONE) >= 0)) {
                left = StockTexts.nz(r.batchNo) + StockTexts.DOT + "全部计入";
            } else {
                left = StockTexts.join(StockTexts.DOT, r.batchNo, r.headCount == null ? null : "存栏 " + r.headCount + " 头",
                        r.ratio == null ? null : NumberFormatter.formatPercent(r.ratio));
            }
            rows.add(new String[]{left, StockCalc.money(r.amount) + " 元"});
        }
        return rows;
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
        DrugLotOptionVO lot = selectedLot.getValue();
        if (isNew && lot == null) {
            e.put(F_LOT, "请选择库存批号");
        }
        BigDecimal q = StockCalc.parse(inputQty.getValue());
        boolean dose = StockEnums.isVaccine(drugCategory.getValue());
        if (q == null || q.signum() <= 0) {
            e.put(F_QTY, StockValidators.MSG_GT_ZERO);
        } else if (dose && !StockCalc.isInteger(q)) {
            e.put(F_QTY, "头份必须是整数");
        } else if (StockCalc.scaleOf(q) > 2) {
            e.put(F_QTY, StockValidators.MSG_SCALE);
        } else if ((lot != null || !isNew) && q.compareTo(maxOf(lot)) > 0) {
            e.put(F_QTY, "超过该批号结存 " + StockQtyFormatter.qty(maxOf(lot), unitText.getValue())
                    + "，出库后库存不能为负数");
        }
        String r = trim(remark.getValue());
        if (isUse()) {
            if (StockEnums.TARGET_PEN.equals(targetType.getValue())) {
                if (penId.getValue() == null) {
                    e.put(F_PEN, "请选择栏舍");
                }
            } else if (batchId.getValue() == null) {
                e.put(F_BATCH, "请选择批次");
            }
            if (receiverId.getValue() == null) {
                e.put(F_RECEIVER, "请选择领用人");
            }
        } else {
            String rt = StockValidators.text(returnTo.getValue(), 30, "请填写退回对象");
            if (rt != null) {
                e.put(F_RETURN_TO, rt);
            }
            if (returnReason.getValue() == null) {
                e.put(F_REASON, "请选择退货原因");
            }
            if (StockEnums.RETURN_OTHER.equals(returnReason.getValue()) && r.isEmpty()) {
                e.put(F_REMARK, "请写明退货原因");
            }
        }
        if (!e.containsKey(F_REMARK) && r.length() > 100) {
            e.put(F_REMARK, "最多 100 个字");
        }
        String d = StockValidators.notAfterToday(outDate.getValue(), isUse() ? "领用日期" : "退货日期");
        if (d != null) {
            e.put(F_DATE, d);
        }
        return e;
    }

    @VisibleForTesting
    @Nullable
    DrugOutboundRequest buildRequest() {
        DrugOptionVO d = selectedDrug.getValue();
        DrugLotOptionVO l = selectedLot.getValue();
        if (d == null || l == null) {
            return null;
        }
        DrugOutboundRequest r = new DrugOutboundRequest();
        r.outType = outType.getValue();
        r.drugId = d.drugId;
        r.drugLotId = l.lotId;
        r.inputQty = StockCalc.parse(inputQty.getValue());
        r.inputUnit = inputUnit();
        if (isUse()) {
            r.targetType = targetType.getValue();
            r.batchId = StockEnums.TARGET_BATCH.equals(r.targetType) ? batchId.getValue() : null;
            r.penId = StockEnums.TARGET_PEN.equals(r.targetType) ? penId.getValue() : null;
            r.receiverId = receiverId.getValue();
        } else {
            r.returnTo = trim(returnTo.getValue());
            r.returnReason = returnReason.getValue();
        }
        r.remark = emptyToNull(remark.getValue());
        r.outDate = outDate.getValue();
        r.stockVersion = l.stockVersion;
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
        DrugOutboundRequest req = buildRequest();
        if (req == null) {
            return;
        }
        String qty = StockQtyFormatter.qty(req.inputQty, unitText.getValue());
        String name = drugName.getValue();
        boolean use = isUse();
        submitGuarded(req, key -> repository.createDrugOutbound(req, key),
                st -> repository.getOutbound(st.bizId == null ? 0 : st.bizId),
                vo -> {
                    onSubmitted(null, StockEnums.BIZ_OUTBOUND, vo.outboundId, farmContext.farmIdOrZero());
                    submitted.setValue(successText(use, qty, name, vo.allocations));
                }, this::onError);
    }

    /** 领用：“已登记领用 2 桶聚维酮碘溶液，成本已计入 2 个批次”/“…，成本已计入 ZM2610-031”；退货：“已登记退货 4 袋阿莫西林可溶性粉” */
    @NonNull
    static String successText(boolean use, @NonNull String qty, @Nullable String name,
                              @Nullable List<CostAllocationVO.Row> allocations) {
        if (!use) {
            return "已登记退货 " + qty + StockTexts.nz(name);
        }
        String text = "已登记领用 " + qty + StockTexts.nz(name);
        if (allocations == null || allocations.isEmpty()) {
            return text;
        }
        if (allocations.size() == 1) {
            return text + "，成本已计入 " + StockTexts.nz(allocations.get(0).batchNo);
        }
        return text + "，成本已计入 " + allocations.size() + " 个批次";
    }

    private void onError(@NonNull ApiException e) {
        switch (e.getCode()) {
            case ErrorCodes.LOT_UNAVAILABLE:
                putFieldError(F_LOT, e.getMessage());
                return;
            case ErrorCodes.BANNED_DRUG:
                showMessage(e.getMessage());
                return;
            case ErrorCodes.STOCK_NOT_ENOUGH:
                putFieldError(F_QTY, e.getMessage());
                return;
            case ErrorCodes.VERSION_CONFLICT: {
                String cur = e.dataString("current_qty");
                if (cur != null) {
                    // 服务端带回当前结存：显示出来，并自动重拉批号 / 上限（取到新的 stock_version），核对后可直接重新提交
                    putFieldError(F_QTY, "库存已变化，当前结存 " + StockQtyFormatter.qty(StockCalc.parse(cur),
                            unitText.getValue()) + "，已刷新，请核对后重新提交");
                    if (FormMode.NEW.equals(mode.getValue())) {
                        reloadOptions();
                    } else if (FormMode.EDIT.equals(mode.getValue())) {
                        refreshEditLimit();
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

    private void applyRecord(@NonNull StockOutboundVO r) {
        record.setValue(r);
        mode.setValue(FormMode.VIEW);
        outType.setValue(StockEnums.OUT_RETURN.equals(r.outType) ? StockEnums.OUT_RETURN : StockEnums.OUT_USE);
        drugName.setValue(StockTexts.nz(r.materialName));
        drugCategory.setValue(r.category);
        drugCategoryText.setValue(StockEnums.categoryText(r.category));
        lotText.setValue(StockTexts.nz(r.lotNo));
        boolean dose = StockEnums.isVaccine(r.category);
        String unit = dose ? StockEnums.unitText(StockEnums.UNIT_DOSE)
                : r.qty != null && r.qty.packUnit != null ? r.qty.packUnit : r.qty == null ? "" : StockQtyFormatter.unitText(r.qty);
        unitText.setValue(unit);
        allocation.setValue(r.allocations == null ? null : allocationOf(r));
        StockActionsVO a = r.actions;
        pending.setValue(a != null && (StockEnums.EDIT_PENDING.equals(a.editState) || a.pendingRequestId != null));
        boolean use = isUse();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"药品", r.materialName});
        rows.add(new String[]{"库存批号", r.lotNo});
        rows.add(new String[]{use ? "领用数量" : "退货数量", StockQtyFormatter.qty(r.inputQty, unit)});
        if (use) {
            String target = StockEnums.TARGET_PEN.equals(r.targetType) ? (r.pen == null ? null : r.pen.getName())
                    : r.batch == null ? null : r.batch.getBatchNo();
            rows.add(new String[]{"关联对象", target});
            rows.add(new String[]{"领用日期", DateFormatter.formatDate(r.outDate)});
            rows.add(new String[]{"领用人", r.receiver == null ? null : r.receiver.getName()});
        } else {
            rows.add(new String[]{"退回对象", r.returnTo});
            rows.add(new String[]{"退货原因", r.returnReasonText != null ? r.returnReasonText : reasonText(r.returnReason)});
            rows.add(new String[]{"退货日期", DateFormatter.formatDate(r.outDate)});
        }
        rows.add(new String[]{"说明", r.remark});
        rows.add(new String[]{use ? "领用成本" : "退货金额", r.amount == null ? null : StockCalc.money(r.amount) + " 元"});
        rows.add(new String[]{"登记人", StockTexts.join(StockTexts.DOT,
                r.createdBy == null ? null : r.createdBy.getName(),
                r.createdAt == null ? null : DateFormatter.formatDateTime(r.createdAt))});
        readonlyRows.setValue(rows);
    }

    @NonNull
    private static CostAllocationVO allocationOf(@NonNull StockOutboundVO r) {
        CostAllocationVO a = new CostAllocationVO();
        a.rows = r.allocations;
        a.totalAmount = r.amount;
        a.unitPrice = r.unitPrice;
        return a;
    }

    @Nullable
    static String reasonText(@Nullable String code) {
        for (String[] r : RETURN_REASONS) {
            if (r[0].equals(code)) {
                return r[1];
            }
        }
        return null;
    }

    /** 菜单“修改”：出库类型、药品与批号只读，上限为 max_qty */
    public void startEdit() {
        StockOutboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        inputQty.setValue(StockCalc.plain(r.inputQty));
        remark.setValue(StockTexts.nz(r.remark));
        outDate.setValue(r.outDate);
        if (isUse()) {
            targetType.setValue(r.targetType == null ? StockEnums.TARGET_BATCH : r.targetType);
            if (r.batch != null) {
                batchId.setValue(r.batch.getBatchId());
                targetText.setValue(StockTexts.nz(r.batch.getBatchNo()));
            }
            if (r.pen != null) {
                penId.setValue(r.pen.getPenId());
                targetText.setValue(StockTexts.nz(r.pen.getName()));
            }
            if (r.receiver != null) {
                receiverId.setValue(r.receiver.getUserId());
                receiverText.setValue(StockTexts.nz(r.receiver.getName()));
            }
        } else {
            returnTo.setValue(StockTexts.nz(r.returnTo));
            returnReason.setValue(r.returnReason);
        }
        fieldErrors.setValue(Collections.emptyMap());
        mode.setValue(FormMode.EDIT);
        maxText.setValue("最多 " + StockQtyFormatter.qty(r.maxQty, unitText.getValue()));
        lotHelper.setValue("出库类型、药品和批号不能修改，选错时请作废后重新登记");
        recomputeAmounts();
        markClean();
        checkDraft();
    }

    public boolean cancelEdit() {
        if (FormMode.EDIT.equals(mode.getValue())) {
            mode.setValue(FormMode.VIEW);
            StockOutboundVO r = record.getValue();
            if (r != null) {
                applyRecord(r);
            }
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
        req.inputUnit = r.inputUnit != null ? r.inputUnit : StockEnums.INPUT_PACK;
        if (isUse()) {
            req.targetType = targetType.getValue();
            req.batchId = StockEnums.TARGET_BATCH.equals(req.targetType) ? batchId.getValue() : null;
            req.penId = StockEnums.TARGET_PEN.equals(req.targetType) ? penId.getValue() : null;
            req.receiverId = receiverId.getValue();
        } else {
            req.returnTo = trim(returnTo.getValue());
            req.returnReason = returnReason.getValue();
        }
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

    /** 冲突提示里的“刷新”：新建时重拉药品与批号结存（已填内容保留），查看 / 修改时重新取记录 */
    public void refresh() {
        if (FormMode.NEW.equals(mode.getValue())) {
            reloadOptions();
            showMessage(MSG_REFRESHED);
        } else {
            reloadRecord();
        }
    }

    /** 新建：重拉药品选项（沿用搜索词），已选药品重拉批号；已选批号仍可选时按最新结存与 stock_version 保留 */
    @VisibleForTesting
    void reloadOptions() {
        launch(repository.listDrugOptions(StockEnums.PURPOSE_OUTBOUND, drugKeyword.isEmpty() ? null : drugKeyword),
                this::applyDrugs);
        DrugOptionVO d = selectedDrug.getValue();
        if (d != null) {
            loadLots(d.drugId, false);
        }
    }

    /** 修改中库存变化：只更新记录版本与上限，留在修改状态、已填内容保留 */
    private void refreshEditLimit() {
        if (recordId == null) {
            return;
        }
        launch(repository.getOutbound(recordId), r -> {
            record.setValue(r);
            maxText.setValue("最多 " + StockQtyFormatter.qty(r.maxQty, unitText.getValue()));
        });
    }

    /** 作废对话框正文：领用写明撤销的批次成本，退货只写结存增加 */
    public void onVoidMenu() {
        StockOutboundVO r = record.getValue();
        if (r == null) {
            return;
        }
        String head = "作废后" + StockTexts.nz(r.materialName) + " " + StockTexts.nz(r.lotNo) + " 结存增加 "
                + StockQtyFormatter.qty(r.inputQty, unitText.getValue());
        if (StockEnums.OUT_RETURN.equals(r.outType)) {
            confirmVoid.setValue(head + "。");
            return;
        }
        List<String> nos = new ArrayList<>();
        if (r.allocations != null) {
            for (CostAllocationVO.Row row : r.allocations) {
                nos.add(StockTexts.nz(row.batchNo));
            }
        }
        String cost = nos.isEmpty() ? "" : "，计入 " + String.join("、", nos) + " 的 "
                + NumberFormatter.formatAmountSummary(r.amount) + "成本同时撤销";
        confirmVoid.setValue(head + cost + "。");
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
        DrugOptionVO d = selectedDrug.getValue();
        DrugLotOptionVO l = selectedLot.getValue();
        m.put("out_type", outType.getValue());
        m.put(F_DRUG, d == null ? null : String.valueOf(d.drugId));
        m.put(F_LOT, l == null ? null : String.valueOf(l.lotId));
        m.put(F_QTY, inputQty.getValue());
        m.put("target_type", targetType.getValue());
        m.put(F_BATCH, batchId.getValue() == null ? null : String.valueOf(batchId.getValue()));
        m.put(F_PEN, penId.getValue() == null ? null : String.valueOf(penId.getValue()));
        m.put(F_RECEIVER, receiverId.getValue() == null ? null : String.valueOf(receiverId.getValue()));
        m.put(F_RETURN_TO, returnTo.getValue());
        m.put(F_REASON, returnReason.getValue());
        m.put(F_REMARK, remark.getValue());
        m.put(F_DATE, outDate.getValue());
        return m;
    }

    @Override
    protected void applyDraft(@NonNull Map<String, String> d) {
        if (FormMode.NEW.equals(mode.getValue())) {
            String type = d.get("out_type");
            if (type != null) {
                outType.setValue(type);
            }
            presetLotId = StockExtras.readId(d.get(F_LOT));
            Long drug = StockExtras.readId(d.get(F_DRUG));
            if (drug != null) {
                onDrugPicked(drug);
            }
        }
        inputQty.setValue(nz(d.get(F_QTY)));
        String t = d.get("target_type");
        if (t != null) {
            setTargetType(t);
        }
        Long batch = StockExtras.readId(d.get(F_BATCH));
        if (batch != null) {
            onBatchPicked(batch);
        }
        Long pen = StockExtras.readId(d.get(F_PEN));
        if (pen != null) {
            onPenPicked(pen);
        }
        Long receiver = StockExtras.readId(d.get(F_RECEIVER));
        if (receiver != null) {
            onReceiverPicked(receiver);
        }
        returnTo.setValue(nz(d.get(F_RETURN_TO)));
        returnReason.setValue(d.get(F_REASON));
        remark.setValue(nz(d.get(F_REMARK)));
        if (d.get(F_DATE) != null) {
            outDate.setValue(d.get(F_DATE));
        }
    }

    // ===================== 对外 =====================

    public LiveData<String> getMode() {
        return mode;
    }

    public LiveData<String> getOutType() {
        return outType;
    }

    public LiveData<DrugOptionVO> getSelectedDrug() {
        return selectedDrug;
    }

    public LiveData<DrugLotOptionsVO> getLotOptions() {
        return lotOptions;
    }

    public LiveData<DrugLotOptionVO> getSelectedLot() {
        return selectedLot;
    }

    public LiveData<String> getDrugName() {
        return drugName;
    }

    public LiveData<String> getDrugCategoryText() {
        return drugCategoryText;
    }

    public LiveData<String> getLotText() {
        return lotText;
    }

    public LiveData<String> getLotHelper() {
        return lotHelper;
    }

    public LiveData<Boolean> getShowPriorityTag() {
        return showPriorityTag;
    }

    public LiveData<String> getUnitText() {
        return unitText;
    }

    public LiveData<String> getMaxText() {
        return maxText;
    }

    public MutableLiveData<String> getInputQty() {
        return inputQty;
    }

    public MutableLiveData<String> getRemark() {
        return remark;
    }

    public MutableLiveData<String> getReturnTo() {
        return returnTo;
    }

    public LiveData<String> getOutDate() {
        return outDate;
    }

    public LiveData<String> getTargetType() {
        return targetType;
    }

    public LiveData<String> getReturnReason() {
        return returnReason;
    }

    public LiveData<Long> getBatchId() {
        return batchId;
    }

    public LiveData<Long> getPenId() {
        return penId;
    }

    public LiveData<Long> getReceiverId() {
        return receiverId;
    }

    public LiveData<String> getTargetText() {
        return targetText;
    }

    public LiveData<String> getTargetHelper() {
        return targetHelper;
    }

    public LiveData<String> getReceiverText() {
        return receiverText;
    }

    public LiveData<String> getRecorderText() {
        return recorderText;
    }

    public LiveData<List<StockPenOptionVO>> getPens() {
        return pens;
    }

    public LiveData<List<StockBatchOptionVO>> getBatches() {
        return batches;
    }

    public LiveData<List<StockMemberVO>> getMembers() {
        return members;
    }

    public LiveData<CostAllocationVO> getAllocation() {
        return allocation;
    }

    public LiveData<String> getReturnAmount() {
        return returnAmount;
    }

    public LiveData<List<String>> getReturnToSuggest() {
        return returnToSuggest;
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

    public LiveData<PickerData> getDrugPicker() {
        return drugPicker;
    }

    public LiveData<PickerData> getLotPicker() {
        return lotPicker;
    }

    @Nullable
    public StockActionsVO actions() {
        StockOutboundVO r = record.getValue();
        return r == null ? null : r.actions;
    }

    @NonNull
    public String drugKeyword() {
        return drugKeyword;
    }

    public SingleLiveEvent<String> getSubmitted() {
        return submitted;
    }

    public SingleLiveEvent<Long> getConfirmDiscard() {
        return confirmDiscard;
    }

    public SingleLiveEvent<Long> getOpenScrap() {
        return openScrap;
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

    /** 领用下拉选项 */
    static final class Options {
        final List<StockPenOptionVO> pens;
        final List<StockBatchOptionVO> batches;
        final List<StockMemberVO> members;

        Options(List<StockPenOptionVO> pens, List<StockBatchOptionVO> batches, List<StockMemberVO> members) {
            this.pens = pens;
            this.batches = batches;
            this.members = members;
        }
    }
}
