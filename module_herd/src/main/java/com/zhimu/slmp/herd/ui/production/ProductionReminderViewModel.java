package com.zhimu.slmp.herd.ui.production;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.model.vo.ProductionReminderItemVO;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * 生产提醒页（L01 “生产提醒 N 项”卡进入）：用 API-L01 的 reminders，内容与顺序同服务端返回（休药中、AI 诊断待处理、
 * 异常上报待诊断等）；没有提醒时显示空状态。养殖场未通过审核时没有提醒。
 */
public class ProductionReminderViewModel extends BaseViewModel {

    private final StockRepository repository;
    private final MutableLiveData<List<ProductionReminderItemVO>> reminders =
            new MutableLiveData<>(Collections.emptyList());
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    @Inject
    public ProductionReminderViewModel(@NonNull StockRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<ProductionReminderItemVO>> getReminders() {
        return reminders;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    /** API-L01；首次显示骨架屏，之后为刷新（失败保留旧列表） */
    public void load() {
        load(repository.getOverview().map(ProductionReminderViewModel::remindersOf), reminders::setValue);
    }

    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    @NonNull
    static List<ProductionReminderItemVO> remindersOf(@Nullable ProductionOverviewVO o) {
        return o == null || ProductionViewModel.isAuditPending(o) ? Collections.emptyList() : o.getReminders();
    }

    /** 提醒项点击（与原生产页列表相同） */
    public void onReminderClick(@NonNull ProductionReminderItemVO item) {
        NavTarget t = targetOf(item);
        if (t != null) {
            navEvent.setValue(t);
        }
    }

    /**
     * 未处理异常上报 → I06 诊断详情（带 report_id；缺 report_id 时退回 T04 待办详情）；
     * 其余按 route 打开 D05（带 batch_id）、M01 等；未知页面编号返回 null。
     */
    @Nullable
    static NavTarget targetOf(@NonNull ProductionReminderItemVO item) {
        if (ProductionReminderItemVO.TYPE_ABNORMAL_REPORT.equals(item.getReminderType())) {
            if (item.getReportId() != null) {
                return NavTarget.of(RouterPath.I06).put(RouterPath.EXTRA_REPORT_ID, item.getReportId());
            }
            return item.getTodoId() == null ? null
                    : NavTarget.of(RouterPath.T04).put(RouterPath.EXTRA_TODO_ID, item.getTodoId());
        }
        NavTarget t = NavTarget.ofPage(item.getRoute());
        return t == null ? null : t.put(RouterPath.EXTRA_BATCH_ID, item.getBatchId())
                .put(RouterPath.EXTRA_TODO_ID, item.getTodoId());
    }
}
