package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterTable;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.ui.status.StatusActivity;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.today.model.vo.LinkVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 待办与消息的去向（技术文档 2.6.2）：未完成用 list_link，已完成或已忽略进 T04，“去登记”用 register_link；
 * need_switch 为真时先经 IFarmContextService.switchTo（API-F03）切换养殖场，成功后目标页提示“已切换到西岭鸡场”；
 * 切换失败进 U03，不打开目标页；route 无法识别时退回打开 T04。
 */
@FeatureScope
public class LinkNavigator {

    private final FarmContext farmContext;

    @Inject
    public LinkNavigator(@NonNull FarmContext farmContext) {
        this.farmContext = farmContext;
    }

    /** 模块02 的当前养殖场服务（单独运行时为空） */
    @Nullable
    protected IFarmContextService farmService() {
        return ServiceHub.get(IFarmContextService.class);
    }

    /** 点击待办 */
    @NonNull
    public Single<NavTarget> forTodo(@NonNull TodoItemVO item) {
        LinkVO link = item.getListLink();
        if (TodoTypeStyle.isClosed(item) || link == null || !isKnownRoute(link.getRoute())) {
            return Single.just(NavTarget.todoDetail(item.getTodoId()));
        }
        return forLink(link);
    }

    /** 点击“去登记” */
    @NonNull
    public Single<NavTarget> forRegister(@NonNull TodoItemVO item) {
        LinkVO link = item.getRegisterLink() != null ? item.getRegisterLink() : item.getListLink();
        if (link == null || !isKnownRoute(link.getRoute())) {
            return Single.error(new ApiException(ErrorCodes.UNKNOWN, Router.MSG_NOT_READY));
        }
        return forLink(link);
    }

    /** 按 LinkVO 打开；需要时先切换养殖场 */
    @NonNull
    public Single<NavTarget> forLink(@NonNull LinkVO link) {
        String route = link.getRoute() == null ? "" : link.getRoute();
        NavTarget target = new NavTarget(route, link.getParams(), null);
        if (!needSwitch(link)) {
            return Single.just(target);
        }
        IFarmContextService service = farmService();
        if (service == null) {
            return Single.error(new ApiException(ErrorCodes.UNKNOWN, Router.MSG_NOT_READY));
        }
        return service.switchTo(link.getFarmId())
                .map(ctx -> target.withSwitchTip(switchTip(ctx, link)))
                .onErrorResumeNext(t -> {
                    ApiException e = ApiException.from(t);
                    if (e.isNetwork() || ErrorCodes.isAuthHandled(e.getCode())) {
                        return Single.error(e);
                    }
                    return Single.just(switchFailed(e));
                });
    }

    /** 是否需要先切换养殖场：后端 need_switch，或链接所属养殖场与当前不同 */
    public boolean needSwitch(@NonNull LinkVO link) {
        if (link.isNeedSwitch()) {
            return true;
        }
        long current = farmContext.farmIdOrZero();
        return link.getFarmId() > 0 && current > 0 && link.getFarmId() != current;
    }

    @NonNull
    static String switchTip(@Nullable FarmContextVO ctx, @NonNull LinkVO link) {
        String name = ctx != null && ctx.getName() != null ? ctx.getName() : link.getFarmName();
        return "已切换到" + (name == null ? "" : name);
    }

    /** 切换失败：进入 U03（无权或冻结为无权查看版式，其余为加载失败版式） */
    @NonNull
    static NavTarget switchFailed(@NonNull ApiException e) {
        int code = e.getCode();
        if (ErrorCodes.isNoPermission(code) || code == ErrorCodes.FARM_FROZEN) {
            return NavTarget.noPermission(e.getMessage(), null, null);
        }
        Map<String, String> p = new HashMap<>();
        p.put(RouterPath.EXTRA_STATUS_TYPE, StatusActivity.TYPE_ERROR);
        p.put(RouterPath.EXTRA_MESSAGE, e.getMessage());
        if (e.getTraceId() != null) {
            p.put(RouterPath.EXTRA_TRACE_ID, e.getTraceId());
        }
        p.put(RouterPath.EXTRA_ERROR_CODE, String.valueOf(code));
        return new NavTarget("U03", p, null);
    }

    static boolean isKnownRoute(@Nullable String route) {
        return RouterTable.actionOf(route) != null;
    }
}
