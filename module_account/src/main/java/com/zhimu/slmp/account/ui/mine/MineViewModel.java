package com.zhimu.slmp.account.ui.mine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.ProfileUpdateRequest;
import com.zhimu.slmp.account.model.vo.AccountFarmStatsVO;
import com.zhimu.slmp.account.model.vo.AccountOverviewVO;
import com.zhimu.slmp.common.model.vo.ReminderSummaryVO;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.session.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;

/**
 * A07 我的（2.4.7 节）：API-A15 汇总（随 X-Farm-Id 返回当前养殖场与修改申请计数）；修改姓名（API-A16）；退出登录。
 */
public class MineViewModel extends BaseViewModel {

    public static final String ENTRY_FARMS = "farms";
    public static final String ENTRY_CHANGE_REQUESTS = "change_requests";
    public static final String ENTRY_REMINDER = "reminder";
    public static final String ENTRY_SECURITY = "security";
    public static final String ENTRY_ABOUT = "about";

    public static final String MSG_NAME_SAVED = "已保存姓名";
    public static final String MSG_LOGGED_OUT = "已退出登录";
    public static final String FARM_EMPTY = "还没有养殖场，点按新建";

    private final AccountRepository repository;
    private final SessionManager sessionManager;

    private final MutableLiveData<AccountOverviewVO> overview = new MutableLiveData<>(null);
    private final LiveData<String> farmSubtitle;
    private final LiveData<List<String>> farmTags;
    private final LiveData<Boolean> showChangeRequest;
    private final LiveData<String> changeRequestTag;
    private final LiveData<String> reminderSubtitle;
    private final LiveData<Long> farmChanged;
    private final MutableLiveData<String> versionText = new MutableLiveData<>("");
    public final MutableLiveData<String> editName = new MutableLiveData<>("");
    private final MutableLiveData<String> nameError = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);

    private final SingleLiveEvent<Boolean> showNameSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> dismissNameSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> navigate = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> loggedOut = new SingleLiveEvent<>();
    /** 场长、员工显示“退出当前养殖场”（所有者需先转让所有权，不显示） */
    private final LiveData<Boolean> showQuitFarm;
    /** 退出行次文字“东山猪场 · 员工” */
    private final LiveData<String> quitFarmSubtitle;
    /** 打开退出确认对话框，值为当前养殖场名 */
    private final SingleLiveEvent<String> confirmQuitFarm = new SingleLiveEvent<>();
    /** 退出成功，值为提示文案“你已退出东山猪场” */
    private final SingleLiveEvent<String> quitFarmDone = new SingleLiveEvent<>();
    private final FarmContext farmContext;

    @Inject
    public MineViewModel(@NonNull AccountRepository repository, @NonNull SessionManager sessionManager,
                         @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.sessionManager = sessionManager;
        this.farmContext = farmContext;
        showQuitFarm = Transformations.map(farmContext.getContext(),
                ctx -> ctx != null && (FarmContextVO.ROLE_STAFF.equals(ctx.getRole())
                        || FarmContextVO.ROLE_MANAGER.equals(ctx.getRole())));
        quitFarmSubtitle = Transformations.map(farmContext.getContext(),
                ctx -> ctx == null ? null : ctx.getName() + " · " + ctx.getRoleText());
        farmSubtitle = Transformations.map(overview, o -> farmSubtitleOf(o == null ? null : o.getFarmStats()));
        farmTags = Transformations.map(overview, o -> farmTagsOf(o == null ? null : o.getFarmStats()));
        showChangeRequest = Transformations.map(farmContext.getContext(),
                ctx -> ctx == null || !FarmContextVO.ROLE_STAFF.equals(ctx.getRole()));
        changeRequestTag = Transformations.map(overview, o -> {
            Integer n = o == null ? null : o.getChangeRequestPendingCount();
            return n != null && n > 0 ? n + " 条待审核" : null;
        });
        reminderSubtitle = Transformations.map(overview, o -> reminderOf(o == null ? null : o.getReminderSummary()));
        farmChanged = Transformations.distinctUntilChanged(Transformations.map(farmContext.getContext(),
                ctx -> ctx == null ? 0L : ctx.getFarmId()));
        load();
    }

    @NonNull
    static String farmSubtitleOf(@Nullable AccountFarmStatsVO s) {
        if (s == null || s.getTotalCount() <= 0) {
            return FARM_EMPTY;
        }
        return "共 " + s.getTotalCount() + " 个";
    }

    @NonNull
    static List<String> farmTagsOf(@Nullable AccountFarmStatsVO s) {
        if (s == null) {
            return Collections.emptyList();
        }
        List<String> tags = new ArrayList<>();
        if (s.getPendingAuditCount() > 0) {
            tags.add(s.getPendingAuditCount() + " 个待审核");
        }
        if (s.getRejectedCount() > 0) {
            tags.add(s.getRejectedCount() + " 个已驳回");
        }
        return tags;
    }

    @Nullable
    static String reminderOf(@Nullable ReminderSummaryVO r) {
        if (r == null) {
            return null;
        }
        if (!r.isDigestEnabled()) {
            return "待办汇总已关闭";
        }
        String text = "待办汇总 " + AccountForm.text(r.getDigestTime());
        if (r.isQuietEnabled() && r.getQuietStart() != null && r.getQuietEnd() != null) {
            text += " · 免打扰 " + r.getQuietStart() + "–" + r.getQuietEnd();
        }
        return text;
    }

    // ===== 对外 LiveData =====

    public LiveData<AccountOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<String> getFarmSubtitle() {
        return farmSubtitle;
    }

    public LiveData<List<String>> getFarmTags() {
        return farmTags;
    }

    public LiveData<Boolean> getShowChangeRequest() {
        return showChangeRequest;
    }

    public LiveData<String> getChangeRequestTag() {
        return changeRequestTag;
    }

    public LiveData<String> getReminderSubtitle() {
        return reminderSubtitle;
    }

    public LiveData<Long> getFarmChanged() {
        return farmChanged;
    }

    public LiveData<String> getVersionText() {
        return versionText;
    }

    public LiveData<String> getNameError() {
        return nameError;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public SingleLiveEvent<Boolean> getShowNameSheet() {
        return showNameSheet;
    }

    public SingleLiveEvent<Boolean> getDismissNameSheet() {
        return dismissNameSheet;
    }

    public SingleLiveEvent<String> getNavigate() {
        return navigate;
    }

    public SingleLiveEvent<Boolean> getLoggedOut() {
        return loggedOut;
    }

    /** 是否还没有养殖场（点“我的养殖场”进入 F02） */
    public boolean hasNoFarm() {
        AccountOverviewVO o = overview.getValue();
        return o != null && o.getFarmStats() != null && o.getFarmStats().getTotalCount() <= 0;
    }

    // ===== 方法 =====

    /** 首次加载显示骨架屏；之后视为刷新，失败只提示 */
    public void load() {
        load(repository.getOverview(), overview::setValue);
    }

    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    /** View 读取 PackageInfo 后传入 */
    public void setVersionName(@Nullable String version) {
        versionText.setValue(version == null ? "" : "版本 " + version);
    }

    public void openEditName() {
        AccountOverviewVO o = overview.getValue();
        AccountUserVO u = o == null ? null : o.getUser();
        editName.setValue(u == null || u.getName() == null ? "" : u.getName());
        nameError.setValue(null);
        showNameSheet.setValue(true);
    }

    public void clearNameError() {
        if (nameError.getValue() != null) {
            nameError.setValue(null);
        }
    }

    public void saveName() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        String name = AccountForm.trim(editName.getValue());
        if (name.isEmpty()) {
            nameError.setValue(AccountForm.MSG_NAME_EMPTY);
            return;
        }
        if (AccountForm.length(name) > AccountForm.NAME_MAX) {
            nameError.setValue(AccountForm.MSG_NAME_TOO_LONG);
            return;
        }
        AccountOverviewVO o = overview.getValue();
        if (o == null) {
            return;
        }
        submitting.setValue(true);
        launch(repository.updateProfile(new ProfileUpdateRequest(name, o.getProfileVersion())), vo -> {
            submitting.setValue(false);
            AccountUserVO old = o.getUser() != null ? o.getUser() : sessionManager.currentUser();
            AccountUserVO updated = old == null
                    ? new AccountUserVO(0, vo.getName(), vo.getNameInitial(), null)
                    : old.withName(vo.getName(), vo.getNameInitial());
            o.applyProfile(updated, vo.getVersion());
            overview.setValue(o);
            sessionManager.updateUser(updated);
            dismissNameSheet.setValue(true);
            showMessage(MSG_NAME_SAVED);
        }, e -> {
            submitting.setValue(false);
            if (ErrorCodes.isDataChanged(e.getCode())) {
                dismissNameSheet.setValue(true);
            }
            handleError(e);
        });
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> fieldErrors) {
        String msg = fieldErrors.get("name");
        if (msg != null) {
            nameError.setValue(msg);
            return true;
        }
        return false;
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }

    /** 退出前回调 → API-A07（失败也继续）→ 清除本机数据 */
    public void logout() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        submitting.setValue(true);
        launch(repository.logoutAndClear(), () -> {
            submitting.setValue(false);
            loggedOut.setValue(true);
        }, e -> {
            submitting.setValue(false);
            loggedOut.setValue(true);
        });
    }

    /** 点“退出当前养殖场”：确认对话框（文案同 F05、F09 场长版） */
    public void onQuitFarmClick() {
        FarmContextVO ctx = farmContext.current();
        if (ctx != null) {
            confirmQuitFarm.setValue(ctx.getName());
        }
    }

    /** 确认退出：API-F27（经 IFarmContextService），成功后当前养殖场切到下一个，回到今日首页并提示 */
    public void quitFarm() {
        FarmContextVO ctx = farmContext.current();
        IFarmContextService service = ServiceHub.get(IFarmContextService.class);
        if (ctx == null || service == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        String name = ctx.getName();
        submitting.setValue(true);
        launch(service.quitFarm(ctx.getFarmId()), () -> {
            submitting.setValue(false);
            quitFarmDone.setValue("你已退出" + name);
        }, e -> {
            submitting.setValue(false);
            handleError(e);
        });
    }

    public LiveData<Boolean> getShowQuitFarm() {
        return showQuitFarm;
    }

    public LiveData<String> getQuitFarmSubtitle() {
        return quitFarmSubtitle;
    }

    public SingleLiveEvent<String> getConfirmQuitFarm() {
        return confirmQuitFarm;
    }

    public SingleLiveEvent<String> getQuitFarmDone() {
        return quitFarmDone;
    }

    public void onEntryClick(@NonNull String entry) {
        navigate.setValue(entry);
    }

    /** 当前手机号（退出后预填 A01 用 AppSettings.last_phone） */
    @Nullable
    public String currentPhoneMasked() {
        AccountUserVO u = sessionManager.currentUser();
        return u == null ? null : u.getPhoneMasked();
    }
}
