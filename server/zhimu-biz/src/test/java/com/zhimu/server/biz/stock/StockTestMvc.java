package com.zhimu.server.biz.stock;

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
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/** 模块09 Controller standalone MockMvc 的最小封装（与生产一致的 JSON 规则、异常处理、snake_case 绑定）。 */
public final class StockTestMvc {

    public static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    private StockTestMvc() {
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
}
