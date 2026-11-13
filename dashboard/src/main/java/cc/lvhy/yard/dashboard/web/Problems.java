package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.consul.ConsulSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * This advice handles the exceptions that no controller and no other advice handles. Each one logs
 * one line. The line has the stack trace only when {@code YARD_STACK_TRACES} is true. The REST API
 * answers with Problem Details (RFC 9457). The pages answer with the error page.
 *
 * <p>It must come last. Its handler for {@link Exception} matches every exception, so it can take
 * the exceptions of {@code ApiProblems}.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class Problems {

  private static final Logger LOG = LoggerFactory.getLogger(Problems.class);

  private final ConsulSettings consul;

  private final boolean stackTraces;

  Problems(ConsulSettings consul, @Value("${lvh-yard.stack-traces}") boolean stackTraces) {
    this.consul = consul;
    this.stackTraces = stackTraces;
  }

  /**
   * Only {@code Consul} uses a RestClient. So this exception always means that Consul of this
   * Workstation did not answer in time. This is normal while Consul starts or is busy.
   */
  @ExceptionHandler
  @ResponseBody
  ProblemDetail unanswered(
      ResourceAccessException e, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    LOG.atWarn()
        .setCause(stackTraces ? e : null)
        .log("{} {} failed: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
    request.setAttribute(ErrorPage.CONSUL_URL, consul.url().toString());
    return answer(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Consul does not answer at " + consul.url() + ". Try again in a moment.",
        request,
        response);
  }

  /**
   * An exception with its own status, such as a missing page, keeps the default handling of Spring.
   * So does a client that closed the connection. The handler throws these again, and Spring gives
   * them to its default handling.
   */
  @ExceptionHandler
  @ResponseBody
  ProblemDetail unexpected(Exception e, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    if (e instanceof ErrorResponse || DisconnectedClientHelper.isClientDisconnectedException(e)) {
      throw e;
    }
    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
    LOG.atError()
        .setCause(stackTraces ? e : null)
        .log(
            "{} {} failed: {}{}",
            request.getMethod(),
            request.getRequestURI(),
            e,
            cause == e ? "" : ", caused by " + cause);
    return answer(
        HttpStatus.INTERNAL_SERVER_ERROR,
        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
        request,
        response);
  }

  private static ProblemDetail answer(
      HttpStatus status, String detail, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (request.getRequestURI().startsWith("/api/")) {
      return ProblemDetail.forStatusAndDetail(status, detail);
    }
    response.sendError(status.value(), detail);
    return null;
  }
}
