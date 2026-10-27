package com.zhimu.slmp.today.ui.notice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmInviteService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.vo.MessageActionVO;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.model.vo.NoticePayload;
import com.zhimu.slmp.today.model.vo.TransferPayload;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.CalendarRange;
import com.zhimu.slmp.today.util.Texts;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * T07 通知详情（NoticeDetailActivity），技术文档 2.4.6：平台通知（重要 / 普通、配图、正文、已撤回）、所有权转让（接受 / 拒绝）、
 * 养殖场冻结说明。打开即由 API-T16 标为已读；菜单“删除消息”成功后返回 T06 提示“撤销”。
 */
public class NoticeDetailViewModel extends BaseViewModel {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    static final long REFRESH_INTERVAL_SECONDS = 60;

    private final MessageRepository repository;
    private final TodayServiceImpl todoService;

    private final MutableLiveData<MessageDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<NoticePayload> notice = new MutableLiveData<>();
    private final MutableLiveData<String> senderLine = new MutableLiveData<>("");
    private final MutableLiveData<String> revokedLine = new MutableLiveData<>();
    private final MutableLiveData<TransferPayload> transfer = new MutableLiveData<>();
    private final MutableLiveData<Boolean> canAccept = new MutableLiveData<>(false);
    private final MutableLiveData<String> frozenText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> acting = new MutableLiveData<>(false);

    private final SingleLiveEvent<FileVO> openPreview = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> finishWithDeleted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> confirmRejectTransfer = new SingleLiveEvent<>();

    private long messageId;
    private boolean visible;
    private boolean hasBeenVisible;
    private boolean refreshingDetail;
    private final SerialDisposable refreshTimer = new SerialDisposable();
    private final SerialDisposable refreshRequest = new SerialDisposable();

    @Inject
    public NoticeDetailViewModel(@NonNull MessageRepository repository, @NonNull TodayServiceImpl todoService) {
        this.repository = repository;
        this.todoService = todoService;
        disposables.add(refreshTimer);
        disposables.add(refreshRequest);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Object id = handle.get(RouterPath.EXTRA_MESSAGE_ID);
        long value = id instanceof Number ? ((Number) id).longValue() : 0;
        if (value == 0 && id != null) {
            try {
                value = Long.parseLong(String.valueOf(id));
            } catch (NumberFormatException ignored) {
                value = 0;
            }
        }
        if (value > 0) {
            load(value);
        }
    }

    // ===================== 对外 LiveData =====================

    public LiveData<MessageDetailVO> getDetail() {
        return detail;
    }

    public LiveData<NoticePayload> getNotice() {
        return notice;
    }

    public LiveData<String> getSenderLine() {
        return senderLine;
    }

    public LiveData<String> getRevokedLine() {
        return revokedLine;
    }

    public LiveData<TransferPayload> getTransfer() {
        return transfer;
    }

    public LiveData<Boolean> getCanAccept() {
        return canAccept;
    }

    public LiveData<String> getFrozenText() {
        return frozenText;
    }

    public LiveData<Boolean> getActing() {
        return acting;
    }

    public SingleLiveEvent<FileVO> getOpenPreview() {
        return openPreview;
    }

    public SingleLiveEvent<Long> getFinishWithDeleted() {
        return finishWithDeleted;
    }

    public SingleLiveEvent<Void> getConfirmRejectTransfer() {
        return confirmRejectTransfer;
    }

    // ===================== 加载 =====================

    public void load(long id) {
        messageId = id;
        load(repository.getDetail(id), this::apply);
    }

    /** 回到详情时立即核对状态；停留时每分钟同步，避免继续显示管理员已撤回的正文。 */
    public void onVisible() {
        if (visible) return;
        visible = true;
        if (hasBeenVisible) refreshVisible();
        hasBeenVisible = true;
        if (!isRevoked()) {
            refreshTimer.set(Observable.interval(REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(t -> refreshVisible()));
        }
    }

    /** 离开页面或切到后台后不再发起请求，也不让旧请求覆盖重新进入时的状态。 */
    public void onInvisible() {
        visible = false;
        refreshTimer.set(null);
        refreshRequest.set(null);
        refreshingDetail = false;
    }

    private boolean isRevoked() {
        MessageDetailVO current = detail.getValue();
        NoticePayload payload = notice.getValue();
        return current != null && (current.isRevoked() || payload != null && payload.isRevoked());
    }

    private void refreshVisible() {
        MessageDetailVO current = detail.getValue();
        if (!visible || refreshingDetail || messageId <= 0 || current == null || isRevoked()) return;
        refreshingDetail = true;
        refreshRequest.set(repository.getDetail(messageId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> refreshingDetail = false)
                .subscribe(this::apply, e -> {
                    // 后台同步失败保留已有内容，下一轮或回到页面时再尝试。
                }));
    }

    @Override
    public void retry() {
        if (messageId > 0) {
            load(messageId);
        }
    }

    /** 40401：U03 无权查看版式“记录不存在或无权查看” */
    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.NOT_FOUND) {
            setUiState(UiState.noPermission(e.getMessage(), e.getCode()));
            return;
        }
        super.applyErrorState(e);
    }

    @VisibleForTesting
    void apply(@NonNull MessageDetailVO vo) {
        detail.setValue(vo);
        todoService.setUnread(vo.getTotalUnread());
        NoticePayload n = vo.getNotice();
        notice.setValue(n);
        if (n != null) {
            senderLine.setValue(Texts.join(" · ", java.util.Arrays.asList(n.getSenderName(),
                    dateTimeWithWeek(n.getSentAt()) + " 发送")));
            if (n.isRevoked() || vo.isRevoked()) {
                revokedLine.setValue("平台通知 · " + shortDateTime(n.getSentAt()) + " 发送 · "
                        + shortDateTime(n.getRevokedAt()) + " 撤回");
            } else {
                revokedLine.setValue(null);
            }
        } else {
            senderLine.setValue("");
            revokedLine.setValue(null);
        }
        transfer.setValue(vo.getTransfer());
        MessageActionVO action = vo.getAction();
        canAccept.setValue(action != null && action.isCanAccept());
        if (MessageBriefVO.TYPE_FARM_FROZEN.equals(vo.getMsgType())) {
            String text = vo.payloadString("text");
            String reason = vo.payloadString("reason");
            frozenText.setValue(Texts.join("", java.util.Arrays.asList(text, reason == null ? null : "原因：" + reason)));
        } else {
            frozenText.setValue(null);
        }
        if (isRevoked()) refreshTimer.set(null);
    }

    /** “10月18日 周日 16:00” */
    @NonNull
    static String dateTimeWithWeek(@Nullable String datetime) {
        LocalDateTime t = DateFormatter.parseDateTime(datetime);
        if (t == null) {
            return Texts.orEmpty(datetime);
        }
        return CalendarRange.dayText(t.toLocalDate()) + " " + t.format(HM);
    }

    /** “10月17日 10:00” */
    @NonNull
    static String shortDateTime(@Nullable String datetime) {
        LocalDateTime t = DateFormatter.parseDateTime(datetime);
        if (t == null) {
            return Texts.orEmpty(datetime);
        }
        return t.getMonthValue() + "月" + t.getDayOfMonth() + "日 " + t.format(HM);
    }

    // ===================== 操作 =====================

    public void onImageClick() {
        NoticePayload n = notice.getValue();
        if (n != null && n.getImage() != null) {
            openPreview.setValue(n.getImage());
        }
    }

    /** 菜单“删除消息”（API-T17） */
    public void delete() {
        if (messageId <= 0) {
            return;
        }
        launch(repository.delete(messageId), r -> finishWithDeleted.setValue(messageId));
    }

    public void acceptTransfer() {
        MessageActionVO action = detail.getValue() == null ? null : detail.getValue().getAction();
        IFarmInviteService service = ServiceHub.get(IFarmInviteService.class);
        if (action == null || Boolean.TRUE.equals(acting.getValue())) {
            return;
        }
        if (service == null) {
            showMessage(Router.MSG_NOT_READY);
            return;
        }
        acting.setValue(true);
        launch(service.acceptTransfer(action.getActionId()), ctx -> {
            acting.setValue(false);
            TransferPayload t = transfer.getValue();
            showMessage("已接收" + (t == null ? "" : Texts.orEmpty(t.getFarmName())) + "的所有权");
            retry();
        }, e -> {
            acting.setValue(false);
            handleError(e);
        });
    }

    public void onRejectTransferClick() {
        if (!Boolean.TRUE.equals(acting.getValue())) {
            confirmRejectTransfer.call();
        }
    }

    public void rejectTransfer() {
        MessageActionVO action = detail.getValue() == null ? null : detail.getValue().getAction();
        IFarmInviteService service = ServiceHub.get(IFarmInviteService.class);
        if (action == null) {
            return;
        }
        if (service == null) {
            showMessage(Router.MSG_NOT_READY);
            return;
        }
        acting.setValue(true);
        launch(service.rejectTransfer(action.getActionId()), () -> {
            acting.setValue(false);
            showMessage("已拒绝转让");
            retry();
        }, e -> {
            acting.setValue(false);
            handleError(e);
        });
    }

    /** 弹窗内操作无权时只提示 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }
}
