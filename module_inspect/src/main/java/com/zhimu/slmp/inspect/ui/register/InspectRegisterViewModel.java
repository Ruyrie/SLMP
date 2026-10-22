package com.zhimu.slmp.inspect.ui.register;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.data.InspectionRepository;
import com.zhimu.slmp.inspect.model.request.InspectionSubmitRequest;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.form.InspectFormViewModel;
import com.zhimu.slmp.media.upload.MediaQueueController;

import java.util.List;

import javax.inject.Inject;

/**
 * I02 登记巡检（含修改当天记录、只读查看，模块07 文档 2.4.3）：REGISTER 返回 already_registered 时以 MODIFY 与其
 * inspection_id 再取一次 API-I06；提交 API-I07（client_request_id 兼作幂等键）/ 修改 API-I08；submit(true) 成功后打开 I05。
 * 参数：pen_id、todo_id、mode（REGISTER / MODIFY / READONLY）、inspection_id。
 */
public class InspectRegisterViewModel extends InspectFormViewModel {

    @Nullable
    private String requestedMode;

    @Inject
    public InspectRegisterViewModel(@NonNull InspectionRepository repository, @NonNull FarmContext farmContext,
                                    @NonNull Context context, @NonNull MediaQueueController.Factory mediaFactory) {
        super(repository, farmContext, context, mediaFactory);
    }

    @NonNull
    @Override
    protected String pageCode() {
        return InspectExtras.PAGE_I02;
    }

    @Override
    protected boolean isBackfill() {
        return false;
    }

    @NonNull
    @Override
    protected String defaultTitle() {
        return "登记巡检";
    }

    @NonNull
    @Override
    protected String defaultPrimaryText() {
        return "提交";
    }

    @Override
    protected void init(@NonNull SavedStateHandle handle) {
        requestedMode = Args.getString(handle, RouterPath.EXTRA_MODE);
        start();
    }

    private void start() {
        Long id = inspectionId;
        if (InspectExtras.MODE_READONLY.equals(requestedMode) && id != null) {
            load(repository.getDetail(id), this::applyReadOnly);
        } else if (InspectExtras.MODE_MODIFY.equals(requestedMode) && id != null) {
            mode = InspectionFormVO.MODE_MODIFY;
            load(repository.getFormContext(penId, InspectionFormVO.MODE_MODIFY, null, id), this::applyContext);
        } else {
            load(repository.getFormContext(penId, InspectionFormVO.MODE_REGISTER, null, null), this::onRegisterContext);
        }
    }

    private void onRegisterContext(@NonNull InspectionFormVO ctx) {
        InspectionFormVO.RegisteredInfo reg = ctx.getAlreadyRegistered();
        if (reg == null) {
            applyContext(ctx);
            return;
        }
        // 当天已登记：转为修改这条记录
        setRegisteredNotice("今天 " + InspectFormat.time(reg.getRegisteredAt()) + " 已登记，本次保存会修改这条记录。");
        mode = InspectionFormVO.MODE_MODIFY;
        inspectionId = reg.getInspectionId();
        launch(repository.getFormContext(penId, InspectionFormVO.MODE_MODIFY, null, reg.getInspectionId()),
                this::applyContext);
    }

    @Override
    public void retry() {
        start();
    }

    @Override
    protected void submitNew(boolean thenReport, @NonNull List<InspectionSubmitRequest.ItemResultRequest> items,
                             @NonNull List<Long> fileIds) {
        InspectionSubmitRequest req = new InspectionSubmitRequest();
        req.penId = penId;
        fillCommon(req, items, fileIds);
        req.location = locationRequest();
        req.clientRequestId = keeper() == null ? null : keeper().idFor(req);
        launch(repository.register(req), r -> onSaved(r, thenReport), e -> onSubmitError(e, thenReport));
    }

    @Override
    protected boolean onBusinessError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            // 已由他人登记：带 inspection_id 时转为修改
            String id = e.dataString("inspection_id");
            showMessage(e.getMessage());
            if (id != null) {
                Long l = Args.toLong(id);
                if (l != null) {
                    inspectionId = l;
                    mode = InspectionFormVO.MODE_MODIFY;
                    launch(repository.getFormContext(penId, InspectionFormVO.MODE_MODIFY, null, l),
                            this::applyContext);
                }
            }
            return true;
        }
        return false;
    }
}
