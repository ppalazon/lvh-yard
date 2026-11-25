package cc.lvhy.yard.dashboard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * A test that waits for a real container, such as Traefik, to be ready, and that times out now and
 * then on a GitHub Actions runner, where the container answers later than the test waits. The
 * standing policy is to annotate such a test. It runs on a computer and is skipped in GitHub
 * Actions, so CI does not run it and {@code mise run check} before each commit is its gate. The
 * annotation stays: do not remove it to try the test on CI. Write the test to wait, with a limit,
 * for the state that it needs, and not for an event or a fixed time.
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
