package cc.lvhy.yard.dashboard.profiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** A Route Profile of this Workstation, with its definition. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteProfileDetail(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(
            description =
                "Its Traefik middleware type, clientCertificate or mutualTLS. Missing when it has"
                    + " more or none.")
        String type,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The Services of this Workstation whose Publications name it.")
        List<String> services,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "The middleware in Traefik's format, or {\"clientCertificate\": {}} or"
                    + " {\"mutualTLS\": {}}.")
        Map<String, Object> definition) {

  public RouteProfileDetail {
    services = List.copyOf(services);
  }
}
