package dev.palazon.lvhyard.dashboard.services;

public class UnknownAliasException extends RuntimeException {

  UnknownAliasException(String alias) {
    super("This Workstation has no Alias " + alias + ".");
  }
}
