package cc.lvhy.yard.dashboard.backups;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param path the Backups folder, from {@code YARD_BACKUPS}
 */
@ConfigurationProperties("lvh-yard.backups")
public record BackupsSettings(Path path) {}
