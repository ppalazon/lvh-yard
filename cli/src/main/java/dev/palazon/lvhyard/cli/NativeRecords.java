package dev.palazon.lvhyard.cli;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Lets Jackson read and write {@link Dashboard}'s records in the native image, which keeps only the
 * reflection it is told about at build time. Every record in Dashboard is registered, so a new one
 * needs nothing here. Runs only inside native-image ({@code -Pnative}); the jar never loads it.
 */
final class NativeRecords implements Feature {

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    for (Class<?> type : Dashboard.class.getDeclaredClasses()) {
      if (type.isRecord()) {
        RuntimeReflection.register(type);
        RuntimeReflection.registerAllRecordComponents(type);
        RuntimeReflection.register(type.getDeclaredConstructors());
        RuntimeReflection.register(type.getDeclaredMethods());
        RuntimeReflection.register(type.getDeclaredFields());
      }
    }
  }
}
