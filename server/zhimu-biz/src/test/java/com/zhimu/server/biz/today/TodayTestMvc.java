package com.zhimu.server.biz.today;

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
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * MockMvc standalone 的最小封装：与生产一致的 JSON 规则、全局异常处理、snake_case 查询参数绑定、TraceFilter。
 * 测试源码不能复用 framework 的 TestMvc，这里按模块02 的 FarmTestMvc 同样方式组装。
 */
public final class TodayTestMvc {

    public static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    private TodayTestMvc() {
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
}
