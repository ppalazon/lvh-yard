package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.services.RemoteServiceException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Keeps the Service pages' refusals ahead of the general error handler. */
@ControllerAdvice(
    assignableTypes = {
      PagesController.class,
      ServicePagesController.class,
      ServiceLifecyclePagesController.class,
      InstancePagesController.class,
      RoutingPagesController.class,
      AliasesPagesController.class
    })
@Order(Ordered.HIGHEST_PRECEDENCE)
class ServicePagesProblems {

  @ExceptionHandler
  void forbidden(RemoteServiceException e, HttpServletResponse response) throws IOException {
    response.sendError(HttpStatus.FORBIDDEN.value(), e.getMessage());
  }
}
