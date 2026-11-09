package dev.palazon.lvhyard.dashboard.services;

public class UnknownAliasException extends RuntimeException {

  UnknownAliasException(String service, String id) {
    super(service + " has no Alias to " + id + ".");
  }
}
