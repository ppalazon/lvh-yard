package cc.lvhy.yard.dashboard.profiles;

import java.util.List;
import java.util.Map;

/** The Services know which Route Profiles they name, so they give this to the profiles. */
public interface ProfileUsage {

  /** The Services of this Workstation whose Publications name each Route Profile, by name. */
  Map<String, List<String>> servicesByProfile();
}
