package cc.lvhy.yard.dashboard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * A test that waits on the real timing of Traefik or Docker and failed now and then on a slow
 * GitHub Actions runner, with the same code that passed on the next run. It runs on a computer and
 * is skipped in GitHub Actions, where it gives no signal. Make the test deterministic, then remove
 * this annotation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@DisabledIfEnvironmentVariable(
    named = "GITHUB_ACTIONS",
    matches = "true",
    disabledReason =
        "Depends on the timing of real containers; unreliable on GitHub Actions runners")
public @interface TimingSensitive {}
