package com.zhimu.slmp.ai.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.ai.AiTestRules;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.request.AiAskRequest;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * API-K06 流式读取（OkHttp 拦截器返回 text/event-stream 或 JSON 错误体）：meta → delta → citation → done 顺序推送，
 * 心跳为 HEARTBEAT；开流前错误按 ApiResponse 转为 ApiException；Mock 整段返回（Content-Type 为 JSON）时按 SSE 回放。
 */
public class AiRepositoryStreamTest {

    @Rule
    public AiTestRules rules = new AiTestRules();

    private static AiRepository repo(int code, String contentType, String body) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(ResponseBody.create(body, MediaType.get(contentType))).build()).build();
        Retrofit r = new Retrofit.Builder().baseUrl("https://api.example.com/").client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonFactory.get())).build();
        return new AiRepository(r.create(AiApi.class), r.create(AiStreamApi.class), GsonFactory.get());
    }

    @Test
    public void eventStream_inOrder() throws IOException {
        String sse = AiTestRules.read("ask_normal.sse");
        TestSubscriber<AiStreamEvent> ts = repo(200, "text/event-stream", sse).ask(new AiAskRequest(), "k1").test();
        ts.assertComplete();
        List<AiStreamEvent> list = ts.values();
        assertEquals(AiStreamEvent.Type.META, list.get(0).getType());
        assertEquals(AiStreamEvent.Type.HEARTBEAT, list.get(1).getType());
        assertEquals(AiStreamEvent.Type.DELTA, list.get(2).getType());
        AiStreamEvent done = list.get(list.size() - 1);
        assertEquals(AiStreamEvent.Type.DONE, done.getType());
        assertEquals(AiCodes.ANSWER_NORMAL, done.getDone().getAnswerType());
        assertEquals(2, done.getDone().getMessage().getCitations().size());
        assertEquals(AiStreamEvent.Type.CITATION, list.get(list.size() - 2).getType());
        StringBuilder text = new StringBuilder();
        for (AiStreamEvent e : list) {
            if (e.getType() == AiStreamEvent.Type.DELTA) {
                text.append(e.getText());
            }
        }
        assertTrue(text.toString().startsWith("按平台休药期标准，氟苯尼考粉用于猪的休药期为 20 天[^1]"));
    }

    @Test
    public void errorBeforeStream_isApiException() {
        String body = "{\"code\":42901,\"message\":\"上一个问题还在回答中，请稍后再问\",\"data\":null,\"trace_id\":\"t\"}";
        repo(429, "application/json", body).ask(new AiAskRequest(), "k2").test()
                .assertError(e -> e instanceof ApiException && ((ApiException) e).getCode() == 42901
                        && "上一个问题还在回答中，请稍后再问".equals(e.getMessage()));
    }

    @Test
    public void mockReplay_jsonContentTypeButSseBody() throws IOException {
        String sse = AiTestRules.read("ask_timeout.sse");
        TestSubscriber<AiStreamEvent> ts = repo(200, "application/json", sse).retry(5501).test();
        ts.assertComplete();
        AiStreamEvent last = ts.values().get(ts.values().size() - 1);
        assertEquals(AiStreamEvent.Type.ERROR, last.getType());
        assertEquals(50401, last.getError().getCode());
        assertEquals(AiCodes.STATUS_TIMEOUT, last.getError().getStatus());
    }
}
