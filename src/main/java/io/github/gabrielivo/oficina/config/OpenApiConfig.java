package io.github.gabrielivo.oficina.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI oficinaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Oficina REST API")
                        .version("v1")
                        .description("API RESTful de gerenciamento de clientes, veículos, ordens de serviço e pagamentos."));
    }
}
