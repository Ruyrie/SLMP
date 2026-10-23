package com.zhimu.server.biz.common.support;

import com.zhimu.server.framework.exception.GlobalExceptionHandler;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.web.SnakeCaseModelAttributeMethodProcessor;
import com.zhimu.server.framework.web.WebMvcConfig;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * 与生产一致的 standalone MockMvc（参考 framework 测试源码的 TestMvc，测试源码不可复用，这里写最小封装）：
 * 统一 JSON 规则、全局异常处理、snake_case 查询参数绑定。
 * 不加 TraceFilter：它会在每次请求结束时清理 UserContext，而单元测试直接在测试线程里设置登录用户。
 */
public final class TestMvc {

    public static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    private TestMvc() {
    }

    public static StandaloneMockMvcBuilder builder(Object... controllers) {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        new WebMvcConfig(null, null, null, null).addFormatters(conversionService);
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(MAPPER),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .setCustomArgumentResolvers(new SnakeCaseModelAttributeMethodProcessor())
                .setConversionService(conversionService);
    }

    public static JsonNode json(String body) {
        return MAPPER.readTree(body);
    }
}
