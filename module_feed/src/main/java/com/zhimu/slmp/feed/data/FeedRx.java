package com.zhimu.slmp.feed.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleTransformer;

/** 写接口需要服务端 message（成功提示）：code 为 0 时保留整个 ApiResponse，否则转为 ApiException。 */
final class FeedRx {

    private FeedRx() {
    }

    @NonNull
    static <T> SingleTransformer<ApiResponse<T>, ApiResponse<T>> checked() {
        return upstream -> upstream
                .flatMap(resp -> resp.isSuccess() ? Single.just(resp) : Single.<ApiResponse<T>>error(
                        RxResult.fromResponse(resp)))
                .onErrorResumeNext(t -> Single.error(RxResult.toApiException(t)));
    }
}
