package cc.lvhy.yard.dashboard.services;

/**
 * A Publication was saved or removed. Bound Instances listen, because their targets follow the
 * Scope. Publications know nothing of them.
 */
record PublicationsChanged() {}
