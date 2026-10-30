package dev.palazon.lvhyard.dashboard.services;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The REST API answers every error with a Problem Details document (RFC 9457): a broken rule with a detail that
 * explains it, and Spring's own errors, such as a malformed body or an unknown Service, as its base class does.
 */
@RestControllerAdvice(assignableTypes = { ServicesController.class, WorkstationServicesController.class,
        WorkstationsController.class })
class ApiProblems extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail invalidRegistration(InvalidRegistrationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail unknownInstance(UnknownInstanceException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail unreachableWorkstation(UnreachableWorkstationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler
    ProblemDetail unknownService(UnknownServiceException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
