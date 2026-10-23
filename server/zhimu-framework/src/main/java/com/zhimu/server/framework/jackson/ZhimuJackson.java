package com.zhimu.server.framework.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.util.BeijingTime;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.YearMonthDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.YearMonthSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 统一的 JSON 规则：SNAKE_CASE、null 不输出、北京时间格式、BigDecimal 普通数字、忽略未知字段。
 * HTTP 用的 JsonMapper 由 {@link JacksonConfig} 通过 Spring Boot 的定制器套用；Redis 等内部用途用 {@link #newPlainMapper()}。
 */
public final class ZhimuJackson {

    /** 反序列化 datetime：同时接受 yyyy-MM-dd HH:mm:ss 与 yyyy-MM-dd HH:mm */
    public static final DateTimeFormatter DATETIME_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");
    /** 反序列化时刻：同时接受 HH:mm 与 HH:mm:ss */
    public static final DateTimeFormatter TIME_INPUT = DateTimeFormatter.ofPattern("HH:mm[:ss]");

    private ZhimuJackson() {
    }

    /** HTTP 用：基础规则 + 员工视图裁剪（@ManagerView） */
    public static void customizeHttp(JsonMapper.Builder builder) {
        customizeBase(builder);
        builder.addModule(new SimpleModule("zhimu-manager-view").setSerializerModifier(new ManagerViewSerializerModifier()));
    }

    /** 基础规则（不含员工视图裁剪） */
    public static void customizeBase(JsonMapper.Builder builder) {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .defaultTimeZone(TimeZone.getTimeZone(BeijingTime.ZONE))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 3 默认按字母排序，这里保持声明顺序（code、message、data、trace_id）
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .addMixIn(ApiResponse.class, ApiResponseMixin.class)
                .addModule(timeModule());
    }

    /** ApiResponse.isSuccess() 只供服务端判断，不输出 */
    @JsonIgnoreProperties({"success"})
    private abstract static class ApiResponseMixin {
    }

    public static JsonMapper newHttpMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        customizeHttp(builder);
        return builder.build();
    }

    public static JsonMapper newPlainMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        customizeBase(builder);
        return builder.build();
    }

    private static SimpleModule timeModule() {
        SimpleModule module = new SimpleModule("zhimu-time");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(BeijingTime.DATETIME));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_INPUT));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(BeijingTime.DATE));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(BeijingTime.DATE));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(BeijingTime.TIME));
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(TIME_INPUT));
        module.addSerializer(YearMonth.class, new YearMonthSerializer(BeijingTime.MONTH));
        module.addDeserializer(YearMonth.class, new YearMonthDeserializer(BeijingTime.MONTH));
        return module;
    }
}
