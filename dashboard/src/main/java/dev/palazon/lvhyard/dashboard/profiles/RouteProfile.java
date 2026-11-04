package dev.palazon.lvhyard.dashboard.profiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** A Route Profile as {@code route-profiles.yaml} writes it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteProfile(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(
            description =
                "Its Traefik middleware type, such as headers or basicAuth. Missing when it has"
                    + " more or none.")
        String type,
    @Schema(description = "Why it is refused. A refused profile does not reach Consul.")
        String error) {

  @JsonIgnore
  public boolean valid() {
    return error == null;
  }
}
