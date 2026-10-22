package com.zhimu.slmp.farm.ui.member;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.farm.model.vo.MemberItemVO;
import com.zhimu.slmp.farm.model.vo.MemberSummaryVO;
import com.zhimu.slmp.farm.repository.MemberRepository;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * F07 成员管理（模块02 技术文档 2.4.7、2.6.4）：API-F15 概况、API-F16 成员分页、API-F19 邀请分页；
 * 筛选 ALL / MANAGER / STAFF / INVITE；搜索 300 ms 防抖；子页返回后刷新并提示。
 */
public class MemberListViewModel extends BaseListViewModel<MemberListViewModel.Query, MemberItemVO> {

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_MANAGER = "MANAGER";
    public static final String FILTER_STAFF = "STAFF";
    public static final String FILTER_INVITE = "INVITE";
    public static final long KEYWORD_DEBOUNCE_MS = 300;
    public static final int KEYWORD_MAX = 20;

    /** 列表查询条件：筛选 + 关键字 */
    public static final class Query {
        @NonNull
        public final String filter;
        @NonNull
        public final String keyword;

        public Query(@NonNull String filter, @Nullable String keyword) {
            this.filter = filter;
            String k = keyword == null ? "" : keyword.trim();
            this.keyword = k.length() > KEYWORD_MAX ? k.substring(0, KEYWORD_MAX) : k;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Query)) {
                return false;
            }
            Query q = (Query) o;
            return filter.equals(q.filter) && keyword.equals(q.keyword);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filter, keyword);
        }
    }

    private final MemberRepository repository;
    private final FarmContext farmContext;

    private long farmId;
    private final MutableLiveData<MemberSummaryVO> summary = new MutableLiveData<>();
    private final LiveData<String> filter;
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final LiveData<PagingData<InviteItemVO>> invites;
    @Nullable
    private PagingSource<Integer, InviteItemVO> inviteSource;
    private final MutableLiveData<Integer> memberTotal = new MutableLiveData<>();
    private final MutableLiveData<Integer> inviteTotal = new MutableLiveData<>();
    private final MediatorLiveData<String> memberHeader = new MediatorLiveData<>("");
    private final MediatorLiveData<String> inviteHeader = new MediatorLiveData<>("");
    private final MediatorLiveData<List<String>> unassignedBanner = new MediatorLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> canInvite = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> onlyOwnerHint = new MediatorLiveData<>(false);

    private final SingleLiveEvent<Long> openDetail = new SingleLiveEvent<>();
    private final SingleLiveEvent<InviteItemVO> openInvite = new SingleLiveEvent<>();

    private final PublishSubject<String> keywordSubject = PublishSubject.create();
    /** 子页返回的“没有负责员工”的栏舍（概况刷新后以服务端为准） */
    private List<String> extraUnassigned = Collections.emptyList();

    @Inject
    public MemberListViewModel(@NonNull MemberRepository repository, @NonNull FarmContext farmContext) {
        super(new Query(FILTER_ALL, ""));
        this.repository = repository;
        this.farmContext = farmContext;
        filter = Transformations.map(getFilter(), q -> q == null ? FILTER_ALL : q.filter);
        invites = Transformations.switchMap(getFilter(), q -> {
            Pager<Integer, InviteItemVO> pager = new Pager<>(pagingConfig(), () -> {
                if (q != null && (FILTER_MANAGER.equals(q.filter) || FILTER_STAFF.equals(q.filter))) {
                    inviteSource = new EmptyPagingSource<>();
                    inviteTotal.postValue(0);
                } else {
                    inviteSource = repository.invitePagingSource(farmId, q == null ? null : q.keyword,
                            page -> inviteTotal.postValue(page.getTotal()));
                }
                return inviteSource;
            });
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });
        memberHeader.addSource(memberTotal, v -> updateHeaders());
        memberHeader.addSource(summary, v -> updateHeaders());
        inviteHeader.addSource(inviteTotal, v -> updateHeaders());
        unassignedBanner.addSource(summary, v -> updateBanner());
        onlyOwnerHint.addSource(summary, v -> updateOnlyOwner());
        onlyOwnerHint.addSource(canInvite, v -> updateOnlyOwner());

        disposables.add(keywordSubject
                .debounce(KEYWORD_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(k -> setFilter(new Query(currentFilterValue(), k))));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(RouterPath.EXTRA_FARM_ID);
        farmId = id != null && id > 0 ? id : farmContext.farmIdOrZero();
        Long current = farmContext.currentFarmId();
        canInvite.setValue(current != null && current == farmId && farmContext.permissions().isCanInvite());
        if (FarmUi.deniedByContext(farmContext, farmId, farmContext.permissions().isCanViewMembers())) {
            setUiState(FarmUi.noPermissionState());
            return;
        }
        load();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, MemberItemVO> createPagingSource(@Nullable Query q) {
        if (q != null && FILTER_INVITE.equals(q.filter)) {
            memberTotal.postValue(0);
            return new EmptyPagingSource<>();
        }
        String role = q == null || FILTER_ALL.equals(q.filter) ? FILTER_ALL : q.filter;
        return repository.memberPagingSource(farmId, role, q == null ? null : q.keyword,
                page -> memberTotal.postValue(page.getTotal()));
    }

    /** 整页加载（概况决定页面状态：40303 时 U03 无权查看） */
    public void load() {
        load(repository.getMemberSummary(farmId), summary::setValue);
    }

    @Override
    public void refresh() {
        super.refresh();
        if (inviteSource != null) {
            inviteSource.invalidate();
        }
        launch(repository.getMemberSummary(farmId), summary::setValue, e -> {
        });
    }

    @Override
    public void retry() {
        load();
        super.refresh();
        if (inviteSource != null) {
            inviteSource.invalidate();
        }
    }

    public void onFilterChanged(@NonNull String f) {
        setFilter(new Query(f, keyword.getValue()));
    }

    public void onKeywordChanged(@Nullable String text) {
        String k = text == null ? "" : text;
        keyword.setValue(k);
        keywordSubject.onNext(k.trim());
    }

    public void onMemberClick(@NonNull MemberItemVO item) {
        openDetail.setValue(item.getMemberId());
    }

    public void onInviteClick() {
        openInvite.setValue(null);
    }

    public void onReinviteClick(@NonNull InviteItemVO item) {
        openInvite.setValue(item);
    }

    /** 子页（F08、F09）返回：提示并刷新；unassignedPens 为移出后没有负责员工的栏舍 */
    public void onChildResult(@Nullable String snackText, @Nullable String[] unassignedPens) {
        if (snackText != null) {
            showMessage(snackText);
        }
        extraUnassigned = unassignedPens == null ? Collections.emptyList() : Arrays.asList(unassignedPens);
        updateBanner();
        refresh();
    }

    @NonNull
    private String currentFilterValue() {
        Query q = currentFilter();
        return q == null ? FILTER_ALL : q.filter;
    }

    private void updateHeaders() {
        MemberSummaryVO s = summary.getValue();
        Integer total = memberTotal.getValue();
        int members = total != null ? total : s == null ? 0 : s.getMemberTotal();
        memberHeader.setValue("成员 · " + members + " 人");
        Integer inv = inviteTotal.getValue();
        inviteHeader.setValue("邀请 · " + (inv != null ? inv : s == null ? 0 : s.getInviteCount()) + " 条");
    }

    private void updateBanner() {
        MemberSummaryVO s = summary.getValue();
        List<String> names = new ArrayList<>();
        if (s != null && s.isShowUnassignedBanner()) {
            for (PenBriefVO p : s.getUnassignedPens()) {
                if (p.getName() != null) {
                    names.add(p.getName());
                }
            }
        }
        if (names.isEmpty()) {
            names.addAll(extraUnassigned);
        }
        unassignedBanner.setValue(names);
    }

    private void updateOnlyOwner() {
        MemberSummaryVO s = summary.getValue();
        onlyOwnerHint.setValue(s != null && s.getMemberTotal() == 1 && Boolean.TRUE.equals(canInvite.getValue()));
    }

    public long getFarmId() {
        return farmId;
    }

    // ===== 对外 =====

    public LiveData<MemberSummaryVO> getSummary() {
        return summary;
    }

    public LiveData<String> getSelectedFilter() {
        return filter;
    }

    public MutableLiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<PagingData<MemberItemVO>> getMembers() {
        return getPagingData();
    }

    public LiveData<PagingData<InviteItemVO>> getInvites() {
        return invites;
    }

    public LiveData<String> getMemberHeader() {
        return memberHeader;
    }

    public LiveData<String> getInviteHeader() {
        return inviteHeader;
    }

    /** 没有负责员工的栏舍名（空列表时不显示横幅） */
    public LiveData<List<String>> getUnassignedBanner() {
        return unassignedBanner;
    }

    public LiveData<Boolean> getCanInvite() {
        return canInvite;
    }

    public LiveData<Boolean> getOnlyOwnerHint() {
        return onlyOwnerHint;
    }

    public SingleLiveEvent<Long> getOpenDetail() {
        return openDetail;
    }

    public SingleLiveEvent<InviteItemVO> getOpenInvite() {
        return openInvite;
    }
}
