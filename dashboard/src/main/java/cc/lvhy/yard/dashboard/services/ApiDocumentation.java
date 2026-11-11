package cc.lvhy.yard.dashboard.services;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/**
 * Every 4xx and 5xx response gets the Problem Details body (RFC 9457). The server is relative, so
 * the committed {@code dashboard/openapi.yaml} does not depend on the port.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "LVH Yard",
            description =
                "The Yard's Workstations and Services, and this Workstation's Instances,"
                    + " Publications and Aliases. A change to a Remote Service is refused with"
                    + " 405."),
    servers = @Server(url = "/"))
class ApiDocumentation {

  private static final String PROBLEM_DETAIL = "ProblemDetail";

  @Bean
  OpenApiCustomizer problemDetailsForErrors() {
    return openApi -> {
      openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetail());
      Content problem =
          new Content()
              .addMediaType(
                  MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                  new io.swagger.v3.oas.models.media.MediaType()
                      .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL)));
      openApi
          .getPaths()
          .values()
          .forEach(path ->
              path.readOperations()
                  .forEach(operation ->
                      operation
                          .getResponses()
                          .forEach((status, response) -> {
                            if (status.startsWith("4") || status.startsWith("5")) {
                              response.setContent(problem);
                            }
                          })));
    };
  }

  /** RFC 9457's members; extension members sit beside them. */
  private static Schema<?> problemDetail() {
    return new ObjectSchema()
        .properties(
            Map.of(
                "type", new StringSchema().format("uri"),
                "title", new StringSchema(),
                "status", new IntegerSchema(),
                "detail", new StringSchema(),
                "instance", new StringSchema().format("uri")))
        .additionalProperties(true);
  }
}
