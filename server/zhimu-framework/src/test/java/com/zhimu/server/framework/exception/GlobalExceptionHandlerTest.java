package com.zhimu.server.framework.exception;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.web.TestMvc;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class GlobalExceptionHandlerTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Getter
    @Setter
    public static class ItemRequest {
        @NotNull(message = "请填写实际数量")
        @DecimalMin(value = "0.01", message = "实际数量必须大于 0")
        private BigDecimal actualQty;
    }

    @Getter
    @Setter
    public static class CreateRequest {
        @NotBlank(message = "请填写名称")
        private String penName;
        @Valid
        @NotEmpty(message = "至少登记一项")
        private List<ItemRequest> items;
    }

    public static class Service {
        public void create(@Valid CreateRequest request) {
        }
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/v1/t/biz")
        public ApiResponse<Void> biz() {
            throw new BizException(ErrorCode.CAPACITY_CONFIRM, "超出栏舍容量").with("need_confirm", true);
        }

        @PostMapping("/api/v1/t/body")
        public ApiResponse<Void> body(@Valid @RequestBody CreateRequest request) {
            return ApiResponse.ok();
        }

        @GetMapping("/api/v1/t/param")
        public ApiResponse<Void> param(@RequestParam("biz_type") @NotBlank(message = "请选择类型") String bizType,
                                       @RequestParam @Min(value = 1, message = "数量至少为 1") Integer count) {
            return ApiResponse.ok();
        }

        @GetMapping("/api/v1/t/typed")
        public ApiResponse<Void> typed(@RequestParam("farm_id") Long farmId) {
            return ApiResponse.ok();
        }

        @GetMapping("/api/v1/t/upload")
        public ApiResponse<Void> upload() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/api/v1/t/dup")
        public ApiResponse<Void> dup() {
            throw new DuplicateKeyException("Duplicate entry");
        }

        @GetMapping("/api/v1/t/boom")
        public ApiResponse<Void> boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/api/v1/t/violation")
        public ApiResponse<Void> violation() {
            CreateRequest request = new CreateRequest();
            request.setPenName("1 号舍");
            request.setItems(List.of(new ItemRequest()));
            throw new ConstraintViolationException(VALIDATOR.validate(request));
        }

        @GetMapping("/api/v1/t/method-violation")
        public ApiResponse<Void> methodViolation() throws Exception {
            CreateRequest request = new CreateRequest();
            request.setPenName("");
            request.setItems(List.of(new ItemRequest()));
            Method method = Service.class.getMethod("create", CreateRequest.class);
            throw new ConstraintViolationException(VALIDATOR.forExecutables()
                    .validateParameters(new Service(), method, new Object[]{request}));
        }

        @GetMapping("/api/v1/t/secured")
        public ApiResponse<Void> secured() {
            return ApiResponse.ok();
        }
    }

    /** 模拟拦截器中抛出的业务异常 */
    static class RejectInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "PASSWORD_CHANGED");
        }
    }

    private final MockMvc mvc = TestMvc.builder(new TestController())
            .addMappedInterceptors(new String[]{"/api/v1/t/secured"}, new RejectInterceptor())
            .build();

    private JsonNode perform(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = TestMvc.json(result.getResponse().getContentAsString());
        assertThat(body.get("trace_id").asString()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        return body;
    }

    @Test
    void 业务异常原样返回且HTTP状态取前三位() throws Exception {
        JsonNode body = perform(get("/api/v1/t/biz"), 422);
        assertThat(body.get("code").asInt()).isEqualTo(42206);
        assertThat(body.get("message").asString()).isEqualTo("超出栏舍容量");
        assertThat(body.get("data").get("need_confirm").asBoolean()).isTrue();
    }

    @Test
    void 拦截器抛出的40104返回401和JSON() throws Exception {
        JsonNode body = perform(get("/api/v1/t/secured"), 401);
        assertThat(body.get("code").asInt()).isEqualTo(40104);
        assertThat(body.get("data").get("reason").asString()).isEqualTo("PASSWORD_CHANGED");
    }

    @Test
    void 请求体校验失败_字段为snake_case路径() throws Exception {
        String json = "{\"pen_name\":\"\",\"items\":[{\"actual_qty\":0}]}";
        JsonNode body = perform(post("/api/v1/t/body").contentType(MediaType.APPLICATION_JSON).content(json), 400);

        assertThat(body.get("code").asInt()).isEqualTo(40001);
        String errors = body.get("data").get("field_errors").toString();
        assertThat(errors).contains("{\"field\":\"pen_name\",\"message\":\"请填写名称\"}")
                .contains("{\"field\":\"items[0].actual_qty\",\"message\":\"实际数量必须大于 0\"}");
        assertThat(body.get("message").asString()).isIn("请填写名称", "实际数量必须大于 0");
    }

    @Test
    void 方法参数校验失败() throws Exception {
        JsonNode body = perform(get("/api/v1/t/param?biz_type=&count=0"), 400);
        String errors = body.get("data").get("field_errors").toString();
        assertThat(errors).contains("{\"field\":\"biz_type\",\"message\":\"请选择类型\"}")
                .contains("{\"field\":\"count\",\"message\":\"数量至少为 1\"}");
    }

    @Test
    void 缺少参数与类型不匹配() throws Exception {
        JsonNode missing = perform(get("/api/v1/t/typed"), 400);
        assertThat(missing.get("data").get("field_errors").toString())
                .contains("{\"field\":\"farm_id\",\"message\":\"不能为空\"}");

        JsonNode mismatch = perform(get("/api/v1/t/typed?farm_id=abc"), 400);
        assertThat(mismatch.get("data").get("field_errors").toString())
                .contains("{\"field\":\"farm_id\",\"message\":\"格式不正确\"}");
    }

    @Test
    void 约束违反异常_去掉方法与参数前缀() throws Exception {
        JsonNode bean = perform(get("/api/v1/t/violation"), 400);
        assertThat(bean.get("data").get("field_errors").toString())
                .contains("{\"field\":\"items[0].actual_qty\",\"message\":\"请填写实际数量\"}");

        JsonNode method = perform(get("/api/v1/t/method-violation"), 400);
        assertThat(method.get("data").get("field_errors").toString())
                .contains("{\"field\":\"pen_name\",\"message\":\"请填写名称\"}")
                .contains("{\"field\":\"items[0].actual_qty\",\"message\":\"请填写实际数量\"}");
    }

    @Test
    void 请求体格式错误() throws Exception {
        JsonNode body = perform(post("/api/v1/t/body").contentType(MediaType.APPLICATION_JSON).content("{bad"), 400);
        assertThat(body.get("code").asInt()).isEqualTo(40001);
        assertThat(body.get("message").asString()).isEqualTo("请求格式不正确");
    }

    @Test
    void 文件过大() throws Exception {
        JsonNode body = perform(get("/api/v1/t/upload"), 400);
        assertThat(body.get("code").asInt()).isEqualTo(40002);
        assertThat(body.get("message").asString()).isEqualTo("文件过大");
    }

    @Test
    void 接口不存在与方法不支持() throws Exception {
        JsonNode notFound = perform(get("/api/v1/t/nope"), 404);
        assertThat(notFound.get("code").asInt()).isEqualTo(40401);
        assertThat(notFound.get("message").asString()).isEqualTo("接口不存在");

        JsonNode method = perform(delete("/api/v1/t/biz"), 404);
        assertThat(method.get("code").asInt()).isEqualTo(40401);
    }

    @Test
    void 唯一键冲突() throws Exception {
        JsonNode body = perform(get("/api/v1/t/dup"), 409);
        assertThat(body.get("code").asInt()).isEqualTo(40903);
        assertThat(body.get("message").asString()).isEqualTo("数据已存在，请刷新后重试");
    }

    @Test
    void 未知异常返回50001() throws Exception {
        JsonNode body = perform(get("/api/v1/t/boom"), 500);
        assertThat(body.get("code").asInt()).isEqualTo(50001);
        assertThat(body.get("message").asString()).isEqualTo("出了点问题，请稍后重试");
        assertThat(body.has("data")).isFalse();
    }

    @Test
    void 字段路径转snake_case() {
        assertThat(FieldPaths.toSnake("items[0].actualQty")).isEqualTo("items[0].actual_qty");
        assertThat(FieldPaths.toSnake("recent7DoneDays")).isEqualTo("recent7_done_days");
        assertThat(FieldPaths.toSnake("tabCounts[unreadCount]")).isEqualTo("tab_counts[unreadCount]");
        assertThat(FieldPaths.toSnake("farm.penList[2].penName")).isEqualTo("farm.pen_list[2].pen_name");
        assertThat(FieldPaths.toSnake("already_snake")).isEqualTo("already_snake");
    }
}
