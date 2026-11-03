package dev.palazon.lvhyard.dashboard.services;

/**
 * @param scope when left out, a new Publication gets Local Scope and an existing one keeps its
 *     Scope
 */
public record PublicationRequest(Scope scope) {}
