package com.zhimu.slmp.network.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Mock 规则匹配：目录字母顺序、文件内顺序、路径参数、when 条件、HTTP 状态与未配置兜底 */
public class MockInterceptorTest {

    private MemorySource source;
    private OkHttpClient client;

    /** 内存中的 mock 目录 */
    static final class MemorySource implements MockSource {
        final Map<String, String> files = new HashMap<>();

        @NonNull
        @Override
        public List<String> listDirs() {
            List<String> dirs = new ArrayList<>();
            for (String k : files.keySet()) {
                String d = k.substring(0, k.indexOf('/'));
                if (!dirs.contains(d)) {
                    dirs.add(d);
                }
            }
            Collections.reverse(dirs);
            return dirs;
        }

        @Nullable
        @Override
        public String read(@NonNull String dir, @NonNull String file) {
            return files.get(dir + "/" + file);
        }
    }

    private static String ok(String data) {
        return "{\"code\":0,\"message\":\"ok\",\"data\":" + data + ",\"trace_id\":\"mock\"}";
    }

    @Before
    public void setUp() {
        source = new MemorySource();
        source.files.put("farm/routes.json", "["
                + "{\"method\":\"GET\",\"path\":\"/api/v1/farms/{farmId}/context\","
                + "\"when\":{\"path\":{\"farmId\":\"1001\"},\"header\":{\"Authorization\":\"Bearer mock-staff\"}},"
                + "\"file\":\"ctx_staff.json\",\"delay\":0},"
                + "{\"method\":\"GET\",\"path\":\"/api/v1/farms/{farmId}/context\",\"file\":\"ctx_farm.json\",\"delay\":0},"
                + "{\"method\":\"GET\",\"path\":\"/api/v1/farms/{farmId}/members\",\"when\":{\"query\":{\"page\":\"2\"}},"
                + "\"file\":\"members_p2.json\",\"delay\":0},"
                + "{\"method\":\"GET\",\"path\":\"/api/v1/farms/{farmId}/members\",\"file\":\"members.json\",\"delay\":0}"
                + "]");
        source.files.put("farm/ctx_staff.json", ok("{\"role\":\"STAFF\"}"));
        source.files.put("farm/ctx_farm.json", ok("{\"role\":\"FROM_FARM\"}"));
        source.files.put("farm/members.json", ok("{\"page\":1}"));
        source.files.put("farm/members_p2.json", ok("{\"page\":2}"));
        source.files.put("common/routes.json", "["
                + "{\"method\":\"GET\",\"path\":\"/api/v1/farms/{farmId}/context\",\"when\":{\"path\":{\"farmId\":\"1003\"}},"
                + "\"file\":\"ctx_common.json\",\"delay\":0},"
                + "{\"method\":\"POST\",\"path\":\"/api/v1/auth/login/password\",\"when\":{\"body\":[\"13700005678\",\"Zmt12345\"]},"
                + "\"file\":\"login_staff.json\",\"delay\":0},"
                + "{\"method\":\"POST\",\"path\":\"/api/v1/auth/login/password\",\"file\":\"login_wrong.json\",\"delay\":0}"
                + "]");
        source.files.put("common/ctx_common.json", ok("{\"role\":\"FROM_COMMON\"}"));
        source.files.put("common/login_staff.json", ok("{\"access_token\":\"mock-staff\"}"));
        source.files.put("common/login_wrong.json",
                "{\"code\":40102,\"message\":\"手机号或密码不正确\",\"data\":null,\"trace_id\":\"mock\"}");
        client = new OkHttpClient.Builder().addInterceptor(new MockInterceptor(source)).build();
    }

    private Response get(String path, String... headers) throws IOException {
        Request.Builder b = new Request.Builder().url("https://api.zhimutong.example.com" + path);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return client.newCall(b.build()).execute();
    }

    private Response post(String path, String json) throws IOException {
        Request r = new Request.Builder().url("https://api.zhimutong.example.com" + path)
                .post(RequestBody.create(json, MediaType.get("application/json"))).build();
        return client.newCall(r).execute();
    }

    @Test
    public void directoriesSortedAlphabetically() throws IOException {
        // common 在 farm 之前：1003 命中 common 的规则
        try (Response r = get("/api/v1/farms/1003/context")) {
            assertTrue(r.body().string().contains("FROM_COMMON"));
        }
        try (Response r = get("/api/v1/farms/1002/context")) {
            assertTrue(r.body().string().contains("FROM_FARM"));
        }
    }

    @Test
    public void headerAndPathConditions() throws IOException {
        try (Response r = get("/api/v1/farms/1001/context", "Authorization", "Bearer mock-staff")) {
            assertTrue(r.body().string().contains("STAFF"));
        }
        try (Response r = get("/api/v1/farms/1001/context", "authorization", "Bearer mock-owner")) {
            assertTrue(r.body().string().contains("FROM_FARM"));
        }
    }

    @Test
    public void queryCondition() throws IOException {
        try (Response r = get("/api/v1/farms/1001/members?page=2&size=20")) {
            assertTrue(r.body().string().contains("\"page\":2"));
        }
        try (Response r = get("/api/v1/farms/1001/members?page=1&size=20")) {
            assertTrue(r.body().string().contains("\"page\":1"));
        }
    }

    @Test
    public void bodyConditionAndHttpStatus() throws IOException {
        try (Response r = post("/api/v1/auth/login/password", "{\"phone\":\"13700005678\",\"password\":\"Zmt12345\"}")) {
            assertEquals(200, r.code());
            assertTrue(r.body().string().contains("mock-staff"));
        }
        try (Response r = post("/api/v1/auth/login/password", "{\"phone\":\"13700005678\",\"password\":\"wrong\"}")) {
            assertEquals(401, r.code());
        }
    }

    @Test
    public void notConfigured() throws IOException {
        try (Response r = get("/api/v1/unknown/path")) {
            assertEquals(500, r.code());
            String body = r.body().string();
            assertTrue(body.contains("50001"));
            assertTrue(body.contains("Mock 未配置：GET /api/v1/unknown/path"));
        }
    }

    @Test
    public void pathMatching() {
        assertNotNull(MockRouter.matchPath(MockRule.split("/api/v1/farms/{farmId}"), Arrays.asList("api", "v1", "farms", "1001")));
        assertNull(MockRouter.matchPath(MockRule.split("/api/v1/farms/{farmId}"), Arrays.asList("api", "v1", "farms")));
        assertEquals("1001", MockRouter.matchPath(MockRule.split("/api/v1/farms/{farmId}/members"),
                Arrays.asList("api", "v1", "farms", "1001", "members")).get("farmId"));
    }
}
