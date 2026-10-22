package com.zhimu.slmp.farm.service;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.farm.model.vo.FarmListItemVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.ui.switcher.FarmSwitchSheet;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.Optional;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;

/**
 * 当前养殖场服务（模块02 技术文档 2.6.1）：
 * <ul>
 *     <li>showSwitcher：在宿主 Activity 上打开 F01 底部面板</li>
 *     <li>switchTo：API-F03 成功后 FarmContext.update（MainActivity 按新角色重建导航）；farmId 无效或遇到 40302、41001 时
 *     按 {@link #selectNext(long)} 重定当前养殖场，返回重定后的上下文（都没有时清空 FarmContext 并返回原错误）</li>
 *     <li>refreshContext：API-F02 刷新当前养殖场；没有当前养殖场或遇到 40302、41001 时同样重定</li>
 * </ul>
 */
@FeatureScope
public class FarmContextServiceImpl implements IFarmContextService {

    private final FarmRepository repository;
    private final FarmContext farmContext;

    @Inject
    public FarmContextServiceImpl(@NonNull FarmRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
    }

    @Override
    public void showSwitcher(@NonNull FragmentActivity host, boolean allowAllFarms, boolean inAllFarms) {
        FarmSwitchSheet.newInstance(allowAllFarms, inAllFarms).showSafely(host.getSupportFragmentManager());
    }

    @NonNull
    @Override
    public Single<FarmContextVO> switchTo(long farmId) {
        if (farmId <= 0) {
            return selectNext(0).flatMap(opt -> opt.isPresent()
                    ? Single.just(opt.get())
                    : Single.<FarmContextVO>error(new ApiException(ErrorCodes.NOT_FARM_MEMBER, MSG_NO_FARM)));
        }
        return repository.switchFarm(farmId)
                .doOnSuccess(farmContext::update)
                .onErrorResumeNext(t -> {
                    ApiException e = ApiException.from(t);
                    if (!needsReselect(e.getCode())) {
                        return Single.error(e);
                    }
                    return selectNext(farmId).flatMap(opt -> opt.isPresent()
                            ? Single.just(opt.get())
                            : Single.<FarmContextVO>error(e));
                });
    }

    @NonNull
    @Override
    public Completable refreshContext() {
        long farmId = farmContext.farmIdOrZero();
        if (farmId <= 0) {
            return selectNext(0).ignoreElement();
        }
        return repository.getContext(farmId)
                .doOnSuccess(farmContext::update)
                .ignoreElement()
                .onErrorResumeNext(t -> {
                    ApiException e = ApiException.from(t);
                    if (!needsReselect(e.getCode())) {
                        return Completable.error(e);
                    }
                    return selectNext(farmId).ignoreElement();
                });
    }

    @NonNull
    @Override
    public LiveData<FarmContextVO> context() {
        return farmContext.getContext();
    }

    /**
     * 重新选择当前养殖场（退出、删除当前养殖场后也调用）：API-F01 取第一个 can_switch 为 true 的养殖场（跳过 excludeFarmId）
     * 调 API-F03；只有待审核（PENDING / REJECTED）养殖场时用 API-F02 作为当前养殖场；都没有时清空 FarmContext。
     *
     * @return 新的当前养殖场；Optional.empty() 表示已清空
     */
    @NonNull
    public Single<Optional<FarmContextVO>> selectNext(long excludeFarmId) {
        return repository.getFarms(FarmRepository.RELATION_ALL, 1, PagedSource.PAGE_SIZE)
                .flatMap(page -> pickFrom(page, excludeFarmId))
                .doOnSuccess(opt -> {
                    if (opt.isPresent()) {
                        farmContext.update(opt.get());
                    } else {
                        farmContext.clear();
                    }
                });
    }

    @NonNull
    private Single<Optional<FarmContextVO>> pickFrom(@NonNull PageVO<FarmListItemVO> page, long excludeFarmId) {
        for (FarmListItemVO item : page.getList()) {
            if (item.getFarmId() != excludeFarmId && item.isCanSwitch()) {
                return repository.switchFarm(item.getFarmId()).map(Optional::of);
            }
        }
        for (FarmListItemVO item : page.getList()) {
            if (item.getFarmId() != excludeFarmId && isPendingAudit(item.getStatus())) {
                return repository.getContext(item.getFarmId()).map(Optional::of);
            }
        }
        return Single.just(Optional.empty());
    }

    static boolean needsReselect(int code) {
        return code == ErrorCodes.NOT_FARM_MEMBER || code == ErrorCodes.RECORD_DELETED;
    }

    static boolean isPendingAudit(String status) {
        return FarmContextVO.STATUS_PENDING.equals(status) || FarmContextVO.STATUS_REJECTED.equals(status);
    }

    static final String MSG_NO_FARM = "你还没有养殖场。新建一个养殖场，或在消息中心同意别人的邀请。";

    @NonNull
    @Override
    public Completable quitFarm(long farmId) {
        return repository.quitFarm(farmId).andThen(Completable.defer(() -> {
            FarmContextVO current = farmContext.current();
            if (current != null && current.getFarmId() == farmId) {
                return selectNext(farmId).ignoreElement();
            }
            return Completable.complete();
        }));
    }

    @NonNull
    @Override
    public Single<List<FarmBriefVO>> listMyFarms() {
        return repository.getFarms(FarmRepository.RELATION_ALL, 1, 100).map(page -> {
            List<FarmBriefVO> list = new ArrayList<>();
            for (FarmListItemVO item : page.getList()) {
                list.add(new FarmBriefVO(item.getFarmId(), item.getName(), item.getSpeciesText(), item.getStatus(),
                        item.getStatusText(), item.getRole()));
            }
            return list;
        });
    }
}
