package com.zhimu.slmp.stock.ui.drugarchive;

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
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.request.DrugSaveRequest;
import com.zhimu.slmp.stock.model.request.StockSettingRequest;
import com.zhimu.slmp.stock.model.vo.CatalogResultVO;
import com.zhimu.slmp.stock.model.vo.DrugArchiveItemVO;
import com.zhimu.slmp.stock.model.vo.DrugArchiveListResult;
import com.zhimu.slmp.stock.model.vo.DrugDetailVO;
import com.zhimu.slmp.stock.model.vo.StockSettingVO;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerItem;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockValidators;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockSettingDelegate;
import com.zhimu.slmp.stock.util.StockTexts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * M09 药品档案（技术准备文档 2.4.11，按需求补充）：
 * <ul>
 *     <li>新增：类别连接按钮组 → 名称面板（API-U11 目录搜索，300 ms；本场已建档的项按 API-M34 的 catalog_id 标“已建档”不可选，待定事项 3）</li>
 *     <li>编辑：API-M36；已有出入库记录（unit_locked）时类别和名称合并为只读药品卡、单位锁定；禁用药只读、不显示底部操作栏</li>
 *     <li>保存 API-M37 / M38（非疫苗 pack_size 取目录值）；40905 写名称框；40902 对话框“内容已更新，请刷新后再修改”</li>
 *     <li>临期天数行打开与 M05 相同的面板（API-M40 / M41），与本页表单互不影响</li>
 * </ul>
 */
public class DrugArchiveViewModel extends StockFormViewModel {

    public static final String F_CATALOG = "catalog_id";
    public static final String F_SPEC = "spec";
    public static final String F_UNIT = "pack_unit";
    public static final String F_MIN = "min_stock";
    public static final String PICKER_CATALOG = "stock_m09_catalog";

    private final StockSettingDelegate settings = new StockSettingDelegate();

    private final MutableLiveData<DrugDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<String> category = new MutableLiveData<>(StockEnums.CAT_VET_DRUG);
    private final MutableLiveData<Long> catalogId = new MutableLiveData<>();
    private final MutableLiveData<String> name = new MutableLiveData<>("");
    private final MutableLiveData<String> unit = new MutableLiveData<>();
    private final MutableLiveData<BigDecimal> packSize = new MutableLiveData<>();
    private final MutableLiveData<String> spec = new MutableLiveData<>("");
    private final MutableLiveData<String> packUnit = new MutableLiveData<>();
    private final MutableLiveData<String> minStock = new MutableLiveData<>("");
    private final MutableLiveData<List<String>> packUnitOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> locked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> banned = new MutableLiveData<>(false);
    private final MutableLiveData<String> lastModified = new MutableLiveData<>();
    private final MediatorLiveData<String> nameHelper = new MediatorLiveData<>();
    private final MutableLiveData<String> specHelper = new MutableLiveData<>();
    private final MutableLiveData<String> unitHelper = new MutableLiveData<>("入库、出库都按这个单位");
    private final MutableLiveData<String> nearDaysText = new MutableLiveData<>("临期天数：到期前 30 天");
    private final MutableLiveData<String> nearDaysSub = new MutableLiveData<>("按类别设置，对全部兽药生效");
    private final MutableLiveData<PickerData> catalogPicker = new MutableLiveData<>();
    private final MutableLiveData<Boolean> settingSaving = new MutableLiveData<>(false);

    private final SingleLiveEvent<DrugDetailVO> saved = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> savedMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> conflict = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> nearDaysSaved = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showNearDays = new SingleLiveEvent<>();

    @Nullable
    private Long drugId;
    @NonNull
    private String keyword = "";
    private final Set<Long> archivedCatalogIds = new HashSet<>();
    private final List<CatalogResultVO.Item> catalogItems = new ArrayList<>();

    @Inject
    public DrugArchiveViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts) {
        super(repository, drafts);
        nameHelper.setValue("只能从兽药目录中选择，禁用药不可选");
        nameHelper.addSource(category, c -> {
            nameHelper.setValue(StockEnums.CAT_DISINFECTANT.equals(c) ? "从平台的消毒剂字典中选择"
                    : "只能从兽药目录中选择，禁用药不可选");
            updateNearDays();
        });
        nameHelper.addSource(minStock, v -> clearFieldError(F_MIN));
        nameHelper.addSource(spec, v -> clearFieldError(F_SPEC));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        String kw = handle.get(StockExtras.EXTRA_KEYWORD);
        keyword = kw == null ? "" : kw.trim();
        String cat = handle.get(StockExtras.EXTRA_CATEGORY);
        if (StockEnums.CAT_VACCINE.equals(cat) || StockEnums.CAT_DISINFECTANT.equals(cat)
                || StockEnums.CAT_VET_DRUG.equals(cat)) {
            category.setValue(cat);
        }
        load(StockExtras.readId(handle.get(StockExtras.EXTRA_DRUG_ID)));
    }

    public void load(@Nullable Long id) {
        drugId = id;
        Single<StockSettingVO> st = repository.getStockSetting().onErrorReturnItem(new StockSettingVO());
        Single<DrugArchiveListResult> archives = repository.listDrugs(null, null)
                .onErrorReturnItem(new DrugArchiveListResult());
        if (id == null) {
            load(Single.zip(st, archives, (s, a) -> new Init(null, s, a)), this::apply);
        } else {
            load(Single.zip(repository.getDrug(id), st, archives, Init::new), this::apply);
        }
    }

    @Override
    public void retry() {
        load(drugId);
    }

    private void apply(@NonNull Init init) {
        StockSettingVO s = init.setting;
        settings.apply(s.version == 0 && s.nearDaysVet == 0 ? null : s);
        archivedCatalogIds.clear();
        if (init.archives.list != null) {
            for (DrugArchiveItemVO a : init.archives.list) {
                if (a.catalogId != null) {
                    archivedCatalogIds.add(a.catalogId);
                }
            }
        }
        DrugDetailVO d = init.detail;
        detail.setValue(d);
        if (d != null && d.catalogId != null) {
            archivedCatalogIds.remove(d.catalogId);
        }
        if (d != null) {
            category.setValue(d.category);
            catalogId.setValue(d.catalogId);
            name.setValue(StockTexts.nz(d.name));
            unit.setValue(d.unit);
            packSize.setValue(d.packSize);
            spec.setValue(StockTexts.nz(d.spec));
            packUnit.setValue(d.packUnit);
            List<String> opts = d.packUnitOptions == null || d.packUnitOptions.isEmpty()
                    ? (d.packUnit == null ? Collections.emptyList() : Collections.singletonList(d.packUnit))
                    : d.packUnitOptions;
            packUnitOptions.setValue(opts);
            minStock.setValue(StockCalc.plain(d.minStock));
            locked.setValue(d.unitLocked);
            banned.setValue(d.banned);
            String when = DateFormatter.formatDateTime(d.updatedAt);
            lastModified.setValue(d.updatedAt == null ? null
                    : "上次修改：" + StockTexts.join(StockTexts.DOT, when, d.updatedByName));
        }
        updateHelpers();
        updateNearDays();
        fieldErrors.setValue(Collections.emptyMap());
        markClean();
    }

    public boolean isEdit() {
        return drugId != null;
    }

    // ===================== 类别与名称 =====================

    /** 切换类别：已选名称、规格、库存单位清空，临期天数行跟随 */
    public void setCategory(@NonNull String c) {
        if (c.equals(category.getValue()) || Boolean.TRUE.equals(locked.getValue())
                || Boolean.TRUE.equals(banned.getValue())) {
            return;
        }
        category.setValue(c);
        catalogId.setValue(null);
        name.setValue("");
        spec.setValue("");
        unit.setValue(null);
        packUnit.setValue(null);
        packSize.setValue(null);
        packUnitOptions.setValue(Collections.emptyList());
        catalogItems.clear();
        updateHelpers();
    }

    /** 打开名称面板（带 M08 的搜索词） */
    @NonNull
    public String openCatalogPicker() {
        searchCatalog(keyword);
        return keyword;
    }

    /** 目录搜索（已防抖 300 ms） */
    public void searchCatalog(@NonNull String kw) {
        keyword = kw;
        String c = category.getValue() == null ? StockEnums.CAT_VET_DRUG : category.getValue();
        launch(repository.searchCatalog(c, kw.isEmpty() ? null : kw), this::applyCatalog);
    }

    private void applyCatalog(@NonNull CatalogResultVO r) {
        catalogItems.clear();
        if (r.items != null) {
            catalogItems.addAll(r.items);
        }
        List<PickerItem> items = new ArrayList<>();
        Long sel = catalogId.getValue();
        for (CatalogResultVO.Item i : catalogItems) {
            boolean archived = archivedCatalogIds.contains(i.id);
            items.add(PickerItem.item(String.valueOf(i.id), StockTexts.nz(i.name))
                    .subtitle(StockTexts.join(StockTexts.DOT, i.spec, i.packUnit))
                    .tag(archived ? "已建档" : null, StatusTagView.NEUTRAL)
                    .disabled(archived)
                    .selected(sel != null && sel == i.id));
        }
        String error = r.bannedHit == null ? null : r.bannedHit.message;
        if (error != null) {
            error = error.replace("不能入库", "不能建档");
        }
        boolean dis = StockEnums.CAT_DISINFECTANT.equals(category.getValue());
        String empty = (dis ? "消毒剂字典" : "兽药目录") + "中没有找到“" + keyword + "”相关的"
                + (dis ? "消毒剂" : "药品") + "。目录由平台维护，暂时不能自行添加。";
        catalogPicker.setValue(new PickerData(items, error, keyword.isEmpty() ? null : empty, null));
    }

    /** 名称面板选中：回填名称、规格、库存单位 */
    public void onCatalogPicked(long id) {
        for (CatalogResultVO.Item i : catalogItems) {
            if (i.id != id || archivedCatalogIds.contains(i.id)) {
                continue;
            }
            catalogId.setValue(i.id);
            name.setValue(StockTexts.nz(i.name));
            spec.setValue(StockTexts.nz(i.spec));
            boolean vaccine = StockEnums.isVaccine(category.getValue());
            unit.setValue(vaccine ? StockEnums.UNIT_DOSE : i.measureUnit);
            packUnit.setValue(i.packUnit);
            packSize.setValue(i.packSize);
            packUnitOptions.setValue(i.packUnit == null ? Collections.emptyList()
                    : Collections.singletonList(i.packUnit));
            clearFieldError(F_CATALOG);
            clearFieldError(F_UNIT);
            updateHelpers();
        }
    }

    public void onPackUnitPicked(@Nullable String u) {
        if (u != null && !Boolean.TRUE.equals(locked.getValue())) {
            packUnit.setValue(u);
            clearFieldError(F_UNIT);
        }
    }

    private void updateHelpers() {
        boolean vaccine = StockEnums.isVaccine(category.getValue());
        BigDecimal size = packSize.getValue();
        String pu = packUnit.getValue();
        specHelper.setValue(vaccine && size != null && pu != null
                ? "按目录每" + pu + " " + NumberFormatter.formatNumber(size) + " 头份换算" : null);
        unitHelper.setValue(Boolean.TRUE.equals(locked.getValue()) ? "已有出入库记录，单位不能修改"
                : "入库、出库都按这个单位");
    }

    private void updateNearDays() {
        String c = category.getValue();
        nearDaysText.setValue("临期天数：到期前 " + settings.nearDaysOf(c) + " 天");
        nearDaysSub.setValue("按类别设置，对全部" + StockEnums.categoryText(c) + "生效");
    }

    // ===================== 保存 =====================

    @VisibleForTesting
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (catalogId.getValue() == null) {
            e.put(F_CATALOG, "请选择名称");
        }
        String s = StockValidators.optionalText(spec.getValue(), 20);
        if (s != null) {
            e.put(F_SPEC, s);
        }
        if (packUnit.getValue() == null) {
            e.put(F_UNIT, "请选择库存单位");
        }
        String minText = trim(minStock.getValue());
        if (!minText.isEmpty()) {
            BigDecimal min = StockCalc.parse(minText);
            if (min == null || min.signum() < 0) {
                e.put(F_MIN, "不能小于 0");
            } else if (StockCalc.scaleOf(min) > 2) {
                e.put(F_MIN, StockValidators.MSG_SCALE);
            }
        }
        return e;
    }

    @VisibleForTesting
    @NonNull
    DrugSaveRequest buildRequest() {
        DrugSaveRequest r = new DrugSaveRequest();
        boolean vaccine = StockEnums.isVaccine(category.getValue());
        if (!Boolean.TRUE.equals(locked.getValue())) {
            r.category = category.getValue();
            r.catalogId = catalogId.getValue();
        }
        r.spec = emptyToNull(spec.getValue());
        r.unit = vaccine ? StockEnums.UNIT_DOSE : unit.getValue();
        r.packUnit = packUnit.getValue();
        r.packSize = vaccine ? null : packSize.getValue();
        r.minStock = StockCalc.parse(minStock.getValue());
        r.minStockUnit = r.minStock == null ? null : StockEnums.INPUT_PACK;
        DrugDetailVO d = detail.getValue();
        r.version = d == null ? null : d.version;
        return r;
    }

    public void save() {
        if (Boolean.TRUE.equals(submitting.getValue()) || Boolean.TRUE.equals(banned.getValue())) {
            return;
        }
        Map<String, String> e = validate();
        setErrors(e);
        if (!e.isEmpty()) {
            return;
        }
        DrugSaveRequest req = buildRequest();
        String n = trim(name.getValue());
        submitting.setValue(true);
        Single<DrugDetailVO> call = drugId == null ? repository.createDrug(req) : repository.updateDrug(drugId, req);
        launch(call, vo -> {
            submitting.setValue(false);
            finished = true;
            savedMessage.setValue(drugId == null ? "已新增药品档案：" + n : "已保存" + n + "的档案");
            saved.setValue(vo);
        }, this::onSaveError);
    }

    private void onSaveError(@NonNull ApiException e) {
        submitting.setValue(false);
        switch (e.getCode()) {
            case ErrorCodes.NAME_DUPLICATE:
                putFieldError(F_CATALOG, e.getMessage());
                return;
            case ErrorCodes.BANNED_DRUG:
                if (isEdit()) {
                    showMessage(e.getMessage());
                    retry();
                } else {
                    putFieldError(F_CATALOG, e.getMessage());
                }
                return;
            case ErrorCodes.VERSION_CONFLICT:
                conflict.setValue(e.getMessage());
                return;
            default:
                handleError(e);
        }
    }

    public void onConflictRefresh() {
        resetContentLoaded();
        load(drugId);
    }

    public void onChangeLog() {
        if (drugId != null) {
            openChangeLog.setValue(drugId);
        }
    }

    // ===================== 临期天数 =====================

    public void openNearDays() {
        StockSettingVO s = settings.getSetting().getValue();
        settings.apply(s);
        showNearDays.call();
    }

    public void saveNearDays() {
        if (Boolean.TRUE.equals(settingSaving.getValue())) {
            return;
        }
        StockSettingRequest req = settings.buildRequest();
        if (req == null) {
            return;
        }
        settingSaving.setValue(true);
        launch(repository.saveStockSetting(req), () -> {
            settingSaving.setValue(false);
            settings.onSaved(req);
            updateNearDays();
            nearDaysSaved.setValue(StockSettingDelegate.MSG_SAVED);
        }, e -> {
            settingSaving.setValue(false);
            showMessage(e.getMessage());
        });
    }

    @NonNull
    @Override
    protected Map<String, String> draftFields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category", category.getValue());
        m.put(F_CATALOG, catalogId.getValue() == null ? null : String.valueOf(catalogId.getValue()));
        m.put(F_SPEC, spec.getValue());
        m.put(F_UNIT, packUnit.getValue());
        m.put(F_MIN, minStock.getValue());
        return m;
    }

    // ===================== 对外 =====================

    public StockSettingDelegate getSettings() {
        return settings;
    }

    public LiveData<Boolean> getSettingSaving() {
        return settingSaving;
    }

    public LiveData<DrugDetailVO> getDetail() {
        return detail;
    }

    public LiveData<String> getCategory() {
        return category;
    }

    public LiveData<Long> getCatalogId() {
        return catalogId;
    }

    public LiveData<String> getName() {
        return name;
    }

    public LiveData<String> getUnit() {
        return unit;
    }

    public MutableLiveData<String> getSpec() {
        return spec;
    }

    public LiveData<String> getPackUnit() {
        return packUnit;
    }

    public MutableLiveData<String> getMinStock() {
        return minStock;
    }

    public LiveData<List<String>> getPackUnitOptions() {
        return packUnitOptions;
    }

    public LiveData<Boolean> getLocked() {
        return locked;
    }

    public LiveData<Boolean> getBanned() {
        return banned;
    }

    public LiveData<String> getLastModified() {
        return lastModified;
    }

    public LiveData<String> getNameHelper() {
        return nameHelper;
    }

    public LiveData<String> getSpecHelper() {
        return specHelper;
    }

    public LiveData<String> getUnitHelper() {
        return unitHelper;
    }

    public LiveData<String> getNearDaysText() {
        return nearDaysText;
    }

    public LiveData<String> getNearDaysSub() {
        return nearDaysSub;
    }

    public LiveData<PickerData> getCatalogPicker() {
        return catalogPicker;
    }

    public SingleLiveEvent<DrugDetailVO> getSaved() {
        return saved;
    }

    public SingleLiveEvent<String> getSavedMessage() {
        return savedMessage;
    }

    public SingleLiveEvent<String> getConflict() {
        return conflict;
    }

    public SingleLiveEvent<String> getNearDaysSaved() {
        return nearDaysSaved;
    }

    public SingleLiveEvent<Long> getOpenChangeLog() {
        return openChangeLog;
    }

    public SingleLiveEvent<Void> getShowNearDays() {
        return showNearDays;
    }

    static final class Init {
        @Nullable
        final DrugDetailVO detail;
        final StockSettingVO setting;
        final DrugArchiveListResult archives;

        Init(@Nullable DrugDetailVO detail, StockSettingVO setting, DrugArchiveListResult archives) {
            this.detail = detail;
            this.setting = setting;
            this.archives = archives;
        }
    }
}
