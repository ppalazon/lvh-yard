package dev.palazon.lvhyard.dashboard.services;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * The OpenAPI document of the REST API. The controller names each error response's status and meaning; every 4xx and
 * 5xx response is then given the Problem Details body (RFC 9457) the API answers errors with. The server is relative, so
 * the committed copy at {@code dashboard/openapi.yaml} does not depend on the port it was generated at.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "LVH Yard",
        description = "The Workstations and Services of the Yard, read only, and the Static Instances and"
                + " Publications of this Workstation's Services. A Remote Service can be changed only on its Home"
                + " Workstation, so a change asked under /api/services is refused with 405."),
        servers = @Server(url = "/"))
class ApiDocumentation {

    private static final String PROBLEM_DETAIL = "ProblemDetail";

    @Bean
    OpenApiCustomizer problemDetailsForErrors() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetail());
            Content problem = new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    new io.swagger.v3.oas.models.media.MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL)));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> operation
                    .getResponses().forEach((status, response) -> {
                        if (status.startsWith("4") || status.startsWith("5")) {
                            response.setContent(problem);
                        }
                    })));
        };
    }

    /** RFC 9457's members; extension members sit beside them. */
    private static Schema<?> problemDetail() {
        return new ObjectSchema()
                .properties(Map.of(
                        "type", new StringSchema().format("uri"),
                        "title", new StringSchema(),
                        "status", new IntegerSchema(),
                        "detail", new StringSchema(),
                        "instance", new StringSchema().format("uri")))
                .additionalProperties(true);
    }
}
