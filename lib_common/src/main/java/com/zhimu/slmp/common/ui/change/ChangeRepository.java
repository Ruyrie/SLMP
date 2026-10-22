package com.zhimu.slmp.common.ui.change;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.common.ui.change.model.vo.ChangeFormVO;
import com.zhimu.slmp.common.ui.change.model.vo.ChangeRequestItemVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 修改申请仓库（U01、U02）：封装 ChangeApi。
 */
@FeatureScope
public class ChangeRepository {

    private final ChangeApi api;

    @Inject
    public ChangeRepository(@NonNull ChangeApi api) {
        this.api = api;
    }

    /** API-U01 */
    @NonNull
    public Single<ChangeFormVO> getForm(@NonNull String bizType, long bizId) {
        return api.getForm(bizType, bizId).compose(RxResult.unwrap());
    }

    /** API-U02 */
    @NonNull
    public Single<ChangeRequestItemVO> submit(@NonNull ChangeRequestSubmitRequest request, @NonNull String idemKey) {
        return api.submit(request, idemKey).compose(RxResult.unwrap());
    }

    /** API-U04 */
    @NonNull
    public Single<ChangeRequestItemVO> detail(long id) {
        return api.detail(id).compose(RxResult.unwrap());
    }

    /** API-U03 分页数据源；第 1 页到达时回调（读取 tab_counts、total） */
    @NonNull
    public PagedSource<ChangeRequestItemVO> listSource(@Nullable String status,
                                                       @Nullable Consumer<PageVO<ChangeRequestItemVO>> onFirstPage) {
        return new PagedSource<>((page, size) -> api.list(status, page, size).compose(RxResult.unwrap()), onFirstPage);
    }
}
