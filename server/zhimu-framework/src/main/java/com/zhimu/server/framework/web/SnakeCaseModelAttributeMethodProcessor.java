package com.zhimu.server.framework.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询对象（com.zhimu.server 下没有 Spring 绑定注解的复杂类型参数，例如 XxxQuery）的参数绑定：
 * 请求参数 start_date 同时以驼峰别名 startDate 提供给数据绑定器，驼峰参数名照常可用；
 * 逗号分隔的 category_ids=1,2,3 由转换服务绑定到 List&lt;Long&gt;；绑定后照常执行 @Valid 校验。
 */
public class SnakeCaseModelAttributeMethodProcessor extends ServletModelAttributeMethodProcessor {

    private static final String BASE_PACKAGE = "com.zhimu.server.";
    private static final String SPRING_BIND_PACKAGE = "org.springframework.web.bind.annotation";

    public SnakeCaseModelAttributeMethodProcessor() {
        super(true);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        if (BeanUtils.isSimpleProperty(type) || !type.getName().startsWith(BASE_PACKAGE)) {
            return false;
        }
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            if (SPRING_BIND_PACKAGE.equals(annotation.annotationType().getPackageName())
                    && !(annotation instanceof ModelAttribute)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void constructAttribute(WebDataBinder binder, NativeWebRequest request) {
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
        ((ServletRequestDataBinder) binder).construct(new CamelAliasRequest(servletRequest));
    }

    @Override
    protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
        ((ServletRequestDataBinder) binder).bind(new CamelAliasRequest(servletRequest));
    }

    /** 在原参数之外追加驼峰别名（原请求已有同名驼峰参数时不覆盖） */
    static final class CamelAliasRequest extends HttpServletRequestWrapper {

        private final Map<String, String[]> parameters;

        CamelAliasRequest(HttpServletRequest request) {
            super(request);
            Map<String, String[]> origin = request.getParameterMap();
            Map<String, String[]> merged = new LinkedHashMap<>(origin);
            origin.forEach((name, values) -> {
                String camel = SnakeCaseNames.toCamel(name);
                if (!camel.equals(name)) {
                    merged.putIfAbsent(camel, values);
                }
            });
            this.parameters = Collections.unmodifiableMap(merged);
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }
    }
}
