package dev.palazon.lvhyard.dashboard.profiles;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error of the Route Profiles API is a Problem Details document (RFC 9457), as {@code
 * ApiProblems} gives for the other endpoints. It comes before {@code Problems}, because {@code
 * Problems} matches every exception.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = RouteProfilesController.class)
class RouteProfileProblems extends ResponseEntityExceptionHandler {

  @ExceptionHandler
  ProblemDetail refused(RefusedRouteProfilesException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setProperty("problems", exception.problems());
    return problem;
  }

  @ExceptionHandler
  ProblemDetail inUse(RouteProfileInUseException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setProperty("services", exception.services());
    problem.setProperty("chains", exception.chains());
    return problem;
  }

  @ExceptionHandler
  ProblemDetail missing(NoRouteProfileException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }
}
