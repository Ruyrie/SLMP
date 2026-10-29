package com.zhimu.slmp.herd.ui.penedit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.data.DictTypes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.PenCreateRequest;
import com.zhimu.slmp.herd.model.request.PenUpdateRequest;
import com.zhimu.slmp.herd.model.vo.PenDetailVO;
import com.zhimu.slmp.herd.model.vo.PenFormOptionsVO;
import com.zhimu.slmp.herd.ui.common.FormSupport;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * L05 新增 / 编辑栏舍。类型 Chip 来自 API-U10（PEN_TYPE，BaseDataRepository），适用品类来自 API-L10。
 */
public class PenEditViewModel extends BaseViewModel {

    /** L03 Snackbar“去分配”的 actionId */
    public static final String ACTION_ASSIGN = "herd_assign";

    public static final String F_CODE = "pen_code";
    public static final String F_NAME = "pen_name";
    public static final String F_TYPE = "pen_type";
    public static final String F_CATEGORY = "category_id";
    public static final String F_CAPACITY = "capacity";

    private static final String CODE_PATTERN = "^[A-Za-z0-9-]{1,10}$";

    private final PenRepository repository;
    private final BaseDataRepository baseData;
    private final FarmContext farmContext;

    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<PenFormOptionsVO> formOptions = new MutableLiveData<>();
    private final MutableLiveData<List<DictItemVO>> penTypes = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> penCode = new MutableLiveData<>("");
    private final MutableLiveData<String> penName = new MutableLiveData<>("");
    private final MutableLiveData<String> capacityText = new MutableLiveData<>("");
    private final MutableLiveData<String> penType = new MutableLiveData<>();
    private final MutableLiveData<Long> categoryId = new MutableLiveData<>();
    private final MutableLiveData<PenDetailVO> editing = new MutableLiveData<>();
    private final MediatorLiveData<String> capacityWarning = new MediatorLiveData<>();
    private final MediatorLiveData<String> unitText = new MediatorLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final LiveData<Boolean> canSubmit;
    private final SingleLiveEvent<UiMessage> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> discardEvent = new SingleLiveEvent<>();

    private final FormSupport.IdemKeeper idem = new FormSupport.IdemKeeper();
    @Nullable
    private Long penId;
    @Nullable
    private String initialSnapshot;

    /** 初始化加载结果 */
    private static final class Init {
        final PenFormOptionsVO options;
        final List<DictItemVO> types;
        @Nullable
        final PenDetailVO detail;

        Init(PenFormOptionsVO options, List<DictItemVO> types, @Nullable PenDetailVO detail) {
            this.options = options;
            this.types = types;
            this.detail = detail;
        }
    }

    @Inject
    public PenEditViewModel(@NonNull PenRepository repository, @NonNull BaseDataRepository baseData,
                            @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.baseData = baseData;
        this.farmContext = farmContext;
        capacityWarning.addSource(capacityText, t -> capacityWarning.setValue(computeWarning()));
        capacityWarning.addSource(editing, p -> capacityWarning.setValue(computeWarning()));
        unitText.addSource(categoryId, id -> unitText.setValue(computeUnit()));
        unitText.addSource(formOptions, o -> unitText.setValue(computeUnit()));
        canSubmit = Transformations.map(submitting, s -> !Boolean.TRUE.equals(s));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Object id = handle.get(RouterPath.EXTRA_PEN_ID);
        init(id instanceof Number ? ((Number) id).longValue() : null);
    }

    // ===== 对外 =====

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<PenFormOptionsVO> getFormOptions() {
        return formOptions;
    }

    public LiveData<List<DictItemVO>> getPenTypes() {
        return penTypes;
    }

    public MutableLiveData<String> getPenCode() {
        return penCode;
    }

    public MutableLiveData<String> getPenName() {
        return penName;
    }

    public MutableLiveData<String> getCapacityText() {
        return capacityText;
    }

    public MutableLiveData<String> getPenType() {
        return penType;
    }

    public MutableLiveData<Long> getCategoryId() {
        return categoryId;
    }

    public LiveData<String> getCapacityWarning() {
        return capacityWarning;
    }

    public LiveData<String> getUnitText() {
        return unitText;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public SingleLiveEvent<UiMessage> getFinishEvent() {
        return finishEvent;
    }

    public SingleLiveEvent<Void> getDiscardEvent() {
        return discardEvent;
    }

    // ===== 初始化 =====

    /** API-L10 + API-U10（PEN_TYPE）；编辑时另取 API-L08 带入原值 */
    public void init(@Nullable Long penId) {
        this.penId = penId;
        editMode.setValue(penId != null);
        Single<PenFormOptionsVO> options = repository.getFormOptions();
        Single<List<DictItemVO>> types = baseData.getDicts(DictTypes.PEN_TYPE, null);
        Single<Init> source = penId == null
                ? Single.zip(options, types, (o, t) -> new Init(o, t, null))
                : Single.zip(options, types, repository.getPenDetail(penId), Init::new);
        load(source, init -> {
            formOptions.setValue(init.options);
            penTypes.setValue(init.types);
            PenDetailVO d = init.detail;
            if (d != null) {
                editing.setValue(d);
                penCode.setValue(d.getPenCode());
                penName.setValue(d.getPenName());
                penType.setValue(d.getPenType());
                categoryId.setValue(d.getCategoryId());
                capacityText.setValue(String.valueOf(d.getCapacity()));
            }
            initialSnapshot = snapshot();
        });
    }

    @Override
    public void retry() {
        init(penId);
    }

    // ===== 派生 =====

    @Nullable
    private String computeWarning() {
        PenDetailVO d = editing.getValue();
        Integer cap = FormSupport.parseInt(capacityText.getValue());
        if (d == null || cap == null || cap <= 0 || d.getStockCount() <= cap) {
            return null;
        }
        String unit = computeUnit();
        return "当前在栏 " + HerdFormat.qty(d.getStockCount(), unit) + "，超过新容量 "
                + HerdFormat.qty(d.getStockCount() - cap, unit);
    }

    /** 容量单位：已选品类的单位；编辑时为栏舍原单位；新增还没选品类时按当前养殖场主品类（鸡场“只”），都取不到时“头” */
    @NonNull
    String computeUnit() {
        PenFormOptionsVO o = formOptions.getValue();
        PenFormOptionsVO.CategoryOption picked = categoryOf(o, categoryId.getValue());
        if (picked != null) {
            return unitOf(picked);
        }
        PenDetailVO d = editing.getValue();
        if (d != null) {
            return HerdFormat.unit(d.getUnitText());
        }
        FarmContextVO farm = farmContext.current();
        if (farm != null) {
            PenFormOptionsVO.CategoryOption main = categoryOf(o, farm.getMainCategoryId());
            if (main != null) {
                return unitOf(main);
            }
            if (farm.getCountUnit() != null) {
                return HerdFormat.unitOfCode(farm.getCountUnit());
            }
        }
        return HerdFormat.unit(null);
    }

    @Nullable
    private static PenFormOptionsVO.CategoryOption categoryOf(@Nullable PenFormOptionsVO o, @Nullable Long id) {
        if (o == null || id == null) {
            return null;
        }
        for (PenFormOptionsVO.CategoryOption c : o.getCategories()) {
            if (c.getCategoryId() == id) {
                return c;
            }
        }
        return null;
    }

    @NonNull
    private static String unitOf(@NonNull PenFormOptionsVO.CategoryOption c) {
        return c.getCountUnitText() != null ? c.getCountUnitText() : HerdFormat.unitOfCode(c.getCountUnit());
    }

    // ===== 校验与保存 =====

    public void clearFieldError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(editMode.getValue())) {
            String code = trim(penCode.getValue());
            if (code.isEmpty()) {
                e.put(F_CODE, "请填写栏舍编号");
            } else if (!code.matches(CODE_PATTERN)) {
                e.put(F_CODE, "栏舍编号最多 10 位，只能包含字母、数字和短横线");
            }
        }
        String name = trim(penName.getValue());
        if (name.isEmpty()) {
            e.put(F_NAME, "请填写栏舍名称");
        } else if (name.codePointCount(0, name.length()) > 20) {
            e.put(F_NAME, "栏舍名称最多 20 个字");
        }
        if (penType.getValue() == null) {
            e.put(F_TYPE, "请选择栏舍类型");
        }
        if (categoryId.getValue() == null) {
            e.put(F_CATEGORY, "请选择适用品类");
        }
        String capText = trim(capacityText.getValue());
        Integer cap = FormSupport.parseInt(capText);
        if (capText.isEmpty()) {
            e.put(F_CAPACITY, "请填写容量");
        } else if (cap == null || cap < 1 || cap > 999_999) {
            e.put(F_CAPACITY, "容量需为大于 0 的整数");
        }
        return e;
    }

    /** API-L11 新增（Idempotency-Key）/ API-L12 编辑 */
    public void save() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        String name = trim(penName.getValue());
        int capacity = Objects.requireNonNull(FormSupport.parseInt(capacityText.getValue()));
        long category = Objects.requireNonNull(categoryId.getValue());
        submitting.setValue(true);
        PenDetailVO d = editing.getValue();
        if (d != null) {
            PenUpdateRequest req = new PenUpdateRequest();
            req.penName = name;
            req.penType = penType.getValue();
            req.categoryId = category;
            req.capacity = capacity;
            req.version = d.getVersion();
            launch(repository.updatePen(d.getPenId(), req), vo -> {
                submitting.setValue(false);
                finishEvent.setValue(UiMessage.of("已保存栏舍信息"));
            }, this::onSaveError);
        } else {
            PenCreateRequest req = new PenCreateRequest();
            req.penCode = trim(penCode.getValue()).toUpperCase(Locale.ROOT);
            req.penName = name;
            req.penType = penType.getValue();
            req.categoryId = category;
            req.capacity = capacity;
            launch(repository.createPen(req, idem.keyFor(req)), vo -> {
                submitting.setValue(false);
                finishEvent.setValue(UiMessage.withAction("已新增 " + vo.getPenName(), "去分配", ACTION_ASSIGN));
            }, this::onSaveError);
        }
    }

    private void onSaveError(@NonNull Throwable t) {
        submitting.setValue(false);
        handleError(t);
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    /** 有改动时返回：弹“放弃这次登记？”；返回 true 表示已拦截 */
    public boolean onBackPressed() {
        if (initialSnapshot != null && !initialSnapshot.equals(snapshot())) {
            discardEvent.call();
            return true;
        }
        return false;
    }

    @NonNull
    private String snapshot() {
        return trim(penCode.getValue()) + "|" + trim(penName.getValue()) + "|" + penType.getValue() + "|"
                + categoryId.getValue() + "|" + trim(capacityText.getValue());
    }

    @NonNull
    private static String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }
}
