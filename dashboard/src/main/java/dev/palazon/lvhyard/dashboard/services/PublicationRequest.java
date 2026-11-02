package dev.palazon.lvhyard.dashboard.services;

/**
 * What a Publication is to become.
 *
 * @param scope who gets a Route for it; when left out, a new Publication gets Local Scope and one
 *     that exists keeps its Scope
 */
public record PublicationRequest(Scope scope) {}
