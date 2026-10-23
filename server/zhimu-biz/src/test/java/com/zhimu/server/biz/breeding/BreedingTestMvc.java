package com.zhimu.server.biz.breeding;

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

/** 模块05 Controller 的 standalone MockMvc：与生产一致的 JSON（snake_case、null 不输出）、异常与参数绑定。 */
public final class BreedingTestMvc {

    public static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    private BreedingTestMvc() {
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
