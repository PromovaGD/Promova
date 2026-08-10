package br.com.promova.source;

/**
 * Provider-neutral boundary for discovering source items and normalizing them into evidence.
 *
 * <p>Implementations own provider authentication, HTTP/JSON DTOs, provider filtering, and
 * provider error handling. The sync workflow only sees this contract and never needs to import a
 * provider DTO.
 */
public interface SourceAdapter {
  /** Stable display/source value used by the existing evidence natural key. */
  String source();

  /**
   * Discovers one bounded page. The result must contain only normalized items that match the
   * request's source-specific scope, author, and lookback constraints.
   */
  SourcePageResult discover(SourceAdapterRequest request);
}
