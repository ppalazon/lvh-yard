package dev.palazon.lvhyard.dashboard.services;

public class UnknownContainerException extends RuntimeException {

  UnknownContainerException(String container) {
    super("No running container is called " + container + ".");
  }
}
