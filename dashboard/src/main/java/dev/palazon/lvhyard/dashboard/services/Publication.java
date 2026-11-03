package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/** It covers every Instance of the Service, including ones registered later. */
public record Publication(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Scope scope) {}
