package dev.palazon.lvhyard.dashboard.services;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is a Problem Details document (RFC 9457). Before the advice for every controller,
 * which matches every exception.
 */
@Order(0)
@RestControllerAdvice(
    assignableTypes = {
      AliasesController.class,
      ContainersController.class,
      ServicesController.class,
      WorkstationServicesController.class,
      YardController.class
    })
class ApiProblems extends ResponseEntityExceptionHandler {

  @ExceptionHandler
  ProblemDetail invalidRegistration(InvalidRegistrationException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail invalidPriority(InvalidPriorityException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownRouteProfile(UnknownRouteProfileException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail coreService(CoreServiceException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownState(UnknownStateException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownInstance(UnknownInstanceException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownContainerBinding(UnknownContainerBindingException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unreachableWorkstation(UnreachableWorkstationException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unreadableDocker(UnreadableDockerException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail followedContainer(FollowedContainerException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownContainer(UnknownContainerException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownWorkstation(UnknownWorkstationException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownAlias(UnknownAliasException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler
  ProblemDetail unknownService(UnknownServiceException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }
}
