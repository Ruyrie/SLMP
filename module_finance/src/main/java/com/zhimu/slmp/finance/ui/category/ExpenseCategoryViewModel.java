package com.zhimu.slmp.finance.ui.category;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.SubcategoryRequest;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.SubcategoryVO;
import com.zhimu.slmp.finance.ui.common.FinanceViewModel;
import com.zhimu.slmp.finance.ui.record.FinanceRecordEditViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

/**
 * E05 支出分类：平台维护的一级分类（不可改）与本场子类的增改删（API-E10–E13）。同一时间只展开一个一级分类。
 */
public class ExpenseCategoryViewModel extends FinanceViewModel {

    private final FinanceRepository repository;

    private final MutableLiveData<List<FinanceCategoryVO>> categories = new MutableLiveData<>(Collections.emptyList());
    /** 本页改动后的子类（一级分类 ID → 子类），覆盖 E10 返回 */
    private final MutableLiveData<Map<Long, List<SubcategoryVO>>> subs = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Long> expandedId = new MutableLiveData<>();
    /** 新子类输入框（双向） */
    public final MutableLiveData<String> newName = new MutableLiveData<>("");
    private final MutableLiveData<String> nameError = new MutableLiveData<>();
    private final MutableLiveData<String> renameError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> adding = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> renaming = new MutableLiveData<>(false);
    private final SingleLiveEvent<SubcategoryVO> showRenameSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> dismissRenameSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<SubcategoryVO> confirmDelete = new SingleLiveEvent<>();

    @Inject
    public ExpenseCategoryViewModel(@NonNull FinanceRepository repository, @NonNull FarmContext farm) {
        super(farm);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        load();
    }

    public void load() {
        if (denyStaff()) {
            return;
        }
        load(repository.getCategories(FinanceConst.EXPENSE), list -> {
            categories.setValue(list);
            Map<Long, List<SubcategoryVO>> map = new LinkedHashMap<>();
            for (FinanceCategoryVO c : list) {
                map.put(c.getId(), new ArrayList<>(c.getSubcategories()));
            }
            subs.setValue(map);
        });
    }

    @Override
    public void retry() {
        load();
    }

    public LiveData<List<FinanceCategoryVO>> getCategories() {
        return categories;
    }

    public LiveData<Map<Long, List<SubcategoryVO>>> getSubs() {
        return subs;
    }

    public LiveData<Long> getExpandedId() {
        return expandedId;
    }

    public LiveData<String> getNameError() {
        return nameError;
    }

    public LiveData<String> getRenameError() {
        return renameError;
    }

    public LiveData<Boolean> getAdding() {
        return adding;
    }

    public LiveData<Boolean> getRenaming() {
        return renaming;
    }

    public SingleLiveEvent<SubcategoryVO> getShowRenameSheet() {
        return showRenameSheet;
    }

    public SingleLiveEvent<Boolean> getDismissRenameSheet() {
        return dismissRenameSheet;
    }

    public SingleLiveEvent<SubcategoryVO> getConfirmDelete() {
        return confirmDelete;
    }

    @NonNull
    public List<SubcategoryVO> subsOf(long categoryId) {
        Map<Long, List<SubcategoryVO>> map = subs.getValue();
        List<SubcategoryVO> list = map == null ? null : map.get(categoryId);
        return list == null ? Collections.emptyList() : list;
    }

    /** “平台维护 · 3 个子类”“平台维护 · 电费、水费”“平台维护 · 暂无子类” */
    @NonNull
    public String subtitleOf(long categoryId) {
        List<SubcategoryVO> list = subsOf(categoryId);
        if (list.isEmpty()) {
            return "平台维护 · 暂无子类";
        }
        if (list.size() <= 2) {
            List<String> names = new ArrayList<>();
            for (SubcategoryVO s : list) {
                names.add(s.getName());
            }
            return "平台维护 · " + String.join("、", names);
        }
        return "平台维护 · " + list.size() + " 个子类";
    }

    /** 点一级分类：展开或收起，同一时间只展开一项 */
    public void toggle(long categoryId) {
        expandedId.setValue(Objects.equals(expandedId.getValue(), categoryId) ? null : categoryId);
        newName.setValue("");
        nameError.setValue(null);
    }

    @Nullable
    private FinanceCategoryVO category(long id) {
        for (FinanceCategoryVO c : categories.getValue()) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    /** “添加”：当前展开的一级分类下新增（API-E11） */
    public void add() {
        Long cid = expandedId.getValue();
        FinanceCategoryVO c = cid == null ? null : category(cid);
        if (c == null || Boolean.TRUE.equals(adding.getValue())) {
            return;
        }
        String name = newName.getValue() == null ? "" : newName.getValue().trim();
        String err = FinanceRecordEditViewModel.validateSubName(c.getName(), name, subsOf(cid));
        nameError.setValue(err);
        if (err != null) {
            return;
        }
        adding.setValue(true);
        launch(repository.addSubcategory(new SubcategoryRequest(cid, name), IdemKey.newKey()), sub -> {
            adding.setValue(false);
            List<SubcategoryVO> list = new ArrayList<>(subsOf(cid));
            list.add(sub);
            putSubs(cid, list);
            newName.setValue("");
            showMessage("已添加子类“" + sub.getName() + "”");
        }, e -> {
            adding.setValue(false);
            if (!applyNameError(e, nameError)) {
                handleError(e);
            }
        });
    }

    public void requestRename(@NonNull SubcategoryVO sub) {
        renameError.setValue(null);
        showRenameSheet.setValue(sub);
    }

    /** 改名（API-E12）：已有账目同步显示新名称 */
    public void rename(long categoryId, long subId, @Nullable String rawName) {
        FinanceCategoryVO c = category(categoryId);
        if (c == null || Boolean.TRUE.equals(renaming.getValue())) {
            return;
        }
        String name = rawName == null ? "" : rawName.trim();
        List<SubcategoryVO> others = new ArrayList<>();
        SubcategoryVO current = null;
        for (SubcategoryVO s : subsOf(categoryId)) {
            if (s.getId() == subId) {
                current = s;
            } else {
                others.add(s);
            }
        }
        if (current != null && name.equals(current.getName())) {
            dismissRenameSheet.setValue(true);
            return;
        }
        String err = FinanceRecordEditViewModel.validateSubName(c.getName(), name, null);
        if (err == null) {
            for (SubcategoryVO s : others) {
                if (name.equals(s.getName())) {
                    err = c.getName() + "下已有“" + name + "”";
                    break;
                }
            }
        }
        renameError.setValue(err);
        if (err != null) {
            return;
        }
        renaming.setValue(true);
        launch(repository.renameSubcategory(subId, new SubcategoryRequest(null, name)), sub -> {
            renaming.setValue(false);
            List<SubcategoryVO> list = new ArrayList<>();
            for (SubcategoryVO s : subsOf(categoryId)) {
                list.add(s.getId() == subId ? sub : s);
            }
            putSubs(categoryId, list);
            dismissRenameSheet.setValue(true);
            showMessage("已修改子类名称");
        }, e -> {
            renaming.setValue(false);
            if (!applyNameError(e, renameError)) {
                handleError(e);
            }
        });
    }

    public void requestDelete(@NonNull SubcategoryVO sub) {
        confirmDelete.setValue(sub);
    }

    /** 删除（API-E13）：已有账目保留原子类名称 */
    public void delete(long categoryId, long subId) {
        String name = null;
        for (SubcategoryVO s : subsOf(categoryId)) {
            if (s.getId() == subId) {
                name = s.getName();
            }
        }
        final String deleted = name;
        launch(repository.deleteSubcategory(subId), () -> {
            List<SubcategoryVO> list = new ArrayList<>();
            for (SubcategoryVO s : subsOf(categoryId)) {
                if (s.getId() != subId) {
                    list.add(s);
                }
            }
            putSubs(categoryId, list);
            showMessage("已删除子类“" + (deleted == null ? "" : deleted) + "”");
        });
    }

    /** 子类所属一级分类 */
    @Nullable
    public Long categoryOf(long subId) {
        Map<Long, List<SubcategoryVO>> map = subs.getValue();
        if (map == null) {
            return null;
        }
        for (Map.Entry<Long, List<SubcategoryVO>> e : map.entrySet()) {
            for (SubcategoryVO s : e.getValue()) {
                if (s.getId() == subId) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    private void putSubs(long categoryId, @NonNull List<SubcategoryVO> list) {
        Map<Long, List<SubcategoryVO>> map = new LinkedHashMap<>(subs.getValue() == null
                ? Collections.emptyMap() : subs.getValue());
        map.put(categoryId, list);
        subs.setValue(map);
    }

    private static boolean applyNameError(@NonNull ApiException e, @NonNull MutableLiveData<String> target) {
        if (e.getCode() == ErrorCodes.NAME_DUPLICATE || e.getCode() == ErrorCodes.PARAM_INVALID) {
            String msg = e.getFieldErrors().isEmpty() ? e.getMessage() : e.getFieldErrors().values().iterator().next();
            target.setValue(msg);
            return true;
        }
        return false;
    }
}
