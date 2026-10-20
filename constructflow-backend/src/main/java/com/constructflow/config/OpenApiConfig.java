package com.constructflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI constructFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConstructFlow API")
                        .version("v1")
                        .description("""
                                Construction project management API. Authenticate with POST /api/v1/auth/login, \
                                then send the returned access token as `Authorization: Bearer <token>`. \
                                Errors use RFC 9457 Problem Details with a machine-readable `code`."""))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
