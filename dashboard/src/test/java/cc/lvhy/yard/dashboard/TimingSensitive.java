package cc.lvhy.yard.dashboard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * A test that needs real containers to answer on time. On a GitHub Actions runner, Docker and its
 * published ports answer late or not at all, so the test failed now and then with the same code
 * that passed on the next run. It runs on a computer and is skipped in GitHub Actions, where it
 * gives no signal. CI does not run it, so run it before you commit. Keep this annotation when the
 * test is correct and only the runner is unreliable. Remove it only after you see the test pass
 * reliably on GitHub Actions.
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
