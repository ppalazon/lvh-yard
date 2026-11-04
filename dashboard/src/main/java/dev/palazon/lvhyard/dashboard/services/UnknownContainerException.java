package dev.palazon.lvhyard.dashboard.services;

/** No running container has the name. */
public class UnknownContainerException extends RuntimeException {

  UnknownContainerException(String container) {
    super("No running container is called " + container + ".");
  }
}
