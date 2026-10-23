package com.zhimu.slmp.inspect.ui.backfill;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.data.InspectionRepository;
import com.zhimu.slmp.inspect.model.request.InspectionBackfillRequest;
import com.zhimu.slmp.inspect.model.request.InspectionSubmitRequest;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSubmitResultVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.form.InspectFormRules;
import com.zhimu.slmp.inspect.ui.form.InspectFormViewModel;
import com.zhimu.slmp.media.upload.MediaQueueController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * I03 补录巡检（所有者、场长，模块07 文档 2.4.4）：补录日期只能选 backfill_dates 中前 3 天内的漏巡日，实际执行人必选，
 * 可以手动标记异常但不能转上报；提交 API-I09；从 I04 修改前 3 天内的记录进入时日期锁定，提交 API-I08。
 * 参数：pen_id、inspect_date、date_locked、inspection_id。
 */
public class InspectBackfillViewModel extends InspectFormViewModel {

    public static final String MSG_DATE_OUT_OF_RANGE = "只能补录前 3 天内的漏巡，更早的记录需要提交修改申请";

    private final MutableLiveData<String> inspectDate = new MutableLiveData<>();
    private final MutableLiveData<List<String>> backfillDates = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> rangeText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> dateLocked = new MutableLiveData<>(false);

    @Inject
    public InspectBackfillViewModel(@NonNull InspectionRepository repository, @NonNull FarmContext farmContext,
                                    @NonNull Context context, @NonNull MediaQueueController.Factory mediaFactory) {
        super(repository, farmContext, context, mediaFactory);
    }

    @NonNull
    @Override
    protected String pageCode() {
        return InspectExtras.PAGE_I03;
    }

    @Override
    protected boolean isBackfill() {
        return true;
    }

    @NonNull
    @Override
    protected String defaultTitle() {
        return "补录巡检";
    }

    @NonNull
    @Override
    protected String defaultPrimaryText() {
        return "提交补录";
    }

    @Override
    protected void init(@NonNull SavedStateHandle handle) {
        inspectDate.setValue(Args.getString(handle, RouterPath.EXTRA_INSPECT_DATE));
        dateLocked.setValue(Args.getBoolean(handle, RouterPath.EXTRA_DATE_LOCKED) && inspectionId != null);
        start();
    }

    private void start() {
        if (Boolean.TRUE.equals(dateLocked.getValue()) && inspectionId != null) {
            mode = InspectionFormVO.MODE_MODIFY;
            load(repository.getFormContext(penId, InspectionFormVO.MODE_MODIFY, inspectDate.getValue(), inspectionId),
                    this::applyContext);
        } else {
            mode = InspectionFormVO.MODE_BACKFILL;
            load(repository.getFormContext(penId, InspectionFormVO.MODE_BACKFILL, inspectDate.getValue(), null),
                    this::applyContext);
        }
    }

    @Override
    public void retry() {
        start();
    }

    @Override
    protected void applyContext(@NonNull InspectionFormVO ctx) {
        super.applyContext(ctx);
        backfillDates.setValue(ctx.getBackfillDates());
        InspectionFormVO.DateRange range = ctx.getBackfillRange();
        rangeText.setValue(range == null ? "" : "限 " + InspectFormat.monthDay(range.getFrom()) + "–"
                + InspectFormat.monthDay(range.getTo()).replaceFirst("^\\d+月", ""));
        if (inspectDate.getValue() == null) {
            inspectDate.setValue(ctx.getInspectDate());
        }
    }

    /** “ZM2609-038”与“· 存栏 200 头 · 无负责员工”两行 */
    @NonNull
    @Override
    protected String buildSubtitle(@NonNull InspectionFormVO ctx) {
        InspectionFormVO.FormPen pen = ctx.getPen();
        List<String> names = pen == null ? Collections.emptyList() : pen.getResponsibleNames();
        return ctx.batchNoText() + "\n· 存栏 " + ctx.getStockCount() + " 头 · "
                + (names.isEmpty() ? "无负责员工" : "负责人 " + InspectFormat.join(names, ""));
    }

    // ===== 对外 =====

    public LiveData<String> getInspectDate() {
        return inspectDate;
    }

    public LiveData<List<String>> getBackfillDates() {
        return backfillDates;
    }

    /** “限 10月16日–18日” */
    public LiveData<String> getRangeText() {
        return rangeText;
    }

    public LiveData<Boolean> getDateLocked() {
        return dateLocked;
    }

    /** 选择补录日期：只能选 backfill_dates 中的日期；重新取该日的表单上下文（已选结果保留） */
    public void pickDate(@Nullable String date) {
        if (date == null || Boolean.TRUE.equals(dateLocked.getValue()) || Objects.equals(date, inspectDate.getValue())) {
            return;
        }
        List<String> allowed = backfillDates.getValue();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(date)) {
            putFieldError(InspectFormRules.F_DATE, MSG_DATE_OUT_OF_RANGE);
            return;
        }
        inspectDate.setValue(date);
        clearFieldError(InspectFormRules.F_DATE);
        launch(repository.getFormContext(penId, InspectionFormVO.MODE_BACKFILL, date, null), this::applyContext);
    }

    // ===== 提交 =====

    @NonNull
    @Override
    protected Map<String, String> validate() {
        Map<String, String> e = super.validate();
        if (!isModifyMode() && inspectDate.getValue() == null) {
            java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
            m.put(InspectFormRules.F_DATE, InspectFormRules.MSG_DATE_REQUIRED);
            m.putAll(e);
            return m;
        }
        return e;
    }

    @Override
    protected void submitNew(boolean thenReport, @NonNull List<InspectionSubmitRequest.ItemResultRequest> items,
                             @NonNull List<Long> fileIds) {
        InspectionSubmitRequest common = new InspectionSubmitRequest();
        fillCommon(common, items, fileIds);
        InspectionBackfillRequest req = new InspectionBackfillRequest();
        req.penId = penId;
        req.inspectDate = inspectDate.getValue();
        Long executor = getExecutorId().getValue();
        req.executorId = executor == null ? 0 : executor;
        req.itemResults = common.itemResults;
        req.isManualMarked = common.isManualMarked;
        req.manualReason = common.manualReason;
        req.remark = common.remark;
        req.location = locationRequest();
        req.mediaFileIds = common.mediaFileIds;
        req.clientRequestId = keeper() == null ? null : keeper().idFor(req);
        launch(repository.backfill(req), r -> onSaved(r, false), e -> onSubmitError(e, false));
    }

    @NonNull
    @Override
    protected String successMessage(@NonNull InspectionSubmitResultVO r) {
        String day = InspectFormat.monthDay(r.getInspectDate() != null ? r.getInspectDate() : inspectDate.getValue());
        if (isModifyMode()) {
            return "已修改 " + day + " 的巡检，修改记录已保留";
        }
        String pen = r.getPenName() != null ? r.getPenName() : getInfoTitle().getValue();
        return "已补录 " + pen + " " + day + " 的巡检";
    }

    @Override
    protected boolean onBusinessError(@NonNull ApiException e) {
        String day = InspectFormat.monthDay(inspectDate.getValue());
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            putFieldError(InspectFormRules.F_DATE, day + " 已有巡检记录，可在巡检历史中修改");
            return true;
        }
        if (e.getCode() == ErrorCodes.CHANGE_REQUEST_REQUIRED) {
            putFieldError(InspectFormRules.F_DATE, MSG_DATE_OUT_OF_RANGE);
            return true;
        }
        return false;
    }
}
