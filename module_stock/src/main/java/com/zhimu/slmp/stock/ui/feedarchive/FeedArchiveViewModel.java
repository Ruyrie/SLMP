package com.zhimu.slmp.stock.ui.feedarchive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.data.DictTypes;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.request.FeedSaveRequest;
import com.zhimu.slmp.stock.model.vo.FeedDetailVO;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.util.StockCalc;
import com.zhimu.slmp.stock.util.StockTexts;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * M02 饲料档案（技术准备文档 2.4.3）：feed_id 为空时新增（API-M05），否则编辑（API-M04 → API-M06，带 edit_version）。
 * 类型取平台字典 FEED_TYPE，适用品类取 API-U09（待定事项 2：暂按公共字典缓存实现）；单位固定千克，已有出入库记录后锁定。
 * 40905 重名写名称框，40902 弹“内容已更新，请刷新后再修改”。不要求幂等键。
 */
public class FeedArchiveViewModel extends StockFormViewModel {

    public static final String F_NAME = "name";
    public static final String F_TYPE = "feed_type_code";
    public static final String F_SPEC = "spec";
    public static final String F_CATEGORY = "category_id";
    public static final String F_MIN = "min_stock";
    public static final String F_WARN = "warn_days";

    private final BaseDataRepository baseData;

    private final MutableLiveData<FeedDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<String> name = new MutableLiveData<>("");
    private final MutableLiveData<String> spec = new MutableLiveData<>("");
    private final MutableLiveData<String> minStock = new MutableLiveData<>("");
    private final MutableLiveData<String> warnDays = new MutableLiveData<>("7");
    private final MutableLiveData<String> feedTypeCode = new MutableLiveData<>();
    private final MutableLiveData<String> feedTypeText = new MutableLiveData<>("");
    private final MutableLiveData<Long> categoryId = new MutableLiveData<>();
    private final MutableLiveData<String> categoryText = new MutableLiveData<>("");
    private final MutableLiveData<String> unitHelper = new MutableLiveData<>("库存按这个单位结存");
    private final MutableLiveData<String> lastModified = new MutableLiveData<>();
    private final MutableLiveData<List<DictItemVO>> feedTypes = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<CategoryVO>> categories = new MutableLiveData<>(Collections.emptyList());

    private final SingleLiveEvent<String> saved = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> conflict = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeLog = new SingleLiveEvent<>();

    @Nullable
    private Long feedId;

    @Inject
    public FeedArchiveViewModel(@NonNull StockRepository repository, @NonNull StockDraftRepository drafts,
                                @NonNull BaseDataRepository baseData) {
        super(repository, drafts);
        this.baseData = baseData;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        super.onSavedStateAttached(handle);
        load(StockExtras.readId(handle.get(StockExtras.EXTRA_FEED_ID)));
    }

    /** 新增只取选项；编辑同时取档案 */
    public void load(@Nullable Long id) {
        feedId = id;
        Single<List<DictItemVO>> types = baseData.getDicts(DictTypes.FEED_TYPE, null)
                .onErrorReturnItem(Collections.emptyList());
        Single<List<CategoryVO>> cats = baseData.getCategories().onErrorReturnItem(Collections.emptyList());
        if (id == null) {
            load(Single.zip(types, cats, (t, c) -> new Init(null, t, c)), this::apply);
        } else {
            load(Single.zip(repository.getFeed(id), types, cats, Init::new), this::apply);
        }
    }

    @Override
    public void retry() {
        load(feedId);
    }

    private void apply(@NonNull Init init) {
        feedTypes.setValue(init.types);
        categories.setValue(init.categories);
        FeedDetailVO d = init.detail;
        detail.setValue(d);
        if (d != null) {
            name.setValue(StockTexts.nz(d.name));
            spec.setValue(StockTexts.nz(d.spec));
            minStock.setValue(StockCalc.plain(d.minStock));
            warnDays.setValue(d.warnDays == null ? "7" : String.valueOf(d.warnDays));
            feedTypeCode.setValue(d.feedTypeCode);
            feedTypeText.setValue(StockTexts.nz(d.feedTypeText));
            categoryId.setValue(d.categoryId);
            categoryText.setValue(StockTexts.nz(d.categoryText));
            unitHelper.setValue(d.unitLocked ? "已有出入库记录，单位不能修改" : "库存按这个单位结存");
            String when = DateFormatter.formatDateTime(d.updatedAt);
            lastModified.setValue(d.updatedAt == null ? null
                    : "上次修改：" + StockTexts.join(StockTexts.DOT, when, d.updatedByName));
        }
        fieldErrors.setValue(Collections.emptyMap());
        markClean();
    }

    public boolean isEdit() {
        return feedId != null;
    }

    // ===================== 选择 =====================

    public void onFeedTypePicked(@Nullable String code) {
        List<DictItemVO> list = feedTypes.getValue();
        if (code == null || list == null) {
            return;
        }
        for (DictItemVO t : list) {
            if (code.equals(t.getCode())) {
                feedTypeCode.setValue(t.getCode());
                feedTypeText.setValue(t.getName());
                clearFieldError(F_TYPE);
            }
        }
    }

    public void onCategoryPicked(@Nullable Long id) {
        List<CategoryVO> list = categories.getValue();
        if (id == null || list == null) {
            return;
        }
        for (CategoryVO c : list) {
            if (c.getId() == id) {
                categoryId.setValue(c.getId());
                categoryText.setValue(c.getName());
            }
        }
    }

    // ===================== 校验与保存 =====================

    @VisibleForTesting
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        String n = trim(name.getValue());
        if (n.isEmpty()) {
            e.put(F_NAME, "请填写名称");
        } else if (n.length() > 20) {
            e.put(F_NAME, "最多 20 个字");
        }
        if (feedTypeCode.getValue() == null) {
            e.put(F_TYPE, "请选择类型");
        }
        if (trim(spec.getValue()).length() > 20) {
            e.put(F_SPEC, "最多 20 个字");
        }
        String minText = trim(minStock.getValue());
        if (!minText.isEmpty()) {
            BigDecimal min = StockCalc.parse(minText);
            if (min == null || min.signum() < 0) {
                e.put(F_MIN, "不能小于 0");
            } else if (StockCalc.scaleOf(min) > 2) {
                e.put(F_MIN, "最多两位小数");
            }
        }
        BigDecimal w = StockCalc.parse(warnDays.getValue());
        if (w == null || !StockCalc.isInteger(w) || w.compareTo(BigDecimal.ONE) < 0
                || w.compareTo(BigDecimal.valueOf(365)) > 0) {
            e.put(F_WARN, "必须是大于 0 的整数");
        }
        return e;
    }

    @VisibleForTesting
    @NonNull
    FeedSaveRequest buildRequest() {
        FeedSaveRequest r = new FeedSaveRequest();
        r.name = trim(name.getValue());
        r.feedTypeCode = feedTypeCode.getValue();
        r.spec = emptyToNull(spec.getValue());
        FeedDetailVO d = detail.getValue();
        if (d != null) {
            r.packUnit = d.packUnit;
            r.packSize = d.packSize;
            r.editVersion = d.editVersion;
        }
        r.categoryId = categoryId.getValue();
        r.minStock = StockCalc.parse(minStock.getValue());
        BigDecimal w = StockCalc.parse(warnDays.getValue());
        r.warnDays = w == null ? null : w.intValue();
        return r;
    }

    /** 底部“保存” */
    public void save() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> e = validate();
        setErrors(e);
        if (!e.isEmpty()) {
            return;
        }
        FeedSaveRequest req = buildRequest();
        submitting.setValue(true);
        Single<FeedDetailVO> call = feedId == null ? repository.createFeed(req) : repository.updateFeed(feedId, req);
        launch(call, d -> {
            submitting.setValue(false);
            finished = true;
            saved.setValue(feedId == null ? "已新增饲料：" + req.name : "已保存" + req.name + "的档案");
        }, this::onSaveError);
    }

    private void onSaveError(@NonNull ApiException e) {
        submitting.setValue(false);
        if (e.getCode() == ErrorCodes.NAME_DUPLICATE) {
            putFieldError(F_NAME, e.getMessage());
        } else if (e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            conflict.setValue(e.getMessage());
        } else {
            handleError(e);
        }
    }

    /** 对话框“刷新”：重取档案，未保存的修改丢弃 */
    public void onConflictRefresh() {
        resetContentLoaded();
        load(feedId);
    }

    public void onChangeLog() {
        if (feedId != null) {
            openChangeLog.setValue(feedId);
        }
    }

    @NonNull
    @Override
    protected Map<String, String> draftFields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(F_NAME, name.getValue());
        m.put(F_TYPE, feedTypeCode.getValue());
        m.put(F_SPEC, spec.getValue());
        m.put(F_CATEGORY, categoryId.getValue() == null ? null : String.valueOf(categoryId.getValue()));
        m.put(F_MIN, minStock.getValue());
        m.put(F_WARN, warnDays.getValue());
        return m;
    }

    // ===================== 对外 =====================

    public LiveData<FeedDetailVO> getDetail() {
        return detail;
    }

    public MutableLiveData<String> getName() {
        return name;
    }

    public MutableLiveData<String> getSpec() {
        return spec;
    }

    public MutableLiveData<String> getMinStock() {
        return minStock;
    }

    public MutableLiveData<String> getWarnDays() {
        return warnDays;
    }

    public LiveData<String> getFeedTypeCode() {
        return feedTypeCode;
    }

    public LiveData<String> getFeedTypeText() {
        return feedTypeText;
    }

    public LiveData<Long> getCategoryId() {
        return categoryId;
    }

    public LiveData<String> getCategoryText() {
        return categoryText;
    }

    public LiveData<String> getUnitHelper() {
        return unitHelper;
    }

    public LiveData<String> getLastModified() {
        return lastModified;
    }

    public LiveData<List<DictItemVO>> getFeedTypes() {
        return feedTypes;
    }

    public LiveData<List<CategoryVO>> getCategories() {
        return categories;
    }

    public SingleLiveEvent<String> getSaved() {
        return saved;
    }

    public SingleLiveEvent<String> getConflict() {
        return conflict;
    }

    public SingleLiveEvent<Long> getOpenChangeLog() {
        return openChangeLog;
    }

    /** 加载结果 */
    static final class Init {
        @Nullable
        final FeedDetailVO detail;
        final List<DictItemVO> types;
        final List<CategoryVO> categories;

        Init(@Nullable FeedDetailVO detail, List<DictItemVO> types, List<CategoryVO> categories) {
            this.detail = detail;
            this.types = types;
            this.categories = categories;
        }
    }
}
