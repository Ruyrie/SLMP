package com.zhimu.server.biz.account.test;

import com.zhimu.server.framework.exception.GlobalExceptionHandler;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/** 与生产一致的 standalone MockMvc：snake_case JSON、空值省略、GlobalExceptionHandler。 */
public final class AccountTestMvc {

    public static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    private AccountTestMvc() {
    }

    public static StandaloneMockMvcBuilder builder(Object... controllers) {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(MAPPER),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .setConversionService(conversionService);
    }

    public static JsonNode json(String body) {
        return MAPPER.readTree(body);
    }
}
