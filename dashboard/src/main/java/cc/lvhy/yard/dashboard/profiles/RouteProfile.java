package cc.lvhy.yard.dashboard.profiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A Route Profile of this Workstation, as the list shows it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteProfile(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(
            description =
                "Its Traefik middleware type or clientCertificate. Missing when it has more or none.")
        String type,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The Services of this Workstation whose Publications name it.")
        List<String> services) {

  public RouteProfile {
    services = List.copyOf(services);
  }
}
