package com.zhimu.slmp.account.ui.mine;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
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
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
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
 * <p>
 * 首次加载显示骨架屏；A15 失败（含 40309、登录失效等全局处理的错误）时不停在骨架屏，也不整页切换为加载失败，
 * 而是用本机保存的账号信息降级显示，提醒设置、账号与安全、关于、退出登录照常可用，下拉可重新加载。
 * 没有养殖场或只有待审核 / 已驳回的养殖场时不显示“我的修改申请”。
 */
public class MineViewModel extends BaseViewModel {

    private static final String TAG = "MineViewModel";

    public static final String ENTRY_FARMS = "farms";
    public static final String ENTRY_CHANGE_REQUESTS = "change_requests";
    public static final String ENTRY_REMINDER = "reminder";
    public static final String ENTRY_SECURITY = "security";
    public static final String ENTRY_ABOUT = "about";

    public static final String MSG_NAME_SAVED = "已保存姓名";
    public static final String MSG_LOGGED_OUT = "已退出登录";
    public static final String MSG_AVATAR_UPDATED = "已更新头像";
    public static final String MSG_AVATAR_REMOVED = "已恢复默认头像";
    public static final String MSG_AVATAR_UPLOAD_FAILED = "头像上传失败，请稍后重试";
    public static final String MSG_AVATAR_CLEAR_FAILED = "恢复默认头像失败，请稍后重试";
    public static final String FARM_EMPTY = "还没有养殖场，点按新建";
    public static final String MSG_LOAD_FAILED = "个人信息加载失败，下拉可重新加载";
    public static final String MSG_PROFILE_NOT_LOADED = "个人信息还没有加载完成，请下拉刷新后再修改";

    private final AccountRepository repository;
    private final SessionManager sessionManager;

    private final MutableLiveData<AccountOverviewVO> overview = new MutableLiveData<>(null);
    /** 个人信息卡：优先取 A15 返回的用户，没有时用本机保存的登录用户 */
    private final MediatorLiveData<AccountUserVO> user = new MediatorLiveData<>();
    /** 下拉刷新指示（已显示内容后再加载） */
    private final MutableLiveData<Boolean> pullRefreshing = new MutableLiveData<>(false);
    /** 是否已显示过内容（含降级显示）；之后的加载视为刷新，不再显示骨架屏 */
    private boolean contentShown;
    private final LiveData<String> farmSubtitle;
    private final LiveData<List<String>> farmTags;
    private final MediatorLiveData<Boolean> showChangeRequest = new MediatorLiveData<>();
    private final LiveData<String> changeRequestTag;
    private final LiveData<String> reminderSubtitle;
    private final LiveData<Long> farmChanged;
    private final MutableLiveData<String> versionText = new MutableLiveData<>("");
    public final MutableLiveData<String> editName = new MutableLiveData<>("");
    private final MutableLiveData<String> nameError = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loggingOut = new MutableLiveData<>(false);
    /** 头像上传/清除中：个人信息卡显示环形进度，面板按钮不再响应 */
    private final MutableLiveData<Boolean> uploadingAvatar = new MutableLiveData<>(false);
    /** 真实头像地址；为空时个人信息卡回退显示姓名首字 */
    private final LiveData<String> avatarUrl;
    private final LiveData<Boolean> hasAvatar;

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
        user.addSource(overview, o -> user.setValue(pickUser()));
        user.addSource(sessionManager.getUser(), u -> user.setValue(pickUser()));
        avatarUrl = Transformations.map(user, u -> u == null ? null : u.getAvatarUrl());
        hasAvatar = Transformations.map(avatarUrl, url -> url != null && !url.isEmpty());
        // 概况未取到（降级显示）时养殖场数未知，副文字留空，不误显示“还没有养殖场”
        farmSubtitle = Transformations.map(overview, o -> o == null ? null : farmSubtitleOf(o.getFarmStats()));
        farmTags = Transformations.map(overview, o -> farmTagsOf(o == null ? null : o.getFarmStats()));
        showChangeRequest.addSource(farmContext.getContext(), ctx -> updateShowChangeRequest());
        showChangeRequest.addSource(overview, o -> updateShowChangeRequest());
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

    /**
     * 是否显示“我的修改申请”：当前养殖场中为员工时不显示（规范）；本人没有可用的养殖场
     * （没有养殖场，或只有待审核 / 已驳回的养殖场）时修改申请无从提交，也不显示。
     * 概况未取到时按当前养殖场判断。
     */
    static boolean showChangeRequestOf(@Nullable FarmContextVO ctx, @Nullable AccountOverviewVO o) {
        if (ctx != null && FarmContextVO.ROLE_STAFF.equals(ctx.getRole())) {
            return false;
        }
        AccountFarmStatsVO s = o == null ? null : o.getFarmStats();
        if (s != null) {
            return s.getTotalCount() - s.getPendingAuditCount() - s.getRejectedCount() > 0;
        }
        return ctx != null && !FarmContextVO.STATUS_PENDING.equals(ctx.getStatus())
                && !FarmContextVO.STATUS_REJECTED.equals(ctx.getStatus());
    }

    private void updateShowChangeRequest() {
        boolean show = showChangeRequestOf(farmContext.current(), overview.getValue());
        if (!Boolean.valueOf(show).equals(showChangeRequest.getValue())) {
            showChangeRequest.setValue(show);
        }
    }

    @Nullable
    private AccountUserVO pickUser() {
        AccountOverviewVO o = overview.getValue();
        if (o != null && o.getUser() != null) {
            return o.getUser();
        }
        return sessionManager.currentUser();
    }

    // ===== 对外 LiveData =====

    public LiveData<AccountOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<AccountUserVO> getUser() {
        return user;
    }

    /** 下拉刷新指示：本页自己管理加载状态，不用 BaseViewModel.load 的 refreshing */
    @Override
    public LiveData<Boolean> getRefreshing() {
        return pullRefreshing;
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

    /** 真实头像地址；为空表示显示姓名首字 */
    public LiveData<String> getAvatarUrl() {
        return avatarUrl;
    }

    /** 当前是否有真实头像（面板据此显示“恢复默认头像”） */
    public LiveData<Boolean> getHasAvatar() {
        return hasAvatar;
    }

    public LiveData<Boolean> getUploadingAvatar() {
        return uploadingAvatar;
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

    /**
     * 首次加载显示骨架屏；之后视为刷新，失败只提示。
     * 首次加载失败时降级显示（见 {@link #showDegraded}），保证退出登录等入口始终可用。
     */
    public void load() {
        final boolean refresh = contentShown;
        if (refresh) {
            pullRefreshing.setValue(true);
        } else {
            setUiState(UiState.loading());
        }
        launch(repository.getOverview(), o -> {
            pullRefreshing.setValue(false);
            // 先切到内容态再写入数据：即使某个观察者处理数据时出错，页面也不会停在骨架屏
            showContent();
            overview.setValue(o);
        }, e -> {
            pullRefreshing.setValue(false);
            if (refresh || contentShown) {
                handleError(e);
            } else {
                showDegraded(e);
            }
        });
    }

    private void showContent() {
        contentShown = true;
        setUiState(UiState.content());
    }

    /**
     * 首次加载失败：用本机保存的账号信息降级显示内容，个人信息卡、提醒设置、账号与安全、关于、退出登录照常可用；
     * 40309 仍交给全局处理进入强制步骤，登录失效由认证器统一回到登录页，这两类不再提示。
     * 本机也没有账号信息时才按 U03 显示“个人信息加载失败”。
     */
    private void showDegraded(@NonNull ApiException e) {
        int code = e.getCode();
        boolean handledGlobally = ErrorCodes.isAuthHandled(code) || code == ErrorCodes.FORCED_STEP_REQUIRED;
        Log.w(TAG, "我的页概况加载失败 code=" + code + " trace_id=" + e.getTraceId());
        if (code == ErrorCodes.FORCED_STEP_REQUIRED) {
            GlobalEvents.postForcedStep(e.dataString("next_step"));
        }
        if (sessionManager.currentUser() == null && !handledGlobally) {
            applyErrorState(e);
            return;
        }
        showContent();
        if (!handledGlobally) {
            showMessage(e.isNetwork() ? e.getMessage() : MSG_LOAD_FAILED);
        }
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
        if (o == null) {
            // 降级显示时没有资料版本号，不能提交修改
            showMessage(MSG_PROFILE_NOT_LOADED);
            return;
        }
        AccountUserVO u = o.getUser();
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

    // ===== 头像 =====

    /**
     * 选择图片后上传：POST files?scene=AVATAR → PUT account/avatar，
     * 成功用返回的 avatar_url 立即更新个人信息卡与会话缓存；失败提示固定文案（登录失效仍交给全局处理）。
     */
    public void uploadAvatar(@NonNull Uri uri) {
        if (Boolean.TRUE.equals(uploadingAvatar.getValue())) {
            return;
        }
        uploadingAvatar.setValue(true);
        launch(repository.uploadAvatar(uri), vo -> {
            uploadingAvatar.setValue(false);
            String avatarUrl = vo.getAvatarUrl();
            if (avatarUrl == null || avatarUrl.isEmpty() || !applyAvatarUrl(avatarUrl)) {
                // 返回值没带头像地址，或本机没有可合并的用户信息：用一次汇总请求拿到权威头像
                refresh();
            }
            showMessage(MSG_AVATAR_UPDATED);
        }, e -> {
            uploadingAvatar.setValue(false);
            handleAvatarError(e, MSG_AVATAR_UPLOAD_FAILED);
        });
    }

    /** 恢复默认头像：DELETE account/avatar，成功后立即回退为姓名首字 */
    public void clearAvatar() {
        if (Boolean.TRUE.equals(uploadingAvatar.getValue())) {
            return;
        }
        uploadingAvatar.setValue(true);
        launch(repository.clearAvatar(), vo -> {
            uploadingAvatar.setValue(false);
            if (!applyAvatarUrl(null)) {
                refresh();
            }
            showMessage(MSG_AVATAR_REMOVED);
        }, e -> {
            uploadingAvatar.setValue(false);
            handleAvatarError(e, MSG_AVATAR_CLEAR_FAILED);
        });
    }

    /**
     * 把头像地址写回 overview 与 SessionManager（改名后立即保存到本机，不用等下一次汇总）。
     *
     * @return 是否找到可更新的用户；false 时调用方需要重新加载汇总
     */
    private boolean applyAvatarUrl(@Nullable String newAvatarUrl) {
        AccountOverviewVO o = overview.getValue();
        AccountUserVO old = o != null && o.getUser() != null ? o.getUser() : sessionManager.currentUser();
        if (old == null) {
            return false;
        }
        AccountUserVO updated = old.withAvatarUrl(newAvatarUrl);
        if (o != null) {
            o.applyProfile(updated, o.getProfileVersion());
            overview.setValue(o);
        }
        sessionManager.updateUser(updated);
        return true;
    }

    private void handleAvatarError(@NonNull ApiException e, @NonNull String fallback) {
        Log.w(TAG, "头像操作失败 code=" + e.getCode() + " trace_id=" + e.getTraceId());
        if (ErrorCodes.isAuthHandled(e.getCode()) || e.getCode() == ErrorCodes.FORCED_STEP_REQUIRED) {
            handleError(e);
        } else {
            showMessage(fallback);
        }
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }

    /**
     * 退出前回调 → API-A07（失败、超时也继续）→ 清除本机数据。
     * 不论哪一步出错都要清掉本机登录态并回到登录页，没有养殖场、概况没加载出来时同样可用。
     */
    public void logout() {
        if (Boolean.TRUE.equals(loggingOut.getValue())) {
            return;
        }
        loggingOut.setValue(true);
        launch(repository.logoutAndClear(), () -> {
            loggingOut.setValue(false);
            loggedOut.setValue(true);
        }, e -> {
            Log.w(TAG, "退出登录过程出错，仍清除本机登录态", e);
            try {
                sessionManager.logout();
            } catch (RuntimeException ex) {
                Log.w(TAG, "清除本机登录态失败", ex);
            }
            loggingOut.setValue(false);
            loggedOut.setValue(true);
        });
    }

    /** 退出登录按钮的加载状态（与修改姓名、退出养殖场的 submitting 分开，互不挡住） */
    public LiveData<Boolean> getLoggingOut() {
        return loggingOut;
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
