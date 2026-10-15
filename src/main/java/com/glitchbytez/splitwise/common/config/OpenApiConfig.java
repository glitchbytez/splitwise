package com.glitchbytez.splitwise.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI splitwiseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Splitwise API")
                        .version("v1")
                        .description("Groups, shared expenses, balances, settlements and debt simplification. "
                                + "All amounts are USD decimal strings with two places, e.g. \"12.50\"."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
