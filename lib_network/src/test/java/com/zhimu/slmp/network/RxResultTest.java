package com.zhimu.slmp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.Test;

import java.net.SocketTimeoutException;

import io.reactivex.rxjava3.core.Single;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

public class RxResultTest {

    private static final MediaType JSON = MediaType.get("application/json");

    @Test
    public void successReturnsData() {
        String data = RxResult.unwrap(Single.just(new ApiResponse<>(0, "ok", "东山猪场", "t1"))).blockingGet();
        assertEquals("东山猪场", data);
    }

    @Test
    public void businessErrorBecomesApiException() {
        ApiException e = errorOf(RxResult.unwrap(Single.just(new ApiResponse<String>(40902, "数据已被他人修改，请刷新后重试", null, "t2"))));
        assertEquals(40902, e.getCode());
        assertEquals("数据已被他人修改，请刷新后重试", e.getMessage());
        assertEquals("t2", e.getTraceId());
    }

    @Test
    public void emptyDataIsError() {
        ApiException e = errorOf(RxResult.unwrap(Single.just(new ApiResponse<String>(0, "ok", null, "t3"))));
        assertEquals(ErrorCodes.EMPTY_DATA, e.getCode());
    }

    @Test
    public void httpErrorBodyParsedWithFieldErrors() {
        String body = "{\"code\":40001,\"message\":\"参数校验失败\",\"data\":{\"field_errors\":["
                + "{\"field\":\"phone\",\"message\":\"手机号格式不正确，请输入 11 位手机号\"}]},\"trace_id\":\"t4\"}";
        HttpException http = new HttpException(Response.error(400, ResponseBody.create(body, JSON)));
        ApiException e = errorOf(RxResult.unwrap(Single.<ApiResponse<String>>error(http)));
        assertEquals(40001, e.getCode());
        assertEquals("t4", e.getTraceId());
        assertEquals("手机号格式不正确，请输入 11 位手机号", e.getFieldErrors().get("phone"));
    }

    @Test
    public void sessionInvalidReason() {
        String body = "{\"code\":40104,\"message\":\"登录状态已失效\",\"data\":{\"reason\":\"PASSWORD_CHANGED\"},\"trace_id\":\"t5\"}";
        ApiException e = RxResult.toApiException(new HttpException(Response.error(401, ResponseBody.create(body, JSON))));
        assertEquals(ErrorCodes.SESSION_INVALID, e.getCode());
        assertEquals("PASSWORD_CHANGED", e.dataString("reason"));
    }

    @Test
    public void nonJsonServerErrorAndNetwork() {
        ApiException e = RxResult.toApiException(new HttpException(
                Response.error(502, ResponseBody.create("<html>Bad Gateway</html>", MediaType.get("text/html")))));
        assertEquals(ErrorCodes.SERVER_ERROR, e.getCode());

        // 超时与断网同属网络失败，超时的提示单独为“服务器响应超时，请稍后重试”（ApiException.network）
        ApiException net = RxResult.toApiException(new SocketTimeoutException("timeout"));
        assertTrue(net.isNetwork());
        assertEquals(ApiException.MSG_TIMEOUT, net.getMessage());
    }

    @Test
    public void completable() {
        RxResult.completable(Single.just(new ApiResponse<Void>(0, "ok", null, "t6"))).blockingAwait();
        RxResult.completable(Single.just(new ApiResponse<Void>(40903, "状态已变化", null, "t7")))
                .test()
                .assertError(t -> t instanceof ApiException && ((ApiException) t).getCode() == 40903);
    }

    private static ApiException errorOf(Single<?> single) {
        try {
            single.blockingGet();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("应当抛出 ApiException");
    }
}
