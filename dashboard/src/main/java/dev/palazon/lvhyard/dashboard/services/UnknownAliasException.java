package dev.palazon.lvhyard.dashboard.services;

/** This Workstation has no Alias of this name. */
public class UnknownAliasException extends RuntimeException {

  UnknownAliasException(String alias) {
    super("This Workstation has no Alias " + alias + ".");
  }
}
