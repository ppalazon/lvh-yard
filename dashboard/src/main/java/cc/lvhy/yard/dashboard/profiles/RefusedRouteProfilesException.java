package cc.lvhy.yard.dashboard.profiles;

import cc.lvhy.yard.dashboard.profiles.RouteProfileChecks.Problem;
import java.util.List;

/** One or more profiles of a set are refused, so no profile of the set is written. */
public class RefusedRouteProfilesException extends RuntimeException {

  private final List<Problem> problems;

  RefusedRouteProfilesException(List<Problem> problems) {
    super(String.join(" ", problems.stream().map(Problem::reason).toList()));
    this.problems = List.copyOf(problems);
  }

  public List<Problem> problems() {
    return problems;
  }
}
