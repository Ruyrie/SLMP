package com.zhimu.server.admin.base;

import com.zhimu.server.framework.exception.GlobalExceptionHandler;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.web.AppVersionProperties;
import com.zhimu.server.framework.web.SnakeCaseModelAttributeMethodProcessor;
import com.zhimu.server.framework.web.TraceFilter;
import com.zhimu.server.framework.web.WebMvcConfig;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/** 管理端基础数据测试的 MockMvc standalone 封装：与生产一致的 JSON、异常、snake_case 绑定与 TraceFilter。 */
public final class AdminBaseTestMvc {

    public static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    private AdminBaseTestMvc() {
    }

    public static StandaloneMockMvcBuilder builder(Object... controllers) {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        new WebMvcConfig(null, null, null, null).addFormatters(conversionService);
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(MAPPER),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .setCustomArgumentResolvers(new SnakeCaseModelAttributeMethodProcessor())
                .setConversionService(conversionService)
                .addFilters(new TraceFilter(new AppVersionProperties()));
    }

    public static StandaloneMockMvcBuilder builderWithInterceptors(Object[] controllers,
                                                                   HandlerInterceptor... interceptors) {
        return builder(controllers).addInterceptors(interceptors);
    }

    public static JsonNode json(String body) {
        return MAPPER.readTree(body);
    }
}
