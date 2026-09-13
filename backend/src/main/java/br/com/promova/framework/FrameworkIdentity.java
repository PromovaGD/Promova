package br.com.promova.framework;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic, display-name-independent identifiers for copyable framework links. */
public final class FrameworkIdentity {
  private FrameworkIdentity() {}

  public static String version(CareerFramework framework) {
    StringBuilder canonical = new StringBuilder("promova-framework-v1\n");
    framework
        .levels()
        .forEach(
            (levelKey, level) -> {
              canonical
                  .append(levelKey)
                  .append('\n')
                  .append(value(level.title()))
                  .append('\n')
                  .append(value(level.description()))
                  .append('\n');
              level.criteria().forEach(
                  (criterionKey, description) ->
                      canonical
                          .append(criterionKey)
                          .append('\n')
                          .append(value(description))
                          .append('\n'));
            });
    return "sha256-" + digest(canonical.toString(), 16);
  }

  public static String criterionId(String version, String levelKey, String criterionKey) {
    return "criterion-v1-" + digest(version + "\n" + levelKey + "\n" + criterionKey, 24);
  }

  private static String digest(String value, int characters) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes).substring(0, characters);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
