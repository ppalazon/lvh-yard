package cc.lvhy.yard.dashboard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * A test that waits for a real container, such as Traefik, to be ready. On a GitHub Actions runner
 * the container answered later than the test waited, so the test failed now and then with the same
 * code that passed on the next run. It runs on a computer and is skipped in GitHub Actions, where
 * it gives no signal. CI does not run it, so run it before you commit. First make the test wait for
 * the state that it needs, not for an event or a fixed time. Keep this annotation if the test is
 * still unreliable on GitHub Actions. Remove it only after you see the test pass there reliably.
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
