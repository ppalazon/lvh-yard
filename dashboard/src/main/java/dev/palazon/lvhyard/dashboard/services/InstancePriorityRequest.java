package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What to change in the Instance Priority of a Service. Each part left out, {@code null}, is kept.
 *
 * @param order Instance Source IDs. They go first, in this order, and the other sources follow in
 *     their order.
 * @param on false makes every passing Instance of every source share the Route. The order stays.
 */
public record InstancePriorityRequest(
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Instance Source IDs. They go first, in this order, and the other sources"
                            + " follow in their order. When left out, the order stays."))
        List<String> order,
    @Schema(
            description =
                "false makes every passing Instance of every source share the Route. When left"
                    + " out, the toggle stays.")
        Boolean on) {}
