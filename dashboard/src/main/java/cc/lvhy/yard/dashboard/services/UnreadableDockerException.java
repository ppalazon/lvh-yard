package cc.lvhy.yard.dashboard.services;

public class UnreadableDockerException extends RuntimeException {

  /**
   * @param reason what to do about it, from {@link cc.lvhy.yard.dashboard.docker.DockerFailures}
   */
  UnreadableDockerException(String reason, Throwable cause) {
    super(reason, cause);
  }
}
