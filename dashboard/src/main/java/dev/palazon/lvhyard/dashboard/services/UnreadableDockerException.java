package dev.palazon.lvhyard.dashboard.services;

/** Docker did not answer, so its containers are not known for now. */
public class UnreadableDockerException extends RuntimeException {

  /**
   * @param reason what to do about it, from {@link
   *     dev.palazon.lvhyard.dashboard.docker.DockerFailures}
   */
  UnreadableDockerException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
