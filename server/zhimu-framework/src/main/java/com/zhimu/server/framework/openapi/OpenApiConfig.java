package com.zhimu.server.framework.openapi;

import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.annotation.PublicApi;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 接口文档：用户端 /api/v1/**（Bearer 访问令牌）、管理端 /admin/v1/**（会话 Cookie）两个分组；@PublicApi 接口不标安全要求。
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String USER_SCHEME = "bearerAuth";
    public static final String ADMIN_SCHEME = "adminSession";

    @Bean
    public OpenAPI zhimuOpenApi(SecurityProperties properties) {
        return new OpenAPI()
                .info(new Info().title("智牧通服务端接口").version("v1"))
                .components(new Components()
                        .addSecuritySchemes(USER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(ADMIN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                                .name(properties.getAdminSessionCookieName())));
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user")
                .displayName("用户端")
                .pathsToMatch("/api/v1/**")
                .addOperationCustomizer(security(USER_SCHEME))
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("管理端")
                .pathsToMatch("/admin/v1/**")
                .addOperationCustomizer(security(ADMIN_SCHEME))
                .build();
    }

    private static OperationCustomizer security(String scheme) {
        return (operation, handlerMethod) -> {
            boolean publicApi = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PublicApi.class)
                    || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicApi.class);
            if (!publicApi) {
                operation.addSecurityItem(new SecurityRequirement().addList(scheme));
            }
            return operation;
        };
    }
}
