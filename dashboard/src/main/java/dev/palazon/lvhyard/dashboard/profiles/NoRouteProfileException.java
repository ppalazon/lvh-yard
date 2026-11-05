package dev.palazon.lvhyard.dashboard.profiles;

public class NoRouteProfileException extends RuntimeException {

  NoRouteProfileException(String profile) {
    super("This Workstation has no Route Profile " + profile + ".");
  }
}
