package com.zhimu.slmp.finance.ui.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.data.SettlementRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.FinanceRecordRequest;
import com.zhimu.slmp.finance.model.request.SubcategoryRequest;
import com.zhimu.slmp.finance.model.vo.DuplicateCheckVO;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionVO;
import com.zhimu.slmp.finance.model.vo.SubcategoryVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.FinanceFormat;
import com.zhimu.slmp.finance.util.FinanceIdemKeeper;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.finance.util.SaleAmountCalculator;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.UploadManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * E03 记一笔（新增 / 编辑 / 只读）：支出 / 收入、金额、分类与子类、日期、关联批次、说明（收入）、凭证照片；
 * 疑似重复检查防抖 500 毫秒（分类 code 为 FEED、DRUG、SALE），保存经幂等键与 API-E43 确认（技术准备文档 2.6.1）。
 */
public class FinanceRecordEditViewModel extends FinanceViewModel {

    public static final String F_AMOUNT = "amount";
    public static final String F_CATEGORY = "category_id";
    public static final String F_DATE = "record_date";
    public static final String F_NOTE = "note";
    public static final String MODE_VIEW = "VIEW";
    static final BigDecimal AMOUNT_MAX = new BigDecimal("99999999.99");
    static final int NOTE_MAX = 30;
    static final int SUB_NAME_MAX = 10;
    static final int SUB_MAX_COUNT = 30;
    static final int MAX_VOUCHERS = 6;
    static final long DUPLICATE_DEBOUNCE_MS = 500;

    private final FinanceRepository repository;
    private final SettlementRepository settlementRepository;
    private final UploadManager.Factory uploads;
    private final FinanceIdemKeeper idem = new FinanceIdemKeeper("record");

    /** 双向绑定 */
    public final MutableLiveData<String> recordType = new MutableLiveData<>(FinanceConst.EXPENSE);
    public final MutableLiveData<String> amountText = new MutableLiveData<>("");
    public final MutableLiveData<String> note = new MutableLiveData<>("");
    public final MutableLiveData<String> recordDate = new MutableLiveData<>(DateFormatter.todayString());
    public final MutableLiveData<Long> categoryId = new MutableLiveData<>();
    public final MutableLiveData<Long> subcategoryId = new MutableLiveData<>();
    public final MutableLiveData<Long> batchId = new MutableLiveData<>();

    private final MutableLiveData<List<FinanceCategoryVO>> categories = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<SubcategoryVO>> subcategories = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<SettlementOptionVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DuplicateCheckVO> duplicate = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<FinanceRecordDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> readOnly = new MutableLiveData<>(false);
    private final MutableLiveData<String> batchHelper = new MutableLiveData<>("");
    private final MutableLiveData<String> subcategoryError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> subcategorySubmitting = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> finishWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openDuplicate = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> showSubcategorySheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> dismissSubcategorySheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> confirmDiscard = new SingleLiveEvent<>();

    private final PublishSubject<Long> duplicateTrigger = PublishSubject.create();
    /** 本页新增的子类（E10 返回之外） */
    private final Map<Long, List<SubcategoryVO>> addedSubs = new HashMap<>();
    @Nullable
    private UploadManager photos;
    private long recordId;
    private boolean filling;

    @Inject
    public FinanceRecordEditViewModel(@NonNull FinanceRepository repository,
                                      @NonNull SettlementRepository settlementRepository,
                                      @NonNull FarmContext farm, @NonNull UploadManager.Factory uploads) {
        super(farm);
        this.repository = repository;
        this.settlementRepository = settlementRepository;
        this.uploads = uploads;
        canSubmit.addSource(amountText, v -> onFieldChanged());
        canSubmit.addSource(categoryId, v -> onFieldChanged());
        canSubmit.addSource(recordDate, v -> onFieldChanged());
        canSubmit.addSource(submitting, v -> recomputeCanSubmit());
        canSubmit.addSource(batchId, v -> updateBatchHelper());
        canSubmit.setValue(false);
        disposables.add(duplicateTrigger
                .debounce(DUPLICATE_DEBOUNCE_MS, TimeUnit.MILLISECONDS, Schedulers.computation())
                .switchMapSingle(seq -> buildDuplicateCheck()
                        .subscribeOn(Schedulers.io())
                        .onErrorReturnItem(new DuplicateCheckVO()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(d -> duplicate.setValue(d.isHit() ? d : null), e -> {
                }));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        photos = uploads.create(handle, "vouchers", MediaScene.VOUCHER, MAX_VOUCHERS);
        Long id = handle.get(RouterPath.EXTRA_RECORD_ID);
        recordId = id == null ? 0 : id;
        String type = handle.get(RouterPath.EXTRA_RECORD_TYPE);
        if (FinanceConst.INCOME.equals(type)) {
            recordType.setValue(FinanceConst.INCOME);
        }
        readOnly.setValue(MODE_VIEW.equals(handle.get(RouterPath.EXTRA_MODE)));
        editMode.setValue(recordId > 0);
        init();
    }

    // ===== 对外 LiveData =====

    public LiveData<List<FinanceCategoryVO>> getCategories() {
        return categories;
    }

    public LiveData<List<SubcategoryVO>> getSubcategories() {
        return subcategories;
    }

    public LiveData<List<SettlementOptionVO>> getBatchOptions() {
        return batchOptions;
    }

    public LiveData<DuplicateCheckVO> getDuplicate() {
        return duplicate;
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

    public LiveData<FinanceRecordDetailVO> getDetail() {
        return detail;
    }

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<Boolean> getReadOnly() {
        return readOnly;
    }

    public LiveData<String> getBatchHelper() {
        return batchHelper;
    }

    public LiveData<String> getSubcategoryError() {
        return subcategoryError;
    }

    public LiveData<Boolean> getSubcategorySubmitting() {
        return subcategorySubmitting;
    }

    public SingleLiveEvent<String> getFinishWithResult() {
        return finishWithResult;
    }

    public SingleLiveEvent<Long> getOpenDuplicate() {
        return openDuplicate;
    }

    public SingleLiveEvent<Long> getShowSubcategorySheet() {
        return showSubcategorySheet;
    }

    public SingleLiveEvent<Boolean> getDismissSubcategorySheet() {
        return dismissSubcategorySheet;
    }

    public SingleLiveEvent<Boolean> getConfirmDiscard() {
        return confirmDiscard;
    }

    @Nullable
    public UploadManager getPhotos() {
        return photos;
    }

    public boolean isExpense() {
        return !FinanceConst.INCOME.equals(recordType.getValue());
    }

    // ===== 加载 =====

    /** E10（当前方向）、E26（size = 100）并行；编辑态先取 E04 */
    void init() {
        if (denyStaff()) {
            return;
        }
        launch(settlementRepository.getOptions(null, 1, SettlementRepository.OPTION_SIZE),
                p -> batchOptions.setValue(p.getList()), e -> {
                });
        if (recordId > 0) {
            load(repository.getRecordDetail(recordId), this::fill);
        } else {
            loadCategories(false);
        }
    }

    @Override
    public void retry() {
        init();
    }

    private void fill(@NonNull FinanceRecordDetailVO d) {
        filling = true;
        detail.setValue(d);
        recordType.setValue(d.getRecordType());
        amountText.setValue(d.getAmount() == null ? "" : d.getAmount().stripTrailingZeros().toPlainString());
        recordDate.setValue(d.getRecordDate());
        note.setValue(d.getNote() == null ? "" : d.getNote());
        categoryId.setValue(d.getCategoryId());
        subcategoryId.setValue(d.getSubcategoryId());
        batchId.setValue(d.getBatchId());
        if (photos != null) {
            photos.setInitialFiles(d.getVouchers());
        }
        filling = false;
        loadCategories(true);
    }

    private void loadCategories(boolean keepSelection) {
        String direction = recordType.getValue();
        launch(repository.getCategories(direction), list -> {
            categories.setValue(list);
            if (!keepSelection) {
                categoryId.setValue(null);
                subcategoryId.setValue(null);
            }
            refreshSubcategories();
            updateBatchHelper();
            recomputeCanSubmit();
        });
    }

    // ===== 表单交互 =====

    /** 切换支出 / 收入：清空分类与子类，金额、日期、批次、照片保留（编辑态不可切换） */
    public void switchType(@NonNull String type) {
        if (Boolean.TRUE.equals(editMode.getValue()) || type.equals(recordType.getValue())) {
            return;
        }
        recordType.setValue(type);
        categoryId.setValue(null);
        subcategoryId.setValue(null);
        duplicate.setValue(null);
        idem.clear();
        loadCategories(false);
    }

    public void selectCategory(@Nullable Long id) {
        if (Objects.equals(id, categoryId.getValue())) {
            return;
        }
        categoryId.setValue(id);
        subcategoryId.setValue(null);
        refreshSubcategories();
        updateBatchHelper();
    }

    /** 子类单选，再次点按取消 */
    public void selectSubcategory(long id) {
        subcategoryId.setValue(Objects.equals(subcategoryId.getValue(), id) ? null : id);
        idem.clear();
    }

    public void selectBatch(@Nullable Long id) {
        batchId.setValue(id);
        idem.clear();
    }

    public void setRecordDate(@NonNull String date) {
        recordDate.setValue(date);
    }

    @Nullable
    public FinanceCategoryVO selectedCategory() {
        Long id = categoryId.getValue();
        if (id == null) {
            return null;
        }
        for (FinanceCategoryVO c : categories.getValue()) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private void refreshSubcategories() {
        FinanceCategoryVO c = selectedCategory();
        if (c == null || !isExpense()) {
            subcategories.setValue(Collections.emptyList());
            return;
        }
        List<SubcategoryVO> list = new ArrayList<>(c.getSubcategories());
        List<SubcategoryVO> added = addedSubs.get(c.getId());
        if (added != null) {
            list.addAll(added);
        }
        subcategories.setValue(list);
    }

    private void onFieldChanged() {
        recomputeCanSubmit();
        if (!filling) {
            idem.clear();
            scheduleDuplicateCheck();
        }
    }

    private void recomputeCanSubmit() {
        boolean ok = !Boolean.TRUE.equals(submitting.getValue()) && !Boolean.TRUE.equals(readOnly.getValue())
                && !isBlank(amountText.getValue()) && categoryId.getValue() != null && !isBlank(recordDate.getValue());
        if (!Objects.equals(canSubmit.getValue(), ok)) {
            canSubmit.setValue(ok);
        }
    }

    private void updateBatchHelper() {
        if (!isExpense()) {
            batchHelper.setValue("关联后计入批次收入");
            return;
        }
        FinanceCategoryVO c = selectedCategory();
        batchHelper.setValue(batchId.getValue() != null && c != null && c.isMarkOnly()
                ? "只做标记，成本按出库或引种单价计入批次" : "可选");
    }

    /** 分类为 FEED、DRUG、SALE 且金额、日期齐全时，停止输入 500 毫秒后调 API-E09 */
    void scheduleDuplicateCheck() {
        FinanceCategoryVO c = selectedCategory();
        BigDecimal amount = SaleAmountCalculator.parseDecimal(amountText.getValue());
        if (c == null || !isDuplicateCode(c.getCode()) || amount == null || amount.signum() <= 0
                || isBlank(recordDate.getValue())) {
            duplicate.setValue(null);
            return;
        }
        duplicateTrigger.onNext(System.nanoTime());
    }

    static boolean isDuplicateCode(@Nullable String code) {
        return FinanceConst.CODE_FEED.equals(code) || FinanceConst.CODE_DRUG.equals(code)
                || FinanceConst.CODE_SALE.equals(code);
    }

    @NonNull
    private Single<DuplicateCheckVO> buildDuplicateCheck() {
        FinanceCategoryVO c = selectedCategory();
        BigDecimal amount = SaleAmountCalculator.parseDecimal(amountText.getValue());
        String date = recordDate.getValue();
        if (c == null || amount == null || date == null) {
            return Single.just(new DuplicateCheckVO());
        }
        return repository.checkDuplicate(recordType.getValue(), c.getId(), amount, date,
                recordId > 0 ? recordId : null);
    }

    /** 横幅“查看”：进入 E04 并定位那条自动记录 */
    public void viewDuplicate() {
        DuplicateCheckVO d = duplicate.getValue();
        if (d == null || d.getRecordId() == null) {
            return;
        }
        open(NavTarget.of(RouterPath.E04).put(RouterPath.EXTRA_HIGHLIGHT_ID, d.getRecordId())
                .put(RouterPath.EXTRA_MONTH, FinanceFormat.monthOf(d.getRecordDate() == null ? recordDate.getValue()
                        : d.getRecordDate())));
    }

    /** 横幅文案 */
    @NonNull
    public static String duplicateText(@NonNull DuplicateCheckVO d) {
        return "今天已有一条金额相同的自动记录，" + (d.getCategoryName() == null ? "" : d.getCategoryName() + " ")
                + FinanceFormat.yuan(d.getAmount()) + (d.getSourceLabel() == null ? "" : "，" + d.getSourceLabel())
                + "。确认不是同一笔再保存。";
    }

    // ===== 子类 =====

    public void openSubcategorySheet() {
        FinanceCategoryVO c = selectedCategory();
        if (c == null) {
            return;
        }
        subcategoryError.setValue(null);
        showSubcategorySheet.setValue(c.getId());
    }

    /** 添加子类：去首尾空格 1–10 字；本地先查已满 30 个与重名（API-E11） */
    public void addSubcategory(@Nullable String rawName) {
        FinanceCategoryVO c = selectedCategory();
        if (c == null || Boolean.TRUE.equals(subcategorySubmitting.getValue())) {
            return;
        }
        String name = rawName == null ? "" : rawName.trim();
        String err = validateSubName(c.getName(), name, subcategories.getValue());
        subcategoryError.setValue(err);
        if (err != null) {
            return;
        }
        subcategorySubmitting.setValue(true);
        launch(repository.addSubcategory(new SubcategoryRequest(c.getId(), name), IdemKey.newKey()), sub -> {
            subcategorySubmitting.setValue(false);
            List<SubcategoryVO> added = addedSubs.get(c.getId());
            if (added == null) {
                added = new ArrayList<>();
                addedSubs.put(c.getId(), added);
            }
            added.add(sub);
            refreshSubcategories();
            subcategoryId.setValue(sub.getId());
            dismissSubcategorySheet.setValue(true);
            showMessage("已添加子类“" + sub.getName() + "”");
        }, e -> {
            subcategorySubmitting.setValue(false);
            if (e.getCode() == com.zhimu.slmp.base.error.ErrorCodes.NAME_DUPLICATE
                    || e.getCode() == com.zhimu.slmp.base.error.ErrorCodes.PARAM_INVALID) {
                subcategoryError.setValue(e.getMessage());
            } else {
                handleError(e);
            }
        });
    }

    /** E03、E05 共用的子类名称校验 */
    @Nullable
    public static String validateSubName(@NonNull String categoryName, @NonNull String name,
                                         @Nullable List<SubcategoryVO> existing) {
        if (name.isEmpty()) {
            return "请输入子类名称";
        }
        if (name.length() > SUB_NAME_MAX) {
            return "子类名称不能超过 10 个字";
        }
        if (existing != null) {
            for (SubcategoryVO s : existing) {
                if (name.equals(s.getName())) {
                    return categoryName + "下已有“" + name + "”";
                }
            }
            if (existing.size() >= SUB_MAX_COUNT) {
                return "每个分类最多添加 30 个子类";
            }
        }
        return null;
    }

    // ===== 保存 =====

    /** 本地校验（UI 设计文档 2.5 表单校验）；键为 JSON 字段 */
    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        String text = amountText.getValue() == null ? "" : amountText.getValue().trim();
        BigDecimal amount = SaleAmountCalculator.parseDecimal(text);
        if (text.isEmpty()) {
            e.put(F_AMOUNT, "请输入金额");
        } else if (amount == null || amount.signum() <= 0) {
            e.put(F_AMOUNT, "金额必须大于 0");
        } else if (amount.stripTrailingZeros().scale() > 2) {
            e.put(F_AMOUNT, "金额最多保留 2 位小数");
        } else if (amount.compareTo(AMOUNT_MAX) > 0) {
            e.put(F_AMOUNT, "金额不能超过 99,999,999.99 元");
        }
        if (categoryId.getValue() == null) {
            e.put(F_CATEGORY, "请选择分类");
        }
        LocalDate date = DateFormatter.parseDate(recordDate.getValue());
        if (date == null) {
            e.put(F_DATE, "请选择日期");
        } else if (date.isAfter(DateFormatter.today())) {
            e.put(F_DATE, "日期不能晚于今天");
        }
        if (!isExpense() && note.getValue() != null && note.getValue().trim().length() > NOTE_MAX) {
            e.put(F_NOTE, "说明不能超过 30 字");
        }
        return e;
    }

    @NonNull
    FinanceRecordRequest buildRequest() {
        boolean edit = recordId > 0;
        boolean expense = isExpense();
        String n = note.getValue() == null ? "" : note.getValue().trim();
        List<Long> files = photos == null ? Collections.emptyList() : photos.getFileIds();
        FinanceRecordDetailVO d = detail.getValue();
        return new FinanceRecordRequest(edit ? null : recordType.getValue(),
                SaleAmountCalculator.parseDecimal(amountText.getValue()), categoryId.getValue(),
                expense ? subcategoryId.getValue() : null, !expense && !n.isEmpty() ? n : null,
                recordDate.getValue(), batchId.getValue(), files.isEmpty() ? null : files,
                edit && d != null ? d.getVersion() : null);
    }

    public void save() {
        if (Boolean.TRUE.equals(submitting.getValue()) || Boolean.TRUE.equals(readOnly.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        if (photos != null) {
            if (photos.hasUploading()) {
                showMessage("凭证还在上传，请稍候再保存");
                return;
            }
            String block = photos.getSubmitBlockMessage();
            if (block != null) {
                showMessage(block);
                return;
            }
        }
        FinanceRecordRequest req = buildRequest();
        submitting.setValue(true);
        if (recordId > 0) {
            launch(repository.updateRecord(recordId, req), vo -> onSaved(vo, req), e -> onSaveError(e, null, req));
        } else {
            String key = idem.keyFor(req);
            launch(repository.createRecord(req, key), vo -> onSaved(vo, req), e -> onSaveError(e, key, req));
        }
    }

    private void onSaveError(@NonNull ApiException e, @Nullable String key, @NonNull FinanceRecordRequest req) {
        if (key != null && FinanceErrors.isTimeout(e)) {
            confirmSaved(key, req);
            return;
        }
        submitting.setValue(false);
        if (e.isNetwork()) {
            showMessage(e.keptMessage(FinanceErrors.MSG_NETWORK_KEPT));
            return;
        }
        idem.clear();
        handleError(e);
    }

    /** 提交超时：API-E43 查询，saved 为真按成功处理，为假提示并保留幂等键 */
    void confirmSaved(@NonNull String key, @NonNull FinanceRecordRequest req) {
        launch(repository.getSubmission(key), s -> {
            if (s.isSaved()) {
                onSaved(null, req);
            } else {
                submitting.setValue(false);
                showMessage(FinanceErrors.MSG_TIMEOUT);
            }
        }, e -> {
            submitting.setValue(false);
            showMessage(FinanceErrors.MSG_TIMEOUT);
        });
    }

    private void onSaved(@Nullable FinanceRecordVO vo, @NonNull FinanceRecordRequest req) {
        submitting.setValue(false);
        idem.clear();
        notifyTodoChanged(FinanceConst.LOG_FINANCE_RECORD, vo == null ? null : vo.getId());
        if (recordId > 0) {
            finishWithResult.setValue("已保存修改");
        } else {
            boolean income = FinanceConst.INCOME.equals(req.getRecordType());
            finishWithResult.setValue("已记一笔" + (income ? "收入 " : "支出 ") + FinanceFormat.yuan(req.getAmount()));
        }
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    // ===== 返回 =====

    /** 返回键：有未保存内容时询问“放弃填写的内容？” */
    public boolean onBack() {
        if (Boolean.TRUE.equals(readOnly.getValue()) || !isDirty()) {
            return false;
        }
        confirmDiscard.setValue(true);
        return true;
    }

    boolean isDirty() {
        FinanceRecordDetailVO d = detail.getValue();
        boolean hasPhotos = photos != null && !photos.current().isEmpty();
        if (d == null) {
            return !isBlank(amountText.getValue()) || categoryId.getValue() != null || batchId.getValue() != null
                    || !isBlank(note.getValue()) || hasPhotos;
        }
        BigDecimal a = SaleAmountCalculator.parseDecimal(amountText.getValue());
        return a == null || d.getAmount() == null || a.compareTo(d.getAmount()) != 0
                || !Objects.equals(categoryId.getValue(), d.getCategoryId())
                || !Objects.equals(subcategoryId.getValue(), d.getSubcategoryId())
                || !Objects.equals(batchId.getValue(), d.getBatchId())
                || !Objects.equals(recordDate.getValue(), d.getRecordDate())
                || !Objects.equals(emptyToNull(note.getValue()), emptyToNull(d.getNote()))
                || (photos != null && photos.current().size() != d.getVouchers().size());
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return isBlank(s) ? null : s.trim();
    }

    @Override
    protected void onCleared() {
        if (photos != null) {
            photos.release();
        }
        super.onCleared();
    }
}
