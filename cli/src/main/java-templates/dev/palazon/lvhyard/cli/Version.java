package dev.palazon.lvhyard.cli;

/**
 * This build's version, which Maven writes in from the pom when it compiles: a release sets it from
 * its tag with {@code -Drevision=0.1.0}. A constant, so the jar and the native executable need no
 * file or manifest to read it from.
 */
final class Version {

  static final String VERSION = "${project.version}";

  private Version() {}
}
