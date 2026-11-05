package dev.palazon.lvhyard.dashboard.backups;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error of the Backup API is a Problem Details document (RFC 9457). It comes before {@code
 * Problems}, because {@code Problems} matches every exception.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = BackupController.class)
class BackupProblems extends ResponseEntityExceptionHandler {

  /** Each problem has its {@code place} in the file and its {@code reason}. */
  @ExceptionHandler
  ProblemDetail refused(RefusedBackupException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setProperty("problems", exception.problems());
    return problem;
  }

  @ExceptionHandler
  ProblemDetail unknownMode(UnknownImportModeException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  /** {@code applied} lists the plan entries that the import wrote before it stopped. */
  @ExceptionHandler
  ProblemDetail failed(ImportFailedException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    problem.setProperty("applied", exception.applied());
    return problem;
  }
}
